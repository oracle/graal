/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.SubstrateOptions.UseDedicatedVmThread;

import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.Safepoint.SafepointRequestValues;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;

/**
 * Only one thread at a time can execute {@link VMOperation}s. This mimics HotSpot's single
 * "VMThread", which probably saves a lot of locking or atomics.
 *
 * Depending on the option {@link SubstrateOptions#UseDedicatedVmThread}, VM operations are either
 * executed by a dedicated VM thread or by the application thread that queued the VM operation
 * (i.e., a temporary VM thread).
 *
 * It is possible that the execution of a VM operation triggers another VM operation explicitly or
 * implicitly (e.g. a GC). Such recursive VM operations are executed immediately (see
 * {@link #immediateQueues}).
 */
public final class VMOperationControl {
    private static VMThread dedicatedVmThread = null;

    private final WorkQueues mainQueues;
    private final WorkQueues immediateQueues;
    private final OpInProgress inProgress;
    private IsolateThread temporaryVmThread;

    @Platforms(Platform.HOSTED_ONLY.class)
    VMOperationControl() {
        this.mainQueues = new WorkQueues("main", true);
        this.immediateQueues = new WorkQueues("immediate", false);
        this.inProgress = new OpInProgress();
    }

    static VMOperationControl get() {
        return ImageSingletons.lookup(VMOperationControl.class);
    }

    public static void startVmThread() {
        assert UseDedicatedVmThread.getValue();
        assert get().mainQueues.isEmpty();

        dedicatedVmThread = new VMThread();
        Thread thread = new Thread(dedicatedVmThread, "VMThread");
        thread.setDaemon(true);
        thread.start();
        dedicatedVmThread.waitUntilStarted();
    }

    public static void stopVmThread() {
        assert UseDedicatedVmThread.getValue();
        JavaVMOperation.enqueueBlockingNoSafepoint("Stop VMThread", () -> {
            dedicatedVmThread.stop();
        });
        dedicatedVmThread.waitUntilStopped();
        assert get().mainQueues.isEmpty();
    }

    public static boolean isDedicatedVmThread() {
        return isDedicatedVmThread(CurrentIsolate.getCurrentThread());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static boolean isDedicatedVmThread(IsolateThread thread) {
        if (UseDedicatedVmThread.getValue()) {
            return thread == dedicatedVmThread.getIsolateThread();
        }
        return false;
    }

    public static boolean isTemporaryVmThread() {
        if (!UseDedicatedVmThread.getValue()) {
            return get().temporaryVmThread == CurrentIsolate.getCurrentThread();
        }
        return false;
    }

    private static IsolateThread getTemporaryVmThread() {
        return get().temporaryVmThread;
    }

    private static void setTemporaryVmThread(IsolateThread value) {
        VMOperationControl control = VMOperationControl.get();
        if (UseDedicatedVmThread.getValue()) {
            assert control.temporaryVmThread.isNull();
            assert isDedicatedVmThread(value);
        } else {
            assert value.isNull() || control.temporaryVmThread.isNull() || control.temporaryVmThread == value;
            control.temporaryVmThread = value;
        }
    }

    public static void logRecentEvents(Log log) {
        /*
         * All reads in this method are racy as the currently executed VM operation could finish and
         * a different VM operation could start. So, the read data is not necessarily consistent.
         */
        VMOperationControl control = get();
        VMOperation op = control.inProgress.operation;
        if (op == null) {
            log.string("No VMOperation in progress").newline();
        } else {
            log.string("VMOperation in progress: ").string(op.getName()).newline();
            log.string("  causesSafepoint: ").bool(op.getCausesSafepoint()).newline();
            log.string("  queuingThread: ").zhex(control.inProgress.queueingThread.rawValue()).newline();
            log.string("  executingThread: ").zhex(control.inProgress.executingThread.rawValue()).newline();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    OpInProgress getInProgress() {
        return inProgress;
    }

    void setInProgress(VMOperation operation, IsolateThread queueingThread, IsolateThread executingThread) {
        assert operation != null && queueingThread.isNonNull() && executingThread.isNonNull() || operation == null && queueingThread.isNull() && executingThread.isNull();
        inProgress.operation = operation;
        inProgress.queueingThread = queueingThread;
        inProgress.executingThread = executingThread;
    }

    void enqueue(JavaVMOperation operation) {
        enqueue(operation, WordFactory.nullPointer());
    }

    void enqueue(NativeVMOperationData data) {
        enqueue(data.getNativeVMOperation(), data);
    }

    /**
     * Enqueues a {@link VMOperation} and returns as soon as the operation was executed.
     *
     * Here, we must make the yellow zone available. Otherwise, {@link StackOverflowError}s can
     * happen within the VM operation queuing and processing logic. This can have pretty nasty side
     * effects. E.g., a VM operation could be queued but neither processed nor remove from the
     * queue.
     */
    private void enqueue(VMOperation operation, NativeVMOperationData data) {
        StackOverflowCheck.singleton().makeYellowZoneAvailable();
        try {
            log().string("[VMOperationControl.enqueue:").string("  operation: ").string(operation.getName());
            if (!SubstrateOptions.MultiThreaded.getValue()) {
                // no safepoint is needed, so we can always directly execute the operation
                assert !UseDedicatedVmThread.getValue();
                assert !ThreadingSupportImpl.singleton().needsCallbackOnSafepointCheckSlowpath() : "single threaded execution does not support recurring callbacks";
                operation.execute(data);
            } else if (isDedicatedVmThread() || isTemporaryVmThread()) {
                // a recursive VM operation (either triggered implicitly or explicitly) -> execute
                // it right away
                immediateQueues.enqueueAndExecute(operation, data);
            } else if (UseDedicatedVmThread.getValue()) {
                // a thread queues an operation that the VM thread will execute
                assert !isDedicatedVmThread() : "the dedicated VM thread must execute and not queue VM operations";
                assert dedicatedVmThread.isRunning() : "must not queue a VM operation before the VM thread is started";
                VMThreads.THREAD_MUTEX.guaranteeNotOwner("could result in deadlocks otherwise");
                mainQueues.enqueueAndWait(operation, data);
            } else {
                // use the current thread to execute the operation under a lock
                VMThreads.THREAD_MUTEX.guaranteeNotOwner("could result in deadlocks otherwise");
                mainQueues.enqueueAndExecute(operation, data);
            }
            assert operation.isFinished(data);
            log().string("]").newline();
        } finally {
            StackOverflowCheck.singleton().protectYellowZone();
        }
    }

    /** Check if it is okay for this thread to block. */
    public static void guaranteeOkayToBlock(String message) {
        /* If the system is frozen at a safepoint, then it is not okay to block. */
        if (isAtSafepoint()) {
            Log.log().string(message).newline();
            VMError.shouldNotReachHere("Should not reach here: Not okay to block.");
        }
    }

    public static boolean isAtSafepoint() {
        return Safepoint.Master.singleton().isFrozen();
    }

    private static Log log() {
        return SubstrateOptions.TraceVMOperations.getValue() ? Log.log() : Log.noopLog();
    }

    /**
     * A dedicated VM thread that executes {@link VMOperation}s. If the option
     * {@link SubstrateOptions#UseDedicatedVmThread} is enabled, then this thread is the only one
     * that may initiate a safepoint. Therefore, it never gets blocked at a safepoint and does not
     * support recurring callbacks.
     */
    private static class VMThread implements Runnable {
        private volatile IsolateThread isolateThread;
        private boolean stopped;

        VMThread() {
            this.isolateThread = WordFactory.nullPointer();
            this.stopped = false;
        }

        @Override
        public void run() {
            StatusSupport.setStatusIgnoreSafepoints();
            this.isolateThread = CurrentIsolate.getCurrentThread();

            VMOperationControl control = VMOperationControl.get();
            WorkQueues queues = control.mainQueues;
            while (!stopped) {
                try {
                    assert !ThreadingSupportImpl.singleton().needsCallbackOnSafepointCheckSlowpath() : "VM thread does not participate in any safepoint logic";
                    queues.waitForWorkAndExecute();
                } catch (Throwable e) {
                    log().string("[VMOperation.execute caught: ").string(e.getClass().getName()).string("]").newline();
                    throw VMError.shouldNotReachHere(e);
                }
            }
            this.isolateThread = WordFactory.nullPointer();
        }

        public void waitUntilStarted() {
            while (isolateThread.isNull()) {
                Thread.yield();
            }
        }

        public void waitUntilStopped() {
            while (isolateThread.isNonNull()) {
                Thread.yield();
            }
        }

        @Uninterruptible(reason = "Called from uninterruptible code.")
        public IsolateThread getIsolateThread() {
            return isolateThread;
        }

        public boolean isRunning() {
            return isolateThread.isNonNull();
        }

        public void stop() {
            assert VMOperation.isInProgress() : "must only be called from a VM operation";
            this.stopped = true;
        }
    }

    private static final class WorkQueues {
        private final NativeVMOperationQueue nativeNonSafepointOperations;
        private final NativeVMOperationQueue nativeSafepointOperations;
        private final JavaVMOperationQueue javaNonSafepointOperations;
        private final JavaVMOperationQueue javaSafepointOperations;

        /**
         * This mutex is used by the application threads and by the VM thread. Only normal lock
         * operations with a full transition may be used here. This restriction is necessary to
         * ensure that a VM operation that needs a safepoint can really bring all other threads to a
         * halt, even if those other threads also want to queue VM operations in the meanwhile.
         */
        private final VMMutex mutex;
        private final VMCondition operationQueued;
        private final VMCondition operationFinished;

        @Platforms(Platform.HOSTED_ONLY.class)
        WorkQueues(String prefix, boolean needsLocking) {
            this.nativeNonSafepointOperations = new NativeVMOperationQueue(prefix + "NativeNonSafepointOperations");
            this.nativeSafepointOperations = new NativeVMOperationQueue(prefix + "NativeSafepointOperations");
            this.javaNonSafepointOperations = new JavaVMOperationQueue(prefix + "JavaNonSafepointOperations");
            this.javaSafepointOperations = new JavaVMOperationQueue(prefix + "JavaSafepointOperations");
            this.mutex = createLock(needsLocking);
            this.operationQueued = createCondition();
            this.operationFinished = createCondition();
        }

        boolean isEmpty() {
            return nativeNonSafepointOperations.isEmpty() && nativeSafepointOperations.isEmpty() && javaNonSafepointOperations.isEmpty() && javaSafepointOperations.isEmpty();
        }

        void enqueueAndWait(VMOperation operation, NativeVMOperationData data) {
            assert UseDedicatedVmThread.getValue();
            lock();
            try {
                enqueue(operation, data);
                operationQueued.broadcast();
                while (!operation.isFinished(data)) {
                    operationFinished.block();
                }
            } finally {
                unlock();
            }
        }

        void waitForWorkAndExecute() {
            assert isDedicatedVmThread();
            lock();
            try {
                while (isEmpty()) {
                    operationQueued.block();
                }

                executeAllQueuedVMOperations();
            } finally {
                unlock();
            }
        }

        void enqueueAndExecute(VMOperation operation, NativeVMOperationData data) {
            lock();
            try {
                IsolateThread prevTempVmThread = getTemporaryVmThread();
                setTemporaryVmThread(CurrentIsolate.getCurrentThread());
                try {
                    enqueue(operation, data);
                    executeAllQueuedVMOperations();
                } finally {
                    setTemporaryVmThread(prevTempVmThread);
                    assert isEmpty();
                }
            } finally {
                unlock();
            }
        }

        private void enqueue(VMOperation operation, NativeVMOperationData data) {
            assertIsLocked();
            operation.setQueuingThread(data, CurrentIsolate.getCurrentThread());
            if (operation instanceof JavaVMOperation) {
                if (operation.getCausesSafepoint()) {
                    javaSafepointOperations.push((JavaVMOperation) operation);
                } else {
                    javaNonSafepointOperations.push((JavaVMOperation) operation);
                }
            } else if (operation instanceof NativeVMOperation) {
                assert operation == data.getNativeVMOperation();
                if (operation.getCausesSafepoint()) {
                    nativeSafepointOperations.push(data);
                } else {
                    nativeNonSafepointOperations.push(data);
                }
            } else {
                VMError.shouldNotReachHere();
            }
        }

        private void executeAllQueuedVMOperations() {
            assertIsLocked();

            // Drain the non-safepoint queues.
            drain(nativeNonSafepointOperations);
            drain(javaNonSafepointOperations);

            // Drain the safepoint queue.
            if (!nativeSafepointOperations.isEmpty() || !javaSafepointOperations.isEmpty()) {
                /*
                 * During a safepoint, periodic callbacks and safepoint checks are suspended because
                 * we cannot let arbitrary user code run, e.g., during GC. We save the state here
                 * before initiating the safepoint so that the values can be used when the safepoint
                 * ends. The safepoint logic contains very similar logic for the periodic callbacks,
                 * which triggers for all threads that are blocked due to the safepoint.
                 */
                String safepointReason = null;
                boolean startedSafepoint = false;
                boolean lockedForSafepoint = false;
                boolean needsCallback = false;
                long callbackTime = 0;
                int callbackValue = 0;

                Safepoint.Master master = Safepoint.Master.singleton();
                if (!master.isFrozen()) {
                    startedSafepoint = true;
                    safepointReason = getSafepointReason(nativeSafepointOperations, javaSafepointOperations);

                    assert !(isDedicatedVmThread() && ThreadingSupportImpl.singleton().needsCallbackOnSafepointCheckSlowpath());
                    needsCallback = !UseDedicatedVmThread.getValue() && ThreadingSupportImpl.singleton().needsCallbackOnSafepointCheckSlowpath();
                    if (needsCallback) {
                        callbackTime = System.nanoTime();
                        callbackValue = Safepoint.getSafepointRequested(CurrentIsolate.getCurrentThread());
                        log().string("set safepoint requested").newline();
                        Safepoint.setSafepointRequested(CurrentIsolate.getCurrentThread(), SafepointRequestValues.RESET);
                    }

                    lockedForSafepoint = master.freeze(safepointReason);
                }

                try {
                    drain(nativeSafepointOperations);
                    drain(javaSafepointOperations);
                } finally {
                    if (startedSafepoint) {
                        master.thaw(safepointReason, lockedForSafepoint);

                        if (needsCallback) {
                            ThreadingSupportImpl.singleton().onSafepointCheckSlowpath(callbackTime, callbackValue);
                        }
                    }
                }
            }
        }

        private static String getSafepointReason(NativeVMOperationQueue nativeSafepointOperations, JavaVMOperationQueue javaSafepointOperations) {
            NativeVMOperationData data = nativeSafepointOperations.peek();
            if (data.isNonNull()) {
                return data.getNativeVMOperation().getName();
            } else {
                VMOperation op = javaSafepointOperations.peek();
                assert op != null;
                return op.getName();
            }
        }

        private void drain(NativeVMOperationQueue workQueue) {
            assertIsLocked();
            if (!workQueue.isEmpty()) {
                final Log trace = log();
                trace.string("[Worklist.drain:  queue: ").string(workQueue.name);
                while (!workQueue.isEmpty()) {
                    NativeVMOperationData data = workQueue.pop();
                    try {
                        data.getNativeVMOperation().execute(data);
                    } finally {
                        operationFinished();
                    }
                }
                trace.string("]").newline();
            }
        }

        private void drain(JavaVMOperationQueue workQueue) {
            assertIsLocked();
            if (!workQueue.isEmpty()) {
                final Log trace = log();
                trace.string("[Worklist.drain:  queue: ").string(workQueue.name);
                while (!workQueue.isEmpty()) {
                    JavaVMOperation operation = workQueue.pop();
                    try {
                        operation.execute(WordFactory.nullPointer());
                    } finally {
                        operationFinished();
                    }
                }
                trace.string("]").newline();
            }
        }

        private void lock() {
            if (mutex != null) {
                mutex.lock();
            }
        }

        private void unlock() {
            if (mutex != null) {
                mutex.unlock();
            }
        }

        private void operationFinished() {
            if (operationFinished != null) {
                operationFinished.broadcast();
            }
        }

        private void assertIsLocked() {
            if (mutex != null) {
                mutex.assertIsOwner("must be locked");
            }
        }

        @Platforms(value = Platform.HOSTED_ONLY.class)
        private static VMMutex createLock(boolean needsLocking) {
            if (needsLocking) {
                return new VMMutex();
            }
            return null;
        }

        @Platforms(value = Platform.HOSTED_ONLY.class)
        private VMCondition createCondition() {
            if (mutex != null && UseDedicatedVmThread.getValue()) {
                return new VMCondition(mutex);
            }
            return null;
        }
    }

    protected abstract static class AllocationFreeQueue<T> {
        final String name;

        AllocationFreeQueue(String name) {
            this.name = name;
        }

        abstract boolean isEmpty();

        abstract void push(T element);

        /** Pop an element from the queue, or null if the queue is empty. */
        abstract T pop();

        /** Returns the element that will be popped next. */
        abstract T peek();
    }

    /**
     * A queue that does not allocate because each element has a next pointer. This queue is
     * <em>not</em> multi-thread safe.
     */
    protected static class JavaAllocationFreeQueue<T extends JavaAllocationFreeQueue.Element<T>> extends AllocationFreeQueue<T> {
        private T head;
        private T tail; // can point to an incorrect value if head is null

        JavaAllocationFreeQueue(String name) {
            super(name);
        }

        @Override
        public boolean isEmpty() {
            return head == null;
        }

        @Override
        public void push(T element) {
            assert element.getNext() == null;
            if (head == null) {
                head = element;
            } else {
                tail.setNext(element);
            }
            tail = element;
        }

        @Override
        public T pop() {
            if (head == null) {
                return head;
            }
            T resultElement = head;
            head = resultElement.getNext();
            resultElement.setNext(null);
            return resultElement;
        }

        @Override
        public T peek() {
            return head;
        }

        /** An element for an allocation-free queue. An element can be in at most one queue. */
        public interface Element<T extends Element<T>> {
            T getNext();

            void setNext(T newNext);
        }
    }

    protected static class JavaVMOperationQueue extends JavaAllocationFreeQueue<JavaVMOperation> {
        JavaVMOperationQueue(String name) {
            super(name);
        }
    }

    /**
     * Same implementation as {@link JavaAllocationFreeQueue} but for elements of type
     * {@link NativeVMOperationData}. We can't reuse the other implementation because we need to use
     * the semantics of {@link Word}.
     */
    protected static class NativeVMOperationQueue extends AllocationFreeQueue<NativeVMOperationData> {
        private NativeVMOperationData head;
        private NativeVMOperationData tail; // can point to an incorrect value if head is null

        NativeVMOperationQueue(String name) {
            super(name);
        }

        @Override
        public boolean isEmpty() {
            return head.isNull();
        }

        @Override
        public void push(NativeVMOperationData element) {
            assert element.getNext().isNull();
            if (head.isNull()) {
                head = element;
            } else {
                tail.setNext(element);
            }
            tail = element;
        }

        @Override
        public NativeVMOperationData pop() {
            if (head.isNull()) {
                return head;
            }
            NativeVMOperationData resultElement = head;
            head = resultElement.getNext();
            resultElement.setNext(WordFactory.nullPointer());
            return resultElement;
        }

        @Override
        public NativeVMOperationData peek() {
            return head;
        }
    }

    /**
     * This class holds the information about the {@link VMOperation} that is currently in progress.
     * We use this class to cache all values that another thread might want to query as we must not
     * access the {@link NativeVMOperationData} from another thread (it is allocated in native
     * memory that can be freed when the operation finishes).
     */
    protected static class OpInProgress {
        private VMOperation operation;
        private IsolateThread queueingThread;
        private IsolateThread executingThread;

        public VMOperation getOperation() {
            return operation;
        }

        public IsolateThread getQueuingThread() {
            return queueingThread;
        }

        public IsolateThread getExecutingThread() {
            return executingThread;
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
