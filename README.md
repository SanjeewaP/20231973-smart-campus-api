# Smart Campus Sensor & Room Management API

A RESTful API built with **JAX-RS (Jersey)** for the University of Westminster Smart Campus initiative. The service manages Rooms, Sensors, and historical SensorReadings across campus using a clean, resource-oriented design, custom exception mapping, and cross-cutting logging filters.

**Module:** 5COSC022W Client-Server Architectures
**Coursework:** 2025/26

---

## Table of Contents

1. [API Overview](#1-api-overview)
2. [Technology Stack](#2-technology-stack)
3. [Build & Run Instructions](#3-build--run-instructions)
4. [Sample curl Commands](#4-sample-curl-commands)
5. [Conceptual Report — Answers to Questions](#5-conceptual-report--answers-to-questions)

---

## 1. API Overview

The API exposes three primary resources under the base path `/api/v1`:

| Resource | Path | Description |
|---|---|---|
| Discovery | `GET /api/v1` | API metadata and HATEOAS links |
| Rooms | `/api/v1/rooms` | Physical rooms on campus |
| Sensors | `/api/v1/sensors` | Hardware sensors deployed in rooms |
| Readings (sub-resource) | `/api/v1/sensors/{sensorId}/readings` | Historical reading log for a sensor |

### Endpoint Summary

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1` | API discovery (HATEOAS root) |
| GET | `/api/v1/rooms` | List all rooms |
| POST | `/api/v1/rooms` | Create a room |
| GET | `/api/v1/rooms/{roomId}` | Get a single room |
| DELETE | `/api/v1/rooms/{roomId}` | Delete a room (blocked if sensors present) |
| GET | `/api/v1/sensors` | List all sensors |
| GET | `/api/v1/sensors?type=CO2` | Filter sensors by type |
| GET | `/api/v1/sensors/{sensorId}` | Get a single sensor |
| POST | `/api/v1/sensors` | Register a sensor (validates parent room) |
| GET | `/api/v1/sensors/{sensorId}/readings` | Get reading history |
| POST | `/api/v1/sensors/{sensorId}/readings` | Append a reading (blocked for non-ACTIVE sensors) |

### Error Responses

All errors return a JSON body — no raw stack traces are ever exposed.

| HTTP Status | Scenario |
|---|---|
| 400 Bad Request | Missing required fields in payload |
| 403 Forbidden | Posting a reading to a sensor in MAINTENANCE / OFFLINE state |
| 404 Not Found | Room or sensor id does not exist |
| 409 Conflict | Deleting a room that still has sensors attached |
| 415 Unsupported Media Type | Client sent non-JSON content |
| 422 Unprocessable Entity | Request payload references a non-existent resource (e.g. bad roomId on sensor creation) |
| 500 Internal Server Error | Any unhandled runtime error — sanitized, no stack trace |

---

## 2. Technology Stack

- **Java 11+**
- **Maven** (build)
- **JAX-RS 2.1** (Jakarta RESTful Web Services API)
- **Jersey 2.41** (JAX-RS reference implementation)
- **Grizzly HTTP** (embedded servlet container)
- **Jackson** (JSON serialization via `jersey-media-json-jackson`)

**No database is used.** All state lives in thread-safe `ConcurrentHashMap` instances inside a singleton `DataStore`, per the coursework constraint.

---

## 3. Build & Run Instructions

### Prerequisites
- JDK 11 or higher installed (`java -version`)
- Maven 3.6+ installed (`mvn -version`)

### Step-by-step

```bash
# 1. Clone the repository
git clone https://github.com/<your-username>/smart-campus-api.git
cd smart-campus-api

# 2. Build the project (produces a runnable fat JAR)
mvn clean package

# 3. Run the server
java -jar target/smart-campus-api.jar
```

The server starts on **`http://localhost:8080`** and the API is rooted at **`http://localhost:8080/api/v1`**.

You should see:
```
INFO: Smart Campus API started at http://localhost:8080/api/v1
INFO: Press Ctrl+C to stop the server.
```

The in-memory store is pre-seeded with two rooms (`LIB-301`, `CS-101`) and three sensors (`TEMP-001`, `CO2-001`, `OCC-001`) so you can test immediately.

---

## 4. Sample curl Commands

### (1) Discovery endpoint — HATEOAS root
```bash
curl -s http://localhost:8080/api/v1 | jq
```

### (2) List all rooms
```bash
curl -s http://localhost:8080/api/v1/rooms | jq
```

### (3) Create a new room
```bash
curl -s -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"ENG-204","name":"Engineering Workshop","capacity":25}' | jq
```

### (4) Register a new sensor (valid roomId → 201 Created)
```bash
curl -s -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-002","type":"Temperature","status":"ACTIVE","currentValue":21.0,"roomId":"ENG-204"}' | jq
```

### (5) Register a sensor with non-existent roomId (triggers 422)
```bash
curl -s -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-999","type":"Temperature","status":"ACTIVE","currentValue":0,"roomId":"GHOST-000"}' | jq
```

### (6) Filter sensors by type
```bash
curl -s "http://localhost:8080/api/v1/sensors?type=CO2" | jq
```

### (7) Post a reading to an ACTIVE sensor
```bash
curl -s -X POST http://localhost:8080/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":23.7}' | jq
```

### (8) Post a reading to a MAINTENANCE sensor (triggers 403)
```bash
curl -s -X POST http://localhost:8080/api/v1/sensors/OCC-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":5}' | jq
```

### (9) Retrieve reading history for a sensor
```bash
curl -s http://localhost:8080/api/v1/sensors/TEMP-001/readings | jq
```

### (10) Attempt to delete a room that still has sensors (triggers 409)
```bash
curl -s -X DELETE http://localhost:8080/api/v1/rooms/LIB-301 | jq
```

---

## 5. Conceptual Report — Answers to Questions

### Part 1.1 — JAX-RS Resource Lifecycle

**Question:** Explain the default lifecycle of a JAX-RS Resource class. Is a new instance instantiated for every incoming request, or does the runtime treat it as a singleton? Elaborate on how this architectural decision impacts the way you manage and synchronize your in-memory data structures to prevent data loss or race conditions.

**Answer:**

By default, JAX-RS treats every resource class as **per-request scoped** — a fresh instance is created for each incoming HTTP request and discarded when the response is sent. This is deliberate: it prevents accidental state leakage between unrelated clients and makes each request independent.

The consequence for in-memory state is significant. If I stored rooms or sensors as regular instance fields on `SensorRoomResource`, that data would vanish the moment the request completed, because the resource instance itself is garbage-collected. A client would POST a room, receive 201, and then the next `GET` — handled by a different instance of the same class — would return an empty list.

To work around this I centralised all mutable state in a **singleton `DataStore`** (classic singleton pattern via a `private static final` instance). Every resource instance obtains the same shared reference, so state persists across requests.

However, singletons introduce a new risk: because Grizzly serves requests on a thread pool, multiple resource instances on different threads can hit the `DataStore` concurrently. A plain `HashMap` would corrupt under concurrent `put` operations (lost updates, infinite loops in older JDKs, or `ConcurrentModificationException` on iteration). I therefore used `ConcurrentHashMap` for both the rooms and sensors collections. Its internal striped-lock design permits safe concurrent reads and writes without serialising the entire map.

Reads of single objects followed by mutations (e.g. "get the room, add a sensor id to its list, put it back") are still technically racy if two threads operate on the same room — but for the scope of this coursework the `ConcurrentHashMap` guarantees, combined with the fact that `ArrayList` inside a single sensor's reading list is only appended to sequentially per-sensor in typical usage, are sufficient. A production-grade version would wrap compound operations in `compute` lambdas or use explicit locks per room.

---

### Part 1.2 — HATEOAS and Hypermedia

**Question:** Why is the provision of "Hypermedia" (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?

**Answer:**

HATEOAS (Hypermedia As The Engine Of Application State) is the highest level of Roy Fielding's REST maturity model. Instead of clients hard-coding every URL they need, the server embeds navigational links directly in responses — just as a web browser discovers pages by following `<a>` tags rather than being pre-loaded with a sitemap.

Compared to static documentation, this brings several benefits:

1. **Loose coupling between client and server.** If I move `/api/v1/sensors` to `/api/v2/devices`, a HATEOAS client that reads `resources.sensors` from the discovery endpoint adapts automatically. A client built from static docs breaks and must be redeployed.
2. **Self-describing APIs.** New developers can explore the API by hitting the root and following links, much like a REST Swagger UI without the tooling. This dramatically reduces onboarding friction.
3. **State-driven navigation.** Responses can include only the links that are currently valid — e.g. a room response might omit the `delete` link if it still has sensors attached. The server effectively tells the client what it can do next, rather than the client guessing and getting 403/409 errors.
4. **Versioning and deprecation.** Servers can inject `deprecated` links or alternative URLs to steer clients toward newer endpoints without breaking existing ones.
5. **Reduced documentation drift.** Static docs rot the moment a developer renames a path. Hypermedia is always in sync with what the server actually serves.

In my discovery endpoint (`GET /api/v1`) I expose a `resources` map that acts as a lightweight HATEOAS directory — clients learn the paths to `rooms`, `sensors`, and the filtered/nested variants from the server itself.

---

### Part 2.1 — ID-only vs Full Object Responses

**Question:** When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client side processing.

**Answer:**

This is a classic bandwidth-versus-chattiness trade-off.

**Returning full objects** (my chosen approach for `GET /rooms`):
- *Pro:* one round trip gives the client everything it needs to render a list view — no follow-up requests, instant UI.
- *Pro:* simpler client logic; no loop of `GET /rooms/{id}` calls.
- *Con:* response size grows linearly with both room count and per-room field count. For thousands of rooms this can be tens or hundreds of KB.
- *Con:* wasted bytes if the client only needs names or a count.

**Returning IDs only:**
- *Pro:* payload is tiny and constant per-room — ideal for discovery or pagination previews.
- *Con:* the classic **N+1 problem** — rendering a 100-room list now requires 1 + 100 requests. On mobile or high-latency networks this is punishing.
- *Con:* the client becomes responsible for stitching data together, increasing code complexity.

The pragmatic answer for most campus-scale APIs is what I implemented: **return full objects but keep them small** (no deeply nested reading lists in the rooms collection response, for example). For larger systems the standard escape hatches are **pagination** (`?page=1&size=20`), **field selection** (`?fields=id,name`), and **link expansion** (`?expand=sensors`) — all of which preserve the one-round-trip ergonomics while controlling payload size.

---

### Part 2.2 — DELETE Idempotency

**Question:** Is the DELETE operation idempotent in your implementation? Provide a detailed justification by describing what happens if a client mistakenly sends the exact same DELETE request for a room multiple times.

**Answer:**

**Yes, my DELETE implementation is idempotent** — the server ends up in the same state regardless of how many times the request is repeated, which is the definition of idempotency in HTTP (RFC 9110).

Walking through a repeated `DELETE /rooms/ENG-204` for an empty room:

- **First call:** the room exists and has no sensors → `store.getRooms().remove(roomId)` → `204 No Content`. State: room is gone.
- **Second call:** the room no longer exists → my handler returns `404 Not Found`. State: room is still gone.
- **Third, fourth, nth call:** same as the second — `404`. State: still gone.

The *response status code* differs between the first and subsequent calls (204 vs 404), but idempotency is about **end state on the server**, not response equivalence. After one call or one hundred, the room `ENG-204` is absent from the store. No additional side effects occur. This is contrast to, say, a naive counter-increment endpoint which would be non-idempotent because each call changes state.

There's a subtle wrinkle with the `RoomNotEmptyException` case. If a room has sensors, the first DELETE returns 409 and the room remains. A repeated DELETE also returns 409 and the room still remains. The end state is unchanged across attempts, so idempotency still holds — the operation simply never succeeds.

Some teams argue that all DELETEs should return 204 even for absent resources, to make the client's error handling simpler. I chose to return 404 because it gives the client useful information (the resource wasn't there) without violating idempotency. Both conventions are defensible.

---

### Part 3.1 — @Consumes and Content-Type Mismatch

**Question:** We explicitly use the `@Consumes(MediaType.APPLICATION_JSON)` annotation on the POST method. Explain the technical consequences if a client attempts to send data in a different format, such as `text/plain` or `application/xml`. How does JAX-RS handle this mismatch?

**Answer:**

`@Consumes(MediaType.APPLICATION_JSON)` is part of JAX-RS's request dispatching machinery. It tells the runtime: "this method will only accept requests whose `Content-Type` header is `application/json`."

When a client POSTs with a mismatching header — say `Content-Type: application/xml` or `text/plain` — JAX-RS does the matching *before* ever invoking my method. The process is roughly:

1. JAX-RS resolves the URL to the candidate resource methods (those whose `@Path` and HTTP method match).
2. It filters those candidates against the request's `Content-Type` using each method's `@Consumes`.
3. If no method matches, JAX-RS short-circuits the dispatch and returns **HTTP 415 Unsupported Media Type** automatically. My method body never runs.

The important implication is that validation happens at the protocol boundary — I don't need defensive `if (contentType.equals(...))` checks inside my handler. The framework protects the method.

A separate but related concern is parseability. Even if the header says `application/json`, if the body is actually garbage like `not-json-at-all`, Jackson (the JSON provider) will fail to deserialise and Jersey returns **HTTP 400 Bad Request**. So the two failure modes are distinct:
- Wrong `Content-Type` header → 415 (framework-level, before dispatch).
- Correct header, malformed body → 400 (deserialiser-level).

This layered defense keeps resource code clean and makes the API's contract explicit to clients.

---

### Part 3.2 — Query Parameter vs Path for Filtering

**Question:** You implemented this filtering using `@QueryParam`. Contrast this with an alternative design where the type is part of the URL path (e.g., `/api/v1/sensors/type/CO2`). Why is the query parameter approach generally considered superior for filtering and searching collections?

**Answer:**

Both designs work, but the query-parameter approach aligns better with REST's resource-oriented principles.

The fundamental issue is **what the URL identifies**. In REST, a URL names a *resource*. `/sensors` is the collection of all sensors — a single, well-defined resource. A filter doesn't change what resource is being addressed; it changes which representation of that collection is returned. Query parameters are the idiomatic way to express "give me a view of this resource."

Specific advantages of `@QueryParam`:

1. **Composability.** `GET /sensors?type=CO2&status=ACTIVE&roomId=LIB-301` scales naturally. The path-based alternative would explode into combinatorial routes like `/sensors/type/CO2/status/ACTIVE/room/LIB-301`, which is brittle and unmaintainable.
2. **Optionality.** Query parameters are optional by nature. `/sensors` and `/sensors?type=CO2` both make sense. In the path-based design, `/sensors` and `/sensors/type/CO2` look like different resources rather than the same collection under different filters.
3. **Caching semantics.** Intermediate caches understand that `/sensors` and `/sensors?type=CO2` are different cacheable representations of the same underlying collection. Path-based variants are treated as genuinely separate resources.
4. **Discoverability and convention.** Every major REST API (GitHub, Stripe, Twilio) uses query parameters for filtering. Developers recognise the pattern instantly.
5. **Separation of identity and modification.** Paths identify; query strings modify. Mixing filtering into the path muddles this separation and often leads to ambiguous routes ("is `type` a sub-resource or a filter keyword?").

Path parameters belong in the URL when the value is part of the resource's **identity** — `/rooms/LIB-301` identifies a specific room — not when it's a filter criterion applied to a collection.

---

### Part 4.1 — Sub-Resource Locator Pattern

**Question:** Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path in one massive controller class?

**Answer:**

The sub-resource locator pattern is JAX-RS's mechanism for expressing resource *containment* — a sensor "has" readings, so `/sensors/{id}/readings` is naturally handled by a `SensorReadingResource` rather than by cramming another half-dozen methods into `SensorResource`.

Concretely, my `SensorResource.getReadingsSubResource(sensorId)` has no `@GET` or `@POST` annotation. That's the signal to JAX-RS: this method doesn't serve an HTTP verb directly — instead it *returns another resource object* that JAX-RS continues dispatching against using the remaining URL path. The parent resource essentially delegates the rest of the routing to a child class.

Architectural benefits:

1. **Single Responsibility Principle.** `SensorResource` handles sensor CRUD. `SensorReadingResource` handles reading CRUD. Neither class knows about the other's internals beyond a constructor argument. This is the same reasoning behind splitting a monolithic `UserController` into `UserController`, `UserAddressController`, `UserOrderController`, etc.
2. **Shared parent context.** My locator passes the parent `Sensor` object directly into the sub-resource's constructor. The sub-resource never has to re-look-up the sensor on every request — it's already scoped to its parent. This eliminates repetitive "find the sensor or 404" boilerplate inside each reading method.
3. **Security and validation choke-points.** Because every reading operation must go through the locator, I have a single place to enforce parent-level rules (existence of the sensor, for example). If I later add authorization — "you can only view readings for sensors you own" — that check lives in one method, not five.
4. **Testability.** `SensorReadingResource` can be unit-tested by instantiating it with a mock `Sensor`. There's no need to spin up the whole JAX-RS container or mock the data store — the dependency is explicit in the constructor.
5. **Scalability for large APIs.** Imagine extending the Smart Campus to `/sensors/{id}/readings/{rid}/annotations/{aid}/attachments/{atid}` — without sub-resource locators, this logic would live in one gigantic class. With them, each level delegates to its own class, mirroring the URL hierarchy as a class hierarchy.

The pattern trades a small amount of upfront complexity (you need two classes) for a large long-term payoff in maintainability, which is exactly the trade-off mature codebases make.

---

### Part 5.2 — 422 vs 404 for Missing References

**Question:** Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?

**Answer:**

The two status codes answer different questions.

**404 Not Found** means "the resource *identified by this URL* does not exist." It's about the request target. When a client requests `GET /rooms/GHOST-000`, the URL itself points to something absent, and 404 is exactly right.

**422 Unprocessable Entity** (RFC 4918) means "I understood your request syntax, I could parse your payload, but I can't process it because it's semantically wrong." The URL is fine, the JSON is well-formed, the fields have the right types — but a business rule fails.

In my case: `POST /sensors` with body `{"roomId": "GHOST-000", ...}`. The target URL `/sensors` exists. The body is valid JSON. The `roomId` field is a valid string. Nothing is technically "not found" at the URL level — `/sensors` is absolutely there. What fails is a referential integrity check buried inside the payload. Returning 404 in this situation is misleading because a naive client might think the `/sensors` endpoint itself is missing, retry at different URLs, or log an incorrect diagnosis.

422 communicates exactly the right thing: "your payload parsed fine but contains a reference to a room that doesn't exist." Combined with a JSON error body that names the missing resource (`missingResource.type: "Room"`, `missingResource.id: "GHOST-000"`), the client gets actionable feedback.

Some APIs use **400 Bad Request** for this case. 400 is acceptable but broader — it covers both syntactic failures (malformed JSON) and semantic ones (bad references). 422 draws the distinction more precisely, which is why modern APIs like GitHub and Stripe prefer it for semantic validation failures.

---

### Part 5.4 — Security Risks of Exposing Stack Traces

**Question:** From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?

**Answer:**

A Java stack trace is a dense dossier on the inner workings of an application. Exposing one to an unauthenticated client is the kind of small mistake that accelerates a breach, because it leaks information attackers otherwise have to pay for in time and probes.

Specific things an attacker can learn from a single stack trace:

1. **Framework and library versions.** Lines like `org.glassfish.jersey.server.ServerRuntime$2.run` reveal the JAX-RS implementation and — combined with behaviour — often its version. An attacker cross-references this against public CVE databases. If your Jersey is the version vulnerable to CVE-XXXX-YYYY, they now know which exploit to try.
2. **Application package structure.** `com.westminster.smartcampus.resources.SensorRoomResource.deleteRoom(SensorRoomResource.java:57)` tells the attacker the internal class hierarchy, the naming conventions, and the line number where the failure occurred. This is a free map of the codebase.
3. **Database and persistence details.** If the stack trace contains `org.hibernate.exception.ConstraintViolationException` or `java.sql.SQLSyntaxErrorException: Table 'users' doesn't exist`, the attacker learns the ORM, the DB engine, and sometimes the table names — all data the reconnaissance phase of an attack normally has to guess.
4. **Filesystem paths.** `at com.acme.Config.<init>(/opt/acme-prod/conf/Config.java)` leaks deployment paths, which can be combined with path traversal or local file inclusion attempts.
5. **Business logic hints.** Variable names and method names in the trace (`validateOwnership`, `isAdminOverride`) hint at security-sensitive code paths worth probing.
6. **Presence of debug/dev code in production.** Traces often reveal debug filters, mocked services, or internal health endpoints the attacker shouldn't know about.
7. **Fingerprinting for targeted attacks.** A trace is a unique fingerprint. Attackers can correlate it across known leaks ("this is the same Jersey 2.41 + Jackson 2.15 stack seen in X exploit").

The OWASP cheat sheet for error handling treats stack-trace leakage as an **Information Disclosure** vulnerability (CWE-209) for exactly these reasons. My `GlobalExceptionMapper` logs the full trace server-side (so developers can still diagnose production issues) but returns a sanitised generic message to the caller. That's the best-of-both-worlds approach: observability for operators, opacity for attackers.

---

### Part 5.5 — Why Filters Beat Inline Logging

**Question:** Why is it advantageous to use JAX-RS filters for cross-cutting concerns like logging, rather than manually inserting `Logger.info()` statements inside every single resource method?

**Answer:**

Logging is a textbook **cross-cutting concern** — it applies uniformly across many methods but isn't part of any of their core business logic. Manually scattering `LOGGER.info("request for ...")` inside every resource method is the hand-rolled alternative, and it's bad for several compounding reasons:

1. **DRY violation and copy-paste drift.** If you copy the same log line into 30 methods, you're guaranteed to end up with inconsistencies — someone logs the URI, someone else logs the method, someone forgets to log the status. A single filter produces identical, uniform output across every endpoint.
2. **Coupling business logic to infrastructure.** A resource method's job is to handle a sensor, not to decide how observability works. Mixing the two makes the resource harder to read and harder to test. Filters keep logging *outside* the method, leaving the resource focused on its domain.
3. **Complete coverage for free.** A filter runs for every request, including ones that never reach a resource method — for example, 404s from unmatched paths, or 415s from `@Consumes` mismatches, or exceptions thrown during deserialization. Inline logging misses all of these because the method is never entered.
4. **Separation of request vs response timing.** `ContainerResponseFilter` runs *after* the method completes, so it can log the final status code — something the resource method can't easily know, since it returns a `Response` object whose final status isn't necessarily fixed until post-processing completes.
5. **Centralized configuration.** Want to turn logging off in production? Change the log level in one place. Want to add request-id correlation? Do it in the filter and every endpoint automatically gets it. With inline logging, changes ripple across dozens of files.
6. **Composable cross-cutting concerns.** Filters chain. The same mechanism that powers logging also powers authentication, rate limiting, request tracing, CORS, and content negotiation. Learning the pattern once pays dividends across many concerns.

This is the same reasoning behind servlet filters, Spring interceptors, Express middleware, and aspect-oriented programming. Cross-cutting concerns belong in cross-cutting infrastructure, not in the code that does domain work.

---

## License

Submitted as coursework for the University of Westminster — 5COSC022W Client-Server Architectures (2025/26).
