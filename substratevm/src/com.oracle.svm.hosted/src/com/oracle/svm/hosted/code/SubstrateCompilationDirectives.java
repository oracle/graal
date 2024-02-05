/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredImageSingleton
public class SubstrateCompilationDirectives {

    public static boolean isRuntimeCompiledMethod(ResolvedJavaMethod method) {
        if (method instanceof MultiMethod multiMethod) {
            return multiMethod.getMultiMethodKey() == RUNTIME_COMPILED_METHOD;
        }
        return false;
    }

    public static final MultiMethod.MultiMethodKey RUNTIME_COMPILED_METHOD = new MultiMethod.MultiMethodKey() {
        @Override
        public String toString() {
            return "Runtime_Compiled_Method_Key";
        }
    };

    /**
     * Stores the value kinds present at a deoptimization point's (deoptimization source)
     * FrameState. This information is used to validate the deoptimization point's target
     * (deoptimization entry point).
     */
    public static final class DeoptSourceFrameInfo {
        public final JavaKind[] expectedKinds;
        public final int numLocals;
        public final int numStack;
        public final int numLocks;

        public static final DeoptSourceFrameInfo INVALID_DEOPT_SOURCE_FRAME = new DeoptSourceFrameInfo(null, 0, 0, 0);

        private DeoptSourceFrameInfo(JavaKind[] expectedKinds, int numLocals, int numStack, int numLocks) {
            this.expectedKinds = expectedKinds;
            this.numLocals = numLocals;
            this.numStack = numStack;
            this.numLocks = numLocks;
        }

        public static DeoptSourceFrameInfo create(FrameState state) {
            return new DeoptSourceFrameInfo(getKinds(state), state.localsSize(), state.stackSize(), state.locksSize());
        }

        private static JavaKind[] getKinds(FrameState state) {
            JavaKind[] kinds = new JavaKind[state.locksSize() + state.stackSize() + state.localsSize()];
            int index = 0;
            for (int i = 0; i < state.localsSize(); i++) {
                kinds[index++] = getKind(state.localAt(i));
            }
            for (int i = 0; i < state.stackSize(); i++) {
                kinds[index++] = getKind(state.stackAt(i));
            }
            for (int i = 0; i < state.locksSize(); i++) {
                kinds[index++] = getKind(state.lockAt(i));
            }
            return kinds;
        }

        private static JavaKind getKind(ValueNode value) {
            if (value == null) {
                return JavaKind.Illegal;
            } else {
                return value.getStackKind();
            }
        }

        /**
         * Potentially there are multiple deoptimization points which target the same deoptimization
         * entry point. If so, the state used at the deoptimization entry point must be a subset of
         * the intersection of all potential deoptimization points.
         */
        public DeoptSourceFrameInfo mergeStateInfo(FrameState state) {
            if (this == INVALID_DEOPT_SOURCE_FRAME) {
                // nothing do to
                return this;
            }

            JavaKind[] otherKinds = getKinds(state);

            boolean matchingSizes = numLocals == state.localsSize() &&
                            numStack == state.stackSize() &&
                            numLocks == state.locksSize() &&
                            expectedKinds.length == otherKinds.length;
            if (!matchingSizes) {
                return INVALID_DEOPT_SOURCE_FRAME;
            }

            for (int i = 0; i < expectedKinds.length; i++) {
                JavaKind current = expectedKinds[i];
                JavaKind other = otherKinds[i];

                if (current != JavaKind.Illegal && current != other) {
                    /*
                     * The deopt target cannot have a value in this slot which matches all seen
                     * frame states.
                     */
                    expectedKinds[i] = JavaKind.Illegal;
                }
            }
            return this;
        }
    }

    private boolean deoptInfoSealed = false;
    private boolean forceCompilationsSealed = false;

    private final Set<AnalysisMethod> forcedCompilations = ConcurrentHashMap.newKeySet();
    private final Set<AnalysisMethod> frameInformationRequired = ConcurrentHashMap.newKeySet();
    private Map<AnalysisMethod, Map<Long, DeoptSourceFrameInfo>> deoptEntries = new ConcurrentHashMap<>();
    private final Set<AnalysisMethod> deoptForTestingMethods = ConcurrentHashMap.newKeySet();
    private final Set<AnalysisMethod> deoptInliningExcludes = ConcurrentHashMap.newKeySet();

    public static SubstrateCompilationDirectives singleton() {
        return ImageSingletons.lookup(SubstrateCompilationDirectives.class);
    }

    public void sealDeoptimizationInfo() {
        deoptInfoSealed = true;
    }

    public void registerForcedCompilation(ResolvedJavaMethod method) {
        assert !forceCompilationsSealed;
        forcedCompilations.add(toAnalysisMethod(method));
    }

    public boolean isForcedCompilation(ResolvedJavaMethod method) {
        if (Assertions.assertionsEnabled()) {
            forceCompilationsSealed = true;
        }
        return forcedCompilations.contains(toAnalysisMethod(method));
    }

    public void registerFrameInformationRequired(AnalysisMethod frameMethod, AnalysisMethod deoptMethod) {
        assert deoptInfoModifiable();
        frameInformationRequired.add(frameMethod);
        /*
         * Frame information is matched using the deoptimization entry point of a method. So in
         * addition to requiring frame information, we also need to mark the method as a
         * deoptimization target. No bci needs to be registered, it is enough to have a non-null
         * value in the map.
         */
        deoptEntries.computeIfAbsent(deoptMethod, m -> new ConcurrentHashMap<>());
    }

    public boolean isFrameInformationRequired(ResolvedJavaMethod method) {
        return frameInformationRequired.contains(toAnalysisMethod(method));
    }

    /**
     * @return whether this was a new frame state seen.
     */
    public boolean registerDeoptEntry(FrameState state, ResolvedJavaMethod method) {
        assert deoptInfoModifiable();
        assert state.bci >= 0;
        long encodedBci = FrameInfoEncoder.encodeBci(state.bci, state.getStackState());

        Map<Long, DeoptSourceFrameInfo> sourceFrameInfoMap = deoptEntries.computeIfAbsent(toAnalysisMethod(method), m -> new ConcurrentHashMap<>());

        boolean newEntry = !sourceFrameInfoMap.containsKey(encodedBci);

        sourceFrameInfoMap.compute(encodedBci, (k, v) -> v == null ? DeoptSourceFrameInfo.create(state) : v.mergeStateInfo(state));

        return newEntry;
    }

    /*
     * Register a method which can deopt for testing
     */
    public void registerForDeoptTesting(ResolvedJavaMethod method) {
        assert deoptInfoModifiable();
        deoptForTestingMethods.add((AnalysisMethod) method);
    }

    public boolean isRegisteredForDeoptTesting(ResolvedJavaMethod method) {
        return deoptForTestingMethods.contains(toAnalysisMethod(method));
    }

    public boolean isRegisteredDeoptTarget(ResolvedJavaMethod method) {
        return deoptEntries.containsKey(toAnalysisMethod(method));
    }

    public void registerDeoptTarget(ResolvedJavaMethod method) {
        assert deoptInfoModifiable();
        deoptEntries.computeIfAbsent(toAnalysisMethod(method), m -> new ConcurrentHashMap<>());
    }

    public boolean isDeoptEntry(MultiMethod method, int bci, FrameState.StackState stackState) {
        if (method instanceof HostedMethod && ((HostedMethod) method).getMultiMethod(MultiMethod.ORIGINAL_METHOD).compilationInfo.canDeoptForTesting()) {
            return true;
        }

        return isRegisteredDeoptEntry(method, bci, stackState);
    }

    public boolean isRegisteredDeoptEntry(MultiMethod method, int bci, FrameState.StackState stackState) {
        Map<Long, DeoptSourceFrameInfo> bciMap = deoptEntries.get(toAnalysisMethod((ResolvedJavaMethod) method));
        assert bciMap != null : "can only query for deopt entries for methods registered as deopt targets";

        long encodedBci = FrameInfoEncoder.encodeBci(bci, stackState);
        return bciMap.containsKey(encodedBci);
    }

    public void registerAsDeoptInlininingExclude(ResolvedJavaMethod method) {
        assert deoptInfoModifiable();
        deoptInliningExcludes.add(toAnalysisMethod(method));
    }

    public boolean isDeoptInliningExclude(ResolvedJavaMethod method) {
        return deoptInliningExcludes.contains(toAnalysisMethod(method));
    }

    public Map<AnalysisMethod, Map<Long, DeoptSourceFrameInfo>> getDeoptEntries() {
        return deoptEntries;
    }

    private static AnalysisMethod toAnalysisMethod(ResolvedJavaMethod method) {
        if (method instanceof AnalysisMethod) {
            return (AnalysisMethod) method;
        } else if (method instanceof HostedMethod) {
            return ((HostedMethod) method).wrapped;
        } else {
            throw VMError.shouldNotReachHereUnexpectedInput(method); // ExcludeFromJacocoGeneratedReport
        }
    }

    private boolean deoptInfoModifiable() {
        return !deoptInfoSealed;
    }

    /**
     * We record the deoptimization information twice: once during analysis and then again during
     * compilation. The information recorded during compilation will be strictly a subset of the
     * information recorded during analysis.
     */
    public void resetDeoptEntries() {
        assert !deoptInfoSealed;
        // all methods which are registered for deopt testing cannot be cleared
        Map<AnalysisMethod, Map<Long, DeoptSourceFrameInfo>> newDeoptEntries = new ConcurrentHashMap<>();
        for (var deoptForTestingMethod : deoptForTestingMethods) {
            var key = deoptForTestingMethod.getMultiMethod(MultiMethod.DEOPT_TARGET_METHOD);
            var value = deoptEntries.get(key);
            assert key != null && value != null : "Unexpected null value " + key + ", " + value;
            newDeoptEntries.put(key, value);
        }
        deoptEntries = newDeoptEntries;
        // all methods which require frame information must have a deoptimization entry
        frameInformationRequired.forEach(m -> {
            assert m.isOriginalMethod();
            var deoptMethod = m.getMultiMethod(MultiMethod.DEOPT_TARGET_METHOD);
            assert deoptMethod != null;
            deoptEntries.computeIfAbsent(deoptMethod, n -> new ConcurrentHashMap<>());
        });
    }
}
