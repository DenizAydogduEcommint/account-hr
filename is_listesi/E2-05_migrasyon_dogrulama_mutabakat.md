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
- [ ] Her period için Excel TOPLAM (TL) ↔ DB expense TL toplamı karşılaştırması; fark eşiği (ör. ±0.01) içinde
- [ ] Satır sayısı mutabakatı: Excel'deki harcama satırı sayısı = DB'deki expense sayısı (TOPLAM/bilgi bölümleri hariç doğru sayılıyor)
- [ ] Fatura/dosya mutabakatı: `faturalar/` fizik dosya sayısı ↔ `files` kayıt sayısı
- [ ] Durum dağılımı mutabakatı: Excel durum sayıları ↔ DB enum sayıları (E2-04 ile)
- [ ] İdempotency: tüm importer'lar ikinci kez çalıştırıldığında yeni kayıt oluşturmaz, mevcutları günceller (upsert / doğal anahtar)
- [ ] Mutabakat raporu üretiliyor (okunabilir: period bazlı tablo + farklar + uyarılar), kullanıcı onayına sunulabilir
- [ ] Tutarsızlıklar net listeleniyor (hangi satır/dosya eşleşmedi, hangi tutar tutmadı)

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
- Excel'de elle yapılmış küçük tutarsızlıklar (yuvarlama, manuel düzeltme) tolerans eşiğini zorlayabilir
- Bilgi bölümlerinin toplamlara dahil edilip edilmeyeceği netleştirilmeli (öneri: hariç, ana harcama gibi)
- Migrasyon birden çok kez mi çalışacak (Drive güncellenip tekrar import)? İdempotency bunu varsayar
