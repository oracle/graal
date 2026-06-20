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
package com.oracle.svm.interpreter.ristretto.meta;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Function;

import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.deopt.DeoptimizedFrame.DeoptTargetTier;
import com.oracle.svm.graal.meta.SubstrateInstalledCodeImpl;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.profile.MethodProfile;
import com.oracle.svm.interpreter.ristretto.RistrettoConstants;
import com.oracle.svm.interpreter.ristretto.RistrettoOptions;
import com.oracle.svm.interpreter.ristretto.RistrettoUtils;
import com.oracle.svm.interpreter.ristretto.compile.RistrettoSpeculationLog;
import com.oracle.svm.interpreter.ristretto.profile.RistrettoDiagnostics;
import com.oracle.svm.interpreter.ristretto.profile.RistrettoProfileSupport;
import com.oracle.svm.interpreter.ristretto.profile.RistrettoProfilingInfo;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.PauseNode;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * JVMCI representation of a {@link jdk.vm.ci.meta.ResolvedJavaMethod} used by Ristretto for
 * compilation. Exists once per {@link InterpreterResolvedJavaMethod}. Allocated before the start of
 * a runtime compilation by {@link RistrettoUtils}. Acts as the major connection link between
 * substrate's JVMCI world and the interpreter's JVMCI world.
 * <p>
 * Additionally, holds necessary information for profiling and runtime compilation code management.
 * See {@link com.oracle.svm.interpreter.ristretto.profile.RistrettoCompilationManager} and
 * {@link com.oracle.svm.interpreter.ristretto.profile.RistrettoProfileSupport} for details.
 * <p>
 * Life cycle: lives until the referencing {@link InterpreterResolvedJavaMethod} is gc-ed.
 */
public final class RistrettoMethod extends SubstrateMethod {
    public static final int NO_OSR_COMPILATION_REQUEST = RistrettoOSRBackedgeState.NO_COMPILATION_REQUEST;

    private final InterpreterResolvedJavaMethod interpreterMethod;
    private RistrettoConstantPool ristrettoConstantPool;
    /**
     * Cached exception handlers for this method. Propagated the first time
     * {@link #getExceptionHandlers()} is called.
     */
    private volatile ExceptionHandler[] rHandlers;

    /**
     * Link to the original SubstrateMethod built at image-build time, if it exists. Can be
     * {@code null}.
     */
    private volatile SubstrateMethod originalRuntimeMethod;

    // JIT COMPILER SUPPORT START
    /**
     * Field exposed for profiling support for this method. Initialized once upon first profiling
     * under heavy synchronization. Never written again. If a ristretto method is GCed profile is
     * lost.
     */
    private MethodProfile profile;
    /**
     * State-machine for compilation handling of this crema method. Every methods starts in a
     * NEVER_COMPILED state and than can cycle through different states.
     * <p>
     * TODO expand docs once this becomes more sophisticated.
     */
    public volatile int compilationState = RistrettoConstants.COMPILE_STATE_INIT_VAL;

    private volatile int compilationAttempts;

    /**
     * Pointer to the current runtime-compiled code for this method.
     *
     * Stale invalidation requests must claim this field atomically before mutating method-global
     * state so older installed-code objects cannot clobber newer compilations.
     */
    public volatile SubstrateInstalledCodeImpl installedCode;

    private static final AtomicIntegerFieldUpdater<RistrettoMethod> COMPILATION_ATTEMPTS_UPDATER = AtomicIntegerFieldUpdater.newUpdater(RistrettoMethod.class, "compilationAttempts");

    private static final AtomicReferenceFieldUpdater<RistrettoMethod, SubstrateInstalledCodeImpl> INSTALLED_CODE_UPDATER = AtomicReferenceFieldUpdater.newUpdater(RistrettoMethod.class,
                    SubstrateInstalledCodeImpl.class, "installedCode");

    /*
     * The speculation log belongs to the RistrettoMethod, not to one RistrettoInstalledCode object.
     * Each compilation snapshots the current failed speculations from this log, while deoptimization
     * appends newly failed speculations back into the same method-level log before invalidating the
     * installed code. Reprofiling may reset interpreter profile counters, but it must not clear this
     * log because later installed-code objects for the same method still need those failures.
     */
    private final RistrettoSpeculationLog speculationLog = new RistrettoSpeculationLog();

    /**
     * Compact OSR trigger table for this method.
     *
     * The interpreter can only enter OSR at loop backedge targets that are known from the bytecodes.
     * We compute that target set once while creating the Ristretto method and store the hot state in
     * side-by-side arrays:
     *
     * <pre>
     * targetBCIs[i]  -> bytecode index reached by a backward branch
     * entries[i]     -> submit state and installed code
     * </pre>
     *
     * Backedge counters live in {@link MethodProfile}. This table avoids allocating map entries or
     * hashing on every interpreted backedge. Dynamic targets such as {@code ret} are not in the
     * table, so they are not OSR entry candidates.
     */
    private final RistrettoOSRBackedgeTable osrBackedges;
    // JIT COMPILER SUPPORT END

    private RistrettoMethod(InterpreterResolvedJavaMethod interpreterMethod) {
        super(0, null, 0, null, 0, null);
        this.interpreterMethod = interpreterMethod;
        this.declaringClass = RistrettoType.getOrCreate(interpreterMethod.getDeclaringClass());
        this.signature = new RistrettoUnresolvedSignature(interpreterMethod.getSignature());
        /*
         * TODO GR-34928 / GR-70938 - Setup indirectCallTarget for miranda and overpass methods.
         */
        this.indirectCallTarget = this;
        this.vTableIndex = interpreterMethod.getVTableIndex();
        this.osrBackedges = RistrettoOSRBackedgeTable.create(interpreterMethod.getCode());
    }

    @Override
    public boolean hasImageCodeOffset() {
        return RistrettoUtils.wasAOTCompiled(interpreterMethod);
    }

    @Override
    public InterpreterResolvedJavaMethod getInterpreterMethod() {
        return interpreterMethod;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean hasInterpreterMethod() {
        return true;
    }

    @Override
    public boolean needSafepointCheck() {
        /*
         * Ristretto methods are runtime-created bytecode methods, so they do not pass through the
         * hosted SubstrateMethod constructor that encodes the safepoint-check flag.
         */
        return true;
    }

    private static final Function<InterpreterResolvedJavaMethod, ResolvedJavaMethod> RISTRETTO_METHOD_FUNCTION = RistrettoMethod::new;

    public static RistrettoMethod getOrCreate(InterpreterResolvedJavaMethod interpreterMethod) {
        return (RistrettoMethod) interpreterMethod.getRistrettoMethod(RISTRETTO_METHOD_FUNCTION);
    }

    private void transitionToInterpreted() {
        int oldState = RistrettoProfileSupport.COMPILATION_STATE_UPDATER.get(this);
        do {
            switch (oldState) {
                case RistrettoConstants.COMPILE_STATE_INIT_VAL:
                case RistrettoConstants.COMPILE_STATE_SUBMITTED:
                case RistrettoConstants.COMPILE_STATE_INTERPRETED:
                case RistrettoConstants.COMPILE_STATE_PERMANENT_BAILOUT:
                case RistrettoConstants.COMPILE_STATE_MAX_ATTEMPTS_REACHED:
                    return;
                case RistrettoConstants.COMPILE_STATE_COMPILED:
                    int nextState = compilationAttempts >= RistrettoConstants.COMPILE_STATE_MAX_ATTEMPTS ? RistrettoConstants.COMPILE_STATE_MAX_ATTEMPTS_REACHED
                                    : RistrettoConstants.COMPILE_STATE_INTERPRETED;
                    if (RistrettoProfileSupport.COMPILATION_STATE_UPDATER.compareAndSet(this, RistrettoConstants.COMPILE_STATE_COMPILED, nextState)) {
                        RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Method]Transitioned to state %s from COMPILED for %s%n", nextState, this);
                        return;
                    }
                    PauseNode.pause();
                    break;
                case RistrettoConstants.COMPILE_STATE_INITIALIZING:
                    throw GraalError.shouldNotReachHere("Can never go back to initialization");
                default:
                    throw VMError.shouldNotReachHere("Unknown state " + oldState);
            }
            oldState = RistrettoProfileSupport.COMPILATION_STATE_UPDATER.get(this);
        } while (true);
    }

    /**
     * Atomically claims the right to submit one invocation-entry compilation for this method.
     */
    public boolean claimInvocationEntryCompilation() {
        if (RistrettoProfileSupport.COMPILATION_STATE_UPDATER.get(this) != RistrettoConstants.COMPILE_STATE_INTERPRETED) {
            return false;
        }
        if (compilationAttempts >= RistrettoConstants.COMPILE_STATE_MAX_ATTEMPTS) {
            if (RistrettoProfileSupport.COMPILATION_STATE_UPDATER.compareAndSet(this, RistrettoConstants.COMPILE_STATE_INTERPRETED,
                            RistrettoConstants.COMPILE_STATE_MAX_ATTEMPTS_REACHED)) {
                RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilationQueuing,
                                "[Ristretto Method]Invocation-entry compilation retry limit reached for %s after %s attempts%n", this, compilationAttempts);
            }
            return false;
        }
        if (RistrettoProfileSupport.COMPILATION_STATE_UPDATER.compareAndSet(this, RistrettoConstants.COMPILE_STATE_INTERPRETED,
                        RistrettoConstants.COMPILE_STATE_SUBMITTED)) {
            COMPILATION_ATTEMPTS_UPDATER.incrementAndGet(this);
            return true;
        }
        return false;
    }

    public int getCompilationAttempts() {
        return compilationAttempts;
    }

    public boolean isCompilationAttemptLimitReached() {
        return compilationAttempts >= RistrettoConstants.COMPILE_STATE_MAX_ATTEMPTS ||
                        compilationState == RistrettoConstants.COMPILE_STATE_MAX_ATTEMPTS_REACHED;
    }

    /**
     * Publishes a freshly compiled installed-code object and advances the method state to
     * {@code COMPILED}.
     *
     * The installed-code pointer is published before the state transition so interpreter dispatch
     * never observes a compiled state without a current code object.
     *
     * @param code the newly compiled code object
     * @param installCode whether the current test configuration wants to publish {@code code}
     *            through {@link #installedCode}
     */
    public void onCompilationSuccess(SubstrateInstalledCodeImpl code, boolean installCode) {
        if (installCode) {
            INSTALLED_CODE_UPDATER.set(this, code);
        }
        if (!RistrettoProfileSupport.COMPILATION_STATE_UPDATER.compareAndSet(this, RistrettoConstants.COMPILE_STATE_SUBMITTED,
                        RistrettoConstants.COMPILE_STATE_COMPILED)) {
            if (installCode) {
                INSTALLED_CODE_UPDATER.compareAndSet(this, code, null);
            }
            throw GraalError.shouldNotReachHere(
                            String.format("Only a single compile of %s should ever reach the compile queue, it cannot be that we reach here with a different state but did %s",
                                            this, RistrettoProfileSupport.COMPILATION_STATE_UPDATER.get(this)));
        }
    }

    /**
     * Restores interpreter profiling after a queued compilation failed.
     *
     * Failed background compilations must not leave the method stuck in
     * {@code COMPILE_STATE_SUBMITTED}, otherwise profiling would stop permanently.
     */
    public void onCompilationFailure() {
        int nextState = compilationAttempts >= RistrettoConstants.COMPILE_STATE_MAX_ATTEMPTS ? RistrettoConstants.COMPILE_STATE_MAX_ATTEMPTS_REACHED
                        : RistrettoConstants.COMPILE_STATE_INTERPRETED;
        while (RistrettoProfileSupport.COMPILATION_STATE_UPDATER.get(this) == RistrettoConstants.COMPILE_STATE_SUBMITTED) {
            if (RistrettoProfileSupport.COMPILATION_STATE_UPDATER.compareAndSet(this, RistrettoConstants.COMPILE_STATE_SUBMITTED, nextState)) {
                return;
            }
            PauseNode.pause();
        }
    }

    /**
     * Records that invocation-entry compilation hit a bailout that Graal declared non-retryable.
     *
     * Future profiling must not enqueue another invocation-entry compile for the same method; otherwise
     * the compile queue would repeatedly spend work on the same known-bailing graph.
     */
    public void onInvocationEntryPermanentBailout() {
        while (RistrettoProfileSupport.COMPILATION_STATE_UPDATER.get(this) == RistrettoConstants.COMPILE_STATE_SUBMITTED) {
            if (RistrettoProfileSupport.COMPILATION_STATE_UPDATER.compareAndSet(this, RistrettoConstants.COMPILE_STATE_SUBMITTED, RistrettoConstants.COMPILE_STATE_PERMANENT_BAILOUT)) {
                return;
            }
            PauseNode.pause();
        }
    }

    /**
     * Returns the mutable OSR trigger state for a precomputed loop backedge target.
     *
     * @param targetBCI bytecode index reached by a backward branch
     * @return the state for {@code targetBCI}, or {@code null} when this BCI is not an OSR entry
     *         candidate for the method
     */
    public RistrettoOSRBackedgeState getOSRBackedgeState(int targetBCI) {
        return osrBackedges.lookup(targetBCI);
    }

    /**
     * Atomically claims the right to submit one OSR compilation for {@code targetBCI}.
     *
     * @param targetBCI bytecode index reached by a backward branch
     * @return {@code true} for the single caller that may enqueue compilation; {@code false} for all
     *         concurrent or already-compiled attempts
     */
    public boolean claimOSRCompilation(int targetBCI) {
        return requireOSRBackedgeState(targetBCI).claimCompilation();
    }

    /**
     * Claims an OSR compilation and returns the request id that must be supplied by the eventual
     * completion callback.
     */
    public int claimOSRCompilationRequest(int targetBCI) {
        return requireOSRBackedgeState(targetBCI).claimCompilationRequest();
    }

    /**
     * Returns the current live OSR entry point for {@code targetBCI}, or a null pointer when no
     * installed code is ready to enter.
     */
    public CFunctionPointer getOSRInstalledCodeEntryPointIfLive(int targetBCI) {
        return requireOSRBackedgeState(targetBCI).installedCodeEntryPointIfLive();
    }

    public void onOSRCompilationSuccess(int targetBCI, int requestId, SubstrateInstalledCodeImpl code, boolean installCode) {
        requireOSRBackedgeState(targetBCI).onCompilationSuccess(this, requestId, code, installCode);
        if (installCode) {
            getProfile().resetOSRBackedgeCodePoll(targetBCI);
        }
    }

    public void onOSRCompilationFailure(int targetBCI, int requestId) {
        requireOSRBackedgeState(targetBCI).onCompilationFailure(requestId);
    }

    public void onOSRPermanentCompilationFailure(int targetBCI, int requestId) {
        requireOSRBackedgeState(targetBCI).onPermanentCompilationFailure(requestId);
    }

    public int getOSRCompilationAttempts(int targetBCI) {
        return requireOSRBackedgeState(targetBCI).compilationAttempts();
    }

    public boolean isOSRCompilationAttemptLimitReached(int targetBCI) {
        return requireOSRBackedgeState(targetBCI).isCompilationAttemptLimitReached();
    }

    /**
     * Returns the currently published installed code for an OSR backedge target.
     *
     * @param targetBCI bytecode index used as the OSR entry point
     * @return installed code for {@code targetBCI}, or {@code null} when no code is published
     */
    public SubstrateInstalledCodeImpl getOSRInstalledCode(int targetBCI) {
        return requireOSRBackedgeState(targetBCI).installedCode();
    }

    /**
     * Checks whether an OSR backedge target has live installed code with a non-zero entry point.
     *
     * @param targetBCI bytecode index used as the OSR entry point
     * @return {@code true} when OSR can enter compiled code for {@code targetBCI}
     */
    public boolean hasOSRInstalledCode(int targetBCI) {
        SubstrateInstalledCodeImpl code = getOSRInstalledCode(targetBCI);
        return code != null && code.getEntryPoint() != 0;
    }

    /**
     * Invalidates all OSR installed-code entries owned by this method.
     */
    public void invalidateOSRInstalledCode() {
        osrBackedges.invalidateAll();
        getProfile().resetOSRBackedgeProfiles();
    }

    /**
     * Looks up an OSR backedge state and fails loudly when callers use a non-OSR BCI.
     *
     * @param targetBCI bytecode index reached by a backward branch
     * @return the existing OSR state for {@code targetBCI}
     */
    private RistrettoOSRBackedgeState requireOSRBackedgeState(int targetBCI) {
        RistrettoOSRBackedgeState state = getOSRBackedgeState(targetBCI);
        if (state == null) {
            throw VMError.shouldNotReachHere("No OSR backedge state for " + this + "@" + targetBCI);
        }
        return state;
    }

    /**
     * Invalidates the method-global runtime-compilation state only if {@code expectedInstalledCode}
     * is still the current installed code for this method.
     *
     * This keeps stale invalidations and reprofiling requests from older code objects from
     * resetting the profile or compile state of a newer compilation.
     *
     * @param expectedInstalledCode the installed-code object that triggered invalidation
     * @param reprofile whether the invalidation should also reset interpreter profiling
     */
    public void invalidateInstalledCode(SubstrateInstalledCodeImpl expectedInstalledCode, boolean reprofile) {
        if (!INSTALLED_CODE_UPDATER.compareAndSet(this, expectedInstalledCode, null)) {
            invalidateOSRInstalledCode(expectedInstalledCode, reprofile);
            return;
        }
        RistrettoDiagnostics.InvalidatedCode.getAndIncrement();
        if (reprofile) {
            RistrettoDiagnostics.ReprofileRequested.getAndIncrement();
            getProfile().reprofile();
        }
        transitionToInterpreted();
    }

    /**
     * Invalidates a single OSR installed-code object if it is still published by this method.
     *
     * <pre>
     * for each precomputed backedge entry:
     *     if entry.installedCode == expectedInstalledCode:
     *         clear that entry's installed code and submit state
     *         optionally reset the method profile, otherwise reset only this OSR backedge profile
     *         report that the invalidation was consumed
     * </pre>
     *
     * This keeps stale invalidations from one OSR target from clearing unrelated OSR compilations for
     * other loop backedges in the same method.
     */
    private boolean invalidateOSRInstalledCode(SubstrateInstalledCodeImpl expectedInstalledCode, boolean reprofile) {
        int invalidatedTargetBCI = osrBackedges.invalidateInstalledCode(expectedInstalledCode);
        if (invalidatedTargetBCI >= 0) {
            RistrettoDiagnostics.InvalidatedCode.getAndIncrement();
            if (reprofile) {
                RistrettoDiagnostics.ReprofileRequested.getAndIncrement();
                getProfile().reprofile();
                getProfile().resetOSRBackedgeProfile(invalidatedTargetBCI);
            } else {
                getProfile().resetOSRBackedgeProfile(invalidatedTargetBCI);
            }
            return true;
        }
        return false;
    }

    public void invalidate() {
        RistrettoDiagnostics.InvalidatedCode.getAndIncrement();
        // directly go back to interpreted if possible
        transitionToInterpreted();
        INSTALLED_CODE_UPDATER.set(this, null);
        invalidateOSRInstalledCode();
    }

    @Override
    public void reprofile() {
        RistrettoDiagnostics.ReprofileRequested.getAndIncrement();
        // first overwrite profile and then transition back to interpreted
        getProfile().reprofile();
        transitionToInterpreted();
    }

    public MethodProfile getProfile() {
        if (profile == null) {
            initializeProfile();
        }
        return profile;
    }

    @Override
    public ProfilingInfo getProfilingInfo() {
        return new RistrettoProfilingInfo(getProfile());
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        // TODO GR-71494 - OSR support
        return getProfilingInfo();
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        return speculationLog;
    }

    public RistrettoSpeculationLog getSubstrateSpeculationLog() {
        return speculationLog;
    }

    public void recordDeoptimization(DeoptimizationReason reason) {
        getProfile().recordDeoptimization(reason);
    }

    /**
     * Allocate the profile once per method. Apart from test scenarios the profile is never set to
     * null again. Thus, the heavy locking code below is normally not run in a fast path.
     */
    private synchronized void initializeProfile() {
        if (profile == null) {
            MethodProfile newProfile = new MethodProfile(this, RistrettoType.RISTRETTO_TYPE_FUNCTION);
            // ensure everything is allocated and initialized before we signal the barrier
            // for the publishing write
            MembarNode.memoryBarrier(MembarNode.FenceKind.STORE_STORE);
            profile = newProfile;
        }
    }

    public synchronized void resetProfile() {
        profile = null;
    }

    /**
     * Resets root and OSR compile-attempt state for runtime tests that reuse JVMCI method objects.
     */
    public void resetCompilationStateForTesting() {
        compilationState = RistrettoConstants.COMPILE_STATE_INIT_VAL;
        COMPILATION_ATTEMPTS_UPDATER.set(this, 0);
        osrBackedges.resetCompilationStateForTesting();
    }

    @Override
    public boolean shouldBeInlined() {
        // TODO GR-74452: preserve AOT ForceInline-annotated methods.
        // TODO GR-74415: interpreter methods do not preserve ForceInline.
        return false;
    }

    @Override
    public boolean canBeInlined() {
        InterpreterResolvedJavaMethod interpreter = this.getInterpreterMethod();
        if (!RistrettoUtils.wasAOTCompiled(interpreter)) {
            // until GR-71589 is fixed we assume every other method can be inlined
            return RistrettoUtils.runtimeBytecodesAvailable(interpreter);
        }
        return RistrettoUtils.canInlineAOT(interpreter);
    }

    @Override
    public boolean canBeStaticallyBound() {
        return interpreterMethod.canBeStaticallyBound();
    }

    @Override
    public String getName() {
        return interpreterMethod.getName();
    }

    @Override
    public byte[] getCode() {
        return interpreterMethod.getCode();
    }

    @Override
    public int getCodeSize() {
        return interpreterMethod.getCodeSize();
    }

    @Override
    public int getMaxStackSize() {
        return interpreterMethod.getMaxStackSize();
    }

    @Override
    public int getMaxLocals() {
        return interpreterMethod.getMaxLocals();
    }

    @Override
    public ConstantPool getConstantPool() {
        if (ristrettoConstantPool == null) {
            ristrettoConstantPool = RistrettoConstantPool.create(this);
        }
        return ristrettoConstantPool;
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return interpreterMethod.getLineNumberTable();
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return interpreterMethod.asStackTraceElement(bci);
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return interpreterMethod.getLocalVariableTable();
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        if (rHandlers == null) {
            synchronized (this) {
                if (rHandlers == null) {
                    ExceptionHandler[] iHandlers = interpreterMethod.getExceptionHandlers();
                    ExceptionHandler[] effectiveRHandlers = new ExceptionHandler[iHandlers.length];
                    for (int i = 0; i < iHandlers.length; i++) {
                        final ExceptionHandler iHandler = iHandlers[i];
                        final JavaType catchType = iHandler.getCatchType();
                        if (catchType instanceof ResolvedJavaType) {
                            assert catchType instanceof InterpreterResolvedJavaType;
                            InterpreterResolvedJavaType iCatchType = (InterpreterResolvedJavaType) catchType;
                            RistrettoType rType = RistrettoType.getOrCreate(iCatchType);
                            effectiveRHandlers[i] = new ExceptionHandler(iHandler.getStartBCI(), iHandler.getEndBCI(), iHandler.getHandlerBCI(), iHandler.catchTypeCPI(), rType);
                        } else {
                            effectiveRHandlers[i] = iHandler;
                        }
                    }
                    rHandlers = effectiveRHandlers;
                }
            }
        }
        return rHandlers;
    }

    @Override
    public Signature getSignature() {
        return new RistrettoUnresolvedSignature(interpreterMethod.getSignature());
    }

    @Override
    public String toString() {
        return "RistrettoMethod{super=" + super.toString() + ", interpreterMethod=" + interpreterMethod + "}";
    }

    @Override
    public SubstrateType getDeclaringClass() {
        return RistrettoType.getOrCreate(interpreterMethod.getDeclaringClass());
    }

    @Override
    public int getModifiers() {
        return interpreterMethod.getModifiers();
    }

    @Override
    public boolean isVarArgs() {
        return interpreterMethod.isVarArgs();
    }

    @Override
    public boolean isInterface() {
        return interpreterMethod.isInterface();
    }

    @Override
    public boolean isSynchronized() {
        return interpreterMethod.isSynchronized();
    }

    @Override
    public boolean isStatic() {
        return interpreterMethod.isStatic();
    }

    @Override
    public boolean isConstructor() {
        return interpreterMethod.isConstructor();
    }

    @Override
    public boolean isFinalFlagSet() {
        return interpreterMethod.isFinalFlagSet();
    }

    @Override
    public boolean isPublic() {
        return interpreterMethod.isPublic();
    }

    @Override
    public boolean isPackagePrivate() {
        return interpreterMethod.isPackagePrivate();
    }

    @Override
    public boolean isPrivate() {
        return interpreterMethod.isPrivate();
    }

    @Override
    public boolean isProtected() {
        return interpreterMethod.isProtected();
    }

    @Override
    public boolean isTransient() {
        return interpreterMethod.isTransient();
    }

    @Override
    public boolean isStrict() {
        return interpreterMethod.isStrict();
    }

    @Override
    public boolean isVolatile() {
        return interpreterMethod.isVolatile();
    }

    @Override
    public boolean isNative() {
        return interpreterMethod.isNative();
    }

    @Override
    public boolean isAbstract() {
        return interpreterMethod.isAbstract();
    }

    @Override
    public boolean isConcrete() {
        return interpreterMethod.isConcrete();
    }

    @Override
    public CFunctionPointer getAOTEntrypoint() {
        assert !SubstrateUtil.HOSTED;
        assert SubstrateOptions.useRistretto();
        assert interpreterMethod.hasNativeEntryPoint();
        return interpreterMethod.getNativeEntryPoint();
    }

    public SubstrateMethod getOriginalRuntimeMethod() {
        return originalRuntimeMethod;
    }

    public void setOriginalRuntimeMethod(SubstrateMethod sMethod) {
        assert originalRuntimeMethod == null;
        if (originalRuntimeMethod == null) {
            synchronized (this) {
                if (originalRuntimeMethod == null) {
                    originalRuntimeMethod = sMethod;
                }
            }
        }
    }

    /**
     * AOT-compiled Ristretto methods can deoptimize to baseline-style target code because they
     * already have the necessary AOT metadata, while runtime-compiled-only methods must resume in
     * the interpreter.
     */
    @Override
    public DeoptTargetTier getDeoptTargetTier() {
        assert !SubstrateUtil.HOSTED && SubstrateOptions.useRistretto();
        return RistrettoUtils.wasAOTCompiled(this) ? DeoptTargetTier.BaselineCompiledCode : DeoptTargetTier.Interpreter;
    }

}
