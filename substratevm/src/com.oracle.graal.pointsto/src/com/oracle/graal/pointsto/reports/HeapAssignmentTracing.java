package com.oracle.graal.pointsto.reports;

import java.lang.reflect.Field;

public class HeapAssignmentTracing {
    private static final HeapAssignmentTracing instance;

    static {
        HeapAssignmentTracing impl = new NativeImpl();
        try {
            // Try to invoke native method
            impl.getResponsibleClass(new Object());
        } catch (UnsatisfiedLinkError error) {
            // JVMTI agent is not loaded
            impl = new HeapAssignmentTracing();
        }
        instance = impl;
    }

    public static HeapAssignmentTracing getInstance() {
        return instance;
    }

    public static boolean isActive() {
        return instance instanceof NativeImpl;
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

    private static final class NativeImpl extends HeapAssignmentTracing {
        @Override
        public native Object getResponsibleClass(Object imageHeapObject);

        @Override
        public native Object getClassResponsibleForNonstaticFieldWrite(Object receiver, Field field, Object val);

        @Override
        public native Object getClassResponsibleForStaticFieldWrite(Class<?> declaring, Field field, Object val);

        @Override
        public native Object getClassResponsibleForArrayWrite(Object[] array, int index, Object val);

        @Override
        public native Object getBuildTimeClinitResponsibleForBuildTimeClinit(Class<?> clazz);

        @Override
        public native void setCause(Object cause, boolean recordHeapAssignments);

        @Override
        public native void dispose();
    }
}
