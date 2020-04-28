package com.oracle.svm.core.genscavenge;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanNotificationInfo;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import com.oracle.svm.core.util.TimeUtils;
import com.sun.management.GcInfo;

import sun.management.Util;

final class GCManagementFactory {

    private final List<GarbageCollectorMXBean> gcBeanList;

    GCManagementFactory() {
        final List<GarbageCollectorMXBean> newList = new ArrayList<>();
        /* Changing the order of this list will break assumptions we take in the object replacer. */
        newList.add(new IncrementalGarbageCollectorMXBean());
        newList.add(new CompleteGarbageCollectorMXBean());
        gcBeanList = newList;
    }

    List<GarbageCollectorMXBean> getGCBeanList() {
        return gcBeanList;
    }

    private static final class IncrementalGarbageCollectorMXBean implements com.sun.management.GarbageCollectorMXBean, NotificationEmitter {

        private IncrementalGarbageCollectorMXBean() {
        }

        @Override
        public long getCollectionCount() {
            return HeapImpl.getHeapImpl().getGCImpl().getAccounting().getIncrementalCollectionCount();
        }

        @Override
        public long getCollectionTime() {
            final long nanos = HeapImpl.getHeapImpl().getGCImpl().getAccounting().getIncrementalCollectionTotalNanos();
            return TimeUtils.roundNanosToMillis(nanos);
        }

        @Override
        public String[] getMemoryPoolNames() {
            /* Return a new array each time because arrays are not immutable. */
            return new String[]{"young generation space"};
        }

        @Override
        public String getName() {
            /* Changing this name will break assumptions we take in the object replacer. */
            return "young generation scavenger";
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public ObjectName getObjectName() {
            return Util.newObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE, getName());
        }

        @Override
        public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
        }

        @Override
        public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
        }

        @Override
        public void removeNotificationListener(NotificationListener listener) {
        }

        @Override
        public MBeanNotificationInfo[] getNotificationInfo() {
            return new MBeanNotificationInfo[0];
        }

        @Override
        public GcInfo getLastGcInfo() {
            return null;
        }
    }

    private static final class CompleteGarbageCollectorMXBean implements com.sun.management.GarbageCollectorMXBean, NotificationEmitter {

        private CompleteGarbageCollectorMXBean() {
        }

        @Override
        public long getCollectionCount() {
            return HeapImpl.getHeapImpl().getGCImpl().getAccounting().getCompleteCollectionCount();
        }

        @Override
        public long getCollectionTime() {
            final long nanos = HeapImpl.getHeapImpl().getGCImpl().getAccounting().getCompleteCollectionTotalNanos();
            return TimeUtils.roundNanosToMillis(nanos);
        }

        @Override
        public String[] getMemoryPoolNames() {
            /* Return a new array each time because arrays are not immutable. */
            return new String[]{"young generation space", "old generation space"};
        }

        @Override
        public String getName() {
            /* Changing this name will break assumptions we take in the object replacer. */
            return "complete scavenger";
        }

        @Override
        public boolean isValid() {
            return true;
        }

        @Override
        public ObjectName getObjectName() {
            return Util.newObjectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE, getName());
        }

        @Override
        public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
        }

        @Override
        public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
        }

        @Override
        public void removeNotificationListener(NotificationListener listener) {
        }

        @Override
        public MBeanNotificationInfo[] getNotificationInfo() {
            return new MBeanNotificationInfo[0];
        }

        @Override
        public GcInfo getLastGcInfo() {
            return null;
        }
    }
}
