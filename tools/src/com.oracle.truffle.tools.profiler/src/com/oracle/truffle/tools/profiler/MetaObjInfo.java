package com.oracle.truffle.tools.profiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TODO
 */
public class MetaObjInfo {

    private long allocatedInstances;
    private long liveInstances;
    private long bytes;
    private long liveBytes;
    final String name;
    final String language;

    MetaObjInfo(String l, String n) {
        language = l;
        name = n;
    }

    /**
     * TODO
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * TODO
     * @return
     */
    public String getLanguage() {
        return language;
    }

    /**
     * TODO
     * @return
     */
    public long getAllocatedInstancesCount() {
        return allocatedInstances;
    }

    /**
     * TODO
     * @return
     */
    public long getLiveInstancesCount() {
        return liveInstances;
    }

    /**
     * TODO
     * @return
     */
    public long getBytes() {
        return bytes;
    }

    /**
     * TODO
     * @return
     */
    public long getLiveBytes() {
        return liveBytes;
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MetaObjInfo) {
            MetaObjInfo info = (MetaObjInfo) obj;
            return getName().equals(info.getName()) && getLanguage().equals(info.getLanguage());
        }
        return false;
    }

    private static Map<String, Object>[] toMap(MetaObjInfo[] heap) {
        List<Map<String, Object>> heapHisto = new ArrayList<>(heap.length);
        for (MetaObjInfo mi : heap) {
            Map<String, Object> metaObjMap = new HashMap<>();
            metaObjMap.put("language", mi.getLanguage());
            metaObjMap.put("name", mi.getName());
            metaObjMap.put("allocatedInstancesCount", mi.getAllocatedInstancesCount());
            metaObjMap.put("bytes", mi.getBytes());
            metaObjMap.put("liveInstancesCount", mi.getLiveInstancesCount());
            metaObjMap.put("liveBytes", mi.getLiveBytes());
            heapHisto.add(metaObjMap);
        }
        return heapHisto.toArray(new Map[0]);
    }

    void addInstanceWithSize(long addedSize) {
        allocatedInstances++;
        liveInstances++;
        bytes += addedSize;
        liveBytes += addedSize;
    }

    void removeInstanceWithSize(long oldSize) {
        allocatedInstances--;
        liveInstances--;
        bytes -= oldSize;
        liveBytes -= oldSize;
    }

    void gcInstanceWithSize(long size) {
        liveInstances--;
        liveBytes -= size;
    }
}
