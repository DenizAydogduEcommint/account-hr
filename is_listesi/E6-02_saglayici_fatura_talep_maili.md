# [E6-02] Sağlayıcı firmaya fatura talep maili (şablon)

| Alan | Değer |
|------|-------|
| Epic | E6 — Hatırlatma & Bildirim |
| Sprint | Sprint 5 |
| Öncelik | Orta |
| Bağımlılıklar | E3-02, E6-01, E6-03 |
| Tahmini Efor | 3 puan (~2 gün) |
| Etiketler | backend, mail, bildirim |

## Amaç
İç ekipten fatura gelmiyorsa, doğrudan sağlayıcı firmaya (ör. yurtdışı abonelik sağlayıcısı) fatura talep maili gönderebil. Faturayı kaynağından isteme yolunu açar.

## Açıklama / Bağlam
Bazı faturalar ekip üyesinde değil, doğrudan sağlayıcıdan (örn. fatura/destek e-postası) istenmeli. Bu görev, bir servis için sağlayıcıya gönderilecek (genelde İngilizce/uygun dilde) bir fatura talep şablonu sunar ve gönderir: "Lütfen X dönemi için fatura gönderin, fatura adresimiz/VKN şudur" gibi. Kullanıcı E3-04 veya servis ekranından "Sağlayıcıdan İste" aksiyonuyla tetikler. Gönderim kaydı E6-03'te tutulur.

## Kabul Kriterleri (DOD)
- [ ] Servis için sağlayıcı e-postasına fatura talep maili gönderilebilir
- [ ] Talep şablonu firma fatura bilgilerini (unvan, VKN/adres) içerir
- [ ] Dil seçilebilir/uygun (yurtdışı için İngilizce, yerel için Türkçe)
- [ ] Manuel tetikleme aksiyonu (E3-04 / servis ekranı) çalışır
- [ ] Gönderim kaydı tutulur (E6-03)
- [ ] Sağlayıcı e-postası tanımlı değilse uyarı verilir

## Alt Görevler
- [ ] Backend: sağlayıcı talep maili gönderim akışı
- [ ] Backend: firma fatura bilgileri (unvan, VKN, adres) konfigürasyonu
- [ ] Backend: dil seçimli şablon (E6-03 ile)
- [ ] Angular: "Sağlayıcıdan İste" aksiyonu
- [ ] Gönderim kaydı entegrasyonu

## Teknik Notlar
- Firma fatura bilgileri tek yerde (ayar) tutulmalı; şablona enjekte edilir
- E6-01 ile aynı mail gönderim altyapısını kullanır (accounting@e-commint.com)
- Sağlayıcı e-postası servis kaydında olmayabilir; servise "sağlayıcı fatura e-postası" alanı gerekebilir (E3-02 ile koordine)

## Açık Sorular / Riskler
- Sağlayıcı e-postaları toplu mevcut mu yoksa zamanla mı doldurulacak? — eksikse manuel giriş
- Yurtdışı sağlayıcılar e-posta talebine fatura ile döner mi yoksa panelden mi alınır? — bazı servisler sadece panel; bu durumda mail anlamsız olabilir
