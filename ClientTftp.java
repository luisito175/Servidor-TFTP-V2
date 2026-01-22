import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;

public class ClientTftp {

    private static final int PORT_CONNECT_TFTP = 44444;
    private static final int TAM_MAX_BUFFER = 512;
    private static final String COD_TEXTO = "UTF-8";
    private static final int TIME_MAX_LISTEN = 4000;

    private static String IP_SERVER;
    private static DatagramSocket socket;
    private static InetAddress ipServer;

    // CONEXIÓN INICIAL: pide puerto dinámico al servidor
    private static Integer connectTftp() {
        try {
            socket = new DatagramSocket();
            ipServer = InetAddress.getByName(IP_SERVER);
            socket.setSoTimeout(TIME_MAX_LISTEN);

            String msg = "Enviando petición de conexión";
            byte[] bufferSend = msg.getBytes(COD_TEXTO);
            byte[] bufferReceive = new byte[TAM_MAX_BUFFER];

            DatagramPacket sendPacket = new DatagramPacket(bufferSend, bufferSend.length, ipServer, PORT_CONNECT_TFTP);
            socket.send(sendPacket);

            DatagramPacket receivePacket = new DatagramPacket(bufferReceive, bufferReceive.length);
            socket.receive(receivePacket);

            int newPort = Integer.parseInt(
                    new String(receivePacket.getData(), 0, receivePacket.getLength(), COD_TEXTO)
            );

            socket.close();
            return newPort;

        } catch (Exception e) {
            System.out.println("Error en conexión inicial: " + e.getMessage());
        }
        return null;
    }

    // ENVÍA UN MENSAJE
    private static void sendCommand(String msg, int port) throws IOException {
        byte[] buffer = msg.getBytes(COD_TEXTO);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, ipServer, port);
        socket.send(packet);
    }

    // RECIBE RESPUESTA
    private static String receiveResponse() throws IOException {
        byte[] buffer = new byte[TAM_MAX_BUFFER];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        return new String(packet.getData(), 0, packet.getLength(), COD_TEXTO);
    }

    // LOOP DE COMANDOS
    public static void commandLoop(int port) throws IOException {
        socket = new DatagramSocket();
        ipServer = InetAddress.getByName(IP_SERVER);
        socket.setSoTimeout(TIME_MAX_LISTEN);

        Scanner sc = new Scanner(System.in);

        System.out.println("Conectado al puerto dinámico: " + port);
        System.out.print("Usuario: ");
        String user = sc.nextLine();
        System.out.print("Password: ");
        String pass = sc.nextLine();

        // LOGIN
        sendCommand("LOGIN " + user + " " + pass, port);
        String resp = receiveResponse();
        System.out.println(resp);

        if (!resp.startsWith("OK TOKEN")) {
            System.out.println("Login fallido. Cerrando...");
            return;
        }

        String token = resp.split(" ")[2];

        while (true) {
            System.out.print("> ");
            String line = sc.nextLine();
            if (line.equalsIgnoreCase("exit")) break;

            String[] parts = line.split(" ");

            if (parts[0].equalsIgnoreCase("LIST")) {
                sendCommand("LIST " + token, port);
                System.out.println(receiveResponse());

            } else if (parts[0].equalsIgnoreCase("GET")) {

                String filename = parts[2];
                sendCommand("GET " + token + " " + filename, port);

                // 1) tamaño
                String sizeStr = receiveResponse();
                int size = Integer.parseInt(sizeStr);

                // 2) datos
                FileOutputStream fos = new FileOutputStream("download_" + filename);
                int received = 0;

                while (received < size) {
                    byte[] buffer = new byte[TAM_MAX_BUFFER];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    fos.write(packet.getData(), 0, packet.getLength());
                    received += packet.getLength();
                }
                fos.close();
                System.out.println("GET completado: download_" + filename);

            } else if (parts[0].equalsIgnoreCase("PUT")) {

                String filename = parts[2];
                File f = new File(filename);

                if (!f.exists()) {
                    System.out.println("NO_EXISTE_FICHERO");
                    continue;
                }

                byte[] fileBytes = Files.readAllBytes(f.toPath());
                int size = fileBytes.length;

                // 1) enviar comando PUT
                sendCommand("PUT " + token + " " + filename, port);

                // 2) enviar tamaño
                sendCommand(String.valueOf(size), port);

                // 3) enviar datos
                int offset = 0;
                while (offset < size) {
                    int chunk = Math.min(TAM_MAX_BUFFER, size - offset);
                    byte[] part = Arrays.copyOfRange(fileBytes, offset, offset + chunk);
                    DatagramPacket packet = new DatagramPacket(part, part.length, ipServer, port);
                    socket.send(packet);
                    offset += chunk;
                }

                System.out.println("PUT enviado: " + filename);
                System.out.println(receiveResponse());

            } else if (parts[0].equalsIgnoreCase("DELETE")) {

                String filename = parts[2];
                sendCommand("DELETE " + token + " " + filename, port);
                System.out.println(receiveResponse());

            } else {
                System.out.println("COMANDO INVALIDO");
            }
        }

        socket.close();
    }

    public static void main(String[] args) throws IOException {

        if (args.length < 1) {
            System.out.println("Debes pasar la dirección ip del servidor");
            System.exit(-1);
        }

        IP_SERVER = args[0];

        Integer portServerTftp = connectTftp();
        if (portServerTftp == null) {
            System.exit(-1);
        }

        commandLoop(portServerTftp);
    }
}