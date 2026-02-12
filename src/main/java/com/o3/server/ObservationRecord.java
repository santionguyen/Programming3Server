package com.o3.server;

import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ObservationRecord {
    
    // Scientific Data (Astronomy)
    private String targetBodyName;
    private String centerBodyName;
    private String epoch;
    private JSONObject orbitalElements;
    private JSONObject stateVector;
    
    // Metadata
    private String id;
    private long recordTimeReceived;
    private String recordOwner;
    
    // Observatories (Stored as raw strings for simplicity)
    private String observatory; 
    private String observatoryWeather; 

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
        
        // Handle Observatory Arrays
        if (json.has("observatory")) {
            this.observatory = json.getJSONArray("observatory").toString();
        }
        
        // Handle Persistence Metadata
        if (json.has("metadata")) {
            JSONObject metadata = json.getJSONObject("metadata");
            this.id = metadata.optString("id", null);
            this.recordOwner = metadata.optString("record_owner", null);
        }
    }

    // --- STRICT VALIDATION ---
    public boolean isValid() {
        if (targetBodyName == null || targetBodyName.isEmpty()) return false;
        if (centerBodyName == null || centerBodyName.isEmpty()) return false;
        if (epoch == null || epoch.isEmpty()) return false;

        // Check Orbital Elements are Numbers
        if (orbitalElements != null) {
            for (String key : orbitalElements.keySet()) {
                Object val = orbitalElements.get(key);
                if (!(val instanceof Number)) return false; 
            }
        }

        // Check State Vector components are Numbers
        if (stateVector != null) {
            for (String key : stateVector.keySet()) {
                Object val = stateVector.get(key);
                if (val instanceof JSONObject) {
                    JSONObject sub = (JSONObject) val;
                    for (String subKey : sub.keySet()) {
                         if (!(sub.get(subKey) instanceof Number)) return false;
                    }
                }
            }
        }

        if (orbitalElements == null && stateVector == null) return false;
        return true;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("target_body_name", this.targetBodyName);
        json.put("center_body_name", this.centerBodyName);
        json.put("epoch", this.epoch);
        if (this.orbitalElements != null) json.put("orbital_elements", this.orbitalElements);
        if (this.stateVector != null) json.put("state_vector", this.stateVector);
        
        JSONObject metadata = new JSONObject();
        metadata.put("id", this.id);
        metadata.put("record_owner", this.recordOwner);
        if (this.recordTimeReceived > 0) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
                .withZone(ZoneId.of("UTC"));
            metadata.put("record_time_received", formatter.format(Instant.ofEpochMilli(this.recordTimeReceived)));
        }
        json.put("metadata", metadata);

        if (this.observatory != null) {
             try { json.put("observatory", new org.json.JSONArray(this.observatory)); } catch (Exception e) {}
        }
        if (this.observatoryWeather != null) {
             try { json.put("observatoryWeather", new org.json.JSONArray(this.observatoryWeather)); } catch (Exception e) {}
        }

        return json;
    }

    // Getters & Setters
    public void setId(String id) { this.id = id; }
    public String getId() { return id; }
    public void setRecordTimeReceived(long t) { this.recordTimeReceived = t; }
    public long getRecordTimeReceived() { return recordTimeReceived; }
    public void setRecordOwner(String o) { this.recordOwner = o; }
    public String getRecordOwner() { return recordOwner; }
    public void setObservatoryWeather(String w) { this.observatoryWeather = w; }
    public String getObservatoryWeather() { return observatoryWeather; }
    public String getObservatory() { return observatory; }

    public String getTargetBodyName() { return targetBodyName; }
    public String getCenterBodyName() { return centerBodyName; }
    public String getEpoch() { return epoch; }
    public JSONObject getOrbitalElements() { return orbitalElements; }
    public JSONObject getStateVector() { return stateVector; }
}