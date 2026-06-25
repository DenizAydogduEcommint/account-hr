# [E2-01] Excel ay sheet'lerini (12 kolon) expenses tablosuna aktaran importer

| Alan | Değer |
|------|-------|
| Epic | E2 — Veri Migrasyonu |
| Sprint | Sprint 2 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-02 |
| Etiketler | backend, migration, excel, import |

## Amaç
Mevcut `2026_Harcamalar.xlsx` dosyasındaki her ay sheet'inin (Ocak/Şubat/Mart/Nisan) 12 kolonlu harcama satırlarını yeni sistemin `expenses` tablosuna sadık şekilde aktarmak.

## Açıklama / Bağlam
Bugüne kadar tüm veri Excel'de tutuldu. Yeni sisteme geçişte bu geçmiş kaybolmamalı. Her ay bir sheet ve her satır bir harcama (transaction). 12 kolon: Tarih, Hizmet, Sağlayıcı, Tutar, Para Birimi, TL Karşılığı, Kart, Kullanan Takım, Amaç, Muhasebe E-posta, Fatura Durumu, Fatura Notu.

Bu importer sheet'leri tarar, satırları okur, ilgili period/card/service/provider kayıtlarına bağlar ve `expenses` (+ taslak `invoices`) oluşturur. Bilgi amaçlı bölümler (Multinet, Allianz sağlık sigortası — "Ignored") ve TOPLAM satırları doğru ele alınmalı.

## Kabul Kriterleri (DOD)
- [x] Excel dosyası okunup her ay sheet'i ayrı işleniyor (Apache POI 5.4.1)
- [x] Her harcama satırı bir `expense`'e dönüşüyor; 12 kolon doğru eşleniyor (gerçek veride 101 satır)
- [x] Tarih parse ediliyor (boş→null); tutar/TL karşılığı sayısal (BigDecimal, negatif iade korunur)
- [x] Kart son-4 (****3800/3909/9164) `card`'a bağlanıyor; boş→null
- [x] Para birimi `currency` enum'a (TL→TRY/USD/EUR)
- [x] Hizmet+Sağlayıcı service/provider'a eşlenir; yoksa minimal oluşturulur (E2-02 zenginleştirir)
- [x] **TOPLAM satırları atlanır** (gerçek veride 10 TOPLAM atlandı)
- [x] Bilgi bölümleri (Multinet/Allianz sağlık) `informational=true` + "Ignored" (Şubat: 23 info doğrulandı)
- [x] period yoksa oluşturuluyor (2026-01..2026-04)
- [x] Özet rapor: sheet başına okunan/aktarılan/atlanan (ImportSummary)

## Alt Görevler
- [ ] Excel okuma (Apache POI öneri — Java tarafı) ve sheet keşfi
- [ ] Satır parser: 12 kolon → DTO; TOPLAM ve boş satır tespiti
- [ ] Tarih/para/kart/para-birimi dönüştürücüler
- [ ] Service/provider eşleme (isim normalizasyonu)
- [ ] Bilgi bölümü (Ignored) tespiti ve işaretleme
- [ ] `expense` (+ taslak invoice) kayıtlarını yaz
- [ ] Özet rapor üretimi

## Teknik Notlar
- Java tarafında Apache POI; alternatif olarak ayrı bir Python script (openpyxl) ile bir kerelik import de olabilir — karar verilmeli (öneri: Java importer, tekrar çalıştırılabilir olsun)
- Hizmet adları sheet'te serbest metin; service eşlemesi için normalizasyon (trim, lowercase, bilinen alias'lar)
- Fatura Durumu ve Fatura Notu bu görevde okunur ama renk→enum eşlemesi E2-04'te, dosya path eşlemesi E2-03'te netleşir
- Idempotency E2-05'te ele alınacak; bu görevde en azından (period, satır-hash) ile çift kayıt önlenmeli

## Açık Sorular / Riskler — ÇÖZÜLDÜ (gerçek veriyle doğrulandı)
- ~~TOPLAM tespiti kırılgan mı?~~ → `TOPLAM:` içeren hücre (TR-locale) ile tespit; gerçek veride 10/10 doğru atlandı.
- ~~Sheet→period eşleme?~~ → Sabit tablo (Ocak→2026-01 … Nisan→2026-04).
- ~~Boş sağlayıcı/hizmet?~~ → Sağlayıcı boş → `(Bilinmeyen)`; service minimal oluşturulur.

## Tamamlanma Kaydı
- Durum: ✅ Tamamlandı — 2026-06-25
- YouTrack: IK-232 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend)
- Üretilenler: ExcelImportService (POI), AdminImportController (`POST /api/v1/admin/imports/excel`, ADMIN), ImportSummary, Flyway V6 (`expenses.source_row_hash` + amount/transaction_date NOT NULL kaldırıldı), ExcelImportException
- **Gerçek veri doğrulaması (lokal PG14)**: `2026_Harcamalar.xlsx` import → **101 expense** (Ocak 11, Şubat 51=28 op+23 info, Mart 23, Nisan 16), 101 taslak invoice, 32 service + 24 provider oluştu, 7 iade; TOPLAM(10)+boş(2910) atlandı; **idempotency: 2. import = 0 yeni**. Şubat informational satırları (Sağlık 19 + Multinet 3 + Allianz 1) Excel kaynağıyla birebir doğrulandı.
- Test: `./mvnw test` 50/50 (2 importer testi: imported/skip/informational/idempotency)
- **CI: GitHub Actions YEŞİL** (gh ile teyit edildi). Push sonrası 4 CI fix gerekti: (1) test izolasyonu `AbstractDataCleanupIT` — paylaşılan H2'de FK ihlali (CI test sırası); (2) frontend package-lock yeniden üretimi; (3) workflow `npm ci || npm install` fallback; (4) Dockerfile aynı fallback. Dersler CLAUDE.md "Code Review Loop" adım 7'ye işlendi.
- Review: 3 robustness bulgusu düzeltildi (DataFormatter per-call thread-safety, section-header gevşetildi, duplicate-skip log.warn). Codex kotası dolu → Claude code-reviewer fallback.
- **expenses/ Drive aynasına dokunulmadı** (Excel read-only upload).
- Sapma: E1-02'deki `Expense.amount` ve `transaction_date` NOT NULL → nullable yapıldı (V6); TL ödeme/bekleniyor satırları için gerekli.
