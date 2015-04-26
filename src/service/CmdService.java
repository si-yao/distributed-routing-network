package service;

import java.util.Scanner;

/**
 * Created by szeyiu on 4/25/15.
 */
public class CmdService implements Runnable {
    BFService bfService;
    SendService sendService;
    public CmdService(BFService bfService, SendService sendService){
        this.bfService = bfService;
        this.sendService = sendService;
    }

    @Override
    public void run(){
        Scanner scanner = new Scanner(System.in);
        while(true){
            String line = scanner.nextLine();
            String[] cmds = line.split(" ");
            String cmd = cmds[0];
            if(cmd.toUpperCase().equals("LINKDOWN")){//BUG HERE
                linkDown(cmds);
            } else if(cmd.toUpperCase().equals("LINKUP")){//BUG HERE
                linkUp(cmds);
            } else if(cmd.toUpperCase().equals("SHOWRT")){
                showRT();
            } else if(cmd.toUpperCase().equals("CHANGECOST")){
                changeCost(cmds);
            } else if(cmd.toUpperCase().equals("SHOWNB")){
                showNB();
            }
            else {
                System.out.println("Unsupported cmd");
            }
        }
    }

    private void linkDown(String[] cmds){
        String toIP = cmds[1];
        int toPort = Integer.valueOf(cmds[2]);
        sendService.sendLinkDown(toIP, toPort);
        bfService.linkDown(toIP, toPort);
        System.out.println("Link is down...");
    }

    private void linkUp(String[] cmds){
        String toIP = cmds[1];
        int toPort = Integer.valueOf(cmds[2]);
        sendService.sendLinkUp(toIP, toPort);
        bfService.linkUp(toIP, toPort);
        System.out.println("Link is up...");
    }

    private synchronized void showRT(){
        bfService.showRT();
    }

    private synchronized void showNB(){
        bfService.showNeighbor();
    }

    private void changeCost(String[] cmds){
        String toIP = cmds[1];
        int toPort = Integer.valueOf(cmds[2]);
        float cost = Float.valueOf(cmds[3]);
        boolean handle = bfService.changeCost(toIP,toPort,cost);
        if(!handle){
            System.out.println("This link is not available.");
        }
    }
}