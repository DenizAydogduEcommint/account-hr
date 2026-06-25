# [E2-02] Servisler master sheet'ini services + service_contacts tablolarına aktar

| Alan | Değer |
|------|-------|
| Epic | E2 — Veri Migrasyonu |
| Sprint | Sprint 2 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-02 |
| Etiketler | backend, migration, excel, import, service-first |

## Amaç
SERVICE-FIRST modelin çekirdeği olan "Servisler" master sheet'ini `services` (ve fatura iletişim bilgilerini `service_contacts`) tablolarına aktararak, eksik fatura tespitinin temelini oluşturmak.

## Açıklama / Bağlam
Yeni sistemin tüm mantığı bu master listeden başlar: "ödenen her servis için her ay fatura bekle". Excel'deki Servisler sheet'i kolonları: Hizmet, Sağlayıcı, Kart, Frekans (Aylık/Yıllık/Kullanım bazlı/Ad-hoc), Aktif (Evet/Hayır/Belirsiz), Aktif Aylar, Yaklaşık Tutar (TL), Fatura E-posta, Fatura Kaynağı (Servis paneli/E-posta/e-Fatura/Drive waiting), Notlar.

Örnek servisler: AWS, Atlassian, Claude AI (iade patterni), Contabo VPS 10/20/30, Google Workspace/One, HepsiBurada, LeasePlan (e-Fatura), Mailjet, Microsoft 365, Ngrok, OpenAI ChatGPT/API, OpenRouter, Pipedrive, Zoom, JetBrains YouTrack (yıllık), Godaddy (yıllık), Allianz/Multinet/Sağlık Sigortası (Ignored).

## Kabul Kriterleri (DOD)
- [x] Servisler sheet'i okunup her satır bir `service`'e (upsert: E2-01 servisini zenginleştirir, yoksa oluşturur)
- [x] Frekans → `frequency` enum (gerçek veride MONTHLY=21/YEARLY=2/USAGE_BASED=2/AD_HOC=7)
- [x] Aktif → `active_state` enum (YES=24/NO=1/UNCERTAIN=7)
- [x] Kart → `card` (varsayılan kart, son-4)
- [x] Sağlayıcı → `provider` (bul-veya-oluştur, dedup)
- [x] Fatura E-posta + Fatura Kaynağı → `service_contacts` (12 contact)
- [x] Yaklaşık Tutar (TL) + Notlar aktarılıyor
- [x] "Aktif Aylar" → `service.active_months` string (V7, bilgi amaçlı; 29 service dolu)
- [x] Ignored servisler (Allianz/Multinet/Sağlık Sigortası) `informational=true`
- [x] Eşleşmeyenler raporlanıyor (master'da olup expense'siz + expense'te olup master'da olmayan)
- [x] Özet rapor: ServiceImportSummary (created/updated/contactsCreated/providersCreated/unmatched listeleri)

## Alt Görevler
- [ ] Servisler sheet okuma + satır parser
- [ ] Frekans/aktiflik/kart/sağlayıcı eşleyiciler
- [ ] `service_contacts` üretimi (mail + kaynak)
- [ ] "Aktif Aylar" parse + period ilişkilendirme
- [ ] Provider deduplikasyonu (aynı firma tek kayıt)
- [ ] E2-01 expense'leri ↔ service eşleme bağlama (foreign key güncelle)
- [ ] Özet rapor

## Teknik Notlar
- Bu görev E2-01 ile birlikte çalışmalı: önce service master, sonra expense'ler service_id'ye bağlanır (veya tersine, sonradan eşleme). Sıra: E2-02 service master → E2-01 expense eşleme önerilir
- Contabo VPS 10/20/30 gibi varyantlar ayrı service kayıtları (farklı tutar/abonelik)
- Claude AI "iade patterni" notu service.notes alanında korunmalı (ileride iade algoritması için ipucu)
- LeasePlan e-Fatura kaynağı, Godaddy/YouTrack yıllık frekans örnekleri doğru eşlenmeli
- "Aktif Aylar" denormalize string; modelde service↔period ilişkisi varsa oradan türetilir, yoksa not olarak saklanır

## Açık Sorular / Riskler
- ~~Hizmet adı eşleşmesi (yazım farkı)?~~ → Normalize (trim+collapse+lowercase) eşleşme; gerçek veride 28/28 sheet servisi mevcut E2-01 servisleriyle eşleşti (created=0, updated=28).
- "Belirsiz" servisler eksik-fatura tespitine dahil mi? → E3 (eksik fatura ekranı) iş kuralında netleşecek. AÇIK.
- Yaklaşık Tutar son ay toplamı → bilgi amaçlı saklandı (approx_amount_try).

## Tamamlanma Kaydı
- Durum: ✅ Tamamlandı — 2026-06-25
- YouTrack: IK-233 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend)
- Üretilenler: ServiceMasterImportService, AdminImportController `POST /api/v1/admin/imports/services`, ServiceImportSummary, Flyway V7 (`services.active_months`), enum mapper'lar
- **Gerçek veri doğrulaması (lokal PG14)**: Servisler import → 28 service zenginleşti (frequency MONTHLY=21/YEARLY=2/USAGE_BASED=2/AD_HOC=7, active_state YES=24/NO=1/UNCERTAIN=7), 12 contact, 29 active_months, informational=Allianz/Multinet/Sağlık. Idempotency: 2. import created=0/contactsCreated=0.
- Test: `./mvnw test` 58/58 (3 sırada da: alphabetical/reverse/default — CI uyumlu)
- **KRİTİK BUG yakalandı (E1-05 audit kusuru):** auth context altında entity güncelleme → `SecurityAuditorAware.findByEmail` (FlushMode AUTO) → audit `@PreUpdate` → sonsuz özyineleme → StackOverflowError 500. H2 testleri SecurityContext olmadığı için kaçırdı; **yalnızca gerçek Postgres + authenticated path ortaya çıkardı.** Fix: JPQL + `FlushMode.COMMIT`. Ek: GlobalExceptionHandler artık 500'leri logluyor (sebep görünürlüğü); ServiceMaster `findAll(Sort.by(id))` idempotency.
- expenses/ Drive aynasına dokunulmadı (xlsx read-only).
