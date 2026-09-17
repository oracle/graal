/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.profile;

import java.util.concurrent.Callable;

import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl;
import com.oracle.svm.interpreter.ristretto.RistrettoOptions;
import com.oracle.svm.interpreter.ristretto.RistrettoUtils;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoInstalledCode;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.InstalledCode;

public class RistrettoCompilationRequest implements Comparable<RistrettoCompilationRequest>, Callable<InstalledCode> {
    /** Protects completion publication and per-request waiter counts. */
    private static final VMMutex COMPLETION_WAITERS_MUTEX = new VMMutex("ristrettoCompilationCompletionWaiters");
    /** Wakes completion waiters, which recheck the predicate of their own request. */
    private static final VMCondition COMPLETION_WAITERS_CONDITION = new VMCondition(COMPLETION_WAITERS_MUTEX, "ristrettoCompilationCompletion");

    /**
     * Default priority for any graal top tier compilation.
     */
    public static final int DEFAULT_TOP_TIER_COMPILATION_PRIORITY = 100;

    /**
     * Default priority for OSR compilations. OSR requests come from already-hot loops, so they should
     * run ahead of ordinary invocation-triggered top-tier requests in the compilation queue.
     */
    public static final int DEFAULT_OSR_COMPILATION_PRIORITY = 50;

    /**
     * Ristretto method whose bytecodes or OSR entry graph will be compiled.
     */
    private final RistrettoMethod rMethod;

    /**
     * Queue ordering key; lower values are consumed first by the compilation manager.
     */
    private final int priority;

    /** One-way publication bit for a request that can no longer install code. */
    private volatile boolean completed;

    /** Waiters registered for this request while it is incomplete. Guarded by {@link #COMPLETION_WAITERS_MUTEX}. */
    private int completionWaiterCount;

    /**
     * Entry BCI for this compilation.
     *
     * {@link RistrettoUtils#INVOCATION_ENTRY_BCI} denotes an ordinary invocation compile. Any other
     * value denotes an OSR compile that parses from that bytecode index and installs code in the
     * per-backedge OSR state for the same BCI.
     */
    private final int entryBCI;

    /**
     * OSR request id that owns this compilation callback, or
     * {@link RistrettoMethod#NO_OSR_COMPILATION_REQUEST} for invocation-entry compiles and tests that
     * only inspect queue ordering.
     */
    private final int osrCompilationRequestId;

    /// Monotonic time at which this request entered the compilation manager.
    private volatile long submittedAtNanos;

    /// Compilation requests are one-shot queue entries.
    private boolean submitted;

    /// Intrusive links used only while this request is tracked for watchdog diagnostics.
    RistrettoCompilationRequest previousQueuedRequestToReport;
    RistrettoCompilationRequest nextQueuedRequestToReport;
    boolean sampledForCompilationWatchdog;

    public RistrettoCompilationRequest(RistrettoMethod rMethod, int priority) {
        this(rMethod, priority, RistrettoUtils.INVOCATION_ENTRY_BCI, RistrettoMethod.NO_OSR_COMPILATION_REQUEST);
    }

    public RistrettoCompilationRequest(RistrettoMethod rMethod, int priority, int entryBCI) {
        this(rMethod, priority, entryBCI, RistrettoMethod.NO_OSR_COMPILATION_REQUEST);
    }

    public RistrettoCompilationRequest(RistrettoMethod rMethod, int priority, int entryBCI, int osrCompilationRequestId) {
        this.rMethod = rMethod;
        this.priority = priority;
        this.entryBCI = entryBCI;
        this.osrCompilationRequestId = osrCompilationRequestId;
    }

    @Override
    public int compareTo(RistrettoCompilationRequest o) {
        return Integer.compare(priority, o.priority);
    }

    @Override
    public InstalledCode call() throws Exception {
        try {
            SubstrateInstalledCodeImpl code = compileAndInstall();
            if (code == null) {
                onCompilationFailure();
                return null;
            }
            RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Finished compiling %s%n", rMethod);

            /*
             * Installing a reference to installed code in ristretto method to have the same
             * lifecycle as InterpreterMethod->RistrettoMethod->code, so the root pointer is only
             * dropped when a class is unloaded and the interpreter jvmci objects are collected.
             */
            boolean installCode = RistrettoCompilationManager.TestingBackdoor.installCode();
            boolean published = publishCompiledCode(code, installCode);
            if (!published) {
                if (code.isValid()) {
                    code.invalidate();
                }
                onCompilationFailure();
                return null;
            }
            return code;
        } catch (BailoutException e) {
            if (!e.isPermanent()) {
                onCompilationFailure();
                throw e;
            }
            onPermanentBailout();
            RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilation, "[Ristretto Compiler]Permanent bailout compiling %s: %s%n", this, e.getMessage());
            return null;
        } catch (Throwable t) {
            onCompilationFailure();
            throw t;
        }
    }

    protected SubstrateInstalledCodeImpl compileAndInstall() {
        return RistrettoUtils.compileAndInstallForPublication(rMethod, entryBCI);
    }

    /** Publishes {@code code}, atomically with hierarchy validation when it carries such assumptions. */
    private boolean publishCompiledCode(SubstrateInstalledCodeImpl code, boolean installCode) {
        if (code instanceof RistrettoInstalledCode ristrettoCode) {
            return ristrettoCode.registerHierarchyAssumptionsAndPublish(() -> publishCompiledCodeAfterHierarchyValidation(code, installCode));
        }
        return publishCompiledCodeAfterHierarchyValidation(code, installCode);
    }

    /** Performs the method-state publication step after hierarchy validation. */
    protected boolean publishCompiledCodeAfterHierarchyValidation(SubstrateInstalledCodeImpl code, boolean installCode) {
        if (isOSR()) {
            return rMethod.onOSRCompilationSuccessAfterHierarchyValidation(entryBCI, osrCompilationRequestId, code, installCode);
        }
        return rMethod.onCompilationSuccess(code, installCode);
    }

    @Override
    public String toString() {
        return "CompilationRequest <" + rMethod + ", priority=" + priority + ", entryBCI=" + entryBCI + ">";
    }

    public int getPriority() {
        return priority;
    }

    public RistrettoMethod getRMethod() {
        return rMethod;
    }

    public boolean isOSR() {
        return entryBCI != RistrettoUtils.INVOCATION_ENTRY_BCI;
    }

    /**
     * Records the monotonic time at which this request entered the compilation queue.
     *
     * @param nowNanos submission time obtained from {@link System#nanoTime()}
     */
    synchronized void markSubmitted(long nowNanos) {
        if (submitted) {
            throw new IllegalStateException("A Ristretto compilation request can only be submitted once.");
        }
        submitted = true;
        submittedAtNanos = nowNanos;
    }

    /**
     * Returns the monotonic time at which this request entered the compilation queue.
     */
    long getSubmittedAtNanos() {
        return submittedAtNanos;
    }

    /**
     * Publishes that this request can no longer install code and releases all blocking Xcomp or
     * Xbatch callers. Completion is idempotent so shutdown cleanup can safely race with ordinary
     * compiler-thread cleanup; ownership is cleared only if this request is still current.
     */
    void markCompleted() {
        if (rMethod != null && !isOSR()) {
            rMethod.clearInvocationCompilationRequest(this);
        }
        COMPLETION_WAITERS_MUTEX.lock();
        try {
            completed = true;
            if (completionWaiterCount > 0) {
                COMPLETION_WAITERS_CONDITION.broadcast();
            }
        } finally {
            COMPLETION_WAITERS_MUTEX.unlock();
        }
    }

    /**
     * Cancels a request that will not be executed, restores its invocation or OSR state to a
     * retryable state, and releases completion waiters. This is used when submission is refused or
     * shutdown removes the request from the queue.
     */
    void cancelBeforeExecution() {
        if (rMethod != null) {
            onCompilationFailure();
        }
        markCompleted();
    }

    /** Returns whether this request has finished processing or was cancelled before execution. */
    public boolean isCompleted() {
        return completed;
    }

    int completionWaiterCount() {
        COMPLETION_WAITERS_MUTEX.lock();
        try {
            return completionWaiterCount;
        } finally {
            COMPLETION_WAITERS_MUTEX.unlock();
        }
    }

    /**
     * Waits until this request has finished processing or was cancelled, without consuming a
     * guest-level park permit.
     */
    public void awaitCompletion() {
        if (completed) {
            return;
        }
        COMPLETION_WAITERS_MUTEX.lock();
        try {
            if (completed) {
                return;
            }
            completionWaiterCount++;
            try {
                while (!completed) {
                    COMPLETION_WAITERS_CONDITION.block();
                }
            } finally {
                completionWaiterCount--;
            }
        } finally {
            COMPLETION_WAITERS_MUTEX.unlock();
        }
    }

    /**
     * Records a Graal bailout that declared this request non-retryable.
     */
    private void onPermanentBailout() {
        if (isOSR()) {
            rMethod.onOSRPermanentCompilationFailure(entryBCI, osrCompilationRequestId);
        } else {
            rMethod.onInvocationEntryPermanentBailout();
        }
    }

    /**
     * Records a retryable compilation failure for this request.
     */
    private void onCompilationFailure() {
        if (isOSR()) {
            rMethod.onOSRCompilationFailure(entryBCI, osrCompilationRequestId);
        } else {
            rMethod.onCompilationFailure(this);
        }
    }
}
