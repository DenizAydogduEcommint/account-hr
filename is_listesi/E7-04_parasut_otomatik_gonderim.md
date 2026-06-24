# [E7-04] Paraşüt'e otomatik gönderim & senkron durumu / hata yönetimi

| Alan | Değer |
|------|-------|
| Epic | E7 — Paraşüt Entegrasyonu |
| Sprint | Sprint 8 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E7-02 (API client), E7-03 (dönüştürücü), E7-05 (mükerrer kontrol) |
| Etiketler | paraşüt, senkron, otomasyon, hata-yönetimi |

## Amaç
Eşleşmiş ve dönüştürülmüş faturaları Paraşüt'e otomatik gider kaydı olarak göndermek; her kaydın senkron durumunu (gönderildi/başarısız/beklemede) izlenebilir tutmak ve hataları yönetmek.

## Açıklama / Bağlam
E7-03 Paraşüt gider DTO'su üretir, E7-02 client API'yi çağırır. Bu görev gönderim akışını orkestre eder: hangi faturalar gönderime hazır (Bulundu/e-Fatura + dönüştürülebilir), gönderim sırası, başarı/başarısızlık takibi, retry. Her faturada bir Paraşüt referansı (kayıt ID) saklanır; bu E7-05 mükerrer kontrolü ve E8 raporlamayı besler.

## Kabul Kriterleri (DOD)
- [ ] Gönderime hazır faturalar belirlenip Paraşüt'e gider olarak gönderiliyor
- [ ] Her fatura için senkron durumu (Gönderildi / Başarısız / Beklemede) + Paraşüt kayıt ID saklanıyor
- [ ] Başarısız gönderimler retry ediliyor; kalıcı hatalar audit log (E8-04) + ekranda görünür
- [ ] Gönderim manuel onaylı veya otomatik modda çalışabiliyor (ayar)
- [ ] E7-05 mükerrer kontrolüyle entegre — aynı fatura iki kez gönderilmiyor
- [ ] Gönderim sonuçları E8 raporlamasına veri sağlıyor

## Alt Görevler
- [ ] Gönderime hazır fatura sorgusu/seçimi
- [ ] Gönderim servisi (E7-03 dönüştür → E7-02 gönder)
- [ ] Senkron durumu + Paraşüt ID persistansı
- [ ] Retry + kalıcı hata yönetimi + audit log
- [ ] Manuel onay / otomatik mod ayarı
- [ ] Durum görünürlüğü (ekran/rapor entegrasyonu)

## Teknik Notlar
- Gönderim asenkron kuyrukla (E5-06 altyapısıyla uyumlu) yapılabilir.
- Manuel onay modu başlangıçta güvenli — muhasebe güveni oturana kadar otomatik kapalı.
- Paraşüt kayıt ID mutlaka saklanmalı (mükerrer kontrol + raporlama + geri izleme).

## Açık Sorular / Riskler
- Başlangıçta tam otomatik mi, onaylı mı? (Yanlış gider kaydı muhasebede sorun yaratır.)
- Paraşüt API kısmi başarı/timeout durumunda kayıt oluştu mu belirsizliği — idempotency anahtarı gerekli.
