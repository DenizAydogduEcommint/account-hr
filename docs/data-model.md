# account-hr — Veri Modeli (E1-02)

SERVICE-FIRST veri modeli: banka ekstresinden değil, **ödenen tüm servislerin master
listesinden** yola çıkarak her ay hangi faturanın eksik olduğunu tespit eder.

## Tablolar ve Amaçları

| Tablo | Amaç |
|-------|------|
| **providers** | Fatura kesen firmalar (Anthropic, AWS, Contabo...). `name` tekil. |
| **cards** | Kredi kartları. `last_four` tekil (3800 / 3909 / 9164). |
| **teams** | Servisleri kullanan takımlar. `name` tekil. |
| **users** | Sistem kullanıcıları (entity `AppUser`, tablo `users`). Roller `UserRole`. Auth/parola alanları E1-03'te eklenecek. |
| **periods** | Aylar (`code` ör. "2026-01"). `code` tekil, `(year, month)` tekil. |
| **services** | **Çekirdek master varlık.** Hizmet adı, sağlayıcı, varsayılan kart, frekans, aktiflik, yaklaşık tutar, fatura kaynağı, notlar. |
| **service_contacts** | Bir servisin fatura iletişim bilgileri (mail, panel login referansı, kaynak). |
| **expenses** | Kart işlemi (transaction) = ay sheet'indeki bir satır. `service_id` **zorunlu**. |
| **invoices** | Bir işleme bağlı fatura(lar). Durum `InvoiceStatus`. |
| **files** | Fatura dosyası metadata + path (entity `FileAsset`, tablo `files`). |
| **audit_log** | Değişiklik geçmişi (polimorfik: `entity_type` + `entity_id`). |

## Anahtar Tasarım Kararları

### 1:N ilişkiler
- **expense → invoices = 1:N.** Bir kart işlemine birden çok fatura bağlanabilir:
  iade (refund), invoice + receipt aynı işlem, ya da duplicate senaryoları.
- **invoice → files = 1:N.** Bir faturaya pdf + xml + statement gibi birden çok dosya.

### expense.service_id zorunlu FK
Servis ↔ expense eşleşmesi **isim bazlı değil**, `service_id` referansı üzerinden. Hizmet
adı + kart kombinasyonu kırılgan olduğu için reddedildi; her expense bir servise bağlı olmak
zorunda. Bu, "bu servisin bu ay satırı var mı?" sorgusunu güvenilir kılar.

### Para modeli (MVP)
Her tutar üç alanla saklanır: `amount` (orijinal, döviz cinsinden) + `currency` +
`amount_try` (kart ekstresindeki TL karşılığı). **Kur oranı/tarihi TUTULMAZ** — MVP için
ekstredeki TL yeterli. Tüm para alanları `NUMERIC(15,2)`, kodda `BigDecimal`.

### Renk DB'de yok
Fatura durumu → renk eşlemesi bir frontend sabitidir (4CAF50 / 8BC34A / FF4444 / FF9800),
DB'de saklanmaz. Sadece `InvoiceStatus` enum'u (STRING) tutulur.

### informational bayrağı
`services.informational` ve `expenses.informational` ayrı boolean kolonlardır. Multinet
yemek kartı, sağlık sigortası gibi bilgi-amaçlı kalemler operasyonel TOPLAM'a dahil edilmez;
bu bayrak ile işaretlenir (enum'a gömülmez).

### Enum'lar STRING
Tüm enum'lar `@Enumerated(EnumType.STRING)` ile okunabilir string olarak saklanır:
`InvoiceStatus`, `Frequency`, `Currency`, `ActiveState`, `UserRole`, `InvoiceSource`,
`FileType`, `AuditAction`.

### Duplicate tespiti
`invoices` üzerinde **partial unique index**: `(provider_id, invoice_no)` — yalnızca
`invoice_no IS NOT NULL` olduğunda. Aynı sağlayıcıdan aynı fatura numarası iki kez girilemez.

## Eksik Fatura Kuralı

> **Eksik fatura** = Aktif (`active_state = YES`) ve aylık (`frequency = MONTHLY`) bir servis
> için, ilgili dönemde (`period`) hiçbir `expense` satırının olmaması.

Sorgu temelleri (E1-02'de repository ile kanıtlandı):
- `ServiceRepository.findByActiveStateAndFrequency(YES, MONTHLY)` → her ay beklenen servisler.
- `ExpenseRepository.existsByServiceIdAndPeriodId(serviceId, periodId)` → o servisin o ay
  satırı var mı? `false` dönen her aktif-aylık servis = eksik fatura adayı.

## Index'ler
- `expenses(period_id, service_id)` — dönem bazlı servis sorguları.
- `invoices(status)` — durum bazlı listeleme (ör. tüm "Bekleniyor").
- `invoices(expense_id)` — bir işlemin faturaları.
- `invoices(provider_id, invoice_no) WHERE invoice_no IS NOT NULL` — partial unique (duplicate).
- Tekil kısıtlar: `providers.name`, `cards.last_four`, `teams.name`, `users.email`,
  `periods.code`, `periods(year, month)`.

## Migrasyon / Profil Stratejisi
- **Flyway** şemayı kurar (`V1__init_schema.sql` + `V2__seed_reference_data.sql`),
  PostgreSQL hedefli.
- `local` / `docker` / `staging` / `prod`: `flyway.enabled=true`, `ddl-auto=validate`
  (Flyway kurar, JPA eşlemeyi gerçek Postgres'e karşı doğrular).
- `test` (H2): `flyway.enabled=false`, `ddl-auto=create-drop` — şema entity'lerden üretilir,
  testler eşleme + repository davranışını H2 üzerinde doğrular (Docker/Postgres gerekmez).
- **V1/V2 SQL bu ortamda Postgres'e karşı ÇALIŞTIRILMADI** (Docker yok); gerçek Postgres'te
  bir kez doğrulanmalı — orada `ddl-auto=validate` migrasyon ↔ entity paritesini de teyit eder.
