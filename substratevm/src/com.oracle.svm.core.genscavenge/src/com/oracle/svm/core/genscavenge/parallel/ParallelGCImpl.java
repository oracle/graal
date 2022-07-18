package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.heap.ParallelGC;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMThreads;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class ParallelGCImpl extends ParallelGC {

    /// static -> ImageSingletons
    public static final int WORKERS_COUNT = 4;

    private static final VMMutex mutex = new VMMutex("pargc");
    private static final VMCondition cond = new VMCondition(mutex);
    private static AtomicInteger busy = new AtomicInteger(0);

    private static final CasQueue QUEUE = new CasQueue();
    private static final CasQueue.Consumer PROMOTE_TASK =
            obj -> getVisitor().doVisitObject(obj);

    private static volatile boolean enabled;
    private static volatile Throwable throwable;

    @Override
    public void startWorkerThreads() {
        IntStream.range(0, WORKERS_COUNT).forEach(this::startWorkerThread);
    }

    public void startWorkerThread(int n) {
        Thread t = new Thread(() -> {
//                VMThreads.ParallelGCSupport.setParallelGCThread();
                VMThreads.SafepointBehavior.markThreadAsCrashed();
                log().string("WW start ").unsigned(n).newline();
                getStats().install();
                try {
                    while (!stopped) {
                        mutex.lock();
                        while (busy.get() < WORKERS_COUNT) {
                            log().string("WW block ").unsigned(n).newline();
                            cond.block();
                        }
                        mutex.unlock();

                        QUEUE.drain(PROMOTE_TASK);
                        if (busy.decrementAndGet() <= 0) {
                            cond.broadcast();
                        }
                        log().string("WW idle ").unsigned(n).newline();
                    }
                } catch (Throwable e) {
                    throwable = e;
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
        if (!QUEUE.put(ptr)) {
            PROMOTE_TASK.accept(ptr.toObject());
        }
    }

    public static void waitForIdle() {
        log().string("PP start workers\n");
        busy.set(WORKERS_COUNT);
        cond.broadcast();     // let worker threads run

        mutex.lock();
        while (busy.get() > 0) {
            log().string("PP wait busy=").unsigned(busy.get()).newline();
            cond.block();     // wait for them to become idle
        }
        mutex.unlock();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        ParallelGCImpl.enabled = enabled;
    }

    public static void checkThrowable() {
        if (throwable != null) {
            Log.log().string("PGC error : ").string(throwable.getClass().getName())
                    .string(" : ").string(throwable.getMessage()).newline();
            throwable.printStackTrace();
            throw new Error(throwable);
        }
    }

    public static Stats getStats() {
        return Stats.stats();
    }

    static Log log() {
        return Log.noopLog();
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
