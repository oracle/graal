package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.heap.ParallelGC;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.UnsignedWord;

public class ParallelGCImpl extends ParallelGC {

    public final TaskQueue QUEUE = new TaskQueue("pargc");

    @Override
    public void startWorkerThreads() {
        Log trace = Log.log();
        Thread t = new Thread() {
            @Override
            @Uninterruptible(reason = "Trying to ignore safepoints", calleeMustBe = false)
            public void run() {
                VMThreads.ParallelGCSupport.setParallelGCThread();
                try {
                    while (!stopped) {
                        UnsignedWord token = QUEUE.get();
                        trace.string("  got token ").unsigned(token).string(" on ").string(Thread.currentThread().getName()).newline();
                    }
                } catch (Throwable e) {
                    VMError.shouldNotReachHere(e.getClass().getName());
                }
            }
        };
        t.setName("ParallelGC");
        t.setDaemon(true);
        t.start();
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
