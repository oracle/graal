/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public final class OptimizedCompilationProfile {
    private static final int MAX_PROFILED_ARGUMENTS = 256;
    private static final String ARGUMENT_TYPES_ASSUMPTION_NAME = "Profiled Argument Types";
    private static final String RETURN_TYPE_ASSUMPTION_NAME = "Profiled Return Type";

    /**
     * Number of times an installed code for this tree was seen invalidated.
     */
    private int invalidationCount;

    private int callCount;
    private int callAndLoopCount;
    private int lastTierCompilationCallAndLoopThreshold;
    private boolean multiTierEnabled;

    private long timestamp;

    // The values below must only be written under lock, or in the constructor.
    private int compilationCallThreshold;
    private int compilationCallAndLoopThreshold;

    /*
     * Updating profiling information and its Assumption objects is done without synchronization and
     * atomic operations to keep the overhead as low as possible. This means that there can be races
     * and we might see imprecise profiles. That is OK. But we must not install and run compiled
     * code that depends on wrong profiling information, because that leads to crashes. Therefore,
     * the Assumption objects need to be updated in the correct order: A new valid assumption is
     * installed *after* installing profile information, but the assumption is invalidated *before*
     * invalidating profile information. This ensures that the compiler sees an invalidated
     * assumption in case a race happens. Note that OptimizedAssumption.invalidate() performs
     * synchronization and is therefore a memory barrier.
     */
    @CompilationFinal(dimensions = 1) private Class<?>[] profiledArgumentTypes;
    @CompilationFinal private OptimizedAssumption profiledArgumentTypesAssumption;
    @CompilationFinal private Class<?> profiledReturnType;
    @CompilationFinal private OptimizedAssumption profiledReturnTypeAssumption;
    @CompilationFinal private Class<?> exceptionType;

    private volatile boolean compilationFailed;
    @CompilationFinal private boolean callProfiled;

    public OptimizedCompilationProfile(OptionValues options) {
        boolean compileImmediately = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleCompileImmediately);
        int callThreshold = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleMinInvokeThreshold);
        int callAndLoopThreshold = PolyglotCompilerOptions.getValue(options, PolyglotCompilerOptions.CompilationThreshold);
        assert callThreshold >= 0;
        assert callAndLoopThreshold >= 0;
        this.multiTierEnabled = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleMultiTier);
        this.compilationCallThreshold = compileImmediately ? 0 : Math.min(callThreshold, callAndLoopThreshold);
        this.compilationCallAndLoopThreshold = compileImmediately ? 0 : callAndLoopThreshold;
        this.lastTierCompilationCallAndLoopThreshold = this.compilationCallAndLoopThreshold;
        if (multiTierEnabled) {
            int firstTierCallThreshold = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleFirstTierMinInvokeThreshold);
            int firstTierCallAndLoopThreshold = PolyglotCompilerOptions.getValue(options, PolyglotCompilerOptions.FirstTierCompilationThreshold);
            this.compilationCallThreshold = compileImmediately ? 0 : Math.min(firstTierCallThreshold, firstTierCallAndLoopThreshold);
            this.compilationCallAndLoopThreshold = firstTierCallAndLoopThreshold;
        }
        this.timestamp = System.nanoTime();
    }

    @Override
    public String toString() {
        return String.format("CompilationProfile(callCount=%d/%d, callAndLoopCount=%d/%d)", callCount, compilationCallThreshold, callAndLoopCount,
                        compilationCallAndLoopThreshold);
    }

    void initializeArgumentTypes(Class<?>[] argumentTypes) {
        CompilerAsserts.neverPartOfCompilation();
        if (profiledArgumentTypesAssumption != null) {
            this.profiledArgumentTypesAssumption.invalidate();
            throw new AssertionError("Argument types already initialized. initializeArgumentTypes must be called before any profile is initialized.");
        } else {
            this.profiledArgumentTypes = argumentTypes;
            this.profiledArgumentTypesAssumption = createValidAssumption("Custom profiled argument types");
            this.callProfiled = true;
        }
    }

    List<OptimizedAssumption> getProfiledTypesAssumptions() {
        List<OptimizedAssumption> result = new ArrayList<>();
        if (getProfiledArgumentTypes() != null) {
            result.add(profiledArgumentTypesAssumption);
        }
        if (getProfiledReturnType() != null) {
            result.add(profiledReturnTypeAssumption);
        }
        return result;
    }

    Class<?>[] getProfiledArgumentTypes() {
        if (profiledArgumentTypesAssumption == null) {
            /*
             * We always need an assumption. If this method is called before the profile was
             * initialized, we have to be conservative and disable profiling, which is done by
             * creating an invalid assumption but leaving the type field null.
             */
            CompilerDirectives.transferToInterpreterAndInvalidate();
            profiledArgumentTypesAssumption = createInvalidAssumption(ARGUMENT_TYPES_ASSUMPTION_NAME);
        }

        if (profiledArgumentTypesAssumption.isValid()) {
            return profiledArgumentTypes;
        } else {
            return null;
        }
    }

    Class<?> getProfiledReturnType() {
        if (profiledReturnTypeAssumption == null) {
            /*
             * We always need an assumption. If this method is called before the profile was
             * initialized, we have to be conservative and disable profiling, which is done by
             * creating an invalid assumption but leaving the type field null.
             */
            CompilerDirectives.transferToInterpreterAndInvalidate();
            profiledReturnTypeAssumption = createInvalidAssumption(RETURN_TYPE_ASSUMPTION_NAME);
        }

        if (profiledReturnTypeAssumption.isValid()) {
            return profiledReturnType;
        } else {
            return null;
        }
    }

    @ExplodeLoop
    void profileDirectCall(Object[] args) {
        Assumption typesAssumption = profiledArgumentTypesAssumption;
        if (typesAssumption == null) {
            if (CompilerDirectives.inInterpreter()) {
                initializeProfiledArgumentTypes(args);
            }
        } else {
            Class<?>[] types = profiledArgumentTypes;
            if (types != null) {
                if (types.length != args.length) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    typesAssumption.invalidate();
                    profiledArgumentTypes = null;
                } else if (typesAssumption.isValid()) {
                    for (int i = 0; i < types.length; i++) {
                        Class<?> type = types[i];
                        Object value = args[i];
                        if (type != null && (value == null || value.getClass() != type)) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            updateProfiledArgumentTypes(args, types);
                            break;
                        }
                    }
                }
            }
        }
    }

    void profileIndirectCall() {
        Assumption argumentTypesAssumption = profiledArgumentTypesAssumption;
        if (argumentTypesAssumption != null && argumentTypesAssumption.isValid()) {
            // Argument profiling is not possible for targets of indirect calls.
            CompilerDirectives.transferToInterpreter();
            argumentTypesAssumption.invalidate();
            profiledArgumentTypes = null;
        }
    }

    void profileInlinedCall() {
        // nothing to profile for inlined calls by default
    }

    void profileReturnValue(Object result) {
        Assumption returnTypeAssumption = profiledReturnTypeAssumption;
        if (CompilerDirectives.inInterpreter() && returnTypeAssumption == null) {
            // we only profile return values in the interpreter as we don't want to deoptimize
            // for immediate compiles.
            if (TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleReturnTypeSpeculation)) {
                profiledReturnType = classOf(result);
                profiledReturnTypeAssumption = createValidAssumption(RETURN_TYPE_ASSUMPTION_NAME);
            }
        } else if (profiledReturnType != null) {
            if (result == null || profiledReturnType != result.getClass()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                returnTypeAssumption.invalidate();
                profiledReturnType = null;
            }
        }
    }

    @SuppressWarnings("unchecked")
    <E extends Throwable> E profileExceptionType(E ex) {
        Class<?> cachedClass = exceptionType;
        // if cachedClass is null and we are not in the interpreter we don't want to deoptimize
        // This usually happens only if the call target was compiled using compile without ever
        // calling it.
        if (cachedClass != Object.class) {
            if (cachedClass != null && cachedClass == ex.getClass()) {
                return (E) cachedClass.cast(ex);
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (cachedClass == null) {
                exceptionType = ex.getClass();
            } else {
                // object class is not reachable for exceptions
                exceptionType = Object.class;
            }
        }
        return ex;
    }

    Object[] injectArgumentProfile(Object[] originalArguments) {
        Assumption argumentTypesAssumption = profiledArgumentTypesAssumption;
        Object[] args = originalArguments;
        if (argumentTypesAssumption != null && argumentTypesAssumption.isValid()) {
            args = OptimizedCallTarget.unsafeCast(OptimizedCallTarget.castArrayFixedLength(args, profiledArgumentTypes.length), Object[].class, true, true, true);
            args = castArgumentsImpl(args);
        }
        return args;
    }

    @ExplodeLoop
    private Object[] castArgumentsImpl(Object[] originalArguments) {
        Class<?>[] types = profiledArgumentTypes;
        Object[] castArguments = new Object[types.length];
        boolean isCallProfiled = callProfiled;
        for (int i = 0; i < types.length; i++) {
            // callProfiled: only the receiver type is exact.
            Class<?> targetType = types[i];
            boolean exact = !isCallProfiled || i == 0;
            castArguments[i] = targetType != null ? OptimizedCallTarget.unsafeCast(originalArguments[i], targetType, true, true, exact) : originalArguments[i];
        }
        return castArguments;
    }

    Object injectReturnValueProfile(Object result) {
        Class<?> klass = profiledReturnType;
        if (klass != null && CompilerDirectives.inCompiledCode() && profiledReturnTypeAssumption.isValid()) {
            return OptimizedCallTarget.unsafeCast(result, klass, true, true, true);
        }
        return result;
    }

    void reportCompilationFailure() {
        compilationFailed = true;
    }

    void reportLoopCount(int count) {
        callAndLoopCount += count;
    }

    void reportCompilationIgnored() {
        compilationFailed = true;
    }

    void reportInvalidated() {
        invalidationCount++;
        int reprofile = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleInvalidationReprofileCount);
        ensureProfiling(reprofile, reprofile);
    }

    void reportNodeReplaced() {
        // delay compilation until tree is deemed stable enough
        int replaceBackoff = TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleReplaceReprofileCount);
        ensureProfiling(1, replaceBackoff);
    }

    @CompilerDirectives.TruffleBoundary
    private static boolean firstTierCompile(OptimizedCallTarget callTarget) {
        return callTarget.compile(true);
    }

    boolean firstTierCall(OptimizedCallTarget callTarget) {
        // The increment and the check must be inlined into the compilation unit.
        int totalCallCount = ++callCount;
        if (totalCallCount >= lastTierCompilationCallAndLoopThreshold && !callTarget.isCompiling() && !compilationFailed) {
            return firstTierCompile(callTarget);
        }
        return false;
    }

    @SuppressWarnings("try")
    boolean interpreterCall(OptimizedCallTarget callTarget) {
        int intCallCount = ++callCount;
        int intAndLoopCallCount = ++callAndLoopCount;
        // Check if call target is hot enough to compile, but took not too long to get hot.
        int callThreshold = compilationCallThreshold; // 0 if TruffleCompileImmediately
        if (callThreshold == 0 || (intCallCount >= callThreshold //
                        && intAndLoopCallCount >= compilationCallAndLoopThreshold //
                        && !compilationFailed && !callTarget.isCompiling())) {
            return callTarget.compile(!multiTierEnabled);
        }
        return false;
    }

    private void initializeProfiledArgumentTypes(Object[] args) {
        CompilerAsserts.neverPartOfCompilation();
        if (args.length <= MAX_PROFILED_ARGUMENTS && TruffleRuntimeOptions.getValue(SharedTruffleRuntimeOptions.TruffleArgumentTypeSpeculation)) {
            Class<?>[] result = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                result[i] = classOf(args[i]);
            }
            profiledArgumentTypes = result;
            profiledArgumentTypesAssumption = createValidAssumption(ARGUMENT_TYPES_ASSUMPTION_NAME);
        } else {
            profiledArgumentTypesAssumption = createInvalidAssumption(ARGUMENT_TYPES_ASSUMPTION_NAME);
        }
    }

    private void updateProfiledArgumentTypes(Object[] args, Class<?>[] types) {
        CompilerAsserts.neverPartOfCompilation();
        profiledArgumentTypesAssumption.invalidate();
        for (int j = 0; j < types.length; j++) {
            types[j] = joinTypes(types[j], classOf(args[j]));
        }
        profiledArgumentTypesAssumption = createValidAssumption(ARGUMENT_TYPES_ASSUMPTION_NAME);
    }

    private static boolean checkProfiledArgumentTypes(Object[] args, Class<?>[] types) {
        assert types != null;
        if (args.length != types.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        // receiver type is always non-null and exact
        if (types[0] != args[0].getClass()) {
            throw new ClassCastException();
        }
        // other argument types may be inexact
        for (int j = 1; j < types.length; j++) {
            if (types[j] == null) {
                continue;
            }
            types[j].cast(args[j]);
            Objects.requireNonNull(args[j]);
        }
        return true;
    }

    private static Class<?> classOf(Object arg) {
        return arg != null ? arg.getClass() : null;
    }

    private static Class<?> joinTypes(Class<?> class1, Class<?> class2) {
        if (class1 == class2) {
            return class1;
        } else {
            return null;
        }
    }

    private synchronized void ensureProfiling(int calls, int callsAndLoop) {
        if (this.compilationCallThreshold == 0) { // TruffleCompileImmediately
            return;
        }
        int increaseCallAndLoopThreshold = callsAndLoop - Math.max(0, this.compilationCallAndLoopThreshold - this.callAndLoopCount);
        if (increaseCallAndLoopThreshold > 0) {
            this.compilationCallAndLoopThreshold += increaseCallAndLoopThreshold;
        }

        int increaseCallsThreshold = calls - Math.max(0, this.compilationCallThreshold - this.callCount);
        if (increaseCallsThreshold > 0) {
            this.compilationCallThreshold += increaseCallsThreshold;
        }
    }

    public Map<String, Object> getDebugProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        String callsThreshold = String.format("%7d/%5d", getCallCount(), getCompilationCallThreshold());
        String loopsThreshold = String.format("%7d/%5d", getCallAndLoopCount(), getCompilationCallAndLoopThreshold());
        String invalidations = String.format("%5d", invalidationCount);
        properties.put("Calls/Thres", callsThreshold);
        properties.put("CallsAndLoop/Thres", loopsThreshold);
        properties.put("Inval#", invalidations);
        return properties;
    }

    public int getInvalidationCount() {
        return invalidationCount;
    }

    public int getCallAndLoopCount() {
        return callAndLoopCount;
    }

    public int getCallCount() {
        return callCount;
    }

    public int getCompilationCallAndLoopThreshold() {
        return compilationCallAndLoopThreshold;
    }

    public int getCompilationCallThreshold() {
        return compilationCallThreshold;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public static OptimizedCompilationProfile create(OptionValues options) {
        return new OptimizedCompilationProfile(options);
    }

    private static OptimizedAssumption createValidAssumption(String name) {
        return (OptimizedAssumption) Truffle.getRuntime().createAssumption(name);
    }

    private static OptimizedAssumption createInvalidAssumption(String name) {
        OptimizedAssumption result = createValidAssumption(name);
        result.invalidate();
        return result;
    }

    public boolean isValidArgumentProfile(Object[] args) {
        return profiledArgumentTypesAssumption != null && profiledArgumentTypesAssumption.isValid() && checkProfiledArgumentTypes(args, profiledArgumentTypes);
    }
}
