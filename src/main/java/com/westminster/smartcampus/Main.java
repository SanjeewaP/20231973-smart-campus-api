package com.westminster.smartcampus;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;

/**
 * Entry point. Boots an embedded Grizzly HTTP server and registers the JAX-RS
 * application class defined in {@link SmartCampusApplication}.
 */
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final String BASE_URI = "http://localhost:8080/api/v1/";

    public static void main(String[] args) {
        ResourceConfig config = ResourceConfig.forApplication(new SmartCampusApplication());
        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(
                URI.create(BASE_URI),
                config
        );

        LOGGER.info("Smart Campus API started at " + BASE_URI);
        LOGGER.info("Press ENTER to stop the server.");

        // Also shut down cleanly if the JVM is killed (e.g. NetBeans Stop button).
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down Smart Campus API...");
            server.shutdownNow();
        }));

        // Block until the user hits Enter.
        try {
            System.in.read();
        } catch (IOException e) {
            LOGGER.warning("Input stream closed; shutting down.");
        }

        LOGGER.info("Stopping server...");
        server.shutdownNow();
    }
}
