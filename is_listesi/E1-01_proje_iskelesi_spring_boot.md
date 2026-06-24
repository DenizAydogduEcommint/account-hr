# [E1-01] Proje iskeletini kur: Spring Boot + Angular + PostgreSQL + Docker Compose

| Alan | Değer |
|------|-------|
| Epic | E1 — Temel Altyapı & Veri Modeli |
| Sprint | Sprint 1 |
| Öncelik | Yüksek |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | — |
| Etiketler | backend, frontend, devops, setup |

## Amaç
Tüm geliştirmenin üzerine oturacağı çalışan bir monorepo/çoklu-repo iskeleti kurmak; "klonla → docker compose up → çalışıyor" deneyimini sağlamak.

## Açıklama / Bağlam
account-hr uygulamasının iki ana parçası var: Java + Spring Boot backend (REST API, ileride mobil de aynı API'yi kullanacak) ve Angular frontend (hazır bir admin arayüz teması). Veritabanı PostgreSQL. Geliştiricinin makinesinde her şeyin tek komutla ayağa kalkması için Docker Compose ile servisleri (api, db, opsiyonel adminer/pgadmin) bağlayacağız. Bu görev kod değil iskelet üretir: paketleme, katmanlı yapı (controller / service / repository / domain), build araçları, ortam değişkenleri ve çalışan bir "health check" endpoint'i.

## Kabul Kriterleri (DOD)
- [ ] `mvn` (veya Gradle) ile derlenen Spring Boot projesi; ana katmanlar (controller, service, repository, domain, config) klasör olarak mevcut
- [ ] Angular projesi kurulmuş (seçilen hazır admin teması entegre), `npm start` (ng serve) ile çalışıyor, API base URL ortam değişkeninden okunuyor
- [ ] `docker-compose.yml` ile `db` (PostgreSQL), `api` (Spring Boot) ayağa kalkıyor; `docker compose up` tek komutla çalışıyor
- [ ] `GET /api/health` endpoint'i 200 ve `{status:"UP"}` dönüyor; Angular'dan bu endpoint'e başarılı çağrı yapılabiliyor
- [ ] README'de yerel kurulum adımları (prereq, env, çalıştırma) yazılı
- [ ] `.env.example` ve `.gitignore` (secret'lar, target/, node_modules/) mevcut

## Alt Görevler
- [ ] Spring Boot projesini Spring Initializr ile oluştur (Web, Data JPA, PostgreSQL Driver, Validation, Actuator, Security bağımlılıkları)
- [ ] Katmanlı paket yapısını oluştur: `com.ecommint.accounthr.{config,controller,service,repository,domain,dto,mapper}`
- [ ] Angular + seçilen hazır admin temasını yerleştir, klasör yapısı ve API client (HttpClient) iskeletini kur
- [ ] `docker-compose.yml`, `Dockerfile` (api) ve frontend dev konfigürasyonunu yaz
- [ ] Actuator health + custom `/api/health` ekle
- [ ] README + `.env.example` yaz

## Teknik Notlar
- Java 21 LTS + Spring Boot 3.x öneriliyor; build aracı Maven (ekip alışkın değilse Gradle de olur)
- Hazır admin temaları lisanslı olabilir; tema dosyaları repoya ekleneceği için lisans/erişim kullanıcıdan teyit alınmalı
- PostgreSQL 16; Docker volume ile veri kalıcılığı
- Profil yönetimi: `application-local.yml`, `application-staging.yml`, `application-prod.yml`
- Frontend ve backend ayrı container; CORS dev'de açık, prod'da reverse proxy arkasında

## Açık Sorular / Riskler
- Monorepo mu yoksa ayrı repo mu? (Öneri: tek repo, `/backend` `/frontend` klasörleri)
- Seçilecek hazır admin temasının lisans dosyalarının repoya eklenmesi sorun yaratır mı? Kullanıcı teyidi gerekli
