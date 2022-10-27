/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredImageSingleton
public class SubstrateCompilationDirectives {

    public static final MultiMethod.MultiMethodKey DEOPT_TARGET_METHOD = new MultiMethod.MultiMethodKey() {
        @Override
        public String toString() {
            return "Deopt_Target_Method_Key";
        }
    };

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

    protected boolean sealed;

    private final Set<AnalysisMethod> forcedCompilations = ConcurrentHashMap.newKeySet();
    private final Set<AnalysisMethod> frameInformationRequired = ConcurrentHashMap.newKeySet();
    private final Map<AnalysisMethod, Map<Long, DeoptSourceFrameInfo>> deoptEntries = new ConcurrentHashMap<>();
    private final Set<AnalysisMethod> deoptInliningExcludes = ConcurrentHashMap.newKeySet();

    public static SubstrateCompilationDirectives singleton() {
        return ImageSingletons.lookup(SubstrateCompilationDirectives.class);
    }

    public void registerForcedCompilation(ResolvedJavaMethod method) {
        assert !sealed;
        forcedCompilations.add(toAnalysisMethod(method));
    }

    public boolean isForcedCompilation(ResolvedJavaMethod method) {
        assert seal();
        return forcedCompilations.contains(toAnalysisMethod(method));
    }

    public void registerFrameInformationRequired(AnalysisMethod method) {
        assert !sealed;
        frameInformationRequired.add(method);
        /*
         * Frame information is matched using the deoptimization entry point of a method. So in
         * addition to requiring frame information, we also need to mark the method as a
         * deoptimization target. No bci needs to be registered, it is enough to have a non-null
         * value in the map.
         */
        deoptEntries.computeIfAbsent(method, m -> new ConcurrentHashMap<>());
    }

    public boolean isFrameInformationRequired(ResolvedJavaMethod method) {
        assert seal();
        return frameInformationRequired.contains(toAnalysisMethod(method));
    }

    public void registerDeoptEntry(FrameState state) {
        assert !sealed;
        assert state.bci >= 0;
        long encodedBci = FrameInfoEncoder.encodeBci(state.bci, state.duringCall(), state.rethrowException());

        Map<Long, DeoptSourceFrameInfo> sourceFrameInfoMap = deoptEntries.computeIfAbsent(toAnalysisMethod(state.getMethod()), m -> new ConcurrentHashMap<>());
        sourceFrameInfoMap.compute(encodedBci, (k, v) -> v == null ? DeoptSourceFrameInfo.create(state) : v.mergeStateInfo(state));
    }

    public boolean isDeoptTarget(ResolvedJavaMethod method) {
        assert seal();
        return deoptEntries.containsKey(toAnalysisMethod(method));
    }

    public boolean isDeoptEntry(ResolvedJavaMethod method, int bci, boolean duringCall, boolean rethrowException) {
        assert seal();
        Map<Long, DeoptSourceFrameInfo> bciMap = deoptEntries.get(toAnalysisMethod(method));
        assert bciMap != null : "can only query for deopt entries for methods registered as deopt targets";

        long encodedBci = FrameInfoEncoder.encodeBci(bci, duringCall, rethrowException);
        return bciMap.containsKey(encodedBci);
    }

    public void registerAsDeoptInlininingExclude(ResolvedJavaMethod method) {
        assert !sealed;
        deoptInliningExcludes.add(toAnalysisMethod(method));
    }

    public boolean isDeoptInliningExclude(ResolvedJavaMethod method) {
        assert seal();
        return deoptInliningExcludes.contains(toAnalysisMethod(method));
    }

    public Map<AnalysisMethod, Map<Long, DeoptSourceFrameInfo>> getDeoptEntries() {
        assert seal();
        return deoptEntries;
    }

    private static AnalysisMethod toAnalysisMethod(ResolvedJavaMethod method) {
        if (method instanceof AnalysisMethod) {
            return (AnalysisMethod) method;
        } else if (method instanceof HostedMethod) {
            return ((HostedMethod) method).wrapped;
        } else {
            throw VMError.shouldNotReachHere();
        }
    }

    private boolean seal() {
        sealed = true;
        return true;
    }
}
