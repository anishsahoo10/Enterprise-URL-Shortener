# Output
<img width="959" height="539" alt="Screenshot 2026-10-10 001737" src="https://github.com/user-attachments/assets/e8cfcc62-49fa-4997-be41-b0ebf77d2ac2" />

<div style="display: flex; gap: 10px;">
  <img src="https://github.com/user-attachments/assets/e0943a4a-a791-435e-86fb-cfe07ef78055" width="49%" alt="Screenshot 1" />
  <img src="https://github.com/user-attachments/assets/a5d96ffc-f7bd-4a35-a1f9-5016a9e72bf3" width="49%" alt="Screenshot 2" />
</div>





# Enterprise URL Shortener

A URL shortener built with Java 21, Spring Boot, PostgreSQL, and Redis.

The idea behind this project was to build more than a basic URL shortener. Along with generating short links, it handles redirects, tracks clicks, supports URL expiry and deactivation, and uses Redis to speed up frequently accessed links.

The application follows a modular monolith architecture, keeping the code organized into controllers, services, repositories, and cache-related components.

## Features

- **Short URL generation:** Generates 7-character Base62 codes using `SecureRandom`.
- **Fast redirects:** Uses Redis to cache frequently accessed URLs and reduce database queries.
- **URL expiry:** Expired or deactivated links return `410 Gone`, including when a URL is present in the cache.
- **Click tracking:** Maintains click counts using atomic database updates.
- **Click analytics:** Stores click events and provides paginated analytics.
- **Redis fallback:** Falls back to PostgreSQL when Redis is unavailable.
- **URL management:** Supports creating, inspecting, deactivating, and deleting short URLs.
- **Automated tests:** Includes unit and integration tests for core functionality, validation, collision handling, and cache fallback.

## Tech Stack

| Technology | What it's used for |
|---|---|
| Java 21 | Application logic |
| Spring Boot 3.3 | REST APIs and application configuration |
| Spring Data JPA | Database access |
| PostgreSQL 16 | Persistent storage for URLs and click data |
| Redis 7 | Caching URL lookups |
| Maven | Dependency management and builds |
| Docker Compose | Running the application and its dependencies locally |
| JUnit 5, Mockito, MockMvc | Testing |

## How It Works

The application uses PostgreSQL as its main database and Redis as a cache for URL lookups.

When someone opens a short link, the application checks Redis first. If the URL isn't cached, it fetches the record from PostgreSQL and caches the result. Before redirecting, it checks whether the link is still active and hasn't expired.

Click counts are updated using database-level atomic operations. Click events are handled separately so that an analytics persistence failure doesn't unnecessarily interrupt the redirect.

The application is structured as a modular monolith rather than multiple microservices. This keeps local development and deployment straightforward while separating the main responsibilities of the codebase.

### Architecture

```text
                 Client / Browser
                        |
                        v
                Spring Boot App
                        |
          +-------------+-------------+
          |             |             |
          v             v             v
     Controllers     Services       Cache
          |             |             |
          +-------------+-------------+
                        |
               +--------+--------+
               |                 |
               v                 v
          PostgreSQL           Redis
        Primary storage      URL cache
```

## Getting Started

### Prerequisites

Make sure you have the following installed:

- JDK 21
- Maven 3.9+ (or use the Maven Wrapper if included)
- Docker with Docker Compose

### Run with Docker Compose

Clone the repository and move into the project directory.

```bash
git clone <your-repository-url>
cd <your-project-directory>
docker compose up -d --build
```

Once the services have started, the application should be available at:

`http://localhost:8080`

PostgreSQL and Redis are configured through the Docker Compose file.

Check the application logs if it takes a little time to start:

```bash
docker compose logs -f
```

### Run with Maven

If you're running the application directly, make sure PostgreSQL and Redis are available and the database configuration matches your local setup.

Then run:

```bash
mvn spring-boot:run
```

## Configuration

The following environment variables can be used to configure the application. The defaults below assume a local development environment.

| Variable | Description | Default |
|---|---|---|
| `SERVER_PORT` | Application port | `8080` |
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/urlshortener` |
| `DB_USERNAME` | Database username | `urlapp` |
| `DB_PASSWORD` | Database password | `urlpass` |
| `REDIS_HOST` | Redis hostname | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `APP_BASE_URL` | Base URL used in generated links | `http://localhost:8080` |
| `URL_CACHE_TTL_SECONDS` | Default cache TTL | `1800` |
| `APP_CACHE_ENABLED` | Enable or disable caching | `true` |

For anything beyond local development, use appropriate secrets and credentials rather than the sample defaults.

## API Examples

The examples below assume the application is running locally on port `8080`.

### 1. Create a short URL

```bash
curl -X POST http://localhost:8080/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{
    "originalUrl": "https://example.com/products/electronics",
    "expiresAt": "2028-12-31T23:59:59Z"
  }'
```

The endpoint returns the generated short code, short URL, original URL, creation time, expiry time, and active status.

Example response:

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

The short code and timestamps above are illustrative.

### 2. Redirect to the original URL

Open the generated link in a browser or use:

```bash
curl -i http://localhost:8080/k8P2qZa
```

For an active, unexpired link, the application responds with `302 Found` and a `Location` header containing the original URL.

### 3. Get URL details

```bash
curl http://localhost:8080/api/v1/urls/k8P2qZa
```

### 4. View click analytics

```bash
curl "http://localhost:8080/api/v1/urls/k8P2qZa/analytics?page=0&size=20"
```

The analytics endpoint returns the total click count and a paginated list of click events, including the timestamp and available referrer information.

### 5. Deactivate a short URL

```bash
curl -X PATCH http://localhost:8080/api/v1/urls/k8P2qZa \
  -H "Content-Type: application/json" \
  -d '{"active": false}'
```

Once deactivated, the link should no longer redirect to the original destination.

For additional examples, see [`docs/api-examples.md`](docs/api-examples.md).

## Running Tests

Run the test suite with:

```bash
mvn clean test
```

The tests cover the main URL management and redirect flows, input validation, error handling, code-generation collisions, and Redis fallback behaviour.

The project includes 42 automated tests. The exact coverage depends on the current test suite.

## Performance Testing

The project includes a benchmark script for comparing redirects with Redis enabled and disabled.

On Windows PowerShell, run:

```powershell
.\benchmark\run-benchmark.ps1 `
  -BaseUrl "http://localhost:8080" `
  -TotalRequests 500 `
  -Concurrency 20
```

The repository currently documents the following benchmark results:

| Metric | Redis enabled | Redis disabled |
|---|---:|---:|
| Throughput | ~2,000 req/s | ~500 req/s |
| p50 latency | 2.1 ms | 12.4 ms |
| p95 latency | 5.8 ms | 28.6 ms |

These figures should be treated as environment-specific results, not guaranteed performance. Actual results depend on the machine, database configuration, cache hit rate, and test conditions.

See [`docs/benchmark-results.md`](docs/benchmark-results.md) for the benchmark details and scripts.

## Project Structure

```text
├── benchmark/
│   ├── benchmark.js
│   └── run-benchmark.ps1
├── docs/
│   ├── architecture.md
│   ├── api-examples.md
│   └── benchmark-results.md
├── postman/
│   └── url-shortener.postman_collection.json
├── src/
│   ├── main/java/com/example/urlshortener/
│   │   ├── cache/
│   │   ├── config/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── exception/
│   │   ├── repository/
│   │   └── service/
│   └── test/java/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── README.md
```

## Design Decisions

### Why a modular monolith?

For this project, a modular monolith keeps deployment simple without putting all the application logic into a single class or package. Controllers, services, repositories, and caching logic have separate responsibilities.

### Why PostgreSQL and Redis?

PostgreSQL stores the URL records and click data persistently. Redis helps reduce repeated database lookups for frequently accessed short links. Since Redis is used as a cache, the application can fall back to PostgreSQL when the cache is unavailable.

### How are short-code collisions handled?

The application generates a candidate code and relies on a database uniqueness constraint to prevent duplicate short codes. If a collision occurs, it retries within a configured limit.

### How are concurrent clicks handled?

Click counts use an atomic database update rather than reading a count into application memory and writing back an incremented value. This avoids the lost-update problem associated with that read-modify-write approach.

### How does URL expiry work with caching?

A cached record must not be treated as permanently valid. The application checks the URL's active status and expiry before redirecting. Changes to a URL also require appropriate cache invalidation.

### What happens if Redis goes down?

Redis failures should not make an otherwise available PostgreSQL-backed redirect fail solely because the cache is unavailable. The cache layer falls back to the database, subject to the database and application remaining healthy.

## Future Improvements

Some areas that could be explored next:

- Rate limiting for URL creation and other sensitive endpoints.
- Authentication and per-user URL management.
- More detailed analytics and dashboard visualisation.
- Database migrations using Flyway.
- Structured logging, metrics, and monitoring.
- Load testing with larger request volumes and documented test environments.
- Asynchronous click-event processing if analytics volume becomes a bottleneck.

## License

Add a license if you intend to distribute or reuse this project publicly.
