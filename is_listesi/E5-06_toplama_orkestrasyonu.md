# [E5-06] Toplama orkestrasyonu & zamanlanmış iş (job, kuyruk, retry)

| Alan | Değer |
|------|-------|
| Epic | E5 — Otomatik Fatura Toplama |
| Sprint | Sprint 7 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E5-01, E5-02, E5-03, E5-04, E5-05 |
| Etiketler | otomasyon, scheduler, queue, retry, orkestrasyon |

## Amaç
Tüm toplama kaynaklarını (mail, Drive waiting, panel worker) tek bir zamanlanmış akışta koordine etmek; ay sonu otomatik çalışan, kuyruk/retry/hata yönetimi olan dayanıklı bir toplama pipeline'ı kurmak.

## Açıklama / Bağlam
E5-01..E5-05 ayrı yetenekler. Bu görev onları orkestre eder: belirli aralıklarla (ör. günlük + ay sonu tetik) mail tarama, Drive pull ve panel indirme job'larını çalıştırır; çıkan belgeleri parse (E5-03) → eşleştirme (E5-04) kuyruğundan geçirir. Hatalı adımlar retry edilir, kalıcı hatalar audit log'a (E8-04) ve bildirime düşer. Servisler master ile çapraz doğrulama: aktif+aylık her servis için o ay faturası geldi mi, gelmediyse "Bekleniyor" + ilgili kişiye mail.

## Kabul Kriterleri (DOD)
- [ ] Zamanlanmış job'lar tanımlı (ör. günlük tarama + ay sonu tam toplama)
- [ ] Mail / Drive / panel kaynakları tek pipeline'da sırayla veya paralel koşuyor
- [ ] Belgeler kuyruk üzerinden parse → eşleştirme adımlarından geçiyor
- [ ] Geçici hatalar retry ediliyor (backoff); kalıcı hatalar audit log + bildirim üretiyor
- [ ] Ay sonu: Servisler master ile çapraz doğrulama — aktif+aylık servis için fatura yoksa "Bekleniyor" satırı ve ilgili kişiye mail tetikleniyor
- [ ] Her çalıştırmanın durumu (başladı/bitti/hata) izlenebiliyor (E8-04 audit)
- [ ] Job tekrar çalışsa bile idempotent (duplicate üretmez)

## Alt Görevler
- [ ] Scheduler kurulumu (Spring `@Scheduled` / Quartz)
- [ ] İş kuyruğu (DB tabanlı kuyruk veya hafif broker) + worker tüketici
- [ ] Retry + backoff + dead-letter politikası
- [ ] Kaynakları (E5-01/02/05) pipeline'a bağlayan orkestratör
- [ ] Servisler master çapraz doğrulama job'u → eksik için "Bekleniyor" + bildirim
- [ ] İzleme/durum kayıtları (E8-04 ile entegre)

## Teknik Notlar
- Spring Scheduling / Quartz. Kuyruk için başlangıçta DB tabanlı (PostgreSQL) yeterli; ölçek artarsa RabbitMQ/Redis.
- Panel worker (E5-05) ayrı süreç/container ise orkestratör onu kuyruk/HTTP ile tetikler.
- Çapraz doğrulama, CLAUDE.md aylık checklist'teki "Servisler sheet'i ile çapraz doğrulama" adımının otomasyonu.
- İlgili kişi mailleri Servisler master'daki Fatura E-posta alanından alınır.

## Açık Sorular / Riskler
- Ay sonu tek seferde mi yoksa sürekli mi toplama? (Servislerin fatura kesim günleri farklı.)
- Panel worker'ı uzun sürer/başarısız olursa pipeline'ı bloklamamalı (asenkron).
- Bildirim spam'i: aynı eksik fatura için tekrar tekrar mail atılmamalı (bildirim de idempotent).
