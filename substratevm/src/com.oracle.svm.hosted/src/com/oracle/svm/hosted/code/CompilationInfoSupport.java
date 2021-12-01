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

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CompilationInfoSupport {

    /**
     * Stores the value kinds present at a deoptimization point's (deoptimization source)
     * FrameState. This information is used to validate the deoptimization point's target
     * (deoptimization entry point).
     */
    public static final class DeoptSourceFrameInfo {
        public final List<JavaKind> expectedKinds;
        public final int numLocals;
        public final int numStack;
        public final int numLocks;

        private DeoptSourceFrameInfo(List<JavaKind> expectedKinds, int numLocals, int numStack, int numLocks) {
            this.expectedKinds = expectedKinds;
            this.numLocals = numLocals;
            this.numStack = numStack;
            this.numLocks = numLocks;
        }

        public static DeoptSourceFrameInfo create(FrameState state) {
            return new DeoptSourceFrameInfo(getKinds(state), state.localsSize(), state.stackSize(), state.locksSize());
        }

        private static List<JavaKind> getKinds(FrameState state) {
            return state.values().stream().map(DeoptSourceFrameInfo::getKind).collect(Collectors.toList());
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
            List<JavaKind> otherKinds = getKinds(state);

            boolean matchingSizes = numLocals == state.localsSize() &&
                            numStack == state.stackSize() &&
                            numLocks == state.locksSize() &&
                            expectedKinds.size() == otherKinds.size();
            if (!matchingSizes) {
                StringBuilder errorMessage = new StringBuilder();
                errorMessage.append("Unexpected number of values in state to merge. Please report this problem.\n");
                errorMessage.append(String.format("****Merge FrameState****\n%s************************\n", state.toString(Verbosity.Debugger)));
                errorMessage.append(String.format("bci: %d, duringCall: %b, rethrowException: %b\n", state.bci, state.duringCall(), state.rethrowException()));
                errorMessage.append(String.format("DeoptSourceFrameInfo: locals-%d, stack-%d, locks-%d.\n", numLocals, numStack, numLocks));
                errorMessage.append(String.format("Merge FrameState: locals-%d, stack-%d, locks-%d.\n", state.localsSize(), state.stackSize(), state.locksSize()));
                throw VMError.shouldNotReachHere(errorMessage.toString());
            }

            for (int i = 0; i < expectedKinds.size(); i++) {
                JavaKind current = expectedKinds.get(i);
                JavaKind other = otherKinds.get(i);

                if (current != JavaKind.Illegal && current != other) {
                    /*
                     * The deopt target cannot have a value in this slot which matches all seen
                     * frame states.
                     */
                    expectedKinds.set(i, JavaKind.Illegal);
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

    public static CompilationInfoSupport singleton() {
        return ImageSingletons.lookup(CompilationInfoSupport.class);
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

    protected boolean isDeoptEntry(ResolvedJavaMethod method, int bci, boolean duringCall, boolean rethrowException) {
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

@AutomaticFeature
class CompilationInfoFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(CompilationInfoSupport.class, new CompilationInfoSupport());
    }
}
