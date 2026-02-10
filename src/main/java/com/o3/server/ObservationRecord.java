package com.o3.server;

import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ObservationRecord {
    
    // Scientific Data
    private String targetBodyName;
    private String centerBodyName;
    private String epoch;
    private JSONObject orbitalElements;
    private JSONObject stateVector;
    
    // Metadata Fields
    private String id;
    private long recordTimeReceived;
    private String recordOwner;
    private String recordPayload; 

    // Constructor
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
        
        // Read Metadata if it exists
        if (json.has("metadata")) {
            JSONObject metadata = json.getJSONObject("metadata");
            this.id = metadata.optString("id", null);
            this.recordOwner = metadata.optString("record_owner", null);
            this.recordPayload = metadata.optString("record_payload", null);
        }
    }

    // Convert to JSON (Nested Structure)
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        
        // 1. Scientific Data
        json.put("target_body_name", this.targetBodyName);
        json.put("center_body_name", this.centerBodyName);
        json.put("epoch", this.epoch);
        
        if (this.orbitalElements != null) {
            json.put("orbital_elements", this.orbitalElements);
        }
        if (this.stateVector != null) {
            json.put("state_vector", this.stateVector);
        }

        // 2. Metadata Box
        JSONObject metadata = new JSONObject();
        metadata.put("id", this.id);
        metadata.put("record_owner", this.recordOwner);
        
        if (this.recordTimeReceived > 0) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
                .withZone(ZoneId.of("UTC"));
            String dateStr = formatter.format(Instant.ofEpochMilli(this.recordTimeReceived));
            metadata.put("record_time_received", dateStr);
        }
        
        if (this.recordPayload != null) {
            metadata.put("record_payload", this.recordPayload);
        }

        json.put("metadata", metadata); 
        return json;
    }

    // --- GETTERS & SETTERS ---

    public void setId(String id) { this.id = id; }
    public String getId() { return id; }

    public void setRecordTimeReceived(long time) { this.recordTimeReceived = time; }
    public long getRecordTimeReceived() { return recordTimeReceived; }

    public void setRecordOwner(String owner) { this.recordOwner = owner; }
    public String getRecordOwner() { return recordOwner; }
    
    public boolean isValid() {
        if (targetBodyName == null || centerBodyName == null || epoch == null) {
            return false;
        }
        return true;
    }

    // --- THESE WERE MISSING! ---
    public String getTargetBodyName() { return targetBodyName; }
    public String getCenterBodyName() { return centerBodyName; }
    public String getEpoch() { return epoch; }
    public JSONObject getOrbitalElements() { return orbitalElements; }
    public JSONObject getStateVector() { return stateVector; }


    public String getRecordPayload() { return recordPayload; }
    public void setRecordPayload(String payload) { this.recordPayload = payload; }
}