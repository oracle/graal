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

    /// determine at runtime?
    private static final int WORKERS_COUNT = 4;

    /**
     * Each GC worker allocates memory in its own thread local chunk, entering mutex only when new chunk needs to be allocated.
     */
    private static final FastThreadLocalWord<AlignedHeapChunk.AlignedHeader> chunkTL =
            FastThreadLocalFactory.createWord("ParallelGCImpl.chunkTL");

    public static final VMMutex mutex = new VMMutex("ParallelGCImpl");
    private final VMCondition cond = new VMCondition(mutex);
    private final AtomicInteger busy = new AtomicInteger(0);

    private static final ThreadLocalTaskStack[] STACKS =
            IntStream.range(0, WORKERS_COUNT).mapToObj(i -> new ThreadLocalTaskStack()).toArray(ThreadLocalTaskStack[]::new);
    private static final ThreadLocal<ThreadLocalTaskStack> localStack = new ThreadLocal<>();
    private int currentStack;

    private volatile boolean inParallelPhase;

    @Override
    public void startWorkerThreads() {
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
                    mutex.lock();
                    while (busy.get() < WORKERS_COUNT) {
                        log().string("WW block ").unsigned(n).newline();
                        cond.block();
                    }
                    mutex.unlock();

                    log().string("WW run ").unsigned(n).string(", count=").unsigned(stack.size()).newline();
                    Object obj;
                    while ((obj = stack.pop()) != null) {
                        getVisitor().doVisitObject(obj);
                    }
                    killThreadLocalChunk();
                    if (busy.decrementAndGet() <= 0) {
                        cond.broadcast();
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

    /// name, explain
    public void push(Pointer ptr) {
        assert !isInParallelPhase();
        push(ptr, STACKS[currentStack]);
        currentStack = (currentStack + 1) % WORKERS_COUNT;
    }

    public void pushLocally(Pointer ptr) {
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
        assert isSupported();
        return singleton().inParallelPhase;
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
        setInParallelPhase(true);

        log().string("PP start workers\n");
        busy.set(WORKERS_COUNT);
        cond.broadcast();     // let worker threads run

        mutex.lock();
        while (busy.get() > 0) {
            log().string("PP wait busy=").unsigned(busy.get()).newline();
            cond.block();     // wait for them to become idle
        }
        mutex.unlock();

        setInParallelPhase(false);
    }

    private void setInParallelPhase(boolean inParallelPhase) {
        assert isSupported();
        singleton().inParallelPhase = inParallelPhase;
    }

    public static Stats getStats() {
        return Stats.stats();
    }

    static Log log() {
        return Log.log();///
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
