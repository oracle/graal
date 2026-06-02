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

    /**
     * Pointer to the current runtime-compiled code for this method.
     *
     * Stale invalidation requests must claim this field atomically before mutating method-global
     * state so older installed-code objects cannot clobber newer compilations.
     */
    public volatile SubstrateInstalledCodeImpl installedCode;

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
                    return;
                case RistrettoConstants.COMPILE_STATE_COMPILED:
                    if (RistrettoProfileSupport.COMPILATION_STATE_UPDATER.compareAndSet(this, RistrettoConstants.COMPILE_STATE_COMPILED, RistrettoConstants.COMPILE_STATE_INTERPRETED)) {
                        RistrettoProfileSupport.trace(RistrettoOptions.JITTraceCompilationQueuing, "[Ristretto Method]Transitioned to INTERPRETED from COMPILED for %s%n", this);
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
        while (RistrettoProfileSupport.COMPILATION_STATE_UPDATER.get(this) == RistrettoConstants.COMPILE_STATE_SUBMITTED) {
            if (RistrettoProfileSupport.COMPILATION_STATE_UPDATER.compareAndSet(this, RistrettoConstants.COMPILE_STATE_SUBMITTED, RistrettoConstants.COMPILE_STATE_INTERPRETED)) {
                return;
            }
            PauseNode.pause();
        }
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
            return;
        }
        RistrettoDiagnostics.InvalidatedCode.getAndIncrement();
        if (reprofile) {
            RistrettoDiagnostics.ReprofileRequested.getAndIncrement();
            getProfile().reprofile();
        }
        transitionToInterpreted();
    }

    public void invalidate() {
        RistrettoDiagnostics.InvalidatedCode.getAndIncrement();
        // directly go back to interpreted if possible
        transitionToInterpreted();
        INSTALLED_CODE_UPDATER.set(this, null);
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
