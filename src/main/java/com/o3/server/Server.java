package com.o3.server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.net.ssl.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Server implements HttpHandler {
    
    public List<ObservationRecord> messages = new ArrayList<>();

    // Helper to create SSL context
    private static SSLContext myServerSSLContext(String file, String pass) throws Exception {
        char[] passphrase = pass.toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(file)) {
            ks.load(fis, passphrase);
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);
        
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);
        
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return ssl;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            // Read the request body
            InputStream stream = exchange.getRequestBody();
            String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            stream.close();

            try {
                // 1. Parse valid JSON syntax
                JSONObject jsonMsg = new JSONObject(text);

                // 2. Convert to Record (This should throw JSONException if data types are wrong)
                ObservationRecord newRecord = new ObservationRecord(jsonMsg);
                
                // 3. Validate logical constraints (e.g. valid lat/lon ranges)
                if (newRecord.isValid()) {
                    messages.add(newRecord);
                    exchange.sendResponseHeaders(200, -1); // 200 OK 
                } else {
                    sendResponse(exchange, 400, "Invalid content in orbital elements or state_vector");
                }

            } catch (JSONException e) {
                // Catch malformed JSON or wrong data types (e.g., string instead of double)
                sendResponse(exchange, 400, "Invalid JSON format or data type");
            } catch (Exception e) {
                // Catch any other unexpected server errors
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal Server Error");
            }

        } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            
            if (messages.isEmpty()) {
                exchange.sendResponseHeaders(204, -1); // 204 No Content
            } else {
                JSONArray responseArray = new JSONArray();
                for (ObservationRecord record : messages) {
                    responseArray.put(record.toJSON());
                }
                
                String responseString = responseArray.toString();
                byte[] bytes = responseString.getBytes(StandardCharsets.UTF_8);
                
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);

                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } 
            
        } else {
            exchange.sendResponseHeaders(405, -1); // 405 Method Not Allowed
        }
    }

    // Helper method to send simple text responses
    private void sendResponse(HttpExchange exchange, int code, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void main(String[] args) {
        try {
            String keystoreFile = "keystore.jks"; // default for local testing
            String password = "password";
            
            if (args.length >= 2) {
                keystoreFile = args[0];
                password = args[1];
            }

            // Create HTTPS Server
            HttpsServer server = HttpsServer.create(new InetSocketAddress(8001), 0);

            // Configure SSL
            SSLContext sslContext = myServerSSLContext(keystoreFile, password);
            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                public void configure(HttpsParameters params) {
                    try {
                        SSLContext c = getSSLContext();
                        SSLEngine engine = c.createSSLEngine();
                        params.setNeedClientAuth(false);
                        params.setCipherSuites(engine.getEnabledCipherSuites());
                        params.setProtocols(engine.getEnabledProtocols());
                        SSLParameters sslparams = c.getDefaultSSLParameters();
                        params.setSSLParameters(sslparams);
                    } catch (Exception ex) {
                        System.out.println("Failed to create https port");
                    }
                }
            });

            // Create Authenticator
            UserAuthenticator authenticator = new UserAuthenticator("datarecord");
            
            // Setup paths
            server.createContext("/registration", new RegistrationHandler(authenticator));
            
            HttpContext context = server.createContext("/datarecord", new Server());
            context.setAuthenticator(authenticator);

            // Start server
            server.setExecutor(null);
            server.start();
            System.out.println("HTTPS Server has started on port 8001");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}