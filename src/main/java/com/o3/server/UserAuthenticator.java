package com.o3.server;
import com.sun.net.httpserver.BasicAuthenticator;
import java.util.Hashtable;
import java.util.Map;


public class UserAuthenticator extends BasicAuthenticator{
    private Map<String, User> users = null;  // now store full user object 
    public UserAuthenticator(String realm) {
        super(realm);
        users = new Hashtable<String, User>();
        // create a default user with an email 
        users.put("dummy", new User("dummy", "password", "dummy@test.com"));
    }
    @Override
    public boolean checkCredentials(String username, String password) {
        User u = users.get(username);
        // check if user exists an if the password inside matches
        if (u !=null && u.getPassword().equals(password)){
            return true;
        }
        return false;
    }
    // method to add users during registration
    public boolean addUser(User newUser){
        if (users.containsKey(newUser.getUsername())){
            return false; 
        }
        users.put(newUser.getUsername(), newUser);
        return true;
    }
}
