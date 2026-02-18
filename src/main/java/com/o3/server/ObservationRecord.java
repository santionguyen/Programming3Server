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
    private String recordPayload;
    
    // WEEK 5: Observatory Array
    private JSONArray observatory; 

    private JSONObject orbitalElements;
    private JSONObject stateVector; 

    public ObservationRecord(JSONObject json) throws JSONException {
        // 1. Strict String Checks
        if (json.has("target_body_name") && !(json.get("target_body_name") instanceof String)) {
            throw new JSONException("target_body_name must be a string");
        }
        if (json.has("center_body_name") && !(json.get("center_body_name") instanceof String)) {
            throw new JSONException("center_body_name must be a string"); 
        }
        if (json.has("epoch") && !(json.get("epoch") instanceof String)) {
            throw new JSONException("epoch must be a string"); 
        }

        this.targetBodyName = json.optString("target_body_name", null);
        this.centerBodyName = json.optString("center_body_name", null);
        this.epoch = json.optString("epoch", null);
        this.recordPayload = json.optString("record_payload", null);

        // 2. Parse Orbital Elements
        if (json.has("orbital_elements")) {
            JSONObject orbital = json.getJSONObject("orbital_elements");
            orbital.getDouble("semi_major_axis_au");
            orbital.getDouble("eccentricity");
            orbital.getDouble("inclination_deg");
            orbital.getDouble("longitude_ascending_node_deg");
            orbital.getDouble("argument_of_periapsis_deg");
            orbital.getDouble("mean_anomaly_deg");
            this.orbitalElements = orbital;
        }

        // 3. Parse State Vector
        if (json.has("state_vector")) {
            JSONObject vector = json.getJSONObject("state_vector");
            JSONArray pos = vector.getJSONArray("position_au");
            JSONArray vel = vector.getJSONArray("velocity_au_per_day");
            if (pos.length() != 3 || vel.length() != 3) {
                 throw new JSONException("State vectors must have 3 components");
            }
            for(int i=0; i<3; i++) {
                pos.getDouble(i);
                vel.getDouble(i);
            }
            this.stateVector = vector;
        }
        
        // 4. Parse Metadata & Observatory
        if (json.has("metadata")) {
            JSONObject metadata = json.getJSONObject("metadata");
            this.id = metadata.optString("id");
            if (metadata.has("record_owner")) this.record_owner = metadata.getString("record_owner");
            if (metadata.has("record_payload")) this.recordPayload = metadata.getString("record_payload");
            
            // Capture observatory array
            if (metadata.has("observatory")) {
                this.observatory = metadata.getJSONArray("observatory");
            }
        }
    }

    // --- FIX the test value  ---
    public boolean isValid() {
        if (targetBodyName == null || targetBodyName.isEmpty()) return false;
        if (centerBodyName == null || centerBodyName.isEmpty()) return false;
        if (epoch == null || epoch.isEmpty()) return false;
        if (orbitalElements == null && stateVector == null) return false;

        // Check Observatory Data
        if (this.observatory != null) {
            for (int i = 0; i < this.observatory.length(); i++) {
                try {
                    JSONObject obs = this.observatory.getJSONObject(i);
                    // 1. Check keys exist
                    if (!obs.has("latitude") || !obs.has("longitude") || !obs.has("observatory name")) {
                        return false; 
                    }
                    // 2. Check types (Latitude/Longtitude must be numbers)
                    if (!(obs.get("latitude") instanceof Number)) return false;
                    if (!(obs.get("longitude") instanceof Number)) return false;
                    
                } catch (JSONException e) {
                    return false;
                }
            }
        }

        return true;
    }

    public void setId(String id) { this.id = id; }
    public void setRecordTimeReceived(long time) {
        this.record_time_received = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(time), ZoneOffset.UTC);
    }
    public void setRecordOwner(String owner) { this.record_owner = owner; }
    public void setRecordPayload(String payload) { this.recordPayload = payload; }
    public void setObservatory(JSONArray observatory) { this.observatory = observatory; }
    
    public String getId(){ return this.id; }
    public String getRecordOwner(){ return this.record_owner; }
    public long getRecordTimeReceived(){ return this.record_time_received.toInstant().toEpochMilli(); }
    public String getTargetBodyName(){ return this.targetBodyName; }
    public String getCenterBodyName(){ return this.centerBodyName; }
    public String getEpoch(){ return this.epoch; }
    public String getRecordPayload() { return this.recordPayload; } 
    public JSONArray getObservatory() { return this.observatory; }
    public JSONObject getOrbitalElements() { return this.orbitalElements; }
    public JSONObject getStateVector() { return this.stateVector; }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("target_body_name", this.targetBodyName);
        json.put("center_body_name", this.centerBodyName);
        json.put("epoch", this.epoch);
        
        JSONObject metadata = new JSONObject();
        metadata.put("id", this.id);
        metadata.put("record_owner", this.record_owner);
        if (this.record_time_received != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");
            metadata.put("record_time_received", this.record_time_received.format(formatter));
        }
        if (this.recordPayload != null) {
            metadata.put("record_payload", this.recordPayload);
        }
        // Include observatory in output
        if (this.observatory != null) {
            metadata.put("observatory", this.observatory);
        }
        json.put("metadata", metadata);
        
        if (this.orbitalElements != null) json.put("orbital_elements", this.orbitalElements);
        if (this.stateVector != null) json.put("state_vector", this.stateVector);
        return json;
    }
}