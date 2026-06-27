package com.ecommint.accounthr.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.dto.admin.AdminUserResponse;
import com.ecommint.accounthr.dto.admin.CreateUserRequest;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.RefreshTokenRepository;

/**
 * Admin kullanıcı yönetimi iş mantığı (E1-08, backoffice). Yalnızca ADMIN
 * rolündeki çağıranlar erişebilir (kontrolör katmanında {@code @PreAuthorize}).
 *
 * <p>Güvenlik değişmezleri:
 * <ul>
 *   <li><b>Parola hash'i asla yanıta sızmaz</b> — {@link AdminUserResponse} hash
 *       alanı içermez.</li>
 *   <li><b>Parola değişimi refresh token'ları iptal eder</b> — çalınan/eski
 *       oturumlar geçersizleşir ({@link RefreshTokenRepository#revokeAllByUserId}).</li>
 *   <li><b>Son-aktif-yönetici koruması</b> — bir rol düşürme veya pasifleştirme,
 *       aktif ADMIN sayısını 0'a düşürecekse 409 ({@link LastAdminException}) ile
 *       reddedilir; kullanıcı kendi üzerinde işlem yapsa bile.</li>
 * </ul>
 *
 * <p>Sert silme (hard DELETE) yoktur; kullanıcılar yalnızca pasifleştirilir.
 */
@Service
public class AdminUserService {

    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(AppUserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Tüm kullanıcıları e-postaya göre alfabetik sıralı döner (parola hash'i HARİÇ). */
    @Transactional(readOnly = true)
    public List<AdminUserResponse> list() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "email")).stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    /**
     * Yeni kullanıcı oluşturur. E-posta benzersiz olmalı (mükerrer → 409). Parola
     * BCrypt ile hash'lenir; {@code active} varsayılan {@code true}.
     */
    @Transactional
    public AdminUserResponse create(CreateUserRequest req) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new DuplicateEmailException("Bu e-posta adresi zaten kayıtlı.");
        }
        AppUser user = new AppUser();
        user.setEmail(req.email());
        user.setFullName(req.fullName());
        user.setRole(req.role());
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        return AdminUserResponse.from(userRepository.save(user));
    }

    /**
     * Hedef kullanıcının parolasını BCrypt ile yeniden hash'ler VE tüm aktif
     * refresh token'larını iptal eder (logout ile aynı iptal mekanizması ailesi —
     * kullanıcı-bazlı global revoke). Bilinmeyen id → 404.
     */
    @Transactional
    public void changePassword(Long id, String rawPassword) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı."));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(id);
    }

    /**
     * Kullanıcının rolünü değiştirir. Son aktif ADMIN'in rolü ADMIN dışı bir role
     * düşürülemez (409 LAST_ADMIN). Bilinmeyen id → 404.
     *
     * <p><b>Son-aktif-yönetici koruması — POST-mutasyon doğrulama (TOCTOU yarışına
     * karşı).</b> Eski tasarım sayımı mutasyondan ÖNCE okuyordu (check-then-write);
     * iki eşzamanlı istek FARKLI iki admin'i düşürürken her ikisi de count=2 görüp
     * geçebiliyordu → 0 aktif admin. Artık mutasyon önce uygulanır + {@code flush()}
     * ile UPDATE zorlanır (satır kilidi alınır), SONRA aktif admin sayısı okunur;
     * &lt; 1 ise istisna ile transaction ROLLBACK olur. PostgreSQL READ_COMMITTED
     * altında her UPDATE satır kilidini commit'e kadar tutar, böylece eşzamanlı iki
     * düşürme serileşir: ikinci transaction'ın COUNT'u birincinin commit'inden sonra
     * çalışır ve azalmış sayıyı görüp doğru biçimde reddeder.
     */
    @Transactional
    public AdminUserResponse changeRole(Long id, UserRole newRole) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı."));
        boolean couldReduceActiveAdmins =
                user.getRole() == UserRole.ADMIN && user.isActive() && newRole != UserRole.ADMIN;
        user.setRole(newRole);
        AppUser saved = userRepository.save(user);
        if (couldReduceActiveAdmins) {
            enforceAtLeastOneActiveAdmin();
        }
        return AdminUserResponse.from(saved);
    }

    /**
     * Kullanıcıyı aktif/pasif yapar. Son aktif ADMIN pasifleştirilemez (409
     * LAST_ADMIN). Bilinmeyen id → 404.
     *
     * <p>Son-aktif-yönetici koruması POST-mutasyon doğrulamayla yapılır; ayrıntı
     * için {@link #changeRole(Long, UserRole)} javadoc'una bakınız.
     */
    @Transactional
    public AdminUserResponse changeActive(Long id, boolean active) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı."));
        boolean couldReduceActiveAdmins =
                !active && user.getRole() == UserRole.ADMIN && user.isActive();
        user.setActive(active);
        AppUser saved = userRepository.save(user);
        if (couldReduceActiveAdmins) {
            enforceAtLeastOneActiveAdmin();
        }
        return AdminUserResponse.from(saved);
    }

    /**
     * POST-mutasyon değişmez kontrolü: mutasyonu DB'ye zorla ({@code flush}), ardından
     * aktif ADMIN sayısını oku. Sayı &lt; 1 ise {@link LastAdminException} fırlatılır →
     * transaction ROLLBACK olur (mutasyon geri alınır), 409 döner.
     *
     * <p>{@code flush()} kritik: UPDATE'i hemen DB'ye gönderir → satır kilidi commit'e
     * kadar tutulur. PostgreSQL READ_COMMITTED altında eşzamanlı düşürmeler bu sayede
     * serileşir; ikinci transaction'ın COUNT'u birincinin commit'i sonrası çalışıp
     * azalmış sayıyı görür (H2 testlerinde tek-thread'li davranış aynen korunur — satır
     * kilidi serileşmesi PostgreSQL garantisidir; post-flush count mantığı her iki
     * veritabanında da doğrudur).
     */
    private void enforceAtLeastOneActiveAdmin() {
        userRepository.flush();
        long activeAdmins = userRepository.countByRoleAndActiveTrue(UserRole.ADMIN);
        if (activeAdmins < 1) {
            throw new LastAdminException("Sistemde en az bir aktif yönetici kalmalı.");
        }
    }
}
