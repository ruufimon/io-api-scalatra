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
