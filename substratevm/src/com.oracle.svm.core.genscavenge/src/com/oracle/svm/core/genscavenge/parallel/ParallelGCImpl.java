package com.oracle.svm.core.genscavenge.parallel;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.heap.ParallelGC;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
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

    /// determine at runtime, max 32 (from busyWorkers)
    private static final int WORKERS_COUNT = 4;
    private static final int WORKERS_ALL   = 0b1111;
    private static final int WORKERS_NONE  = 0;

    /**
     * Each GC worker allocates memory in its own thread local chunk, entering mutex only when new chunk needs to be allocated.
     */
    private static final FastThreadLocalWord<AlignedHeapChunk.AlignedHeader> chunkTL =
            FastThreadLocalFactory.createWord("ParallelGCImpl.chunkTL");

    public static final VMMutex mutex = new VMMutex("ParallelGCImpl");
    private final VMCondition seqPhase = new VMCondition(mutex);
    private final VMCondition parPhase = new VMCondition(mutex);
    private final AtomicInteger busyWorkers = new AtomicInteger(0);

    private static final ThreadLocalTaskStack[] STACKS =
            IntStream.range(0, WORKERS_COUNT).mapToObj(i -> new ThreadLocalTaskStack()).toArray(ThreadLocalTaskStack[]::new);
    private static final ThreadLocal<ThreadLocalTaskStack> localStack = new ThreadLocal<>();
    private int currentStack;

    private volatile boolean inParallelPhase;

    @Override
    public void startWorkerThreadsImpl() {
        int hubOffset = ConfigurationValues.getObjectLayout().getHubOffset();
        VMError.guarantee(hubOffset == 0, "hub offset must be 0");

        IntStream.range(0, WORKERS_COUNT).forEach(this::startWorkerThread);
    }

    private void startWorkerThread(int n) {
        Thread t = new Thread(() -> {
                VMThreads.SafepointBehavior.markThreadAsCrashed();
                log().string("WW start ").unsigned(n).newline();

                final ThreadLocalTaskStack stack = STACKS[n];
                localStack.set(stack);
                getStats().install();
                while (true) {
                    do {
                        log().string("WW block ").unsigned(n).newline();
                        mutex.lock();
                        parPhase.block();
                        mutex.unlock();
                    } while ((busyWorkers.get() & (1 << n)) == 0);

                    log().string("WW run ").unsigned(n).string(", count=").unsigned(stack.size()).newline();
                    Object obj;
                    while ((obj = stack.pop()) != null) {
                        getVisitor().doVisitObject(obj);
                    }
                    killThreadLocalChunk();

                    int witness = busyWorkers.get();
                    int expected;
                    do {
                        expected = witness;
                        witness = busyWorkers.compareAndExchange(expected, expected & ~(1 << n));
                    } while (witness != expected);
                    if (busyWorkers.get() == WORKERS_NONE) {
                        seqPhase.signal();
                    }
                    log().string("WW idle ").unsigned(n).newline();
                }
        });
        t.setName("ParallelGCWorker-" + n);
        t.setDaemon(true);
        t.start();
    }

    private GreyToBlackObjectVisitor getVisitor() {
        return GCImpl.getGCImpl().getGreyToBlackObjectVisitor();
    }

    /**
     * To be invoked in sequential GC phase. Pushes object pointer to one of the workers' stacks
     * in round-robin fashion, attempting to balance load between workers.
     */
    public void push(Pointer ptr) {
        assert !isInParallelPhase();
        push(ptr, STACKS[currentStack]);
        currentStack = (currentStack + 1) % WORKERS_COUNT;
    }

    /**
     * To be invoked in parallel GC phase. Pushes object pointer to current worker's thread local stack.
     */
    public void pushToLocalStack(Pointer ptr) {
        assert isInParallelPhase();
        push(ptr, localStack.get());
    }

    private void push(Pointer ptr, ThreadLocalTaskStack stack) {
        if (!stack.push(ptr)) {
            getVisitor().doVisitObject(ptr.toObject());
        }
    }

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

    public static void killThreadLocalChunk() {
        chunkTL.set(WordFactory.nullPointer());
    }

    private void waitForIdleImpl() {
        inParallelPhase = true;

        log().string("PP start workers\n");
        busyWorkers.set(WORKERS_ALL);
        parPhase.broadcast();     // let worker threads run

        while (busyWorkers.get() != WORKERS_NONE) {
            mutex.lock();
            log().string("PP wait busy=").unsigned(busyWorkers.get()).newline();
            seqPhase.block();     // wait for them to become idle
            mutex.unlock();
        }

        inParallelPhase = false;
    }

    public static Stats getStats() {
        return Stats.stats();
    }

    static Log log() {
        return Log.noopLog();///
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
