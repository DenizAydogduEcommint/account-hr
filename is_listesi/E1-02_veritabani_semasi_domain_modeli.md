# [E1-02] Veritabanı şeması ve domain modelini tasarla (SERVICE-FIRST)

| Alan | Değer |
|------|-------|
| Epic | E1 — Temel Altyapı & Veri Modeli |
| Sprint | Sprint 1 |
| Öncelik | Yüksek |
| Tahmini Efor | 8 puan (~4-5 gün) |
| Bağımlılıklar | E1-01 |
| Etiketler | backend, db, domain, schema |

## Amaç
Tüm sistemin kalbi olan, "servisten (master liste) yola çıkıp eksik faturayı tespit etmeyi" mümkün kılan SERVICE-FIRST veri modelini ve PostgreSQL şemasını kurmak.

## Açıklama / Bağlam
Mevcut manuel sistemde banka ekstresinden hareket ediliyordu; yeni sistemde tersine dönüyoruz: önce **ödenen tüm servislerin master listesi** (Servisler sheet'i) kayıt altına alınır, sonra her ay her servis için bir harcama/işlem (transaction) ve buna bağlı fatura(lar) beklenir. Böylece "bu ay X servisinin faturası eksik" sorusunu sistem otomatik yanıtlayabilir.

Modellenecek ana varlıklar:
- **services** (çekirdek master varlık): hizmet adı, sağlayıcı, varsayılan kart, frekans, aktiflik, yaklaşık tutar, fatura kaynağı, notlar
- **providers**: fatura kesen firmalar (AWS, Anthropic, Contabo...)
- **cards**: kredi kartları (Akbank Axess ****3800, YKB ****3909, Ziraat ****9164)
- **expenses** (transactions): ay sheet'indeki her satır = bir işlem (tarih, tutar, para birimi, TL karşılığı, kart, kullanan takım, amaç)
- **invoices**: bir işleme bağlı fatura(lar); birden çok dosya olabilir
- **invoice_status** (enum): Bulundu / e-Fatura / Bekleniyor / Araştırılacak / Ignored
- **periods**: aylar (2026-01, 2026-02...)
- **users**, **teams**: kullanan takım ve sistem kullanıcıları
- **service_contacts**: bir servisin fatura iletişim bilgileri (mail adresi, panel login referansı, kaynak)
- **files**: fatura dosyaları (PDF/XML/statement) metadata + path
- **audit_log**: değişiklik geçmişi

## Kabul Kriterleri (DOD)
- [x] ER diyagramı (mermaid) çizildi → `docs/er-diagram.md` + `docs/data-model.md`; kullanıcı ile gözden geçirilip onaylandı
- [x] services, providers, cards, expenses, invoices, periods, users, teams, service_contacts, files, audit_log tabloları Flyway migration olarak tanımlı (`V1__init_schema.sql`)
- [x] Enum'lar tanımlı: `invoice_status`, `frequency`, `currency`, `active_state` + ayrıca `user_role`, `invoice_source`, `file_type`, `audit_action` — hepsi DB'de STRING
- [x] İlişkiler doğru: expense → service (N:1, ZORUNLU), expense → invoices (1:N), invoice → files (1:N), service → provider/card/team (N:1), service_contact → service (N:1)
- [x] JPA entity + repository oluşturuldu; uygulama H2'de hatasız ayağa kalkıyor (5 test geçer) — **Postgres boot'u Docker yokluğundan doğrulanmadı** (aşağıya bkz.)
- [x] Para tutarları `NUMERIC(15,2)` / `BigDecimal`; tarih `DATE`, zaman damgası `TIMESTAMP`
- [x] Servisler master sorgulanabilir: `ServiceRepository.findByActiveStateAndFrequency` + `ExpenseRepository.existsByServiceIdAndPeriodId` (= "bu servisin bu ay satırı var mı")

## Alt Görevler
- [x] ER modelini çıkar (mermaid), kullanıcı ile gözden geçir → onaylandı
- [x] Migration aracı: **Flyway** (`flyway-core` + `flyway-database-postgresql`)
- [x] Tabloları ve enum'ları migration script'leri olarak yaz (V1)
- [x] JPA entity + repository sınıfları (12 entity / 11 repository)
- [x] Seed verisi: 3 kart + periods 2026-01…06 (`V2`, idempotent ON CONFLICT) — _takımlar seed'i E2 import sırasında doldurulacak_
- [x] Index'ler: `expenses(period_id, service_id)`, `invoices(status)`, partial unique `invoices(provider_id, invoice_no)`

## Teknik Notlar
- Enum'lar DB'de string olarak saklanmalı (`@Enumerated(EnumType.STRING)`) — okunabilirlik ve migration güvenliği
- Renk bilgisi enum'a değil, frontend tarafına ait (status→renk eşlemesi UI sabiti); ancak CLAUDE.md renk kodları dokümante edilmeli (4CAF50/8BC34A/FF4444/FF9800)
- expense = "transaction" kavramı; isimlendirmede tutarlı olun (DB'de `expenses`, koddaki domain `Expense`)
- Bir işleme birden çok fatura/dosya: invoice(1) → files(N) ya da expense(1) → invoices(N) → files(N) kararı net verilmeli (öneri: expense→invoice 1:N esnekliği koru çünkü iade/duplicate senaryoları var)
- Duplicate tespiti için invoice no alanı + (provider, invoice_no) unique partial index
- "Aktif Aylar" Servisler sheet'inde virgüllü string; modelde service ↔ period ilişkisinden türetilebilir (denormalize tutmak yerine sorgu ile)

## Açık Sorular / Riskler — KARARA BAĞLANDI
- ~~expense→invoice yönü?~~ → **expense → invoices (1:N)** seçildi (iade/duplicate/Invoice+Receipt esnekliği).
- ~~Çoklu para birimi + kur?~~ → MVP: `amount` + `currency` + `amount_try` saklanır; kur tarihi/oranı **saklanmaz**.
- ~~Servis-expense eşleme anahtarı?~~ → `expense.service_id` **zorunlu FK** (isim+kart kombinasyonu kullanılmaz).

## Tamamlanma Kaydı
- **Durum:** ✅ Tamamlandı (Postgres migration çalıştırma doğrulaması hariç) — 2026-06-24
- **YouTrack:** E1-02 (IK-226 — teyit edilecek)
- **Repo:** account-hr (backend) — commit bu güncelleme ile birlikte
- **Üretilenler:** 12 entity (`BaseEntity` + 11 tablo), 8 enum (STRING), 11 repository, Flyway `V1`+`V2`, `docs/er-diagram.md`, `docs/data-model.md`
- **Doğrulananlar:** `./mvnw package` ✅ · `./mvnw test` ✅ **5 test** (1 health + 4 domain JPA, H2 PostgreSQL modu)
- **Önemli düzeltme:** `Period.year`/`month` SQL reserved word → fiziksel kolonlar `period_year`/`period_month` (Java alan adları korundu). H2'de DDL hatası gözlemlenip düzeltildi.
- **Kalan iş:** Docker'lı ortamda `docker compose up` → Flyway `V1`/`V2` gerçek Postgres'te çalıştırma + `ddl-auto=validate` ile migration↔entity uyum teyidi.
- **Not (Java):** pom Java 17 hedefliyor (görev özetindeki 21 değil) — mevcut pom'a saygı gösterildi.
