package com.o3.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class CollectionsHandler implements HttpHandler {
    private MessageDatabase db;

    public CollectionsHandler(MessageDatabase db) {
        this.db = db;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        try {
            if (method.equalsIgnoreCase("GET")) {
                if (path.equals("/collections") || path.equals("/collections/")) {
                    List<Integer> ids = db.getCollectionIds();
                    JSONArray jsonArray = new JSONArray(ids);
                    sendResponse(exchange, 200, jsonArray.toString());
                } else {
                    String[] parts = path.split("/");
                    if (parts.length > 2) {
                        try {
                            int collectionId = Integer.parseInt(parts[2]);
                            if (!db.collectionExists(collectionId)) {
                                sendResponse(exchange, 404, "Collection not found");
                                return;
                            }
                            
                            List<ObservationRecord> records = db.getMessagesInCollection(collectionId);
                            JSONArray responseArray = new JSONArray();
                            for (ObservationRecord rec : records) {
                                responseArray.put(rec.toJSON());
                            }
                            sendResponse(exchange, 200, responseArray.toString());
                        } catch (NumberFormatException e) {
                            sendResponse(exchange, 400, "Invalid collection ID");
                        }
                    } else {
                        sendResponse(exchange, 400, "Missing collection ID");
                    }
                }
            } else if (method.equalsIgnoreCase("POST")) {
                InputStream stream = exchange.getRequestBody();
                String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .lines().collect(Collectors.joining("\n"));
                stream.close();

                if (path.equals("/collections/create")) {
                    JSONArray messageIds = null;
                    if (!text.trim().isEmpty()) {
                        try {
                            messageIds = new JSONArray(text);
                        } catch (JSONException e) {
                            sendResponse(exchange, 400, "Invalid JSON array");
                            return;
                        }
                    }
                    
                    int newId = db.createCollection(messageIds);
                    if (newId != -1) {
                        JSONObject responseJson = new JSONObject();
                        responseJson.put("created_collection_id", newId);
                        sendResponse(exchange, 200, responseJson.toString());
                    } else {
                        sendResponse(exchange, 500, "Failed to create collection");
                    }
                } else if (path.equals("/collections/add")) {
                    try {
                        JSONObject json = new JSONObject(text);
                        int collectionId = json.getInt("collection_id");
                        JSONArray messageIds = json.getJSONArray("message_ids");
                        
                        boolean success = db.addMessagesToCollection(collectionId, messageIds);
                        if (success) {
                            sendResponse(exchange, 200, "{}");
                        } else {
                            sendResponse(exchange, 500, "Failed to add messages");
                        }
                    } catch (JSONException e) {
                        sendResponse(exchange, 400, "Invalid JSON format");
                    }
                } else {
                    sendResponse(exchange, 404, "Not Found");
                }
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Internal Server Error");
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}