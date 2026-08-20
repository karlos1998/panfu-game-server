# Panfu Game Server

Real-time Panfu engine built with Java 21, Spring Boot, WebFlux, Reactor Netty, MySQL and Redis.
The server replaces the legacy Java/Netty engine and the separate Node.js WebSocket proxy while
preserving the packet protocol expected by the Flash/Ruffle client.

## Architecture

- native WebSocket endpoint at `/game` on port `9596`;
- optional legacy TCP listener on port `9595` using the same packet pipeline;
- ordered command dispatch per player session;
- explicit command handlers for authentication, rooms, movement, chat, social actions and minigames;
- JDBC repositories for atomic ticket consumption, presence and opt-in idempotent minigame rewards;
- Redis-backed nonce protection for the signed Laravel internal API;
- Actuator health and Prometheus metrics.

The Java namespace is `it.letscode.panfu`. Runtime plugins from arbitrary JAR files were deliberately
not migrated because they allowed untrusted code to execute inside the game-server process.

## Security model

- a login ticket is consumed once inside a database transaction;
- player identifiers, usernames and moderator flags come from the authenticated session, not packets;
- frame sizes, parameter counts, coordinates, connection counts and idle time are bounded;
- browser WebSocket origins are allowlisted;
- non-moderator broadcasts are constrained to the player's room;
- the opt-in server reward path caps scores, checks time and records each server-generated round UUID once;
- Laravel commands use HMAC-SHA256 over method, path, timestamp, nonce and request-body hash;
- internal API nonces are single-use and expire in Redis.

## Configuration

Important environment variables:

| Variable | Purpose | Default |
| --- | --- | --- |
| `DB_URL` | MySQL JDBC URL | local `panfu` database |
| `DB_USERNAME`, `DB_PASSWORD` | MySQL credentials | local development values |
| `REDIS_HOST`, `REDIS_PORT` | replay-protection store | `localhost:6379` |
| `HTTP_PORT` | HTTP/WebSocket port | `9596` |
| `LEGACY_TCP_PORT` | legacy TCP port | `9595` |
| `LEGACY_TCP_ENABLED` | enable legacy TCP | `true` |
| `SERVER_AWARDS_ENABLED` | enable server-side minigame coin awards | `false` |
| `INTERNAL_API_SECRET` | shared Laravel HMAC secret | development-only value |
| `ALLOWED_ORIGINS` | comma-separated browser origins | localhost only |

Production deployments must provide a unique, randomly generated `INTERNAL_API_SECRET`, HTTPS/WSS
at the edge, restricted management-port access and the exact public origins.

Legacy Panfu SWFs currently calculate game-specific coin payouts and persist the resulting balance
through Laravel AMF. `SERVER_AWARDS_ENABLED` therefore remains disabled to prevent double payouts.
It may be enabled only after the affected games use a trusted, correlated server-side start/finish
flow and have verified per-game reward policies.

## Internal API

- `GET /internal/v1/health/connection`
- `POST /internal/v1/players/{playerId}/kick`
- `POST /internal/v1/players/{playerId}/buddy-status`

These routes reject unsigned, expired, modified or replayed requests.

## Build and tests

```bash
./gradlew clean check
```

The suite includes unit, property, architecture, transport and MySQL 8.4 Testcontainers tests.
JaCoCo reports are written to `build/reports/jacoco/test/html/index.html`; the build enforces at least
65% line coverage and 40% branch coverage.

Build the production image with:

```bash
docker build -t panfu-game-server .
```

The runtime image uses Java 21, runs as an unprivileged user and exposes ports `9595` and `9596`.
