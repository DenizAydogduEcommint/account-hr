# [E3-06] Manuel harcama/satır girişi (ekstre yoksa)

| Alan | Değer |
|------|-------|
| Epic | E3 — Fatura Yönetim Web Uygulaması |
| Sprint | Sprint 3 |
| Öncelik | Orta |
| Bağımlılıklar | E1-02, E1-03, E1-07, E3-02, E3-03 |
| Tahmini Efor | 3 puan (~2 gün) |
| Etiketler | frontend, backend, ui, harcamalar |

## Amaç
Banka ekstresi henüz işlenmemiş ya da gelmemişken, ekip üyesi bir harcama satırını manuel olarak ekleyebilsin (servis bazlı). Böylece fatura yükleme ve eksik takip ekstreyi beklemeden başlar.

## Açıklama / Bağlam
Aylık harcamalar ekranından (E3-03) "Yeni Satır Ekle" ile açılan bir form. Kullanıcı servisi seçer (master listeden — E3-02), 12 kolonun manuel girilebilir alanlarını doldurur: Tarih, Tutar, Para Birimi, TL Karşılığı, Kart, Kullanan Takım, Amaç, Muhasebe E-posta. Servis seçilince Hizmet/Sağlayıcı/Kart ön-doldurulur. Fatura Durumu varsayılan "Bekleniyor" olur. Örnek senaryo: Axess ****3800 ile çekilen bir abonelik ekstre gelmeden satır olarak girilir.

## Kabul Kriterleri (DOD)
- [ ] "Yeni Satır Ekle" formu E3-03 ekranından açılır
- [ ] Servis seçilince Hizmet, Sağlayıcı, Kart alanları ön-doldurulur (düzenlenebilir)
- [ ] Tarih, Tutar, Para Birimi, TL Karşılığı zorunlu/doğrulanır
- [ ] Kart tanımlı kartlardan seçilir
- [ ] Yeni satır varsayılan "Bekleniyor" durumuyla oluşur
- [ ] Kaydedilen satır anında listede ve toplamda görünür (operasyonelse)
- [ ] Servis master listede yoksa "yeni servis ekle" yönlendirmesi yapılır

## Alt Görevler
- [ ] Backend: `POST /api/expenses` satır oluşturma endpoint'i
- [ ] Backend: para/sayı validasyonu, varsayılan durum atama
- [ ] Angular: yeni satır formu/modalı
- [ ] Angular: servis seçince alan ön-doldurma mantığı
- [ ] Bilgi amaçlı/operasyonel işaretleme seçeneği

## Teknik Notlar
- Form, E3-05 fatura yükleme ile birleşik akışta da kullanılabilir (önce satır, sonra fatura)
- Tutar gerçek sayı olarak saklanır; TL kolonu format `#,##0.00 ₺`
- Durum atama E3-07 state machine ile uyumlu olmalı

## Açık Sorular / Riskler
- Manuel satır ile sonradan gelen ekstre satırı çakışırsa (duplicate) nasıl ayırt edilecek? — E4 eşleştirme motoruyla koordine, satıra "kaynak: manuel/ekstre" alanı önerilir
