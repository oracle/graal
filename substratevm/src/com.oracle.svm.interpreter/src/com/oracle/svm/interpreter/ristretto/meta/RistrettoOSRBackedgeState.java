/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.meta;

import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl;
import com.oracle.svm.interpreter.metadata.profile.MethodProfile;
import com.oracle.svm.interpreter.ristretto.RistrettoConstants;

import jdk.graal.compiler.debug.GraalError;

/**
 * Mutable trigger state for one OSR backedge target.
 *
 * State transitions are intentionally small and monotonic on the hot path:
 *
 * <pre>
 * INITIAL --claimCompilation()--> SUBMITTED --onCompilationSuccess()--> COMPILED
 * INITIAL --claimCompilation()--> SUBMITTED --onCompilationFailure()--> INITIAL
 * INITIAL --claimCompilation()--> SUBMITTED --onPermanentCompilationFailure()--> PERMANENT_BAILOUT
 * INITIAL --claimCompilation()--> SUBMITTED --onCompilationFailure()--> MAX_ATTEMPTS_REACHED
 * COMPILED --invalidateInstalledCode()--> INITIAL
 * </pre>
 *
 * Backedge hotness lives in {@link MethodProfile}; this state only owns the OSR compilation and
 * installed-code lifecycle. The compilation-state transition remains atomic so only one interpreter
 * thread submits the OSR compile for this target BCI.
 */
public final class RistrettoOSRBackedgeState {
    public static final int NO_COMPILATION_REQUEST = -1;

    /**
     * No OSR compilation is queued or installed for this backedge target.
     */
    private static final int STATE_INITIAL = 0;

    /**
     * One OSR compilation has been submitted and has not yet completed.
     */
    private static final int STATE_SUBMITTED = 1;

    /**
     * The backedge target has completed OSR compilation. Code may still be unpublished in tests that
     * disable installation.
     */
    private static final int STATE_COMPILED = 2;

    /**
     * OSR compilation hit a permanent bailout and must not be retried for this backedge target.
     */
    private static final int STATE_PERMANENT_BAILOUT = 3;

    /**
     * OSR compilation consumed its retry budget and must not be retried for this backedge target.
     */
    private static final int STATE_MAX_ATTEMPTS_REACHED = 4;

    /**
     * Bytecode index reached by the backward branch represented by this state.
     */
    private final int targetBCI;

    private volatile int compilationAttempts;

    /**
     * Current OSR compile state, one of {@link #STATE_INITIAL}, {@link #STATE_SUBMITTED}, or
     * {@link #STATE_COMPILED}.
     */
    private volatile int compilationState = STATE_INITIAL;

    /**
     * Monotonic id for the OSR compilation request currently allowed to complete this state.
     */
    private volatile int compilationRequestId;

    /**
     * Installed code for this OSR entry BCI, or {@code null} until compilation publishes one.
     */
    private volatile SubstrateInstalledCodeImpl installedCode;

    RistrettoOSRBackedgeState(int targetBCI) {
        this.targetBCI = targetBCI;
    }

    /**
     * Atomically claims the right to enqueue OSR compilation from this backedge.
     */
    boolean claimCompilation() {
        return claimCompilationRequest() != NO_COMPILATION_REQUEST;
    }

    /**
     * Atomically claims the right to enqueue OSR compilation and returns the request id that owns the
     * eventual completion callback.
     */
    synchronized int claimCompilationRequest() {
        if (compilationState != STATE_INITIAL) {
            return NO_COMPILATION_REQUEST;
        }
        if (compilationAttempts >= RistrettoConstants.COMPILE_STATE_MAX_ATTEMPTS) {
            compilationState = STATE_MAX_ATTEMPTS_REACHED;
            return NO_COMPILATION_REQUEST;
        }
        int requestId = ++compilationRequestId;
        compilationAttempts++;
        compilationState = STATE_SUBMITTED;
        return requestId;
    }

    /**
     * Returns installed code once it has a live entry point.
     */
    CFunctionPointer installedCodeEntryPointIfLive() {
        SubstrateInstalledCodeImpl code = installedCode;
        if (code == null) {
            return Word.nullPointer();
        }
        CFunctionPointer entryPoint = Word.pointer(code.getEntryPoint());
        return entryPoint;
    }

    /**
     * Returns the bytecode index represented by this state.
     */
    public int targetBCI() {
        return targetBCI;
    }

    /**
     * Returns the installed-code object published for this OSR backedge, even when it no longer has a
     * live entry point.
     */
    SubstrateInstalledCodeImpl installedCode() {
        return installedCode;
    }

    /**
     * Returns the number of compile requests claimed for this backedge.
     */
    int compilationAttempts() {
        return compilationAttempts;
    }

    /**
     * Returns whether this backedge exhausted its compile retry budget.
     */
    boolean isCompilationAttemptLimitReached() {
        return compilationAttempts >= RistrettoConstants.COMPILE_STATE_MAX_ATTEMPTS || compilationState == STATE_MAX_ATTEMPTS_REACHED;
    }

    /**
     * Publishes successful OSR compilation for the request id that currently owns this backedge state.
     */
    synchronized void onCompilationSuccess(RistrettoMethod method, int requestId, SubstrateInstalledCodeImpl code, boolean installCode) {
        if (!ownsCurrentSubmittedRequest(requestId)) {
            if (code != null) {
                code.invalidate();
            }
            return;
        }
        if (installCode) {
            installedCode = code;
        }
        if (compilationState == STATE_SUBMITTED) {
            compilationState = STATE_COMPILED;
        } else if (compilationState != STATE_COMPILED) {
            if (installCode) {
                installedCode = null;
            }
            throw GraalError.shouldNotReachHere(String.format("Unexpected OSR compile state for %s@%s: %s", method, targetBCI, compilationState));
        }
    }

    /**
     * Reopens this backedge after a retryable OSR compilation failure owned by {@code requestId}.
     */
    synchronized void onCompilationFailure(int requestId) {
        if (!ownsCurrentSubmittedRequest(requestId)) {
            return;
        }
        int nextState = compilationAttempts >= RistrettoConstants.COMPILE_STATE_MAX_ATTEMPTS ? STATE_MAX_ATTEMPTS_REACHED : STATE_INITIAL;
        compilationState = nextState;
    }

    /**
     * Marks this backedge as non-retryable after a permanent OSR bailout owned by {@code requestId}.
     */
    synchronized void onPermanentCompilationFailure(int requestId) {
        if (!ownsCurrentSubmittedRequest(requestId)) {
            return;
        }
        installedCode = null;
        compilationState = STATE_PERMANENT_BAILOUT;
    }

    /**
     * Returns whether {@code requestId} is the current submitted OSR compilation request.
     */
    private boolean ownsCurrentSubmittedRequest(int requestId) {
        return compilationState == STATE_SUBMITTED && requestId == compilationRequestId;
    }

    /**
     * Invalidates and forgets whatever installed code is currently published for this backedge.
     */
    synchronized void invalidateInstalledCode() {
        if (installedCode != null) {
            installedCode.invalidate();
        }
        installedCode = null;
        resetStateAfterInvalidation();
    }

    /**
     * Forgets installed code only when it still matches the caller's stale code object.
     */
    synchronized boolean invalidateInstalledCode(SubstrateInstalledCodeImpl expectedInstalledCode) {
        if (installedCode != expectedInstalledCode) {
            return false;
        }
        installedCode = null;
        CodeInfoTable.invalidateInstalledCode(expectedInstalledCode);
        resetStateAfterInvalidation();
        return true;
    }

    /**
     * Moves this backedge out of the compiled state after its installed code was invalidated.
     */
    private void resetStateAfterInvalidation() {
        if (compilationState == STATE_PERMANENT_BAILOUT || compilationState == STATE_MAX_ATTEMPTS_REACHED) {
            return;
        }
        compilationState = compilationAttempts >= RistrettoConstants.COMPILE_STATE_MAX_ATTEMPTS ? STATE_MAX_ATTEMPTS_REACHED : STATE_INITIAL;
    }

    /**
     * Clears terminal retry state for white-box tests while preserving ordinary runtime invariants.
     */
    synchronized void resetCompilationStateForTesting() {
        compilationAttempts = 0;
        if (compilationState == STATE_PERMANENT_BAILOUT || compilationState == STATE_MAX_ATTEMPTS_REACHED) {
            compilationState = STATE_INITIAL;
        }
    }
}
