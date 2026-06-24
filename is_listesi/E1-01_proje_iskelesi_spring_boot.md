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
- [x] `mvn` ile derlenen Spring Boot projesi; ana katmanlar (controller, service, repository, domain, config) klasör olarak mevcut — `./mvnw package` BAŞARILI
- [x] Angular projesi kurulmuş (sade el-yazımı layout — hazır tema kullanılmadı), `npm start` ile çalışıyor, API base URL `environment` dosyasından okunuyor — `npm run build` + `curl localhost:4200` (200) doğrulandı
- [~] `docker-compose.yml` ile `db` (PostgreSQL) + `api` + adminer tanımlı — **dosyalar yazıldı, ANCAK bu makinede Docker kurulu olmadığından `docker compose up` çalıştırma DOĞRULANAMADI**; Docker'lı ortamda bir kez test edilecek
- [x] `GET /api/health` → 200 `{status:"UP"}` — H2 ile `@SpringBootTest` entegrasyon testi geçti (Angular→API canlı çağrısı backend+DB ayağa kalkınca manuel teyit edilecek)
- [x] README'de yerel kurulum adımları (prereq, env, çalıştırma) yazılı
- [x] `.env.example` ve `.gitignore` (secret'lar, target/, node_modules/) mevcut

## Alt Görevler
- [x] Spring Boot projesini Spring Initializr ile oluştur (Web, Data JPA, PostgreSQL Driver, Validation, Actuator, Security)
- [x] Katmanlı paket yapısını oluştur: `com.ecommint.accounthr.{config,controller,service,repository,domain,dto,mapper}`
- [x] Angular kur (sade layout: sidebar/topbar), klasör yapısı + API client (HttpClient) iskeleti
- [x] `docker-compose.yml`, `Dockerfile` (api) yaz; frontend dev'de `npm start` ile ayrı çalışır
- [x] Actuator health + custom `/api/health` ekle
- [x] README + `.env.example` yaz

## Teknik Notlar
- Java 21 LTS + Spring Boot 3.x öneriliyor; build aracı Maven (ekip alışkın değilse Gradle de olur)
- Hazır admin temaları lisanslı olabilir; tema dosyaları repoya ekleneceği için lisans/erişim kullanıcıdan teyit alınmalı
- PostgreSQL 16; Docker volume ile veri kalıcılığı
- Profil yönetimi: `application-local.yml`, `application-staging.yml`, `application-prod.yml`
- Frontend ve backend ayrı container; CORS dev'de açık, prod'da reverse proxy arkasında

## Açık Sorular / Riskler — KARARA BAĞLANDI
- ~~Monorepo mu yoksa ayrı repo mu?~~ → **İki ayrı repo** seçildi: `account-hr` (backend + altyapı) ve `account-hr-frontend` (Angular).
- ~~Hazır admin teması lisans riski?~~ → **Hazır tema kullanılmadı**; sade el-yazımı layout tercih edildi, lisans sorunu yok.

## Tamamlanma Kaydı
- **Durum:** ✅ Tamamlandı (Docker çalıştırma doğrulaması hariç) — 2026-06-24
- **YouTrack:** IK-225
- **Repolar:**
  - Backend + altyapı: https://github.com/DenizAydogduEcommint/account-hr — commit `7f8e246`
  - Frontend (Angular): https://github.com/DenizAydogduEcommint/account-hr-frontend — commit `64312d8`
- **Teknoloji:** Spring Boot 3.5.15 (Java 17 source; Docker imajı JDK 21) · Angular 18.2 · PostgreSQL 16 · Docker Compose
- **Not (Spring Boot sürümü):** Görevde "3.x" deniyordu; Spring Initializr artık 3.3.x sunmadığından en güncel kararlı **3.5.15** kullanıldı.
- **Doğrulananlar:** `./mvnw package` ✅, H2 ile `/api/health` entegrasyon testi ✅, `npm run build` ✅, `ng serve` → :4200 200 ✅
- **Kalan iş:** Docker'lı ortamda `docker compose up` ile tam stack ve tarayıcıdan canlı Angular→API çağrısı smoke testi.
