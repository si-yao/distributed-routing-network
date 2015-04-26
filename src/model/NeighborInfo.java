package model;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by szeyiu on 4/25/15.
 */
public class NeighborInfo {
    public float cost;
    public boolean isConnected;
    public Date time;

    public NeighborInfo(float cost) {
        this.cost = cost;
        isConnected = true;
        updateTime();
    }

    public void updateTime() {
        time = Calendar.getInstance().getTime();
    }
}
