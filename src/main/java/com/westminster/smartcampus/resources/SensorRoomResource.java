package com.westminster.smartcampus.resources;

import com.westminster.smartcampus.data.DataStore;
import com.westminster.smartcampus.exceptions.RoomNotEmptyException;
import com.westminster.smartcampus.model.Room;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Collection;

/**
 * Part 2 — Room Management.
 *
 * Path: /api/v1/rooms
 */
@Path("/rooms")
public class SensorRoomResource {

    private final DataStore store = DataStore.getInstance();

    @Context
    private UriInfo uriInfo;

    /** GET /rooms — list every room. */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listRooms() {
        Collection<Room> all = store.getRooms().values();
        return Response.ok(all).build();
    }

    /** GET /rooms/{roomId} — fetch one room by id. */
    @GET
    @Path("/{roomId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRoom(@PathParam("roomId") String roomId) {
        Room room = store.getRooms().get(roomId);
        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(errorBody("Room not found: " + roomId))
                    .build();
        }
        return Response.ok(room).build();
    }

    /**
     * POST /rooms — create a new room. Returns 201 Created with a Location
     * header pointing at the new resource — the RESTful convention.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createRoom(Room room) {
        if (room == null || room.getId() == null || room.getId().isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(errorBody("Room id is required."))
                    .build();
        }
        if (store.getRooms().containsKey(room.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(errorBody("Room already exists: " + room.getId()))
                    .build();
        }
        store.getRooms().put(room.getId(), room);

        URI location = uriInfo.getAbsolutePathBuilder().path(room.getId()).build();
        return Response.created(location).entity(room).build();
    }

    /**
     * DELETE /rooms/{roomId}.
     *
     * Part 2.2 business rule: a room with active sensors cannot be deleted —
     * we throw {@link RoomNotEmptyException} which is mapped to 409 Conflict
     * by RoomNotEmptyExceptionMapper (Part 5.1).
     */
    @DELETE
    @Path("/{roomId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        Room room = store.getRooms().get(roomId);
        if (room == null) {
            // Idempotency: deleting a non-existent room is still "successful"
            // from the client's perspective — see the Part 2.2 report answer.
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(errorBody("Room not found: " + roomId))
                    .build();
        }
        if (!room.getSensorIds().isEmpty()) {
            throw new RoomNotEmptyException(roomId, room.getSensorIds().size());
        }
        store.getRooms().remove(roomId);
        return Response.noContent().build();
    }

    private static java.util.Map<String, String> errorBody(String message) {
        return java.util.Map.of("error", message);
    }
}
