public class User {
    String username;
    String password;
    boolean anonymous;

    public User(String username, String password, boolean anonymous) {
        this.username = username;
        this.password = password;
        this.anonymous = anonymous;
    }
}
