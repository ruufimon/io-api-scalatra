# Scalatra Ping API

A minimal Scala 3 and Scalatra service exposing health-check endpoints. Redis
effects are represented with Cats Effect `IO`; Redis and Jetty are managed as
`Resource` values and released safely when the application stops.

## Run

```bash
sbt run
```

The server listens on port `8080` by default. Set `PORT` to override it.

```bash
curl http://localhost:8080/ping
```

Response:

```json
{"status":"ok"}
```

Health endpoints:

- `GET /live` returns `200` while the API process is running and does not
  contact Redis.
- `GET /ready` returns `200` when Redis responds to `PING`, or `503` when
  Redis is unavailable.

## Test

```bash
sbt test
```

## Redis

Start Redis with Docker Compose:

```bash
docker compose up -d redis
```

Redis is available at `localhost:6379`. To stop it:

```bash
docker compose down
```

Each successful `GET /ping` appends a JSON log entry containing the event name
and UTC timestamp to the Redis list `ping:logs`. The application connects to
`redis://localhost:6379` by default; override this with the `REDIS_URL`
environment variable.

Inspect the log entries with:

```bash
docker compose exec redis redis-cli LRANGE ping:logs 0 -1
```
