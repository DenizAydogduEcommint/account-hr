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
- [ ] CI: her push/PR'da backend testleri ve frontend build/lint otomatik çalışıyor; kırmızıysa merge engelleniyor
- [ ] Docker image'ları (api + frontend) pipeline'da build edilip bir registry'ye push ediliyor
- [ ] Staging ortamı tanımlı; main'e merge → staging'e otomatik deploy (veya tek tık)
- [ ] DB migration (Flyway) deploy sırasında otomatik uygulanıyor
- [ ] Pipeline durumu (yeşil/kırmızı) görünür; başarısız adım net rapor veriyor
- [ ] Rollback / önceki image'a dönme prosedürü dokümante

## Alt Görevler
- [ ] CI aracı seç (GitHub Actions / GitLab CI) ve pipeline tanımı yaz
- [ ] Backend job: cache + `mvn verify`
- [ ] Frontend job: `npm ci` + lint + build
- [ ] Docker build & push (registry seçimi)
- [ ] Staging deploy job (compose pull/up veya orchestration)
- [ ] Flyway migration adımı
- [ ] Ortam secret'larının CI/CD'de güvenli enjeksiyonu (E1-05 ile uyumlu)

## Teknik Notlar
- Deploy hedefi netleşmeli: tek VPS (Contabo zaten kullanılıyor) + docker compose mu, yoksa managed mı? MVP için tek sunucu + compose pratik
- Image registry: GitHub Container Registry / Docker Hub / self-hosted
- Staging DB ayrı; prod verisi staging'e kopyalanmaz (KVKK + güvenlik)
- Fatura dosya volume'ü deploy'da korunmalı (kalıcı mount)

## Açık Sorular / Riskler
- Deploy hedef altyapı (hangi sunucu/bulut) henüz net değil — kullanıcı kararı gerekli
- Prod deploy otomatik mi manuel onaylı mı? (Öneri: MVP manuel onay)
- Fatura dosyalarının yedeği/backup stratejisi (Drive zaten kopya tutuyor ama DB backup ayrı planlanmalı)
