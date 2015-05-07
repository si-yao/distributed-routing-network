package service;

import model.*;

import java.io.IOException;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import static java.util.concurrent.TimeUnit.*;
/**
 * This class provides the main services and functions for
 * BF routing algorithm and maintaining routing info and neighbour's info.
 * Created by szeyiu on 4/25/15.
 */
public class BFService {
    public String myIP;//actually I don't need to know my IP, because UDP contains it.
    public int myPort;//I must know my port, because UDP does not contain it, and I need to indicate when send msg to neighbours.
    public String myAddress;//unnecessary, only need to know port.
    private int timeout;// seconds
    private ConcurrentHashMap<String, NeighborInfo> neighbors;
    private ConcurrentHashMap<String, DistanceInfo> myDV;
    private ConcurrentHashMap<String, ConcurrentHashMap<String, DistanceInfo>> neighborsDV;
    private HashSet<String> knownHost;
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
        //set timer for heartbeat event. I set the max timer is 2 second, to deal with the situation
        //if some router has a large timeout value, that will cause timeout on its neighbours.
        scheduler.scheduleAtFixedRate(heartBeats, Math.min(timeout*1000, 2000), Math.min(timeout*1000, 2000), MILLISECONDS);
        //check alive
        scheduler.scheduleAtFixedRate(checkAlive, timeout*1000, timeout*1000, MILLISECONDS);
        //to record which host is known
        knownHost = new HashSet<String>(50);
    }

    /**
     * To tell which neighbou I should send the packet through.
     * @param desIP
     * @param desPort
     * @return
     */
    public String nextHop(String desIP, int desPort){
        String addr = getAddress(desIP, desPort);
        if(!myDV.containsKey(addr)) return null;
        return myDV.get(addr).firstHop;
    }

    /**
     * Kill the user when no response for 3*timeout time.
     */
    private void checkActive() {
        Date currentTime = Calendar.getInstance().getTime();
        boolean isChanged = false;
        for(Map.Entry<String,NeighborInfo> item: neighbors.entrySet()) {
            if(!item.getValue().isConnected) continue;
            Date lastUpdate = item.getValue().time;
            if((currentTime.getTime() - lastUpdate.getTime())> 3*timeout*1000) {
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
        if(!neighbors.containsKey(neiAddr)) return;
        if(!neighbors.get(neiAddr).isConnected && knownHost.contains(neiAddr)) return;
        knownHost.add(neiAddr);
        NeighborInfo nei = updateNeighbor(fromIP, fromPort, cost);
        nei.updateTime();
        nei.isConnected = true;
        if(isSameDV(neighborDV, fromIP,fromPort,cost)) return;
        neighborsDV.put(neiAddr, neighborDV);
        updateDVModal(neighborDV, fromIP, fromPort, toIP, cost);
    }

    /**
     * Update my DV forcely, no matter whether neighborDV has been changed or not.
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

    /**
     * This function is invoked when a router starts. It tells its neighbours about their mutual links.
     * @param toIP
     * @param toPort
     * @param cost
     */
    public void addCost(String toIP, int toPort, float cost){
        String addr = getAddress(toIP, toPort);
        neighbors.put(addr, new NeighborInfo(cost));
        updateMyDV();
        //linkUp(toIP, toPort);
        sendService.sendLinkUp(toIP, toPort);
        sendService.sendMyDV();
    }

    /**
     * Change costs for links between neighbours
     * @param toIP
     * @param toPort
     * @param cost
     * @return
     */
    public boolean changeCost(String toIP, int toPort, float cost){
        String addr = getAddress(toIP, toPort);
        if(neighbors.containsKey(addr) && neighbors.get(addr).isConnected){
            addCost(toIP, toPort, cost);
            return true;
        }
        return false;
    }

    /**
     * Check if the neighbours' DV has been changed. If not, then do not update my DV.
     * @param neighborDV
     * @param fromIP
     * @param fromPort
     * @param cost
     * @return
     */
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


    /**
     * Show route table
     */
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

    /**
     * show neighbours.
     */
    public synchronized void showNB() {
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

    /**
     * Handle the link up event in BF level
     * @param ip
     * @param port
     */
    public void linkUp(String ip, int port){
        String addr = getAddress(ip, port);
        if(!neighbors.containsKey(addr)) return;
        neighbors.get(addr).isConnected = true;
        neighbors.get(addr).updateTime();
        updateMyDV();
    }

    /**
     * Handle the link down event in BF level
     * @param ip
     * @param port
     */
    public void linkDown(String ip, int port){
        String addr = getAddress(ip, port);
        if(!neighbors.containsKey(addr)) return;
        neighbors.get(addr).isConnected = false;
        neighborsDV.remove(addr);
        updateMyDV();
    }

    /**
     * add proxy in BF level: check if the neighbour exists,
     * then invoke the setProxy function in sending level.
     * @param nbIP
     * @param nbPort
     * @param pyIP
     * @param pyPort
     */
    public void setProxy(String nbIP, int nbPort, String pyIP, int pyPort){
        String nbAddr = getAddress(nbIP, nbPort);
        if(!neighbors.containsKey(nbAddr)){
            System.out.println("Proxy failed: Neighbour does not existed.");
            return;
        }
        sendService.setProxy(nbIP, nbPort, pyIP, pyPort);
    }

    /**
     * remove the proxy in BF level: check if the nrighbour exists.
     * Then invoke the remove process in sending level.
     * @param nbIP
     * @param nbPort
     */
    public void rmProxy(String nbIP, int nbPort){
        String nbAddr = getAddress(nbIP, nbPort);
        if(!neighbors.containsKey(nbAddr)){
            System.out.println("Proxy failed: Neighbour does not existed.");
            return;
        }
        sendService.rmProxy(nbIP,nbPort);
    }
}
