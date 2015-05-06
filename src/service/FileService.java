package service;

import threads.ReceiveThread;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketException;

/**
 * Created by szeyiu on 4/29/15.
 */
public class FileService {
    SendService sendService;
    BFService bfService;
    byte[] curBuffer;
    public FileService(BFService bfService) throws SocketException {
        this.bfService = bfService;
        this.sendService = new SendService(bfService);
        curBuffer = null;
    }
    public void forwardBinFile(String srcIP, int srcPort, String desIP, int desPort, byte[] bin, int offset, String filename, short sum) throws IOException {
        boolean isAck = filename.equals("thisisaack");
        if(!isAck) System.out.println("packet received (offset: " + offset + ")");
        if(!isAck) System.out.println("Source = " + srcIP + ":" + srcPort);
        if(bfService.myIP.equals(desIP) && bfService.myPort==desPort){
            byte[] tmp = new byte[1];
            String nextAddr = bfService.nextHop(srcIP, srcPort);
            //System.out.print("pckt sum: "+ReceiveThread.checksum(bin)+"\nsupposed sum: "+sum+"\n");
            if(nextAddr!=null && ReceiveThread.checksum(bin)==sum){
                String nextIP = bfService.extractIP(nextAddr);
                int nextPort = bfService.extractPort(nextAddr);
                sendService.forwardBin(desIP,desPort,srcIP,srcPort,nextIP,nextPort,tmp,offset,"thisisaack");
            }
            boolean isEnd = false;
            if(offset<0){
                isEnd = true;
                offset = -offset-1;
            }
            int end = offset+bin.length-1;
            if(curBuffer==null || curBuffer.length<=end){
                if(curBuffer!=null){
                    byte[] newBuf = new byte[end+1];
                    System.arraycopy(curBuffer, 0, newBuf, 0, curBuffer.length);
                    curBuffer = newBuf;
                } else {
                    curBuffer = new byte[end+1];
                }
            }
            System.arraycopy(bin, 0, curBuffer, offset, bin.length);
            if(isEnd){
                File file = new File(filename);
                FileOutputStream os = new FileOutputStream(file);
                os.write(curBuffer);
                os.close();
                curBuffer = null;
                System.out.println("File received successfully");
            }
        } else {
            String nextAddr = bfService.nextHop(desIP, desPort);
            if(nextAddr==null) {
                System.out.println("Host cannot reach the dest.");
                return;
            }
            String nextIP = bfService.extractIP(nextAddr);
            int nextPort = bfService.extractPort(nextAddr);
            if(!isAck)  System.out.println("Destination = " + desIP + ":" + desPort);
            sendService.forwardBin(srcIP, srcPort, desIP, desPort, nextIP, nextPort, bin, offset, filename);
            if(!isAck)  System.out.println("Next hop = "+nextAddr);
        }
    }

    public void receiveAck(int offset) throws IOException, InterruptedException {
        sendService.ackReceived(offset);
    }
    public void sendFile(String desIP, int desPort, String filename) throws IOException, InterruptedException {
        String addr = bfService.nextHop(desIP,desPort);
        if(addr==null){
            System.out.println("Cannot reach the destination");
            return;
        }
        String nextIP = bfService.extractIP(addr);
        int nextport = bfService.extractPort(addr);
        System.out.println("next Hop = "+addr);
        sendService.sendFile(desIP, desPort, nextIP, nextport, filename);
        System.out.println("File sent successfully");
    }
}