package com.o3.server;

// Data model representing a registered user
// Maps directly to the users data table in the database.

public class User {
    private String username;
    private String password;
    private String email;
    private String nickname;

    public User(String username, String password, String email, String nickname){
        this.username = username;
        this.password = password;
        this.email = email;
        this.nickname = nickname;
    }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getEmail() { return email; }
    public String getNickname() { return nickname; }
}