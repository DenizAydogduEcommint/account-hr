# Architecture

account-hr is a monorepo with an Angular SPA talking to a Spring Boot REST API
backed by PostgreSQL. The frontend reads its API base URL from an environment
file and calls `/api/...`; in dev CORS is open for `http://localhost:4200`, and
in production the SPA and API sit behind a reverse proxy. The backend is layered
(`controller → service → repository → domain`, with `dto`/`mapper`/`config`
supporting them); auth is not implemented yet (security is open with a TODO for
JWT). Everything runs together via Docker Compose (`db` + `api`), while the
frontend runs from `npm start` in dev.

## Folder map

```
account-hr/
├── backend/                       # Spring Boot 3 (Java 17 source, JDK 21 Docker)
│   ├── src/main/java/com/ecommint/accounthr/
│   │   ├── config/                # SecurityConfig, CorsConfig
│   │   ├── controller/            # HealthController (GET /api/health)
│   │   ├── service/ repository/ domain/ dto/ mapper/   # layers (skeleton)
│   │   └── AccountHrApplication.java
│   ├── src/main/resources/        # application*.yml (local/docker/staging/prod)
│   ├── src/test/                  # HealthControllerIT (H2), application-test.yml
│   ├── Dockerfile                 # multi-stage: maven:3.9-temurin-21 → temurin:21-jre
│   └── pom.xml
├── frontend/                      # Angular 18 (standalone, SCSS, routing)
│   └── src/app/
│       ├── core/                  # ApiService, models, http-error.interceptor
│       ├── layout/                # LayoutComponent + Sidebar + Topbar (shell)
│       └── pages/dashboard/       # DashboardComponent (health status card)
├── docker-compose.yml             # db (postgres:16) + api + adminer
├── .env.example  .gitignore  README.md
└── docs/architecture.md
```
