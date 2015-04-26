package service;

import java.util.Scanner;

/**
 * Created by szeyiu on 4/25/15.
 */
public class CmdService implements Runnable {
    BFService bfService;

    public CmdService(BFService bfService){
        this.bfService = bfService;
    }

    @Override
    public void run(){
        Scanner scanner = new Scanner(System.in);
        while(true){
            String line = scanner.nextLine();
            String[] cmds = line.split(" ");
            String cmd = cmds[0];
            if(cmd.equals("LINKDOWN")){
                linkDown(cmds);
            } else if(cmd.equals("LINKUP")){
                linkUp(cmds);
            } else if(cmd.equals("SHOWRT")){
                showRT();
            } else {
                System.out.println("Unsupported cmd");
            }
        }
    }

    //TODO
    private void linkDown(String[] cmds){
        return;
    }

    //TODO
    private void linkUp(String[] cmds){
        return;
    }

    private void showRT(){
        bfService.showRT();
    }
}
