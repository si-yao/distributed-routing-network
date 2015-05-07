package service;

import model.DistanceInfo;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * This class provides the service for serialization and deserialization.
 * Created by szeyiu on 4/25/15.
 */
public class SerializeService {
    private ConcurrentHashMap<String, DistanceInfo> distanceVectors;
    private short msgType;
    private String srcIP;
    private int srcPort;
    private float desCost;
    private String desIP;
    private int desPort;
    private int offset;
    private String filename;
    private short checksum;
    public static int headerSize;
    int filenameSize = 26;
    int offsetSize = 4;

    public SerializeService(){
        distanceVectors = new ConcurrentHashMap<String, DistanceInfo>();
        headerSize = 44; //general header size
    }

    /**
     * Serialize the header, which is used for all serializations.s
     * @param buffer
     */
    private void serializeHeader(ByteBuffer buffer){
        buffer.putShort(msgType);   //2B
        if(srcIP==null) srcIP="";
        String srcIP_temp = srcIP;
        for(int i=0; i<15-srcIP.length(); i++) {
            srcIP_temp = srcIP_temp + " ";
        }
        buffer.put(srcIP_temp.getBytes()); //15B
        buffer.putInt(srcPort);   //4B
        buffer.putFloat(desCost);   //4B

        String desIP_temp = desIP;
        for(int i=0; i<15-desIP.length(); i++) {
            desIP_temp = desIP_temp + " ";
        }
        buffer.put(desIP_temp.getBytes());  //15B
        buffer.putInt(desPort);
    }

    /**
     * The serialize function for packets updating DV
     * @return
     */
    public byte[] serialize() {
        int size = distanceVectors.size();
        ByteBuffer buffer = ByteBuffer.allocate(46 * size + headerSize);
        serializeHeader(buffer);
        if(distanceVectors!=null) {
            for (Map.Entry<String, DistanceInfo> entry : distanceVectors.entrySet()) {
                String destination = entry.getKey();
                float cost = entry.getValue().cost;
                String firsHop = entry.getValue().firstHop;

                String add1 = "";
                String add2 = "";
                for (int i = 0; i < 21 - destination.length(); i++) {
                    add1 += " ";
                }
                for (int i = 0; i < 21 - firsHop.length(); i++) {
                    add2 += " ";
                }

                destination = destination + add1;
                firsHop = firsHop + add2;

                buffer.put(destination.getBytes());
                buffer.putFloat(cost);
                buffer.put(firsHop.getBytes());
            }
        }
        return buffer.array();
    }

    /**
     * serialize packets transmitting binary files.
     * @param bin
     * @param offset
     * @return
     */
    public byte[] serializeBinFile(byte[] bin, int offset, String filename){
        int binSize = bin.length;
        ByteBuffer buffer = ByteBuffer.allocate(filenameSize + offsetSize + binSize + headerSize + 2);
        serializeHeader(buffer);
        buffer.put(formatString(filename, filenameSize).getBytes());
        buffer.putInt(offset);
        buffer.putShort(checksum);
        buffer.put(bin);
        return buffer.array();
    }

    /**
     * Format the string to the certain length.
     * @param s
     * @param len
     * @return
     */
    private String formatString(String s, int len){
        if(len<=s.length()){
            return s.substring(0,len);
        }
        StringBuilder sb = new StringBuilder(s);
        for(int i=s.length(); i<len; ++i){
            sb.append(" ");
        }
        return sb.toString();
    }

    /**
     * deerialize the packet. Save the parameters to this object.
     * @param buf
     */
    public void deserialize(byte[] buf){
        int size = buf.length;
        ByteBuffer buffer = ByteBuffer.wrap(buf);
        msgType = buffer.getShort(0);
        byte[] srcIP_temp = new byte[15];
        System.arraycopy(buf, 2, srcIP_temp, 0, 15);
        String srcIP_str = new String(srcIP_temp);
        srcIP = srcIP_str.trim();
        srcPort = buffer.getInt(17);
        desCost = buffer.getFloat(21);
        byte[] desIP_temp = new byte[15];
        System.arraycopy(buf, 25, desIP_temp, 0, 15);
        String desIP_str = new String(desIP_temp);
        desIP = desIP_str.trim();
        desPort = buffer.getInt(40);
        int pos = 44;

        while(pos+45 < size) {
            byte[] des = new byte[21];
            byte[] hop = new byte[21];

            System.arraycopy(buf, pos, des, 0, 21);
            String nei = new String(des);
            nei = nei.trim();

            float cost = buffer.getFloat(pos+21);

            System.arraycopy(buf, pos+25, hop, 0, 21);
            String firstHop = new String(hop);
            firstHop = firstHop.trim();

            pos += 46;

            DistanceInfo item = new DistanceInfo(cost, firstHop);
            distanceVectors.put(nei, item);
        }
    }

    /**
     * deserialized the file pakcet.
     * @param buf
     * @return the bin file
     */
    public byte[] deserializeBinFile(byte[] buf){
        int size = buf.length;
        ByteBuffer buffer = ByteBuffer.wrap(buf);
        msgType = buffer.getShort(0);
        byte[] srcIP_temp = new byte[15];
        System.arraycopy(buf, 2, srcIP_temp, 0, 15);
        String srcIP_str = new String(srcIP_temp);
        srcIP = srcIP_str.trim();
        srcPort = buffer.getInt(17);
        desCost = buffer.getFloat(21);
        byte[] desIP_temp = new byte[15];
        System.arraycopy(buf, 25, desIP_temp, 0, 15);
        String desIP_str = new String(desIP_temp);
        desIP = desIP_str.trim();
        desPort = buffer.getInt(40);
        byte[] filenameBuf = new byte[filenameSize];
        System.arraycopy(buf, 44, filenameBuf, 0, filenameSize);
        String filename_str = new String(filenameBuf);
        filename = filename_str.trim();
        offset = buffer.getInt(44 + filenameSize);
        checksum = buffer.getShort(44+filenameSize+offsetSize);
        int pos = 44+filenameSize+offsetSize+2;
        if(pos>=size) return null;
        byte[] binBytes = new byte[size-pos];
        System.arraycopy(buf, pos, binBytes, 0, binBytes.length);
        return binBytes;//the returned bin array which does not include the offset.
    }

    public ConcurrentHashMap<String, DistanceInfo> getDistanceVectors() {
        return distanceVectors;
    }

    public void setDistanceVectors(ConcurrentHashMap<String, DistanceInfo> vec) {
        distanceVectors = vec;
    }

    public int getDesPort() {
        return desPort;
    }

    public void setDesPort(int desPort) {
        this.desPort = desPort;
    }

    public float getDesCost() {
        return desCost;
    }

    public void setDesCost(float desCost) {
        this.desCost = desCost;
    }

    public short getMsgType() {
        return msgType;
    }

    public void setMsgType(short type) {
        msgType = type;
    }

    public int getSrcPort() {
        return srcPort;
    }

    public void setSrcPort(int port) {
        srcPort = port;
    }

    public float getCost() {
        return desCost;
    }

    public void setCost(float cost) {
        this.desCost = cost;
    }

    public String getDesIP() {
        return desIP;
    }

    public void setDesIP(String ip) {
        this.desIP = ip;
    }

    public String getSrcIP() {
        return srcIP;
    }

    public void setSrcIP(String srcIP) {
        this.srcIP = srcIP;
    }

    public int getOffset() {
        return offset;
    }
    public void setOffset(int offset) {
        this.offset = offset;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public static int getHeaderSize() {
        return headerSize;
    }

    public static void setHeaderSize(int headerSize) {
        SerializeService.headerSize = headerSize;
    }

    public short getChecksum() {
        return checksum;
    }

    public void setChecksum(short checksum) {
        this.checksum = checksum;
    }

    public int getFilenameSize() {
        return filenameSize;
    }

    public void setFilenameSize(int filenameSize) {
        this.filenameSize = filenameSize;
    }

    public int getOffsetSize() {
        return offsetSize;
    }

    public void setOffsetSize(int offsetSize) {
        this.offsetSize = offsetSize;
    }
}
