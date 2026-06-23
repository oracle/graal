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

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.interpreter.metadata.CremaResolvedJavaMethodImpl;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.profile.MethodProfile;
import com.oracle.svm.interpreter.ristretto.RistrettoConstants;
import com.oracle.svm.interpreter.ristretto.RistrettoFeature;
import com.oracle.svm.interpreter.ristretto.RistrettoOptions;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.nodes.PauseNode;

public class RistrettoProfileSupport {

    public static final AtomicIntegerFieldUpdater<RistrettoMethod> COMPILATION_STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(RistrettoMethod.class, "compilationState");

    @Fold
    public static boolean isEnabled() {
        return ImageSingletons.contains(RistrettoFeature.class);
    }

    public static void trace(RuntimeOptionKey<Boolean> optionKey, String msg, Object... args) {
        if (optionKey.getValue()) {
            Log.log().string(String.format(msg, args));
        }
    }

    /**
     * Profiles a method call and manages the compilation submission process for the Ristretto
     * interpreter. This method implements a thread-safe state machine that handles the lifecycle of
     * method profiling and compilation in a multithreaded environment.
     * <p>
     * For details about the life-cycle of compilations for a given method see
     * {@link RistrettoCompileStateMachine}.
     * <h3>Thread Safety and Invariants</h3>
     * <ul>
     * <li><strong>Atomic State Transitions:</strong> All state changes use
     * {@link AtomicIntegerFieldUpdater#compareAndSet} to ensure thread-safe updates</li>
     * <li><strong>Single Compilation Guarantee:</strong> Each method is compiled exactly once. The
     * first thread that crosses the invocation threshold wins the INTERPRETED-&gt;SUBMITTED
     * transition; duplicate submitters must observe the advanced state and return instead of
     * retrying the submission CAS forever. Once in SUBMITTED or COMPILED state, no further
     * profiling occurs</li>
     * <li><strong>Profile Initialization:</strong> Only one thread can initialize the profile
     * (transition from INIT_VAL to INITIALIZING), others wait for completion</li>
     * <li><strong>Approximate Counting:</strong> Invocation counter increments are unsynchronized,
     * accepting lost updates for performance. This may cause slight variations in exact threshold
     * triggering but ensures scalability</li>
     * <li><strong>Non-Blocking Submission:</strong> Compilation submission only occurs when the
     * invocation count exceeds {@link RistrettoOptions#JITCompilerInvocationThreshold}</li>
     * </ul>
     *
     * <h3>Integration with Compilation Manager</h3> When compilation is triggered, a
     * {@link RistrettoCompilationRequest} is submitted to the {@link RistrettoCompilationManager}
     * via {@link RistrettoCompilationManager#submitCompilationRequest}. The manager uses a
     * {@link PriorityBlockingQueue} for thread-safe queuing and a fixed thread pool for concurrent
     * compilation processing.
     *
     * @param iMethod the interpreter-resolved method being called, must be an instance of
     *            {@link CremaResolvedJavaMethodImpl}
     * @throws AssertionError if iMethod is not a InterpreterResolvedJavaMethod instance
     */
    public static MethodProfile profileMethodEntry(InterpreterResolvedJavaMethod iMethod) {
        if (!SubstrateOptions.useRistretto()) {
            return null;
        }
        if (!RistrettoProfileSupport.isEnabled()) {
            return null;
        }
        if (!RistrettoOptions.JITEnableCompilation.getValue()) {
            return null;
        }

        assert iMethod instanceof CremaResolvedJavaMethodImpl;
        final RistrettoMethod rMethod = RistrettoMethod.getOrCreate(iMethod);

        int oldState = COMPILATION_STATE_UPDATER.get(rMethod);
        if (!RistrettoCompileStateMachine.shouldEnterProfiling(oldState)) {
            /*
             * Invocation-entry compilation is done, but an interpreted activation can still execute
             * loop backedges, for example after deoptimization. Keep returning the existing profile
             * so OSR can compile and enter from those backedges while root code remains installed.
             */
            trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Should not enter profiling for method %s because of state %s%n", iMethod,
                            RistrettoCompileStateMachine.toString(oldState));
            return rMethod.getProfile();
        }

        // this point is only reached for state=INIT_VAL|INITIALIZING|NEVER_COMPILED
        do {
            /*
             * TODO GR-71597 A note on interpreter performance. Code is abstracted in methods here
             * to ensure better readability. However, for performance we should ensure inlining
             * happens on most of the frequently executed cases here.
             */
            switch (oldState) {
                case RistrettoConstants.COMPILE_STATE_INIT_VAL: {
                    /*
                     * The profile has not been initialized yet for this method. Do so by switching
                     * to INITIALIZING. If another thread transitioned to initializing in the
                     * meantime we are done.
                     */
                    methodEntryInitCase(iMethod, rMethod);
                    break;
                }
                case RistrettoConstants.COMPILE_STATE_SUBMITTED:
                case RistrettoConstants.COMPILE_STATE_COMPILED: {
                    profileSkipCase(iMethod, rMethod);
                    return null;
                }
                case RistrettoConstants.COMPILE_STATE_INTERPRETED: {
                    methodEntryInterpretedCase(iMethod, rMethod, oldState);
                    // we only increment (and submit if applicable) once, thus return now
                    MethodProfile profile = rMethod.getProfile();
                    assert profile != null;
                    return profile;
                }
                case RistrettoConstants.COMPILE_STATE_INITIALIZING: {
                    /*
                     * TODO GR-71948 - investigate an early return here
                     * 
                     * another thread is initializing the compilation data, do a few more spins
                     * until that is done and then go on
                     */
                    PauseNode.pause();
                    break;
                }
                default:
                    throw VMError.shouldNotReachHere("Unknown state " + oldState);
            }
            oldState = COMPILATION_STATE_UPDATER.get(rMethod);
        } while (true);
    }

    private static void methodEntryInterpretedCase(InterpreterResolvedJavaMethod iMethod, RistrettoMethod rMethod, int oldState) {
        MethodProfile methodProfile = rMethod.getProfile();
        trace(RistrettoOptions.JITTraceProfilingIncrements, String.format("[Ristretto Compile Queue]Entering state %s for %s, counter=%s%n",
                        RistrettoCompileStateMachine.toString(COMPILATION_STATE_UPDATER.get(rMethod)), iMethod, methodProfile.getProfileEntryCount()));
        /*
         * We write without any synchronization to the methodProfile.counter value at the cost of
         * lost updates.
         */
        if (methodProfile.profileMethodEntry() > RistrettoOptions.JITCompilerInvocationThreshold.getValue()) {
            trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Entering state %s for %s, profile overflown, trying to submit compile%n",
                            RistrettoCompileStateMachine.toString(oldState), iMethod);
            if (RistrettoOptions.JITDisableRootCompiles.getValue()) {
                trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Skipping invocation compilation for %s because root compiles are disabled%n", iMethod);
                return;
            }
            if (!RistrettoOptions.matchesJITCompileOnly(iMethod)) {
                trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Skipping invocation compilation for %s because it does not match JITCompileOnly%n", iMethod);
                return;
            }
            /*
             * A failed claim only proves that this caller did not get ownership of the current
             * INTERPRETED -> SUBMITTED transition. The follow-up load may observe SUBMITTED, a
             * later COMPILED state, or a terminal state. An eventual invalidation can move the
             * method back to INTERPRETED in a later compile epoch, but this caller must not spin
             * waiting for that separate future cycle here.
             */
            if (!rMethod.claimInvocationEntryCompilation()) {
                int observedState = COMPILATION_STATE_UPDATER.get(rMethod);
                assert observedState == RistrettoConstants.COMPILE_STATE_SUBMITTED || observedState == RistrettoConstants.COMPILE_STATE_COMPILED ||
                                observedState == RistrettoConstants.COMPILE_STATE_INTERPRETED ||
                                observedState == RistrettoConstants.COMPILE_STATE_PERMANENT_BAILOUT ||
                                observedState == RistrettoConstants.COMPILE_STATE_MAX_ATTEMPTS_REACHED : String.format(
                                                "Unexpected compile state after duplicate submission race for %s: %s", iMethod,
                                                RistrettoCompileStateMachine.toString(observedState));
                trace(RistrettoOptions.JITTraceCompilationQueuing,
                                "[Ristretto Compile Queue]Another thread already advanced %s to %s, skipping duplicate submission%n",
                                iMethod, RistrettoCompileStateMachine.toString(observedState));
                return;
            }
            trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Entering state %s for %s%n",
                            RistrettoCompileStateMachine.toString(COMPILATION_STATE_UPDATER.get(rMethod)), iMethod);
            RistrettoCompilationManager.get()
                            .submitCompilationRequest(new RistrettoCompilationRequest(rMethod, RistrettoCompilationRequest.DEFAULT_TOP_TIER_COMPILATION_PRIORITY));
        }
    }

    private static void profileSkipCase(InterpreterResolvedJavaMethod iMethod, RistrettoMethod rMethod) {
        // in the meantime compilation happened already, we are done
        trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Entering state %s for %s, skipping any profiling or compilation%n",
                        RistrettoCompileStateMachine.toString(COMPILATION_STATE_UPDATER.get(rMethod)), iMethod);
    }

    private static void methodEntryInitCase(InterpreterResolvedJavaMethod iMethod, RistrettoMethod rMethod) {
        trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Entering state %s for %s%n",
                        RistrettoCompileStateMachine.toString(COMPILATION_STATE_UPDATER.get(rMethod)), iMethod);

        if (COMPILATION_STATE_UPDATER.compareAndSet(rMethod, RistrettoConstants.COMPILE_STATE_INIT_VAL, RistrettoConstants.COMPILE_STATE_INITIALIZING)) {
            trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Entering state %s for %s%n",
                            RistrettoCompileStateMachine.toString(COMPILATION_STATE_UPDATER.get(rMethod)), iMethod);
            if (!COMPILATION_STATE_UPDATER.compareAndSet(rMethod, RistrettoConstants.COMPILE_STATE_INITIALIZING, RistrettoConstants.COMPILE_STATE_INTERPRETED)) {
                throw VMError.shouldNotReachHere("We set transition to COMPILE_STATE_INITIALIZING, we must be allowed to set it to COMPILE_STATE_NEVER_COMPILED");
            }
            // continue to the NEVER_COMPILED state
            trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Finished setting state %s for %s%n",
                            RistrettoCompileStateMachine.toString(COMPILATION_STATE_UPDATER.get(rMethod)), iMethod);
        }
    }

}
