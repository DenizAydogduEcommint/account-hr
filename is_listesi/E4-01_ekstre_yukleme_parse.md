# [E4-01] Banka ekstresi yükleme ve parse (Word/Excel, çok kart)

| Alan | Değer |
|------|-------|
| Epic | E4 — Banka Ekstresi İşleme |
| Sprint | Sprint 4 |
| Öncelik | Yüksek |
| Bağımlılıklar | E1-02, E1-04, E1-07, E3-03 |
| Tahmini Efor | 8 puan (~5 gün) |
| Etiketler | backend, parse, ekstre |

## Amaç
Kaan'ın gönderdiği Word veya Excel kart ekstrelerini yükleyip otomatik olarak işlem (transaction) satırlarına dönüştür. Manuel veri girişini ortadan kaldırır.

## Açıklama / Bağlam
Kaan ekstreleri Word VEYA Excel formatında, çoklu kart için gönderiyor (Akbank Axess ****3800, YKB ****3909, Ziraat ****9164). Bu görev: dosya yükleme + format algılama + tablo parse + işlem satırı çıkarma (tarih, açıklama/işyeri, tutar, para birimi, kart). Çıkan işlemler "ham işlem" (transaction) olarak DB'ye yazılır; henüz servisle eşleştirilmez (eşleştirme E4-02). Parse edilemeyen/şüpheli satırlar kullanıcıya gösterilir, sessizce atlanmaz.

## Kabul Kriterleri (DOD)
- [ ] Word (.doc/.docx) ve Excel (.xls/.xlsx) ekstre yüklenebilir
- [ ] Format otomatik algılanır
- [ ] İşlem satırları çıkarılır: tarih, açıklama/işyeri, tutar, para birimi, kart
- [ ] Birden çok kart aynı dosyada/ardışık dosyalarda işlenebilir
- [ ] Çıkan işlemler "ham işlem" olarak kaydedilir (eşleştirme öncesi durum)
- [ ] Parse edilemeyen satırlar kullanıcıya raporlanır
- [ ] Yükleme öncesi kullanıcı kart ve dönem (ay) seçebilir/doğrulayabilir
- [ ] Aynı ekstrenin tekrar yüklenmesi mükerrer işlem yaratmaz (idempotent kontrol)

## Alt Görevler
- [ ] Backend: ekstre yükleme endpoint'i (multipart)
- [ ] Backend: Word parse (Apache POI - XWPF / tablolar)
- [ ] Backend: Excel parse (Apache POI - XSSF/HSSF)
- [ ] Backend: işlem (transaction) veri modeli ve kayıt
- [ ] Backend: parse hata/uyarı raporu
- [ ] Angular: ekstre yükleme ekranı + parse sonucu önizleme/onay
- [ ] Mükerrer yükleme tespiti

## Teknik Notlar
- Java tarafında Apache POI hem Word hem Excel için kullanılabilir
- Banka ekstre formatları bankaya göre değişir; parse stratejisi banka bazlı olmalı (E4-05 ile koordine)
- Tutar/para birimi ayrıştırma TR sayı formatı (binlik nokta, ondalık virgül) dikkate almalı
- Parse sonucu kullanıcı onayından geçmeden kesinleşmemeli (önizle → onayla)

## Açık Sorular / Riskler
- Word ekstrelerin yapısı standart mı yoksa serbest metin mi? Örnek dosyalar gerekli — parse zorluğunu belirler
- .doc (eski binary) desteklenecek mi yoksa sadece .docx mi? — Karar gerekli
- PDF ekstre gelirse kapsama dahil mi? (Şimdilik hariç, gerekirse ayrı görev)
