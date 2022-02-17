package com.oracle.svm.core.heap;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import sun.misc.Signal;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

public class ParallelGC {

    private final VMMutex mutex = new VMMutex("pargc");
    private final VMCondition cond = new VMCondition(mutex);

    private Task task = new Task();
    private volatile boolean idle = true;

    public void setTask(Pointer src, Pointer dst, UnsignedWord size) {
        mutex.lock();
        task.src = src;
        task.dst = dst;
        task.size = size;
        mutex.unlock();

        idle = false;
        cond.signal();
        Log.log().string("setTask() on ").string(Thread.currentThread().getName()).newline();
    }

    public boolean isIdle() {
        return idle;
    }

    public void _startThreads() {
        Log trace = Log.log();
        Thread t = new Thread(() -> {
            trace.string("  started thread ").string(Thread.currentThread().getName()).newline();
            while (true) {
                mutex.lock();
                while (idle) {
                    trace.string("  blocking on ").string(Thread.currentThread().getName()).newline();
                    cond.block();
                    trace.string("  unblocked on ").string(Thread.currentThread().getName()).newline();
                }
                trace.string("  copying...").newline();
//                UnmanagedMemoryUtil.copyLongsForward(task.src, task.dst, task.size);
                mutex.unlock();
                idle = true;
                trace.string("  done").newline();
            }
        });
        t.setName("ParallelGC");
        t.setDaemon(true);
        trace.string("starting thread...").newline();
        t.start();
    }
    
    static class Task {
        Pointer src, dst;
        UnsignedWord size;
    }

    private volatile boolean notified;
    private volatile boolean stopped;

//    public static Thread parThread;

    public void __startThreads() {
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
                    VMError.shouldNotReachHere("No exception must by thrown in the JFR recorder thread as this could break file IO operations.");
                }
            }
        };
        t.setName("ParallelGC-noop");
        t.setDaemon(true);
//        parThread = t;
        t.start();
    }

    private void waitForNotification() {
        Log trace = Log.log();
        trace.string("  blocking on ").string(Thread.currentThread().getName()).newline();
        while (!notified);
        trace.string("  unblocking ").string(Thread.currentThread().getName()).newline();
        notified = false;
    }

    public void signal() {
        Log.log().string("signaling on ").string(Thread.currentThread().getName()).newline();
        notified = true;
    }

    public void startThreads() {
        Log trace = Log.log();
        Thread t = new Thread() {
            @Override
            public void run() {
                VMThreads.ParallelGCSupport.setParallelGCThread();
                try {
                    while (!stopped) {
                        waitForNotification();
                    }
                } catch (Throwable e) {
                    VMError.shouldNotReachHere("No exception must by thrown in the JFR recorder thread as this could break file IO operations.");
                }
            }
        };
        t.setName("ParallelGC-noop");
        t.setDaemon(true);
        t.start();
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
