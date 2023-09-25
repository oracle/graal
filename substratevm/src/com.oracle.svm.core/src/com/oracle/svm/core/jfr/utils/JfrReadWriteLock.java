package com.oracle.svm.core.jfr.utils;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import org.graalvm.compiler.nodes.PauseNode;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.jfr.SubstrateJVM;

/** An uninterruptible read-write lock implementation using atomics with writer preference.*/
public class JfrReadWriteLock {
    private static final long CURRENTLY_WRITING = Long.MAX_VALUE;
    private final UninterruptibleUtils.AtomicLong ownerCount;
    private final UninterruptibleUtils.AtomicLong waitingWriters;
    private volatile long writeOwnerTid;

    public JfrReadWriteLock() {
        ownerCount = new UninterruptibleUtils.AtomicLong(0);
        waitingWriters = new UninterruptibleUtils.AtomicLong(0);
        writeOwnerTid = -1;
    }

    @Uninterruptible(reason = "This method does not do a transition, so the whole critical section must be uninterruptible.", callerMustBe = true)
    public void readLockNoTransition(){
        readTryLock(Integer.MAX_VALUE);
    }

    @Uninterruptible(reason = "This method does not do a transition, so the whole critical section must be uninterruptible.", callerMustBe = true)
    public void writeLockNoTransition(){
        writeTryLock(Integer.MAX_VALUE);
    }

    /** The bias towards writers does NOT ensure that there are no waiting writers at the time the readerCount is
     * compared and set. The only guarantee is that this reader will not acquire the lock before any writer that has
     * been waiting longer than it. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void readTryLock(int retries){
        int yields = 0;
        for (int i = 0; i < retries; i++) {
            long readers = ownerCount.get();
            // Only attempt to enter the critical section if no writers are waiting or writes in-progress.
            if (waitingWriters.get() > 0 || readers == CURRENTLY_WRITING){
                yields = maybeYield(i, yields);
            } else {
                // Attempt to take the lock
                if (ownerCount.compareAndSet(readers, readers+1)){
                    return;
                }
            }
        }
    }
    @Uninterruptible(reason = "This method does not do a transition, so the whole critical section must be uninterruptible.", callerMustBe = true)
    public void writeTryLock(int retries){
        int yields = 0;
        // Increment the writer to count to signal intent.
        waitingWriters.incrementAndGet();
        for (int i = 0; i < retries; i++) {
            long readers = ownerCount.get();
            // Only enter the critical section if all in-progress readers have finished.
            if (readers != 0){
                yields = maybeYield(i, yields);
            } else {
                // Attempt to take the lock
                if (ownerCount.compareAndSet(0, CURRENTLY_WRITING)){
                    // Success. Signal no longer waiting.
                    long waiters = waitingWriters.decrementAndGet();
                    assert  waiters >= 0;
                    writeOwnerTid = SubstrateJVM.getCurrentThreadId();
                    return;
                }
            }
        }
    }

    /** This is the same logic as in {@link com.oracle.svm.core.thread.JavaSpinLockUtils#tryLock(Object, long, int)}*/
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int maybeYield(int retryCount, int yields){
        if ((retryCount & 0xff) == 0 && VMThreads.singleton().supportsNativeYieldAndSleep()) {
            if (yields > 5) {
                VMThreads.singleton().nativeSleep(1);
            } else {
                VMThreads.singleton().yield();
                return yields + 1;
            }
        } else {
            PauseNode.pause();
        }
        return yields;
    }

    @Uninterruptible(reason = "Used in locking without transition, so the whole critical section must be uninterruptible.", callerMustBe = true)
    public void unlock(){
        writeOwnerTid = -1;
        ownerCount.set(0);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isWriteOwner(){
        return writeOwnerTid == SubstrateJVM.getCurrentThreadId();
    }
}
