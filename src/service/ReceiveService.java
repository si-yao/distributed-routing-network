package service;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;

/**
 * Created by szeyiu on 4/25/15.
 */
public class ReceiveService implements Runnable{
    BFService bfService;
    private DatagramSocket listenSocket;


    public ReceiveService(BFService bfService){
        this.bfService = bfService;
    }

    @Override
    public void run() {
        System.out.println("start running ReceiveService");
        try {
            listenSocket = new DatagramSocket(bfService.getPort());
        } catch (SocketException e) {
            e.printStackTrace();
        }
        byte arr[] = new byte[1024];
        DatagramPacket packet = new DatagramPacket(arr, arr.length);
        while(true){
            try {
                listenSocket.receive(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
            onPacket(packet);
        }

    }

    public void onPacket(DatagramPacket packet){
        int length = packet.getLength();
        byte[] buf = new byte[length];
        System.arraycopy(packet.getData(), 0, buf, 0, length);

        ByteBuffer buffer = ByteBuffer.wrap(buf);
        int msgType = buffer.getShort(0);
        String ip = packet.getAddress().getHostAddress();
        ip = (ip.equals("localhost"))? "127.0.0.1":ip;
        int port = buffer.getInt(2);

        if(msgType == 1) {
            SerializeService serializeService = new SerializeService();
            serializeService.deserialize(buf);
            bfService.updateDV(serializeService.getDistanceVectors(), ip, port, serializeService.getDesIP(), serializeService.getCost());
        } else {
            System.out.println("Unsupported message type.");
        }

        /*
        else if(msgType == 2) {
            receiveLinkDown(ip, port);
        }
        else if(msgType == 3) {
            receiveLinkUp(ip, port);
        }
        else if(msgType == 4) {
            float cost = buffer.getFloat(6);
            receiveNewCost(ip, port, cost);
        }*/
    }
}
