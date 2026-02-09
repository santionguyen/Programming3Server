package com.o3.server;

import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ObservationRecord {
    
    // Data Fields
    private String targetBodyName;
    private String centerBodyName;
    private String epoch;
    private JSONObject orbitalElements;
    private JSONObject stateVector;
    
    // Metadata Fields
    private String id;
    private long recordTimeReceived;
    private String recordOwner;
    private String recordPayload; // updated

    // Constructor (Parses the JSON)
    public ObservationRecord(JSONObject json) {
        this.targetBodyName = json.optString("target_body_name", null);
        this.centerBodyName = json.optString("center_body_name", null);
        this.epoch = json.optString("epoch", null);
        
        if (json.has("orbital_elements")) {
            this.orbitalElements = json.getJSONObject("orbital_elements");
        }
        if (json.has("state_vector")) {
            this.stateVector = json.getJSONObject("state_vector");
        }
        
        // --- NEW: Parse Metadata to get the payload ---
        if (json.has("metadata")) {
            JSONObject metadata = json.getJSONObject("metadata");
            if (metadata.has("record_payload")) {
                this.recordPayload = metadata.getString("record_payload");
            }
        }
    }

    // Convert Object back to JSON 
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("target_body_name", this.targetBodyName);
        json.put("center_body_name", this.centerBodyName);
        json.put("epoch", this.epoch);
        
        if (this.orbitalElements != null) {
            json.put("orbital_elements", this.orbitalElements);
        }
        if (this.stateVector != null) {
            json.put("state_vector", this.stateVector);
        }

        // Create the Metadata Box ---
        JSONObject metadata = new JSONObject();
        
        // 1. ID
        metadata.put("id", this.id);
        
        // 2. Owner
        metadata.put("record_owner", this.recordOwner);
        
        // 3. Time (Formatted as ISO String)
        if (this.recordTimeReceived > 0) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
                .withZone(ZoneId.of("UTC"));
            String dateStr = formatter.format(Instant.ofEpochMilli(this.recordTimeReceived));
            metadata.put("record_time_received", dateStr);
        }
        
        // 4. Payload (If it exists)
        if (this.recordPayload != null) {
            metadata.put("record_payload", this.recordPayload);
        }

        // Add the box to the main JSON
        json.put("metadata", metadata);
        // ------------------------------------

        return json;
    }

    // Getters and Setters
    public void setId(String id) { this.id = id; }
    public String getId() { return id; }

    public void setRecordTimeReceived(long time) { this.recordTimeReceived = time; }
    public long getRecordTimeReceived() { return recordTimeReceived; }

    public void setRecordOwner(String owner) { this.recordOwner = owner; }
    public String getRecordOwner() { return recordOwner; }
    
    // Validation Logic
    public boolean isValid() {
        // Must have at least basic names and epoch
        if (targetBodyName == null || centerBodyName == null || epoch == null) {
            return false;
        }
        // Must have EITHER orbital_elements OR state_vector
        if (orbitalElements == null && stateVector == null) {
            return false;
        }
        // If we have elements, they must be valid numbers (simplified check)
        if (orbitalElements != null && orbitalElements.length() == 0) return false;
        
        return true;
    }

    // Getters for DB access
    public String getTargetBodyName() { return targetBodyName; }
    public String getCenterBodyName() { return centerBodyName; }
    public String getEpoch() { return epoch; }
    public JSONObject getOrbitalElements() { return orbitalElements; }
    public JSONObject getStateVector() { return stateVector; }
}