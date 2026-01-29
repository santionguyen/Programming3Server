package com.o3.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class RegistrationHandler implements HttpHandler{
    private UserAuthenticator auth;
    public RegistrationHandler(UserAuthenticator auth) {
        this.auth = auth;
    }
    @Override
    public void handle(HttpExchange exchange ) throws IOException{
        // Only allow POST request for registration 
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")){
        // Read request body 
            InputStream stream = exchange.getRequestBody();
            String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            stream.close();

        // Parse "username.password"
        String[] parts = text.split(":");
        if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            String newUser = parts[0];
            String newPass = parts[1];
            boolean success = auth.addUser(newUser, newPass);
            if(success){
                sendResponse(exchange, 200, "User registered!");
            }else{
                sendResponse(exchange, 403, "User already exists");
            }
        }else{
                sendResponse(exchange, 400, "Invalid format. Must use username:password");
            } 
        }else {
            // reject other methods
            sendResponse(exchange, 405, "Not Supported!");
        }
    }
    private void sendResponse(HttpExchange exchange, int code, String message) throws IOException{
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
