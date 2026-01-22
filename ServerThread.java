import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;

public class ServerThread extends Thread {

    private Resource r;
    private int port;
    private static final int TAM_MAX_BUFFER = 512;
    private static final String COD_TEXTO = "UTF-8";
    private static final int TIME_MAX_LISTEN = 10000;

    public ServerThread(Resource _r, int portLisen) {
        port = portLisen;
        r = _r;
    }

    private void send(String msg, DatagramSocket socket, InetAddress addr, int port) throws IOException {
        byte[] buffer = msg.getBytes(COD_TEXTO);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, addr, port);
        socket.send(packet);
    }

    private String listFiles(String user) {
        File folder = new File(user);
        if (!folder.exists() || !folder.isDirectory()) return "CARPETA NO EXISTE";
        String[] files = folder.list();
        if (files == null || files.length == 0) return "VACIO";
        return String.join(",", files);
    }

    @Override
    public void run() {
        System.out.println("ServerThread iniciado en puerto " + port);

        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(port);
            socket.setSoTimeout(TIME_MAX_LISTEN);

            byte[] buffer = new byte[TAM_MAX_BUFFER];

            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);

                    InetAddress addr = packet.getAddress();
                    int clientPort = packet.getPort();

                    String msg = new String(packet.getData(), 0, packet.getLength(), COD_TEXTO);
                    System.out.println("Recibido: " + msg);

                    String[] parts = msg.split(" ");

                    // --------------------------
                    // LOGIN
                    // --------------------------
                    if (parts[0].equalsIgnoreCase("LOGIN")) {

                        String user = parts[1];
                        String pass = parts[2];

                        // ⚠️ Usamos Resource (TU lista)
                        String token = r.login(user, pass);

                        if (token != null) {
                            send("OK TOKEN " + token, socket, addr, clientPort);
                            System.out.println("Login correcto: " + user + " -> token " + token);
                        } else {
                            send("ERROR", socket, addr, clientPort);
                            System.out.println("Login fallido: " + user);
                        }

                    }

                    // --------------------------
                    // LIST
                    // --------------------------
                    else if (parts[0].equalsIgnoreCase("LIST")) {

                        String token = parts[1];
                        User user = r.getUserByToken(token);

                        if (user == null) {
                            send("TOKEN_INVALIDO", socket, addr, clientPort);
                        } else {
                            send(listFiles(user.username), socket, addr, clientPort);
                        }

                    }

                    // --------------------------
                    // DELETE
                    // --------------------------
                    else if (parts[0].equalsIgnoreCase("DELETE")) {

                        String token = parts[1];
                        User user = r.getUserByToken(token);

                        if (user == null) {
                            send("TOKEN_INVALIDO", socket, addr, clientPort);
                            continue;
                        }

                        // anonymous NO puede borrar
                        if (user.anonymous) {
                            send("NO_PERMISO", socket, addr, clientPort);
                            continue;
                        }

                        String filename = parts[2];
                        File file = new File(user.username + "/" + filename);
                        if (file.exists()) {
                            file.delete();
                            send("BORRADO_OK", socket, addr, clientPort);
                        } else {
                            send("NO_EXISTE", socket, addr, clientPort);
                        }

                    }

                    // --------------------------
                    // PUT
                    // --------------------------
                    else if (parts[0].equalsIgnoreCase("PUT")) {

                        String token = parts[1];
                        User user = r.getUserByToken(token);

                        if (user == null) {
                            send("TOKEN_INVALIDO", socket, addr, clientPort);
                            continue;
                        }

                        // anonymous NO puede subir
                        if (user.anonymous) {
                            send("NO_PERMISO", socket, addr, clientPort);
                            continue;
                        }

                        String filename = parts[2];

                        // 1) recibimos tamaño
                        DatagramPacket sizePacket = new DatagramPacket(buffer, buffer.length);
                        socket.receive(sizePacket);
                        int size = Integer.parseInt(new String(sizePacket.getData(), 0, sizePacket.getLength(), COD_TEXTO));

                        // 2) recibimos datos
                        File folder = new File(user.username);
                        if (!folder.exists()) folder.mkdir();

                        FileOutputStream fos = new FileOutputStream(user.username + "/" + filename);
                        int received = 0;

                        while (received < size) {
                            DatagramPacket dataPacket = new DatagramPacket(buffer, buffer.length);
                            socket.receive(dataPacket);
                            fos.write(dataPacket.getData(), 0, dataPacket.getLength());
                            received += dataPacket.getLength();
                        }

                        fos.close();
                        send("PUT_OK", socket, addr, clientPort);

                    }

                    // --------------------------
                    // GET
                    // --------------------------
                    else if (parts[0].equalsIgnoreCase("GET")) {

                        String token = parts[1];
                        User user = r.getUserByToken(token);

                        if (user == null) {
                            send("TOKEN_INVALIDO", socket, addr, clientPort);
                            continue;
                        }

                        String filename = parts[2];
                        File file = new File(user.username + "/" + filename);

                        if (!file.exists()) {
                            send("NO_EXISTE", socket, addr, clientPort);
                            continue;
                        }

                        byte[] fileBytes = Files.readAllBytes(file.toPath());
                        int size = fileBytes.length;

                        // 1) enviamos tamaño
                        send(String.valueOf(size), socket, addr, clientPort);

                        // 2) enviamos datos en paquetes
                        int offset = 0;
                        while (offset < size) {
                            int chunk = Math.min(TAM_MAX_BUFFER, size - offset);
                            DatagramPacket dataPacket = new DatagramPacket(fileBytes, offset, chunk, addr, clientPort);
                            socket.send(dataPacket);
                            offset += chunk;
                        }
                    }

                    else {
                        send("COMANDO NO SOPORTADO", socket, addr, clientPort);
                    }

                } catch (SocketTimeoutException e) {
                    // sigue escuchando
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }
}
