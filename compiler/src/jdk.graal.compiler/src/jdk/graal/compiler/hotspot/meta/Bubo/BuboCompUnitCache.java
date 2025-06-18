package jdk.graal.compiler.hotspot.meta.Bubo;

import java.util.HashMap;
import java.util.List;
import java.lang.Thread;

public class BuboCompUnitCache extends Thread {

    public static HashMap<Integer, List<CompUnitInfo>> Buffer;

    public BuboCompUnitCache() {
        Buffer = new HashMap<>();
    }

    public static void add(Integer ID, List<CompUnitInfo> Info) {
        Buffer.put(ID, Info);
    }
}