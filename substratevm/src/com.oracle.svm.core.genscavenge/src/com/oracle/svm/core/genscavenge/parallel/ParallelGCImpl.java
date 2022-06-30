package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
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

    private static final TaskQueue QUEUE = new TaskQueue("pargc-queue");
    private static final TaskQueue.Consumer PROMOTE_TASK =
            obj -> getVisitor().doVisitObject(obj);

    private static boolean enabled;

    @Override
    public void startWorkerThreads() {
        IntStream.range(0, WORKERS_COUNT).forEach(this::startWorkerThread);
    }

    public void startWorkerThread(int n) {
        final Log trace = Log.log();
        trace.string("PP start worker-").unsigned(n).newline();
        Thread t = new Thread(() -> {
//                VMThreads.ParallelGCSupport.setParallelGCThread();
                VMThreads.SafepointBehavior.markThreadAsCrashed();
                try {
                    while (!stopped) {
                        QUEUE.consume(PROMOTE_TASK);
                    }
                } catch (Throwable e) {
                    VMError.shouldNotReachHere(e.getClass().getName());
                }
        });
        t.setName("ParallelGCWorker-" + n);
        t.setDaemon(true);
        t.start();
    }

    public static GreyToBlackObjectVisitor getVisitor() {
        return GCImpl.getGCImpl().getGreyToBlackObjectVisitor();
    }

    public static void queue(Pointer ptr) {
        QUEUE.put(ptr);
    }

    public static void waitForIdle() {
        if (WORKERS_COUNT > 0) {
            QUEUE.waitUntilIdle(WORKERS_COUNT);
        } else {
            QUEUE.drain(PROMOTE_TASK); // execute synchronously
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        ParallelGCImpl.enabled = enabled;
    }

    public static TaskQueue.Stats getStats() {
        return QUEUE.stats;
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
