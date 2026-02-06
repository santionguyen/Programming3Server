package com.o3.server;
import org.json.JSONObject;

public class User {
    private String username;
    private String password;
    private String email;

    //Building
    public User(String username, String password, String email){

        this.username = username;
        this.password = password;
        this.email = email;
    }
    //Getter (needed to read the private data later)
    public String getUsername() {
        return username;
    }
    public String getPassword(){
        return password;
    }
    public String getEmail(){
        return email;
    }
}
