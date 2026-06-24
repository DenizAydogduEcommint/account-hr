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
- [ ] Servisler tablo halinde listelenir (Hizmet, Sağlayıcı, Kart, Frekans, Aktif, Aktif Aylar, Yaklaşık Tutar, Fatura E-posta, Fatura Kaynağı, Notlar)
- [ ] Yeni servis ekleme formu çalışır
- [ ] Mevcut servis düzenleme çalışır
- [ ] Aktif (Evet/Hayır/Belirsiz) ve Frekans (Aylık/Yıllık/Kullanım bazlı/Ad-hoc) dropdown/enum ile seçilir
- [ ] Kart seçimi tanımlı kartlardan (****3800, ****3909, ****9164) yapılır
- [ ] Aktif/pasif duruma göre filtreleme yapılabilir
- [ ] Fatura E-posta alanı geçerli e-posta formatı doğrular
- [ ] Frekans/Aktif değerleri görsel rozet (badge) olarak gösterilir

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
- "Fatura E-posta" tek kişi mi, grup mu olabilir? (E6 için virgülle çoklu adres desteği düşünülmeli)
- Servis silme yerine pasifleştirme tercih edilmeli (geçmiş veriyle ilişki bozulmasın)
