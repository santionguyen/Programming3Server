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

public class Server implements HttpHandler {
    public List<String> messages = new ArrayList<>();
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
            if (!text.isEmpty()) {
                messages.add(text);
            }
            stream.close();
            exchange.sendResponseHeaders(200, -1);
        } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            String responseString = messages.isEmpty() ? "No messages" : String.join("\n", messages);
            byte[] bytes = responseString.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } else {
            exchange.sendResponseHeaders(400, -1);
        }
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
        // Setup datarecord with authentication 
        HttpContext context = server.createContext("/datarecord", new Server());
        context.setAuthenticator(authenticator);
        server.createContext("/registration", new RegistrationHandler(authenticator));
        server.setExecutor(null);
        server.start();
        System.out.println("HTTPS Server has started on port 8001");
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
}

    
