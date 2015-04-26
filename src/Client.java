import client.CmdClient;

import java.io.IOException;
import java.net.SocketException;

/**
 * Created by szeyiu on 4/25/15.
 */
public class Client {
    public static void main(String[] args) throws IOException {
        String fin = "client0.txt";
        CmdClient cmdClient = new CmdClient(fin);
        cmdClient.start();
    }
}
