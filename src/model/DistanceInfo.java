package model;

/**
 * Created by szeyiu on 4/25/15.
 */
public class DistanceInfo {
    public float cost;
    public String firstHop;

    public DistanceInfo(float cost, String firstHop) {
        this.cost = cost;
        this.firstHop = firstHop;
    }
}
