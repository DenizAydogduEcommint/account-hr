# [E3-04] EKSİK FATURA ekranı: servis ↔ ay çapraz doğrulama (KİLİT MVP)

| Alan | Değer |
|------|-------|
| Epic | E3 — Fatura Yönetim Web Uygulaması |
| Sprint | Sprint 2 |
| Öncelik | Yüksek |
| Bağımlılıklar | E1-02, E1-03, E1-07, E3-02, E3-03 |
| Tahmini Efor | 8 puan (~5 gün) |
| Etiketler | frontend, backend, core, eksik-fatura |

## Amaç
Projenin asıl acı noktasını çözer: "Bu ay hangi servislerin faturası bekleniyordu ama gelmedi?" sorusunu otomatik yanıtlar. Banka ekstresinden değil, SERVİSLER master listesinden giderek eksikleri tespit eder.

## Açıklama / Bağlam
CLAUDE.md'deki çapraz doğrulama kuralının çekirdeği: Servisler sheet'inde **Aktif = Evet ve Frekans = Aylık** olan her servis için, seçili ayda bir harcama satırı VE/VEYA yüklenmiş bir fatura olmalı. Olmayanlar "Eksik" listesinde gösterilir. Yıllık servisler sadece beklenen ayında (Aktif Aylar bilgisine göre) kontrol edilir; her ay eksik sayılmaz. Kullanım bazlı/Ad-hoc servisler eksik kontrolüne girmez.

Ekranda seçili ay için: solda "Eksik Faturalar" (beklenen ama bulunmayan servisler), sağ/alt tarafta isteğe bağlı "Beklemede/Araştırılacak" satırlar. Her eksik satırdan doğrudan "Fatura Yükle" (E3-05) ve "Hatırlatma Gönder" (E6) aksiyonları tetiklenebilir.

## Kabul Kriterleri (DOD)
- [ ] Aktif + Aylık her servis için seçili ayda fatura/satır var mı kontrolü yapılır
- [ ] Eksik servisler liste halinde gösterilir (servis adı, sağlayıcı, kart, yaklaşık tutar, ilgili kişi e-postası)
- [ ] Yıllık servisler yalnızca beklenen ayında eksik sayılır (Aktif Aylar / yenileme ayı mantığı)
- [ ] Kullanım bazlı / Ad-hoc servisler eksik kontrolüne dahil edilmez
- [ ] Eksik satırından "Fatura Yükle" aksiyonu E3-05 akışını açar
- [ ] Eksik satırından "Hatırlatma Gönder" aksiyonu E6 akışını tetikler (E6 hazır değilse buton placeholder)
- [ ] Eksik sayısı dashboard sayacıyla (E3-01) birebir aynı
- [ ] Ay seçici ile farklı aylar kontrol edilebilir

## Alt Görevler
- [ ] Backend: çapraz doğrulama servisi — `GET /api/missing-invoices?month=YYYY-MM`
- [ ] Backend: frekans mantığı (Aylık her ay, Yıllık beklenen ay, Kullanım bazlı/Ad-hoc hariç)
- [ ] Backend: "fatura var mı" tanımı (harcama satırı durumu Bulundu/e-Fatura VEYA yüklenmiş dosya)
- [ ] Angular: eksik fatura liste ekranı
- [ ] Angular: satır içi aksiyon butonları (Fatura Yükle, Hatırlatma Gönder)
- [ ] Ay seçici entegrasyonu

## Teknik Notlar
- Bu mantık birim testlerle korunmalı (her frekans tipi için ayrı senaryo)
- "Yıllık servisin beklenen ayı" verisi: Aktif Aylar veya servis üzerinde yenileme ayı alanı gerekebilir — E3-02/E1-02 ile koordine
- Eksik tanımı = (Aktif + Aylık servis) AND (o ay için Bulundu/e-Fatura durumlu satır YOK)
- Bekleniyor durumundaki satır da "henüz fatura yok" demektir → eksik kabul edilir

## Açık Sorular / Riskler
- Yıllık servisin hangi ay beklendiği nasıl belirlenecek? (yenileme ayı alanı mı, Aktif Aylar parse mı?) — Karar gerekli
- Aynı servisin ay içinde birden çok çekimi (OpenAI API) varsa "tek fatura yeterli mi yoksa her çekim için mi" — Öneri: servis-ay bazında en az bir fatura yeterli, ama E4 ile çekim-bazlı eşleştirme detaylandırılır
- İade/refund olan servis (Claude AI) net çekim yoksa eksik sayılmamalı
