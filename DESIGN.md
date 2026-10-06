# Redis Availability at API Startup

## Current behavior

This branch implements fail-fast startup with standard synchronous Scala. It
creates a Jedis client, sends Redis `PING`, and only constructs and starts Jetty
after that call succeeds. A Redis exception propagates out of `main`, causing
the process to exit immediately.

The endpoints then behave as follows:

- The process never begins listening when Redis is unavailable at startup.
- `GET /live` returns `200` after successful startup.
- `GET /ready` attempts Redis `PING` and returns `503` with
  `{"status":"not_ready"}` when Redis is unavailable.
- `GET /ping` attempts to append a log entry to the Redis list `ping:logs`. If
  Redis is unavailable, the Redis effect fails, no entry is written, and the
  request fails rather than returning a successful ping response.

No effect library is used. Dependency methods return `Unit`, resource cleanup
uses `scala.util.Using`, and failures use ordinary exception propagation.

## Solution 1: Fail-fast startup

The application checks Redis with `PING` before starting Jetty. If Redis cannot
be reached, application startup fails and the process exits. A container
orchestrator can restart it until Redis becomes available.

### Advantages

- A running API always has its required Redis dependency.
- No requests are accepted when ping logs cannot be persisted.
- Failure behavior is simple and immediately visible during deployment.

### Disadvantages

- Redis startup ordering directly controls API availability.
- Temporary Redis outages can cause application restart loops.
- The API cannot expose liveness or diagnostics while waiting for Redis.
- Recovery normally depends on a process or container restart.

## Solution 2: Degraded operation

The HTTP server starts independently from Redis. Liveness continues to report
that the process is running, while readiness and Redis-dependent requests
reflect the dependency failure.

### Advantages

- The API can expose liveness and diagnostics while Redis is unavailable.
- It can recover automatically when Redis becomes available again.
- Deployments are less sensitive to container startup order.
- Readiness checks prevent traffic from being routed prematurely.

### Disadvantages

- Redis-dependent endpoint failures must be handled explicitly.
- Requests may wait for the Redis connection timeout unless bounded by an
  application timeout.
- Retries, circuit breaking, or buffering add complexity.
- Returning success before persistence would risk losing required log entries.

## Comparison

### Two solutions

|                              | Fail-fast startup                       | Degraded operation                                          |
|------------------------------|-----------------------------------------|-------------------------------------------------------------|
| Startup                      | Run Redis `PING`; exit if unavailable   | Start API even without Redis                                |
| `/ping` while Redis is down  | API is unavailable                      | Return `503 Service Unavailable`                            |
| Recovery                     | Requires process/container restart      | Automatically works when Redis returns                      |
| Complexity                   | Simple                                  | Requires error handling and possibly retries                |
| Best for                     | Redis is an absolute dependency         | Independent deployments and temporary Redis outages        |

## Demonstration decision

Use fail-fast startup:

1. Construct the Redis logger before the HTTP server.
2. Call Redis `PING` during logger creation.
3. Let a failed `PING` escape from `main` so the process exits non-zero.
4. Start Jetty only after Redis validation succeeds.
5. Close the Redis client with `scala.util.Using` when the server stops.

This keeps the startup invariant explicit: if the process is accepting HTTP
traffic, its required Redis dependency passed validation during startup.
