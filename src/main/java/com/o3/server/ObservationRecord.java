package com.o3.server;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.ZoneOffset; 

public class ObservationRecord {
    private String targetBodyName;
    private String centerBodyName;
    private String epoch;

    private String id;
    private ZonedDateTime record_time_received;
    private String record_owner;

    private JSONObject orbitalElements;
    private JSONObject stateVector; 

    // Constructor
    public ObservationRecord(JSONObject json) throws JSONException {
        // Validate Strings 
        this.targetBodyName = json.getString("target_body_name");
        this.centerBodyName = json.getString("center_body_name");
        this.epoch = json.getString("epoch");


        // Validate Orbital Elements
        if (json.has("orbital_elements")) {
            JSONObject orbital = json.getJSONObject("orbital_elements");
            
            // We call getDouble() to force validation. 
            // If the test sends "ribbonry" or "null", this throws an exception.
            orbital.getDouble("semi_major_axis_au");
            orbital.getDouble("eccentricity");
            orbital.getDouble("inclination_deg");
            orbital.getDouble("longitude_ascending_node_deg");
            orbital.getDouble("argument_of_periapsis_deg");
            orbital.getDouble("mean_anomaly_deg");
            
            this.orbitalElements = orbital;
        }

        // Validate State Vector
        if (json.has("state_vector")) {
            JSONObject vector = json.getJSONObject("state_vector");
            
            // Validate that these keys exist and are valid JSON Arrays
            JSONArray pos = vector.getJSONArray("position_au");
            JSONArray vel = vector.getJSONArray("velocity_au_per_day");
            
            // Validate that the arrays actually contain numbers
            if (pos.length() != 3 || vel.length() != 3) {
                 throw new JSONException("State vectors must have 3 components");
            }
            // Try reading the values to ensure they are numbers, not strings
            for(int i=0; i<3; i++) {
                pos.getDouble(i);
                vel.getDouble(i);
            }

            this.stateVector = vector;
        }
        
        // Ensure at least one data object exists
        if (this.orbitalElements == null && this.stateVector == null) {
            throw new JSONException("Message must contain either orbital_elements or state_vector");
        }
    }

    public boolean isValid() {
        // Since we validated everything in the constructor, 
        // if the object was created successfully, it is valid.
        return true;
    }

    // Setters for server-generated fields
    public void setId(String id) {
        this.id = id;
    }

    public void setRecordTimeReceived(long time) {
    // Converts the database number (long) back to a ZonedDateTime object
    this.record_time_received = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(time), ZoneOffset.UTC);
}

    public void setRecordOwner(String owner) {
        this.record_owner = owner;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("target_body_name", this.targetBodyName);
        json.put("center_body_name", this.centerBodyName);
        json.put("epoch", this.epoch);
        json.put("id", this.id);

        if (this.record_time_received != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
            json.put("record_time_received", this.record_time_received.format(formatter));
        }
        json.put("record_owner", this.record_owner);
        
        if (this.orbitalElements != null) {
            json.put("orbital_elements", this.orbitalElements);
        }
        if (this.stateVector != null) {
            json.put("state_vector", this.stateVector);
        }
        return json;
    }

    public String getId(){
        return this.id;
    }
    public String getRecordOwner(){
        return this.record_owner;
    }
    public long getRecordTimeReceived(){
        return this.record_time_received.toInstant().toEpochMilli();
    }
    public String getTargetBodyName(){
        return this.targetBodyName;
    }
    public String getCenterBodyName(){
        return this.centerBodyName;
    }
    public String getEpoch(){
        return this.epoch;
    }
    public JSONObject getOrbitalElements() {
        return this.orbitalElements;
    }

    public JSONObject getStateVector() {
        return this.stateVector;
    }



}