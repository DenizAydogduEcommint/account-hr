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
- [x] Aktif + Aylık her servis için seçili ayda Bulundu/e-Fatura kontrolü (yoksa eksik)
- [x] Eksik servisler liste (servis adı, sağlayıcı, kart, yaklaşık tutar, ilgili e-posta, frekans)
- [x] Yıllık servisler yalnızca beklenen ayında (activeMonths TAM eşleşme — substring değil)
- [x] Kullanım bazlı / Ad-hoc / pasif / informational hariç
- [x] "Fatura Yükle" butonu (E3-05 için placeholder, disabled)
- [x] "Hatırlatma Gönder" butonu (E6 için placeholder, disabled)
- [x] **Eksik sayısı dashboard sayacıyla BİREBİR** (DashboardService missingCount = MissingInvoiceService; Mart 2=2)
- [x] Ay seçici ile farklı aylar

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
- ~~Yıllık beklenen ay?~~ → `service.active_months` (virgüllü "YYYY-MM") TAM eşleşme; o ay listede varsa kontrol edilir.
- ~~Çoklu çekim?~~ → Servis-ay bazında en az bir Bulundu/e-Fatura satırı yeterli (çekim-bazlı E4'te).
- ~~İade/refund (Claude AI)?~~ → İade satırları AD_HOC/informational → eksik kontrolüne girmez.

## Tamamlanma Kaydı
- Durum: ✅ Tamamlandı — 2026-06-26 · **MVP ÇEKİRDEĞİ**
- YouTrack: IK-241 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend) + account-hr-frontend
- **Backend:** MissingInvoiceService (çapraz doğrulama: aktif+aylık her ay / yıllık activeMonths'ta / usage-adhoc-informational-pasif hariç; eksik = o ay FOUND/E_INVOICE yok). `GET /api/v1/missing-invoices?month=`. MissingInvoiceRow DTO. **Dashboard missingCount bu servis-bazlı mantığa bağlandı (birebir DOD).** 4 toplu sorgu (N+1 yok).
- **Frontend:** Eksik Fatura ekranı (belirgin sayaç + tablo + satır aksiyonları Fatura Yükle/Hatırlatma placeholder + boş durum "tüm faturalar tamam"), sidebar "Eksik Fatura" aktif.
- **Gerçek doğrulama (lokal PG14 + tarayıcı):** Mart 2026 → **tam 2 eksik** (HepsiBurada Mağaza ~69,90; Zoom Workplace Pro ~2.550,15), **dashboard missingCount=2 (birebir)**, Şubat=2, bozuk ay→400. Tarayıcıda sayaç + tablo + aksiyon butonları + Aylık badge görsel onaylı.
- Test: backend `./mvnw test` 161/161 (3 surefire sırasında; +15, her frekans senaryosu + dashboard-tutarlılık); frontend `ng build`/`tsc` temiz.
- **Bağımsız review: "no new issues"** (frekans kuralı, YEARLY exact-match, found tanımı, dashboard birebir, N+1 yok, security, frontend hepsi temiz).
