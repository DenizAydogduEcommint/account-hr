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
- [x] Servis + ay seçimi ile yükleme formu/modalı; Eksik Fatura ekranından ön-doldurulur
- [x] Tutar, para birimi, açıklama alanları
- [x] Çoklu dosya (drag-drop + dosya seç)
- [x] İzinli tipler (PDF/XML/JPG/PNG) + boyut limiti (≤10MB) hem client hem server doğrulama
- [x] Yükleme sonrası durum "Bulundu" (eInvoice seçilirse "e-Fatura")
- [x] Fatura Notu dosya bilgisini içerir (mevcut not korunur — append)
- [x] Yükleme başarısızsa anlaşılır hata; **atomik** (transaction-synchronization ile commit-sonrası orphan temizliği)
- [x] Eksik fatura sayacı yükleme sonrası azalır (tarayıcı + endpoint: HepsiBurada upload → eksik 2→1)

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
- ~~Tutar/para birimi PDF'ten okunsun mu?~~ → MVP'de manuel giriş (OCR sonraki epic).
- ~~e-Fatura ayrımı?~~ → Kullanıcı işaretler (eInvoice checkbox → E_INVOICE, yoksa FOUND).

## Tamamlanma Kaydı
- Durum: ✅ Tamamlandı — 2026-06-26 · **MVP'NİN İKİNCİ ÇEKİRDEĞİ (yükleme)**
- YouTrack: IK-242 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend) + account-hr-frontend
- **Backend:** `POST /api/v1/invoices` (multipart, InvoiceUploadService). Find-or-create expense/invoice (mevcut EXPECTED'i FOUND'a çeker veya yeni oluşturur), durum FOUND/E_INVOICE, E1-04 storage (`{slug}_{ay}.{ext}`, sha256 dedup), FileAsset. **Atomik**: transaction-synchronization ile commit-sonrası başarısızlıkta yazılan dosyalar temizlenir (orphan yok); batch-içi sha256 dedup; mevcut not korunur (append).
- **Frontend:** ortak upload modalı (dropzone + çoklu dosya + sürükle-bırak + eInvoice + client validation), Eksik Fatura "Fatura Yükle" butonundan ön-dolu açılır, başarıda eksik listesi/sayaç yenilenir.
- **Gerçek doğrulama (lokal PG14 + tarayıcı):** HepsiBurada Mart'a test PDF upload → invoice 26 EXPECTED→**FOUND**, FileAsset bağlı, **eksik 2→1** (dashboard missingCount=1 birebir), dosya storage root'a `hepsiburada_magaza_mart.pdf` indi, **kaynak expenses/faturalar 59 sabit (dokunulmadı)**. Geri alma ile DB temiz duruma döndürüldü.
- Test: backend `./mvnw test` 174/174 (3 surefire sırasında; +13 toplam — upload IT'leri + rollback IT); frontend `ng build` temiz.
- **İki bağımsız review turu (agent + parent):** parent turu 3 atomicity/data-loss bulgusu: (1) post-commit file orphan → transaction-synchronization afterCompletion temizliği; (2) mevcut invoice not'unun ezilmesi → mergeNote (koru/append); (3) batch-içi sha256 duplicate → orphan → batch-içi dedup. Hepsi düzeltildi + test edildi (InvoiceUploadRollbackIT).
