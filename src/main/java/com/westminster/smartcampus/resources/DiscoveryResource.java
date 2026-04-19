package com.westminster.smartcampus.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Part 1.2 — The Discovery Endpoint.
 *
 * Root endpoint at GET /api/v1/. Returns API metadata plus a map of links to
 * the primary resource collections. This is the HATEOAS hallmark: clients can
 * navigate the API without hard-coding every URL.
 */
@Path("/")
public class DiscoveryResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getApiRoot() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "Smart Campus API");
        body.put("version", "1.0.0");
        body.put("description",
                "RESTful API for managing rooms, sensors, and readings across the university Smart Campus.");

        Map<String, String> contact = new LinkedHashMap<>();
        contact.put("maintainer", "Smart Campus Backend Team");
        contact.put("email", "smartcampus-admin@westminster.ac.uk");
        body.put("contact", contact);

        Map<String, String> resources = new LinkedHashMap<>();
        resources.put("self", "/api/v1");
        resources.put("rooms", "/api/v1/rooms");
        resources.put("sensors", "/api/v1/sensors");
        resources.put("sensorsByType", "/api/v1/sensors?type={type}");
        resources.put("sensorReadings", "/api/v1/sensors/{sensorId}/readings");
        body.put("resources", resources);

        return Response.ok(body).build();
    }
}
