# [E1-04] Fatura dosya depolama servisini geliştir (faturalar/ yapısı + kurallar)

| Alan | Değer |
|------|-------|
| Epic | E1 — Temel Altyapı & Veri Modeli |
| Sprint | Sprint 2 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-02 |
| Etiketler | backend, files, storage |

## Amaç
Fatura dosyalarını (PDF/XML/statement) CLAUDE.md kurallarına uygun şekilde dosya sisteminde saklayan, isimlendiren ve DB metadata'sıyla eşleyen bir depolama servisi kurmak.

## Açıklama / Bağlam
Fatura dosyaları DB'ye değil dosya sistemine kaydedilir; `faturalar/` altındaki ay klasör yapısı (`2026-01`, `2026-02`, ... `waiting/`, `trash/`) korunur. DB'de sadece metadata + path tutulur. CLAUDE.md'deki şu kurallar koda dökülmeli:

1. **Fatura tarihine göre klasörleme**: Dosya, faturanın üzerindeki **Invoice Date** ayının klasörüne konur (ödeme ayı değil). Örn: Şubat 28 faturası Mart 01'de çekilmişse → dosya `faturalar/2026-02/`, ama expense satırı Mart sheet'inde.
2. **İsimlendirme**: `{hizmet_kisa}_{ay}.pdf` (lowercase, boşluksuz). Birden fazla → `_1`, `_2` veya `_statement`, `.xml`.
3. **Bir satıra (expense) birden çok dosya**: e-Fatura + statement + XML.
4. **Duplicate tespiti**: Aynı invoice no = duplicate; Invoice + Receipt gelirse Invoice tutulur.
5. **waiting/ ve trash/ akışı**: eşleşmeyen → waiting veya trash.

## Kabul Kriterleri (DOD)
- [x] Dosya yükleme servisi: bir invoice'a dosya bağlanır, fizik dosya doğru ay klasörüne yazılır (`POST /api/files`)
- [x] Klasör seçimi **fatura tarihine göre** (invoice date'ten `YYYY-MM`), ödeme tarihinden değil
- [x] İsimlendirme kuralı + çakışmada `_1`/`_2`; Türkçe slugify (`google_workspace_subat.pdf`)
- [x] Bir invoice'a birden çok dosya bağlanabilir (PDF + XML + statement)
- [x] Duplicate kontrolü: aynı (provider, invoice_no) veya aynı SHA-256 → ikinci dosya `DuplicateFileException` (409). Receipt/Invoice ince ayarı kod yorumunda, iş katmanına bırakıldı
- [x] `waiting/` ve `trash/` taşıma operasyonları API üzerinden (`POST /{id}/waiting`, `/{id}/trash`)
- [x] Her fizik dosya için `files` tablosunda kayıt (path, tip, boyut, sha256, invoice_id, uploaded_by)
- [x] Path traversal koruması + dosya adı sanitize

## Alt Görevler
- [x] `StorageService` arayüzü + `FileSystemStorageService` (root config'ten, `STORAGE_ROOT`)
- [x] Fatura tarihinden ay klasörü hesaplayan yardımcı
- [x] İsimlendirme + çakışma çözümleyici (Türkçe slugify)
- [x] Duplicate dedektörü (invoice_no + SHA-256 hash)
- [x] waiting/trash taşıma operasyonları
- [x] `files` metadata kayıt (FileAsset + `sha256` kolonu, Flyway V4)
- [x] Dosya indirme endpoint'i (yetki kontrollü, `GET /{id}/download`)

## Teknik Notlar
- Root path ortam değişkeni; staging/prod'da kalıcı volume
- Dosya hash (SHA-256) hem duplicate hem bütünlük için
- Türkçe karakter slugify (ç→c, ş→s, ğ→g, ı→i, ö→o, ü→u) — `google_workspace_subat.pdf` örneği
- Ocak/Şubat/Mart 2026 klasörleri ödeme ayına göre yerleştirilmiş (eski kural) — migration'da geriye dönük taşıma YOK (E2-03 ile uyumlu)
- Drive sync ayrı görev (E2-06); bu servis sadece lokal dosya sistemini yönetir

## Açık Sorular / Riskler — KARARA BAĞLANDI
- ~~Invoice date otomatik mi kullanıcı mı?~~ → MVP: kullanıcı/çağıran girer (`invoiceDate` param). Otomatik parse E-epic'te.
- waiting/ Drive çift yönlü → E2-06'da. Bu görev sadece lokal taraf.
- ~~Yükleme limiti?~~ → 25MB (`spring.servlet.multipart.max-file-size/max-request-size`).

## Tamamlanma Kaydı
- Durum: Tamamlandı (Postgres V4 doğrulaması hariç) — 2026-06-25
- YouTrack: IK-228 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend)
- Storage root: `STORAGE_ROOT` env, varsayılan `~/account-hr-data/faturalar` (repo dışı, boş başlar; kök+waiting+trash otomatik oluşur)
- Üretilenler: StorageService + FileSystemStorageService (slugify/ay-klasör/SHA-256/duplicate/traversal koruması/move), FileController (upload/list/download/trash/waiting), FileAsset.sha256 + Flyway V4, hata tipleri (DuplicateFile 409, traversal 400)
- Doğrulananlar: `./mvnw package` ve `./mvnw test` — 23/23 (11 yeni storage testi, @TempDir ile)
- Sıkı kısıtlara uyuldu: `expenses/faturalar`'a hiçbir kod yolu dokunmaz (sadece yorumda yasak notu); veri aktarımı/kopyalama YOK (E2-03'e ait)
- Kalan iş: V4 migration gerçek Postgres'te `ddl-auto=validate` ile doğrulanacak (Docker)
- Not: `application.yml`'de çift `app:` anahtarı YAML hatası verdi → tek blokta birleştirildi (gözlemlenip düzeltildi)
