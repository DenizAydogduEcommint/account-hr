-- V15__files_sha256_per_invoice_unique.sql
-- E2-DR-1: files.sha256 tekilliği GLOBAL'den BİLEŞİK (invoice_id, sha256)'ya geçirilir.
--
-- Sorun (V9 + E1-DR-5): byte-aynı (aynı SHA-256) bir dosya MEŞRU şekilde İKİ FARKLI
-- faturaya ait olabilir; ancak global tekil index (V9 uq_files_sha256) ikinci faturanın
-- dosyasını engelliyor, "içerik-aynı, başka faturaya bağlı" diye unmatched bırakıyordu.
--
-- Çözüm: tekillik artık (invoice_id, sha256) ikilisinde. Böylece:
--   * İki FARKLI fatura aynı fizik içeriği (aynı path) paylaşan birer FileAsset satırına
--     sahip olabilir → ikinci faturanın dosyası artık BAĞLANIR, unmatched değil.
--   * AYNI fatura içinde aynı içerikli iki FileAsset hâlâ ENGELLENİR (fatura-içi duplicate
--     + V9'un eşzamanlı-import yarış korumasını fatura tanecikliğinde korur).
--   * Fizik dosya yine bir kez saklanır (copyPreservingPath içerik-dedup eder); ikinci
--     FileAsset satırı aynı file_path/sha256'ı farklı invoice_id ile referanslar.
--
-- KISMİ (partial) index: WHERE sha256 IS NOT NULL → null-sha satırları (tipi belirsiz eski
-- kayıtlar) çakışma SAYMAZ, serbest kalır. Hem invoice_id hem sha256 nullable'dır;
-- invoice_id NULL satırlar da index'e girer ama Postgres tekil index'lerde NULL'ları
-- BİRBİRİNDEN FARKLI kabul ettiğinden, null-invoice (eşleşmemiş/trash) + aynı sha satırları
-- DB düzeyinde ÇAKIŞMAZ — bu, uygulama düzeyindeki (InvoiceFileImportService) null-invoice
-- içerik-dedup mantığıyla uyumludur (orada null-invoice aynı içerik tekil tutulur).
--
-- VERİ-GÖÇÜ UYARISI (canlı Postgres): Eğer mevcut veride AYNI (invoice_id, sha256) ikilisine
-- sahip iki satır varsa bu index oluşturma BAŞARISIZ olur. V9 global tekil zaten aynı sha'lı
-- iki dolu satıra izin vermediğinden, temiz/yeniden-seed edilmiş DB'de bu durum oluşamaz.
-- Canlıda V9 yürürlükteyken çift (invoice_id, sha256) ortaya çıkmış OLAMAZ (global tekil daha
-- sıkıydı) → göç güvenlidir. Yine de defansif olarak ihtiyaç olursa önce çiftler temizlenmeli.
--
-- NOT: Bu dosya yalnızca PostgreSQL üzerinde (Flyway etkinken) çalışır. Testlerde Flyway
-- kapalıdır; şema entity'lerden Hibernate ile üretilir (FileAsset @UniqueConstraint).

-- Eski global tekil index'i bırak; bileşik tekil onun yerine geçer.
DROP INDEX IF EXISTS uq_files_sha256;

CREATE UNIQUE INDEX uq_files_invoice_sha256 ON files (invoice_id, sha256) WHERE sha256 IS NOT NULL;
