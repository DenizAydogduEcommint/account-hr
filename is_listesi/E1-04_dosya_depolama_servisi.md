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
- [ ] Dosya yükleme servisi: bir expense/invoice'a dosya bağlanır, fizik dosya doğru ay klasörüne yazılır
- [ ] Klasör seçimi **fatura tarihine göre** yapılır (verilen invoice date'ten `YYYY-MM` türetilir), ödeme tarihinden değil
- [ ] İsimlendirme kuralı uygulanır; aynı isim çakışmasında `_1`, `_2` veya tip eki (`_statement`, `.xml`) eklenir
- [ ] Bir invoice'a birden çok dosya bağlanabilir (PDF + XML + statement)
- [ ] Duplicate kontrolü: aynı (provider, invoice_no) ikinci dosyada uyarı/engelle; Receipt yerine Invoice tutma kuralı uygulanır
- [ ] `waiting/` ve `trash/` klasörlerine taşıma operasyonları API üzerinden mümkün
- [ ] Her fizik dosya için `files` tablosunda kayıt (path, tip, boyut, hash, invoice_id)
- [ ] Path traversal / güvenli dosya adı (sanitize) önlemleri var

## Alt Görevler
- [ ] `StorageService` arayüzü + dosya-sistemi implementasyonu (root path config'ten)
- [ ] Fatura tarihinden ay klasörü hesaplayan yardımcı
- [ ] İsimlendirme + çakışma çözümleyici (slugify hizmet adı)
- [ ] Duplicate dedektörü (invoice_no + dosya hash)
- [ ] waiting/trash taşıma operasyonları
- [ ] `files` metadata kayıt/güncelleme
- [ ] Dosya indirme endpoint'i (yetki kontrollü)

## Teknik Notlar
- Root path ortam değişkeni; staging/prod'da kalıcı volume
- Dosya hash (SHA-256) hem duplicate hem bütünlük için
- Türkçe karakter slugify (ç→c, ş→s, ğ→g, ı→i, ö→o, ü→u) — `google_workspace_subat.pdf` örneği
- Ocak/Şubat/Mart 2026 klasörleri ödeme ayına göre yerleştirilmiş (eski kural) — migration'da geriye dönük taşıma YOK (E2-03 ile uyumlu)
- Drive sync ayrı görev (E2-06); bu servis sadece lokal dosya sistemini yönetir

## Açık Sorular / Riskler
- Invoice date dosyadan otomatik mi okunacak (OCR/parse) yoksa kullanıcı mı girecek? (MVP: kullanıcı girer / E-epic'te otomatik parse)
- waiting/ Drive ile çift yönlü mü? (E2-06'da çözülecek; bu görevde sadece lokal taraf)
- Çok büyük dosyalar / yükleme limiti?
