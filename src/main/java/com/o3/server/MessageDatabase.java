package com.o3.server;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public class MessageDatabase {
    private Connection connection;

    public synchronized void open(String dbName){
        try{
            String url = "jdbc:sqlite:" + dbName;
            this.connection = DriverManager.getConnection(url);
        }catch(SQLException e){
            System.out.println("Connection failed " + e.getMessage());
        }
    }
    
    public synchronized void createTable(){
        try{
            Statement statement = connection.createStatement();
            // Added 'observatory' column
            String sql = "CREATE TABLE IF NOT EXISTS messages (" +
                         "id TEXT PRIMARY KEY, " +
                         "record_time_received INTEGER, " + 
                         "record_owner TEXT, " + 
                         "target_body_name TEXT, " +
                         "center_body_name TEXT, " +
                         "epoch TEXT, " +
                         "orbital_elements TEXT, " +
                         "state_vector TEXT, " +
                         "record_payload TEXT, " + 
                         "observatory TEXT" + 
                         ")";
            statement.execute(sql);

            String sqlUsers = "CREATE TABLE IF NOT EXISTS users (" +
                              "username TEXT PRIMARY KEY, " +
                              "password TEXT, " +
                              "email TEXT, " +
                              "nickname TEXT)";
            statement.execute(sqlUsers);
            statement.close();
        } catch(SQLException e){
            System.out.println("Create table error: " + e.getMessage());
        }
    }
    
    public synchronized void addMessage(ObservationRecord record){
        String sql = "INSERT INTO messages (id, record_time_received, record_owner, " + 
                     "target_body_name, center_body_name, epoch, orbital_elements, state_vector, record_payload, observatory) " + 
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try{
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, record.getId());
            pstmt.setLong(2, record.getRecordTimeReceived());
            pstmt.setString(3, record.getRecordOwner());
            pstmt.setString(4, record.getTargetBodyName());
            pstmt.setString(5, record.getCenterBodyName());
            pstmt.setString(6, record.getEpoch());
            
            if (record.getOrbitalElements() != null) pstmt.setString(7, record.getOrbitalElements().toString());
            else pstmt.setString(7, null);

            if (record.getStateVector() != null) pstmt.setString(8, record.getStateVector().toString());
            else pstmt.setString(8, null);

            pstmt.setString(9, record.getRecordPayload()); 
            
            // Save observatory
            if (record.getObservatory() != null) pstmt.setString(10, record.getObservatory().toString());
            else pstmt.setString(10, null);

            pstmt.executeUpdate();
            pstmt.close();
        }catch(SQLException e){
            System.out.println("Add message error: " + e.getMessage());
        }
    }
    
    public synchronized List<ObservationRecord> readMessages() {
        List<ObservationRecord> loadedMessages = new ArrayList<>();
        String sql = "SELECT * FROM messages";
        try {
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(sql);
            while (rs.next()) {
                JSONObject json = new JSONObject();
                json.put("target_body_name", rs.getString("target_body_name"));
                json.put("center_body_name", rs.getString("center_body_name"));
                json.put("epoch", rs.getString("epoch"));

                String orbitalStr = rs.getString("orbital_elements");
                if (orbitalStr != null) json.put("orbital_elements", new JSONObject(orbitalStr));
                
                String vectorStr = rs.getString("state_vector");
                if (vectorStr != null) json.put("state_vector", new JSONObject(vectorStr));

                ObservationRecord record = new ObservationRecord(json);
                record.setId(rs.getString("id"));
                record.setRecordTimeReceived(rs.getLong("record_time_received"));
                record.setRecordOwner(rs.getString("record_owner"));
                record.setRecordPayload(rs.getString("record_payload"));
                
                // Read observatory data back
                String obsStr = rs.getString("observatory");
                if (obsStr != null) {
                    record.setObservatory(new org.json.JSONArray(obsStr));
                }

                loadedMessages.add(record);
            }
            rs.close(); statement.close();
        } catch (Exception e) {
            System.out.println("Read error: " + e.getMessage());
        }
        return loadedMessages;
    }

    public synchronized String getUserNickname(String username) {
        String sql = "SELECT nickname FROM users WHERE username = ?";
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getString("nickname");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public synchronized boolean registerUser(User user) {
        String sql = "INSERT INTO users(username, password, email, nickname) VALUES(?,?,?,?)";
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPassword());
            pstmt.setString(3, user.getEmail());
            pstmt.setString(4, user.getNickname());
            pstmt.executeUpdate();
            pstmt.close();
            return true;
        } catch (SQLException e) { return false; }
    }

    public synchronized boolean validateUser(String username, String password) {
        String sql = "SELECT password FROM users WHERE username = ?";
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String dbPassword = rs.getString("password");
                return dbPassword.equals(password);
            }
            return false;
        } catch (SQLException e) { return false; }
    }
}