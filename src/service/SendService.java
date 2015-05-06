package service;

import com.sun.xml.internal.bind.v2.runtime.reflect.Lister;
import model.DistanceInfo;
import model.NeighborInfo;
import threads.ReceiveThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.OverlappingFileLockException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by szeyiu on 4/25/15.
 */
public class SendService {
    private BFService bfService;
    private DatagramSocket socket;
    private int MSS = 44+4+26+2+40000;
    private static Map<String, String> nb2proxy = new ConcurrentHashMap<String, String>();
    private static BlockingDeque<PacketOffset> sendingQueue;
    static Thread sendTh;
    public SendService(BFService bfService) throws SocketException {
        if(nb2proxy==null){
            nb2proxy = new ConcurrentHashMap<String, String>();
        }
        this.bfService = bfService;
        socket = new DatagramSocket();
        if(sendingQueue==null){
            sendingQueue = new LinkedBlockingDeque<PacketOffset>();
        }
        if(sendTh==null) {
            sendTh = new Thread(sendWorker);
            sendTh.start();
        }
    }
    private Runnable sendWorker = new Runnable() {
        @Override
        public void run() {
            while(true){
                try {
                    PacketOffset packet = sendingQueue.peek();
                    //System.out.println("sendworker: size: "+sendingQueue.size());
                    if(packet!=null) socket.send(packet.packet);
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    };
    class PacketOffset{
        DatagramPacket packet;
        int offset;
        public PacketOffset(DatagramPacket p, int o){
            this.packet = p;
            this.offset = o;
        }
    }
    public void ackReceived(int offset) throws InterruptedException, IOException {
        PacketOffset top = sendingQueue.peek();
        //System.out.println("ackrec: size: "+sendingQueue.size());
        if(top==null) return;
        //System.out.println("check the sent packet");
        if(offset==top.offset){
            sendingQueue.poll();
            //System.out.println("Next packet...");
            PacketOffset packet = sendingQueue.peek();
            if(packet!=null) socket.send(packet.packet);
        }
    }

    public void setProxy(String nbIP, int nbPort, String pyIP, int pyPort){
        String pyAddr = pyIP+":"+pyPort;
        String nbAddr = nbIP+":"+nbPort;
        nb2proxy.put(nbAddr, pyAddr);
    }

    public void rmProxy(String nbIP, int nbPort){
        String nbAddr = nbIP+":"+nbPort;
        nb2proxy.remove(nbAddr);
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
        String nextAddr = nextHopIP+":"+nextHopPort;
        int fileSegSize = MSS - SerializeService.headerSize - 30 -2;
        boolean isProxy = false;
        if(nb2proxy.containsKey(nextAddr)){
            System.out.println("forward through proxy");
            String pyAddr = nb2proxy.get(nextAddr);
            nextHopIP = extractIP(pyAddr);
            nextHopPort = extractPort(pyAddr);
            fileSegSize -= 39600;
            isProxy = true;
        }
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
            int s = ReceiveThread.checksum(binArr);
            serializeService.setChecksum((short)s);
            //System.out.println("sender's sum: " +s);
            byte[] sendBytes = serializeService.serializeBinFile(binArr, offset, filename);
            DatagramPacket packet = new DatagramPacket(sendBytes, sendBytes.length);
            packet.setAddress(InetAddress.getByName(nextHopIP));
            packet.setPort(nextHopPort);
            if(!isProxy) {
                //System.out.println("send the packet");
                socket.send(packet);
            }
            else {
                //System.out.println("packet enqueue!");
                sendingQueue.offer(new PacketOffset(packet, offset));
            }
            if(offset<0) break;
            offset += curBinLen;
            curBinLen = is.read(binArr, 0, fileSegSize);
            Thread.sleep(500);
        }
        is.close();
    }

    public void forwardBin(String srcIP, int srcPort, String desIP, int desPort, String nextHopIP, int nextHopPort, byte[] bin, int offset, String filename) throws IOException {
        String nextAddr = nextHopIP+":"+nextHopPort;
        if(nb2proxy.containsKey(nextAddr)){
            System.out.println("forward through proxy");
            String pyAddr = nb2proxy.get(nextAddr);
            nextHopIP = extractIP(pyAddr);
            nextHopPort = extractPort(pyAddr);
        }
        SerializeService serializeService = new SerializeService();
        serializeService.setMsgType((short) 4);//4 is for file transfer
        if(filename.equals("thisisaack")){
            serializeService.setMsgType((short) 5);//here are forwarding ack
        }
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

    private String extractIP(String address) {
        int index = address.indexOf(":");
        return address.substring(0, index);
    }

    private int extractPort(String address) {
        int index = address.indexOf(":");
        return Integer.parseInt(address.substring(index+1));
    }
}
