package com.o3.server;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
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
            
            // Expanded with update_reason and edited for Feature 7
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
                         "observatory TEXT, " + 
                         "update_reason TEXT, " +
                         "edited TEXT" +
                         ")";
            statement.execute(sql);

            String sqlUsers = "CREATE TABLE IF NOT EXISTS users (" +
                              "username TEXT PRIMARY KEY, " +
                              "password TEXT, " +
                              "email TEXT, " +
                              "nickname TEXT)";
            statement.execute(sqlUsers);

            // Feature 6 Tables
            String sqlCollections = "CREATE TABLE IF NOT EXISTS collections (id INTEGER PRIMARY KEY AUTOINCREMENT)";
            statement.execute(sqlCollections);

            String sqlCollectionMessages = "CREATE TABLE IF NOT EXISTS collection_messages (" +
                                           "collection_id INTEGER, " +
                                           "message_id TEXT)";
            statement.execute(sqlCollectionMessages);

            // FEATURE 7 FIX: Safely add columns if the old table already exists
            try { statement.execute("ALTER TABLE messages ADD COLUMN update_reason TEXT"); } catch (Exception e) {}
            try { statement.execute("ALTER TABLE messages ADD COLUMN edited TEXT"); } catch (Exception e) {}

            statement.close();
        } catch(SQLException e){
            System.out.println("Create table error: " + e.getMessage());
        }
    }
    
    public synchronized void addMessage(ObservationRecord record){
        String sql = "INSERT INTO messages (id, record_time_received, record_owner, " + 
                     "target_body_name, center_body_name, epoch, orbital_elements, state_vector, record_payload, observatory, update_reason, edited) " + 
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try{
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, record.getId());
            pstmt.setLong(2, record.getRecordTimeReceived());
            pstmt.setString(3, record.getRecordOwner());
            pstmt.setString(4, record.getTargetBodyName());
            pstmt.setString(5, record.getCenterBodyName());
            pstmt.setString(6, record.getEpoch());
            pstmt.setString(7, record.getOrbitalElements() != null ? record.getOrbitalElements().toString() : null);
            pstmt.setString(8, record.getStateVector() != null ? record.getStateVector().toString() : null);
            pstmt.setString(9, record.getRecordPayload()); 
            pstmt.setString(10, record.getObservatory() != null ? record.getObservatory().toString() : null);
            pstmt.setString(11, record.getUpdateReason());
            pstmt.setString(12, record.getEdited());

            pstmt.executeUpdate();
            pstmt.close();
        }catch(SQLException e){
            System.out.println("Add message error: " + e.getMessage());
        }
    }
    
    // FEATURE 7: Update an existing message
    public synchronized void updateMessage(ObservationRecord record) {
        String sql = "UPDATE messages SET target_body_name=?, center_body_name=?, epoch=?, orbital_elements=?, state_vector=?, record_payload=?, observatory=?, update_reason=?, edited=? WHERE id=?";
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, record.getTargetBodyName());
            pstmt.setString(2, record.getCenterBodyName());
            pstmt.setString(3, record.getEpoch());
            pstmt.setString(4, record.getOrbitalElements() != null ? record.getOrbitalElements().toString() : null);
            pstmt.setString(5, record.getStateVector() != null ? record.getStateVector().toString() : null);
            pstmt.setString(6, record.getRecordPayload());
            pstmt.setString(7, record.getObservatory() != null ? record.getObservatory().toString() : null);
            pstmt.setString(8, record.getUpdateReason());
            pstmt.setString(9, record.getEdited());
            pstmt.setString(10, record.getId());
            
            pstmt.executeUpdate();
            pstmt.close();
        } catch(SQLException e) {
            System.out.println("Update message error: " + e.getMessage());
        }
    }

    // FEATURE 7: Fetch a single message by its ID
    public synchronized ObservationRecord getMessageById(String id) {
        String sql = "SELECT * FROM messages WHERE id = ?";
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
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
                record.setUpdateReason(rs.getString("update_reason"));
                record.setEdited(rs.getString("edited"));
                
                String obsStr = rs.getString("observatory");
                if (obsStr != null) {
                    record.setObservatory(new JSONArray(obsStr));
                }
                
                rs.close();
                pstmt.close();
                return record;
            }
        } catch (Exception e) {
            System.out.println("Get message error: " + e.getMessage());
        }
        return null;
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
                record.setUpdateReason(rs.getString("update_reason"));
                record.setEdited(rs.getString("edited"));
                
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

    public synchronized int createCollection(JSONArray messageIds) {
        String sql = "INSERT INTO collections (id) VALUES (null)";
        try {
            Statement stmt = connection.createStatement();
            stmt.executeUpdate(sql);
            ResultSet rs = stmt.executeQuery("SELECT last_insert_rowid()");
            if (rs.next()) {
                int collectionId = rs.getInt(1);
                if (messageIds != null && messageIds.length() > 0) {
                    addMessagesToCollection(collectionId, messageIds);
                }
                return collectionId;
            }
        } catch (SQLException e) { 
            System.out.println("Create collection error: " + e.getMessage());
        }
        return -1;
    }

    public synchronized boolean addMessagesToCollection(int collectionId, JSONArray messageIds) {
        String sql = "INSERT INTO collection_messages (collection_id, message_id) VALUES (?, ?)";
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            for (int i = 0; i < messageIds.length(); i++) {
                pstmt.setInt(1, collectionId);
                pstmt.setString(2, messageIds.get(i).toString()); 
                pstmt.executeUpdate();
            }
            pstmt.close();
            return true;
        } catch (Exception e) { 
            System.out.println("Add to collection error: " + e.getMessage());
            return false; 
        }
    }

    public synchronized List<Integer> getCollectionIds() {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT id FROM collections";
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                ids.add(rs.getInt("id"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return ids;
    }

    public synchronized boolean collectionExists(int id) {
        try {
            PreparedStatement pstmt = connection.prepareStatement("SELECT 1 FROM collections WHERE id = ?");
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) { return false; }
    }

    public synchronized List<ObservationRecord> getMessagesInCollection(int collectionId) {
        List<ObservationRecord> loadedMessages = new ArrayList<>();
        String sql = "SELECT m.* FROM messages m JOIN collection_messages cm ON m.id = cm.message_id WHERE cm.collection_id = ?";
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setInt(1, collectionId);
            ResultSet rs = pstmt.executeQuery();
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
                record.setUpdateReason(rs.getString("update_reason"));
                record.setEdited(rs.getString("edited"));
                
                String obsStr = rs.getString("observatory");
                if (obsStr != null) {
                    record.setObservatory(new JSONArray(obsStr));
                }

                loadedMessages.add(record);
            }
            rs.close(); pstmt.close();
        } catch (Exception e) {
            System.out.println("Read collection error: " + e.getMessage());
        }
        return loadedMessages;
    }
}