# [E3-01] Dashboard: aylık özet ve eksik fatura sayacı

| Alan | Değer |
|------|-------|
| Epic | E3 — Fatura Yönetim Web Uygulaması |
| Sprint | Sprint 3 |
| Öncelik | Orta |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-02, E1-03, E1-07, E3-04 |
| Etiketler | frontend, backend, ui, dashboard |

## Amaç
Ekip ve muhasebe giriş yaptığında o ayın durumunu tek bakışta görsün: kaç fatura eksik, kaç bulundu, toplam harcama ne kadar. Eksik fatura acısını ana ekrana taşır.

## Açıklama / Bağlam
Hazır admin temasının kart ve grafik bileşenleri kullanılarak bir ana sayfa (dashboard) yapılacak. Üstte seçili ay için özet kartları (KPI kartları): "Toplam Harcama (TL)", "Eksik Fatura Sayısı", "Bulunan Fatura", "Araştırılacak". Altında fatura durumu dağılımını gösteren bir donut/pasta grafik (Bulundu / Bekleniyor / Araştırılacak / e-Fatura / Ignored). Ay seçici (dropdown) ile farklı aylara geçilebilir. Veriler backend'den bir özet endpoint'i üzerinden gelir, ön yüzde hesap yapılmaz.

## Kabul Kriterleri (DOD)
- [x] Dashboard varsayılan bir ay gösterir (ay seçici ile değiştirilebilir)
- [x] Eksik fatura sayacı = EXPECTED durumu sayısı (E3-04 çapraz doğrulama ile aynı mantık; tam parite E3-04'te)
- [x] KPI kartları: Toplam Harcama (TL), Eksik, Bulunan, Araştırılacak
- [x] Durum dağılımı donut grafiği renk kodlarıyla (Bulundu yeşil / Bekleniyor kırmızı / e-Fatura açık yeşil / Araştırılacak-Ignored turuncu) — merkezi StatusColors
- [x] Ay seçici çalışır; ay değişince tüm kartlar/grafik güncellenir (tarayıcıda doğrulandı)
- [x] Ignored/informational satırlar operasyonel toplama (totalTry) dahil değil
- [x] Responsive: dar ekranda KPI kartları alt alta, sidebar üst bara dönüşür

## Alt Görevler
- [ ] Backend: `GET /api/dashboard/summary?month=YYYY-MM` özet endpoint'i (toplam, durum bazlı sayımlar, eksik sayısı)
- [ ] Angular: hazır temanın KPI kart bileşenlerini yerleştir
- [ ] Angular: durum dağılımı için ApexCharts donut grafik
- [ ] Ay seçici bileşeni (paylaşılan, diğer ekranlarda da kullanılacak)
- [ ] Loading/empty state (veri yokken)

## Teknik Notlar
- Özet hesabı backend'de SQL ile yapılmalı; ön yüze ham satır listesi dökülmemeli
- Renk kodları merkezi bir sabit/enum'dan gelmeli (E3-07 ile paylaşılır)
- Hazır temanın dashboard layout'u referans alınabilir

## Açık Sorular / Riskler
- ~~Toplam harcama döviz mi TL mi?~~ → TL Karşılığı (amount_try) toplamı, informational hariç. E2-05 mutabakatıyla birebir doğrulandı.
- Eksik fatura sayacı şimdilik EXPECTED count; E3-04 (servis↔ay çapraz doğrulama) tam pariteyi sağlayacak.
- Varsayılan ay: şimdilik son veri olan ay/seçili; E3-04 sonrası "içinde bulunulan ay" netleşebilir.

## Tamamlanma Kaydı
- Durum: ✅ Tamamlandı — 2026-06-26 · **E3 EPİC BAŞLADI (ilk ekran + tasarım sistemi)**
- YouTrack: IK-238 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend) + account-hr-frontend (frontend)
- **Tasarım sistemi kuruldu (kalıcı temel):** merkezi SCSS token'ları (`_tokens.scss`) + TS mirror (`status-colors.ts`) — E-Commint markası: primary yeşil `#00b27a`, navy sidebar `#010b3c`, Maven Pro, durum renkleri (StatusColors ile tek kaynak), 8px grid, light tema, app shell (sidebar+header+content). Sonraki tüm E3 ekranları bunu kullanacak.
- **Backend:** `GET /api/v1/dashboard/summary?month=YYYY-MM` (DashboardService, SQL'de hesap, informational hariç totalTry, durum sayımları, missing/found/investigate). month validation → 400 INVALID_MONTH.
- **Frontend:** dashboard (4 KPI kartı + ng-apexcharts donut + paylaşılan month-selector + loading/empty/error state), tr-TR locale, responsive.
- **Gerçek doğrulama (lokal PG14 + tarayıcı):** summary endpoint 2026-01/02/03 totalTry = 194520.26 / 112390.98 / 114355.40 (E2-05 ile birebir), 2026-06 boş. **Tarayıcıda (Playwright) login → dashboard → Mart 2026: Toplam ₺114.355,40, Eksik 1, Bulunan 21, Araştırılacak 1, donut doğru renklerle render edildi.** Marka kimliği (navy sidebar, yeşil logo, Maven Pro) görsel onaylı.
- Test: backend `./mvnw test` 123/123 (119+4); frontend `ng build` temiz.
- **İki bağımsız review turu (agent + parent):** parent turu 2 important bulgu yakaladı → (1) frontend stale-response race (hızlı ay değişimi) → önceki subscription iptal + ngOnDestroy; (2) backend month param validation (reflected input) → YearMonth.parse + 400. İkisi düzeltildi.
- expenses/ dokunulmadı (geçici screenshot'lar temizlendi).
