package com.o3.server;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class Server implements HttpHandler {
    
    public MessageDatabase db = new MessageDatabase();
    UserAuthenticator ua; 

    // Constructor 1: For the Test Environment (Logic)
    public Server() {
        String dbPath = System.getenv("DATABASE_PATH");
        if (dbPath == null) {
            dbPath = "server.db";
        }
        db.open(dbPath);
        db.createTable();
    }
    
    // Constructor 2: For Main (Dependency Injection)
    public Server(UserAuthenticator ua) {
        this(); // Call the constructor above to open DB
        this.ua = ua;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        // CORS Headers
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Authorization, Content-Type");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        // Authentication Logic
        String username = "unknown";
        if (ua != null) {
            Authenticator.Result result = ua.authenticate(exchange);
            if (result instanceof Authenticator.Success) {
                username = ((Authenticator.Success) result).getPrincipal().getUsername();
            } else {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
        } else if (exchange.getPrincipal() != null) {
            username = exchange.getPrincipal().getUsername();
        }

        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            handlePost(exchange, username);
        } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            handleGet(exchange);
        } else {
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private void handlePost(HttpExchange exchange, String username) throws IOException {
        InputStream stream = exchange.getRequestBody();
        String text = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));
        stream.close();

        try {
            JSONObject jsonMsg = new JSONObject(text);
            ObservationRecord newRecord = new ObservationRecord(jsonMsg);

            // 1. STRICT VALIDATION 
            if (!newRecord.isValid()) {
                sendResponse(exchange, 400, "Invalid content: Fields must be correct types.");
                return;
            }

            // 2. Add Server Metadata
            newRecord.setRecordTimeReceived(System.currentTimeMillis());
            newRecord.setId(UUID.randomUUID().toString());
            newRecord.setRecordOwner(username);

            // 3. Process Observatories & XML Weather
            if (jsonMsg.has("observatory")) {
                JSONArray observatories = jsonMsg.getJSONArray("observatory");
                JSONArray weatherData = new JSONArray();
                
                for (int i = 0; i < observatories.length(); i++) {
                    JSONObject obs = observatories.getJSONObject(i);
                    if (obs.has("latitude") && obs.has("longitude")) {
                        JSONObject weather = getWeatherInfo(obs.getDouble("latitude"), obs.getDouble("longitude"));
                        if (weather != null) weatherData.put(weather);
                    }
                }
                if (weatherData.length() > 0) {
                    newRecord.setObservatoryWeather(weatherData.toString());
                }
            }

            // 4. Save to DB
            db.addMessage(newRecord);
            exchange.sendResponseHeaders(200, -1);

        } catch (JSONException e) {
            sendResponse(exchange, 400, "Invalid JSON format");
        } catch (Exception e) {
            e.printStackTrace();
            sendResponse(exchange, 500, "Server Error");
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        try {
            JSONArray responseArray = new JSONArray();
            for (ObservationRecord rec : db.readMessages()) {
                 responseArray.put(rec.toJSON());
            }
            
            String responseString = responseArray.toString();
            byte[] bytes = responseString.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (Exception e) {
            sendResponse(exchange, 500, "Error reading database");
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    // XML Weather Fetcher
    private JSONObject getWeatherInfo(double latitude, double longitude) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            // Assuming local weather service is running on port 4001 as per your code
            URI uri = new URI("http://127.0.0.1:4001/wfs?latlon="+latitude+","+longitude);
            URL url = uri.toURL();
            InputStream inputStream = url.openStream();
            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();
            NodeList nodeList = document.getElementsByTagName("wfs:member");
            JSONObject weatherInfo = new JSONObject();
            
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    Element bsWfsElement = (Element) element.getElementsByTagName("BsWfs:BsWfsElement").item(0);
                    String parameterName = bsWfsElement.getElementsByTagName("BsWfs:ParameterName").item(0).getTextContent();
                    String parameterValue = bsWfsElement.getElementsByTagName("BsWfs:ParameterValue").item(0).getTextContent();
                    
                    if (parameterName.equals("temperatureInKelvins")) weatherInfo.put("temperatureInKelvins", parameterValue);
                    else if (parameterName.equals("cloudinessPercentance")) weatherInfo.put("cloudinessPercentance", parameterValue);
                    else if (parameterName.equals("bagroundLightVolume")) weatherInfo.put("bagroundLightVolume", parameterValue);
                }
            }
            return weatherInfo;
        } catch (Exception e) {
            // Fail silently if weather service is down or unparsable
            return null;
        }
    }

    public static void main(String[] args) {
        try {
            HttpsServer server = HttpsServer.create(new InetSocketAddress(8001), 0);
            UserAuthenticator ua = new UserAuthenticator("datarecord", new MessageDatabase()); 
            
            // Pass the authenticator to the Server instance
            Server myServer = new Server(ua); 
            
            HttpContext context = server.createContext("/datarecord", myServer);
            context.setAuthenticator(ua);
            server.createContext("/registration", new RegistrationHandler(ua));
            
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            System.out.println("Server started on port 8001");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}