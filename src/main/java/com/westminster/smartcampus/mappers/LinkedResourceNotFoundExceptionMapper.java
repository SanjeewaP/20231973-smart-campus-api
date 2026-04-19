package com.westminster.smartcampus.mappers;

import com.westminster.smartcampus.exceptions.LinkedResourceNotFoundException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Part 5.2 — Maps {@link LinkedResourceNotFoundException} to HTTP 422
 * Unprocessable Entity.
 *
 * 422 is used instead of 404 because the request URL itself is valid — the
 * problem is that the JSON body references another resource that doesn't
 * exist. RFC 4918 defines 422 specifically for "semantically erroneous"
 * payloads.
 */
@Provider
public class LinkedResourceNotFoundExceptionMapper
        implements ExceptionMapper<LinkedResourceNotFoundException> {

    private static final int UNPROCESSABLE_ENTITY = 422;

    @Override
    public Response toResponse(LinkedResourceNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", UNPROCESSABLE_ENTITY);
        body.put("error", "Unprocessable Entity");
        body.put("message", ex.getMessage());
        body.put("missingResource", Map.of(
                "type", ex.getResourceType(),
                "id", ex.getResourceId()
        ));

        return Response.status(UNPROCESSABLE_ENTITY)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
