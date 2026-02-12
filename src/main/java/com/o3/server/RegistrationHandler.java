package com.o3.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.json.JSONObject;

public class RegistrationHandler implements HttpHandler {
    private UserAuthenticator auth;

    public RegistrationHandler(UserAuthenticator auth) {
        this.auth = auth;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        InputStream stream = exchange.getRequestBody();
        String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));
        stream.close();

        try {
            JSONObject json = new JSONObject(text);
            
            // Validate required fields
            if (!json.has("username") || !json.has("password") || !json.has("nickname")) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            User newUser = new User(
                json.getString("username"),
                json.getString("password"),
                json.optString("email", ""),
                json.getString("nickname")
            );

            if (auth.addUser(newUser)) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(403, -1);
            }
        } catch (Exception e) {
            exchange.sendResponseHeaders(400, -1);
        }
    }
}