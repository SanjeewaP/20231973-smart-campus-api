package com.westminster.smartcampus.mappers;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Part 5.4 — The global safety net.
 *
 * Intercepts ANY Throwable that no more specific mapper has handled and
 * returns a generic 500 Internal Server Error. Crucially, the response body
 * never contains a Java stack trace — that would leak class names, library
 * versions, file paths, and other information an attacker could use for
 * reconnaissance.
 *
 * The trace IS logged server-side so developers can still diagnose issues.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = Logger.getLogger(GlobalExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable ex) {
        // If it's already a JAX-RS WebApplicationException (e.g. NotFoundException
        // thrown from a sub-resource locator), honor the original status code.
        if (ex instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) ex;
            Response original = wae.getResponse();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", original.getStatus());
            body.put("error", Response.Status.fromStatusCode(original.getStatus()) != null
                    ? Response.Status.fromStatusCode(original.getStatus()).getReasonPhrase()
                    : "Error");
            body.put("message", ex.getMessage());

            return Response.status(original.getStatus())
                    .type(MediaType.APPLICATION_JSON)
                    .entity(body)
                    .build();
        }

        // Log the full stack trace server-side for debugging
        LOGGER.log(Level.SEVERE, "Unhandled exception reached global mapper", ex);

        // Return a sanitized response — no stack trace, no internal details
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 500);
        body.put("error", "Internal Server Error");
        body.put("message", "An unexpected error occurred. Please contact the Smart Campus administrator.");

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}
