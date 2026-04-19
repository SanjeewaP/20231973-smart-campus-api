package com.westminster.smartcampus;

import com.westminster.smartcampus.exceptions.*;
import com.westminster.smartcampus.filters.LoggingFilter;
import com.westminster.smartcampus.mappers.*;
import com.westminster.smartcampus.resources.DiscoveryResource;
import com.westminster.smartcampus.resources.SensorResource;
import com.westminster.smartcampus.resources.SensorRoomResource;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

/**
 * JAX-RS Application. The @ApplicationPath annotation defines the base URI
 * prefix for every resource in this API: everything lives under /api/v1.
 */
@ApplicationPath("/api/v1")
public class SmartCampusApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();

        // Resource classes
        classes.add(DiscoveryResource.class);
        classes.add(SensorRoomResource.class);
        classes.add(SensorResource.class);

        // Exception mappers (Part 5)
        classes.add(RoomNotEmptyExceptionMapper.class);
        classes.add(LinkedResourceNotFoundExceptionMapper.class);
        classes.add(SensorUnavailableExceptionMapper.class);
        classes.add(GlobalExceptionMapper.class);

        // Request/Response logging filter (Part 5)
        classes.add(LoggingFilter.class);

        return classes;
    }
}
