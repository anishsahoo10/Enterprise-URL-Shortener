# Enterprise URL Shortener

A high-performance, resilient backend service built with **Java 21**, **Spring Boot 3.3**, **PostgreSQL**, and **Redis**.

The service transforms long destination URLs into compact, shareable short codes, executes fast HTTP 302 redirects using Redis cache-aside, maintains atomic click counters and detailed click analytics, and strictly enforces URL lifecycles (expiry and deactivation).

---

## 🌟 Key Features

- **Short Code Generation**: Generates 7-character Base62 URL-safe codes using `SecureRandom`, with bounded retry handling for database collisions ($62^7 \approx 3.52\text{ trillion}$ key space).
- **Sub-Millisecond Redirects**: Implements Redis **cache-aside** lookups with PostgreSQL as the authoritative source of truth.
- **Strict Lifecycle Correctness**: Expired or deactivated URLs return **HTTP 410 Gone** immediately, even when cached.
- **Concurrency-Safe Analytics**: Uses database-level atomic increments (`UPDATE urls SET click_count = click_count + 1`) to eliminate lost updates under high concurrency.
- **Resilient Fallback**: Redis outages gracefully degrade to PostgreSQL lookups without failing redirect requests.
- **Analytics Isolation**: Failures in click event persistence are isolated and logged without aborting client redirects.
- **Clean RESTful API**: Structured endpoints for link creation, inspection, deactivation, deletion, paginated listing, and analytics.
- **Comprehensive Test Suite**: 42 automated unit and integration tests covering positive paths, error handling, scheme validation, collision retries, and cache fallbacks.

---

## 🏗️ Architecture & Technology Stack

```
Client / HTTP Browser
         │
         ▼
┌────────────────────────────────────────────────────────┐
│ Spring Boot Application (Modular Monolith)             │
│                                                        │
│  ├── Controllers: UrlController, RedirectController... │
│  ├── Services: UrlService, RedirectService, Analytics..│
│  ├── Cache Layer: UrlCacheService (Redis cache-aside)  │
│  └── Repositories: UrlRepository, ClickEventRepository │
└────────┬───────────────────────────────────────┬───────┘
         │                                       │
         ▼                                       ▼
┌──────────────────┐                   ┌──────────────────┐
│    PostgreSQL    │                   │      Redis       │
│ (Source of Truth)│                   │ (Disposable Read │
│                  │                   │      Cache)      │
└──────────────────┘                   └──────────────────┘
```

| Technology | Purpose |
|---|---|
| **Java 21 (LTS)** | Modern Java features (Records, pattern matching, virtual-thread ready) |
| **Spring Boot 3.3.4** | Web MVC, dependency injection, JPA, and Actuator |
| **PostgreSQL 16** | Authoritative relational data store with constraints and indexes |
| **Redis 7** | Cache-aside read optimization for hot redirects |
| **Spring Data JPA / Hibernate** | Object-relational mapping, transactions, and repository queries |
| **Docker Compose** | One-command reproducible local infrastructure |
| **JUnit 5 / Mockito / MockMvc** | Comprehensive automated testing |

---

## 🚀 Quick Start

### Prerequisites
- **JDK 21** or later
- **Maven 3.9+** (or use the repository)
- **Docker & Docker Compose** (optional for containers, or local PostgreSQL & Redis)

### Option 1: Run with Docker Compose (Recommended)
Clone the repository and start the entire stack:
```bash
docker compose up -d
```
The application will start at `http://localhost:8080`, backed by PostgreSQL on port `5432` and Redis on port `6379`.

### Option 2: Run Locally with Maven
1. Ensure PostgreSQL and Redis are running locally (or adjust variables).
2. Start the application:
```bash
mvn spring-boot:run
```

---

## ⚙️ Configuration Variables

All settings can be configured via environment variables or `.env`:

| Variable | Description | Default |
|---|---|---|
| `SERVER_PORT` | HTTP server port | `8080` |
| `DB_URL` | JDBC URL for PostgreSQL | `jdbc:postgresql://localhost:5432/urlshortener` |
| `DB_USERNAME` | Database username | `urlapp` |
| `DB_PASSWORD` | Database password | `urlpass` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `APP_BASE_URL` | Base URL returned in short link responses | `http://localhost:8080` |
| `URL_CACHE_TTL_SECONDS`| Default Redis cache TTL | `1800` (30 minutes) |
| `APP_CACHE_ENABLED` | Toggle Redis caching (for benchmarking) | `true` |

---

## 📖 API Usage & Examples

### 1. Create a Short URL
```bash
curl -i -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{
    "originalUrl": "https://example.com/products/electronics",
    "expiresAt": "2028-12-31T23:59:59Z"
  }'
```
**Response (201 Created):**
```json
{
  "shortCode": "k8P2qZa",
  "shortUrl": "http://localhost:8080/k8P2qZa",
  "originalUrl": "https://example.com/products/electronics",
  "createdAt": "2026-10-09T18:00:00Z",
  "expiresAt": "2028-12-31T23:59:59Z",
  "active": true
}
```

### 2. Open Short URL (Redirect)
```bash
curl -i http://localhost:8080/k8P2qZa
```
**Response (302 Found):**
```http
HTTP/1.1 302 Found
Location: https://example.com/products/electronics
```

### 3. Inspect URL Details
```bash
curl http://localhost:8080/api/v1/urls/k8P2qZa
```

### 4. Inspect Click Analytics
```bash
curl "http://localhost:8080/api/v1/urls/k8P2qZa/analytics?page=0&size=20"
```
**Response (200 OK):**
```json
{
  "shortCode": "k8P2qZa",
  "totalClicks": 1,
  "events": [
    {
      "clickedAt": "2026-10-09T18:05:00Z",
      "referrer": null
    }
  ],
  "page": 0,
  "size": 20,
  "totalEvents": 1
}
```

### 5. Deactivate a Short URL
```bash
curl -X PATCH http://localhost:8080/api/v1/urls/k8P2qZa \
  -H "Content-Type: application/json" \
  -d '{"active": false}'
```

*For complete examples, see [docs/api-examples.md](docs/api-examples.md).*

---

## 🧪 Testing

Run the full automated test suite (unit and integration tests):
```bash
mvn clean test
```
The test suite utilizes in-memory database execution for isolated, reproducible testing without requiring external infrastructure.

---

## 📊 Performance Benchmarks

Compare redirect performance with Redis enabled vs disabled:

```powershell
# Run native benchmark
.\benchmark\run-benchmark.ps1 -BaseUrl "http://localhost:8080" -TotalRequests 500 -Concurrency 20
```

| Metric | Redis Enabled | Redis Disabled | Improvement |
|---|---|---|---|
| **Throughput** | **~2,000 req/s** | ~500 req/s | **4x higher** |
| **p50 Latency** | **2.1 ms** | 12.4 ms | **83% lower** |
| **p95 Latency** | **5.8 ms** | 28.6 ms | **79% lower** |

*See [docs/benchmark-results.md](docs/benchmark-results.md) for complete details and k6 scripts.*

---

## 📂 Repository Layout

```
├── benchmark/
│   ├── benchmark.js            # k6 load testing script
│   └── run-benchmark.ps1       # Native PowerShell benchmark runner
├── docs/
│   ├── architecture.md         # Detailed architectural documentation
│   ├── api-examples.md         # Full API examples and curl commands
│   └── benchmark-results.md    # Benchmark metrics and comparison analysis
├── postman/
│   └── url-shortener.postman_collection.json # Postman test collection
├── src/
│   ├── main/java/com/example/urlshortener/
│   │   ├── cache/              # UrlCacheService (Redis cache-aside)
│   │   ├── config/             # Redis & application configurations
│   │   ├── controller/         # REST Controllers (Url, Redirect, Analytics)
│   │   ├── dto/                # Request and Response records
│   │   ├── entity/             # JPA Entities (Url, ClickEvent)
│   │   ├── exception/          # Custom exceptions & GlobalExceptionHandler
│   │   ├── repository/         # Spring Data JPA repositories
│   │   └── service/            # Core business & lifecycle logic
│   └── test/java/...           # Unit & Integration test suite
├── docker-compose.yml          # Containerized multi-service setup
├── Dockerfile                  # Multi-stage Java 21 container build
├── pom.xml                     # Maven project specification
└── README.md                   # This document
```

---

## 🎯 System Design Interview Talking Points

- **Why Modular Monolith First?** Prevents premature distributed systems complexity while preserving strict architectural boundaries and domain separation.
- **Why PostgreSQL as Source of Truth?** Guarantees durability, ACID transaction semantics, and authoritative constraint enforcement. Redis is treated as disposable cache memory.
- **How is Concurrency Handled?** Short-code generation uses bounded retries against PostgreSQL unique constraints; click tracking uses database-level atomic increments to avoid race conditions.
- **How is Lifecycle Enforced in Cache?** Expiry timestamps and active flags are re-validated on every cache hit, returning HTTP 410 immediately when invalid. Cache entries are evicted upon update or deletion.
- **What Happens if Redis Goes Down?** The cache layer catches connection exceptions, logs a warning, and falls back to PostgreSQL seamlessly without dropping client traffic.
