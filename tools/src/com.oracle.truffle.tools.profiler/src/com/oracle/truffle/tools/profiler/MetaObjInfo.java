package com.oracle.truffle.tools.profiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;

/**
 * Contains information about all allocated instances that share a
 * {@link TruffleInstrument.Env#findMetaObject(LanguageInfo, Object) meta object}.
 *
 * @since 1.0
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
     * The name of the meta object is a {@link TruffleInstrument.Env#toString(LanguageInfo, Object)
     * language specific} representation of it or the string {@code "null"} if none is provided by
     * the language.
     *
     * @return the name of the meta object.
     */
    public String getName() {
        return name;
    }

    /**
     * @return name of the language from which the meta object hails from.
     */
    public String getLanguage() {
        return language;
    }

    /**
     * @return Total number of allocated instances associated with this meta object.
     */
    public long getAllocatedInstancesCount() {
        return allocatedInstances;
    }

    /**
     * @return Live (i.e. not garbage-collected) number of allocated instances associated with this
     *         meta object.
     */
    public long getLiveInstancesCount() {
        return liveInstances;
    }

    /**
     * @return Size (in bytes) of all allocated instances associated with this meta object.
     */
    public long getBytes() {
        return bytes;
    }

    /**
     * @return Size (in bytes) of live (i.e. not garbage-collected) instances associated with this
     *         mete object.
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
