/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.interpreter.metadata.CremaResolvedJavaMethodImpl;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.profile.MethodProfile;
import com.oracle.svm.interpreter.ristretto.RistrettoConstants;
import com.oracle.svm.interpreter.ristretto.RistrettoFeature;
import com.oracle.svm.interpreter.ristretto.RistrettoRuntimeOptions;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.debug.GraalError;
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

    public static void profileIfBranch(InterpreterResolvedJavaMethod iMethod, int bci, boolean taken) {
        if (!RistrettoProfileSupport.isEnabled()) {
            return;
        }

        assert iMethod instanceof CremaResolvedJavaMethodImpl;
        final RistrettoMethod rMethod = RistrettoMethod.create(iMethod);

        int oldState = COMPILATION_STATE_UPDATER.get(rMethod);
        if (!RistrettoCompileStateMachine.shouldEnterProfiling(oldState)) {
            // no need to keep profiling this code, we are done
            trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Should not enter profiling for method %s because of state %s%n", iMethod,
                            RistrettoCompileStateMachine.toString(oldState));
            return;
        }

        // this point is only reached for state=INIT_VAL|INITIALIZING|NEVER_COMPILED
        while (true) {
            oldState = COMPILATION_STATE_UPDATER.get(rMethod);

            switch (oldState) {
                /*
                 * Profile has not been initialized yet for this method, given we are profiling a
                 * branch we must have profiled the method entry already, thus this is a hard error.
                 */
                case RistrettoConstants.COMPILE_STATE_INIT_VAL: {
                    throw GraalError.shouldNotReachHere(String.format("Reached a method without an initialized profile when trying to profile a branch. " +
                                    "This must not happen. Every profile must be created and initially written when profiling the entry of a method in the interpreter. " +
                                    "Method=%s", rMethod));
                }
                case RistrettoConstants.COMPILE_STATE_SUBMITTED:
                case RistrettoConstants.COMPILE_STATE_COMPILED: {
                    profileSkipCase(iMethod, rMethod);
                    return;
                }
                case RistrettoConstants.COMPILE_STATE_NEVER_COMPILED: {

                    MethodProfile methodProfile = rMethod.getProfile();
                    trace(RistrettoRuntimeOptions.JITTraceProfilingIncrements, String.format("[Ristretto Compile Queue]Entering state %s for %s, counter=%s%n",
                                    RistrettoCompileStateMachine.toString(COMPILATION_STATE_UPDATER.get(rMethod)), iMethod, methodProfile.getProfileEntryCount()));
                    /*
                     * We write without any synchronization to the branch profile value at the cost
                     * of lost updates.
                     */
                    methodProfile.profileBranch(bci, taken);

                    // we only increment (and submit if applicable) once, thus return now
                    return;
                }
                case RistrettoConstants.COMPILE_STATE_INITIALIZING: {
                    // another thread is initializing the compilation data, do a few more spins
                    // until that is done and then go on
                    break;
                }
                default:
                    throw GraalError.shouldNotReachHere("Unknown state " + oldState);
            }
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
     * <li><strong>Single Compilation Guarantee:</strong> Each method is compiled exactly once. Once
     * in SUBMITTED or COMPILED state, no further profiling occurs</li>
     * <li><strong>Profile Initialization:</strong> Only one thread can initialize the profile
     * (transition from INIT_VAL to INITIALIZING), others wait for completion</li>
     * <li><strong>Approximate Counting:</strong> Invocation counter increments are unsynchronized,
     * accepting lost updates for performance. This may cause slight variations in exact threshold
     * triggering but ensures scalability</li>
     * <li><strong>Non-Blocking Submission:</strong> Compilation submission only occurs when the
     * invocation count exceeds {@link RistrettoRuntimeOptions#JITCompilerInvocationThreshold}</li>
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
    public static void profileMethodCall(InterpreterResolvedJavaMethod iMethod) {
        if (!RistrettoProfileSupport.isEnabled()) {
            return;
        }

        if (!RistrettoRuntimeOptions.JITEnableCompilation.getValue()) {
            return;
        }

        assert iMethod instanceof CremaResolvedJavaMethodImpl;
        final RistrettoMethod rMethod = RistrettoMethod.create(iMethod);

        int oldState = COMPILATION_STATE_UPDATER.get(rMethod);
        if (!RistrettoCompileStateMachine.shouldEnterProfiling(oldState)) {
            // no need to keep profiling this code, we are done
            trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Should not enter profiling for method %s because of state %s%n", iMethod,
                            RistrettoCompileStateMachine.toString(oldState));
            return;
        }

        // this point is only reached for state=INIT_VAL|INITIALIZING|NEVER_COMPILED
        while (true) {
            oldState = COMPILATION_STATE_UPDATER.get(rMethod);

            /*
             * TODO GR-71597 A note on interpreter performance. Code is abstracted in methods here
             * to ensure better readability. However, for performance we should ensure inlining
             * happens on most of the frequently executed cases here.
             */

            switch (oldState) {
                // profile has not been initialized yet for this method, do so by switching to
                // INITIALIZING and then wait, if another thread went to initializing in the
                // meantime we are done
                case RistrettoConstants.COMPILE_STATE_INIT_VAL: {
                    methodEntryInitCase(iMethod, rMethod);
                    break;
                }
                case RistrettoConstants.COMPILE_STATE_SUBMITTED:
                case RistrettoConstants.COMPILE_STATE_COMPILED: {
                    profileSkipCase(iMethod, rMethod);
                    return;
                }
                case RistrettoConstants.COMPILE_STATE_NEVER_COMPILED: {
                    methodEntryNeverCompiledCase(iMethod, rMethod, oldState);
                    // we only increment (and submit if applicable) once, thus return now
                    return;
                }
                case RistrettoConstants.COMPILE_STATE_INITIALIZING: {
                    // another thread is initializing the compilation data, do a few more spins
                    // until that is done and then go on
                    break;
                }
                default:
                    throw GraalError.shouldNotReachHere("Unknown state " + oldState);
            }
        }
    }

    private static void methodEntryNeverCompiledCase(InterpreterResolvedJavaMethod iMethod, RistrettoMethod rMethod, int oldState) {
        MethodProfile methodProfile = rMethod.getProfile();
        trace(RistrettoRuntimeOptions.JITTraceProfilingIncrements, String.format("[Ristretto Compile Queue]Entering state %s for %s, counter=%s%n",
                        RistrettoCompileStateMachine.toString(COMPILATION_STATE_UPDATER.get(rMethod)), iMethod, methodProfile.getProfileEntryCount()));
        /*
         * We write without any synchronization to the methodProfile.counter value at the cost of
         * lost updates.
         */
        if (methodProfile.profileMethodEntry() > RistrettoRuntimeOptions.JITCompilerInvocationThreshold.getValue()) {
            trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Entering state %s for %s, profile overflown, trying to submit compile%n",
                            RistrettoCompileStateMachine.toString(oldState), iMethod);
            while (!COMPILATION_STATE_UPDATER.compareAndSet(rMethod, RistrettoConstants.COMPILE_STATE_NEVER_COMPILED, RistrettoConstants.COMPILE_STATE_SUBMITTED)) {
                // wait until we are allowed to submit
                PauseNode.pause();
            }
            trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Entering state %s for %s%n",
                            RistrettoCompileStateMachine.toString(COMPILATION_STATE_UPDATER.get(rMethod)), iMethod);
            RistrettoCompilationManager.get()
                            .submitCompilationRequest(new RistrettoCompilationRequest(rMethod, RistrettoCompilationRequest.DEFAULT_TOP_TIER_COMPILATION_PRIORITY));
        }
    }

    private static void profileSkipCase(InterpreterResolvedJavaMethod iMethod, RistrettoMethod rMethod) {
        // in the meantime compilation happened already, we are done
        trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Entering state %s for %s, skipping any profiling or compilation%n",
                        RistrettoCompileStateMachine.toString(COMPILATION_STATE_UPDATER.get(rMethod)), iMethod);
    }

    private static void methodEntryInitCase(InterpreterResolvedJavaMethod iMethod, RistrettoMethod rMethod) {
        trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Entering state %s for %s%n",
                        RistrettoCompileStateMachine.toString(COMPILATION_STATE_UPDATER.get(rMethod)), iMethod);

        if (COMPILATION_STATE_UPDATER.compareAndSet(rMethod, RistrettoConstants.COMPILE_STATE_INIT_VAL, RistrettoConstants.COMPILE_STATE_INITIALIZING)) {
            trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Entering state %s for %s%n",
                            RistrettoCompileStateMachine.toString(COMPILATION_STATE_UPDATER.get(rMethod)), iMethod);
            while (!COMPILATION_STATE_UPDATER.compareAndSet(rMethod, RistrettoConstants.COMPILE_STATE_INITIALIZING, RistrettoConstants.COMPILE_STATE_NEVER_COMPILED)) {
                // spin until we are done writing
                PauseNode.pause();
            }
            // continue to the NEVER_COMPILED state
            trace(RistrettoRuntimeOptions.JITTraceCompilationQueuing, "[Ristretto Compile Queue]Finished setting state %s for %s%n",
                            RistrettoCompileStateMachine.toString(COMPILATION_STATE_UPDATER.get(rMethod)), iMethod);
        }
    }

}
