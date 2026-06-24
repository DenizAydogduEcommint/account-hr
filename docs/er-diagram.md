# account-hr — ER Diyagramı (SERVICE-FIRST)

Tüm 11 tablo ve ilişkileri. Çekirdek varlık **services** (master liste); eksik fatura
tespiti `services` → `expenses` → `invoices` → `files` zinciri üzerinden yapılır.

```mermaid
erDiagram
    providers ||--o{ services : "kesilen faturalar"
    providers ||--o{ invoices : "fatura keser"
    cards ||--o{ services : "varsayılan kart"
    cards ||--o{ expenses : "ile ödendi"
    teams ||--o{ users : "üyesi"
    teams ||--o{ services : "kullanan takım"
    teams ||--o{ expenses : "kullanan takım"
    users ||--o{ files : "yükleyen"
    users ||--o{ audit_log : "değiştiren"

    services ||--o{ service_contacts : "iletişim"
    services ||--o{ expenses : "her ay beklenen işlem"
    periods ||--o{ expenses : "ait olduğu ay"

    expenses ||--o{ invoices : "1:N (iade/receipt/duplicate)"
    invoices ||--o{ files : "1:N (pdf/xml/statement)"

    providers {
        bigint id PK
        varchar name UK
        timestamp created_at
        timestamp updated_at
    }
    cards {
        bigint id PK
        varchar bank
        varchar last_four UK
        varchar holder_name
        varchar label
        boolean active
        timestamp created_at
        timestamp updated_at
    }
    teams {
        bigint id PK
        varchar name UK
        timestamp created_at
        timestamp updated_at
    }
    users {
        bigint id PK
        varchar email UK
        varchar full_name
        varchar role "UserRole"
        bigint team_id FK
        boolean active
        timestamp created_at
        timestamp updated_at
    }
    periods {
        bigint id PK
        int period_year
        int period_month
        varchar code UK
        timestamp created_at
        timestamp updated_at
    }
    services {
        bigint id PK
        varchar name
        bigint provider_id FK
        bigint default_card_id FK
        bigint using_team_id FK
        varchar frequency "Frequency"
        varchar active_state "ActiveState"
        boolean informational
        numeric approx_amount_try
        varchar invoice_source "InvoiceSource"
        text purpose
        text notes
        timestamp created_at
        timestamp updated_at
    }
    service_contacts {
        bigint id PK
        bigint service_id FK
        varchar email
        varchar panel_login_ref
        varchar source
        boolean is_primary
        text notes
        timestamp created_at
        timestamp updated_at
    }
    expenses {
        bigint id PK
        bigint service_id FK "NOT NULL"
        bigint period_id FK
        bigint card_id FK
        date transaction_date
        numeric amount
        varchar currency "Currency"
        numeric amount_try
        bigint using_team_id FK
        boolean informational
        text purpose
        timestamp created_at
        timestamp updated_at
    }
    invoices {
        bigint id PK
        bigint expense_id FK
        bigint provider_id FK
        varchar status "InvoiceStatus"
        varchar invoice_no
        date invoice_date
        numeric amount
        varchar currency "Currency"
        boolean is_refund
        text note
        timestamp created_at
        timestamp updated_at
    }
    files {
        bigint id PK
        bigint invoice_id FK
        varchar file_path
        varchar file_name
        varchar file_type "FileType"
        varchar mime_type
        bigint size_bytes
        bigint uploaded_by FK
        timestamp created_at
    }
    audit_log {
        bigint id PK
        varchar entity_type
        bigint entity_id
        varchar action "AuditAction"
        varchar field_name
        text old_value
        text new_value
        bigint changed_by FK
        timestamp changed_at
        varchar note
    }
```

## İlişki Özeti
- `service → provider` N:1, `service → default_card` N:1 (nullable), `service → using_team` N:1 (nullable)
- `service_contact → service` N:1
- `expense → service` N:1 (**zorunlu** FK — eşleşme anahtarı), `expense → period` N:1, `expense → card` N:1 (nullable), `expense → using_team` N:1 (nullable)
- `invoice → expense` N:1 (**expense → invoices = 1:N**), `invoice → provider` N:1 (nullable)
- `file → invoice` N:1 (**invoice → files = 1:N**), `file → uploaded_by(user)` N:1 (nullable)
- `user → team` N:1 (nullable)
- `audit_log → user` (changed_by) N:1 (nullable); entity_type + entity_id polimorfik referans
