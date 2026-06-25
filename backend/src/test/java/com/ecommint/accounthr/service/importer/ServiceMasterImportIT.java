package com.ecommint.accounthr.service.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.ecommint.accounthr.AbstractDataCleanupIT;
import com.ecommint.accounthr.domain.AppUser;
import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Expense;
import com.ecommint.accounthr.domain.Period;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.ServiceContact;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Currency;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceSource;
import com.ecommint.accounthr.domain.enums.UserRole;
import com.ecommint.accounthr.dto.importer.ServiceImportSummary;
import com.ecommint.accounthr.repository.AppUserRepository;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.PeriodRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceContactRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * E2-02 — {@link ServiceMasterImportService} davranış testi (H2, paylaşılan context).
 *
 * <p>{@link AbstractDataCleanupIT}'i extend eder → her test öncesi/sonrası tüm tablolar
 * FK-güvenli temizlenir, böylece test sırasından bağımsız geçer (CI alphabetical).
 *
 * <p>POI ile in-test bir {@code Servisler} sheet'i üretir; gerçek
 * {@code 2026_Harcamalar.xlsx} dosyasına BAĞIMLI DEĞİLDİR. Senaryolar:
 * <ul>
 *   <li>MONTHLY aktif (e-posta + Servis paneli kaynağı)</li>
 *   <li>YEARLY (Evet yıllık)</li>
 *   <li>"Aylık (bilgi amaçlı)" → informational + Ignored notu</li>
 *   <li>"Hayır" → NO inaktif (e-posta yok)</li>
 *   <li>Önceden seed'lenmiş bir servisle eşleşen satır → UPDATE (duplicate DEĞİL)</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class ServiceMasterImportIT extends AbstractDataCleanupIT {

    @Autowired private ServiceMasterImportService importService;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private ProviderRepository providerRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private ServiceContactRepository contactRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private PeriodRepository periodRepository;
    @Autowired private AppUserRepository userRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void seed() {
        seedCard("Akbank", "3800");
        seedCard("YKB", "3909");
        seedCard("Ziraat", "9164");

        // E2-01 tarzı önceden var olan minimal servis: master'da "Claude AI" satırı
        // ile eşleşecek (UPDATE kanıtı). Bu servisin BİR expense'i var → master
        // eşleşince expensesWithoutMaster'da YER ALMAZ.
        Provider anthropic = new Provider();
        anthropic.setName("Anthropic");
        providerRepository.save(anthropic);

        com.ecommint.accounthr.domain.Service claude = new com.ecommint.accounthr.domain.Service();
        claude.setName("Claude AI");
        claude.setProvider(anthropic);
        claude.setFrequency(Frequency.AD_HOC);          // placeholder; master MONTHLY yapacak
        claude.setActiveState(ActiveState.UNCERTAIN);   // placeholder; master YES yapacak
        claude.setInformational(false);
        serviceRepository.save(claude);

        // "Claude AI" için bir expense (period gerekli).
        Period p = new Period();
        p.setYear(2026);
        p.setMonth(3);
        p.setCode("2026-03");
        periodRepository.save(p);

        Expense e = new Expense();
        e.setService(claude);
        e.setPeriod(p);
        e.setAmount(new BigDecimal("20"));
        e.setCurrency(Currency.USD);
        expenseRepository.save(e);

        // E2-01 expense'i olup master'da OLMAYAN bir servis (Provider X) → master
        // import sonrası expensesWithoutMaster listesinde görünmeli.
        Provider providerX = new Provider();
        providerX.setName("Provider X");
        providerRepository.save(providerX);

        com.ecommint.accounthr.domain.Service orphan = new com.ecommint.accounthr.domain.Service();
        orphan.setName("Orphan Service");
        orphan.setProvider(providerX);
        orphan.setFrequency(Frequency.AD_HOC);
        orphan.setActiveState(ActiveState.UNCERTAIN);
        orphan.setInformational(false);
        serviceRepository.save(orphan);

        Expense e2 = new Expense();
        e2.setService(orphan);
        e2.setPeriod(p);
        e2.setAmount(new BigDecimal("10"));
        e2.setCurrency(Currency.USD);
        expenseRepository.save(e2);
    }

    private void seedCard(String bank, String last4) {
        Card c = new Card();
        c.setBank(bank);
        c.setLastFour(last4);
        cardRepository.save(c);
    }

    /**
     * Test workbook'u: {@code Servisler} sheet'i (10 kolon).
     * <pre>
     * row0: header
     * row1: Claude AI / Anthropic / 3909 / Aylık / Evet / "2026-01, 2026-02, 2026-03"
     *       / 700 / billing@example.com / Servis paneli / "İade patterni var"   (UPDATE)
     * row2: GoDaddy / GoDaddy / 3800 / Yıllık / Evet (yıllık) / "2026-02"
     *       / 500 / (boş) / (boş) / (boş)                                        (CREATE, YEARLY)
     * row3: Allianz Sigorta / Allianz / 9164 / Aylık (bilgi amaçlı) / Evet
     *       / "2026-01" / 1200 / (boş) / (boş) / "Bilgi amaçlı (Ignored)"        (CREATE, info)
     * row4: Eski Servis / Eski / (boş) / Aylık / Hayır / (boş) / (boş) / (boş)
     *       / (boş) / (boş)                                                      (CREATE, NO)
     * </pre>
     */
    private byte[] buildServisler() {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Servisler");

            Row header = sheet.createRow(0);
            String[] headers = {"Hizmet", "Sağlayıcı", "Kart", "Frekans", "Aktif",
                    "Aktif Aylar", "Yaklaşık Tutar (TL)", "Fatura E-posta",
                    "Fatura Kaynağı", "Notlar"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Claude AI");
            r1.createCell(1).setCellValue("Anthropic");
            r1.createCell(2).setCellValue("****3909");
            r1.createCell(3).setCellValue("Aylık");
            r1.createCell(4).setCellValue("Evet");
            r1.createCell(5).setCellValue("2026-01, 2026-02, 2026-03");
            r1.createCell(6).setCellValue(700.0);
            r1.createCell(7).setCellValue("billing@example.com");
            r1.createCell(8).setCellValue("Servis paneli");
            r1.createCell(9).setCellValue("İade patterni var");

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("GoDaddy");
            r2.createCell(1).setCellValue("GoDaddy");
            r2.createCell(2).setCellValue("****3800");
            r2.createCell(3).setCellValue("Yıllık");
            r2.createCell(4).setCellValue("Evet (yıllık)");
            r2.createCell(5).setCellValue("2026-02");
            r2.createCell(6).setCellValue(500.0);
            // e-posta/kaynak/notlar boş

            Row r3 = sheet.createRow(3);
            r3.createCell(0).setCellValue("Allianz Sigorta");
            r3.createCell(1).setCellValue("Allianz");
            r3.createCell(2).setCellValue("****9164");
            r3.createCell(3).setCellValue("Aylık (bilgi amaçlı)");
            r3.createCell(4).setCellValue("Evet");
            r3.createCell(5).setCellValue("2026-01");
            r3.createCell(6).setCellValue(1200.0);
            r3.createCell(9).setCellValue("Bilgi amaçlı (Ignored)");

            Row r4 = sheet.createRow(4);
            r4.createCell(0).setCellValue("Eski Servis");
            r4.createCell(1).setCellValue("Eski");
            r4.createCell(3).setCellValue("Aylık");
            r4.createCell(4).setCellValue("Hayır");
            // kart/aylar/tutar/eposta/kaynak/notlar boş

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void importsMapsFieldsUpsertsAndReports() {
        long servicesBefore = serviceRepository.count(); // 2 seed (Claude AI, Orphan Service)
        assertThat(servicesBefore).isEqualTo(2);

        ServiceImportSummary summary = importService.importServicesSheet(
                new ByteArrayInputStream(buildServisler()));

        // 4 satır okundu: 1 update (Claude AI) + 3 create (GoDaddy, Allianz, Eski Servis).
        assertThat(summary.rowsRead()).isEqualTo(4);
        assertThat(summary.updated()).isEqualTo(1);
        assertThat(summary.created()).isEqualTo(3);
        assertThat(summary.informationalCount()).isEqualTo(1); // Allianz
        assertThat(summary.contactsCreated()).isEqualTo(1);    // sadece Claude AI'ın e-postası

        // Toplam servis: 2 seed + 3 yeni = 5 (Claude AI çiftlenmedi → UPDATE).
        assertThat(serviceRepository.count()).isEqualTo(5);

        // --- Claude AI UPDATE: placeholder → gerçek değerler ---
        com.ecommint.accounthr.domain.Service claude = findService("Claude AI");
        assertThat(claude.getFrequency()).isEqualTo(Frequency.MONTHLY);
        assertThat(claude.getActiveState()).isEqualTo(ActiveState.YES);
        assertThat(claude.isInformational()).isFalse();
        assertThat(claude.getApproxAmountTry()).isEqualByComparingTo(new BigDecimal("700"));
        assertThat(claude.getInvoiceSource()).isEqualTo(InvoiceSource.SERVICE_PANEL);
        assertThat(claude.getActiveMonths()).isEqualTo("2026-01, 2026-02, 2026-03");
        assertThat(claude.getNotes()).isEqualTo("İade patterni var");
        // defaultCard lazy proxy: @SpringBootTest'te aktif session yok, alanı dolaşmak
        // yerine ID'yi 3909 kartının ID'siyle karşılaştır.
        Long expectedCardId = cardRepository.findByLastFour("3909").orElseThrow().getId();
        assertThat(claude.getDefaultCard()).isNotNull();
        assertThat(claude.getDefaultCard().getId()).isEqualTo(expectedCardId);

        // Contact: e-posta + Servis paneli kaynağı + primary.
        List<ServiceContact> contacts = contactRepository.findByServiceId(claude.getId());
        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).getEmail()).isEqualTo("billing@example.com");
        assertThat(contacts.get(0).getSource()).isEqualTo("Servis paneli");
        assertThat(contacts.get(0).isPrimary()).isTrue();

        // --- GoDaddy CREATE: YEARLY ---
        com.ecommint.accounthr.domain.Service godaddy = findService("GoDaddy");
        assertThat(godaddy.getFrequency()).isEqualTo(Frequency.YEARLY);
        assertThat(godaddy.getActiveState()).isEqualTo(ActiveState.YES);
        assertThat(godaddy.isInformational()).isFalse();

        // --- Allianz Sigorta CREATE: informational + Ignored ---
        com.ecommint.accounthr.domain.Service allianz = findService("Allianz Sigorta");
        assertThat(allianz.getFrequency()).isEqualTo(Frequency.MONTHLY);
        assertThat(allianz.isInformational()).isTrue();
        assertThat(allianz.getNotes()).contains("Ignored");

        // --- Eski Servis CREATE: NO inactive ---
        com.ecommint.accounthr.domain.Service eski = findService("Eski Servis");
        assertThat(eski.getActiveState()).isEqualTo(ActiveState.NO);

        // --- Eşleşmeyen raporu ---
        // master'da var ama expense'i olmayan: GoDaddy, Allianz Sigorta, Eski Servis.
        assertThat(summary.masterWithoutExpenses())
                .containsExactlyInAnyOrder("GoDaddy", "Allianz Sigorta", "Eski Servis");
        // expense'i olup master'da olmayan: Orphan Service (Claude AI master'da var).
        assertThat(summary.expensesWithoutMaster())
                .containsExactly("Orphan Service");
    }

    @Test
    void reRunIsIdempotent() {
        byte[] wb = buildServisler();

        ServiceImportSummary first = importService.importServicesSheet(new ByteArrayInputStream(wb));
        assertThat(first.created()).isEqualTo(3);
        assertThat(first.updated()).isEqualTo(1);
        long servicesAfterFirst = serviceRepository.count();
        long contactsAfterFirst = contactRepository.count();

        // İkinci kez aynı dosya → 0 yeni servis, 0 yeni contact, hepsi UPDATE.
        ServiceImportSummary second = importService.importServicesSheet(new ByteArrayInputStream(wb));
        assertThat(second.created()).isEqualTo(0);
        assertThat(second.updated()).isEqualTo(4);
        assertThat(second.contactsCreated()).isEqualTo(0);

        assertThat(serviceRepository.count()).isEqualTo(servicesAfterFirst);
        assertThat(contactRepository.count()).isEqualTo(contactsAfterFirst);
    }

    /**
     * Regresyon: GERÇEK HTTP yolunda import, kimliği doğrulanmış bir admin
     * SecurityContext'i altında çalışır. JPA auditing {@code preUpdate} callback'i
     * (flush sırasında) {@code SecurityAuditorAware.getCurrentAuditor()}'ı çağırır;
     * bu da e-posta→id sorgusu yapar. Sorgu default {@code FlushModeType.AUTO} ile
     * çalışırsa Hibernate, sorgudan önce bekleyen kirli entity'leri TEKRAR flush eder
     * → tekrar {@code preUpdate} → tekrar sorgu → {@link StackOverflowError} (500).
     *
     * <p>H2 davranış testleri SecurityContext kurmadığı için bu yolu HİÇ tetiklemezdi
     * (auditor erkenden {@code Optional.empty()} döner). Bu test, gerçek-Postgres'te
     * görülen 500'ü H2'de yeniden üretir: import bir auth context altında patlamamalı.
     */
    @Test
    void importUnderAuthenticatedContextDoesNotRecurseOnAuditFlush() {
        AppUser admin = new AppUser();
        admin.setEmail("admin-it@e-commint.com");
        admin.setFullName("IT Admin");
        admin.setRole(UserRole.ADMIN);
        admin.setActive(true);
        userRepository.save(admin);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        admin.getEmail(), "n/a",
                        AuthorityUtils.createAuthorityList("ROLE_ADMIN")));

        // Auditor lookup flush sırasında çalışır; fix yoksa burada StackOverflowError.
        ServiceImportSummary summary = importService.importServicesSheet(
                new ByteArrayInputStream(buildServisler()));

        assertThat(summary.rowsRead()).isEqualTo(4);
        assertThat(serviceRepository.count()).isEqualTo(5);
    }

    /**
     * Regresyon: aynı normalize isimden DB'de İKİ servis varsa (E2-01'den çiftlenmiş
     * gerçek veri — ör. iki "Zoom Workplace Pro"), importer her run'da AYNI servisi
     * seçmeli (deterministik id sırası) ki contact ikinci servise yazılıp çiftlenmesin.
     * Aksi halde tekrar çalıştırma her seferinde yeni bir contact yaratırdı (idempotency
     * ihlali; gerçek-Postgres'te {@code contactsCreated=1} ikinci run'da görülmüştü).
     */
    @Test
    void duplicateNamedServiceContactStaysIdempotentAcrossRuns() {
        // "Claude AI" zaten seed'de var; aynı isimden İKİNCİ bir servis ekle (çift).
        Provider anthropic2 = providerRepository.findByNameIgnoreCase("Anthropic").orElseThrow();
        com.ecommint.accounthr.domain.Service claudeDup = new com.ecommint.accounthr.domain.Service();
        claudeDup.setName("Claude AI");
        claudeDup.setProvider(anthropic2);
        claudeDup.setFrequency(Frequency.AD_HOC);
        claudeDup.setActiveState(ActiveState.UNCERTAIN);
        claudeDup.setInformational(false);
        serviceRepository.save(claudeDup);

        byte[] wb = buildServisler();

        ServiceImportSummary first = importService.importServicesSheet(new ByteArrayInputStream(wb));
        assertThat(first.contactsCreated()).isEqualTo(1); // Claude AI e-postası → 1 contact
        long contactsAfterFirst = contactRepository.count();

        ServiceImportSummary second = importService.importServicesSheet(new ByteArrayInputStream(wb));
        assertThat(second.contactsCreated()).isEqualTo(0); // çift servise YENİ contact açılmaz
        assertThat(contactRepository.count()).isEqualTo(contactsAfterFirst);
    }

    private com.ecommint.accounthr.domain.Service findService(String name) {
        return serviceRepository.findAll().stream()
                .filter(s -> name.equals(s.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Servis bulunamadı: " + name));
    }
}
