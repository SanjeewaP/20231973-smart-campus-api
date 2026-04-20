# Video Demonstration Script — Smart Campus API

**Target length:** 9–10 minutes
**Setup:** server running in a terminal visible somewhere on screen, Postman maximised, webcam and mic on.

Read this naturally, not word-for-word. Slight pauses and rephrasing are fine, you're meant to sound like you, not a voiceover.

---

## 0. Intro (about 30 seconds)

> "Hi, I'm [your name], student ID 20231973. This is my demo for the 5COSC022W Client-Server Architectures coursework. It's a JAX-RS REST API for the Smart Campus Sensor and Room Management system. The server is already running locally on port 8080, so I'll just go through each Part of the spec in Postman and show everything working."

*(Briefly Alt-Tab to the server terminal so the grader can see the `Smart Campus API started at http://localhost:8080/api/v1/` line, then back to Postman.)*

---

## 1. Part 1 — Setup and Discovery (about 60 seconds)

**Click:** `Part 1 → 1.2 GET /`

> "Part 1 has two parts. One is the setup itself — the app has to boot under a versioned path using the `@ApplicationPath("/api/v1")` annotation. The other is the Discovery endpoint. If I hit `GET /api/v1` I get back a 200 with the API name, version, my contact info, and this `resources` map."

*(Point at the `resources` field.)*

> "This map is my HATEOAS implementation. Rather than documenting the URLs somewhere else, the server just tells clients where to find rooms, sensors, the filtered sensor view, and the readings sub-resource. So a client can discover the whole API from this one response."

---

## 2. Part 2 — Room Management (about 90 seconds)

**Click:** `Part 2 → 2.1a GET /rooms`

> "Moving on to rooms. `GET /rooms` gives me the two seeded rooms — LIB-301 and CS-101 — each with their sensor IDs."

**Click:** `Part 2 → 2.1b POST /rooms`

> "I'll create a new one. POST `/rooms` with this JSON body and I get 201 Created back. Location header points at the new room."

**Click:** `Part 2 → 2.1c GET /rooms/ENG-204`

> "And I can fetch it back by ID."

**Click:** `Part 2 → 2.2a DELETE /rooms/ENG-204`

> "Now deletion. ENG-204 has no sensors so DELETE comes back with 204 No Content."

**Click:** `Part 2 → 2.2b DELETE again`

> "The spec asks about idempotency. If I send the exact same DELETE a second time I get 404, because the room is already gone. The status code is different from the first call, but the end state on the server is the same in both cases — the room isn't there. That's what idempotency actually means in HTTP: same end state, not same response code."

---

## 3. Part 3 — Sensors and Filtering (about 90 seconds)

**Click:** `Part 3 → 3.1a GET /sensors`

> "Part 3 is sensors. Listing them I can see three seeded ones — a Temperature, a CO2, and an Occupancy sensor that's currently in MAINTENANCE. I'll come back to that one in a minute."

**Click:** `Part 3 → 3.1b POST /sensors (valid)`

> "Registering a new sensor. TEMP-002, pointing at room LIB-301. Before accepting it, my handler checks the parent room exists, which it does, so I get 201 Created."

**Click:** `Part 3 → 3.1c POST with text/plain`

> "And this is the Content-Type question from the spec. My POST method is annotated `@Consumes(APPLICATION_JSON)`, so if I send text/plain instead, Jersey rejects it with 415 before my code even runs. That's the framework doing the validation at the protocol boundary, so I don't have to check content types manually inside the handler."

**Click:** `Part 3 → 3.2 GET /sensors?type=CO2`

> "And filtering with `@QueryParam`. If I add `?type=CO2` only the CO2 sensor comes back. If I drop the parameter I get the whole list again."

---

## 4. Part 4 — Sub-Resources and Readings (about 90 seconds)

**Click:** `Part 4 → 4.2a GET readings`

> "Part 4 is the sub-resource locator pattern. I have a separate class, `SensorReadingResource`, that handles the readings. It's not wired up by a `@Path` annotation — instead there's a locator method on `SensorResource` with no HTTP-verb annotation. That's what tells JAX-RS to treat it as a router, not an endpoint, so it just passes the rest of the URL path to the sub-resource. Getting the readings for TEMP-001 gives me an empty list to start."

**Click:** `Part 4 → 4.2b POST reading`

> "Posting a reading with value 23.7. Server-generated UUID and timestamp. 201 Created."

**Click:** `Part 4 → 4.2c GET sensor`

> "And the side effect the spec asks for — posting a reading has to update the parent sensor's `currentValue`. Re-fetching TEMP-001, its `currentValue` is now 23.7. So the latest reading flows up automatically."

---

## 5. Part 5 — Error Handling and Logging (about 2 minutes)

> "Part 5 is error handling, which is the biggest section at 30 marks. I've got four exception mappers and a logging filter. Let me trigger each one."

**Click:** `Part 5 → 5.1 DELETE /rooms/LIB-301`

> "First one, 409 Conflict. LIB-301 still has two sensors attached, so my `RoomNotEmptyException` is thrown, the mapper catches it, and the response is a 409 with a JSON body saying which room and how many sensors. No stack trace."

**Click:** `Part 5 → 5.2 POST sensor bad roomId`

> "Second one, 422 Unprocessable Entity. I'm trying to register a sensor that references `GHOST-000` — a room that doesn't exist. This isn't a 404 situation because the URL `/sensors` is perfectly fine and the JSON parses cleanly. The problem is buried inside the payload, it's a reference to something missing. 422 communicates exactly that, and I return the missing type and ID in the body so the client knows what to fix."

**Click:** `Part 5 → 5.3 POST reading to OCC-001`

> "Third, 403 Forbidden. OCC-001 is the sensor in MAINTENANCE from earlier. If I try to POST a reading to it, my `SensorUnavailableException` fires and I get 403 back. The sensor is physically unavailable so it can't accept readings."

**Click:** `Part 5 → 5.4 GET /does-not-exist`

> "Fourth, the global safety net. Any unhandled path gets caught by my `ExceptionMapper<Throwable>` and returns a sanitised JSON body. No Java stack trace ever goes back to the client, because leaking a stack trace basically gives attackers a free map of your dependencies and internal code layout."

**Click:** `Part 5 → 5.5 GET /rooms` *(then Alt-Tab to the server terminal)*

> "And last one, the logging filter. Let me fire any request and then switch to the terminal..."

*(Show the logs:)*
```
INFO: ==> GET http://localhost:8080/api/v1/rooms
INFO: <== GET http://localhost:8080/api/v1/rooms -> 200
```

> "Every request logs an inbound line and an outbound line with the final status. The filter implements both `ContainerRequestFilter` and `ContainerResponseFilter` with `java.util.logging`, which means I get observability for every endpoint without putting log calls in any resource method."

---

## 6. Outro (about 20 seconds)

> "That covers the whole spec — Parts 1 through 5. The source is public on GitHub, link is in the Blackboard submission, and all the written answers are in the README. Thanks for watching."

---

## Quick checklist before hitting record

- [ ] Server running, terminal visible somewhere on screen
- [ ] Postman collection imported, `baseUrl` variable set to `http://localhost:8080/api/v1`
- [ ] Webcam and mic both on and tested (spec says both are mandatory)
- [ ] Screen recorder set to capture the full display including the terminal
- [ ] Don't show the IDE or code — the spec explicitly says no need to show coding
- [ ] If you fumble a line, just keep going. Trim in post. Re-recording from scratch wastes more time than it saves.

## If something breaks mid-record

- Seeded data got mutated from an earlier dry run → Ctrl+C the server and restart with `java -jar target/smart-campus-api.jar`. The DataStore re-seeds on startup.
- Port 8080 is busy → `lsof -ti:8080 | xargs kill -9` and restart.
