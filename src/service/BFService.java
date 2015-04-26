package service;

import model.*;

import java.io.IOException;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.*;
/**
 * Created by szeyiu on 4/25/15.
 */
public class BFService {
    private String myIP;//actually I don't need to know my IP, because UDP contains it.
    private int myPort;//I must know my port, because UDP does not contain it, and I need to indicate when send msg to neighbours.
    private String myAddress;//unnecessary, only need to know port.
    private int timeout;// seconds
    private ConcurrentHashMap<String, NeighborInfo> neighbors;
    private ConcurrentHashMap<String, DistanceInfo> myDV;
    private ConcurrentHashMap<String, ConcurrentHashMap<String, DistanceInfo>> neighborsDV;
    private SendService sendService;
    private ScheduledExecutorService scheduler;

    Runnable heartBeats = new Runnable() {
        @Override
        public void run() {
            sendService.sendMyDV();
        }
    };

    Runnable checkAlive = new Runnable() {
        @Override
        public void run() {
            checkActive();
        }
    };


    public BFService(int port, int timeout) throws SocketException {
        this.myPort = port;
        this.timeout = timeout;
        sendService = new SendService(this);
        neighbors = new ConcurrentHashMap<String, NeighborInfo>();
        myDV = new ConcurrentHashMap<String, DistanceInfo>();
        neighborsDV = new ConcurrentHashMap<String, ConcurrentHashMap<String, DistanceInfo>>();
        scheduler = Executors.newScheduledThreadPool(3);
        scheduler.scheduleAtFixedRate(heartBeats, Math.min(timeout*1000/3, 1000), Math.min(timeout*1000/3, 1000), MILLISECONDS);
        scheduler.scheduleAtFixedRate(checkAlive, timeout*1000, timeout*1000, MILLISECONDS);
    }

    private void checkActive() {
        Date currentTime = Calendar.getInstance().getTime();
        boolean isChanged = false;
        for(Map.Entry<String,NeighborInfo> item: neighbors.entrySet()) {
            if(!item.getValue().isConnected) continue;
            Date lastUpdate = item.getValue().time;
            if((currentTime.getTime() - lastUpdate.getTime())> timeout*1000) {
                System.out.println("Kill neighbor: "+item.getKey());
                item.getValue().isConnected = false;
                neighborsDV.remove(item.getKey());
                isChanged = updateMyDV();
            }
        }
        if(isChanged) sendService.sendMyDV();
    }

    /**
     * Recalculate my DV, invoke this function whenever anything changes.
     * @return
     */
    private synchronized boolean updateMyDV(){
        boolean isChanged = false;
        ConcurrentHashMap<String, DistanceInfo> myDV0 = new ConcurrentHashMap<String, DistanceInfo>();
        for(String nei: neighbors.keySet()){
            if(!neighbors.get(nei).isConnected) continue;
            myDV0.put(nei, new DistanceInfo(neighbors.get(nei).cost, nei));
            if(!neighborsDV.containsKey(nei)) continue;
            ConcurrentHashMap<String, DistanceInfo> neiDV = neighborsDV.get(nei);
            for(String nei2Des: neiDV.keySet()){
                if(neiDV.get(nei2Des).firstHop.equals(myAddress)||nei2Des.equals(myAddress)) continue;
                if(!myDV0.containsKey(nei2Des)){
                    isChanged = true;
                    myDV0.put(nei2Des, new DistanceInfo(neighbors.get(nei).cost+neiDV.get(nei2Des).cost, nei));
                }
            }
        }
        for(String desFromMyDV : myDV0.keySet()){
            DistanceInfo disInfoFromMyDV = myDV0.get(desFromMyDV);
            float curCost = disInfoFromMyDV.cost;
            for(String nei: neighbors.keySet()){
                if(!neighbors.get(nei).isConnected) continue;
                if(!neighborsDV.containsKey(nei)) continue;
                NeighborInfo neiInfo = neighbors.get(nei);
                float neiCost = neiInfo.cost;
                ConcurrentHashMap<String, DistanceInfo> neiDV = neighborsDV.get(nei);
                if(!neiDV.containsKey(desFromMyDV)) continue;
                DistanceInfo disInfoFromNeiDV = neiDV.get(desFromMyDV);
                if(disInfoFromNeiDV.firstHop.equals(myAddress)) continue;
                float neiDesCost = disInfoFromNeiDV.cost;
                if(curCost>neiDesCost+neiCost){
                    disInfoFromMyDV.cost = neiDesCost+neiCost;
                    disInfoFromMyDV.firstHop = nei;
                    isChanged = true;
                }
            }
        }
        myDV = myDV0;
        return isChanged;
    }

    private String getAddress(String ip, int port) {
        return ip + ":" + port;
    }

    private NeighborInfo updateNeighbor(String ip, int port, float cost) {
        ip = (ip.equals("localhost"))? "127.0.0.1": ip;
        String address = getAddress(ip, port);
        NeighborInfo item = new NeighborInfo(cost);
        neighbors.put(address, item);
        return item;
    }

    private  NeighborInfo findNeighbor(String ip, int port) {
        String address = getAddress(ip, port);
        if(neighbors.containsKey(address)) {
            return neighbors.get(address);
        }
        return null;
    }

    public ConcurrentHashMap<String, NeighborInfo> getNeighbors() {
        return neighbors;
    }

    public String extractIP(String address) {
        int index = address.indexOf(":");
        return address.substring(0, index);
    }

    public ConcurrentHashMap<String, DistanceInfo> getMyDV() {
        return myDV;
    }

    public int extractPort(String address) {
        int index = address.indexOf(":");
        return Integer.parseInt(address.substring(index+1));
    }

    public int getPort() {
        return myPort;
    }

    /**
     * update my DV according to the received DV of neighbour. (if neighbor's DV has not changed, then do nothing)
     * @param neighborDV vector is the DV received from a neighbour.
     * @param fromIP the neighbour's ip
     * @param fromPort the neighbour's port
     * @param toIP my ip, need it in case.
     * @param cost the cost between me and this neighbour.
     */
    public void updateDV(ConcurrentHashMap<String, DistanceInfo> neighborDV, String fromIP, int fromPort, String toIP, float cost) {
        //SHOULD CHECK IF NEI IS NOT AVAILABLE, then do not do update.
        //But should do for the 1st time.
        if(myIP==null || myIP.equals("")) {
            myIP = toIP;
            myAddress = getAddress(myIP, myPort);
        }
        String neiAddr = getAddress(fromIP, fromPort);
        NeighborInfo nei = updateNeighbor(fromIP, fromPort, cost);
        nei.updateTime();
        nei.isConnected = true;
        if(isSameDV(neighborDV, fromIP,fromPort,cost)) return;
        neighborsDV.put(neiAddr, neighborDV);
        updateDVModal(neighborDV, fromIP, fromPort, toIP, cost);
    }

    /**
     * Update my DV modally, no matter whether neighborDV has been changed or not.
     * @param neighborDV
     * @param fromIP
     * @param fromPort
     * @param toIP
     * @param cost
     */
    public void updateDVModal(ConcurrentHashMap<String, DistanceInfo> neighborDV, String fromIP, int fromPort, String toIP, float cost){
        boolean isChanged = updateMyDV();
        if(isChanged) {
            sendService.sendMyDV();
        }
    }

    public void addCost(String toIP, int toPort, float cost){
        String addr = getAddress(toIP, toPort);
        neighbors.put(addr, new NeighborInfo(cost));
        updateMyDV();
        sendService.sendMyDV();
    }

    public boolean changeCost(String toIP, int toPort, float cost){
        String addr = getAddress(toIP, toPort);
        if(neighbors.containsKey(addr) && neighbors.get(addr).isConnected){
            addCost(toIP, toPort, cost);
            return true;
        }
        return false;
    }

    private boolean isSameDV(ConcurrentHashMap<String, DistanceInfo> neighborDV, String fromIP, int fromPort, float cost){
        String neiAddr = getAddress(fromIP, fromPort);
        if(!neighbors.containsKey(neiAddr)) return false;
        if(!neighborsDV.containsKey(neiAddr)) return false;
        NeighborInfo oldinfo = neighbors.get(neiAddr);
        if(!oldinfo.isConnected) return false;
        if(oldinfo.cost!=cost) return false;
        ConcurrentHashMap<String, DistanceInfo> oldDV = neighborsDV.get(neiAddr);
        if(neighborDV.size()!=oldDV.size()) return false;
        for(String des : neighborDV.keySet()){
            if(!oldDV.containsKey(des)) return false;
            DistanceInfo oldDist = oldDV.get(des);
            DistanceInfo newDist = neighborDV.get(des);
            if(oldDist.cost!=newDist.cost) return false;
            if(!oldDist.firstHop.equals(newDist.firstHop)) return false;
        }
        return true;
    }


    public void heartFromNeighbour(String fromIP, int fromPort){
        NeighborInfo n = findNeighbor(fromIP, fromPort);
        if(n==null) return;
        n.updateTime();
    }


    public void showRT() {
        SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss");
        String time = format.format(Calendar.getInstance().getTime());
        System.out.println("Time: " + time + "   Distance vector list is:");
        for(Map.Entry<String,DistanceInfo> item: myDV.entrySet()) {
            String output = "";
            output += "Destination = " + item.getKey() + ", ";
            output += "Cost = " + item.getValue().cost + ", ";
            output += "Link = (" + item.getValue().firstHop + ")";
            System.out.println(output);
        }
    }

    public synchronized void showNeighbor() {
        SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss");
        String time = format.format(Calendar.getInstance().getTime());
        System.out.println("Time: " + time + "   Neighbor list is:");
        for(Map.Entry<String,NeighborInfo> item: neighbors.entrySet()) {
            if(item.getValue().isConnected) {
                String output = "";
                output += "Address = " + item.getKey() + ", ";
                output += "Cost = " + item.getValue().cost + ", ";
                output += "last Update = " + format.format(item.getValue().time);
                System.out.println(output);
            }
        }
    }

    public void linkUp(String ip, int port){
        String addr = getAddress(ip, port);
        if(!neighbors.containsKey(addr)) return;
        neighbors.get(addr).isConnected = true;
        updateMyDV();
    }

    public void linkDown(String ip, int port){
        String addr = getAddress(ip, port);
        if(!neighbors.containsKey(addr)) return;
        neighbors.get(addr).isConnected = false;
        neighborsDV.remove(addr);
        updateMyDV();
    }

}
