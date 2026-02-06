package com.o3.server;
import org.json.JSONObject;

public class ObservationRecord {
    private String targetBodyName;
    private String centerBodyName;
    private String epoch;

    private JSONObject orbitalElements;
    private JSONObject stateVector; 

    //Constructor
    public ObservationRecord(JSONObject json){
        this.targetBodyName = json.getString("target_body_name");
        this.centerBodyName = json.getString("center_body_name");
        this.epoch = json.getString("epoch");

        if (json.has("orbital_elements")){
            this.orbitalElements = json.getJSONObject("orbital_elements");
        }
        if (json.has("state_vector")){
            this.stateVector = json.getJSONObject("state_vector");
        }
    }
    public boolean isValid(){
        return (orbitalElements != null || stateVector !=null);
    }
    public JSONObject toJSON(){
        JSONObject json = new JSONObject();
        json.put("target_body_name", this.targetBodyName);
        json.put("center_body_name", this.centerBodyName);
        json.put("epoch", this.epoch);
        if(this.orbitalElements != null){
            json.put("orbital_elements", this.orbitalElements);
        }
        if (this.stateVector != null){
            json.put("state_vector", this.stateVector);
        }
        return json;
    }
}
