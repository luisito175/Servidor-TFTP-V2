import java.net.*;
import java.util.*;

public class ServerTftp {

    private static final int PORT_CONNECT_TFTP = 44444;
    private static final int TAM_MAX_BUFFER = 512;
    private static final String COD_TEXTO = "UTF-8";
    private static final int TIME_MAX_LISTEN = 10000;

    private static DatagramSocket socket;
    private static Resource r = new Resource();

    public static void main(String[] args) {

        try {
            socket = new DatagramSocket(PORT_CONNECT_TFTP);
            socket.setSoTimeout(TIME_MAX_LISTEN);

            System.out.println("======================================");
            System.out.println("Servidor TFTP iniciado");
            System.out.println("Escuchando conexiones en puerto " + PORT_CONNECT_TFTP);
            System.out.println("======================================");
            System.out.println(">> Servidor a la espera de clientes...");

            while (!r.endClient()) {
                try {
                    byte[] bufferReceiver = new byte[TAM_MAX_BUFFER];
                    DatagramPacket packetReceiver = new DatagramPacket(bufferReceiver, bufferReceiver.length);
                    socket.receive(packetReceiver);

                    InetAddress ipClient = packetReceiver.getAddress();
                    int portClient = packetReceiver.getPort();

                    String msg = new String(packetReceiver.getData(), 0, packetReceiver.getLength(), COD_TEXTO);
                    System.out.println("Cliente " + ipClient + ":" + portClient + " -> " + msg);

                    int port = r.devPort();
                    if (port != -1) {
                        byte[] bufferSend = String.valueOf(port).getBytes(COD_TEXTO);
                        DatagramPacket sendPacket = new DatagramPacket(bufferSend, bufferSend.length, ipClient, portClient);
                        socket.send(sendPacket);

                        new ServerThread(r, port).start();
                    }

                } catch (SocketTimeoutException e) {
                    System.out.println(">> Esperando clientes...");
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
