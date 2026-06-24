# [E3-05] Fatura yükleme UI: servis + ay seç, dosya yükle

| Alan | Değer |
|------|-------|
| Epic | E3 — Fatura Yönetim Web Uygulaması |
| Sprint | Sprint 2 |
| Öncelik | Yüksek |
| Bağımlılıklar | E1-02, E1-03, E1-04, E1-07, E3-04 |
| Tahmini Efor | 5 puan (~3 gün) |
| Etiketler | frontend, backend, ui, dosya-yukleme |

## Amaç
Ekip üyeleri eksik faturaları kendileri yükleyebilsin: servis ve ay seçer, tutar/para birimi/açıklama girer, dosya(lar) yükler; durum otomatik "Bulundu" olur. Manuel Excel/Drive akışını ortadan kaldırır.

## Açıklama / Bağlam
Eksik fatura ekranındaki (E3-04) "Fatura Yükle" butonu veya harcama satırından açılan bir form/modal. Kullanıcı servis ve ayı seçer (eksik ekranından gelindiyse önceden dolu gelir), tutar + para birimi + kısa açıklama girer, bir veya birden çok dosya yükler (PDF/XML/JPG). Dosya(lar) dosya sistemine kaydedilir (E1-04), metadata DB'ye yazılır, ilgili harcama satırının Fatura Durumu "Bulundu" (veya e-Fatura) olur, Fatura Notu dosya bilgisini içerir. Bir satıra birden çok dosya eklenebilir (fatura + statement + XML).

## Kabul Kriterleri (DOD)
- [ ] Servis + ay seçimi ile yükleme formu açılır; eksik ekranından gelince ön-doldurulur
- [ ] Tutar, para birimi, açıklama alanları girilebilir
- [ ] Tek seferde birden çok dosya yüklenebilir (drag-drop + dosya seç)
- [ ] İzinli dosya tipleri (PDF, XML, JPG, PNG) ve boyut limiti doğrulanır
- [ ] Yükleme sonrası ilgili satırın durumu "Bulundu" (veya seçildiyse "e-Fatura") olur
- [ ] Fatura Notu / metadata dosya bilgisini içerir
- [ ] Yükleme başarısızsa anlaşılır hata gösterilir, dosya yarım kaydedilmez
- [ ] Eksik fatura sayacı (E3-04/E3-01) yükleme sonrası azalır

## Alt Görevler
- [ ] Backend: `POST /api/invoices` (multipart) — dosya kaydı + metadata + satır durum güncelleme
- [ ] Backend: dosya depolama entegrasyonu (E1-04) ve isimlendirme kuralı
- [ ] Backend: dosya tipi/boyut validasyonu
- [ ] Angular: yükleme formu/modalı (dropzone bileşeni)
- [ ] Angular: çoklu dosya kuyruğu + ilerleme göstergesi
- [ ] Başarı/başarısızlık bildirimleri (toast)

## Teknik Notlar
- Dosya isimlendirme CLAUDE.md kuralına göre: `{hizmet_kisa}_{ay}.pdf`, çoklu için `_1`, `_2`
- Dosya tarihine göre klasör yerleştirme kuralı (Nisan 2026+) backend tarafında uygulanabilir; MVP'de seçili ay klasörü yeterli, sonra rafine edilir
- Durum geçişi E3-07 state machine'i üzerinden yapılmalı (doğrudan değil)
- Yükleme atomik olmalı: dosya + metadata + durum tek transaction mantığında

## Açık Sorular / Riskler
- Tutar/para birimi otomatik fatura PDF'inden okunsun mu? (MVP'de manuel giriş, OCR/parse sonraki epic)
- e-Fatura ile normal fatura ayrımını kullanıcı mı seçer yoksa sistem mi belirler? — Öneri: kullanıcı işaretler
