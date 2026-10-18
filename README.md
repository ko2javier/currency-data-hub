# API Service — KO2 Platform

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.5-6DB33F?logo=springboot&logoColor=white)
![CI](https://github.com/ko2javier/currency-data-hub/actions/workflows/deploy.yml/badge.svg)
![Coverage](https://img.shields.io/badge/coverage-64%25-yellow)
![Docker](https://img.shields.io/badge/Docker-deployed-2496ED?logo=docker&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

Weather and currency REST API with a multilevel cache strategy. Part of the [KO2 Platform](https://github.com/ko2javier/server-infrastructure) microservices ecosystem.

**Live demo:** [hub.ko2-oreilly.com](https://hub.ko2-oreilly.com) · **Swagger:** [api.ko2-oreilly.com/swagger-ui](https://api.ko2-oreilly.com/webjars/swagger-ui/index.html)

---

## What it does

- Returns real-time weather data for any city (temperature, conditions, coordinates)
- Returns live currency exchange rates for any base currency
- Resolves data through a 4-level cache before hitting external APIs

## Cache strategy

```
Request → Redis (10 min TTL)
        → MySQL (record < 10 min old)
        → External API (Open-Meteo / open.er-api.com)
        → Stale MySQL record (last resort — prevents hard failures)
```

This means the service never returns a 500 due to upstream unavailability — it falls back to the last known good value.

## Endpoints

All requests go through the Gateway at `api.ko2-oreilly.com`. Authentication is handled upstream — this service reads `X-User-Name` and `X-User-Roles` headers injected by the Gateway.

| Method | Path | Description |
|---|---|---|
| `GET` | `/weather/{city}` | Current weather for a city |
| `GET` | `/currency/{base}` | Exchange rates for a base currency |

## Tech stack

| | |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.3.5 |
| Persistence | Spring Data JPA + MySQL (Aiven) |
| Cache | Redis 7.2 (self-hosted, same Docker network) |
| External APIs | Open-Meteo (weather), open.er-api.com (currency) |
| Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Tests | JUnit 5 + Mockito + Testcontainers (real MySQL and Redis) — 40 tests, 64% instruction coverage (JaCoCo) |
| Build | Gradle |
| Deploy | GitHub Actions → ghcr.io → Hetzner VPS, pinned by commit SHA |

## Tests

```bash
./gradlew test jacocoTestReport     # 40 tests, ~1.5 min
# Report: build/reports/jacoco/test/html/index.html
```

Two layers. **Unit tests** (Mockito) cover the decision logic of `WeatherService` and `CurrencyService` and the controllers. **Integration tests** run against a **real MySQL and a real Redis** in containers (Testcontainers), because the thing worth proving here is the three-level cache itself: what one level writes, the next one reads.

What the integration tests pin:

- **The cache chain** — a first call hits the external API and writes Redis (with a real 10-minute TTL) and the table; a second call is served from Redis; with Redis empty the table answers; with the table older than 10 minutes the API is called again and the **existing row is updated**, not duplicated (the table has a unique index that a mocked repository would never enforce); and with the API down a stale row is returned rather than an error.
- **Key normalisation** — `Madrid`, ` MADRID ` and `madrid` share one cache entry; `eur`/`EUR` likewise; and `EUR→USD` and `USD→EUR` are two different rows.
- **Security** — the identity headers forwarded by the gateway, the role checks, and the Kafka event published with the real username and client IP.

The two third-party HTTP clients (Open-Meteo and the exchange-rate API) and the Kafka producer are the only things replaced by test doubles — a test that calls someone else's API fails when that API has a bad day, which says nothing about this code. That is why they show as uncovered in the JaCoCo report.

**The suite was tested against itself.** Four faults were injected one at a time — a ten-year Redis TTL, inserting a new cache row instead of updating, dropping the currency upper-casing, and ignoring the role header — and each turns the suite red.

CI runs all of it on every push to `master` **before** anything is built or deployed.

## Deployment

```
push to master ──▶ tests          40 tests, real MySQL + Redis via Testcontainers
                     └──▶ build   image to ghcr.io, tagged `latest` and the commit SHA
                            └──▶ deploy   SSH: pull that SHA, restart, wait for 200
```

Each stage gates the next. **The image is built here, not on the VPS**, which used to spend minutes of compilation and scarce RAM on every push. Deploys pin the commit SHA rather than `latest`, so the running container states which code it serves and a rollback is the same command with the previous SHA.

## Part of the KO2 Platform

```
Frontend (Angular 19 · Vercel)
    └── API Gateway :7000  ← routes & validates JWT
            ├── Auth Service :4000  ← login / logout / token blacklist
            └── API Service :5000  ← this repo
```

→ [server-infrastructure](https://github.com/ko2javier/server-infrastructure) — full architecture, Docker Compose, live demo credentials

## License

MIT
