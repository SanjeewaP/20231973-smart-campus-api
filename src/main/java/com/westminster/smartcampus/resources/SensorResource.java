package com.westminster.smartcampus.resources;

import com.westminster.smartcampus.data.DataStore;
import com.westminster.smartcampus.exceptions.LinkedResourceNotFoundException;
import com.westminster.smartcampus.model.Room;
import com.westminster.smartcampus.model.Sensor;

import javax.ws.rs.*;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Part 3 — Sensor Operations.
 * Part 4 — Sub-resource locator for /{sensorId}/readings.
 *
 * Path: /api/v1/sensors
 */
@Path("/sensors")
public class SensorResource {

    private final DataStore store = DataStore.getInstance();

    @Context
    private UriInfo uriInfo;

    /**
     * GET /sensors?type={type}
     *
     * Part 3.2 — The @QueryParam turns ?type=CO2 into an optional filter.
     * If the type parameter is absent the full list is returned.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listSensors(@QueryParam("type") String type) {
        List<Sensor> result;
        if (type == null || type.isBlank()) {
            result = store.getSensors().values().stream().collect(Collectors.toList());
        } else {
            result = store.getSensors().values().stream()
                    .filter(s -> type.equalsIgnoreCase(s.getType()))
                    .collect(Collectors.toList());
        }
        return Response.ok(result).build();
    }

    /** GET /sensors/{sensorId} — fetch a single sensor. */
    @GET
    @Path("/{sensorId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSensor(@PathParam("sensorId") String sensorId) {
        Sensor sensor = store.getSensors().get(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(java.util.Map.of("error", "Sensor not found: " + sensorId))
                    .build();
        }
        return Response.ok(sensor).build();
    }

    /**
     * POST /sensors — register a new sensor.
     *
     * Part 3.1 rule: the referenced roomId must exist. If it doesn't, we throw
     * {@link LinkedResourceNotFoundException} which is mapped to 422.
     *
     * The @Consumes annotation enforces that clients must send
     * application/json; a mismatch is automatically rejected by JAX-RS
     * with 415 Unsupported Media Type.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createSensor(Sensor sensor) {
        if (sensor == null || sensor.getId() == null || sensor.getId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("error", "Sensor id is required."))
                    .build();
        }
        if (store.getSensors().containsKey(sensor.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(java.util.Map.of("error", "Sensor already exists: " + sensor.getId()))
                    .build();
        }

        // Part 3.1 — validate that the referenced room exists
        Room parentRoom = store.getRooms().get(sensor.getRoomId());
        if (parentRoom == null) {
            throw new LinkedResourceNotFoundException(
                    "Room", sensor.getRoomId(),
                    "Cannot register sensor because the referenced room does not exist.");
        }

        store.getSensors().put(sensor.getId(), sensor);
        parentRoom.getSensorIds().add(sensor.getId());

        URI location = uriInfo.getAbsolutePathBuilder().path(sensor.getId()).build();
        return Response.created(location).entity(sensor).build();
    }

    /**
     * Part 4.1 — Sub-Resource Locator.
     *
     * Note: NO HTTP method annotation (@GET/@POST) on this method — that's
     * what makes it a locator. It returns an instance of another resource
     * class, and JAX-RS continues dispatching against THAT class using the
     * remainder of the URL path. This keeps each class small and focused.
     */
    @Path("/{sensorId}/readings")
    public SensorReadingResource getReadingsSubResource(@PathParam("sensorId") String sensorId) {
        Sensor sensor = store.getSensors().get(sensorId);
        if (sensor == null) {
            throw new NotFoundException("Sensor not found: " + sensorId);
        }
        return new SensorReadingResource(sensor);
    }
}
