package service;

import model.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by szeyiu on 4/25/15.
 */
public class BFService {
    private String myIP;//actually I don't need to know my IP, because UDP contains it.
    private int myPort;//I must know my port, because UDP does not contain it, and I need to indicate when send msg to neighbours.
    private String myAddress;//unnecessary, only need to know port.
    private HashMap<String, NeighborInfo> neighbors;
    private HashMap<String, DistanceInfo> distanceVectors;


    public static String getAddress(String ip, int port) {
        return ip + ":" + port;
    }
    public synchronized NeighborInfo addNeighbor(String ip, int port, float cost) {

        if(ip.equals("localhost")) {
            ip = "127.0.0.1";
        }

        String address = getAddress(ip, port);
        NeighborInfo item = new NeighborInfo(cost);
        if(!neighbors.containsKey(address)) {
            neighbors.put(address, item);
        }

        addPathInDV(address, cost);

        return item;
    }
    public synchronized void addPathInDV(String address, float cost) {
        if(!distanceVectors.containsKey(address)) {
            DistanceInfo newDis = new DistanceInfo(cost, address);
            distanceVectors.put(address, newDis);
        }
        else {
            float currentCost = distanceVectors.get(address).cost;
            if(cost < currentCost) {
                distanceVectors.get(address).firstHop = address;
                distanceVectors.get(address).cost = cost;
            }
        }
    }
    private synchronized NeighborInfo findNeighbor(String ip, int port) {
        String address = getAddress(ip, port);
        if(neighbors.containsKey(address)) {
            return neighbors.get(address);
        }
        return null;
    }

    /**
     * update my DV according to the received DV of neighbour.
     * @param vectors vector is the DV received from a neighbour.
     * @param fromIP the neighbour's ip
     * @param fromPort the neighbour's port
     * @param toIP my ip, need it in case.
     * @param cost the cost between me and this neighbour.
     */
    private synchronized void update(HashMap<String, DistanceInfo> vectors, String fromIP, int fromPort, String toIP, float cost) {
        //System.out.println("getUpdate");
        NeighborInfo nei = findNeighbor(fromIP, fromPort);

        if(myIP.isEmpty()) {
            myIP = toIP;
            myAddress = getAddress(myIP, myPort);
        }

        if(nei == null) {
            nei = addNeighbor(fromIP, fromPort, cost);
        }

        nei.updateTime();
        nei.status = true;
        for(Map.Entry<String, DistanceInfo> entry : vectors.entrySet()) {
            if(entry.getKey().equals(myAddress) || entry.getValue().firstHop.equals(myAddress))
                continue;

            if(distanceVectors.containsKey(entry.getKey())) {
                if(distanceVectors.get(entry.getKey()).firstHop.equals(getAddress(fromIP, fromPort))) {
                    distanceVectors.get(entry.getKey()).cost = nei.cost+entry.getValue().cost;
                }
                else if(nei.cost+entry.getValue().cost < distanceVectors.get(entry.getKey()).cost) {
                    distanceVectors.get(entry.getKey()).cost = nei.cost+entry.getValue().cost;
                    distanceVectors.get(entry.getKey()).firstHop = getAddress(fromIP, fromPort);
                }
            }
            else {
                DistanceInfo newDis = new DistanceInfo(nei.cost+entry.getValue().cost, getAddress(fromIP, fromPort));
                distanceVectors.put(entry.getKey(), newDis);
            }
        }

        Iterator<String> it = distanceVectors.keySet().iterator();

        while(it.hasNext()) {
            String addr = it.next();
            DistanceInfo item = distanceVectors.get(addr);
            if(item.firstHop.equals(getAddress(fromIP, fromPort)) && !addr.equals(getAddress(fromIP, fromPort))) {
                if(!vectors.containsKey(addr)) {
                    it.remove();
                }
            }
        }

        for(Map.Entry<String, NeighborInfo> entry : neighbors.entrySet()) {
            if(!entry.getValue().status)
                continue;

            if(!distanceVectors.containsKey(entry.getKey())) {
                float currentCost = entry.getValue().cost;
                addPathInDV(entry.getKey(), currentCost);
            }
            else {
                float currentCost = distanceVectors.get(entry.getKey()).cost;
                if(entry.getValue().cost < currentCost) {
                    distanceVectors.get(entry.getKey()).cost = entry.getValue().cost;
                    distanceVectors.get(entry.getKey()).firstHop = entry.getKey();
                }
            }
        }
    }

}
