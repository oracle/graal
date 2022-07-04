//package com.oracle.svm.core.monitor;
//
//import java.util.ArrayList;
//import java.util.concurrent.locks.AbstractQueuedSynchronizer;
//
//@com.oracle.svm.core.annotate.TargetClass(value = java.util.concurrent.locks.AbstractQueuedSynchronizer.class, onlyWith = com.oracle.svm.core.jfr.HasJfrSupport.class, innerClass = "Node")
//public final class Target_java_util_concurrent_locks_AbstractQueuedSynchronizer_Node {
//    @com.oracle.svm.core.annotate.Inject
////    @com.oracle.svm.core.annotate.RecomputeFieldValue(kind = com.oracle.svm.core.annotate.RecomputeFieldValue.Kind.NewInstance)//
//    public long notifierId;
//
//    @com.oracle.svm.core.annotate.Alias
//    native boolean compareAndSetWaitStatus(int expect, int update);
//
//    @com.oracle.svm.core.annotate.Alias
//    static final int CONDITION = -2; //is this correct?
//    @com.oracle.svm.core.annotate.Alias
//    static final int SIGNAL    = -1;
//
//    @com.oracle.svm.core.annotate.Alias
//    volatile int waitStatus;
//
//    @com.oracle.svm.core.annotate.Alias
//    volatile Thread thread;
//}