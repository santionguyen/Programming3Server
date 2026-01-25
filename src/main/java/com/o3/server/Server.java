package com.o3.server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Server implements HttpHandler {

    // Storage for mmessages
    private List<String> messages = new ArrayList<>();

    private Server() {
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // Implement handle() function structure 
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            handlePost(exchange);
        } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            handleGet(exchange);
        } else {
            handleOther(exchange);
        }
    }

    // Handling POST requests 
    private void handlePost(HttpExchange exchange) throws IOException {
        // 1. Get the request body input stream
        InputStream stream = exchange.getRequestBody();

        // 2. Read the text from the body 
        String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

        // 3. Store the text 
        if (!text.isEmpty()) {
            messages.add(text);
        }
        stream.close();

        // 4. Send response headers
        exchange.sendResponseHeaders(200, -1);
    }

    // Handling GET requests 
    private void handleGet(HttpExchange exchange) throws IOException {
        String responseString;

        // Create response string from stored messages 
        if (messages.isEmpty()) {
            responseString = "No messages"; // 
        } else {
            // Join all messages with a newline to return them
            responseString = String.join("\n", messages);
        }

        // 3. Get bytes (UTF-8) and send headers...
        byte[] bytes = responseString.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);

        // 4. Write response body 
        OutputStream outputStream = exchange.getResponseBody();
        outputStream.write(bytes);
        
        // 5. Flush and close 
        outputStream.flush();
        outputStream.close();
    }

    // Handling other requests 
    private void handleOther(HttpExchange exchange) throws IOException {
        String response = "Not supported";
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        
        // Return 400 Bad Request
        exchange.sendResponseHeaders(400, bytes.length);
        OutputStream outputStream = exchange.getResponseBody();
        outputStream.write(bytes);
        outputStream.close();
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8001), 0);
        
        // Notes: Changed path from "/help" to "/datarecord" to match
        server.createContext("/datarecord", new Server());
        
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port 8001");
    }
}