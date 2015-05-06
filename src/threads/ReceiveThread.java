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
        byte arr[] = new byte[40960];
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
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
    public void onPacket(DatagramPacket packet) throws IOException, InterruptedException {
        int length = packet.getLength();
        byte[] buf = new byte[length];
        System.arraycopy(packet.getData(), 0, buf, 0, length);

        ByteBuffer buffer = ByteBuffer.wrap(buf);
        int msgType = buffer.getShort(0);
        String ip = packet.getAddress().getHostAddress();
        ip = (ip.equals("localhost"))? "127.0.0.1":ip;
        int port = buffer.getInt(17);

        if(msgType == 1) { //route update
            SerializeService serializeService = new SerializeService();
            serializeService.deserialize(buf);
            bfService.updateDV(serializeService.getDistanceVectors(), ip, port, serializeService.getDesIP(), serializeService.getCost());
        }
        else if(msgType == 2) { //linkdown
            receiveLinkDown(ip, port);
        }
        else if(msgType == 3) { //linkup
            receiveLinkUp(ip, port);
        } else if(msgType == 4){// forward file
            bqueue.offer(ByteBuffer.wrap(buf));
        } else if(msgType == 5){// ACK
            //System.out.println("get ACK");
            SerializeService serializeService = new SerializeService();
            serializeService.deserializeBinFile(buf);
            //System.out.println(serializeService.getDesIP()+":"+serializeService.getDesPort());
            if(serializeService.getDesIP().equals(bfService.myIP) && serializeService.getDesPort()==bfService.myPort){
                receiveAck(serializeService.getOffset());
            } else {
                bqueue.offer(ByteBuffer.wrap(buf));
            }
        }
    }

    class FileConsumer implements Runnable{
        @Override
        public void run(){
            //System.out.println("Start the file consumer!");
            while(true){
                try {
                    ByteBuffer bb = bqueue.take();
                    byte[] buf = bb.array();
                    SerializeService serializeService = new SerializeService();
                    byte[] bin = serializeService.deserializeBinFile(buf);
                    //System.out.println("rec side sum: "+serializeService.getChecksum());
                    forwardBinFile(serializeService.getSrcIP(), serializeService.getSrcPort(),
                            serializeService.getDesIP(), serializeService.getDesPort(),
                            bin, serializeService.getOffset(), serializeService.getFilename(), serializeService.getChecksum());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void receiveAck(int offset) throws IOException, InterruptedException {
        fileService.receiveAck(offset);
    }

    public void receiveLinkDown(String ip, int port){
        System.out.println("Received link down signal, "+ ip +":"+port);
        bfService.linkDown(ip, port);
    }

    public void receiveLinkUp(String ip, int port){
        bfService.linkUp(ip, port);
    }

    public void forwardBinFile(String srcIP, int srcPort, String desIP, int desPort, byte[] bin, int offset, String filename, short sum) throws IOException {
        fileService.forwardBinFile(srcIP, srcPort, desIP, desPort, bin, offset, filename, sum);
    }
    public static int checksum(byte[] buf){
        int count = 0;
        for(int i=0; i<buf.length; ++i){
            count = count ^ buf[i];
        }
        return count%2;
    }
}
