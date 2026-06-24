# [E6-01] Eksik fatura → ilgili kişiye otomatik hatırlatma maili

| Alan | Değer |
|------|-------|
| Epic | E6 — Hatırlatma & Bildirim |
| Sprint | Sprint 4 |
| Öncelik | Yüksek |
| Bağımlılıklar | E1-03, E1-07, E3-02, E3-04, E6-03 |
| Tahmini Efor | 5 puan (~3 gün) |
| Etiketler | backend, mail, bildirim |

## Amaç
Bir servisin faturası eksikse, o servisin "Fatura E-posta" alanındaki ilgili kişiye otomatik hatırlatma maili gönder. "Fatura nerede?" kovalamacasını otomatikleştirir.

## Açıklama / Bağlam
Her serviste, faturayı temin edecek ilgili kişinin e-postası tanımlı (E3-02 / Servisler master). E3-04 eksik fatura tespiti bir servisi "eksik" olarak işaretlediğinde, bu görev ilgili kişiye Türkçe bir hatırlatma maili gönderir: "X servisinin Y ayı faturası eksik, lütfen yükleyin/iletin" + uygulamaya yükleme linki. Mail accounting@e-commint.com kurumsal hesabından gönderilir. Gönderim kaydı tutulur (E6-03). Tetikleme hem manuel (E3-04 "Hatırlatma Gönder" butonu) hem otomatik (E6-04 zamanlama) olabilir.

## Kabul Kriterleri (DOD)
- [ ] Eksik servis için ilgili kişiye (servis Fatura E-posta) mail gönderilir
- [ ] Mail accounting@e-commint.com kurumsal hesabından çıkar
- [ ] Mail içeriği: servis adı, ay, eksik bilgisi, yükleme linki (Türkçe, E6-03 şablonu)
- [ ] Manuel tetikleme: E3-04 ekranındaki "Hatırlatma Gönder" butonu çalışır
- [ ] Gönderim kaydı tutulur (kime, ne zaman, hangi servis/ay) — E6-03
- [ ] İlgili kişi e-postası yoksa uyarı verilir, sessiz başarısızlık olmaz
- [ ] Aynı eksik için kısa sürede mükerrer mail gönderilmez (debounce)

## Alt Görevler
- [ ] Backend: mail gönderim servisi (SMTP, accounting@e-commint.com)
- [ ] Backend: eksik servis → ilgili kişi e-posta çözümleme
- [ ] Backend: `POST /api/reminders/missing-invoice` (manuel tetikleme)
- [ ] Backend: gönderim kaydı (E6-03 ile)
- [ ] Mükerrer gönderim koruması (debounce)
- [ ] Angular: E3-04 "Hatırlatma Gönder" buton entegrasyonu

## Teknik Notlar
- Spring Mail (JavaMailSender) + accounting@e-commint.com SMTP ayarları
- Şablon E6-03'ten gelir; bu görev tetikleme + gönderim + kayıt
- Yükleme linki E3-05 fatura yükleme ekranına (servis+ay önceden dolu) yönlendirmeli
- E6-04 zamanlama bu gönderim servisini çağırır (mantık tekrar yazılmaz)

## Açık Sorular / Riskler
- accounting@e-commint.com SMTP erişim bilgileri/relay hazır mı? — altyapı bağımlılığı
- Yükleme linki giriş gerektiriyorsa kişi hesabı var mı? (harici muhasebeci olabilir) — erişim modeli netleştir
- Mail spam'e düşmemesi için SPF/DKIM ayarları gerekli olabilir
