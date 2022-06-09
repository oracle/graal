package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.heap.ParallelGC;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;

import java.util.stream.IntStream;

public class ParallelGCImpl extends ParallelGC {

    /// static -> ImageSingletons
    public static final int WORKERS_COUNT = 4;
    public static final TaskQueue QUEUE = new TaskQueue("pargc-queue");

    public static final TaskQueue.Consumer PROMOTE_TASK =
            (Pointer originalPtr, Pointer copyPtr, Pointer objRef, boolean compressed, Object holderObject) -> {
                GCImpl.getGCImpl().doPromoteParallel(originalPtr, copyPtr, objRef, 0, compressed, holderObject);
            };

    private static boolean enabled;

    @Override
    public void startWorkerThreads() {
        IntStream.range(0, WORKERS_COUNT).forEach(this::startWorkerThread);
    }

    public void startWorkerThread(int n) {
        final Log trace = Log.log();
        trace.string("PP start worker-").unsigned(n).newline();
//        final TaskQueue.Consumer promoteTask = (Object original, Pointer objRef, boolean compressed, Object holderObject) -> {
//            trace.string(">> promote on worker-").unsigned(n).newline();///
//            if (original == null || holderObject == null) {
//                trace.string("PP orig=").object(original).string(", holder=").object(holderObject).newline();
//            }
//            GCImpl.getGCImpl().doPromoteParallel(original, objRef, 0, compressed, holderObject);
//        };
        Thread t = new Thread() {
            @Override
            public void run() {
//                VMThreads.ParallelGCSupport.setParallelGCThread();
                VMThreads.SafepointBehavior.markThreadAsCrashed();
                try {
                    while (!stopped) {
                        QUEUE.consume(PROMOTE_TASK);
                    }
                } catch (Throwable e) {
                    VMError.shouldNotReachHere(e.getClass().getName());
                }
            }
        };
        t.setName("ParallelGCWorker-" + n);
        t.setDaemon(true);
        t.start();
    }

    public static void waitForIdle() {
        QUEUE.waitUntilIdle(WORKERS_COUNT);
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        ParallelGCImpl.enabled = enabled;
    }
}

@AutomaticFeature
class ParallelGCFeature implements Feature {
///
//    @Override
//    public boolean isInConfiguration(IsInConfigurationAccess access) {
//        return SubstrateOptions.UseSerialGC.getValue();
//    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ParallelGC.class, new ParallelGCImpl());
    }
}
