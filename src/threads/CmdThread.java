package threads;

import service.BFService;
import service.FileService;
import service.SendService;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * This class listens to the user's input.
 * Created by szeyiu on 4/25/15.
 */
public class CmdThread implements Runnable {
    BFService bfService;
    SendService sendService;
    FileService fileService;
    public CmdThread(BFService bfService, SendService sendService, FileService fileService){
        this.bfService = bfService;
        this.sendService = sendService;
        this.fileService = fileService;
    }

    @Override
    public void run(){
        Scanner scanner = new Scanner(System.in);
        while(true){
            try {
                String line = scanner.nextLine();
                String[] cmds = line.split(" ");
                String cmd = cmds[0];
                if (cmd.toUpperCase().equals("LINKDOWN")) {//BUG HERE
                    linkDown(cmds);
                } else if (cmd.toUpperCase().equals("LINKUP")) {//BUG HERE
                    linkUp(cmds);
                } else if (cmd.toUpperCase().equals("SHOWRT")) {
                    showRT();
                } else if (cmd.toUpperCase().equals("CHANGECOST")) {
                    changeCost(cmds);
                } else if (cmd.toUpperCase().equals("SHOWNB")) {
                    showNB();
                } else if (cmd.toUpperCase().equals("TRANSFER")) {
                    try {
                        transfer(cmds);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else if (cmd.toUpperCase().equals("CLOSE")) {
                    close();
                } else if (cmd.toUpperCase().equals("ADDPROXY")) {
                    setProxy(cmds);
                } else if (cmd.toUpperCase().equals("REMOVEPROXY")) {
                    rmProxy(cmds);
                } else {
                    System.out.println("Unsupported cmd");
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void setProxy(String[] cmds){
        String pyIP = cmds[1];
        int pyPort = Integer.valueOf(cmds[2]);
        String nbIP = cmds[3];
        int nbPort = Integer.valueOf(cmds[4]);
        bfService.setProxy(nbIP, nbPort, pyIP, pyPort);
    }

    private void rmProxy(String[] cmds){
        String nbIP = cmds[1];
        int nbPort = Integer.valueOf(cmds[2]);
        bfService.rmProxy(nbIP, nbPort);
    }

    private void close(){
        System.exit(0);
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
        bfService.showNB();
    }

    private void changeCost(String[] cmds){
        String toIP = cmds[1];
        int toPort = Integer.valueOf(cmds[2]);
        float cost = Float.valueOf(cmds[3]);
        boolean handle = bfService.changeCost(toIP,toPort,cost);
        if(!handle){
            System.out.println("This link is not available.");
        } else {
            System.out.println("Cost changed!");
        }
    }

    private void transfer(String[] cmds) throws IOException, InterruptedException {
        String f = cmds[1];
        String desIP = cmds[2];
        int desPort = Integer.valueOf(cmds[3]);
        fileService.sendFile(desIP, desPort, f);
    }
}
