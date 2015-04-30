package client;

import service.BFService;
import service.FileService;
import threads.CmdThread;
import threads.ReceiveThread;
import service.SendService;

import java.io.*;
import java.net.SocketException;

/**
 * Created by szeyiu on 4/25/15.
 */
public class CmdClient{
    private int port;
    private int timeout;
    private BFService bfService;
    private SendService sendService;
    private FileService fileService;
    private String configFile;

    public CmdClient(String configFile) throws SocketException {
        this.configFile = configFile;
    }

    public void start() throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(configFile))));
        String line = bufferedReader.readLine();
        String[] splt = line.split(" ");
        port = Integer.valueOf(splt[0]);
        timeout = Integer.valueOf(splt[1]);
        bfService = new BFService(port, timeout);
        sendService = new SendService(bfService);
        fileService = new FileService(bfService);
        ReceiveThread receiveThread = new ReceiveThread(bfService);
        Thread t = new Thread(receiveThread);
        t.start();
        CmdThread cmdThread = new CmdThread(bfService, sendService, fileService);
        t = new Thread(cmdThread);
        t.start();

        line = bufferedReader.readLine();
        while(line!=null) {
            splt = line.split(" ");
            String addr = splt[0];
            float cost = Float.valueOf(splt[1]);
            String nIP = bfService.extractIP(addr);
            int nPort = bfService.extractPort(addr);
            bfService.addCost(nIP, nPort, cost);
            line = bufferedReader.readLine();
        }
        bufferedReader.close();
    }

}
