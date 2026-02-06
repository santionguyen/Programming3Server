package com.o3.server;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.json.JSONObject;
import org.json.JSONException;

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

        // Parse JSON inside a try-catch block
        try{
            JSONObject jsonUser = new JSONObject(text);
            //Extract the fields
            String username = jsonUser.getString("username");
            String password = jsonUser.getString("password");
            String email = jsonUser.getString("email");
            //Create the new User Object
            User newUser = new User(username, password, email);
            // add to Authenticor
            boolean success = auth.addUser(newUser);
            if (success){
                sendResponse(exchange, 200, "User registered!");
            } else{
                sendResponse(exchange, 403, "User already exists!");
            }
        }catch (JSONException e){
            //handle bad JSON or missing fields
            System.out.println("Registration failed: " + e.getMessage());
            sendResponse(exchange, 400, "Invalid JSON: " + e.getMessage());
        }
    } else {
        //reject other methods
        sendResponse(exchange, 405, "Not supported!");
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