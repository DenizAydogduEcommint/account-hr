# [E6-05] In-app bildirim ve kullanıcı bildirim tercihleri

| Alan | Değer |
|------|-------|
| Epic | E6 — Hatırlatma & Bildirim |
| Sprint | Sprint 5 |
| Öncelik | Düşük |
| Bağımlılıklar | E1-03, E1-07, E3-08, E6-03 |
| Tahmini Efor | 5 puan (~3 gün) |
| Etiketler | frontend, backend, bildirim |

## Amaç
Kullanıcılar uygulama içinde de bildirim alsın (eksik fatura, hatırlatma sonucu, eskalasyon) ve hangi bildirimleri nasıl alacaklarını seçebilsin. Maile bağımlılığı azaltır, kullanıcı kontrolü verir.

## Açıklama / Bağlam
E6-01/E6-04 maillerinin in-app karşılığı. Üst menüde bir bildirim zili ve listesi olur: "X servisinin Şubat faturası eksik", "Hatırlatma gönderildi", "Eskalasyon: Y faturası hâlâ yok" gibi. Kullanıcı okundu işaretler. Ayrıca bir "Bildirim Tercihleri" ekranı: kullanıcı hangi olaylarda e-posta ve/veya in-app bildirim alacağını seçer. Tercihler E6-01/E6-04 gönderim kararlarını etkiler.

## Kabul Kriterleri (DOD)
- [ ] Üst menüde bildirim zili + okunmamış sayacı
- [ ] Bildirim listesi (eksik fatura, hatırlatma sonucu, eskalasyon)
- [ ] Bildirim okundu/okunmadı işaretlenebilir
- [ ] Bildirim tercihleri ekranı: olay bazında e-posta / in-app açma-kapama
- [ ] Tercihler gönderim mantığında (E6-01/E6-04) dikkate alınır
- [ ] Bildirimler role uygun gösterilir (E3-08) — herkese her şey değil
- [ ] Eski bildirimler arşivlenir/temizlenir (aşırı birikme önlenir)

## Alt Görevler
- [ ] Backend: in-app bildirim veri modeli + üretim (eksik/hatırlatma/eskalasyon olaylarında)
- [ ] Backend: bildirim listeleme + okundu işaretleme endpoint'leri
- [ ] Backend: kullanıcı bildirim tercihleri modeli + okuma/yazma
- [ ] Backend: tercihleri gönderim kararına bağlama (E6-01/E6-04)
- [ ] Angular: bildirim zili + liste bileşeni
- [ ] Angular: bildirim tercihleri ekranı

## Teknik Notlar
- Gerçek zamanlılık MVP'de gerekmez; periyodik polling yeterli (WebSocket sonraki iyileştirme)
- Bildirim üretimi E6-03 gönderim olaylarıyla aynı tetikleyicilerden beslenir
- Tercih varsayılanları makul olmalı (yeni kullanıcı eksik bildirim almasın)
- Rol bazlı görünürlük E3-08 ile tutarlı

## Açık Sorular / Riskler
- Gerçek zamanlı bildirim (WebSocket) beklentisi var mı yoksa polling kabul mü? — Öneri: polling MVP
- Tercih kapatılınca kritik eskalasyon yine de gitsin mi? (Öneri: kritik bildirimler kapatılamaz)
