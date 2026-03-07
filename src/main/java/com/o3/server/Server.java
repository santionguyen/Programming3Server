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
import java.util.UUID;

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
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            InputStream stream = exchange.getRequestBody();
            String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            stream.close();

            try {
                JSONObject jsonMsg = new JSONObject(text);
                ObservationRecord newRecord = new ObservationRecord(jsonMsg);
                
                newRecord.setRecordTimeReceived(System.currentTimeMillis());
                newRecord.setId(UUID.randomUUID().toString());
                
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
                        
                        // FEATURE 5 FIX: Weather Cache
                        Map<String, JSONObject> weatherCache = new HashMap<>();

                        for (int i = 0; i < observatories.length(); i++) {
                            JSONObject obs = observatories.getJSONObject(i);
                            
                            // IF the observatory has coordinates, fetch the weather!
                            if (obs.has("latitude") && obs.has("longitude")) {
                                double lat = obs.getDouble("latitude");
                                double lon = obs.getDouble("longitude");
                                String cacheKey = lat + "," + lon;
                                
                                JSONObject weather = null;
                                
                                // Check cache first
                                if (weatherCache.containsKey(cacheKey)) {
                                    weather = weatherCache.get(cacheKey);
                                } else {
                                    weather = getWeatherInfo(lat, lon);
                                    if (weather != null && weather.length() > 0) {
                                        weatherCache.put(cacheKey, weather); // Save to cache
                                    }
                                }

                                // Always attach the weather (either fetched data or an empty object)
                                if (weather != null && weather.length() > 0) {
                                    obs.put("weather", new JSONObject(weather.toString())); 
                                } else {
                                    obs.put("weather", new JSONObject()); 
                                }
                            } else {
                                // If no coordinates were provided, attach an empty weather object
                                obs.put("weather", new JSONObject());
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

        } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
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

    // BULLETPROOF PARSER WITH PROPER CONNECTION CLEANUP
    private JSONObject getWeatherInfo(double latitude, double longitude) {
        HttpURLConnection conn = null;
        InputStream inputStream = null;
        try {
            URI uri = new URI("http://127.0.0.1:4001/wfs?latlon=" + latitude + "," + longitude);
            conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000); 
            conn.setReadTimeout(3000);
            
            inputStream = conn.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            String response = in.lines().collect(Collectors.joining("\n"));
            
            JSONObject weatherInfo = new JSONObject();
            
            // Regex parsing safely ignores XML namespace errors
            java.util.regex.Matcher mName = java.util.regex.Pattern.compile("<[^>]*ParameterName[^>]*>([^<]+)</").matcher(response);
            java.util.regex.Matcher mVal = java.util.regex.Pattern.compile("<[^>]*ParameterValue[^>]*>([^<]+)</").matcher(response);
            
            while (mName.find() && mVal.find()) {
                String name = mName.group(1).trim();
                String valStr = mVal.group(1).trim();
                try {
                    double val = Double.parseDouble(valStr);
                    if (name.equalsIgnoreCase("temperatureInKelvins")) {
                        weatherInfo.put("temperature_in_kelvins", val);
                    } else if (name.equalsIgnoreCase("cloudinessPercentance") || name.equalsIgnoreCase("cloudinessPercentage")) {
                        weatherInfo.put("cloudiness_percentage", val);
                    } else if (name.equalsIgnoreCase("bagroundLightVolume") || name.equalsIgnoreCase("backgroundLightVolume")) {
                        weatherInfo.put("background_light_volume", val);
                    }
                } catch (NumberFormatException ignored) {}
            }
            return weatherInfo;
            
        } catch (Exception e) {
            return null; 
        } finally {
            // CRITICAL: Prevent Connection Exhaustion by safely closing resources
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

            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            System.out.println("HTTPS Server has started on port 8001");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}