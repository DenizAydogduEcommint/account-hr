# [E6-04] Hatırlatma zamanlama ve eskalasyon

| Alan | Değer |
|------|-------|
| Epic | E6 — Hatırlatma & Bildirim |
| Sprint | Sprint 5 |
| Öncelik | Orta |
| Bağımlılıklar | E3-04, E6-01, E6-03 |
| Tahmini Efor | 5 puan (~3 gün) |
| Etiketler | backend, zamanlama, eskalasyon |

## Amaç
Eksik fatura hatırlatmalarını otomatik zamanla (örn. ay sonuna doğru) ve cevapsız kalanları eskale et (X gün sonra tekrar gönder, gerekirse yöneticiye/muhasebeye bilgi). Manuel takip yükünü kaldırır.

## Açıklama / Bağlam
E6-01 manuel tetikleniyor; bu görev otomatik zamanlama ekler. Zamanlı bir iş (scheduled job) belirli kurala göre çalışır: ay sonu yaklaşınca o ayın eksik faturalarını bulur (E3-04) ve ilgili kişilere hatırlatma gönderir (E6-01). Eskalasyon: bir hatırlatma X gün içinde sonuçlanmazsa (fatura hâlâ yok) tekrar gönderilir; belirli sayıda tekrardan sonra yöneticiye/muhasebeye eskalasyon bildirimi yapılır. Son gönderim zamanı E6-03 kaydından okunur.

## Kabul Kriterleri (DOD)
- [ ] Zamanlı iş tanımlı (örn. ay sonu / haftalık) ve konfigüre edilebilir
- [ ] Zamanlı iş o dönemin eksik faturalarını (E3-04) bulur ve hatırlatma gönderir (E6-01)
- [ ] X gün cevapsız (fatura hâlâ eksik) → tekrar hatırlatma gönderilir
- [ ] Belirli tekrar sayısından sonra yönetici/muhasebeye eskalasyon bildirimi gider
- [ ] Fatura geldiğinde o servis için hatırlatma döngüsü durur
- [ ] Gönderim/tekrar kayıtları E6-03'te izlenir
- [ ] Zamanlama ve eşik değerleri (gün, tekrar sayısı) ayarlanabilir

## Alt Görevler
- [ ] Backend: zamanlı iş (Spring `@Scheduled` / cron)
- [ ] Backend: eksik tarama + E6-01 çağrısı
- [ ] Backend: eskalasyon kuralı (X gün, N tekrar → yönetici bildirimi)
- [ ] Backend: döngü durdurma (fatura gelince)
- [ ] Konfigürasyon (zamanlama, eşikler)
- [ ] Eskalasyon bildirimi (mail/in-app)

## Teknik Notlar
- Spring `@Scheduled` veya Quartz; çok örnekli ortamda çift çalışmayı önle (lock)
- "Cevapsız" tanımı = ilgili servis-ay hâlâ eksik (fatura yüklenmemiş) — E3-04 ile sorgulanır
- Son gönderim zamanı E6-03 kaydından; debounce E6-01 ile uyumlu
- Eskalasyon alıcısı yönetici/muhasebe rolü (E3-08)

## Açık Sorular / Riskler
- Zamanlama kuralı net mi (ay sonu mu, her hafta mı, kesim tarihine göre mi)? — iş kuralı netleştir
- Eskalasyon kaç tekrardan sonra ve kime? — eşik kararı gerekli
- Tatil/hafta sonu gönderim yapılsın mı? — opsiyonel iyileştirme
