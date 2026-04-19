package com.westminster.smartcampus.model;

/**
 * A single measurement recorded by a sensor at a point in time.
 */
public class SensorReading {

    private String id;          // UUID
    private long timestamp;     // Epoch millis
    private double value;

    public SensorReading() {
        // Default constructor needed for JSON deserialization
    }

    public SensorReading(String id, long timestamp, double value) {
        this.id = id;
        this.timestamp = timestamp;
        this.value = value;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public double getValue() { return value; }
    public void setValue(double value) { this.value = value; }
}
