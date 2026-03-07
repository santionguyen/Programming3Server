package com.o3.server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.net.ssl.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Weather imports
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class Server implements HttpHandler {
    
    public List<ObservationRecord> messages = Collections.synchronizedList(new ArrayList<>());
    public MessageDatabase db = new MessageDatabase();

    public Server(){
        String dbPath = System.getenv("DATABASE_PATH");
        if (dbPath == null) {
            dbPath = "server.db";
        }
        db.open(dbPath);
        db.createTable();
        this.messages.addAll(db.readMessages());
    }

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
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (method.equalsIgnoreCase("POST")) {
            InputStream stream = exchange.getRequestBody();
            String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            stream.close();

            try {
                JSONObject jsonMsg = new JSONObject(text);
                ObservationRecord newRecord = new ObservationRecord(jsonMsg);
                
                newRecord.setRecordTimeReceived(System.currentTimeMillis());
                int newId = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
                newRecord.setId(String.valueOf(newId));
                
                String ownerToSet = "unknown";
                if (exchange.getPrincipal() != null) {
                    String username = exchange.getPrincipal().getUsername();
                    String nickname = db.getUserNickname(username);
                    if (nickname != null && !nickname.isEmpty()) {
                        ownerToSet = nickname; 
                    } else {
                        ownerToSet = username; 
                    }
                }
                
                if (newRecord.getRecordOwner() == null || newRecord.getRecordOwner().isEmpty()) {
                    newRecord.setRecordOwner(ownerToSet);
                }
                
                if (newRecord.isValid()) {
                    JSONArray observatories = newRecord.getObservatory();
                    if (observatories != null) {
                        Map<String, JSONObject> weatherCache = new HashMap<>();

                        for (int i = 0; i < observatories.length(); i++) {
                            JSONObject obs = observatories.getJSONObject(i);
                            if (obs.has("weather")) {
                                if (obs.has("latitude") && obs.has("longitude")) {
                                    double lat = obs.getDouble("latitude");
                                    double lon = obs.getDouble("longitude");
                                    String cacheKey = lat + "," + lon;
                                    
                                    JSONObject weather = null;
                                    if (weatherCache.containsKey(cacheKey)) {
                                        weather = weatherCache.get(cacheKey);
                                    } else {
                                        weather = getWeatherInfo(lat, lon);
                                        if (weather != null && weather.length() > 0) {
                                            weatherCache.put(cacheKey, weather); 
                                        }
                                    }

                                    if (weather != null && weather.length() > 0) {
                                        obs.put("weather", new JSONObject(weather.toString())); 
                                    } else {
                                        obs.put("weather", new JSONObject()); 
                                    }
                                } else {
                                    obs.put("weather", new JSONObject());
                                }
                            }
                        }
                    }

                    messages.add(newRecord);
                    db.addMessage(newRecord);
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    sendResponse(exchange, 400, "Invalid content");
                }

            } catch (JSONException e) {
                sendResponse(exchange, 400, "Invalid JSON format or data type");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal Server Error");
            }

        } else if (method.equalsIgnoreCase("GET")) {
            JSONArray responseArray = new JSONArray();
            synchronized(messages) {
                if (messages.isEmpty()) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                for (ObservationRecord record : messages) {
                    responseArray.put(record.toJSON());
                }
            }
            
            String responseString = responseArray.toString();
            byte[] bytes = responseString.getBytes(StandardCharsets.UTF_8);
            
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            
        // FEATURE 7: Handle PUT request to update observations
        } else if (method.equalsIgnoreCase("PUT")) {
            if (path.equals("/datarecord") || path.equals("/datarecord/")) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("id=")) {
                    String idStr = query.replace("id=", "");
                    
                    ObservationRecord existingRecord = db.getMessageById(idStr);
                    if (existingRecord == null) {
                        sendResponse(exchange, 404, "Not Found: Observation ID does not exist");
                        return;
                    }
                    
                    // Verify the owner
                    String username = exchange.getPrincipal().getUsername();
                    String nickname = db.getUserNickname(username);
                    String currentUser = (nickname != null && !nickname.isEmpty()) ? nickname : username;
                    
                    if (!existingRecord.getRecordOwner().equals(currentUser)) {
                        sendResponse(exchange, 403, "Forbidden: You are not the owner of this record");
                        return;
                    }
                    
                    InputStream stream = exchange.getRequestBody();
                    String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .lines().collect(Collectors.joining("\n"));
                    stream.close();
                    
                    try {
                        JSONObject jsonMsg = new JSONObject(text);
                        ObservationRecord updatedRecord = new ObservationRecord(jsonMsg);
                        
                        if (!updatedRecord.isValid()) {
                            sendResponse(exchange, 400, "Invalid content in updated observation");
                            return;
                        }
                        
                        // Maintain the original core values
                        updatedRecord.setId(existingRecord.getId());
                        updatedRecord.setRecordTimeReceived(existingRecord.getRecordTimeReceived());
                        updatedRecord.setRecordOwner(existingRecord.getRecordOwner());
                        
                        // Set Feature 7 specific fields
                        if (updatedRecord.getUpdateReason() == null || updatedRecord.getUpdateReason().isEmpty()) {
                            updatedRecord.setUpdateReason("N/A");
                        }
                        
                        updatedRecord.setEdited(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")));
                            
                        // Refresh weather data for the updated observatories
                        JSONArray observatories = updatedRecord.getObservatory();
                        if (observatories != null) {
                            Map<String, JSONObject> weatherCache = new HashMap<>();
                            for (int i = 0; i < observatories.length(); i++) {
                                JSONObject obs = observatories.getJSONObject(i);
                                if (obs.has("weather")) {
                                    if (obs.has("latitude") && obs.has("longitude")) {
                                        double lat = obs.getDouble("latitude");
                                        double lon = obs.getDouble("longitude");
                                        String cacheKey = lat + "," + lon;
                                        JSONObject weather = weatherCache.containsKey(cacheKey) ? weatherCache.get(cacheKey) : getWeatherInfo(lat, lon);
                                        
                                        if (weather != null && weather.length() > 0) {
                                            weatherCache.put(cacheKey, weather);
                                            obs.put("weather", new JSONObject(weather.toString())); 
                                        } else {
                                            obs.put("weather", new JSONObject()); 
                                        }
                                    } else {
                                        obs.put("weather", new JSONObject());
                                    }
                                }
                            }
                        }
                        
                        // Save changes to DB and memory
                        db.updateMessage(updatedRecord);
                        synchronized(messages) {
                            for (int i = 0; i < messages.size(); i++) {
                                if (messages.get(i).getId().equals(updatedRecord.getId())) {
                                    messages.set(i, updatedRecord);
                                    break;
                                }
                            }
                        }
                        
                        // Return the updated object
                        String responseString = updatedRecord.toJSON().toString();
                        byte[] bytes = responseString.getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                        
                    } catch (JSONException e) {
                        sendResponse(exchange, 400, "Invalid JSON format or data type");
                    }
                } else {
                    sendResponse(exchange, 400, "Missing ID parameter in URL query");
                }
            } else {
                sendResponse(exchange, 404, "Not Found");
            }
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private JSONObject getWeatherInfo(double latitude, double longitude) {
        HttpURLConnection conn = null;
        InputStream inputStream = null;
        try {
            String urlString = String.format(java.util.Locale.US, "http://127.0.0.1:4001/wfs?latlon=%.6f,%.6f&parameters=Temperature,TotalCloudCover,RadiationGlobalAccumulation", latitude, longitude);
            URI uri = new URI(urlString);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000); 
            conn.setReadTimeout(5000);
            
            inputStream = conn.getInputStream();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); 
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);
            
            JSONObject weatherInfo = new JSONObject();
            
            NodeList names = document.getElementsByTagNameNS("*", "ParameterName");
            NodeList values = document.getElementsByTagNameNS("*", "ParameterValue");
            
            for (int i = 0; i < names.getLength(); i++) {
                String name = names.item(i).getTextContent().trim();
                double value = Double.parseDouble(values.item(i).getTextContent().trim());
                
                switch (name) {
                    case "Temperature":
                        weatherInfo.put("temperature_in_kelvins", Math.round((value + 273.15) * 100.0) / 100.0);
                        break;
                    case "TotalCloudCover":
                        weatherInfo.put("cloudiness_percentage", (int) Math.round(value));
                        break;
                    case "RadiationGlobalAccumulation":
                        weatherInfo.put("background_light_volume", value);
                        break;
                }
            }
            return weatherInfo;
            
        } catch (Exception e) {
            return null; 
        } finally {
            if (inputStream != null) {
                try { inputStream.close(); } catch (IOException ignored) {}
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static void main(String[] args) {
        try {
            String keystoreFile = "keystore.jks"; 
            String password = "password";
            if (args.length >= 2) {
                keystoreFile = args[0];
                password = args[1];
            }

            HttpsServer server = HttpsServer.create(new InetSocketAddress(8001), 0);
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
            
            Server myServer = new Server();
            UserAuthenticator authenticator = new UserAuthenticator("datarecord", myServer.db);
            
            server.createContext("/registration", new RegistrationHandler(authenticator));
            HttpContext context = server.createContext("/datarecord", myServer); 
            context.setAuthenticator(authenticator);

            // Collections Endpoint
            HttpContext collectionsContext = server.createContext("/collections", new CollectionsHandler(myServer.db));
            collectionsContext.setAuthenticator(authenticator);

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            System.out.println("HTTPS Server has started on port 8001");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}