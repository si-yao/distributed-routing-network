package service;

import model.NeighborInfo;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Created by szeyiu on 4/25/15.
 */
public class SendService {
    private BFService bfService;
    private DatagramSocket socket;

    public SendService(BFService bfService) throws SocketException {
        this.bfService = bfService;
        socket = new DatagramSocket();
    }

    /**
     * For heartbeats, just send something, 2 bytes long.
     * @throws IOException
     */
    public void sendHeartBeats() throws IOException {
        try {
            //System.out.println("sending update");
            for(Map.Entry<String, NeighborInfo> neighborEntry : bfService.getNeighbors().entrySet()) {
                if(!neighborEntry.getValue().isConnected) continue;

                String destIP = bfService.extractIP(neighborEntry.getKey());
                int destPort = bfService.extractPort(neighborEntry.getKey());

                SerializeService serializeService = new SerializeService();
                serializeService.setMsgType((short) 0);// type 0 is heartbeats
                serializeService.setSrcPort(bfService.getPort());
                serializeService.setCost(neighborEntry.getValue().cost);
                serializeService.setDesIP(destIP);
                serializeService.setDesPort(destPort);

                byte[] buf = serializeService.serialize();

                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                packet.setAddress(InetAddress.getByName(destIP));
                packet.setPort(destPort);
                socket.send(packet);
                //System.out.println("send update to" + temp.getKey());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized void sendMyDV() {
        try {
            //System.out.println("sending update");
            for(Map.Entry<String, NeighborInfo> neighborEntry : bfService.getNeighbors().entrySet()) {
                if(!neighborEntry.getValue().isConnected) continue;

                String destIP = bfService.extractIP(neighborEntry.getKey());
                int destPort = bfService.extractPort(neighborEntry.getKey());

                SerializeService serializeService = new SerializeService();
                serializeService.setDistanceVectors(bfService.getMyDV());
                serializeService.setMsgType((short) 1);
                serializeService.setSrcPort(bfService.getPort());
                serializeService.setCost(neighborEntry.getValue().cost);
                serializeService.setDesIP(destIP);
                serializeService.setDesPort(destPort);

                byte[] buf = serializeService.serialize();

                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                packet.setAddress(InetAddress.getByName(destIP));
                packet.setPort(destPort);
                socket.send(packet);
                //System.out.println("send update to" + temp.getKey());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void sendLinkDown(String ip, int port) {
        try {
            //System.out.println("sending link down");
            ByteBuffer buffer = ByteBuffer.allocate(6);
            buffer.putShort(0,(short)2);
            buffer.putInt(2, bfService.getPort());
            DatagramPacket packet = new DatagramPacket(buffer.array(),6);
            packet.setAddress(InetAddress.getByName(ip));
            packet.setPort(port);
            socket.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void sendLinkUp(String ip, int port) {
        try {
            //System.out.println("sending link up");
            ByteBuffer buffer = ByteBuffer.allocate(6);
            buffer.putShort(0,(short)3);
            buffer.putInt(2, bfService.getPort());
            DatagramPacket packet = new DatagramPacket(buffer.array(),6);
            packet.setAddress(InetAddress.getByName(ip));
            packet.setPort(port);
            socket.send(packet);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }


}
