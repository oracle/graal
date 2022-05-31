package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.heap.ParallelGC;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import java.util.function.BiConsumer;
import java.util.stream.IntStream;

public class ParallelGCImpl extends ParallelGC {

    /// static -> ImageSingletons
    public static final int WORKERS_COUNT = 2;
    public static final ObjectQueue QUEUE = new ObjectQueue("pargc");

    @Override
    public void startWorkerThreads() {
        IntStream.range(0, WORKERS_COUNT).forEach(this::startWorkerThread);
    }

    public void startWorkerThread(int n) {
        final Log trace = Log.log();
        final BiConsumer<Object, Object> releaseChunks = (s, cr) -> {
            Space space = (Space) s;
            GCImpl.ChunkReleaser chunkReleaser = (GCImpl.ChunkReleaser) cr;
//                        trace.string("  got space ").object(space)
//                                .string(", chunkReleaser ").object(chunkReleaser)
//                                .string(" on PGCWorker-").unsigned(n).newline();
            space.releaseChunks(chunkReleaser);
//            space.setInd(n);
        };
        Thread t = new Thread() {
            @Override
            public void run() {
//                VMThreads.ParallelGCSupport.setParallelGCThread();
                VMThreads.SafepointBehavior.markThreadAsCrashed();
                try {
                    while (!stopped) {
                        QUEUE.consume(releaseChunks);
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
