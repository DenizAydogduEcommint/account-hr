# [E3-10] Eksik fatura tutar özeti (belgesiz gider görünürlüğü)

| Alan | Değer |
|------|-------|
| Epic | E3 — Fatura Yönetim Web Uygulaması |
| Sprint | Sprint 3 |
| Öncelik | Orta |
| Bağımlılıklar | E3-01, E3-04 |
| Tahmini Efor | 2 puan (~1 gün) |
| Etiketler | frontend, backend, muhasebe, raporlama |

## Amaç
Eksik faturaların **yaklaşık TL toplamını** dashboard'da ve eksik fatura ekranında göster. Muhasebe açısından eksik fatura = **belgesiz gider**; sadece adet değil, "kaç TL belgesiz gider bekliyor" görünürlüğü KKEG/belge riski takibi için gereklidir.

## Açıklama / Bağlam
Bir ön-muhasebe domain incelemesinde (2026-06-26) tespit edildi: dashboard ve eksik fatura ekranı eksik **sayısını** veriyor ama **tutarını** vermiyor. Muhasebeci için belgesiz giderin TL büyüklüğü, adetten daha önemli bir risk göstergesidir. Eksik her servisin "yaklaşık tutarı" (son görülen ayın TL toplamı) zaten `MissingInvoiceRow`'da mevcut; bunların toplamı hesaplanıp gösterilecek.

## Kabul Kriterleri (DOD)
- [ ] Backend: eksik fatura listesi yanında **yaklaşık TL toplamı** (`missingApproxTotalTry`) döner
- [ ] Backend: Dashboard summary'ye eksik fatura yaklaşık tutar toplamı eklenir (`missingTotalTry`)
- [ ] Frontend: Dashboard'da eksik fatura kartı sayı + yaklaşık TL toplamı gösterir
- [ ] Frontend: Eksik fatura ekranında liste toplamı (yaklaşık TL) gösterilir
- [ ] Tutar tr-TR `#.##0,00 ₺` formatında; "yaklaşık" olduğu (~) belirtilir
- [ ] Dashboard sayacı ile eksik fatura ekranı toplamı tutarlı

## Alt Görevler
- [ ] Backend: MissingInvoiceService — eksik satırların `approxAmountTry` toplamı
- [ ] Backend: MissingInvoiceResponse'a toplam alanı + DashboardSummary'ye `missingTotalTry`
- [ ] Angular: Dashboard eksik kartında tutar
- [ ] Angular: Eksik fatura ekranı toplam satırı

## Teknik Notlar
- "Yaklaşık" çünkü tutar son görülen ayın TL'sinden tahmin (fatura henüz gelmediği için kesin tutar bilinmiyor); UI'da ~ ile belirt
- Null yaklaşık tutarlı (hiç görülmemiş) servisler toplama 0 katkı verir
- Dashboard `missingCount` ile aynı kaynaktan beslenir (tutarlılık)

## Açık Sorular / Riskler
- Yaklaşık tutarı olmayan (ilk kez beklenen) servis → tutar tahmini yok; toplama 0 katkı, adette sayılır

## Tamamlanma Kaydı
- Durum: Tamamlandı — 2026-06-26
- YouTrack: IK-287 (YouTrack'ten teyit edildi 2026-06-29)
- Repo: account-hr (backend) + account-hr-frontend
- **Kaynak:** Ön-muhasebe domain incelemesi (2026-06-26) — "belgesiz gider TL görünürlüğü" tespiti.
- **Backend:** `GET /api/v1/missing-invoices` artık wrapper `{items, count, approxTotalTry}` (önceden çıplak liste — breaking, tüm çağıran test'ler güncellendi). `MissingInvoiceService.approxTotalTry` (null→+0, scale 2 HALF_UP). `DashboardSummary.missingTotalTry` — `missingCount` ile **aynı row-set'ten** türetiliyor (tutarsızlık imkânsız).
- **Frontend:** Dashboard eksik kartı "N · ~X ₺"; eksik fatura ekranında banner + tfoot toplam satırı ("~X ₺"). tr-TR format, `~` ile yaklaşık işareti. Wrapper/array defensive.
- **Gerçek doğrulama (lokal PG14):** Mart eksik=2, `approxTotalTry=2.620,05` (HepsiBurada ~69,90 + Zoom ~2.550,15); dashboard `missingTotalTry=2.620,05` **birebir tutarlı**; totalTry=114.355,40 regresyonsuz.
- Test: backend 287/287 (+3); frontend tsc+ng build temiz. Bağımsız review: **0 bulgu** (sözleşme uyumu birebir, count↔total tek-kaynak, null-handling test'li, format doğru).
