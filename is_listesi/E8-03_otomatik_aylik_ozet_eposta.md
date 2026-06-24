# [E8-03] Otomatik aylık özet & e-posta gönderimi

| Alan | Değer |
|------|-------|
| Epic | E8 — Mali Müşavir Paylaşımı & Raporlama |
| Sprint | Sprint 8 |
| Öncelik | Orta |
| Tahmini Efor | 3 puan (~2 gün) |
| Bağımlılıklar | E8-01 (paket export), E8-02 (raporlar) |
| Etiketler | raporlama, e-posta, otomasyon, mali-müşavir |

## Amaç
Her ay sonunda ilgililere (yönetim + mali müşavir) otomatik bir özet e-postası göndermek: ayın toplam harcaması, eşlenen/eksik fatura durumu ve paket/Drive linki. Manuel "muhasebeye ilet" adımını otomatikleştirir.

## Açıklama / Bağlam
E8-02 raporları ve E8-01 paketi hazır olduğunda, sistem ay sonu (veya kapanış tetiği) otomatik bir özet mail oluşturur ve gönderir: kısa metin/HTML özet + eksik fatura uyarıları + paket linki. Alıcılar konfigüre edilebilir (mali müşavir e-postası, Kaan, ilgili yönetici).

## Kabul Kriterleri (DOD)
- [ ] Ay sonu (veya manuel tetik) otomatik özet e-postası üretiliyor
- [ ] Özet: toplam harcama, eşlenen/eksik sayısı, durum dağılımı, eksik servis listesi
- [ ] E8-01 paketi / Drive linki maile ekleniyor veya linkleniyor
- [ ] Alıcı listesi konfigüre edilebilir
- [ ] Gönderim audit log'a (E8-04) yazılıyor
- [ ] Eksik fatura varsa belirgin uyarı içeriyor

## Alt Görevler
- [ ] Özet içerik üretici (E8-02 verisinden HTML/metin)
- [ ] E-posta gönderim servisi (SMTP / Gmail API)
- [ ] Alıcı konfigürasyonu
- [ ] Ay sonu tetik (E5-06 scheduler ile)
- [ ] Audit log entegrasyonu

## Teknik Notlar
- Gönderim için E5-01 mail altyapısı/Gmail API yeniden kullanılabilir.
- HTML şablon (Thymeleaf vb.) ile düzgün özet.
- Tetik E5-06 scheduler'a bağlanır.

## Açık Sorular / Riskler
- Ne zaman gönderilmeli: tüm faturalar toplandığında mı, sabit tarihte mi? (Eksik varken erken mail kafa karıştırabilir.)
- Mali müşavire doğrudan mı, önce iç onaydan sonra mı?
