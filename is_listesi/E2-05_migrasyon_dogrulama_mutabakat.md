# [E2-05] Migrasyon doğrulama / mutabakat: Excel toplamları ↔ DB, idempotency, rapor

| Alan | Değer |
|------|-------|
| Epic | E2 — Veri Migrasyonu |
| Sprint | Sprint 3 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E2-01, E2-02, E2-03, E2-04 |
| Etiketler | backend, migration, validation, reconciliation |

## Amaç
Migrasyonun doğru ve eksiksiz olduğunu kanıtlamak: Excel'deki toplamlar ile DB toplamlarının tutması, importer'ların güvenle yeniden çalıştırılabilmesi (idempotent) ve denetlenebilir bir mutabakat raporu üretmek.

## Açıklama / Bağlam
Migrasyon kritik: muhasebe verisi yanlış aktarılırsa güven kaybı olur. Bu görev E2-01..E2-04 sonuçlarını doğrular. Excel'deki her ay sheet'inin TOPLAM satırı ile DB'deki o period toplamı karşılaştırılır; satır sayıları, fatura sayıları, durum dağılımları kontrol edilir. Ayrıca importer'lar tekrar çalıştırıldığında veri çiftlenmemeli (idempotency).

## Kabul Kriterleri (DOD)
- [x] Period TL mutabakatı (±0.01): Excel ana `TOPLAM:` (Multinet/Sigorta hariç) ↔ DB sum(amount_try) informational=false — 2026-01/02/03 MATCH (diff=0)
- [x] Satır sayısı mutabakatı: Excel ana harcama satırı ↔ DB expense (informational hariç) — 11/28/23/16 tuttu
- [x] Fatura/dosya mutabakatı: storage fizik ↔ `files` kayıt — 55==55
- [x] Durum dağılımı mutabakatı: DB enum sayıları (E2-04 ile tutarlı)
- [x] İdempotency anahtarları dokümante (expense→source_row_hash, service→normalize(ad)+provider, file→sha256)
- [x] Mutabakat raporu (period bazlı tablo + farklar + uyarılar + genel ok)
- [x] Tutarsızlık listesi (inconsistencies[]) — gerçek veride yalnız Nisan WARNING

## Alt Görevler
- [ ] Excel TOPLAM satırı okuyucu + period eşleme
- [ ] DB toplama sorguları (period bazlı TL, satır, fatura, durum)
- [ ] Karşılaştırma motoru + eşik/tolerans
- [ ] İdempotency için doğal anahtar/hash stratejisi (her importer'a uygula)
- [ ] Mutabakat raporu (markdown/HTML veya API response)
- [ ] Tutarsızlık detay listesi

## Teknik Notlar
- İdempotency anahtarı önerileri: expense → (period, hizmet, tutar, tarih, kart) hash; service → (hizmet, kart); file → SHA-256
- Bilgi amaçlı bölümler (Ignored) toplamlara dahil mi? Excel'de ana TOPLAM bunları içermiyor — DB sorgusu da hariç tutmalı (CLAUDE.md: TOPLAM sadece ana harcamalar)
- Çoklu para birimi: mutabakat TL karşılığı üzerinden yapılır (orijinal döviz değil)
- Bu görev migrasyonun "kabul testi"; geçmeden production'a geçilmez

## Açık Sorular / Riskler
- ~~Yuvarlama tutarsızlıkları eşiği zorlar mı?~~ → Gerçek veride 3 ay diff=0.00 (tam). ±0.01 tolerans yeterli.
- ~~Bilgi bölümleri toplama dahil mi?~~ → HARİÇ. DB `informational=false`, Excel ana `TOPLAM:` (Multinet/Sigorta TOPLAM hariç). Şubat doğrulaması: 465411 − Multinet 322362 − Sigorta 30658 = 112390.98 = Excel ana TOPLAM.
- Migrasyon tekrar çalışır (Drive güncellenip re-import); idempotency anahtarları rapora yazıldı.

## Tamamlanma Kaydı
- Durum: ✅ Tamamlandı — 2026-06-25
- YouTrack: IK-236 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend)
- Üretilenler: ReconciliationService (read-only), AdminReconciliationController `POST /api/v1/admin/reconciliation` (ADMIN), ReconciliationReport, SectionHeaderText (E2-01 importer + E2-05 reconciler ortak bölüm-başlığı eşleştirici), ExpenseRepository sum/count (informational=false)
- **Gerçek veri doğrulaması = MİGRASYON KABUL TESTİ (lokal PG14)**: genel ok=true. 2026-01 MATCH (194520.26), 2026-02 MATCH (112390.98), 2026-03 MATCH (114355.40) — diff=0.00, satır 11/28/23 tam; 2026-04 WARNING (kısmi ay, tutar yok). filesPhysical 55==filesDbRows 55, statusDistribution E2-04 ile birebir. read-only teyit: exp/files değişmedi.
- Test: `./mvnw test` 93/93 (3 surefire sırasında; +14)
- **Dört bağımsız review turu (agent 3 + parent 1):** agent round 1-3 (zero-invoice filtre, case-sensitive bölüm-başlığı, storage-scan 500, -1 sentinel, importer/reconciler divergence, Türkçe İ-fold) → 0; parent turu temiz onayladı.
- expenses/ Drive aynasına dokunulmadı (Excel read-only; servis hiç yazma yapmıyor).
