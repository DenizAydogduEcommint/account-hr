# [E6-03] Bildirim şablonları (TR) ve gönderim takibi

| Alan | Değer |
|------|-------|
| Epic | E6 — Hatırlatma & Bildirim |
| Sprint | Sprint 4 |
| Öncelik | Orta |
| Bağımlılıklar | E1-02, E1-07 |
| Tahmini Efor | 5 puan (~3 gün) |
| Etiketler | backend, mail, sablon |

## Amaç
Tüm bildirim mailleri için Türkçe (gerektiğinde İngilizce) şablon altyapısı ve gönderim takip kaydı kur. Kime, ne zaman, hangi mailin gittiği ve yanıt gelip gelmediği izlenebilsin.

## Açıklama / Bağlam
E6-01 (ilgili kişiye hatırlatma) ve E6-02 (sağlayıcıya talep) maillerinin ortak temeli. Şablonlar değişkenli (servis adı, ay, tutar, link, firma bilgisi) ve dil destekli olur. Her gönderim bir kayıt oluşturur: alıcı, konu, gönderim zamanı, ilişkili servis/ay, durum (gönderildi/başarısız), opsiyonel yanıt işareti. Bu kayıt E6-04 eskalasyon (X gün cevapsızsa tekrar) ve raporlama için temeldir.

## Kabul Kriterleri (DOD)
- [ ] Değişkenli mail şablonları tanımlı (TR, gerekirse EN)
- [ ] Şablon değişkenleri: servis adı, ay, tutar, yükleme linki, firma bilgileri
- [ ] Her gönderim için kayıt: alıcı, konu, zaman, servis/ay, durum
- [ ] Gönderim başarısız olursa kayıtta işaretlenir
- [ ] "Yanıt geldi mi" işaretleme alanı (manuel veya ileride otomatik)
- [ ] Gönderim geçmişi bir ekrandan görüntülenebilir
- [ ] Şablonlar koddan ayrı, düzenlenebilir (config/DB)

## Alt Görevler
- [ ] Backend: şablon motoru (değişken yerleştirme, dil)
- [ ] Backend: bildirim/gönderim kaydı veri modeli (E1-02 ile)
- [ ] Backend: gönderim kayıt servisi (E6-01/E6-02 buraya yazar)
- [ ] Backend: gönderim geçmişi endpoint'i
- [ ] Angular: gönderim geçmişi/takip ekranı
- [ ] Yanıt işaretleme aksiyonu

## Teknik Notlar
- Şablon motoru olarak Thymeleaf veya basit placeholder değişimi kullanılabilir
- Gönderim kaydı E6-04 eskalasyon mantığının girdisi (son gönderim zamanı)
- "Yanıt geldi" otomasyonu (gelen kutusu okuma) MVP dışı; manuel işaret yeterli
- Şablonlar versiyonlanabilir olursa ileride iyi olur (opsiyonel)

## Açık Sorular / Riskler
- Şablonlar DB'de mi yoksa dosyada mı tutulsun? — Öneri: dosya/config başlangıç, ileride DB
- Yanıt takibi tamamen manuel mi kalacak? (otomatik gelen kutusu okuma ayrı/sonraki iş)
