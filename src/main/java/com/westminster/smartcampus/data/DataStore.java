package com.westminster.smartcampus.data;

import com.westminster.smartcampus.model.Room;
import com.westminster.smartcampus.model.Sensor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory data store — shared across all resource instances.
 *
 * The coursework forbids databases, so we keep state in ConcurrentHashMap
 * (thread-safe HashMap) singletons. This is directly relevant to the Part 1
 * question about JAX-RS resource lifecycle: because Jersey creates a new
 * resource instance per request by default, the store itself must be
 * a singleton accessible from every instance and must be thread-safe.
 */
public class DataStore {

    private static final DataStore INSTANCE = new DataStore();

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, Sensor> sensors = new ConcurrentHashMap<>();

    private DataStore() {
        seedSampleData();
    }

    public static DataStore getInstance() {
        return INSTANCE;
    }

    public Map<String, Room> getRooms() { return rooms; }
    public Map<String, Sensor> getSensors() { return sensors; }

    /**
     * Prepopulate a few rooms and sensors so the API is demoable out of the
     * box — useful for the Postman video demonstration.
     */
    private void seedSampleData() {
        Room lib301 = new Room("LIB-301", "Library Quiet Study", 40);
        Room cs101  = new Room("CS-101",  "Computer Science Lab", 30);
        rooms.put(lib301.getId(), lib301);
        rooms.put(cs101.getId(), cs101);

        Sensor temp001 = new Sensor("TEMP-001", "Temperature", "ACTIVE", 22.5, "LIB-301");
        Sensor co2001  = new Sensor("CO2-001",  "CO2",         "ACTIVE", 410.0, "LIB-301");
        Sensor occ001  = new Sensor("OCC-001",  "Occupancy",   "MAINTENANCE", 0.0, "CS-101");

        sensors.put(temp001.getId(), temp001);
        sensors.put(co2001.getId(),  co2001);
        sensors.put(occ001.getId(),  occ001);

        lib301.getSensorIds().add("TEMP-001");
        lib301.getSensorIds().add("CO2-001");
        cs101.getSensorIds().add("OCC-001");
    }
}
