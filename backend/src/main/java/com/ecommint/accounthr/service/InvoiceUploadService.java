package com.ecommint.accounthr.service;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.FileAsset;
import com.ecommint.accounthr.domain.Invoice;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.FileType;
import com.ecommint.accounthr.domain.enums.InvoiceStatus;
import com.ecommint.accounthr.dto.invoice.InvoiceUploadResponse;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.FileAssetRepository;
import com.ecommint.accounthr.repository.InvoiceRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ServiceRepository;
import com.ecommint.accounthr.service.storage.StorageException;
import com.ecommint.accounthr.service.storage.StorageService;
import com.ecommint.accounthr.service.storage.StoredFile;

/**
 * E3-05 — Ekip üyesinin "servis + ay seç, dosya yükle" akışı. Bir yüklemede fiziksel
 * dosya(lar) + {@link FileAsset} metadata + {@link Expense}/{@link Invoice} durum
 * güncellemesi TEK transaction mantığında yapılır (atomik).
 *
 * <h2>find-or-create mantığı</h2>
 * <ul>
 *   <li><b>Period</b>: {@code YYYY-MM} koduna karşılık period yoksa oluşturulur
 *       (importer ile aynı desen). Eksik fatura çapraz doğrulaması period'a bağlı
 *       olduğundan period'un var olması şarttır.</li>
 *   <li><b>Expense</b>: servis + period için zaten bir harcama varsa (ör. E2-01 ile
 *       gelen "Bekleniyor" satırı) o KULLANILIR; yoksa yenisi oluşturulur (kart =
 *       servisin varsayılan kartı, tutar/para birimi girdiden — bilgi-amaçlı=false).</li>
 *   <li><b>Invoice</b>: expense'in EXPECTED durumundaki bir invoice'u varsa o
 *       güncellenir; yoksa yeni invoice oluşturulur. Durum = e-Fatura seçiliyse
 *       {@code E_INVOICE} aksi halde {@code FOUND}; not yüklenen dosya adlarını içerir.</li>
 * </ul>
 *
 * <p><b>NOT (E3-07):</b> durum doğrudan set ediliyor (MVP). İleride E3-07 state
 * machine üzerinden geçirilecek; şimdilik EXPECTED → FOUND/E_INVOICE doğrudan yazılır.
 *
 * <h2>Atomiklik</h2>
 * Önce entity'ler doğrulanır/oluşturulur ve flush edilir (invoice id alınır), SONRA
 * dosyalar diske yazılır. Herhangi bir dosya yazımı/metadata persist'i fırlatırsa: o ana
 * kadar yazılmış TÜM fiziksel dosyalar best-effort silinir ve hata yeniden fırlatılır —
 * böylece transaction rollback ile birlikte ne yarım dosya ne de yarım DB kaydı kalır.
 *
 * <p>ASLA {@code expenses/faturalar} (Drive aynası) altına yazmaz; yalnızca
 * STORAGE_ROOT altında {@link StorageService} aracılığıyla çalışır.
 */
@Service
public class InvoiceUploadService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceUploadService.class);

    /** Yüklemeye izin verilen dosya uzantıları (küçük harf). */
    private static final List<String> ALLOWED_EXTENSIONS =
            List.of("pdf", "xml", "jpg", "jpeg", "png");

    /** Tek dosya için maksimum boyut: 10 MB. (Servlet katmanı 25MB; iş kuralı daha sıkı.) */
    static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final ServiceRepository serviceRepository;
    private final PeriodRepository periodRepository;
    private final ExpenseRepository expenseRepository;
    private final InvoiceRepository invoiceRepository;
    private final FileAssetRepository fileAssetRepository;
    private final StorageService storageService;

    public InvoiceUploadService(ServiceRepository serviceRepository,
            PeriodRepository periodRepository,
            ExpenseRepository expenseRepository,
            InvoiceRepository invoiceRepository,
            FileAssetRepository fileAssetRepository,
            StorageService storageService) {
        this.serviceRepository = serviceRepository;
        this.periodRepository = periodRepository;
        this.expenseRepository = expenseRepository;
        this.invoiceRepository = invoiceRepository;
        this.fileAssetRepository = fileAssetRepository;
        this.storageService = storageService;
    }

    /**
     * Bir fatura yüklemesini işler (atomik).
     *
     * @param serviceId  zorunlu — var olan servis id'si (yoksa {@link ResourceNotFoundException} → 404)
     * @param month      zorunlu — {@code YYYY-MM} (biçimsiz ise çağıran 400 üretir; burada da doğrulanır)
     * @param amount     opsiyonel — orijinal (döviz) tutar
     * @param currency   opsiyonel — para birimi (null ise TRY)
     * @param description opsiyonel — fatura notuna eklenecek serbest açıklama
     * @param eInvoice   true ise durum {@code E_INVOICE}, aksi halde {@code FOUND}
     * @param files      1..N dosya (her biri izinli tip + ≤ 10MB)
     * @param uploader   yükleyen kullanıcı (nullable)
     * @return yükleme özeti (invoice/expense id, durum, depolanan dosyalar)
     */
    @Transactional
    public InvoiceUploadResponse upload(Long serviceId,
            String month,
            BigDecimal amount,
            Currency currency,
            String description,
            boolean eInvoice,
            List<MultipartFile> files,
            AppUser uploader) {

        // --- 1) Girdi doğrulama ---------------------------------------------
        if (serviceId == null) {
            throw new StorageException("serviceId zorunludur.");
        }
        YearMonth ym = parseMonth(month);

        if (files == null || files.isEmpty()) {
            throw new StorageException("En az bir dosya yüklenmelidir.");
        }
        for (MultipartFile file : files) {
            validateFile(file);
        }

        com.ecommint.accounthr.domain.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Servis bulunamadı: id=" + serviceId));

        Currency effectiveCurrency = currency != null ? currency : Currency.TRY;

        // --- 2) Period (find-or-create) -------------------------------------
        Period period = resolveOrCreatePeriod(ym);

        // --- 3) Expense (find-or-create) ------------------------------------
        List<Expense> existing = expenseRepository.findByPeriodId(period.getId());
        Expense expense = existing.stream()
                .filter(e -> e.getService() != null
                        && service.getId().equals(e.getService().getId()))
                .findFirst()
                .orElse(null);
        boolean expenseCreated = false;
        if (expense == null) {
            expense = new Expense();
            expense.setService(service);
            expense.setPeriod(period);
            expense.setCard(service.getDefaultCard());
            expense.setAmount(amount);
            expense.setAmountTry(effectiveCurrency == Currency.TRY ? amount : null);
            expense.setCurrency(effectiveCurrency);
            expense.setInformational(false);
            expense = expenseRepository.save(expense);
            expenseCreated = true;
        }

        // --- 4) Invoice (find-or-create) ------------------------------------
        InvoiceStatus targetStatus = eInvoice ? InvoiceStatus.E_INVOICE : InvoiceStatus.FOUND;
        Provider provider = service.getProvider();

        Invoice invoice = invoiceRepository.findByExpenseId(expense.getId()).stream()
                .filter(i -> i.getStatus() == InvoiceStatus.EXPECTED)
                .findFirst()
                .orElse(null);
        boolean invoicePreexisting = invoice != null;
        if (invoice == null) {
            invoice = new Invoice();
            invoice.setExpense(expense);
        }
        invoice.setStatus(targetStatus);
        invoice.setProvider(provider);
        if (invoice.getInvoiceDate() == null) {
            invoice.setInvoiceDate(ym.atDay(1));
        }
        if (amount != null) {
            invoice.setAmount(amount);
        }
        invoice.setCurrency(effectiveCurrency);
        // FIX 2: Pre-existing invoice (ör. reconciliation/importer'ın yazdığı EXPECTED
        // satırı) bir not/path taşıyor olabilir. Bunu KÖRÜ KÖRÜNE ezme → yeni bilgiyi ekle.
        // Brand-new invoice'ta eskiden olduğu gibi doğrudan set edilir.
        invoice.setNote(mergeNote(invoicePreexisting ? invoice.getNote() : null,
                buildNote(description, files)));
        // flush ile invoice id'sini al (dosya depolama bağlamı için).
        invoice = invoiceRepository.saveAndFlush(invoice);

        // --- 5) Dosyaları depola (atomik: hata olursa yazılanları geri al) ---
        LocalDate invoiceDate = invoice.getInvoiceDate();

        // FIX 1: @Transactional commit, upload() döndükten SONRA proxy sınırında olur. Bu
        // yüzden commit-anı bir hata (ör. FileAsset insert'i / V9 sha256 unique index'i
        // flush/commit'te patlarsa, ya da DB hiccup) bu metottaki catch ile YAKALANMAZ ve
        // diske yazılmış fiziksel dosyalar DB satırı olmadan orphan kalır (sessiz sızıntı).
        // Çözüm: yazılan her yolu transaction-bound bir listeye kaydet ve afterCompletion ile
        // commit DIŞINDA HER sonuçta (rollback VEYA commit-failure) bu dosyaları sil.
        List<String> writtenPaths = registerCleanupSynchronization();

        List<InvoiceUploadResponse.StoredFileSummary> summaries = new ArrayList<>();
        // FIX 3: Tek istekte aynı SHA-256'ya sahip dosyalar — ikincisi DB findBySha256'yı
        // (ilki henüz flush edilmediğinden) geçer, diske yazılır ve V9 unique index commit'te
        // patlar (409 + orphan). Çözüm: batch İÇİNDE, dosyayı YAZMADAN ÖNCE SHA-256 ile dedup
        // et; aynı içerikli ikinci dosya için ne fiziksel dosya ne ikinci FileAsset oluştur —
        // zaten depolanmış FileAsset'in özetini tekrar kullan (orphan/409 yok).
        Set<String> seenSha = new HashSet<>();
        Map<String, InvoiceUploadResponse.StoredFileSummary> summaryBySha = new HashMap<>();
        try {
            for (MultipartFile file : files) {
                FileType type = detectFileType(file);
                byte[] bytes;
                try {
                    bytes = file.getBytes();
                } catch (IOException e) {
                    throw new StorageException("Yüklenen dosya okunamadı.", e);
                }
                String sha = sha256Hex(bytes);

                // Batch-içi duplicate: aynı içerik bu istekte zaten depolandı → tekrar yazma,
                // ikinci FileAsset oluşturma; mevcut özeti yeniden kullan.
                if (!seenSha.add(sha)) {
                    summaries.add(summaryBySha.get(sha));
                    continue;
                }

                StoredFile stored;
                try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
                    stored = storageService.store(
                            invoice.getId(),
                            invoiceDate,
                            service.getName(),
                            providerIdOf(provider),
                            invoice.getInvoiceNo(),
                            file.getOriginalFilename(),
                            in,
                            type);
                } catch (IOException e) {
                    throw new StorageException("Yüklenen dosya okunamadı.", e);
                }
                writtenPaths.add(stored.relativePath());

                FileAsset asset = new FileAsset();
                asset.setInvoice(invoice);
                asset.setFilePath(stored.relativePath());
                asset.setFileName(stored.fileName());
                asset.setFileType(type);
                asset.setMimeType(file.getContentType());
                asset.setSizeBytes(stored.sizeBytes());
                asset.setSha256(stored.sha256());
                asset.setUploadedBy(uploader);
                asset = fileAssetRepository.save(asset);

                InvoiceUploadResponse.StoredFileSummary summary =
                        new InvoiceUploadResponse.StoredFileSummary(
                                asset.getId(),
                                asset.getFileName(),
                                asset.getFilePath(),
                                asset.getFileType(),
                                asset.getSizeBytes());
                summaries.add(summary);
                summaryBySha.put(stored.sha256(), summary);
            }
        } catch (RuntimeException ex) {
            // Atomiklik (defense in depth): DB rollback olacak; ayrıca afterCompletion da
            // tetiklenir. Burada da best-effort temizleyip listeyi boşaltıyoruz ki
            // afterCompletion aynı dosyaları İKİNCİ kez silmeye çalışmasın (deletePhysical
            // zaten idempotent/NPE-safe; yine de gereksiz tekrar yapmıyoruz).
            for (String p : new ArrayList<>(writtenPaths)) {
                storageService.deletePhysical(p);
            }
            writtenPaths.clear();
            log.warn("Fatura yüklemesi başarısız; yazılan dosyalar temizlendi.", ex);
            throw ex;
        }

        return new InvoiceUploadResponse(
                invoice.getId(), expense.getId(), invoice.getStatus(),
                expenseCreated, summaries);
    }

    // ------------------------------------------------------------------------
    // Yardımcılar
    // ------------------------------------------------------------------------

    /**
     * FIX 1: Yazılan fiziksel dosyaları toplamak için transaction-bound bir liste döner ve
     * o listeyi temizleyen bir {@link TransactionSynchronization} kaydeder. {@code
     * afterCompletion}, commit DIŞINDA HER sonuçta ({@code status != STATUS_COMMITTED} —
     * rollback ya da commit-failure) listedeki dosyaları best-effort siler. Başarılı
     * commit'te ({@code STATUS_COMMITTED}) hiçbir dosya silinmez.
     *
     * <p>Aktif transaction yoksa (beklenmez; {@code @Transactional} altındayız) kayıt
     * yapılmaz ve metot içi catch tek savunma hattı olarak kalır.
     */
    private List<String> registerCleanupSynchronization() {
        List<String> writtenPaths = new ArrayList<>();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return writtenPaths;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return; // başarılı commit: dosyalar kalıcı, silme yok.
                }
                // Rollback VEYA commit-failure: orphan bırakma → yazılanları best-effort sil.
                // deletePhysical idempotent ve NPE-safe; metot içi catch zaten silmişse
                // (writtenPaths temizlendiyse) burada silinecek bir şey kalmaz.
                int deleted = 0;
                for (String p : writtenPaths) {
                    if (storageService.deletePhysical(p)) {
                        deleted++;
                    }
                }
                if (!writtenPaths.isEmpty()) {
                    log.warn("Transaction commit edilmedi (status={}); {} orphan dosya temizlendi.",
                            status, deleted);
                }
            }
        });
        return writtenPaths;
    }

    /**
     * FIX 2: Pre-existing invoice'un notunu korur. Eski not boş/null ise yeni notu döndürür;
     * doluysa {@code eski | yeni} olarak BİRLEŞTİRİR (veri kaybı yok). Yeni bilgi boşsa eski
     * not aynen korunur.
     */
    private String mergeNote(String existingNote, String newNote) {
        boolean hasOld = existingNote != null && !existingNote.isBlank();
        boolean hasNew = newNote != null && !newNote.isBlank();
        if (hasOld && hasNew) {
            return existingNote.trim() + " | " + newNote.trim();
        }
        if (hasOld) {
            return existingNote;
        }
        return hasNew ? newNote : null;
    }

    /** İçeriğin SHA-256 hex digesti (batch-içi dedup için; FileSystemStorageService ile aynı). */
    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new StorageException("SHA-256 algoritması bulunamadı.", e);
        }
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new StorageException("month zorunludur (YYYY-MM).");
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (RuntimeException ex) {
            throw new StorageException("month YYYY-MM biçiminde olmalıdır: " + month);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new StorageException("Boş dosya yüklenemez.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new StorageException("Dosya boyutu 10MB sınırını aşıyor: "
                    + file.getOriginalFilename());
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new StorageException("İzin verilmeyen dosya tipi: "
                    + file.getOriginalFilename() + " (izinli: PDF, XML, JPG, PNG)");
        }
    }

    private Period resolveOrCreatePeriod(YearMonth ym) {
        String code = ym.toString(); // YYYY-MM
        return periodRepository.findByCode(code).orElseGet(() -> {
            Period p = new Period();
            p.setYear(ym.getYear());
            p.setMonth(ym.getMonthValue());
            p.setCode(code);
            return periodRepository.save(p);
        });
    }

    /** Fatura notu: serbest açıklama + yüklenen dosya adları. */
    private String buildNote(String description, List<MultipartFile> files) {
        StringBuilder sb = new StringBuilder();
        if (description != null && !description.isBlank()) {
            sb.append(description.trim());
        }
        List<String> names = files.stream()
                .map(MultipartFile::getOriginalFilename)
                .filter(n -> n != null && !n.isBlank())
                .toList();
        if (!names.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" — ");
            }
            sb.append("Yüklenen dosyalar: ").append(String.join(", ", names));
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /** Uzantıdan dosya tipini belirler (PDF/XML; jpg/jpeg/png → OTHER görsel). */
    private FileType detectFileType(MultipartFile file) {
        String ext = extensionOf(file.getOriginalFilename());
        if (ext == null) {
            return FileType.OTHER;
        }
        return switch (ext) {
            case "pdf" -> FileType.PDF;
            case "xml" -> FileType.XML;
            default -> FileType.OTHER; // jpg/jpeg/png — görsel ek
        };
    }

    /** Dosya adından küçük-harf uzantı (noktasız); yoksa null. */
    private String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        String name = filename.trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static Long providerIdOf(Provider provider) {
        return provider != null ? provider.getId() : null;
    }
}
