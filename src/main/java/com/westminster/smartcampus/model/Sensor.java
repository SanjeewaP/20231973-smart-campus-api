package com.westminster.smartcampus.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A hardware sensor deployed in a room (e.g., CO2, temperature, occupancy).
 * Each sensor maintains a list of historical readings.
 */
public class Sensor {

    private String id;                  // e.g., "TEMP-001"
    private String type;                // "Temperature", "CO2", "Occupancy", etc.
    private String status;              // "ACTIVE" | "MAINTENANCE" | "OFFLINE"
    private double currentValue;
    private String roomId;              // FK to Room

    // Historical readings are stored on the sensor itself; the sub-resource
    // in Part 4 reads from and writes to this list.
    private List<SensorReading> readings = new ArrayList<>();

    public Sensor() {
        // Default constructor needed for JSON deserialization
    }

    public Sensor(String id, String type, String status, double currentValue, String roomId) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.currentValue = currentValue;
        this.roomId = roomId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getCurrentValue() { return currentValue; }
    public void setCurrentValue(double currentValue) { this.currentValue = currentValue; }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public List<SensorReading> getReadings() { return readings; }
    public void setReadings(List<SensorReading> readings) { this.readings = readings; }
}
