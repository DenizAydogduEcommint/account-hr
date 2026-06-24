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
- [ ] Excel'deki durum metni → `invoice_status` enum'a eşleniyor (5 değer)
- [ ] Metin boş/belirsizse hücre rengi ile tahmin yapılıyor (4CAF50/8BC34A/FF4444/FF9800)
- [ ] Araştırılacak vs Ignored ayrımı metinle yapılıyor (renk aynı olduğu için)
- [ ] E2-03'te dosyası bulunan ama Excel'de "Bekleniyor" kalmış satırlar için tutarsızlık raporlanıyor (otomatik düzeltme opsiyonel/işaretli)
- [ ] Renk→durum eşleme tablosu kod sabiti olarak dokümante (frontend de aynı renkleri kullanacak)
- [ ] Migrasyon sonrası her invoice'ın bir geçerli enum durumu var; "tanımsız" kalan yok
- [ ] Özet rapor: durum dağılımı (kaç Bulundu/Bekleniyor/...), renk-metin tutarsızlığı sayısı

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
- Renk okuması tema/koşullu biçimlendirme kullanıyorsa POI ile okumak zor olabilir (doğrudan dolgu rengi şart)
- Metin-renk çeliştiğinde otomatik mi düzeltilsin yoksa sadece raporlanıp manuel mi onaylansın? (Öneri: raporla, manuel onay)
