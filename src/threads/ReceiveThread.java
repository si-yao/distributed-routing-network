package threads;

import service.BFService;
import service.FileService;
import service.SerializeService;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Created by szeyiu on 4/25/15.
 */
public class ReceiveThread implements Runnable{
    BFService bfService;
    FileService fileService;
    private DatagramSocket listenSocket;
    BlockingQueue<ByteBuffer> bqueue;

    public ReceiveThread(BFService bfService) throws SocketException {
        this.bfService = bfService;
        fileService = new FileService(bfService);
        bqueue = new LinkedBlockingDeque<ByteBuffer>();
    }

    @Override
    public void run() {
        System.out.println("start running ReceiveThread");
        Thread ft = new Thread(new FileConsumer());
        ft.start();
        try {
            listenSocket = new DatagramSocket(bfService.getPort());
        } catch (SocketException e) {
            e.printStackTrace();
        }
        byte arr[] = new byte[4096];
        DatagramPacket packet = new DatagramPacket(arr, arr.length);
        while(true){
            try {
                listenSocket.receive(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
            Thread t = new Thread(new OnPacketThread(packet));
            t.start();
        }
    }

    class OnPacketThread implements Runnable{
        DatagramPacket packet;
        public OnPacketThread(DatagramPacket packet){
            this.packet = packet;
        }
        @Override
        public void run(){
            try {
                onPacket(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    public void onPacket(DatagramPacket packet) throws IOException {
        int length = packet.getLength();
        byte[] buf = new byte[length];
        System.arraycopy(packet.getData(), 0, buf, 0, length);

        ByteBuffer buffer = ByteBuffer.wrap(buf);
        int msgType = buffer.getShort(0);
        String ip = packet.getAddress().getHostAddress();
        ip = (ip.equals("localhost"))? "127.0.0.1":ip;
        int port = buffer.getInt(17);

        if(msgType == 1) {
            SerializeService serializeService = new SerializeService();
            serializeService.deserialize(buf);
            bfService.updateDV(serializeService.getDistanceVectors(), ip, port, serializeService.getDesIP(), serializeService.getCost());
        }
        else if(msgType == 2) {
            receiveLinkDown(ip, port);
        }
        else if(msgType == 3) {
            receiveLinkUp(ip, port);
        } else if(msgType == 4){
            bqueue.offer(ByteBuffer.wrap(buf));
        }
    }

    class FileConsumer implements Runnable{
        @Override
        public void run(){
            while(true){
                try {
                    ByteBuffer bb = bqueue.take();
                    byte[] buf = bb.array();
                    SerializeService serializeService = new SerializeService();
                    byte[] bin = serializeService.deserializeBinFile(buf);
                    forwardBinFile(serializeService.getSrcIP(), serializeService.getSrcPort(),
                            serializeService.getDesIP(), serializeService.getDesPort(), bin, serializeService.getOffset());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void receiveLinkDown(String ip, int port){
        System.out.println("Received link down signal, "+ ip +":"+port);
        bfService.linkDown(ip, port);
    }

    public void receiveLinkUp(String ip, int port){
        bfService.linkUp(ip, port);
    }

    public void forwardBinFile(String srcIP, int srcPort, String desIP, int desPort, byte[] bin, int offset) throws IOException {
        fileService.forwardBinFile(srcIP, srcPort, desIP, desPort, bin, offset);
    }
}
