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
- [ ] REST adlandırma standardı dokümante: kaynak isimleri çoğul (`/api/services`, `/api/expenses`, `/api/invoices`), HTTP metot kullanımı, versiyonlama (`/api/v1/...`)
- [ ] Standart hata formatı tanımlı ve global exception handler ile uygulanmış (örn. `{timestamp, status, error, message, path, traceId, fieldErrors[]}`)
- [ ] Sayfalama, sıralama, filtreleme query parametre standardı (`?page=&size=&sort=&...`)
- [ ] OpenAPI/Swagger UI ayakta; tüm endpoint'ler otomatik dokümante (`/swagger-ui`)
- [ ] Validation hataları 400 + alan bazlı hata listesi; auth 401/403; bulunamadı 404 tutarlı
- [ ] Mobil hazırlık: response'lar UI'dan bağımsız temiz DTO; tarih/para alanları standart format
- [ ] En az bir örnek endpoint (ör. `GET /api/services`) standarda tam uyumlu referans olarak hazır

## Alt Görevler
- [ ] API kılavuzu dokümanını yaz (README/wiki)
- [ ] `@RestControllerAdvice` global exception handler + ortak `ErrorResponse` DTO
- [ ] Sayfalama/sıralama yardımcıları (Spring Data Pageable wrapper)
- [ ] springdoc-openapi entegrasyonu + Swagger UI
- [ ] Örnek referans endpoint'i standarda göre yaz
- [ ] DTO ↔ entity mapping standardı (MapStruct öneri)

## Teknik Notlar
- springdoc-openapi (Spring Boot 3 uyumlu) — `springfox` değil
- Tarih: ISO-8601 (`yyyy-MM-dd`), para: minor unit yerine decimal string/numeric — frontend formatlar
- TraceId E1-05 loglama ile bağlantılı (hata ↔ log eşleşmesi)
- DTO'lar entity'leri dışarı sızdırmamalı (lazy loading / döngüsel referans riskine karşı)

## Açık Sorular / Riskler
- API versiyonlama gerçekten gerekli mi MVP'de? (Öneri: `/v1` prefix koy, ileride değişiklik kolaylaşır)
- Hata mesajları Türkçe mi İngilizce mi / i18n? (Öneri: kod İngilizce, kullanıcıya gösterilen Türkçe — frontend çevirir)
