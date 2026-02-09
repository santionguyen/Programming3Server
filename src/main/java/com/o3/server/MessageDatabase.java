package com.o3.server;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

public class MessageDatabase {
    private Connection connection;

    public void open(String dbName){
        try{
            //build full connection string
            //1.jdbc sqlite + filename
            String url = "jdbc:sqlite:" + dbName;
            // make the connection
            this.connection = DriverManager.getConnection(url);
            System.out.println("Connected to database");


        }catch(SQLException e){
            System.out.println("Connection failed " + e.getMessage());

        }
    }
    public void createTable(){
        try{
            Statement statement = connection.createStatement();

            String sql = "CREATE TABLE IF NOT EXISTS messages (" +
                         "id TEXT PRIMARY KEY, " +
                         "record_time_received INTEGER, " + 
                         "record_owner TEXT, " + 
                         "target_body_name TEXT, " +
                         "center_body_name TEXT, " +
                         "epoch TEXT, " +
                         "orbital_elements TEXT, " +
                         "state_vector TEXT" +
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
    public void addMessage(ObservationRecord record){
        String sql = "INSERT INTO messages (id, record_time_received, record_owner, " + 
                     "target_body_name, center_body_name, epoch, orbital_elements, state_vector) " + 
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try{
            PreparedStatement pstmt = connection.prepareStatement(sql);
            //1. id
            pstmt.setString(1, record.getId());
            //2. recort_time_received
            pstmt.setLong(2, record.getRecordTimeReceived());

            //3. record_owner
            pstmt.setString(3, record.getRecordOwner());
            //4. target
            //5. center
            //6. epoch
            pstmt.setString(4, record.getTargetBodyName());
            pstmt.setString(5, record.getCenterBodyName());
            pstmt.setString(6, record.getEpoch());

            //7. orbital
            if (record.getOrbitalElements() != null) {
                pstmt.setString(7, record.getOrbitalElements().toString());
            } else {
                pstmt.setString(7, null); // Store NULL in database if missing
            }

            //8. state_vector
            if (record.getStateVector() != null) {
                pstmt.setString(8, record.getStateVector().toString());
            } else {
                pstmt.setString(8, null);
            }
            pstmt.executeUpdate();
            pstmt.close();

        }catch(SQLException e){
            System.out.println("Add message error: " + e.getMessage());

        }
    }
    // method to load all the messages from database to a list
    public List<ObservationRecord> readMessages() {
        List<ObservationRecord> loadedMessages = new ArrayList<>();
        String sql = "SELECT * FROM messages";

        try {
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(sql);

            while (rs.next()) {
                // 1. Reconstruct the JSON object from the DB columns
                JSONObject json = new JSONObject();
                json.put("target_body_name", rs.getString("target_body_name"));
                json.put("center_body_name", rs.getString("center_body_name"));
                json.put("epoch", rs.getString("epoch"));

                // We stored these as Strings, so we must parse them back to JSONObjects
                String orbitalStr = rs.getString("orbital_elements");
                if (orbitalStr != null) {
                    json.put("orbital_elements", new JSONObject(orbitalStr));
                }
                
                String vectorStr = rs.getString("state_vector");
                if (vectorStr != null) {
                    json.put("state_vector", new JSONObject(vectorStr));
                }

                // 2. Create the Java object using the constructor
                ObservationRecord record = new ObservationRecord(json);

                // 3. Fill in the "Server-Side" fields (ID, Time, Owner)
                record.setId(rs.getString("id"));
                record.setRecordTimeReceived(rs.getLong("record_time_received"));
                record.setRecordOwner(rs.getString("record_owner"));

                // 4. Add to the list
                loadedMessages.add(record);
            }
            
            rs.close();
            statement.close();
            System.out.println("Restored " + loadedMessages.size() + " messages from database.");

        } catch (Exception e) {
            System.out.println("Read error: " + e.getMessage());
        }
        
        return loadedMessages;
    }

    //Method to save new users
    public boolean registerUser(User user) {
        // We use INSERT to save the 4 user fields
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
        } catch (SQLException e) {
            // This happens if the username is already taken
            return false; 
        }
    }

    //Method to check login requirements
    public boolean validateUser(String username, String password) {
        String sql = "SELECT password FROM users WHERE username = ?";
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                // User found! Check if password matches
                String dbPassword = rs.getString("password");
                return dbPassword.equals(password);
            }
            // User not found
            return false;
            
        } catch (SQLException e) {
            return false;
        }
    }

}
