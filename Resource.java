import java.util.*;

public class Resource {

    int portMin = 50000;
    int portAsign;
    int maxClient = 30;

    List<User> users = new ArrayList<>();
    Map<String, User> tokens = new HashMap<>();

    public Resource() {
        portAsign = portMin;

        users.add(new User("luis", "1234", false));
        users.add(new User("admin", "admin", false));
        users.add(new User("anonymous", "anonymous", true));
    }

    public synchronized int devPort() {
        if (portAsign - portMin < maxClient)
            return portAsign++;
        else
            return -1;
    }

    public synchronized boolean endClient() {
        return (portAsign == maxClient);
    }

    // LOGIN
    public synchronized String login(String user, String pass) {
        for (User u : users) {
            if (u.username.equals(user) && u.password.equals(pass)) {
                String token = UUID.randomUUID().toString();
                tokens.put(token, u);
                return token;
            }
        }
        return null;
    }

    public synchronized User getUserByToken(String token) {
        return tokens.get(token);
    }
}
