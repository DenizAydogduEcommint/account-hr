# [E5-02] Drive waiting/ klasörü otomatik pull & işleme

| Alan | Değer |
|------|-------|
| Epic | E5 — Otomatik Fatura Toplama |
| Sprint | Sprint 5 |
| Öncelik | Yüksek |
| Tahmini Efor | 3 puan (~2 gün) |
| Bağımlılıklar | E1-04 (dosya depolama), E1-05 (secret yönetimi), E5-04 (eşleştirme) |
| Etiketler | otomasyon, drive, rclone, fatura-toplama |

## Amaç
Google Drive'daki `faturalar/waiting/` klasörünü düzenli olarak çekip (pull) işleme kuyruğuna almak; eşleşen faturaları ilgili ay klasörüne taşıyıp waiting'den temizlemek. Ekip Drive'a fatura attığında sistem bunu otomatik yakalar.

## Açıklama / Bağlam
Mevcut manuel akışta `rclone copy gdrive-ecommint:/faturalar/waiting/ local/waiting/` ile çekiliyor, inceleniyor, eşleştiriliyor, ay klasörüne taşınıyor, sonra waiting'den siliniyor. Bu görev o akışı otomatikleştirir. Önemli kural: **Drive'a lokal dosya push edilmez** — waiting'e sadece kullanıcı/ekip Drive üzerinden dosya ekler; sistem sadece pull eder ve işlenenleri waiting'den siler.

Eşleştirme mantığı E5-04'e devredilir; bu görev pull → işleme tetikleme → taşıma/silme orkestrasyonunu kapsar.

## Kabul Kriterleri (DOD)
- [ ] Sistem `gdrive-ecommint:/faturalar/waiting/` içeriğini zamanlanmış şekilde lokale çekebiliyor
- [ ] Her dosya için ham fatura kaydı oluşturuluyor ve işleme kuyruğuna alınıyor (E5-03/E5-04 tetiklenir)
- [ ] Eşleşen fatura ilgili ay klasörüne (fatura tarihine göre) taşınıyor
- [ ] İşlenen dosya hem lokal hem Drive waiting/'den siliniyor (`rclone delete` / Drive API)
- [ ] Eşleşmeyen dosya waiting'de bırakılıyor veya trash'e taşınıyor (kullanıcıya bildirimle)
- [ ] Hiçbir koşulda lokal dosya Drive'a push edilmiyor (yalnızca pull + waiting delete)

## Alt Görevler
- [ ] rclone vs Google Drive API kararı (rclone halihazırda yapılandırılı — wrapper mı, native API mı)
- [ ] Pull servisi + secret/credential entegrasyonu (E1-05)
- [ ] Yeni dosya tespiti (önceden işlenenleri atla)
- [ ] İşleme kuyruğuna ekleme + E5-04 eşleştirme çağrısı
- [ ] Taşıma (ay klasörü) ve waiting temizleme (lokal + Drive)
- [ ] Entegrasyon testi (test Drive klasörü / mock)

## Teknik Notlar
- rclone remote: `gdrive-ecommint`, root folder ID `1vQWwqbozxyb9fsFjpKlk6XZ5cMlURLKm`.
- Java'dan rclone'u subprocess olarak çağırmak hızlı çözüm; uzun vadede Google Drive API (google-api-services-drive) daha kontrollü.
- Push yasağı mimari kural — kod review checklist'e ekle.
- Ay klasörü kuralı: dosya fatura tarihine göre klasörlenir (Nisan 2026'dan itibaren); Excel satırı ödeme ayında kalır.

## Açık Sorular / Riskler
- rclone subprocess mu Drive API mı? Sunucu ortamında rclone binary + config güvenliği.
- Aynı dosya hem mailden (E5-01) hem Drive'dan gelirse duplicate — invoice no ile E5-04 ayıklamalı.
- Drive'da delete yetkisi olan service account gerekiyor.
