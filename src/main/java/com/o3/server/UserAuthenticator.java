package com.o3.server;
import com.sun.net.httpserver.BasicAuthenticator;
import java.util.Hashtable;
import java.util.Map;


public class UserAuthenticator extends BasicAuthenticator{
    private Map<String, String>users = null;
    public UserAuthenticator(String realm) {
        super(realm);
        users = new Hashtable<String, String>();
        // add default user for testing 
        users.put("dummy", "password");
    }
    @Override
    public boolean checkCredentials(String username, String password) {
        //check if users during registration
        if(users.containsKey(username) && users.get(username).equals(password)){
            return true;
        }
        return false;
    }
    // method to add users during registration
    public boolean addUser(String username, String password){
        if (users.containsKey(username)){
            return false; //since user already exists
        }
        users.put(username, password);
        return true;
    }
}
