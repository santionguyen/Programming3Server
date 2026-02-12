package com.o3.server;

import com.sun.net.httpserver.BasicAuthenticator;

public class UserAuthenticator extends BasicAuthenticator {
    
    private MessageDatabase db;

    public UserAuthenticator(String realm, MessageDatabase db) {
        super(realm);
        this.db = db;
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        return db.validateUser(username, password);
    }

    public boolean addUser(User newUser) {
        return db.setUser(newUser);
    }
}