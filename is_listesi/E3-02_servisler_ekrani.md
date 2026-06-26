# [E3-02] Servisler ekranı: master liste yönetimi

| Alan | Değer |
|------|-------|
| Epic | E3 — Fatura Yönetim Web Uygulaması |
| Sprint | Sprint 2 |
| Öncelik | Yüksek |
| Bağımlılıklar | E1-02, E1-03, E1-07, E2-01 |
| Tahmini Efor | 5 puan (~3 gün) |
| Etiketler | frontend, backend, ui, servisler |

## Amaç
Ödenen tüm servislerin master listesini ekranda yönetilebilir hale getir. Bu liste, eksik fatura tespitinin (E3-04) kaynağıdır: "hangi servisler aktif ve aylık fatura bekliyor".

## Açıklama / Bağlam
Excel'deki "Servisler" sheet'inin web karşılığı. Tablo halinde tüm servisler listelenir; her satırda Hizmet, Sağlayıcı, Kart, Frekans, Aktif, Aktif Aylar, Yaklaşık Tutar (TL), Fatura E-posta (ilgili kişi/grup), Fatura Kaynağı, Notlar kolonları bulunur. Kullanıcı yeni servis ekleyebilir, mevcut servisi düzenleyebilir, aktif/pasif yapabilir. "Fatura E-posta" alanı E6 hatırlatma maillerinin kime gideceğini belirler, bu yüzden zorunlu ve doğru olmalı.

## Kabul Kriterleri (DOD)
- [x] Servisler tablo halinde (Hizmet, Sağlayıcı, Kart, Frekans, Aktif, Aktif Aylar, Yaklaşık Tutar, Fatura E-posta, Fatura Kaynağı, Notlar) — tarayıcıda doğrulandı (32 servis)
- [x] Yeni servis ekleme (modal + reactive form, POST)
- [x] Mevcut servis düzenleme (PUT)
- [x] Aktif/Frekans enum dropdown; Kart seçimi `/api/v1/cards`'tan (3 kart)
- [x] Aktif/pasif filtreleme (dropdown) + isim/sağlayıcı arama (debounced)
- [x] Fatura E-posta validasyonu (çoklu/virgüllü adres + format; backend+frontend)
- [x] Frekans/Aktif renkli badge (Aktif Evet=yeşil/Belirsiz=turuncu)
- [x] Hard-delete yok → pasifleştirme (PATCH active, DELETE→405)

## Alt Görevler
- [ ] Backend: Servis CRUD endpoint'leri (`GET/POST/PUT /api/services`)
- [ ] Backend: Frekans ve Aktif için enum tanımı (E1-02 veri modeliyle uyumlu)
- [ ] Angular: servis liste tablosu (filtre + arama)
- [ ] Angular: servis ekle/düzenle modal/form
- [ ] Validasyon: e-posta, zorunlu alanlar
- [ ] Kart listesini referans tablodan getiren ortak bileşen

## Teknik Notlar
- "Aktif Aylar" alanı sistem tarafından da güncellenebilir (servis bir ayda görülünce); bu görevde manuel düzenleme yeterli, otomatik güncelleme E4 ile gelir
- Frekans = Aylık + Aktif = Evet olan servisler E3-04'ün giriş kümesi
- Kart referans verisi (banka, son 4 hane, sahip) E1-02'de modellenir

## Açık Sorular / Riskler
- ~~Fatura E-posta tek/çoklu?~~ → Virgüllü çoklu adres destekleniyor (E6 için), format doğrulanıyor.
- ~~Silme vs pasifleştirme?~~ → Hard-delete yok; PATCH active ile pasifleştirme (geçmiş korunur).

## Tamamlanma Kaydı
- Durum: ✅ Tamamlandı — 2026-06-26
- YouTrack: IK-239 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend) + account-hr-frontend
- **Backend:** ServiceController (GET liste +filtre/arama, POST, PUT, PATCH active), CardController (`GET /api/v1/cards`), ServiceCommandService (create/update/setActive, provider resolve-or-create), JpaSpecificationExecutor arama, EmailList validation, ServiceResponse/Request DTO
- **Frontend:** Servisler ekranı (tablo + ekle/düzenle modal + filtre + debounced arama), badge'ler, sidebar "Servisler" aktif link
- **Gerçek doğrulama (lokal PG14 + tarayıcı):** GET 32 servis, ?active=YES→24, ?q=AWS arama, POST/PATCH çalışıyor, invalid email→400, DELETE→405. Tarayıcıda tablo + badge'ler + sidebar logosu görsel onaylı.
- Test: backend `./mvnw test` 137/137 (134+3, 3 surefire sırasında); frontend `ng build` temiz
- **İki bağımsız review turu (agent + parent):** parent turu 3 bulgu → (1) invalid enum query → 500 yerine 400; (2) PUT'ta `informational` sıfırlanması (data loss) → null-guard; (3) provider resolve-or-create race → saveAndFlush+re-query / 409. Hepsi düzeltildi.

## Görsel Sistem Eklendi (bu görevle birlikte)
- **Higgsfield ile üretildi** (kullanıcı isteği): marka logosu (`logo.png` — yeşil "ah" badge) + login arka planı (`login-bg.png` — yeşil→navy gradient).
- Login ekranı (arka plan görseli + gerçek logo + okunabilir yeşil "Giriş yap" butonu), sidebar gerçek logo, favicon.
- **Kritik tasarım-sistemi düzeltmesi:** yeni component'ler AwesomeDesign'ın `--primary-600`/`--gray-50` CSS değişken isimlerini kullanıyordu ama `_tokens.scss` `--ec-*` öneki tanımlıyordu → tanımsız değişkenler login background'unu ve butonunu görünmez yapmıştı. `_tokens.scss`'e **alias değişkenler** eklendi (AwesomeDesign isimleri → `--ec-*` değerleri) → tüm ekranlar tutarlı. Tarayıcıda doğrulandı.
