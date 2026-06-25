package com.ecommint.accounthr.service.importer;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ecommint.accounthr.domain.Card;
import com.ecommint.accounthr.domain.Provider;
import com.ecommint.accounthr.domain.ServiceContact;
import com.ecommint.accounthr.domain.enums.ActiveState;
import com.ecommint.accounthr.domain.enums.Frequency;
import com.ecommint.accounthr.domain.enums.InvoiceSource;
import com.ecommint.accounthr.dto.importer.ServiceImportSummary;
import com.ecommint.accounthr.repository.CardRepository;
import com.ecommint.accounthr.repository.ExpenseRepository;
import com.ecommint.accounthr.repository.ProviderRepository;
import com.ecommint.accounthr.repository.ServiceContactRepository;
import com.ecommint.accounthr.repository.ServiceRepository;

/**
 * E2-02 — {@code 2026_Harcamalar.xlsx} içindeki {@code Servisler} master sheet'ini
 * {@code services} (+ {@code service_contacts}) tablolarına UPSERT eden importer.
 *
 * <p>E2-01 zaten ay sheet'lerinden minimal {@code Service} kayıtları üretmiştir
 * (frequency=AD_HOC, active_state=UNCERTAIN placeholder). Bu importer master sheet'ten
 * GERÇEK değerleri okuyup mevcut servisleri zenginleştirir; master'da olup expense'lerde
 * olmayan servisleri (ör. pasif) ise yeni oluşturur.
 *
 * <p>Eşleşme: normalize edilmiş Hizmet adı (trim + iç boşluk teke indir, büyük/küçük
 * harf duyarsız). Aynı normalize isimden birden fazla servis varsa (teorik olarak
 * Contabo varyantları gibi) default_card ile ayrıştırılır — fakat varyant isimleri
 * zaten farklı olduğundan ("Contabo VPS 10 SSD" vs "Contabo VPS 30 NVMe") bu durumda
 * her biri ayrı servis olarak kalır.
 *
 * <p>Idempotency: yeniden çalıştırma servisleri/provider'ları/contact'ları çiftlemez —
 * isim eşleşmesiyle yerinde günceller, aynı e-posta için ikinci contact eklemez.
 */
@Service
public class ServiceMasterImportService {

    private static final Logger log = LoggerFactory.getLogger(ServiceMasterImportService.class);

    private static final String SHEET_NAME = "Servisler";

    // Sheet kolon sırası (header row 0, data row 1+).
    private static final int COL_HIZMET = 0;        // Hizmet
    private static final int COL_SAGLAYICI = 1;      // Sağlayıcı
    private static final int COL_KART = 2;           // Kart
    private static final int COL_FREKANS = 3;        // Frekans
    private static final int COL_AKTIF = 4;          // Aktif
    private static final int COL_AKTIF_AYLAR = 5;    // Aktif Aylar
    private static final int COL_TUTAR = 6;          // Yaklaşık Tutar (TL)
    private static final int COL_EPOSTA = 7;         // Fatura E-posta
    private static final int COL_KAYNAK = 8;         // Fatura Kaynağı
    private static final int COL_NOTLAR = 9;         // Notlar

    private static final int LAST_COL = COL_NOTLAR;

    private final ProviderRepository providerRepository;
    private final ServiceRepository serviceRepository;
    private final CardRepository cardRepository;
    private final ServiceContactRepository serviceContactRepository;
    private final ExpenseRepository expenseRepository;

    public ServiceMasterImportService(ProviderRepository providerRepository,
                                      ServiceRepository serviceRepository,
                                      CardRepository cardRepository,
                                      ServiceContactRepository serviceContactRepository,
                                      ExpenseRepository expenseRepository) {
        this.providerRepository = providerRepository;
        this.serviceRepository = serviceRepository;
        this.cardRepository = cardRepository;
        this.serviceContactRepository = serviceContactRepository;
        this.expenseRepository = expenseRepository;
    }

    /**
     * {@code Servisler} sheet'ini okur ve servisleri UPSERT eder. Tek transaction;
     * idempotent. Sheet bulunamazsa boş özet döner (0 satır).
     */
    @Transactional
    public ServiceImportSummary importServicesSheet(InputStream xlsx) {
        // DataFormatter POI'de thread-safe değil; her çağrı için lokal örnek.
        DataFormatter dataFormatter = new DataFormatter();

        int rowsRead = 0;
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int contactsCreated = 0;
        int providersCreated = 0;
        int informationalCount = 0;
        // Master sheet'te görülen normalize isim seti (eşleşmeyen raporu için).
        Set<String> masterNamesNormalized = new HashSet<>();

        // Mevcut servisleri TEK SEFER yükle, normalize-lowercase isme göre indeksle.
        // findExistingService bu map'ten okur (per-row findAll() yok). Aynı run'da
        // oluşturulan yeni servisler map'e eklenir → tekrarlı isimler çiftlenmez.
        // findAll(Sort by id): aday listeleri DETERMİNİSTİK sıralanır. Aynı normalize
        // isimden birden fazla servis varsa (ör. E2-01'den çiftlenmiş "Zoom Workplace Pro")
        // findExistingService HER run'da AYNI adayı (en küçük id) seçer → idempotent;
        // contact ikinci servise yazılıp çiftlenmez.
        Map<String, List<com.ecommint.accounthr.domain.Service>> servicesByName = new HashMap<>();
        for (com.ecommint.accounthr.domain.Service s :
                serviceRepository.findAll(org.springframework.data.domain.Sort.by("id"))) {
            servicesByName
                    .computeIfAbsent(normalize(s.getName()).toLowerCase(Locale.ROOT),
                            k -> new ArrayList<>())
                    .add(s);
        }

        try (Workbook workbook = new XSSFWorkbook(xlsx)) {
            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                log.warn("'{}' sheet'i bulunamadı; import atlandı.", SHEET_NAME);
                return new ServiceImportSummary(0, 0, 0, 0, 0, 0, 0,
                        List.of(), List.of());
            }

            int lastRow = sheet.getLastRowNum();
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (isRowEmpty(row, dataFormatter)) {
                    skipped++;
                    continue;
                }

                String hizmet = normalize(getString(row, COL_HIZMET, dataFormatter));
                if (hizmet.isEmpty() || isFooterRow(row, dataFormatter)) {
                    // Hizmet adı yoksa ya da TOPLAM/footer satırıysa atla (savunmacı).
                    skipped++;
                    continue;
                }

                rowsRead++;

                String providerName = normalize(getString(row, COL_SAGLAYICI, dataFormatter));
                String cardLast4 = parseCardLast4(getString(row, COL_KART, dataFormatter));
                Frequency frequency = parseFrequency(getString(row, COL_FREKANS, dataFormatter));
                boolean infoFromFrekans = isInformationalFrekans(getString(row, COL_FREKANS, dataFormatter));
                ActiveState activeState = parseActiveState(getString(row, COL_AKTIF, dataFormatter));
                String activeMonths = blankToNull(getString(row, COL_AKTIF_AYLAR, dataFormatter));
                BigDecimal approxAmount = getDecimal(row, COL_TUTAR, dataFormatter);
                String email = blankToNull(getString(row, COL_EPOSTA, dataFormatter));
                String kaynakRaw = blankToNull(getString(row, COL_KAYNAK, dataFormatter));
                InvoiceSource invoiceSource = parseInvoiceSource(kaynakRaw);
                String notes = blankToNull(getString(row, COL_NOTLAR, dataFormatter));

                boolean informational = infoFromFrekans || mentionsIgnored(notes);
                if (informational) {
                    informationalCount++;
                }

                masterNamesNormalized.add(hizmet.toLowerCase(Locale.ROOT));

                Card card = cardLast4 != null
                        ? cardRepository.findByLastFour(cardLast4).orElse(null)
                        : null;

                // --- Provider çöz/oluştur ---
                ProviderResolution pr = resolveOrCreateProvider(providerName);
                Provider provider = pr.provider();
                if (pr.created()) {
                    providersCreated++;
                }

                // --- Service UPSERT ---
                String nameKey = hizmet.toLowerCase(Locale.ROOT);
                com.ecommint.accounthr.domain.Service service =
                        findExistingService(servicesByName.get(nameKey), card);
                boolean isNew = service == null;
                if (isNew) {
                    service = new com.ecommint.accounthr.domain.Service();
                    service.setName(hizmet);
                }
                service.setProvider(provider);
                service.setFrequency(frequency);
                service.setActiveState(activeState);
                service.setInformational(informational);
                service.setApproxAmountTry(approxAmount);
                if (invoiceSource != null) {
                    service.setInvoiceSource(invoiceSource);
                }
                service.setNotes(notes);
                service.setActiveMonths(activeMonths);
                if (card != null) {
                    service.setDefaultCard(card);
                }
                service = serviceRepository.save(service);

                if (isNew) {
                    created++;
                    // Yeni servisi map'e ekle: aynı isim aynı sheet'te tekrar gelirse
                    // ikinci satır UPDATE olur, yeni satır AÇILMAZ (idempotency).
                    servicesByName
                            .computeIfAbsent(nameKey, k -> new ArrayList<>())
                            .add(service);
                } else {
                    updated++;
                }

                // --- ServiceContact UPSERT (e-posta varsa) ---
                if (email != null) {
                    boolean contactCreated = upsertContact(service, email, kaynakRaw);
                    if (contactCreated) {
                        contactsCreated++;
                    }
                }
            }
        } catch (IOException e) {
            throw new ExcelImportException("Servisler sheet'i okunamadı.", e);
        }

        // --- Eşleşmeyen raporu ---
        List<String> masterWithoutExpenses = computeMasterWithoutExpenses(masterNamesNormalized);
        List<String> expensesWithoutMaster =
                computeExpensesWithoutMaster(masterNamesNormalized);

        return new ServiceImportSummary(rowsRead, created, updated, skipped,
                contactsCreated, providersCreated, informationalCount,
                masterWithoutExpenses, expensesWithoutMaster);
    }

    // ---------------------------------------------------------------------------
    // Eşleşmeyen raporu
    // ---------------------------------------------------------------------------

    /**
     * Bu import'taki master sheet'te var AMA hiç expense satırı olmayan servis isimleri
     * (sıralı, distinct). Tüm DB servisleri değil — yalnızca bu run'da görülen master
     * isimleriyle kesişim alınır (E2-01'in expense'siz servisleri dışarıda kalır).
     */
    private List<String> computeMasterWithoutExpenses(Set<String> masterNamesNormalized) {
        List<Long> ids = expenseRepository.findServiceIdsWithoutExpenses();
        Set<String> names = new TreeSet<>();
        for (com.ecommint.accounthr.domain.Service s : serviceRepository.findAllById(ids)) {
            String key = normalize(s.getName()).toLowerCase(Locale.ROOT);
            if (masterNamesNormalized.contains(key)) {
                names.add(s.getName());
            }
        }
        return new ArrayList<>(names);
    }

    /**
     * Expense'lerde geçen ama bu import'taki master isim setinde olmayan servis isimleri.
     * Karşılaştırma normalize edilmiş (lowercase) isim üzerinden; rapor orijinal ismi verir.
     */
    private List<String> computeExpensesWithoutMaster(Set<String> masterNamesNormalized) {
        Set<String> result = new TreeSet<>();
        for (String name : expenseRepository.findDistinctServiceNames()) {
            if (name == null) {
                continue;
            }
            String key = normalize(name).toLowerCase(Locale.ROOT);
            if (!masterNamesNormalized.contains(key)) {
                result.add(name);
            }
        }
        return new ArrayList<>(result);
    }

    // ---------------------------------------------------------------------------
    // UPSERT yardımcıları
    // ---------------------------------------------------------------------------

    /**
     * Aynı normalize isimli servis adaylarından doğrusunu seçer.
     * Birden fazla aday varsa (teorik Contabo varyantları gibi) default_card ile
     * ayrıştırır (kart eşleşeni tercih edilir); kart yoksa ilk aday döner. Aday
     * yoksa null (→ yeni oluşturulur).
     */
    private com.ecommint.accounthr.domain.Service findExistingService(
            List<com.ecommint.accounthr.domain.Service> candidates, Card card) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (card != null && card.getId() != null) {
            for (com.ecommint.accounthr.domain.Service s : candidates) {
                if (s.getDefaultCard() != null
                        && card.getId().equals(s.getDefaultCard().getId())) {
                    return s;
                }
            }
        }
        return candidates.get(0);
    }

    private record ProviderResolution(Provider provider, boolean created) {
    }

    private ProviderResolution resolveOrCreateProvider(String name) {
        String providerName = name == null || name.isEmpty() ? "(Bilinmeyen)" : name;
        return providerRepository.findByNameIgnoreCase(providerName)
                .map(p -> new ProviderResolution(p, false))
                .orElseGet(() -> {
                    Provider p = new Provider();
                    p.setName(providerName);
                    return new ProviderResolution(providerRepository.save(p), true);
                });
    }

    /**
     * Servise verilen e-posta için contact oluşturur. Aynı e-posta zaten varsa
     * (büyük/küçük harf duyarsız) source/primary güncellenir ama YENİ kayıt
     * açılmaz (idempotent). {@code true} dönerse yeni contact yaratılmıştır.
     */
    private boolean upsertContact(com.ecommint.accounthr.domain.Service service,
                                  String email, String sourceRaw) {
        List<ServiceContact> existing = service.getId() != null
                ? serviceContactRepository.findByServiceId(service.getId())
                : List.of();
        for (ServiceContact c : existing) {
            if (c.getEmail() != null && c.getEmail().equalsIgnoreCase(email)) {
                // Mevcut: kaynağı tazele, primary işaretle. Yeni kayıt açma.
                if (sourceRaw != null) {
                    c.setSource(sourceRaw);
                }
                c.setPrimary(true);
                serviceContactRepository.save(c);
                return false;
            }
        }
        ServiceContact contact = new ServiceContact();
        contact.setService(service);
        contact.setEmail(email);
        contact.setSource(sourceRaw);
        contact.setPrimary(true);
        serviceContactRepository.save(contact);
        return true;
    }

    // ---------------------------------------------------------------------------
    // Enum eşleyiciler (paket-içi static — birim test edilebilir)
    // ---------------------------------------------------------------------------

    /**
     * Frekans metni → {@link Frequency}.
     * Aylık / Aylık (bilgi amaçlı) → MONTHLY; Yıllık → YEARLY;
     * Kullanım bazlı → USAGE_BASED; Ad-hoc / Tek sefer → AD_HOC.
     * Bilinmeyen/boş → AD_HOC (NOT NULL kolon için güvenli varsayılan).
     */
    static Frequency parseFrequency(String raw) {
        if (raw == null) {
            return Frequency.AD_HOC;
        }
        String v = raw.trim().toLowerCase(Locale.forLanguageTag("tr"));
        if (v.isEmpty()) {
            return Frequency.AD_HOC;
        }
        if (v.startsWith("aylık")) {
            // "Aylık" ve "Aylık (bilgi amaçlı)" → MONTHLY.
            return Frequency.MONTHLY;
        }
        if (v.startsWith("yıllık")) {
            return Frequency.YEARLY;
        }
        if (v.startsWith("kullanım")) {
            return Frequency.USAGE_BASED;
        }
        if (v.startsWith("ad-hoc") || v.startsWith("ad hoc") || v.contains("tek sefer")) {
            return Frequency.AD_HOC;
        }
        return Frequency.AD_HOC;
    }

    /** Frekans "Aylık (bilgi amaçlı)" ise true → service.informational. */
    static boolean isInformationalFrekans(String raw) {
        if (raw == null) {
            return false;
        }
        String v = raw.trim().toLowerCase(Locale.forLanguageTag("tr"));
        return v.startsWith("aylık") && v.contains("bilgi");
    }

    /**
     * Aktif metni → {@link ActiveState}.
     * Evet / Evet (yıllık) → YES; "Hayır..." → NO; Belirsiz → UNCERTAIN.
     * Bilinmeyen/boş → UNCERTAIN.
     */
    static ActiveState parseActiveState(String raw) {
        if (raw == null) {
            return ActiveState.UNCERTAIN;
        }
        String v = raw.trim().toLowerCase(Locale.forLanguageTag("tr"));
        if (v.isEmpty()) {
            return ActiveState.UNCERTAIN;
        }
        if (v.startsWith("evet")) {
            return ActiveState.YES;
        }
        if (v.startsWith("hayır")) {
            return ActiveState.NO;
        }
        if (v.startsWith("belirsiz")) {
            return ActiveState.UNCERTAIN;
        }
        return ActiveState.UNCERTAIN;
    }

    /**
     * Fatura Kaynağı metni → {@link InvoiceSource}.
     * Servis paneli → SERVICE_PANEL; E-posta → EMAIL; e-Fatura → E_INVOICE;
     * Drive waiting → DRIVE_WAITING. Boş/bilinmeyen → null (kaynak set edilmez).
     */
    static InvoiceSource parseInvoiceSource(String raw) {
        if (raw == null) {
            return null;
        }
        String v = raw.trim().toLowerCase(Locale.forLanguageTag("tr"));
        if (v.isEmpty()) {
            return null;
        }
        if (v.startsWith("servis paneli") || v.contains("panel")) {
            return InvoiceSource.SERVICE_PANEL;
        }
        if (v.startsWith("e-fatura") || v.startsWith("efatura") || v.startsWith("e fatura")) {
            return InvoiceSource.E_INVOICE;
        }
        if (v.startsWith("drive")) {
            return InvoiceSource.DRIVE_WAITING;
        }
        if (v.startsWith("e-posta") || v.startsWith("eposta") || v.startsWith("e posta")
                || v.startsWith("mail")) {
            return InvoiceSource.EMAIL;
        }
        return null;
    }

    /** Notlar "Ignored" geçiyorsa true → bilgi amaçlı işaretle. */
    private boolean mentionsIgnored(String notes) {
        if (notes == null) {
            return false;
        }
        return notes.toLowerCase(Locale.forLanguageTag("tr")).contains("ignored");
    }

    // ---------------------------------------------------------------------------
    // Hücre parser'ları / yardımcılar
    // ---------------------------------------------------------------------------

    private String getString(Row row, int col, DataFormatter dataFormatter) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(col);
        if (cell == null) {
            return "";
        }
        return dataFormatter.formatCellValue(cell);
    }

    private BigDecimal getDecimal(Row row, int col, DataFormatter dataFormatter) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(col);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String raw = getString(row, col, dataFormatter).trim();
        if (raw.isEmpty()) {
            return null;
        }
        String cleaned = raw.replace("₺", "").replace("$", "").replace("€", "")
                .replace(" ", "");
        if (cleaned.contains(",") && cleaned.contains(".")) {
            // TR biçim: nokta binlik, virgül ondalık (1.234,56).
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else if (cleaned.contains(",")) {
            // Yalnızca virgül → ondalık ayraç (1234,56).
            cleaned = cleaned.replace(",", ".");
        } else if (looksLikeThousandsDots(cleaned)) {
            // Yalnızca nokta(lar) ve son grup 3 hane → TR binlik ayraç (1.250 → 1250).
            cleaned = cleaned.replace(".", "");
        }
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Yaklaşık tutar parse edilemedi: '{}'", raw);
            return null;
        }
    }

    /**
     * Yalnızca nokta(lar) içeren ve TR binlik ayracı gibi görünen sayı mı?
     * Her nokta-sonrası grup TAM 3 hane ve ilk grup 1-3 hane olmalı (ör. "1.250",
     * "1.234.567"). "1.25" (ondalık) ya da "12.3456" buraya GİRMEZ → ondalık kalır.
     */
    private boolean looksLikeThousandsDots(String cleaned) {
        if (cleaned == null || !cleaned.contains(".")) {
            return false;
        }
        return cleaned.matches("\\d{1,3}(\\.\\d{3})+");
    }

    /** "****3800" → "3800". Boş/eksikse null. */
    private String parseCardLast4(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        return digits.length() > 4 ? digits.substring(digits.length() - 4) : digits;
    }

    private boolean isRowEmpty(Row row, DataFormatter dataFormatter) {
        if (row == null) {
            return true;
        }
        for (int c = 0; c <= LAST_COL; c++) {
            if (!getString(row, c, dataFormatter).trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Savunmacı footer/total tespiti: herhangi bir hücrede "TOPLAM" geçiyorsa. */
    private boolean isFooterRow(Row row, DataFormatter dataFormatter) {
        for (int c = 0; c <= LAST_COL; c++) {
            String v = getString(row, c, dataFormatter)
                    .toUpperCase(Locale.forLanguageTag("tr"));
            if (v.contains("TOPLAM")) {
                return true;
            }
        }
        return false;
    }

    /** Trim + iç boşlukları teke indir. Null → "". */
    private String normalize(String s) {
        if (s == null) {
            return "";
        }
        return s.trim().replaceAll("\\s+", " ");
    }

    private String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
