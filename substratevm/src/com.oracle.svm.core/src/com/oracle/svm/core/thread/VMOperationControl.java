/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.thread;

import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPointContext;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.util.VMError;

/** A multiplex of VMOperation queues. */
public final class VMOperationControl {

    /** Is this thread the owner of the VMOperation lock. */
    private static final FastThreadLocalInt isLockOwner = FastThreadLocalFactory.createInt();

    /**
     * The lists of pending VMOperations of various kinds.
     *
     * The current implementation uses stacks, but a VMOperation should not depend on
     * last-in-first-out ordering, or any other ordering for that matter. If one VMOperation depends
     * on another VMOperation following it immediately, it should run the execute method of that
     * VMOperation itself, after coming to a safepoint if necessary.
     */
    private final Worklist nonBlockingNonSafepointOperations;
    private final Worklist nonBlockingSafepointOperations;
    private final Worklist blockingNonSafepointOperations;
    private final Worklist blockingSafepointOperations;

    /** A reason for this VMOperation. Not necessarily the current operation that is running. */
    private String reason;

    /** An indicator of which VMOperation is in progress. */
    private VMOperation inProgress;

    /** Constructor for singleton. */
    @Platforms(Platform.HOSTED_ONLY.class)
    VMOperationControl() {
        this.nonBlockingNonSafepointOperations = new Worklist(getLock(), "nonBlockingNonSafepoint");
        this.nonBlockingSafepointOperations = new Worklist(getLock(), "nonBlockingSafepoint");
        this.blockingNonSafepointOperations = new Worklist(getLock(), "blockingNonSafepoint");
        this.blockingSafepointOperations = new Worklist(getLock(), "blockingSafepoint");
        this.reason = "TooSoonToTell";
        this.inProgress = null;
    }

    /** There is only one VMOperation controller. Constructed during native image construction. */
    static VMOperationControl getVMOperationControl() {
        return ImageSingletons.lookup(VMOperationControl.class);
    }

    public static void logRecentEvents(Log log) {
        VMOperation cur = getVMOperationControl().inProgress;
        if (cur == null) {
            log.string("No VMOperation in progress").newline();
        } else {
            log.string("VMOperation in progress: ").string(cur.getName()).newline();
            log.string("  blocksCaller: ").bool(cur.getBlocksCaller()).newline();
            log.string("  causesSafepoint: ").bool(cur.getCausesSafepoint()).newline();
            log.string("  queuingThread: ").zhex(cur.getQueuingVMThread().rawValue()).newline();
            log.string("  executingThread: ").zhex(cur.getExecutingVMThread().rawValue()).newline();
        }
    }

    VMOperation getInProgress() {
        return inProgress;
    }

    void setInProgress(VMOperation value) {
        inProgress = value;
    }

    /**
     * Queue a VMOperation and drain all the queued operations. This is over-engineered now because
     * I execute a VMOperation as soon as it is enqueued, and I hold a lock (preventing further
     * enqueuing) by other threads while I do that.
     */
    public static void enqueue(VMOperation operation) {
        final Log trace = SubstrateOptions.TraceVMOperations.getValue() ? Log.log() : Log.noopLog();
        trace.string("[VMOperationControl.enqueue:").string("  operation: ").string(operation.getName());

        boolean needsCallback = ThreadingSupportImpl.singleton().needsCallbackOnSafepointCheckSlowpath();
        long callbackTime = 0;
        int callbackValue = 0;

        /*
         * Policy: Only one thread at a time can drain the queues. This mimics HotSpot's single
         * "VMThread", which probably saves a lot of locking or atomics.
         */
        /*
         * If I am not already the owner of the VMOperation, then acquire the lock, and release it
         * as I exit.
         */
        final boolean needLockUnlock = !isLockOwner();
        if (needLockUnlock) {
            if (needsCallback) {
                /*
                 * While a VMOperation is running, periodic callbacks are suspended because we
                 * cannot let arbitrary user code run, e.g., during GC. We save the state here so
                 * that we can restore it below after releasing the lock again.
                 */
                callbackTime = System.nanoTime();
                callbackValue = Safepoint.getSafepointRequested(CEntryPointContext.getCurrentIsolateThread());
            }

            getVMOperationControl().acquireLock();
        }
        try {
            // The reason is most recently queued VMOperation.
            getVMOperationControl().setReason(operation.getName());
            // Distribute the operation to the correct queue.
            if ((!operation.getBlocksCaller()) && (!operation.getCausesSafepoint())) {
                getVMOperationControl().nonBlockingNonSafepointOperations.push(operation);
            } else if ((!operation.getBlocksCaller()) && (operation.getCausesSafepoint())) {
                getVMOperationControl().nonBlockingSafepointOperations.push(operation);
            } else if ((operation.getBlocksCaller()) && (!operation.getCausesSafepoint())) {
                getVMOperationControl().blockingNonSafepointOperations.push(operation);
            } else if ((operation.getBlocksCaller()) && (operation.getCausesSafepoint())) {
                getVMOperationControl().blockingSafepointOperations.push(operation);
            }
            getVMOperationControl().drain();
        } finally {
            if (needLockUnlock) {
                getVMOperationControl().releaseLock();

                if (needsCallback) {
                    ThreadingSupportImpl.singleton().onSafepointCheckSlowpath(callbackTime, callbackValue);
                }
            }
        }
        trace.string("]").newline();
    }

    protected void drain() {
        final Log trace = SubstrateOptions.TraceVMOperations.getValue() ? Log.log() : Log.noopLog();
        Safepoint.Master master = Safepoint.Master.singleton();
        trace.string("[VMOperationControl.drain:").string("  MASTER.isFrozen: ").bool(master.isFrozen()).newline();
        final String drainReason = getReason();
        // Drain the non-safepoint queues.
        nonBlockingNonSafepointOperations.drain();
        blockingNonSafepointOperations.drain();
        /* Bring the system to a safepoint and then drain the safepoint queues. */
        final boolean nonEmptySafepointQueues = ((!nonBlockingSafepointOperations.isEmpty()) || (!blockingSafepointOperations.isEmpty()));
        /*
         * If I am already at a safepoint, e.g., for a nested operation, do not try to bring the
         * system to a safepoint.
         */
        boolean startedSafepoint = false;
        if (!master.isFrozen() && nonEmptySafepointQueues) {
            startedSafepoint = true;
            master.freeze(drainReason);
        }
        try {
            nonBlockingSafepointOperations.drain();
            blockingSafepointOperations.drain();
        } finally {
            if (startedSafepoint) {
                master.thaw(drainReason);
            }
        }
        trace.string("]").newline();
    }

    @SuppressWarnings({"static-method"})
    /** Abstract which mutex I use for locking the stacks. */
    private VMMutex getLock() {
        // Since I am *sharing* a mutex between the VMOperation code and the Safepoint code,
        // my acquiring and release the lock in this code affects whether that code acquires and
        // releases the lock, or just verifies the state of the lock.
        // Since this code calls that code, the acquisition/release are here
        // and the verification is there.
        return Safepoint.getMutex();
    }

    /** Acquire the mutex. */
    private void acquireLock() {
        final Log trace = SubstrateOptions.TraceVMOperations.getValue() ? Log.log() : Log.noopLog();
        trace.string("[VMOperationControl.acquireLock:").newline();
        getLock().lock();
        setLockOwner();
        trace.string("]").newline();
    }

    private void releaseLock() {
        final Log trace = SubstrateOptions.TraceVMOperations.getValue() ? Log.log() : Log.noopLog();
        trace.string("[VMOperationControl.releaseLock: ").newline();
        final VMMutex lock = getLock();
        lock.assertIsLocked("VMOperationControl.releaseLock but not locked.");
        unsetLockOwner();
        lock.unlock();
        trace.string("  isOwner: ").bool(isLockOwner()).string("]").newline();
    }

    private String getReason() {
        return reason;
    }

    private void setReason(String value) {
        reason = value;
    }

    /*
     * Methods to use the thread-local int "isOwner" as a boolean.
     */

    @Uninterruptible(reason = "Called from Uninterruptible code", mayBeInlined = true)
    protected static boolean isLockOwner() {
        return isLockOwner.get() == 1;
    }

    /** Note that this thread is the owner of the VMOperation lock. */
    private static void setLockOwner() {
        assert (!isLockOwner()) : "VMOperationControl.setOwner, but already owner.";
        isLockOwner.set(1);
    }

    /** Note that this thread is not the owner of the VMOperation lock. */
    private static void unsetLockOwner() {
        assert isLockOwner() : "VMOperationControl.unsetOwner, but not owner.";
        isLockOwner.set(0);
    }

    /** Check if it is okay for this thread to block. */
    public static void guaranteeOkayToBlock(String message) {
        /* If the system is frozen at a safepoint, then it is not okay to block. */
        if (isFrozen()) {
            Log.log().string(message).newline();
            VMError.shouldNotReachHere("Should not reach here: Not okay to block.");
        }
    }

    public static boolean isFrozen() {
        return Safepoint.Master.singleton().isFrozen();
    }

    public static class TestingBackdoor {
        public static boolean isLocked() {
            return VMMutex.TestingBackdoor.isLocked(getVMOperationControl().getLock());
        }
    }

    /** A stack of VMOperations. */
    protected static final class Worklist {

        private final SynchronizedAllocationFreeStack<VMOperation> stack;
        private final String name;

        @Platforms(Platform.HOSTED_ONLY.class)
        protected Worklist(VMMutex lock, String name) {
            this.stack = new SynchronizedAllocationFreeStack<>(lock);
            this.name = name;
        }

        /**
         * Returns true if the work list is empty, else false. This is a snapshot and may no longer
         * be correct by the time the caller can use the result.
         */
        protected boolean isEmpty() {
            return stack.isEmpty();
        }

        /** Push a VMOperation. */
        protected void push(VMOperation element) {
            stack.push(element);
        }

        private VMOperation pop() {
            return stack.pop();
        }

        /** Drain the queue by applying the operations. */
        protected void drain() {
            if (!isEmpty()) {
                final Log trace = SubstrateOptions.TraceVMOperations.getValue() ? Log.log() : Log.noopLog();
                trace.string("[Worklist.drain:  queue: ").string(name);
                for (VMOperation operation = pop(); operation != null; operation = pop()) {
                    operation.execute();
                }
                trace.string("]").newline();
            }
        }
    }

    /**
     * A stack that does not allocate cons-cells because each element has a next pointer.
     *
     * This stack is <em>not</em> multi-thread safe. Someone should hold a mutex to keep this code
     * single-threaded. See {@linkplain SynchronizedAllocationFreeStack}.
     */
    protected static class AllocationFreeStack<T extends AllocationFreeStack.Element<T>> {

        /** The head of the list. */
        private Element<T> head;

        /** Constructor. */
        protected AllocationFreeStack() {
            super();
            this.head = null;
        }

        public static <U extends AllocationFreeStack.Element<U>> AllocationFreeStack<U> factory() {
            return new AllocationFreeStack<>();
        }

        /**
         * Returns true if the stack is empty, else false. This is a snapshot and may no longer be
         * correct by the time the caller can use the result.
         */
        public boolean isEmpty() {
            return head == null;
        }

        public void push(T element) {
            // Widen to Element<T> to access the private fields of Element<T>.
            final Element<T> asElement = element;
            VMError.guarantee(asElement.enqueued == false, "Pushing element, but already enqueued.");
            asElement.enqueued = true;
            // Prepend the element to the head of the list.
            asElement.setNext(head);
            head = asElement;
        }

        /** Pop an element from the stack, or null if the stack is empty. */
        public T pop() {
            if (head == null) {
                return null;
            }
            Element<T> resultElement = head;
            head = resultElement.getNext();
            resultElement.setNext(null);
            resultElement.enqueued = false;
            return Element.asT(resultElement);
        }

        /** An element for an allocation-free stack. An element can be on at most one stack. */
        public static class Element<T extends Element<T>> {

            /** The next element of the stack. */
            private Element<T> next;

            /** Whether this element is already on a queue. */
            private boolean enqueued;

            /**
             * Constructor for subclasses. This might not get called so it can not do anything
             * interesting. The "next" field gets the default value of "null" and the "enqueued"
             * field gets the default value of "false".
             */
            protected Element() {
                super();
            }

            /** Get the next element of the list. */
            private Element<T> getNext() {
                // Since I only put instances of T on the list, the next element is always a T.
                return next;
            }

            private void setNext(Element<T> newNext) {
                assert ((next == null) || (newNext == null)) : "Should not change next abruptly.";
                next = newNext;
            }

            /** Narrow the given Element<T> to a T. The element might be null. */
            @SuppressWarnings("unchecked")
            private static <T extends Element<T>> T asT(Element<T> element) {
                return (T) element;
            }
        }
    }

    /** A synchronized AllocationFreeStack. */
    protected static final class SynchronizedAllocationFreeStack<T extends AllocationFreeStack.Element<T>> extends AllocationFreeStack<T> {

        /**
         * A mutual exclusion lock for the stack.
         * <p>
         * This can either be a per-stack lock constructed just to synchronize this stack, or a
         * shared lock. A per-stack lock will be more work (more frequent locks and unlocks across
         * all the stacks) but more scalable (less interference with other pushes and pops). A
         * shared lock will do fewer locks and unlocks (think: lock coarsening) but be less scalable
         * (think: lock coarsening).
         * <p>
         * Here I am assuming this is a shared lock, so
         * {@link #push(VMOperationControl.AllocationFreeStack.Element)} and {@link #pop()} just
         * *assert* that the lock is held, but do not acquire and release it. If I wanted to support
         * both styles, I could have two versions of each method: e.g., verifiedPop() and
         * lockedPop(), but that seems like too much trouble.
         * <p>
         * The underlying problem is that a VMMutex can not be re-locked, so it can not be locked
         * <em>around</em> these operations, and locked <em>inside</em> these operations.
         */
        private final VMMutex lock;

        public SynchronizedAllocationFreeStack(VMMutex lock) {
            super();
            this.lock = lock;
        }

        /**
         * Returns true if the stack is empty, else false. This is a snapshot and may no longer be
         * correct by the time the caller can use the result.
         */
        @Override
        public boolean isEmpty() {
            return super.isEmpty();
        }

        @Override
        public void push(T element) {
            lock.assertIsLocked("Should hold lock across synchronous push.");
            super.push(element);
        }

        @Override
        public T pop() {
            lock.assertIsLocked("Should hold lock across synchronous pop.");
            return super.pop();
        }
    }
}

@AutomaticFeature
class VMOperationControlFeature implements Feature {
    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Collections.singletonList(SafepointFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(VMOperationControl.class, new VMOperationControl());
    }
}
