package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.heap.ParallelGC;
import com.oracle.svm.core.jdk.Jvm;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.util.VMError;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class ParallelGCImpl extends ParallelGC {

    private static final int MAX_WORKERS_COUNT = 31;

    /**
     * Number of parallel GC workers. Default is 1 so that if GC happens before worker threads are started,
     * it occurs on a single thread just like serial GC.
     */
    private int workerCount = 1;
    private int allWorkersBusy;
    private final AtomicInteger busyWorkers = new AtomicInteger(0);

    /// create only as many as needed
    private static final ThreadLocalBuffer[] BUFFERS =
            IntStream.range(0, MAX_WORKERS_COUNT).mapToObj(i -> new ThreadLocalBuffer()).toArray(ThreadLocalBuffer[]::new);
    private static final FastThreadLocalObject<ThreadLocalBuffer> localBuffer =
            FastThreadLocalFactory.createObject(ThreadLocalBuffer.class, "ParallelGCImpl.bufferTL");
    private int currentBuffer;

    /**
     * Each GC worker allocates memory in its own thread local chunk, entering mutex only when new chunk needs to be allocated.
     */
    private static final FastThreadLocalWord<AlignedHeapChunk.AlignedHeader> chunkTL =
            FastThreadLocalFactory.createWord("ParallelGCImpl.chunkTL");

    public static final VMMutex mutex = new VMMutex("ParallelGCImpl");
    private final VMCondition seqPhase = new VMCondition(mutex);
    private final VMCondition parPhase = new VMCondition(mutex);

    private volatile boolean inParallelPhase;

    @Fold
    public static ParallelGCImpl singleton() {
        return (ParallelGCImpl) ImageSingletons.lookup(ParallelGC.class);
    }

    public static boolean isInParallelPhase() {
        return isSupported() && singleton().inParallelPhase;
    }

    public static void waitForIdle() {
        singleton().waitForIdleImpl();
    }

    public static AlignedHeapChunk.AlignedHeader getThreadLocalChunk() {
        return chunkTL.get();
    }

    public static void setThreadLocalChunk(AlignedHeapChunk.AlignedHeader chunk) {
        chunkTL.set(chunk);
    }

    /**
     * To be invoked in sequential GC phase. Pushes object pointer to one of the workers' buffers
     * in round-robin fashion, attempting to balance load between workers.
     */
    public void push(Pointer ptr) {
        assert !isInParallelPhase();
        BUFFERS[currentBuffer].push(ptr);
        currentBuffer = (currentBuffer + 1) % workerCount;
    }

    /**
     * To be invoked in parallel GC phase. Pushes object pointer to current worker's thread local buffer.
     */
    public void pushToLocalBuffer(Pointer ptr) {
        assert isInParallelPhase();
        localBuffer.get().push(ptr);
    }

    @Override
    public void startWorkerThreadsImpl() {
        int hubOffset = ConfigurationValues.getObjectLayout().getHubOffset();
        VMError.guarantee(hubOffset == 0, "hub offset must be 0");

        workerCount = getWorkerCount(); ///cmp serialGC vs pargc with 1 worker
        allWorkersBusy = ~(-1 << workerCount) & (-1 << 1);
        for (int i = 0; i < workerCount; i++) {
            BUFFERS[i].runtimeInit();
            if (i > 0) {
                // We reuse the gc thread for one of the workers, so number of worker threads is `workerCount - 1`
                startWorkerThread(i);
            }
        }
    }

    private int getWorkerCount() {
        int setting = ParallelGCOptions.ParallelGCWorkers.getValue();
        int workers = setting > 0 ? setting : getDefaultWorkerCount();
        workers = Math.min(workers, MAX_WORKERS_COUNT);
        verboseGCLog().string("[Number of ParallelGC workers: ").unsigned(workers).string("]").newline();
        return workers;
    }

    private int getDefaultWorkerCount() {
        // Adapted from Hotspot, see WorkerPolicy::nof_parallel_worker_threads()
        int cpus = Jvm.JVM_ActiveProcessorCount();
        return cpus <= 8 ? cpus : 8 + (cpus - 8) * 5 / 8;
    }

    private void startWorkerThread(int n) {
        Thread t = new Thread(() -> {
                VMThreads.SafepointBehavior.markThreadAsCrashed();
                debugLog().string("WW start ").unsigned(n).newline();

                final ThreadLocalBuffer buffer = BUFFERS[n];
                localBuffer.set(buffer);
                getStats().install();
                while (true) {
                    try {
                        do {
                            debugLog().string("WW block ").unsigned(n).newline();
                            mutex.lock();
                            parPhase.block();
                            mutex.unlock();
                        } while ((busyWorkers.get() & (1 << n)) == 0);

                        debugLog().string("WW run ").unsigned(n).newline();
                        drain(buffer);
                        int witness = busyWorkers.get();
                        int expected;
                        do {
                            expected = witness;
                            witness = busyWorkers.compareAndExchange(expected, expected & ~(1 << n));
                        } while (witness != expected);
                        if (busyWorkers.get() == 0) {
                            seqPhase.signal();
                        }
                        debugLog().string("WW idle ").unsigned(n).newline();
                    } catch (Throwable ex) {
                        VMError.shouldNotReachHere(ex);
                    }
                }
        });
        t.setName("ParallelGCWorker-" + n);
        t.setDaemon(true);
        t.start();
    }

    private void waitForIdleImpl() {
        inParallelPhase = true;

        debugLog().string("PP start workers\n");
        busyWorkers.set(allWorkersBusy);
        parPhase.broadcast();     // let worker threads run

        localBuffer.set(BUFFERS[0]);
        drain(BUFFERS[0]);

        while (busyWorkers.get() != 0) {
            mutex.lock();
            debugLog().string("PP wait busy=").unsigned(busyWorkers.get()).newline();
            seqPhase.block();     // wait for them to become idle
            mutex.unlock();
        }

        inParallelPhase = false;
    }

    private void drain(ThreadLocalBuffer buffer) {
        Object obj;
        while ((obj = buffer.pop()) != null) {
            getVisitor().doVisitObject(obj);
        }
        chunkTL.set(WordFactory.nullPointer());
    }

    private GreyToBlackObjectVisitor getVisitor() {
        return GCImpl.getGCImpl().getGreyToBlackObjectVisitor();
    }

    public static Stats getStats() {
        return Stats.stats();
    }

    private static Log verboseGCLog() {
        return SubstrateGCOptions.VerboseGC.getValue() ? Log.log() : Log.noopLog();
    }

    static Log debugLog() {///rm
        return Log.noopLog();
    }
}

@AutomaticFeature
class ParallelGCFeature implements Feature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ParallelGC.isSupported();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(ParallelGC.class, new ParallelGCImpl());
    }
}
