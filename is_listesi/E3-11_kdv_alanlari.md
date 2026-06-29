# [E3-11] KDV alanları (fatura KDV ayrımı)

| Alan | Değer |
|------|-------|
| Epic | E3 — Fatura Yönetim Web Uygulaması |
| Sprint | Sprint 3 |
| Öncelik | Orta |
| Bağımlılıklar | E3-05, E3-09 |
| Tahmini Efor | 3 puan (~2 gün) |
| Etiketler | backend, frontend, muhasebe, kdv |

## Amaç
Fatura tutarından **KDV ayrımı** yapılabilsin: KDV oranı, KDV matrahı (net) ve KDV tutarı tutulsun. Muhasebeye iletilen veride KDV görünür olur — **indirilebilir KDV takibi** ve gerçek muhasebe entegrasyonu için temel. Ön-muhasebe domain incelemesinde (2026-06-26) eksik olarak tespit edildi.

## Açıklama / Bağlam
Şu an fatura/expense yalnızca **brüt** (KDV dahil) TL tutarı tutuyor; KDV ayrımı yok. Bir muhasebeci faturanın KDV matrahını ve KDV tutarını ister. KDV bilgisi **fatura belgesine** aittir → `Invoice`'a eklenir (expense brüt kalır). Fatura yüklenirken (E3-05) veya durum güncellenirken KDV oranı girilir; brüt tutardan matrah ve KDV otomatik hesaplanır:
- `matrah (net) = brüt / (1 + oran/100)`
- `kdv = brüt − matrah`
- İade (negatif brüt) → KDV de negatif (tutarlı).

Türkiye'de yaygın oranlar: %0, %1, %10, %20 (+ serbest giriş). KDV oranı **opsiyonel** — girilmezse alanlar null (eski faturalar etkilenmez).

## Kabul Kriterleri (DOD)
- [ ] `Invoice`'a KDV alanları: `kdvRate` (%, nullable), `kdvAmountTry` (TL, nullable), `netAmountTry` (matrah TL, nullable) + migration
- [ ] Fatura yükleme (E3-05) modalında KDV oranı seçimi (yaygın oranlar + serbest, opsiyonel)
- [ ] KDV oranı verilince backend matrah + KDV'yi brüt tutardan hesaplar (scale 2, HALF_UP)
- [ ] Harcama detay panelinde (E3-09) fatura KDV bilgisi gösterilir (oran / matrah / KDV)
- [ ] KDV oranı yoksa alanlar null; UI "KDV girilmemiş" gösterir, hesaba 0 katkı
- [ ] İade (negatif) faturalarda KDV doğru işaretli (negatif)
- [ ] (Opsiyonel) Aylık toplam KDV özeti — sonraki iterasyona bırakılabilir

## Alt Görevler
- [ ] Backend: Invoice entity + V18 migration (kdv kolonları, nullable)
- [ ] Backend: KDV hesabı (brüt→matrah+KDV), upload/güncelleme akışına bağlama
- [ ] Backend: DTO'lara KDV alanları (InvoiceUploadRequest kdvRate; ExpenseRow/file detayına KDV)
- [ ] Angular: yükleme modalında KDV oranı seçimi
- [ ] Angular: detay panelinde KDV gösterimi

## Teknik Notlar
- KDV `Invoice`'a ait (fatura belgesi); `Expense.amountTry` brüt kalır (kart çekim)
- Para alanları BigDecimal scale 2; oran tam sayı veya 1 ondalık
- KDV hesabı brüt'ten geriye (matrah = brüt/(1+oran)) — fatura KDV-dahil tutar üzerinden
- Mevcut faturalar (KDV oranı null) etkilenmez; ddl-auto=validate uyumu

## Açık Sorular / Riskler
- Çok kalemli faturada farklı KDV oranları? → MVP'de fatura başına tek oran; kalem-bazlı KDV sonraki epic
- Tevkifat / özel matrah? → MVP dışı, standart KDV

## Tamamlanma Kaydı
- Durum: Tamamlandı — 2026-06-26
- YouTrack: IK-288 (YouTrack'ten teyit edildi 2026-06-29)
- Repo: account-hr (backend) + account-hr-frontend
- **Kaynak:** Ön-muhasebe domain incelemesi (2026-06-26) — KDV ayrımı eksikliği tespiti.
- **Backend:** `Invoice`'a `kdvRate`/`kdvAmountTry`/`netAmountTry` (nullable) + **V18** migration. `KdvCalculator`: `net = brüt/(1+oran/100)` (scale 2 HALF_UP), `kdv = brüt − yuvarlanmış_net` (**sum-safe: matrah+KDV=brüt kuruşuna eşit**). `POST /api/v1/invoices` opsiyonel `kdvRate` (0–100; geçersiz→400). `ExpenseRow`'a 3 KDV alanı (temsilci invoice'tan). Brüt = `expense.amountTry` (KDV-dahil); iade negatif → net/kdv negatif tutarlı.
- **Frontend:** Yükleme modalında KDV oranı seçimi (%0/1/10/20 + Diğer; varsayılan "KDV girme"; oran sadece doluyken gönderilir). Detay panelinde 3 durumlu KDV gösterimi: oran+matrah+KDV+brüt / "oran kaydedildi ama TL bilinmediğinden hesaplanamadı" / "KDV girilmemiş". tr-TR format.
- **Gerçek doğrulama (lokal PG14, V18):** invoices KDV kolonları oluştu; mevcut faturalar KDV-null (geriye uyumlu); regresyon yok (eksik=2, dashboard=114.355,40).
- Test: backend 295/295 (+8: KdvCalculatorTest + InvoiceUploadIT KDV senaryoları — 120@%20→matrah 100/KDV 20, iade negatif, geçersiz oran 400); frontend tsc+ng build temiz.
- **Bağımsız review:** kritik noktalar TEMİZ (matrah+KDV=brüt sum-safe, field uyumu birebir, validation, rate-0 muaf, temsilci invoice); 1 UX bulgusu düzeltildi (oran var/TL yok → açıklayıcı mesaj).
