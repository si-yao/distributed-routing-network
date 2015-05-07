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

    /**
     * This funtion is the entry of client. It first load the config flie and start all services for routers
     * and transferring files, and dealing with packet loss and corruption.
     * @throws IOException
     */
    public void start() throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(configFile))));
        String line = bufferedReader.readLine();
        String[] splt = line.split(" ");
        port = Integer.valueOf(splt[0]);
        timeout = Integer.valueOf(splt[1]);
        bfService = new BFService(port, timeout);//bf service handling route updates
        sendService = new SendService(bfService);//send service handling sending packets in lower level.
        fileService = new FileService(bfService);//file service handling receiving and forwarding files.
        ReceiveThread receiveThread = new ReceiveThread(bfService);//Receive thread listening on port to receive packet.
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
        //Add a event for shuting down the program.
        Runtime.getRuntime().addShutdownHook(new Thread(){
           @Override
        public void run(){
               System.out.println("Closed!");
           }
        });
    }

}
