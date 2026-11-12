/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.api;

import java.util.Objects;

import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.auximage.AuxiliaryImageCodeObserver;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.guest.staging.c.function.InvokeJavaFunctionPointer;
import com.oracle.svm.shared.ReflectivelyAccessed;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.VMError;
import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.nodes.SlowPathException;
import com.oracle.truffle.runtime.EngineData;
import com.oracle.truffle.runtime.OptimizedAssumption;
import com.oracle.truffle.runtime.OptimizedCallTarget;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Truffle call target which can be partially evaluated and compiled to machine code, which is then
 * represented by {@link SubstrateOptimizedCallTargetInstalledCode}. At most one piece of code is
 * <em>valid</em> as an entry point for new invocations at any given time, but arbitrarily many
 * pieces of code for this call target can be <em>alive</em> at the same time (for example, from
 * previous lower-tier compilations).
 * <p>
 * Assume that methods such as {@link #isValid} or {@link #getCodeAddress} return stale values
 * because internal state can change at any safepoint. Consistent reads of such values require
 * ensuring the absence of safepoint checks and preventing floating reads and read elimination.
 *
 * @see TypedEntrySupport
 */
public final class SubstrateOptimizedCallTarget extends OptimizedCallTarget implements SubstrateCompilableTruffleAST, AuxiliaryImageCodeObserver {

    /**
     * Stores the most recently installed code, which is the only entry point for this call target
     * at one point in time (or not an entry point, if invalid). Must never be {@code null}.
     * <p>
     * Does not need to be volatile because it is modified only in safepoint operations. Reads of
     * this field must be done carefully so they cannot float across safepoint checks.
     */
    private SubstrateOptimizedCallTargetInstalledCode installedCode;

    /**
     * The entry point used by a caller that is compiled when the caller is performing a
     * type-specialized direct call. If {@link #installedCode} is valid, this field is its entry
     * point address, otherwise it is the entry point of {@link #specializedC2IAdapter} or the
     * {@linkplain TypedEntrySupport#lookupInterpreterEntryAddress interpreter entry point}.
     *
     * @see #setFromCompiledSpecializedEntryPoint()
     */
    @ReflectivelyAccessed //
    long fromCompiledSpecializedEntryPoint;

    /**
     * The entry point used by a caller that is compiled when the caller is performing a
     * non-specialized indirect call. If {@link #installedCode} is valid and its code is
     * unspecialized, this field is its entry point address, otherwise this field is 0.
     */
    @ReflectivelyAccessed //
    long fromCompiledUnspecializedEntryPoint;

    /**
     * The entry point used by a caller that is not compiled. This field is an I2C adapter if
     * {@link #installedCode} is valid, or 0 otherwise.
     */
    long fromInterpreterEntryPoint;

    /** The C2I adapter, to be used when the compiled code gets invalidated. */
    private SubstrateInstalledCode specializedC2IAdapter;

    @SuppressWarnings("this-escape")
    public SubstrateOptimizedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode rootNode) {
        super(sourceCallTarget, rootNode);
        this.installedCode = new SubstrateOptimizedCallTargetInstalledCode(this);
    }

    @SuppressWarnings("this-escape")
    public SubstrateOptimizedCallTarget(EngineData engine) {
        super(engine);
        this.installedCode = new SubstrateOptimizedCallTargetInstalledCode(this);
    }

    @SuppressWarnings("sync-override")
    @Override
    public SubstrateSpeculationLog getSpeculationLog() {
        return (SubstrateSpeculationLog) super.getSpeculationLog();
    }

    @Override
    public SpeculationLog getCompilationSpeculationLog() {
        return getSpeculationLog();
    }

    @Override
    public boolean isValid() {
        // Only the most recently installed code can be valid, which entails being an entry point.
        return installedCode.isValid();
    }

    @Override
    public boolean isValidLastTier() {
        return installedCode.isValidLastTier();
    }

    @Override
    public long getCodeAddress() {
        return installedCode.getAddress();
    }

    @Override
    public boolean cancelCompilation(CharSequence reason) {
        if (SubstrateTruffleOptions.isMultiThreaded()) {
            return super.cancelCompilation(reason);
        }
        return false;
    }

    /** Accessor for {@link TypedEntrySupport}. */
    ArgumentsProfile getArgumentsProfile() {
        return getInitializedArgumentsProfile();
    }

    /** Accessor for {@link TypedEntrySupport}. */
    ReturnProfile getReturnProfile() {
        return getInitializedReturnProfile();
    }

    /**
     * Installs a new C2I adapter for this call target. This method is called during compilation of
     * a caller, i.e., a quite random time but strictly before the compiled code of the caller gets
     * installed. In particular, it does not run inside of a VMOperation.
     * <p>
     * We need to be careful because the profiling information can change at any time, and multiple
     * callers can be compiled concurrently. If the assumption of the profile is already invalid,
     * the adapter must not be installed: the adapter for a newer profile could already have been
     * installed by another thread, and we must not overwrite that newer adapter.
     * <p>
     * This method is synchronized to prevent the following race condition: Compiler thread A enters
     * this method with a valid assumption. After the assumption check but before setting the
     * specializedC2IAdapter field thread A is suspended by the OS. The assumption gets invalidated,
     * and a new profile gets installed. Compiler thread B enters this method with the new valid
     * assumption, installs the adapter, and returns. Now thread A start running again. Even though
     * its assumption is by now invalid, it overwrites the specializedC2IAdapter with an outdated
     * value.
     */
    synchronized void installSpecializedC2IAdapter(SubstrateInstalledCode specializedAdapter, Assumption argumentsProfileAssumption, Assumption returnTypeProfileAssumption) {
        if (specializedAdapter == null || (argumentsProfileAssumption.isValid() && (returnTypeProfileAssumption == null || returnTypeProfileAssumption.isValid()))) {
            specializedC2IAdapter = specializedAdapter;
            setFromCompiledSpecializedEntryPoint();
        }
    }

    void onCodeInstalled(SubstrateOptimizedCallTargetInstalledCode code) {
        VMOperation.guaranteeInProgressAtSafepoint("Must be at a safepoint");
        assert code != null : "Must never become null";
        if (code != installedCode) {
            installedCode.makeNonEntrant();
            installedCode = code;
        }

        TypedEntryMethod method = code.getMethod();

        JavaKind currentReturnKind = TypedEntrySupport.getSpecializedReturnKind(getArgumentsProfile(), getReturnProfile());
        JavaKind returnKind = method.getSignature().getReturnKind();
        if (returnKind != currentReturnKind) {
            throw VMError.shouldNotReachHere(String.format("A change in specialized return kind for call target %s from %s to %s " +
                            "should have been caught while validating assumptions", this, returnKind, currentReturnKind));
        }

        if (method.specialized) {
            this.fromCompiledSpecializedEntryPoint = code.getEntryPoint();
            this.fromCompiledUnspecializedEntryPoint = 0;
        } else {
            this.fromCompiledSpecializedEntryPoint = code.getEntryPoint();
            this.fromCompiledUnspecializedEntryPoint = code.getEntryPoint();
        }
        this.fromInterpreterEntryPoint = method.i2cAdapter.getEntryPoint();

        setFromCompiledSpecializedEntryPoint();
    }

    void onCodeCleared(SubstrateOptimizedCallTargetInstalledCode code) {
        VMOperation.guaranteeInProgressAtSafepoint("Must be at a safepoint");

        if (code == installedCode) { // could be older code and not the current entry point
            this.fromCompiledSpecializedEntryPoint = 0;
            this.fromCompiledUnspecializedEntryPoint = 0;
            this.fromInterpreterEntryPoint = 0;

            setFromCompiledSpecializedEntryPoint();
        }
    }

    @Override
    public void onAllAuxiliaryImageCodeInstalled() {
        // If this call target does not have valid compiled code, set its compiled specialized entry
        // point either to the C2I adapter, if available, or to the interpreter entry point.
        setFromCompiledSpecializedEntryPoint();
    }

    private void setFromCompiledSpecializedEntryPoint() {
        if (!installedCode.isValid()) {
            long entry = 0;
            if (specializedC2IAdapter != null) {
                entry = specializedC2IAdapter.getEntryPoint();
            }
            if (entry == 0) {
                entry = TypedEntrySupport.singleton().lookupInterpreterEntryAddress();
            }
            assert entry != 0;
            fromCompiledSpecializedEntryPoint = entry;
        } else {
            assert fromCompiledSpecializedEntryPoint == installedCode.getEntryPoint();
        }
    }

    @Override
    public SubstrateOptimizedCallTargetInstalledCode createPreliminaryInstalledCode() {
        return new SubstrateOptimizedCallTargetInstalledCode(this);
    }

    @Override
    public SubstrateOptimizedCallTargetInstalledCode createSubstrateInstalledCode() {
        assert SubstrateTruffleSupport.isIsolatedCompilation() : "Must be called only with isolated compilation";
        return new SubstrateOptimizedCallTargetInstalledCode(this);
    }

    @Override
    public Object doInvoke(Object[] args) {
        Object result;
        try {
            if (CompilerDirectives.inCompiledCode()) {
                /* C2C and C2I. */

                if (CompilerDirectives.isPartialEvaluationConstant(this)) {
                    /* Direct call. The compiled entry point can be the C2I adapter. */

                    ArgumentsProfile argumentsProfile = getArgumentsProfile();
                    CompilerAsserts.partialEvaluationConstant(argumentsProfile);
                    OptimizedAssumption argumentsProfileAssumption = argumentsProfile.getAssumption();
                    ReturnProfile returnProfile = getReturnProfile();
                    OptimizedAssumption returnProfileAssumption = returnProfile.getAssumption();

                    /*
                     * If we perform argument / return type specialization, we need to guarantee
                     * that the assumptions are registered with the compiled code. The easiest way
                     * for that is to call isValid, which is intrinsified to register the assumption
                     * when it is valid, and a no-op when the assumption is not valid.
                     *
                     * Note that the profile can change at any time during compilation. So the code
                     * in TypedEntrySupport that intrinsifies the invokeFromCompiledReturn* methods
                     * might read a later version of the profile. But in this case, it is guaranteed
                     * that an assumption that we register here is invalid, so the compiled code is
                     * not going to be installed if such a race happens.
                     */
                    CompilerAsserts.partialEvaluationConstant(argumentsProfileAssumption);
                    argumentsProfileAssumption.isValid();
                    CompilerAsserts.partialEvaluationConstant(returnProfileAssumption);
                    returnProfileAssumption.isValid();

                    Class<?> returnType = specializedReturnClass(argumentsProfile, returnProfile);
                    CompilerAsserts.partialEvaluationConstant(returnType);

                    if (returnType == Integer.class) {
                        result = invokeFromCompiledReturnInt(this, args);
                    } else if (returnType == Long.class) {
                        result = invokeFromCompiledReturnLong(this, args);
                    } else if (returnType == Double.class) {
                        result = invokeFromCompiledReturnDouble(this, args);
                    } else if (returnType == Boolean.class) {
                        result = invokeFromCompiledReturnBoolean(this, args);
                    } else {
                        result = invokeFromCompiledReturnObject(this, args);
                    }

                } else {
                    /*
                     * Indirect call. No type specialization is done (not even return type
                     * specialization because we do not know which callee is being invoked).
                     */
                    result = invokeFromCompiledReturnObject(this, args);
                }

            } else {
                /* Interpreter caller: use the I2C adapter when available. */
                result = invokeCompiledAdapterOrCallBoundary(args);
            }
        } catch (DeoptimizedResultException ex) {
            /*
             * The return type specialization failed. The actual return value is boxed in the
             * exception.
             */
            CompilerDirectives.transferToInterpreterAndInvalidate();
            result = ex.result;
        }
        return result;
    }

    @Uninterruptible(reason = "Transitioning back to interruptible code", calleeMustBe = false)
    private Object invokeCallBoundary(Object[] args) {
        return callBoundary(args);
    }

    /**
     * We have to be very careful that the adapter calling code is uninterruptible, i.e., has no
     * safepoint between the read of the compiled code address and the indirect call to this
     * address. Otherwise, the code can be invalidated concurrently and we invoke an address that no
     * longer contains executable code.
     *
     * To avoid the indirection via this uninterruptible method, we could also do a computed
     * indirect call that looks up the fields and checks them for 0 all in the call target
     * computation. But that requires a much more complicated expression language for computed
     * indirect calls that is currently not implemented.
     */
    @Uninterruptible(reason = "Must not have safepoints between reading the entry point address and invoking it")
    private Object invokeCompiledAdapterOrCallBoundary(Object[] args) throws DeoptimizedResultException {
        long entry = installedCode.getEntryPointUninterruptible();
        long adapter = fromInterpreterEntryPoint;
        if (entry != 0 && adapter != 0) {
            /* Invoke adapter code created during compilation. */
            I2CAdapterFunctionPointer adapterFunctionPointer = Word.pointer(adapter);
            return adapterFunctionPointer.invoke(this, entry, args);
        } else {
            /* I2I or C2I: normal Java call to the interpreter. */
            return invokeCallBoundary(args);
        }
    }

    interface I2CAdapterFunctionPointer extends CFunctionPointer {
        @InvokeJavaFunctionPointer
        Object invoke(SubstrateOptimizedCallTarget callTarget, long entryPoint, Object[] args) throws DeoptimizedResultException;
    }

    static Class<?>[] specializedArgumentClasses(ArgumentsProfile profile) {
        Objects.requireNonNull(profile);
        if (TypedEntrySupport.Options.UseTypedArguments.getValue()) {
            Class<?>[] argumentClasses = profile.getTypes();

            /*
             * The maximum number of arguments for specialized signatures must be below the limit of
             * escape analysis for virtualization of arrays. But in practice, a much lower limit is
             * useful anyway: there is no benefit of passing a high number of arguments on the stack
             * vs. in a heap array.
             *
             * Note that the length must be a compile-time constant because this method is reachable
             * during partial evaluation, and the check must constant fold.
             */
            int maxLength = 20;

            if (argumentClasses != null && argumentClasses.length < maxLength) {
                return argumentClasses;
            }
        }
        return null;
    }

    static Class<?> specializedReturnClass(ArgumentsProfile argumentsProfile, ReturnProfile returnProfile) {
        Objects.requireNonNull(returnProfile);
        if (TypedEntrySupport.Options.UseTypedResult.getValue()) {

            /* We only specialize the return type if we also specialize the argument types. */
            if (specializedArgumentClasses(argumentsProfile) != null) {
                Class<?> type = returnProfile.getType();
                if (type != null) {
                    return type;
                }
            }
        }
        return Object.class;
    }

    /*
     * The invokeFromCompiledReturn* methods are intrinsified during runtime compilation, and
     * unreachable from the interpreter. But the static analysis sees the method body.
     */

    @ReflectivelyAccessed
    static int invokeFromCompiledReturnInt(SubstrateOptimizedCallTarget callTarget, Object[] args) throws DeoptimizedResultException {
        return compiledEntryReturnInt(callTarget, args);
    }

    @ReflectivelyAccessed
    static long invokeFromCompiledReturnLong(SubstrateOptimizedCallTarget callTarget, Object[] args) throws DeoptimizedResultException {
        return compiledEntryReturnLong(callTarget, args);
    }

    @ReflectivelyAccessed
    static double invokeFromCompiledReturnDouble(SubstrateOptimizedCallTarget callTarget, Object[] args) throws DeoptimizedResultException {
        return compiledEntryReturnDouble(callTarget, args);
    }

    @ReflectivelyAccessed
    static boolean invokeFromCompiledReturnBoolean(SubstrateOptimizedCallTarget callTarget, Object[] args) throws DeoptimizedResultException {
        return compiledEntryReturnBoolean(callTarget, args);
    }

    @ReflectivelyAccessed
    static Object invokeFromCompiledReturnObject(SubstrateOptimizedCallTarget callTarget, Object[] args) {
        return compiledEntryReturnObject(callTarget, args);
    }

    /*
     * compiledEntryReturn* methods: The return-type-specialized methods that are used as the root
     * for partial evaluation.
     */

    @ReflectivelyAccessed
    @SuppressWarnings("all")
    static int compiledEntryReturnInt(SubstrateOptimizedCallTarget callTarget, Object[] args) throws DeoptimizedResultException {
        args = specializeArguments(callTarget, args);
        Object result = callTarget.profiledPERoot(args);
        if (result instanceof Integer && specializedReturnClass(callTarget.getArgumentsProfile(), callTarget.getReturnProfile()) == Integer.class) {
            return (int) result;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new DeoptimizedResultException(result);
        }
    }

    @ReflectivelyAccessed
    @SuppressWarnings("all")
    static long compiledEntryReturnLong(SubstrateOptimizedCallTarget callTarget, Object[] args) throws DeoptimizedResultException {
        args = specializeArguments(callTarget, args);
        Object result = callTarget.profiledPERoot(args);
        if (result instanceof Long && specializedReturnClass(callTarget.getArgumentsProfile(), callTarget.getReturnProfile()) == Long.class) {
            return (long) result;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new DeoptimizedResultException(result);
        }
    }

    @ReflectivelyAccessed
    @SuppressWarnings("all")
    static double compiledEntryReturnDouble(SubstrateOptimizedCallTarget callTarget, Object[] args) throws DeoptimizedResultException {
        args = specializeArguments(callTarget, args);
        Object result = callTarget.profiledPERoot(args);
        if (result instanceof Double && specializedReturnClass(callTarget.getArgumentsProfile(), callTarget.getReturnProfile()) == Double.class) {
            return (double) result;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new DeoptimizedResultException(result);
        }
    }

    @ReflectivelyAccessed
    @SuppressWarnings("all")
    static boolean compiledEntryReturnBoolean(SubstrateOptimizedCallTarget callTarget, Object[] args) throws DeoptimizedResultException {
        args = specializeArguments(callTarget, args);
        Object result = callTarget.profiledPERoot(args);
        if (result instanceof Boolean && specializedReturnClass(callTarget.getArgumentsProfile(), callTarget.getReturnProfile()) == Boolean.class) {
            return (boolean) result;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new DeoptimizedResultException(result);
        }
    }

    @ReflectivelyAccessed
    @SuppressWarnings("all")
    static Object compiledEntryReturnObject(SubstrateOptimizedCallTarget callTarget, Object[] args) {
        args = specializeArguments(callTarget, args);
        return callTarget.profiledPERoot(args);
    }

    /**
     * Intrinsified: collects the type-specialized parameters and reconstructs the arguments array
     * (which is then eliminated later on by escape analysis).
     */
    @ReflectivelyAccessed
    static Object[] specializeArguments(@SuppressWarnings("unused") SubstrateOptimizedCallTarget callTarget, Object[] args) {
        return args;
    }

    /*
     * interpreterEntryReturn* methods: The return-type-specialized methods that are called by the
     * C2I adapters.
     */

    @ReflectivelyAccessed
    static int interpreterEntryReturnInt(SubstrateOptimizedCallTarget callTarget, Object[] args) throws DeoptimizedResultException {
        Object result = callTarget.callBoundary(args);
        if (result instanceof Integer) {
            return (int) result;
        } else {
            throw new DeoptimizedResultException(result);
        }
    }

    @ReflectivelyAccessed
    static long interpreterEntryReturnLong(SubstrateOptimizedCallTarget callTarget, Object[] args) throws DeoptimizedResultException {
        Object result = callTarget.callBoundary(args);
        if (result instanceof Long) {
            return (long) result;
        } else {
            throw new DeoptimizedResultException(result);
        }
    }

    @ReflectivelyAccessed
    static double interpreterEntryReturnDouble(SubstrateOptimizedCallTarget callTarget, Object[] args) throws DeoptimizedResultException {
        Object result = callTarget.callBoundary(args);
        if (result instanceof Double) {
            return (double) result;
        } else {
            throw new DeoptimizedResultException(result);
        }
    }

    @ReflectivelyAccessed
    static boolean interpreterEntryReturnBoolean(SubstrateOptimizedCallTarget callTarget, Object[] args) throws DeoptimizedResultException {
        Object result = callTarget.callBoundary(args);
        if (result instanceof Boolean) {
            return (boolean) result;
        } else {
            throw new DeoptimizedResultException(result);
        }
    }

    @ReflectivelyAccessed
    static Object interpreterEntryReturnObject(SubstrateOptimizedCallTarget callTarget, Object[] args) {
        return callTarget.callBoundary(args);
    }

    /**
     * Entry point for compiled indirect calls when the callee does not have an unspecialized C2C
     * entry point. Re-enter interpreter-side dispatch without call-boundary profiling so that a
     * specialized compiled target is invoked through its adapter. If the target is no longer
     * compiled, normal call-boundary profiling applies.
     */
    @ReflectivelyAccessed
    static Object compiledIndirectEntryReturnObject(SubstrateOptimizedCallTarget callTarget, Object[] args) {
        return callTarget.doInvoke(args);
    }
}

@SuppressWarnings("serial")
final class DeoptimizedResultException extends SlowPathException {
    private static final long serialVersionUID = 8388271228226754748L;

    final Object result;

    DeoptimizedResultException(Object result) {
        this.result = result;
    }
}
