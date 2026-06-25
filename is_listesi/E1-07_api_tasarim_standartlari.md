# [E1-07] API tasarım standartlarını belirle (REST sözleşmesi, hata formatı, OpenAPI)

| Alan | Değer |
|------|-------|
| Epic | E1 — Temel Altyapı & Veri Modeli |
| Sprint | Sprint 1 |
| Öncelik | Yüksek |
| Tahmini Efor | 3 puan (~2 gün) |
| Bağımlılıklar | E1-01 |
| Etiketler | backend, api, documentation |

## Amaç
Web ve ileride mobil istemcilerin aynı API'yi tutarlı şekilde tüketebilmesi için REST sözleşmesi, standart hata formatı, sayfalama/filtreleme ve OpenAPI/Swagger dokümantasyonu standartlarını oluşturmak.

## Açıklama / Bağlam
Aynı REST API hem Angular frontend hem ileride mobil tarafından kullanılacak. Tutarsız endpoint isimleri, farklı hata formatları, dokümansız API entegrasyonu zorlaştırır. Bu görev "kurallar kitabını" yazar ve örnek bir endpoint üzerinde uygular; diğer tüm endpoint'ler bu standarda uyar.

## Kabul Kriterleri (DOD)
- [x] REST adlandırma standardı `docs/api-guide.md`'de: çoğul kaynak, HTTP metot, versiyonlama `/api/v1/...` (tüm endpoint'ler retrofit edildi)
- [x] Standart hata formatı + global handler: `ErrorResponse {timestamp, status, error, message, path, traceId, fieldErrors[]}` (traceId = MDC correlationId)
- [x] Sayfalama/sıralama: `?page=&size=&sort=` (Spring Data Pageable) + `PagedResponse<T>`
- [x] OpenAPI/Swagger UI ayakta: springdoc 2.8.6; `/swagger-ui/index.html` + `/v3/api-docs` (canlı 200 doğrulandı)
- [x] Validation 400 + `fieldErrors`; 401/403/404 tutarlı (canlı: 401 + 400 fieldErrors doğrulandı)
- [x] Mobil hazırlık: temiz DTO (entity sızdırmaz), tarih ISO-8601, para BigDecimal
- [x] Örnek endpoint `GET /api/v1/services` standarda tam uyumlu (canlı paged response doğrulandı)

## Alt Görevler
- [x] API kılavuzu: `docs/api-guide.md`
- [x] `@RestControllerAdvice` global handler + `ErrorResponse` (+ 401/403 handler'lar aynı format)
- [x] Sayfalama/sıralama: `PagedResponse` + Pageable
- [x] springdoc-openapi + Swagger UI + JWT bearer scheme (OpenApiConfig)
- [x] Örnek endpoint: ServiceController + ServiceQueryService + ServiceDto
- [x] DTO ↔ entity mapping: elle (MapStruct MVP'ye eklenmedi)

## Teknik Notlar
- springdoc-openapi (Spring Boot 3 uyumlu) — `springfox` değil
- Tarih: ISO-8601 (`yyyy-MM-dd`), para: minor unit yerine decimal string/numeric — frontend formatlar
- TraceId E1-05 loglama ile bağlantılı (hata ↔ log eşleşmesi)
- DTO'lar entity'leri dışarı sızdırmamalı (lazy loading / döngüsel referans riskine karşı)

## Açık Sorular / Riskler — KARARA BAĞLANDI
- ~~API versiyonlama MVP'de?~~ → **EVET, `/api/v1/` eklendi** (tüm endpoint'ler + frontend apiBaseUrl retrofit edildi). Mobil API için baştan versiyonlama.
- ~~Hata mesajı dili / i18n?~~ → Kod/`message` **İngilizce**; kullanıcıya gösterilen Türkçe (frontend çevirir).

## Tamamlanma Kaydı
- Durum: ✅ Tamamlandı — 2026-06-25
- YouTrack: IK-231 (sıralı varsayım — teyit edilecek)
- Repolar: account-hr (backend) + account-hr-frontend (apiBaseUrl `/api/v1`)
- Üretilenler: ErrorResponse + ErrorResponses, PagedResponse, ServiceDto + ServiceQueryService + ServiceController (`GET /api/v1/services`), OpenApiConfig (springdoc 2.8.6, JWT bearer), GlobalExceptionHandler yeniden yazıldı, 401/403 handler'lar aynı formata; tüm controller'lar + testler `/api/v1/`; `docs/api-guide.md`
- Doğrulama: `./mvnw test` **48/48**. Lokal PostgreSQL 14'te canlı: `/api/v1/health` UP, `/v3/api-docs` + `/swagger-ui` 200, login → token, `/api/v1/services` paged, token'sız 401 (ErrorResponse formatı), boş login 400 + fieldErrors + traceId
- Review: Claude code-reviewer — 0 gerçek hata (1 kozmetik stale javadoc düzeltildi). Codex kotası dolu olduğu için fallback kullanıldı.
- Sapma: AccessDeniedException, generic handler'da rethrow edilerek 403'ün tek kaynağı (RestAccessDeniedHandler) korundu (aksi halde 403→500)
