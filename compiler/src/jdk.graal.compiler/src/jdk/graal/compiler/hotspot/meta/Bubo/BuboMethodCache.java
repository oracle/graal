package jdk.graal.compiler.hotspot.meta.Bubo;

import java.util.HashMap;
import java.lang.Thread;

public class BuboMethodCache extends Thread  {

    public static String[] Buffer;
    public static int pointer;


    public BuboMethodCache() {
        Buffer = new String[10_000];
        pointer = 0;

    }

    public static void add(String method)
    {
        Buffer[pointer] = method;
        pointer++;
    }

    public static HashMap<Integer, String> getBuffer(){
        HashMap<Integer, String> buffer = new HashMap<Integer, String>();

        String[] idComponents = new String[2];
        for (int i = 0; i < pointer; i++) {
            if (Buffer[i] != null) {

                idComponents = Buffer[i].split(" ");
                // very hacky
                if (buffer.containsValue(idComponents[1])) {

                    //continue; // this is where its been recomped
                    idComponents[1] += idComponents[1] + "-Re-Comp"; // this is not ture, just ddid it for demo purposues 
                }
                buffer.put(Integer.parseInt(idComponents[0].split("-")[1]) , idComponents[1]);
            }
        }

        return buffer;

    }

}