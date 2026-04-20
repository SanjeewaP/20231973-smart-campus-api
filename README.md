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
git clone https://github.com/SanjeewaP/20231973-smart-campus-api.git
cd 20231973-smart-campus-api

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

By default JAX-RS creates a new instance of a resource class for every incoming request. So if I stored rooms and sensors directly as fields on `SensorRoomResource`, that data would be thrown away the moment the request finished. A client could POST a room, get back a 201, and the next GET would come back empty because it'd be handled by a fresh instance of the same class.

To get around this I put all the shared state in a singleton `DataStore` (private static final instance). Every resource grabs the same reference through `DataStore.getInstance()`, so the data survives across requests.

The catch with using a singleton is thread safety. Grizzly serves requests from a thread pool, so multiple resource instances can be hitting the store at the same time from different threads. A plain `HashMap` would eventually corrupt under concurrent writes — lost updates, or in older JDKs, infinite loops. So I used `ConcurrentHashMap` for both the rooms and sensors maps. That gives me safe concurrent reads and writes without locking the whole map on every call.

It's not completely airtight. If two requests both mutate the same room's sensor list at the same moment I could still end up with a race, because "get the room, add an id, put it back" is a compound operation. For this coursework that's fine, but in a real system I'd probably wrap those compound operations in `compute` lambdas or use explicit per-room locks.

---

### Part 1.2 — HATEOAS and Hypermedia

**Question:** Why is the provision of "Hypermedia" (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?

**Answer:**

HATEOAS stands for Hypermedia As The Engine Of Application State, and the idea is basically that the server should tell the client where to go next, rather than the client knowing every URL up front. It's the same idea as following links in a web browser — you don't pre-load a sitemap, you just follow whatever anchor tags appear on the page.

For client developers this is a big deal compared to relying on static documentation. If I ever rename `/sensors` to `/devices` or bump to v2, a client that reads the URLs out of my discovery response keeps working. A client built from a doc PDF breaks and has to be redeployed. It also makes the API easier to explore — a new developer can just hit the root and follow links from there instead of reading a spec cover to cover.

The other nice thing is that the server can include only the links that are currently valid. A room response could omit its `delete` link while it still has sensors attached, which means the server is effectively telling the client what it's allowed to do next instead of making the client guess and get back a 409. And because the server is the source of truth for its own URLs, there's no chance of documentation rot where the docs say one thing and the code does another.

In my discovery endpoint at `GET /api/v1` I expose a `resources` map with the paths to rooms, sensors, the filtered sensor view, and the readings sub-resource. It's a lightweight take on HATEOAS but it gets the point across.

---

### Part 2.1 — ID-only vs Full Object Responses

**Question:** When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client side processing.

**Answer:**

This is a trade-off between payload size and number of round trips.

If I return full room objects (which is what I went with for `GET /rooms`), the client gets everything it needs in one request and can render a list view immediately. No follow-up calls, no extra code. The downside is that the response grows with both the number of rooms and the number of fields per room, so if the campus had thousands of rooms the payload would start to get heavy, and it's wasteful if the client only cares about room names.

If I returned only IDs, the payload per room would be tiny and constant. But then every row in the list view would need its own `GET /rooms/{id}` to actually show anything useful. That's the classic N+1 problem — one list call plus N detail calls — and on mobile or a slow network it's painful. The client also becomes responsible for stitching all those responses back together.

For a campus-scale API with a few dozen rooms, returning full objects is fine and that's what I did. For a much bigger system the usual way out is pagination with something like `?page=1&size=20`, combined with optional field selection (`?fields=id,name`) for cases where the client only wants a subset. That keeps the one-round-trip ergonomics without letting the responses blow up.

---

### Part 2.2 — DELETE Idempotency

**Question:** Is the DELETE operation idempotent in your implementation? Provide a detailed justification by describing what happens if a client mistakenly sends the exact same DELETE request for a room multiple times.

**Answer:**

Yes, my DELETE is idempotent. Idempotency is about whether the server ends up in the same state after one call versus many, not whether the response code is the same every time. That's the RFC 9110 definition.

Walking through it: if I delete `ENG-204` and the room has no sensors, the handler removes it from the store and returns 204. If the client sends the same DELETE again, the room is already gone, so I return 404. Third call, fourth, hundredth — same 404, room still absent. The response code changes between the first and subsequent calls but the actual state on the server is identical from the second call onwards.

The case where the room has sensors works the same way. First DELETE throws `RoomNotEmptyException` and returns 409, room stays. Second DELETE throws the same exception, returns 409, room still there. The end state never changes no matter how many times the client retries — the operation just never succeeds.

Some APIs choose to return 204 every time regardless of whether the resource actually existed, on the grounds that it makes client error handling simpler. I went with 404 because it gives the client a useful signal (that the resource wasn't there) without breaking the idempotency property. Both conventions are defensible — it's a style call.

---

### Part 3.1 — @Consumes and Content-Type Mismatch

**Question:** We explicitly use the `@Consumes(MediaType.APPLICATION_JSON)` annotation on the POST method. Explain the technical consequences if a client attempts to send data in a different format, such as `text/plain` or `application/xml`. How does JAX-RS handle this mismatch?

**Answer:**

`@Consumes(MediaType.APPLICATION_JSON)` is really a routing hint for Jersey. It tells the framework "this method only accepts requests whose Content-Type is application/json." When a request comes in, Jersey looks at the URL and the HTTP verb to find candidate methods, and then filters those candidates by matching their `@Consumes` against the Content-Type header on the request.

If none of the candidates match, Jersey shortcircuits the whole dispatch and returns 415 Unsupported Media Type on its own. My method body never runs. The practical win is that I don't have to write defensive checks like `if (contentType.equals("application/json"))` inside the handler. The framework handles that at the protocol boundary before my code is even reached.

There's a separate failure mode worth flagging though. If the header says `application/json` but the body is actually garbage — say `not-json-at-all` — Jackson fails to deserialize it and Jersey returns 400 Bad Request instead. So the two errors are cleanly separated: wrong Content-Type is a 415 (framework level, before dispatch), malformed body is a 400 (parser level, still before my handler runs). Between them you get a clear contract for clients without any boilerplate inside the resource methods.

---

### Part 3.2 — Query Parameter vs Path for Filtering

**Question:** You implemented this filtering using `@QueryParam`. Contrast this with an alternative design where the type is part of the URL path (e.g., `/api/v1/sensors/type/CO2`). Why is the query parameter approach generally considered superior for filtering and searching collections?

**Answer:**

Both designs work but the query-parameter version fits REST better.

The way I think about it: a URL should name a resource. `/sensors` is the collection of all sensors, full stop. Filtering doesn't change what resource I'm asking about, it just changes which representation I want back. That's exactly what query strings are for.

Practically, `@QueryParam` also composes. `GET /sensors?type=CO2&status=ACTIVE&roomId=LIB-301` reads naturally and scales. Doing the same thing with path segments would give you something like `/sensors/type/CO2/status/ACTIVE/room/LIB-301`, which is awkward and has an obvious combinatorial explosion problem — every new filter doubles the number of routes you'd have to think about.

Query params are also optional by nature. `/sensors` and `/sensors?type=CO2` are clearly the same collection viewed two ways. With a path-based design, `/sensors` and `/sensors/type/CO2` start looking like two different resources, which they really aren't. There's also a convention argument — pretty much every major REST API out there (GitHub, Stripe, and so on) filters with query params, so developers recognise the pattern immediately.

Path parameters belong in a URL when the value is part of the resource's identity — `/rooms/LIB-301` names one specific room. They don't belong there when the value is just a filter on a collection.

---

### Part 4.1 — Sub-Resource Locator Pattern

**Question:** Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path in one massive controller class?

**Answer:**

The sub-resource locator pattern is JAX-RS's way of letting you split a nested URL structure across multiple classes. A sensor "has" readings, so `/sensors/{id}/readings` is naturally handled by its own `SensorReadingResource` instead of being stuffed into `SensorResource` alongside the sensor CRUD methods.

Here's how it works in the code. My `SensorResource.getReadingsSubResource(sensorId)` method doesn't have a `@GET` or `@POST` annotation — that's the signal to JAX-RS that it's a locator, not an endpoint. Instead of returning a response, it returns an instance of another resource class, and JAX-RS continues dispatching against that instance using the rest of the URL path.

The main benefit is keeping each class focused on one thing. `SensorResource` only knows about sensors, `SensorReadingResource` only knows about readings, and neither has to care about the other beyond a constructor argument. Both classes stay small and readable. I'm also passing the parent `Sensor` directly into the sub-resource constructor, so the sub-resource doesn't have to redo the lookup or repeat the "find it or 404" boilerplate on every call — it's already scoped to its parent.

It also gives you one natural place to enforce parent-level rules. Every reading operation has to go through the locator, so if I later added authorization ("you can only read your own sensor's history") that check would live in one method and automatically cover everything downstream.

Scaling this up is where the pattern really earns its keep. If the campus ever needed `/sensors/{id}/readings/{rid}/annotations/{aid}`, each level could keep its own class rather than one giant controller handling every depth of nesting. The class hierarchy ends up mirroring the URL hierarchy, which is usually what you want in an API this structured.

---

### Part 5.2 — 422 vs 404 for Missing References

**Question:** Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?

**Answer:**

404 and 422 answer different questions.

404 means "the resource you asked for doesn't exist" — it's about the URL. If a client hits `GET /rooms/GHOST-000`, the URL itself is pointing at nothing, and 404 is the right answer.

422 means "I understood your request and the JSON parsed fine, but I can't process the content because something about it is semantically wrong." It's about the payload, not the URL.

My case is the second one. The client POSTs to `/sensors` with a body like `{"roomId": "GHOST-000", ...}`. The URL `/sensors` is absolutely there. The JSON is valid. Every field has the right type. The only thing wrong is that the `roomId` points at a room that doesn't exist. Returning 404 here would be misleading — a naive client might assume the `/sensors` endpoint itself is gone and start retrying different URLs, or log the wrong kind of error.

422 says exactly what's wrong: the payload looks fine syntactically but fails a semantic check. I also return the missing resource's type and ID in the error body (`missingResource.type: "Room"`, `missingResource.id: "GHOST-000"`) so the client knows what to fix.

Some APIs use 400 Bad Request for this and that's also fine — 400 is just broader, since it covers both malformed JSON and bad references. 422 is more specific, which is why it tends to show up in well-designed APIs for exactly this situation.

---

### Part 5.4 — Security Risks of Exposing Stack Traces

**Question:** From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?

**Answer:**

A Java stack trace is basically a free intelligence report about your application. Leaking one to unauthenticated clients is a small mistake with disproportionate consequences because it hands attackers information they'd otherwise have to spend real time probing for.

A few concrete things you can learn from a single trace:

- Library versions. Lines like `org.glassfish.jersey.server.ServerRuntime$2.run` reveal the JAX-RS implementation, and combined with behaviour that usually narrows the exact version down. Cross-reference that against public CVE databases and you've got a ready-made list of known exploits to try.
- Application structure. `com.westminster.smartcampus.resources.SensorRoomResource.deleteRoom(SensorRoomResource.java:57)` tells you the package layout, the class names, the method names, and the exact line number of the failure. That's a free map of the codebase.
- Persistence layer. If the trace includes `org.hibernate.ConstraintViolationException` or something like `SQLSyntaxErrorException: Table 'users' doesn't exist`, you know the ORM, the database engine, and sometimes even table names.
- Filesystem paths. Something like `at com.acme.Config.<init>(/opt/acme-prod/conf/Config.java)` leaks deployment paths, which plays into path-traversal or local-file-inclusion attempts.
- Fingerprinting. A particular stack is a unique signature — attackers can correlate it against other known leaks running the same stack.

OWASP classifies this as Information Disclosure (CWE-209) for these reasons. My `GlobalExceptionMapper` still logs the full trace server-side so I can debug problems in production, but it returns a sanitised, generic message to the caller. Operators keep the observability they need, attackers get nothing useful.

---

### Part 5.5 — Why Filters Beat Inline Logging

**Question:** Why is it advantageous to use JAX-RS filters for cross-cutting concerns like logging, rather than manually inserting `Logger.info()` statements inside every single resource method?

**Answer:**

Logging is the textbook example of a cross-cutting concern — it applies to every endpoint but isn't really part of any single endpoint's actual job. The alternative to using a filter is scattering `LOGGER.info(...)` calls manually inside every method, and that's a bad idea for a few reasons.

First, it breaks DRY. If I copied the same log line into thirty methods I'd inevitably end up with thirty slightly different versions — someone logs the URI, someone else logs the method, someone forgets the status code. A single filter produces identical, consistent output for every request.

Second, it mixes infrastructure into business logic. A resource method's job is to handle a sensor, not to decide how logging works. Pulling logging out into a filter keeps those concerns separate and the resource code stays focused on the thing it actually does.

The bigger practical win is that filters get total coverage for free. A filter runs for every single request, including ones that never reach a resource method at all — 404s for unmatched paths, 415s from `@Consumes` rejections, errors thrown during deserialization before the method runs. Inline logging misses all of those because the method is never entered.

There's also a timing thing worth mentioning. `ContainerResponseFilter` runs after the response is finalised, so it can log the actual status code that went out. A resource method returning a `Response` object doesn't necessarily know the final status yet — another filter or an exception mapper might still modify it.

And finally, changing the logging behaviour across the whole API is one line in the filter. With inline logging you'd be editing dozens of methods. The same mechanism also powers authentication, rate limiting, and request tracing, so it's a pattern worth learning once and reusing everywhere.

---

## License

Submitted as coursework for the University of Westminster — 5COSC022W Client-Server Architectures (2025/26).
