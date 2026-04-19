package com.westminster.smartcampus.exceptions;

/**
 * Part 5.2 — Thrown when the payload references a resource (e.g. a room)
 * that does not exist. Mapped to HTTP 422 Unprocessable Entity: the JSON
 * parsed correctly but a referenced resource is missing, which is a
 * semantic error rather than a syntax error.
 */
public class LinkedResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String resourceId;

    public LinkedResourceNotFoundException(String resourceType, String resourceId, String message) {
        super(message);
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
}
