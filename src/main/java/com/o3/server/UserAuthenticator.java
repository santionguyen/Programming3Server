package com.o3.server;
import com.sun.net.httpserver.BasicAuthenticator;

// Handle HTTP basic Authentication for protected endpoints
// Integrated with datasae to verify credentials against stored users.

public class UserAuthenticator extends BasicAuthenticator {
    private MessageDatabase db;

    public UserAuthenticator(String realm, MessageDatabase db) {
        super(realm);
        this.db = db;
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        // Validates credentials for every protected GET/POST/PUT request.
        return db.validateUser(username, password);
    }

    public boolean addUser(User newUser) {
        return db.registerUser(newUser);
    }
}