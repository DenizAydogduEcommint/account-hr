# [E2-04] Fatura durumu ve renklerini invoice_status enum'una migrate et

| Alan | Değer |
|------|-------|
| Epic | E2 — Veri Migrasyonu |
| Sprint | Sprint 3 |
| Öncelik | Orta |
| Tahmini Efor | 3 puan (~2 gün) |
| Bağımlılıklar | E1-02, E2-01, E2-03 |
| Etiketler | backend, migration, enum, status |

## Amaç
Excel'deki metin + renkle ifade edilen fatura durumlarını, yeni sistemin `invoice_status` enum değerlerine güvenilir şekilde dönüştürmek ve her expense/invoice'a doğru durumu atamak.

## Açıklama / Bağlam
Mevcut Excel'de fatura durumu hem metinle (Bulundu, e-Fatura, Bekleniyor, Araştırılacak, Ignored) hem hücre rengiyle kodlanmış:
- Bulundu → yeşil `4CAF50`
- e-Fatura → açık yeşil `8BC34A`
- Bekleniyor → kırmızı `FF4444`
- Araştırılacak → turuncu `FF9800`
- Ignored → turuncu `FF9800`

Metin ve renk çoğu zaman örtüşür ama Araştırılacak ile Ignored aynı renkte (FF9800) — bu nedenle **metin önceliklidir**, renk doğrulama/yedek olarak kullanılır. E2-03'te dosyası bulunan satırların durumu da tutarlılık için güncellenmeli.

## Kabul Kriterleri (DOD)
- [x] Durum metni → `invoice_status` enum (E2-01 + ortak `StatusText` mapper)
- [x] Hücre dolgu rengi okunup metin ile çapraz kontrol (4CAF50/8BC34A/FF4444/FF9800, ARGB son-6-hane)
- [x] Araştırılacak vs Ignored metinle ayrılıyor (FF9800 ortak renk → metin karar verir, yanlış mismatch yok)
- [x] Dosyası bulunan ama EXPECTED kalmış invoice tutarsızlığı raporlanıyor (autofix opsiyonel, default false)
- [x] Renk→durum tablosu kod sabiti `StatusColors` (frontend badge renkleri tek kaynak)
- [x] Her invoice geçerli enum; tanımsız yok (gerçek veride undefinedStatus=0)
- [x] Özet rapor: durum dağılımı + textColorMismatch + fileStatusInconsistent + fileStatusFixed

## Alt Görevler
- [ ] Durum metni normalizasyonu + enum eşleme
- [ ] Hücre dolgu rengi okuma (POI/openpyxl) ve renk→durum tablosu
- [ ] Metin-renk çelişki tespiti (öncelik metin)
- [ ] E2-03 dosya varlığı ile durum tutarlılık kontrolü
- [ ] Tanımsız/eksik durum için fallback ve raporlama

## Teknik Notlar
- Renk kodları CLAUDE.md ile bire bir; frontend status badge renkleri de buradan beslenir (tek kaynak)
- Bu görev E2-01 (satır okuma) ve E2-03 (dosya eşleme) sonrası çalışır; idealde E2-01 import'unda durum metni okunur, burada enum'a kesinleştirilir
- "Ignored" satırları (Multinet/Allianz) bilgi amaçlı; eksik-fatura tespitine dahil edilmez

## Açık Sorular / Riskler
- ~~Renk okuması tema/koşullu biçimlendirme mi?~~ → Solid fill + doğrudan RGB (keşifle doğrulandı); POI `getARGBHex()` son-6-hane okunuyor. Renk okunamazsa (null/indexed) güvenli atlama.
- ~~Metin-renk çelişkisi otomatik mi?~~ → **Raporla + manuel** (autofix default false). Metin öncelikli.

## Tamamlanma Kaydı
- Durum: ✅ Tamamlandı — 2026-06-25
- YouTrack: IK-235 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend)
- Üretilenler: StatusColors (renk↔durum tablosu), StatusText (E2-01'den çıkarılan ortak mapper), StatusAuditService (Part A: Excel metin-renk audit), StatusAuditDbService (Part B: dosya-durum + opsiyonel autofix, @Transactional), StatusAuditSummary, endpoint `POST /api/v1/admin/imports/status-audit?autofix=false`
- **Gerçek veri doğrulaması (lokal PG14)**: durum dağılımı FOUND 55 / IGNORED 23 / EXPECTED 19 / E_INVOICE 2 / TO_INVESTIGATE 2 = 101. **textColorMismatch=0** (metin-renk %100 tutarlı), **fileStatusInconsistent=0** (dosyası olan EXPECTED yok), undefinedStatus=0. autofix=false DB'yi değiştirmedi; veri zaten tutarlı (migration kalitesi yüksek).
- Test: `./mvnw test` 79/79 (3 surefire sırasında; autofix path StatusAuditIT'de kanıtlı)
- **İki bağımsız review (agent 2 tur + parent 1 tur):** parent review 2 important bulgu yakaladı → (1) @Lazy self-injection transaction kırılganlığı → ayrı StatusAuditDbService; (2) implicit cross-join → explicit JOIN...ON. İkisi düzeltildi.
- expenses/ Drive aynasına dokunulmadı (Excel read-only; autofix yalnız DB invoice.status günceller).
