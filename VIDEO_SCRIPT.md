# Video Demonstration Script — Smart Campus API

**Target length:** 9–10 minutes
**Setup:** Server running in a terminal visible in one corner of the screen; Postman maximized; webcam/mic on.

---

## 0. Intro — 30 seconds

> "Hi, I'm [Your Name], student ID 20231973. This is my demo for the 5COSC022W Client-Server Architectures coursework — a JAX-RS RESTful API for the Smart Campus Sensor & Room Management system. The server is already running locally on port 8080, and I'll use Postman to walk through every requirement in the coursework spec, part by part."

*(Briefly alt-tab to the server terminal so the grader sees the `Smart Campus API started at http://localhost:8080/api/v1/` log line, then back to Postman.)*

---

## 1. Part 1 — Setup & Discovery (60 seconds)

**Click:** `Part 1 → 1.2 GET /`

> "Part 1 has two requirements. First, the app must boot under a versioned entry point using the `@ApplicationPath("/api/v1")` annotation — and second, there needs to be a Discovery endpoint at that root. I hit `GET /api/v1` and get a 200 OK with a JSON body showing the API name, version, contact details, and a `resources` map."

*(Point at the `resources` field in the response.)*

> "This `resources` map is my HATEOAS implementation — rather than documenting URLs statically, the server tells clients where to find rooms, sensors, filtered sensor queries, and the sensor readings sub-resource. A client can discover the whole API from this single endpoint."

---

## 2. Part 2 — Room Management (90 seconds)

**Click:** `Part 2 → 2.1a GET /rooms`

> "Part 2 is Room Management. I start with `GET /rooms` — the response lists the two seeded rooms: LIB-301 and CS-101, each with their sensor IDs."

**Click:** `Part 2 → 2.1b POST /rooms`

> "Now I create a new room, ENG-204, with a POST and a JSON body. The response is 201 Created — the correct HTTP status for resource creation."

**Click:** `Part 2 → 2.1c GET /rooms/ENG-204`

> "And I can fetch the new room by its ID."

**Click:** `Part 2 → 2.2a DELETE /rooms/ENG-204`

> "Part 2.2 is deletion. ENG-204 has no sensors, so DELETE succeeds with 204 No Content."

**Click:** `Part 2 → 2.2b DELETE again`

> "The spec asks about idempotency. Watch what happens when I send the same DELETE a second time — I get 404 Not Found. The status code differs, but the *end state* on the server is identical: the room is still gone. That's the RFC definition of idempotent."

---

## 3. Part 3 — Sensors & Filtering (90 seconds)

**Click:** `Part 3 → 3.1a GET /sensors`

> "Part 3 is sensors. `GET /sensors` returns all three seeded sensors — one Temperature, one CO2, and one Occupancy sensor that's in MAINTENANCE state. I'll come back to that one."

**Click:** `Part 3 → 3.1b POST /sensors (valid)`

> "I register a new sensor TEMP-002 referencing room LIB-301. My POST handler validates that the parent room exists before accepting the sensor. Response is 201 Created."

**Click:** `Part 3 → 3.1c POST with text/plain`

> "The spec asks what happens if a client sends the wrong Content-Type. My method uses `@Consumes(APPLICATION_JSON)`, so when I send `text/plain`, Jersey short-circuits dispatch before my code even runs — the client gets a 415 Unsupported Media Type, which is the framework doing protocol-level validation for us."

**Click:** `Part 3 → 3.2 GET /sensors?type=CO2`

> "And Part 3.2 is filtering via `@QueryParam`. I append `?type=CO2` to the URL and only the CO2 sensor comes back."

---

## 4. Part 4 — Sub-Resources & Readings (90 seconds)

**Click:** `Part 4 → 4.2a GET readings`

> "Part 4 is the Sub-Resource Locator pattern. I designed `SensorReadingResource` as a separate class, instantiated by a locator method in `SensorResource` — that method has no HTTP-verb annotation, which is what tells JAX-RS to treat it as a router rather than an endpoint. `GET /sensors/TEMP-001/readings` returns an empty history to start."

**Click:** `Part 4 → 4.2b POST reading`

> "Now I POST a new reading with value 23.7 — 201 Created, with a server-generated UUID and timestamp."

**Click:** `Part 4 → 4.2c GET sensor`

> "The spec also asks for a side effect: posting a reading must update the parent sensor's `currentValue`. I re-fetch TEMP-001, and its `currentValue` is now 23.7 — the latest reading flows up to the parent."

---

## 5. Part 5 — Error Handling & Logging (2 minutes)

> "Part 5 is error handling, worth 30 marks. I've built four Exception Mappers and a logging filter. Let me trigger each one."

**Click:** `Part 5 → 5.1 DELETE /rooms/LIB-301`

> "First, 409 Conflict. LIB-301 still has two sensors attached, so my `RoomNotEmptyException` is thrown, mapped to 409, and the JSON body names the offending room and the sensor count — no stack trace leaked."

**Click:** `Part 5 → 5.2 POST sensor bad roomId`

> "Second, 422 Unprocessable Entity. I try to register a sensor with `roomId: GHOST-000`, which doesn't exist. This isn't a 404 — the URL `/sensors` is perfectly valid and the JSON parses cleanly. The problem is semantic: a reference inside the payload points at something missing. 422 communicates that precisely, and my response body includes a `missingResource` object naming what's absent."

**Click:** `Part 5 → 5.3 POST reading to OCC-001`

> "Third, 403 Forbidden. OCC-001 is in MAINTENANCE, so my `SensorUnavailableException` is thrown and mapped to 403 — the sensor is physically unavailable and can't accept readings."

**Click:** `Part 5 → 5.4 GET /does-not-exist`

> "Fourth, the global safety net. Any unmatched or unhandled path goes through my `ExceptionMapper<Throwable>`, which returns a sanitized JSON body — never a raw Java stack trace. Leaking a stack trace would expose library versions, package paths, and line numbers an attacker could use for reconnaissance, which is why this mapper exists."

**Click:** `Part 5 → 5.5 GET /rooms` *(then Alt-Tab to the server terminal)*

> "And finally Part 5.5 — the logging filter. I fire any request, then switch to the server terminal..."

*(Show the log output:)*
```
INFO: ==> GET http://localhost:8080/api/v1/rooms
INFO: <== GET http://localhost:8080/api/v1/rooms -> 200
```

> "Every request logs an inbound `==>` line and an outbound `<==` line with the status code. The filter implements both `ContainerRequestFilter` and `ContainerResponseFilter` using `java.util.logging.Logger` — which means I didn't have to touch a single resource method to get this observability. That's the power of JAX-RS filters for cross-cutting concerns."

---

## 6. Outro — 20 seconds

> "That covers every requirement in the spec — Parts 1 through 5. The full source is on my public GitHub repo at the link in the Blackboard submission, and the conceptual answers to each spec question are in the README. Thanks for watching."

---

## Checklist before you hit record

- [ ] Server is running and visible in a terminal on screen
- [ ] Postman collection imported, `baseUrl = http://localhost:8080/api/v1`
- [ ] Webcam and microphone both tested (spec demands both)
- [ ] Screen recording tool set to capture the full display including the terminal
- [ ] Do **not** show any coding / IDE — the spec explicitly says "no need to show the coding part"
- [ ] If you make a mistake, keep going — you can trim in post. Re-recording from scratch wastes time.

## If a request fails during recording

- **Seeded data got mutated from a previous run:** stop the server (Ctrl+C), restart `java -jar target/smart-campus-api.jar` — the DataStore re-seeds.
- **Port 8080 busy:** `lsof -ti:8080 | xargs kill -9` then restart.
