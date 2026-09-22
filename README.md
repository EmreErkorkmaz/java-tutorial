# java-tutorial

A three-service Spring Boot system built to learn backend and distributed-systems engineering by
doing, not by reading. Every piece was added to solve a problem the previous step exposed — a
failing test, a slow query, a service that could not be restarted without breaking another one.

**This is a learning project, not production software.** Some weaknesses are deliberate and
documented at the end of this file. The code is heavily commented on purpose: the files double as
revision notes.

Java 21 · Spring Boot 4.1 · PostgreSQL 18 · RabbitMQ 4 · Redis 8 · Docker Compose · nginx · Zipkin ·
Testcontainers · GitHub Actions

## Architecture

```
                         ┌──────────────────────┐
  client ────────────────►  nginx gateway :8090 │  routing + rate limiting (10 r/s, burst 5)
                         └───────┬──────────────┘
                                 │
                 /api/products   │   /api/orders
              ┌──────────────────┴───────────────────┐
              ▼                                      ▼
   ┌──────────────────────┐   REST (sync)  ┌──────────────────────┐
   │ product-service :8080│◄───────────────│ order-service  :8081 │
   │ catalogue + auth     │  price lookup  │ orders, returns      │
   │ issues JWTs          │                │ publishes events     │
   └─────────┬────────────┘                └──────────┬───────────┘
             │                                        │ order.created
             ▼                                        ▼
        productdb ── Redis cache              ┌───────────────┐
                                              │  RabbitMQ     │──► order.created.dlq
        orderdb ◄────────────────────────┐    └───────┬───────┘
                                         │            │ (async)
                                         │            ▼
                                         │  ┌──────────────────────────┐
                                         └──│ notification-service:8082│ no database
                                            └──────────────────────────┘

  Every hop carries the same trace id → Zipkin :9411
```

The two boundaries are the point of the project:

- **Synchronous boundary** (`order-service` → `product-service`): an order cannot be priced without
  the catalogue, so the call is blocking — and therefore needs timeouts, retries and a bulkhead.
  Measured during an outage: response time went from 14 ms to 782 ms before those were added.
- **Asynchronous boundary** (`order-service` → RabbitMQ → `notification-service`): a notification is
  not worth failing an order for. Stopping `notification-service` does not stop order creation; the
  message waits in the queue. Messages that keep failing land in a dead-letter queue.

## Services

| Service | Port | Database | What it does |
|---|---|---|---|
| `product-service` | 8080 | `productdb` | Product catalogue with pagination and bulk reads, user registration/login, JWT issuing and validation, brute-force login counter, Redis caching |
| `order-service` | 8081 | `orderdb` | Order creation and returns, remote price lookup with timeout/retry/bulkhead, publishes `order.created` |
| `notification-service` | 8082 | none | A single `@RabbitListener`; exists to prove the async boundary works |

Main endpoints (through the gateway at `:8090`):

```
POST /api/auth/register     create a user
POST /api/auth             log in, returns a JWT
GET  /api/products         list (paginated)      GET /api/products/by-ids  bulk read
POST /api/products         create (ROLE_ADMIN)   PUT|DELETE /api/products/{id}
POST /api/orders           create an order       GET /api/orders, /api/orders/{id}
POST /api/orders/{id}/return
```

## Running it

```bash
docker compose up -d --build     # postgres, rabbitmq, redis, zipkin, gateway, three services
docker compose ps                # all services have health checks
```

| URL | What |
|---|---|
| `http://localhost:8090` | API through the nginx gateway |
| `http://localhost:9411` | Zipkin — one trace across all three services |
| `http://localhost:15672` | RabbitMQ management UI (`guest` / `guest`) |

Request collections for the IntelliJ HTTP Client live in `product-service/api.http` and
`order-service/api.http`.

## Tests

```bash
cd product-service && ./mvnw clean verify    # same for order-service
```

23 tests in three layers, each chosen for what it is good at:

- **Unit tests** with mocks, no Spring context — fast enough to run constantly.
- **Web-layer tests** (`@WebMvcTest`) for the HTTP contract: status codes, validation, security rules.
- **Integration tests** with **Testcontainers**, against a real PostgreSQL container rather than an
  in-memory database, so migrations and real SQL behaviour are covered.

CI (`.github/workflows/ci.yml`) runs the services as a matrix in parallel: `mvnw -B clean verify`
plus a Docker image build for each.

## What each part demonstrates

| Area | Where to look |
|---|---|
| Transaction boundaries, N+1 and `@EntityGraph`, indexing | `product-service/.../service/ProductService.java`, `repository/ProductRepository.java` |
| Flyway migrations across two databases | `*/src/main/resources/db/migration/` |
| JWT authentication and authorization rules | `config/SecurityConfig.java`, `config/JwtConfig.java`, `service/TokenService.java` |
| Timeout, retry and bulkhead on a remote call | `order-service/.../config/ResilienceConfig.java`, `client/ProductClient.java` |
| Event publishing, dead-letter queue, consumer | `order-service/.../config/RabbitConfig.java`, `notification-service/.../listener/` |
| Error-to-HTTP mapping in one place | `*/exception/GlobalExceptionHandler.java` |
| Gateway routing and rate limiting | `nginx/nginx.conf` |

## Deliberate limitations

Kept on purpose, because each one is a teaching point rather than an oversight:

- **The JWT secret is shared between services (HS256).** Symmetric signing means the ability to
  verify is the ability to issue: any service holding the secret can mint tokens. The fix (RS256 +
  JWKS) is understood but not implemented, because in real systems it is configured, not written.
- **Secrets are committed in plain text.** They are throwaway development values; real deployments
  read them from a secret store.
- **Both services use the same database user**, and `container_name` is pinned in `compose.yaml`,
  which prevents `docker compose --scale`.
- **`OrderService` is intentionally not `@Transactional`**: it makes a network call, and holding a
  database connection open across one is a bad trade.

## Notes

`notes/` contains the learning notes and interview cards written alongside the code, in Turkish.
They record why each decision was made, including the ones that turned out to be wrong.
