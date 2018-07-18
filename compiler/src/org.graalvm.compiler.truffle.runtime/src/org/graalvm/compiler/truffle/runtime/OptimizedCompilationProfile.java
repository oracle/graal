/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleArgumentTypeSpeculation;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleCompileImmediately;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleInvalidationReprofileCount;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleMinInvokeThreshold;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleReplaceReprofileCount;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleReturnTypeSpeculation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerOptions;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public class OptimizedCompilationProfile {

    /**
     * Number of times an installed code for this tree was seen invalidated.
     */
    private int invalidationCount;
    private int deferredCount;

    private int interpreterCallCount;
    private int interpreterCallAndLoopCount;
    private int compilationCallThreshold;
    private int compilationCallAndLoopThreshold;

    private long timestamp;

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

    public OptimizedCompilationProfile(OptionValues options) {
        int callThreshold = TruffleCompilerOptions.getValue(TruffleMinInvokeThreshold);
        int callAndLoopThreshold = PolyglotCompilerOptions.getValue(options, PolyglotCompilerOptions.CompilationThreshold);
        assert callThreshold >= 0;
        assert callAndLoopThreshold >= 0;
        this.compilationCallThreshold = Math.min(callThreshold, callAndLoopThreshold);
        this.compilationCallAndLoopThreshold = callAndLoopThreshold;
        this.timestamp = System.nanoTime();
    }

    @Override
    public String toString() {
        return String.format("CompilationProfile(callCount=%d/%d, callAndLoopCount=%d/%d)", interpreterCallCount, compilationCallThreshold, interpreterCallAndLoopCount,
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
            profiledArgumentTypesAssumption = createInvalidAssumption("Profiled Argument Types");
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
            profiledReturnTypeAssumption = createInvalidAssumption("Profiled Return Type");
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

    final void profileReturnValue(Object result) {
        Assumption returnTypeAssumption = profiledReturnTypeAssumption;
        if (CompilerDirectives.inInterpreter() && returnTypeAssumption == null) {
            // we only profile return values in the interpreter as we don't want to deoptimize
            // for immediate compiles.
            if (TruffleCompilerOptions.getValue(TruffleReturnTypeSpeculation)) {
                profiledReturnType = classOf(result);
                profiledReturnTypeAssumption = createValidAssumption("Profiled Return Type");
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
    final <E extends Throwable> E profileExceptionType(E ex) {
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

    final Object[] injectArgumentProfile(Object[] originalArguments) {
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
        for (int i = 0; i < types.length; i++) {
            castArguments[i] = types[i] != null ? OptimizedCallTarget.unsafeCast(originalArguments[i], types[i], true, true, true) : originalArguments[i];
        }
        return castArguments;
    }

    final Object injectReturnValueProfile(Object result) {
        Class<?> klass = profiledReturnType;
        if (klass != null && CompilerDirectives.inCompiledCode() && profiledReturnTypeAssumption.isValid()) {
            return OptimizedCallTarget.unsafeCast(result, klass, true, true, true);
        }
        return result;
    }

    final void reportCompilationFailure() {
        compilationFailed = true;
    }

    final void reportLoopCount(int count) {
        interpreterCallAndLoopCount += count;
    }

    final void reportInvalidated() {
        invalidationCount++;
        int reprofile = TruffleCompilerOptions.getValue(TruffleInvalidationReprofileCount);
        ensureProfiling(reprofile, reprofile);
    }

    final void reportNodeReplaced() {
        // delay compilation until tree is deemed stable enough
        int replaceBackoff = TruffleCompilerOptions.getValue(TruffleReplaceReprofileCount);
        ensureProfiling(1, replaceBackoff);
    }

    final boolean interpreterCall(OptimizedCallTarget callTarget) {
        int intCallCount = ++interpreterCallCount;
        int intAndLoopCallCount = ++interpreterCallAndLoopCount;
        if (!callTarget.isCompiling() && !compilationFailed) {
            // check if call target is hot enough to get compiled, but took not too long to get hot
            if ((intAndLoopCallCount >= compilationCallAndLoopThreshold && intCallCount >= compilationCallThreshold && !isDeferredCompile(callTarget)) ||
                            TruffleCompilerOptions.getValue(TruffleCompileImmediately)) {
                return callTarget.compile();
            }
        }
        return false;
    }

    private boolean isDeferredCompile(OptimizedCallTarget target) {
        // Workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=440019
        int threshold = target.getOptionValue(PolyglotCompilerOptions.QueueTimeThreshold);

        CompilerOptions compilerOptions = target.getCompilerOptions();
        if (compilerOptions instanceof GraalCompilerOptions) {
            threshold = Math.max(threshold, ((GraalCompilerOptions) compilerOptions).getMinTimeThreshold());
        }
        if (threshold <= 0) {
            return false;
        }

        long time = timestamp;
        if (time == 0) {
            return false;
        }

        long timeElapsed = System.nanoTime() - time;
        if (timeElapsed > (threshold * 1_000_000L)) {

            int callThreshold = TruffleCompilerOptions.getValue(TruffleMinInvokeThreshold);
            int callAndLoopThreshold = PolyglotCompilerOptions.getValue(target.getRootNode(), PolyglotCompilerOptions.CompilationThreshold);

            // defer compilation
            ensureProfiling(0, Math.min(callThreshold, callAndLoopThreshold));
            timestamp = System.nanoTime();
            deferredCount++;
            return true;
        }
        return false;
    }

    private void initializeProfiledArgumentTypes(Object[] args) {
        CompilerAsserts.neverPartOfCompilation();
        if (TruffleCompilerOptions.getValue(TruffleArgumentTypeSpeculation)) {
            Class<?>[] result = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                result[i] = classOf(args[i]);
            }
            profiledArgumentTypes = result;
        }
        profiledArgumentTypesAssumption = createValidAssumption("Profiled Argument Types");
    }

    private void updateProfiledArgumentTypes(Object[] args, Class<?>[] types) {
        CompilerAsserts.neverPartOfCompilation();
        profiledArgumentTypesAssumption.invalidate();
        for (int j = 0; j < types.length; j++) {
            types[j] = joinTypes(types[j], classOf(args[j]));
        }
        profiledArgumentTypesAssumption = createValidAssumption("Profiled Argument Types");
    }

    private static boolean checkProfiledArgumentTypes(Object[] args, Class<?>[] types) {
        assert types != null;
        if (args.length != types.length) {
            throw new ArrayIndexOutOfBoundsException();
        }
        for (int j = 0; j < types.length; j++) {
            // throws ClassCast on error
            types[j].cast(args[j]);
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

    private void ensureProfiling(int calls, int callsAndLoop) {
        int increaseCallAndLoopThreshold = callsAndLoop - (this.compilationCallAndLoopThreshold - this.interpreterCallAndLoopCount);
        if (increaseCallAndLoopThreshold > 0) {
            this.compilationCallAndLoopThreshold += increaseCallAndLoopThreshold;
        }

        int increaseCallsThreshold = calls - (this.compilationCallThreshold - this.interpreterCallCount);
        if (increaseCallsThreshold > 0) {
            this.compilationCallThreshold += increaseCallsThreshold;
        }
    }

    public Map<String, Object> getDebugProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        String callsThreshold = String.format("%7d/%5d", getInterpreterCallCount(), getCompilationCallThreshold());
        String loopsThreshold = String.format("%7d/%5d", getInterpreterCallAndLoopCount(), getCompilationCallAndLoopThreshold());
        String invalidations = String.format("%5d", invalidationCount);
        properties.put("Calls/Thres", callsThreshold);
        properties.put("CallsAndLoop/Thres", loopsThreshold);
        properties.put("Inval#", invalidations);
        return properties;
    }

    public int getInvalidationCount() {
        return invalidationCount;
    }

    public int getInterpreterCallAndLoopCount() {
        return interpreterCallAndLoopCount;
    }

    public int getInterpreterCallCount() {
        return interpreterCallCount;
    }

    public int getDeferredCount() {
        return deferredCount;
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
