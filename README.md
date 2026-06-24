# account-hr

E-Commint Ön Muhasebe & Fatura Yönetim Platformu — monorepo skeleton (IK-225 / E1-01).

Two parts plus infra:

- **`backend/`** — Java + Spring Boot 3 REST API (Maven, `./mvnw`).
- **`frontend/`** — Angular 18 SPA (standalone components, SCSS).
- **`docker-compose.yml`** — PostgreSQL 16 + API (+ Adminer) for one-command startup.

## Prerequisites

| Tool | Version |
|------|---------|
| Java (JDK) | **17+** (built/tested locally on 17; the Docker image uses 21) |
| Node.js | **18+** |
| npm | 10+ |
| Docker + Compose | for the full stack (`docker compose`) |

> Note: the backend source level is **Java 17** (`pom.xml` `java.version`), so it
> compiles and runs on a local JDK 17. The backend **Docker image builds/runs on
> JDK 21** (`eclipse-temurin:21`). Java 17 source compiles cleanly on JDK 21.

## Environment setup

```bash
cp .env.example .env   # .env is gitignored — put real secrets there
```

## Run locally (without Docker)

You need a PostgreSQL on `localhost:5432` (db/user/pass = `accounthr`). The
quickest way is just the DB container:

```bash
docker compose up -d db        # only Postgres
```

**Backend** (profile `local`):

```bash
cd backend
./mvnw spring-boot:run
# → http://localhost:8080
```

**Frontend**:

```bash
cd frontend
npm install
npm start                      # ng serve → http://localhost:4200
```

## Run the full stack (Docker)

```bash
docker compose up -d           # db + api (+ adminer on :8081)
```

- API: http://localhost:8080
- Adminer (DB UI): http://localhost:8081

## Health check

```bash
curl localhost:8080/api/health
# {"status":"UP"}
```

The Angular dashboard (`/dashboard`) calls this endpoint on load and shows a
green "API: UP" card when reachable, or a red "API: unreachable" card otherwise
(it degrades gracefully when the backend is down).

## Tests

```bash
cd backend && ./mvnw test      # includes H2-backed HealthControllerIT (no Postgres needed)
cd frontend && npm test        # Karma (headless requires Chrome)
```

## Spring profiles

| Profile | Datasource | `ddl-auto` |
|---------|-----------|-----------|
| `local` | localhost Postgres (hardcoded dev creds) | `update` |
| `docker` | `db:5432` from env | `update` |
| `staging` / `prod` | all from env vars | `validate` |

API base URL for the frontend comes from `src/environments/environment*.ts`.

## Issue tracking

Work is tracked in YouTrack (project **IK / Ön Muhasebe**). Prefix commits with
the issue code, e.g. `IK-225: ...`.
