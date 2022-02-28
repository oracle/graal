package com.oracle.svm.core.heap;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

public class ParallelGC {

    private final VMMutex mutex = new VMMutex("pargc");
    private final VMCondition cond = new VMCondition(mutex);

    private volatile boolean notified;
    private volatile boolean stopped;

    public void startWorkerThreads() {
        Log trace = Log.log();
        Thread t = new Thread() {
            @Override
            @Uninterruptible(reason = "Trying to ignore safepoints", calleeMustBe = false)
            public void run() {
                VMThreads.ParallelGCSupport.setParallelGCThread();
                try {
                    while (!stopped) {
                        waitForNotification();
                    }
                } catch (Throwable e) {
                    VMError.shouldNotReachHere(e.getClass().getName());
                }
            }
        };
        t.setName("ParallelGC-noop");
        t.setDaemon(true);
        t.start();
    }

    private void waitForNotification() {
        Log trace = Log.log();
        mutex.lock();
        try {
            while (!notified) {
                trace.string("  blocking on ").string(Thread.currentThread().getName()).newline();
                cond.block();
                trace.string("  unblocking ").string(Thread.currentThread().getName()).newline();
            }
            notified = false;
        } finally {
            mutex.unlock();
        }
    }

    public void signal() {
        mutex.lock();
        try {
            Log.log().string("signaling on ").string(Thread.currentThread().getName()).newline();
            notified = true;
            cond.broadcast();
        } finally {
            mutex.unlock();
        }
    }

    public static void thawWorkerThreads() {
        Safepoint.Master.singleton().releaseParallelGCSafepoints();
    }
}

@AutomaticFeature
class ParallelGCFeature implements Feature {
//    @Override
//    public boolean isInConfiguration(IsInConfigurationAccess access) {
//        return SubstrateOptions.UseSerialGC.getValue();
//    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ParallelGC.class, new ParallelGC());
    }
}
