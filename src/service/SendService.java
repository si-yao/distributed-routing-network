package service;

import model.NeighborInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.OverlappingFileLockException;
import java.util.Map;

/**
 * Created by szeyiu on 4/25/15.
 */
public class SendService {
    private BFService bfService;
    private DatagramSocket socket;
    private int MSS = 44+4+26+40000;
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
            int byteSize = 6+15;
            ByteBuffer buffer = ByteBuffer.allocate(byteSize);
            buffer.putShort(0,(short)2);
            buffer.putInt(2+15, bfService.getPort());
            DatagramPacket packet = new DatagramPacket(buffer.array(),byteSize);
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
            int byteSize = 6+15;
            ByteBuffer buffer = ByteBuffer.allocate(byteSize);
            buffer.putShort(0,(short)3);
            buffer.putInt(2+15, bfService.getPort());
            DatagramPacket packet = new DatagramPacket(buffer.array(),byteSize);
            packet.setAddress(InetAddress.getByName(ip));
            packet.setPort(port);
            socket.send(packet);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendFile(String desIP, int desPort, String nextHopIP, int nextHopPort, String file) throws IOException, InterruptedException {
        int fileSegSize = MSS - SerializeService.headerSize - 30;
        SerializeService serializeService = new SerializeService();
        serializeService.setMsgType((short) 4);//4 is for file transfer
        String myIp = (bfService.myIP==null)? "":bfService.myIP;
        serializeService.setSrcIP(myIp);
        serializeService.setSrcPort(bfService.getPort());
        serializeService.setCost(0);
        serializeService.setDesIP(desIP);
        serializeService.setDesPort(desPort);
        File fin = new File(file);
        String filename = fin.getName();
        FileInputStream is = new FileInputStream(fin);
        byte[] binArr = new byte[fileSegSize];
        int offset = 0;
        int curBinLen = is.read(binArr, 0, fileSegSize);
        while(curBinLen>0){
            if(curBinLen!=fileSegSize){
                byte[] binArr0 = new byte[curBinLen];
                System.arraycopy(binArr, 0, binArr0, 0, curBinLen);
                binArr = binArr0;
                offset = -offset-1;//negative means the end of the packet
            }
            //System.out.println("offset: " + offset);
            byte[] sendBytes = serializeService.serializeBinFile(binArr, offset, filename);
            DatagramPacket packet = new DatagramPacket(sendBytes, sendBytes.length);
            packet.setAddress(InetAddress.getByName(nextHopIP));
            packet.setPort(nextHopPort);
            socket.send(packet);
            if(offset<0) break;
            offset += curBinLen;
            curBinLen = is.read(binArr, 0, fileSegSize);
            Thread.sleep(500);
        }
        is.close();
    }

    public void forwardBin(String srcIP, int srcPort, String desIP, int desPort, String nextHopIP, int nextHopPort, byte[] bin, int offset, String filename) throws IOException {
        SerializeService serializeService = new SerializeService();
        serializeService.setMsgType((short) 4);//4 is for file transfer
        serializeService.setSrcIP(srcIP);
        serializeService.setSrcPort(srcPort);
        serializeService.setCost(0);
        serializeService.setDesIP(desIP);
        serializeService.setDesPort(desPort);
        byte[] sendBytes = serializeService.serializeBinFile(bin, offset, filename);
        DatagramPacket packet = new DatagramPacket(sendBytes, sendBytes.length);
        packet.setAddress(InetAddress.getByName(nextHopIP));
        packet.setPort(nextHopPort);
        socket.send(packet);
    }
}
