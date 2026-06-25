# account-hr API Kılavuzu (E1-07)

Bu doküman REST sözleşmesinin "kurallar kitabı"dır. Tüm yeni endpoint'ler bu standartlara uyar. Referans örnek: `GET /api/v1/services` (`ServiceController`).

## 1. REST Adlandırma

- **Versiyonlama:** tüm uçlar `/api/v1/...` altındadır. Kırıcı değişiklikler ileride `/api/v2` ile sunulur.
- **Kaynak isimleri çoğul ve isim (fiil değil):** `/api/v1/services`, `/api/v1/expenses`, `/api/v1/invoices`, `/api/v1/files`.
- **HTTP metotları:**
  | Metot | Anlam | Örnek |
  |-------|-------|-------|
  | `GET` | Listele / getir (yan etkisiz) | `GET /api/v1/services` |
  | `POST` | Oluştur / aksiyon | `POST /api/v1/files` |
  | `PUT` / `PATCH` | Güncelle (tam / kısmi) | `PATCH /api/v1/invoices/{id}` |
  | `DELETE` | Sil | `DELETE /api/v1/...` |
- **Durum kodları:** `200` OK, `201` Created (gövdede yeni kaynak), `204` No Content (gövdesiz başarı), `400` validation, `401` kimlik yok/geçersiz, `403` yetki yok, `404` bulunamadı, `409` çakışma (duplicate), `500` beklenmeyen.

## 2. Standart Hata Formatı

Tüm hatalar — validation, auth (filtre zinciri dahil), not-found, server — **tek** `ErrorResponse` şeklini döner. Frontend tek tipte hata işler.

```json
{
  "timestamp": "2026-06-25T08:30:00Z",
  "status": 400,
  "error": "VALIDATION_ERROR",
  "message": "Validation failed for one or more fields.",
  "path": "/api/v1/auth/login",
  "traceId": "3f1a9c2e-7b4d-4e2a-9c1f-0a2b3c4d5e6f",
  "fieldErrors": [
    { "field": "email", "message": "must not be blank" },
    { "field": "password", "message": "must not be blank" }
  ]
}
```

Alanlar:

| Alan | Açıklama |
|------|----------|
| `timestamp` | Hatanın oluştuğu an (ISO-8601 instant, UTC). |
| `status` | HTTP durum kodu (int). |
| `error` | Kısa makine-okur kod: `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `DUPLICATE_FILE`, `INVALID_PATH`, `STORAGE_ERROR`, `INTERNAL_ERROR`. |
| `message` | İnsan-okur açıklama (**İngilizce**; frontend Türkçeye çevirir). |
| `path` | Hatanın oluştuğu istek yolu. |
| `traceId` | Log korelasyon kimliği (E1-05, MDC `correlationId` / `X-Request-Id`). Yoksa `null`. |
| `fieldErrors` | Yalnızca validation hatalarında; `{field, message}` listesi. Diğer hatalarda `null`. |

Kod ↔ durum eşlemesi `GlobalExceptionHandler` (`@RestControllerAdvice`) içindedir; filtre zincirindeki `401/403` (`RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`) aynı şekli üretir. `500` yanıtlarında stack trace / sır SIZDIRILMAZ.

## 3. Sayfalama & Sıralama

Liste uçları Spring Data `Pageable` kullanır. Sorgu parametreleri:

- `page` — 0-tabanlı sayfa indeksi (varsayılan `0`).
- `size` — sayfa boyutu (varsayılan `20`).
- `sort` — `alan,yön` (ör. `sort=name,asc`); birden çok `sort` parametresi verilebilir.

Örnek: `GET /api/v1/services?page=0&size=20&sort=name,asc`

Yanıt **`PagedResponse<T>`** zarfıdır:

```json
{
  "content": [ /* T listesi */ ],
  "page": 0,
  "size": 20,
  "totalElements": 42,
  "totalPages": 3,
  "first": true,
  "last": false,
  "sort": "name: ASC"
}
```

## 4. Veri Formatları

- **Tarih:** ISO-8601 (`yyyy-MM-dd`); zaman damgaları ISO-8601 (`yyyy-MM-ddTHH:mm:ss`).
- **Para:** ondalık `NUMERIC` / `BigDecimal` (minor-unit / kuruş DEĞİL). Frontend yerel biçimi uygular. Tutarlar JSON'da sayı olarak döner (ör. `1200.00`).
- **Enum'lar:** string olarak döner (ör. `frequency: "MONTHLY"`, `activeState: "YES"`).
- **DTO temizliği:** yanıtlar entity SIZDIRMAZ — lazy ilişkiler düz alanlara indirgenir (ör. `providerName`, `defaultCardLastFour`). Böylece lazy-proxy / döngüsel referans / UI bağımlılığı riski yoktur (mobil hazırlık). Bkz. `ServiceDto`.

## 5. i18n Politikası

- Koddaki ve API'deki `message` metinleri **İngilizce**dir.
- Kullanıcıya gösterilen **Türkçe** metni **frontend** üretir (`error` kodu + `fieldErrors[].field` üzerinden yerelleştirme).
- Yeni hata kodu eklerken frontend sözlüğüne karşılık eklenmelidir.

## 6. TraceId ↔ Log Korelasyonu (E1-05)

- `CorrelationIdFilter` her isteğe bir id atar: gelen `X-Request-Id` başlığı varsa onu, yoksa rastgele UUID. Değer yanıt başlığına (`X-Request-Id`) ve loglara (MDC `correlationId`) yazılır.
- Aynı değer hata gövdesinde `traceId` olarak döner → bir hatayı log satırlarıyla eşleştirmek için `traceId`/`X-Request-Id` ile arama yapılır.

## 7. Kimlik Doğrulama

- JWT **bearer** token: `Authorization: Bearer <access_token>`.
- `permitAll` uçlar: `/api/v1/auth/login`, `/api/v1/auth/refresh`, `/api/v1/auth/logout`, `/api/v1/health`, `/actuator/health`, Swagger yolları.
- Diğer her uç `authenticated()`. Token yok/geçersiz → `401`; yetki yetersiz → `403` (her ikisi de `ErrorResponse` şeklinde).

## 8. OpenAPI / Swagger UI

springdoc-openapi ile tüm uçlar otomatik dokümante edilir.

- **Swagger UI:** `/swagger-ui/index.html`
- **OpenAPI JSON:** `/v3/api-docs`
- "Authorize" butonu JWT bearer şemasıyla (`bearerAuth`) çalışır; access token girilerek korumalı uçlar denenebilir.

Yeni uçlar `@Tag` / `@Operation` ile anlamlı şekilde belgelenmeli; korumalı controller'lara `@SecurityRequirement(name = "bearerAuth")` eklenmeli.
