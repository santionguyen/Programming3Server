package com.o3.server;
import org.json.JSONObject;

public class User {
    private String username;
    private String password;
    private String email;
    private String nickname;

    //Building
    public User(String username, String password, String email, String nickname){

        this.username = username;
        this.password = password;
        this.email = email;
        this.nickname = nickname;
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

    public String getNickname(){
        return nickname;
    }

}
