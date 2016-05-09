package swarmBots;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * used to for testing.
 * run this to see if your rover is sending out information tao others properly
 * @author Shay
 *
 */
public class DummyRover {

    public static void main(String[] args) {

        try {
            ServerSocket serverSocket = new ServerSocket(53799);
            System.out.println("DUMMY ROVER waiting...");

            Socket socket = serverSocket.accept();
            System.out.println("DUMMY ROVER someone has connected");

            while (true) {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                System.out.println(br.readLine());
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
