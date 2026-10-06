# Redis Availability at API Startup

## Current behavior

The API follows the degraded-operation design. Creating the Jedis client does
not validate the Redis connection, so Jetty can start listening while Redis is
unavailable.

The endpoints then behave as follows:

- `GET /live` returns `200` because it does not contact Redis.
- `GET /ready` attempts Redis `PING` and returns `503` with
  `{"status":"not_ready"}` when Redis is unavailable.
- `GET /ping` attempts to append a log entry to the Redis list `ping:logs`. If
  Redis is unavailable, the Redis effect fails, no entry is written, and the
  request fails rather than returning a successful ping response.

Redis effects are represented with Cats Effect `IO`. Failures are handled at
the synchronous Scalatra boundary before rendering the HTTP response.

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

### When to use fail-fast and `IO`

Fail-fast and Cats Effect `IO` address different decisions. Fail-fast defines
whether the service may start without a dependency; `IO` defines how the
application represents, composes, and runs side effects. Either startup policy
can be implemented with or without `IO`.

| Consideration | Fail-fast startup | Cats Effect `IO` |
|---|---|---|
| Primary purpose | Enforce required dependencies before accepting traffic | Represent and compose effects, errors, cancellation, concurrency, and resource lifecycles |
| Use it when | The service cannot do useful or correct work without Redis | The application has multiple effects, concurrent work, cancellation, retries, or resources that benefit from structured management |
| Reconsider it when | The service can provide liveness, diagnostics, or unaffected features while Redis is unavailable | The program is small, synchronous, and its lifecycle is clear with ordinary Scala control flow |
| Failure behavior | Check Redis before binding the HTTP port and exit when the check fails | Keep failures inside `IO` until the application boundary, where they can be handled or allowed to terminate the process |
| Recovery model | Usually rely on a process or container restart | Choose explicitly: retry, degrade, return an error, or terminate |
| Resource cleanup | Use `try`/`finally`, `Using`, or another lifecycle mechanism | Use `Resource` and scoped `use` blocks |
| Relationship | Can run the startup check synchronously or as an `IO` before server startup | Can implement either fail-fast startup or degraded operation |

| Situation | Recommended approach |
|---|---|
| Redis is mandatory and the service is small and synchronous | Standard Scala with fail-fast startup |
| Redis is mandatory and the service already uses Cats Effect | Run the Redis check in `IO` before acquiring or starting the HTTP server |
| Redis is optional or only some routes depend on it | Degraded operation; report dependency health through readiness |
| The service needs cancellation-safe resources or concurrent background work | Use `IO`/`Resource`, then choose fail-fast or degraded startup separately |

## Demonstration decision

Use degraded operation with strict success semantics:

1. Allow Jetty to start even if Redis is unavailable.
2. Keep `GET /live` independent from Redis.
3. Make `GET /ready` return `503` until Redis responds to `PING`.
4. Do not return a successful `GET /ping` response unless its Redis log entry
   was persisted.
5. Return `503 Service Unavailable` from `/ping` for a Redis failure.
6. Add a bounded Redis timeout so dependency failures do not leave requests
   waiting for too long.

This preserves the invariant that every successful ping has a durable Redis log
entry while allowing the process to start, report health, and recover without a
restart.
