package com.westminster.smartcampus.resources;

import com.westminster.smartcampus.exceptions.SensorUnavailableException;
import com.westminster.smartcampus.model.Sensor;
import com.westminster.smartcampus.model.SensorReading;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/**
 * Part 4.2 — Historical readings sub-resource.
 *
 * This class is instantiated by the sub-resource locator in
 * {@link SensorResource#getReadingsSubResource(String)}. Because the parent
 * sensor is passed into the constructor, the sub-resource doesn't need to
 * repeat the lookup — it just operates on the sensor it was handed.
 *
 * IMPORTANT: This class is NOT annotated with @Path — its path is already
 * established by the locator. It IS annotated with @GET / @POST for its
 * own methods.
 */
public class SensorReadingResource {

    private final Sensor parentSensor;

    public SensorReadingResource(Sensor parentSensor) {
        this.parentSensor = parentSensor;
    }

    /** GET /sensors/{sensorId}/readings — full history for this sensor. */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listReadings() {
        List<SensorReading> readings = parentSensor.getReadings();
        return Response.ok(readings).build();
    }

    /**
     * POST /sensors/{sensorId}/readings — append a new reading.
     *
     * Part 5.3 rule: if the parent sensor is in MAINTENANCE, reject with 403.
     *
     * Side effect (Part 4.2): update currentValue on the parent sensor so the
     * sensor's /sensors/{id} endpoint always reflects the latest reading.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addReading(SensorReading reading) {
        if ("MAINTENANCE".equalsIgnoreCase(parentSensor.getStatus())) {
            throw new SensorUnavailableException(parentSensor.getId(), parentSensor.getStatus());
        }
        if ("OFFLINE".equalsIgnoreCase(parentSensor.getStatus())) {
            throw new SensorUnavailableException(parentSensor.getId(), parentSensor.getStatus());
        }

        if (reading == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(java.util.Map.of("error", "Reading body is required."))
                    .build();
        }

        // Server-assigned id and timestamp if the client omitted them.
        if (reading.getId() == null || reading.getId().isBlank()) {
            reading.setId(UUID.randomUUID().toString());
        }
        if (reading.getTimestamp() == 0L) {
            reading.setTimestamp(System.currentTimeMillis());
        }

        parentSensor.getReadings().add(reading);

        // Side effect: update the sensor's current value
        parentSensor.setCurrentValue(reading.getValue());

        return Response.status(Response.Status.CREATED).entity(reading).build();
    }
}
