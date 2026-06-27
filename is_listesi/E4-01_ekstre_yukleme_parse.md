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
- .doc (eski binary) desteklenecek mi yoksa sadece .docx mi? — **Karar: sadece .docx** (.doc unsupported uyarısı). PDF de şimdilik hariç (unsupported uyarısı).

## Tamamlanma Kaydı
- Durum: **Altyapı tamamlandı — 2026-06-27** · ⚠️ **Gerçek parser örnek ekstre bekliyor** (placeholder dönüyor)
- YouTrack: IK-249 (sıralı varsayım — teyit edilecek)
- Repo: account-hr (backend) + account-hr-frontend
- **Karar (kullanıcı):** Altyapı önce kuruldu; format-spesifik satır-çıkarma örnek Akbank/YKB/Ziraat ekstresi gelince eklenecek (çatı değişmeden).
- **Backend:** `RawTransaction` (ham işlem; matched=false, E4-02 eşleştirecek) + **V19** migration. `StatementParser` SPI (`ParseResult`/`ParsedTxn`) + `DefaultStatementParser` (format algılama: .xlsx→XSSF, .xls→HSSF, .docx→XWPF; .doc/.pdf→unsupported uyarı) → `BankStatementExtractor` SPI. **`PlaceholderBankStatementExtractor` (`// TODO E4-01: gerçek satır çıkarma — örnek ekstre gelince`)** boş liste + uyarı dönüyor. `POST /api/v1/statements` (multipart file/cardLast4/month) → önizleme `{batchRef(sha256), card, month, transactions, warnings, alreadyUploaded}`; `POST /statements/confirm` + `/discard` (PENDING→CONFIRMED/DISCARDED) → `{batchRef, confirmed}`; `GET /statements/{batchRef}`. Idempotency (sha256+kart+dönem CONFIRMED). Yetki ADMIN+ACCOUNTING. Dosya `STORAGE_ROOT/statements/` (Drive aynasına dokunmaz).
- **Frontend:** "Ekstre Yükle" ekranı (dosya+kart+ay → önizleme tablosu + uyarı paneli → Onayla/İptal); boş/placeholder/zaten-yüklendi durumları + satır-bazlı parse uyarı göstergesi. Rol-gate (ADMIN+ACCOUNTING) route+menü. OnDestroy temiz.
- **Gerçek doğrulama (lokal PG14, V19):** raw_transactions tablosu oluştu; upload 200 + bozuk/desteksiz dosyada graceful uyarı (500 değil); confirm `{batchRef, confirmed}`; regresyon yok (eksik Mart=2).
- Test: backend **308/308** (+13: StatementUploadIT + StatementUploadServiceTest); frontend tsc+ng build temiz.
- **Bağımsız review:** güvenli alanlar CLEAN (storage Drive-izolasyon, yetki, idempotency-CONFIRMED scope, validation, OnDestroy); **4 bulgu düzeltildi** — confirm `count`→`confirmed` (CRIT), `rawText` DTO'ya (CRIT), idempotency-hit kart+dönem scope (IMP), `sourceFileName` path-traversal sanitize (IMP).
- **Kalan:** Gerçek parse mantığı (E4-01 parser) örnek ekstre gelince; eşleştirme E4-02.
