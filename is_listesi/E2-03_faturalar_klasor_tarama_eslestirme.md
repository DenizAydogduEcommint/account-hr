# [E2-03] faturalar/ klasörlerini tara, invoice kayıtları oluştur ve dosyaları eşle

| Alan | Değer |
|------|-------|
| Epic | E2 — Veri Migrasyonu |
| Sprint | Sprint 3 |
| Öncelik | Yüksek |
| Tahmini Efor | 8 puan (~4-5 gün) |
| Bağımlılıklar | E1-02, E1-04, E2-01 |
| Etiketler | backend, migration, files, matching |

## Amaç
`faturalar/` altındaki mevcut ay klasörlerini ve waiting/trash içeriklerini tarayıp her fizik dosya için `invoices`/`files` kaydı oluşturmak ve bunları doğru `expense` satırlarıyla eşlemek.

## Açıklama / Bağlam
Mevcut sistemde faturalar `faturalar/2026-01..2026-04/`, `waiting/`, `trash/` klasörlerinde duruyor; her dosyanın Excel'de bir karşılık satırı olması bekleniyor (Fatura Notu path'i ile). Migrasyonda bu fizik dosyaları DB metadata'sına bağlamamız ve E2-01'de oluşan expense satırlarıyla eşlememiz gerekir.

Eşleme ipuçları: dosya adı (`{hizmet}_{ay}.pdf`), bulunduğu ay klasörü, Excel'deki "Fatura Notu" path'i, tutar/tarih. Bir expense'e birden çok dosya (PDF + XML + statement) bağlanabilir. Duplicate'ler (aynı invoice no, receipt kopyaları) tespit edilip trash mantığına uygun işaretlenmeli.

## Kabul Kriterleri (DOD)
- [x] `faturalar/` ay + waiting/ + trash/ recursive taranıyor (.DS_Store/dotfile atlanır)
- [x] Her fizik dosya için `files` kaydı (path, tip PDF/XML/STATEMENT/RECEIPT, boyut, sha256)
- [x] Eşleme önceliği: "Fatura Notu" path > base-isim türevi (statement/xml aynı invoice'a) > unmatched
- [x] Bir expense'e birden çok dosya (PDF+XML+statement aynı invoice)
- [x] Duplicate: aynı sha256 → tek kopya tutulur (orphan yok), trash → trashed/unmatched
- [x] Eşleşmeyen "unmatched" raporlanır (konum korunur)
- [x] **Mevcut yerleşim DEĞİŞTİRİLMEZ**: kaynak read-only, storage root'a aynı relative path ile kopyalanır (yeniden adlandırma yok)
- [x] DB path'ler storage root'taki gerçek kopyayla birebir (copyPreservingPath, traversal-safe)
- [x] Özet rapor: scanned/copied/matched/unmatched/trashed/duplicates + unmatchedFiles[]

## Alt Görevler
- [ ] Klasör tarayıcı (recursive, ay klasörü + waiting + trash ayrımı)
- [ ] Dosya tipi tespiti (PDF/XML/statement/receipt) ve hash hesabı
- [ ] Excel "Fatura Notu" path'lerini parse edip eşleme indeksini kur
- [ ] Eşleme motoru (öncelikli kurallar)
- [ ] Duplicate dedektörü (hash + invoice no)
- [ ] `invoices` + `files` kayıtları yaz, expense'e bağla
- [ ] Eşleşmeyen/duplicate raporu

## Teknik Notlar
- E1-04 dosya depolama servisini KULLAN; bu görev mevcut dosyaları yerinden oynatmadan kaydeder (migration modu = path'i olduğu gibi al)
- Invoice no çıkarımı PDF parse gerektirebilir; MVP'de invoice no yoksa hash bazlı duplicate yeterli
- waiting/ ve trash/ içerikleri de kaydedilir ama "unmatched"/"trashed" durumlu
- E2-04 ile koordinasyon: dosya bulunan expense'lerin durumu "Bulundu"/"e-Fatura"ya çekilir

## Açık Sorular / Riskler
- ~~"Fatura Notu" path formatı?~~ → Tutarlı (`faturalar/2026-03/aws_mart.pdf`); 55/101 invoice'ta path var, normalize edilip eşlendi. Açıklama-only note'lar (path değil) reddediliyor.
- ~~XML/statement eşleme?~~ → Base-isim türevi olarak aynı invoice'a (aws_mart.pdf + _statement + .xml).
- waiting/ boş; doluysa unmatched kaydedilir.

## Tamamlanma Kaydı
- Durum: ✅ Tamamlandı — 2026-06-25
- YouTrack: IK-234 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend)
- Üretilenler: InvoiceFileImportService + AdminImportController `POST /api/v1/admin/imports/invoice-files` (ADMIN, sourceDir param confined), InvoiceFileImportSummary, StorageService.copyPreservingPath, ImportProperties, Flyway V8 (files.invoice_id nullable) + V9 (files.sha256 partial unique)
- **Gerçek veri doğrulaması (lokal PG14)**: gerçek `faturalar/` → scanned=58, copied=55, **storage fiziksel 55 == files DB row 55 (ORPHAN YOK)**, matched=49, unmatched=6, duplicates=3. Idempotency: 2. import 0 yeni. **Kaynak (Drive aynası) byte-byte dokunulmadı** (59 dosya + mtime sabit). Bad sourceDir → 400.
- Test: `./mvnw test` 69/69 (3 surefire sırasında)
- **İki bağımsız review (kural gereği): (1) implementation agent self-review (hardcoded path, sha256 unique index, path confinement); (2) parent bağımsız review → orphan bug (content-duplicate fiziksel kopya ama DB row yok) + 500→400 yakalandı, ikisi de düzeltildi.** Bağımsız review değerini kanıtladı (agent self-review orphan'ı kaçırmıştı).
- **Borç (V9):** `files.sha256` partial unique index, mevcut duplicate sha256 olan bir DB'de deploy'da fail edebilir. Lokal PG'de sorunsuz uygulandı + E1-04 zaten duplicate'i engelliyor. V9 değiştirilmedi (Flyway checksum). Prod deploy öncesi preflight (DURUM borç).
- expenses/ Drive aynasına yazma/silme/taşıma YOK (sadece read).
