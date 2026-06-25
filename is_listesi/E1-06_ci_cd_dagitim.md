# [E1-06] CI/CD ve dağıtım hattını kur (build/test/deploy + staging)

| Alan | Değer |
|------|-------|
| Epic | E1 — Temel Altyapı & Veri Modeli |
| Sprint | Sprint 2 |
| Öncelik | Orta |
| Tahmini Efor | 5 puan (~3 gün) |
| Bağımlılıklar | E1-01 |
| Etiketler | devops, ci-cd, deployment |

## Amaç
Her commit'te otomatik derleme + test çalıştıran ve onaylı sürümleri bir staging ortamına dağıtan bir boru hattı kurarak, ekibin elle deploy uğraşmadan güvenle ilerlemesini sağlamak.

## Açıklama / Bağlam
Geliştirme hızlandıkça "bende çalışıyordu" sorunlarını ve elle deploy hatalarını önlemek için CI/CD şart. CI: backend (Maven test), frontend (lint + build) ve Docker image üretimi. CD: staging ortamına otomatik (veya tek tık) dağıtım. Prod dağıtımı MVP'de manuel onaylı olabilir.

## Kabul Kriterleri (DOD)
- [x] CI: her push/PR'da backend testleri (`mvnw verify`, H2) ve frontend build otomatik (GitHub Actions). _Lint: frontend'de eslint yok → atlandı (borç). Merge engelleme = GitHub branch protection ayarı, kullanıcı tarafında açılacak._
- [x] Docker image'ları (api + frontend) pipeline'da build edilip GHCR'a push ediliyor (sadece main push'ta)
- [ ] **CD ERTELENDİ (kullanıcı kararı):** Staging ortamı + otomatik deploy — deploy hedefi (sunucu) belli olunca eklenecek
- [ ] DB migration (Flyway) deploy sırasında — CD ile (Flyway zaten uygulama boot'ta otomatik çalışıyor)
- [x] Pipeline durumu GitHub Actions UI'da görünür (yeşil/kırmızı, adım bazlı)
- [ ] Rollback / önceki image'a dönme prosedürü — CD ile dokümante edilecek

## Alt Görevler
- [x] CI aracı: **GitHub Actions** (iki repoda `.github/workflows/ci.yml`)
- [x] Backend job: maven cache + `./mvnw -B verify`
- [x] Frontend job: `npm ci` + `npm run build` (lint yok → atlandı)
- [x] Docker build & push: **GHCR** (`ghcr.io/denizaydogduecommint/account-hr-api` + `-frontend`, `latest`+SHA), `GITHUB_TOKEN` ile
- [ ] Staging deploy job → CD ertelendi
- [ ] Flyway migration adımı → CD ile (boot'ta zaten çalışıyor)
- [x] CI secret'ı: `GITHUB_TOKEN` (CI için yeterli; deploy secret'ları CD aşamasında)

## Teknik Notlar
- Deploy hedefi netleşmeli: tek VPS (Contabo zaten kullanılıyor) + docker compose mu, yoksa managed mı? MVP için tek sunucu + compose pratik
- Image registry: GitHub Container Registry / Docker Hub / self-hosted
- Staging DB ayrı; prod verisi staging'e kopyalanmaz (KVKK + güvenlik)
- Fatura dosya volume'ü deploy'da korunmalı (kalıcı mount)

## Açık Sorular / Riskler
- **Deploy hedef altyapı belirsiz → CD ertelendi.** Kullanıcı "şimdilik sadece CI" dedi (2026-06-25). Hedef netleşince (Contabo VPS+compose vb.) CD eklenecek.
- Prod deploy otomatik mi manuel? → MVP manuel onay (CD'de uygulanacak).
- Fatura dosya backup → Drive kopya tutuyor; DB backup ayrı planlanacak (CD/ops).

## Tamamlanma Kaydı (KISMİ — CI tamam, CD bekliyor)
- Durum: CI tamamlandı; **CD/staging ertelendi** (deploy hedefi kullanıcı kararına bağlı) — 2026-06-25
- YouTrack: IK-230 (sıralı varsayım — teyit edilecek)
- Repolar: account-hr (backend ci.yml) + account-hr-frontend (Dockerfile + nginx.conf + .dockerignore + ci.yml)
- CI içeriği: push/PR'da backend `mvnw verify` (H2, ~45 test) + frontend `npm ci`+`build`; main'de Docker image → GHCR (api + frontend)
- Doğrulama: frontend `npm run build` geçti (dist/frontend/browser); workflow'lar **actionlint v1.7.12 ile 0 hata**; secret taraması temiz (sadece GITHUB_TOKEN)
- **Açık doğrulama:** ilk gerçek Actions çalışması push sonrası GitHub Actions sekmesinde görülecek (lokalde gh auth yok, server-side çalışır)
- Kalan iş (CD): staging deploy job, Flyway deploy adımı (boot'ta zaten var), rollback prosedürü, branch protection (merge engelleme), deploy secret'ları — deploy hedefi belli olunca
