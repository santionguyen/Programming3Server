package com.o3.server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.net.ssl.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

//week 3 updated ports
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Server implements HttpHandler {
    public List<ObservationRecord> messages = new ArrayList<>();
    //Helper to create SSL context
    private static SSLContext myServerSSLContext (String file, String pass) throws Exception{
        char[] passphrase = pass.toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(file), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks,passphrase);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(),null);
        return ssl;
    }
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            InputStream stream = exchange.getRequestBody();
            String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            stream.close();
            try {
                // Parse the incoming text as json object
                JSONObject jsonMsg = new JSONObject(text);
                // convert to ObservationRecord
                ObservationRecord newRecord = new ObservationRecord(jsonMsg);
                
                if (newRecord.isValid()) {
                    messages.add(newRecord);
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    sendResponse(exchange, 400, "Missing orbital elements or state_vector");
                }

            } catch (JSONException e) {
                sendResponse(exchange, 400, "Invalid JSON format");
            }

        } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {

            if (messages.isEmpty()) {
                exchange.sendResponseHeaders(204, -1);
            } else {
                // create json array
                JSONArray responseArray = new JSONArray();
                
                for (ObservationRecord record : messages) {
                    responseArray.put(record.toJSON());
                }
                
                String responseString = responseArray.toString();
                byte[] bytes = responseString.getBytes(StandardCharsets.UTF_8);
                
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);

                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
            } 
            
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }
    //Helper method to send simple text responses
    private void sendResponse(HttpExchange exchange, int code, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

public static void main (String [] args){
    try{
        //handle keystore and password (step 10)
        String keystoreFile = "keystore.jks"; // default for local testing
        String password = "password";
        if (args.length >=2 ){
            keystoreFile = args[0];
            password = args[1];
        }
        // Create HTTPS Server
        HttpsServer server = HttpsServer.create(new InetSocketAddress(8001), 0);

        // configure SSL
        SSLContext sslContext = myServerSSLContext(keystoreFile, password);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext){
            public void configure(HttpsParameters params){
                try{
                    SSLContext c = getSSLContext();
                    SSLEngine engine = c.createSSLEngine();
                    params.setNeedClientAuth(false);
                    params.setCipherSuites(engine.getEnabledCipherSuites());
                    params.setProtocols(engine.getEnabledProtocols());
                    SSLParameters sslparams = c.getDefaultSSLParameters();
                    params.setSSLParameters(sslparams);

                }catch (Exception ex){
                    System.out.println("Failed to create https port");
                }
            }
        });
        // Create authentificator
        UserAuthenticator authenticator = new UserAuthenticator("datarecord");
        // Setup registration 
        server.createContext("/registration", new RegistrationHandler(authenticator));
        // Setup Datarecord Path
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

    
