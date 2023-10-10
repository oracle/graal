package com.oracle.graal.pointsto.reports.causality;

import java.lang.reflect.Field;

/**
 * As the HeapAssignmentTracingAgent has been stripped out for now, this class is a stub.
 */
public class HeapAssignmentTracing {
    private static final HeapAssignmentTracing instance = new HeapAssignmentTracing();

    public static HeapAssignmentTracing getInstance() {
        return instance;
    }

    public static boolean isActive() {
        return false;
    }

    public Object getResponsibleClass(Object imageHeapObject) {
        return null;
    }

    public Object getClassResponsibleForNonstaticFieldWrite(Object receiver, Field field, Object val) {
        return null;
    }

    public Object getClassResponsibleForStaticFieldWrite(Class<?> declaring, Field field, Object val) {
        return null;
    }

    public Object getClassResponsibleForArrayWrite(Object[] array, int index, Object val) {
        return null;
    }

    public Object getBuildTimeClinitResponsibleForBuildTimeClinit(Class<?> clazz) {
        return null;
    }

    public void setCause(Object cause, boolean recordHeapAssignments) {
    }

    public void dispose() {}
}
