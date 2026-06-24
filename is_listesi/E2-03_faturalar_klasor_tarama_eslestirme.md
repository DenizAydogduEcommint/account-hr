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
- [ ] `faturalar/` ay klasörleri + waiting/ + trash/ recursive taranıyor
- [ ] Her fizik dosya için `files` kaydı (path, tip PDF/XML/statement, boyut, hash)
- [ ] Dosyalar uygun `invoice`/`expense` ile eşleniyor; eşleme öncelik sırası: Excel "Fatura Notu" path'i > dosya adı + ay klasörü > tutar/tarih
- [ ] Bir expense'e birden çok dosya bağlanabiliyor (PDF+XML+statement aynı invoice altında)
- [ ] Duplicate tespiti: aynı hash veya aynı invoice no → duplicate işaretlenir, biri tutulur (Invoice > Receipt)
- [ ] Eşleşmeyen dosyalar "unmatched" olarak raporlanır (waiting/trash konumu korunur)
- [ ] **Mevcut Ocak/Şubat/Mart klasör yerleşimi DEĞİŞTİRİLMEZ** (eski ödeme-ayı kuralı; geriye dönük taşıma yok)
- [ ] DB'deki path'ler gerçek dosya konumlarıyla bire bir tutuyor (E1-04 storage servisi üzerinden)
- [ ] Özet rapor: taranan dosya, eşleşen, eşleşmeyen, duplicate sayıları

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
- "Fatura Notu" path formatı tutarlı mı? Bazı satırlarda açıklama olabilir, path olmayabilir
- e-Fatura (LeasePlan) ve XML dosyaları için eşleme kuralı PDF'ten farklı olabilir
- waiting/ migrasyon anında dolu olabilir — bu içerik DB'ye "bekleyen" olarak mı girecek yoksa atlanacak mı? (Öneri: kaydet, unmatched işaretle)
