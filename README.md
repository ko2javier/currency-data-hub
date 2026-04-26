# API Service — KO2 Platform

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.5-6DB33F?logo=springboot&logoColor=white)
![Tests](https://img.shields.io/badge/tests-15%20passed-brightgreen)
![Coverage](https://img.shields.io/badge/coverage-50%25-yellow)
![Docker](https://img.shields.io/badge/Docker-deployed-2496ED?logo=docker&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)

Weather and currency REST API with a multilevel cache strategy. Part of the [KO2 Platform](https://github.com/ko2javier/server-infrastructure) microservices ecosystem.

**Live demo:** [hub.ko2-oreilly.com](https://hub.ko2-oreilly.com) · **Swagger:** [api.ko2-oreilly.com/swagger-ui](http://167.235.77.17:7000/webjars/swagger-ui/index.html)

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
| Cache | Redis (Railway) |
| External APIs | Open-Meteo (weather), open.er-api.com (currency) |
| Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Tests | JUnit 5 + Mockito — 15 tests, 50% instruction coverage (JaCoCo) |
| Build | Gradle |
| Deploy | Docker · Hetzner VPS · GitHub Actions CI/CD |

## Test coverage

Covers `WeatherService`, `CurrencyService`, `WeatherController`, `CurrencyController` — including all cache paths: Redis hit, DB hit, DB expired, external API call, stale fallback, and city-not-found (404).

```bash
./gradlew test jacocoTestReport
# Report: build/reports/jacoco/test/html/index.html
```

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
