/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.lir.RedundantMoveElimination;
import org.graalvm.compiler.lir.alloc.RegisterAllocationPhase;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.BoxNodeOptimizationPhase;
import org.graalvm.compiler.phases.common.FixReadsPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.LowTierContext;
import org.graalvm.compiler.phases.tiers.MidTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.graalvm.compiler.virtual.phases.ea.ReadEliminationPhase;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.deopt.DeoptEntryInfopoint;
import com.oracle.svm.core.deopt.DeoptTest;
import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.code.StubCallingConvention;
import com.oracle.svm.core.graal.nodes.DeoptTestNode;
import com.oracle.svm.core.graal.snippets.DeoptTester;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.InfopointReason;

class DeoptimizationUtils {

    /**
     * Inserts a call to {@link DeoptTester#deoptTest} right after FixedWithNextNode StateSplits.
     *
     * @param method method that is being augmented with deopt test calls
     * @param graph The graph of a deoptimizable method or the corresponding deopt target method.
     */
    static void insertDeoptTests(HostedMethod method, StructuredGraph graph) {
        for (Node node : graph.getNodes()) {
            if (node instanceof FixedWithNextNode && node instanceof StateSplit && !(node instanceof InvokeNode) && !(node instanceof ForeignCallNode) && !(node instanceof DeoptTestNode) &&
                            !(method.isSynchronized() && node instanceof StartNode)) {
                FixedWithNextNode fixedWithNext = (FixedWithNextNode) node;
                FixedNode next = fixedWithNext.next();
                DeoptTestNode testNode = graph.add(new DeoptTestNode());
                fixedWithNext.setNext(null);
                testNode.setNext(next);
                fixedWithNext.setNext(testNode);
                if (((StateSplit) node).hasSideEffect() && ((StateSplit) node).stateAfter() != null) {
                    testNode.setStateAfter(((StateSplit) node).stateAfter().duplicateWithVirtualState());
                } else {
                    testNode.setStateAfter(GraphUtil.findLastFrameState((FixedNode) node).duplicateWithVirtualState());
                }
            }
        }
    }

    private static boolean containsStackValueNode(HostedUniverse universe, HostedMethod method) {
        return universe.getBigBang().getHostVM().containsStackValueNode(method.wrapped);
    }

    /**
     * Returns true if a method should be considered as deoptimization source. This is only a
     * feature for testing. Note that usually all image compiled methods cannot deoptimize.
     */
    static boolean canDeoptForTesting(HostedUniverse universe, HostedMethod method, boolean deoptimizeAll) {
        if (method.getName().equals("<clinit>")) {
            /* Cannot deoptimize into static initializers. */
            return false;
        }

        if (method.getAnnotation(DeoptTest.class) != null) {
            return true;
        }

        if (method.isEntryPoint()) {
            /*
             * Entry points from C have special entry/exit nodes added, so they cannot be
             * deoptimized.
             */
            return false;
        }
        if (method.isNative()) {
            /*
             * Native methods (i.e., the stubs that actually perform the native calls) cannot be
             * deoptimized.
             */
            return false;
        }
        if (method.wrapped.isIntrinsicMethod()) {
            return false;
        }
        if (Uninterruptible.Utils.isUninterruptible(method)) {
            return false;
        }
        if (method.getAnnotation(RestrictHeapAccess.class) != null) {
            return false;
        }
        if (StubCallingConvention.Utils.hasStubCallingConvention(method)) {
            /* Deoptimization runtime cannot fill the callee saved registers. */
            return false;
        }
        if (containsStackValueNode(universe, method)) {
            /*
             * Stack allocated memory is not seen by the deoptimization code, i.e., it is not copied
             * in case of deoptimization. Also, pointers to it can be used for arbitrary address
             * arithmetic, so we would not know how to update derived pointers into stack memory
             * during deoptimization. Therefore, we cannot allow methods that allocate stack memory
             * for runtime compilation. To remove this limitation, we would need to change how we
             * handle stack allocated memory in Graal.
             */
            return false;
        }

        if (deoptimizeAll) {
            /*
             * The DeoptimizeAll option is set. So we use all methods for deoptimization testing.
             * Exclude some "runtime" methods, like the heap code, via this blacklist. Issue GR-1706
             * tracks the bug in DebugValueMap.
             */
            String className = method.getDeclaringClass().getName();
            if (className.contains("/svm/core/code/CodeInfoEncoder") ||
                            className.contains("com/oracle/svm/core/thread/JavaThreads") ||
                            className.contains("com/oracle/svm/core/thread/PlatformThreads") ||
                            className.contains("com/oracle/svm/core/heap/") ||
                            className.contains("com/oracle/svm/core/genscavenge/") ||
                            className.contains("com/oracle/svm/core/thread/VMOperationControl") ||
                            className.contains("debug/internal/DebugValueMap") && method.getName().equals("registerTopLevel")) {
                return false;
            }
            /*
             * Method without bytecodes, e.g., methods that have a manually constructed graph, are
             * usually not deoptimizable. This needs to change as soon as we want to runtime compile
             * our synthetic annotation methods.
             */
            if (method.getCode() == null) {
                return false;
            }

            return true;
        } else {
            return false;
        }
    }

    static void removeDeoptTargetOptimizations(Suites suites) {
        GraalConfiguration.hostedInstance().removeDeoptTargetOptimizations(suites);

        PhaseSuite<HighTierContext> highTier = suites.getHighTier();
        highTier.removePhase(PartialEscapePhase.class);
        highTier.removePhase(ReadEliminationPhase.class);
        highTier.removePhase(BoxNodeOptimizationPhase.class);
        PhaseSuite<MidTierContext> midTier = suites.getMidTier();
        midTier.removePhase(FloatingReadPhase.class);
        PhaseSuite<LowTierContext> lowTier = suites.getLowTier();
        ListIterator<BasePhase<? super LowTierContext>> it = lowTier.findPhase(FixReadsPhase.class);
        if (it != null) {
            FixReadsPhase fixReads = (FixReadsPhase) it.previous();
            it.remove();
            boolean replaceInputsWithConstants = false;
            it.add(new FixReadsPhase(replaceInputsWithConstants, fixReads.getSchedulePhase()));
        }
    }

    static void removeDeoptTargetOptimizations(LIRSuites lirSuites) {
        ListIterator<LIRPhase<PostAllocationOptimizationPhase.PostAllocationOptimizationContext>> it = lirSuites.getPostAllocationOptimizationStage().findPhase(RedundantMoveElimination.class);
        if (it != null) {
            it.remove();
        }
        lirSuites.getAllocationStage().findPhaseInstance(RegisterAllocationPhase.class).setNeverSpillConstants(true);
    }

    static boolean verifyDeoptTarget(HostedMethod method, StructuredGraph graph, CompilationResult result) {
        Map<Long, BytecodeFrame> encodedBciMap = new HashMap<>();

        /*
         * All deopt targets must have a graph.
         */
        assert graph != null : "Deopt target must have a graph.";

        /*
         * No deopt targets can have a StackValueNode in the graph.
         */
        assert graph.getNodes(StackValueNode.TYPE).isEmpty() : "No stack value nodes must be present in deopt target.";

        for (Infopoint infopoint : result.getInfopoints()) {
            if (infopoint.debugInfo != null) {
                DebugInfo debugInfo = infopoint.debugInfo;
                if (!debugInfo.hasFrame()) {
                    continue;
                }
                BytecodeFrame topFrame = debugInfo.frame();

                BytecodeFrame rootFrame = topFrame;
                while (rootFrame.caller() != null) {
                    rootFrame = rootFrame.caller();
                }
                assert rootFrame.getMethod().equals(method);

                boolean isDeoptEntry = method.compilationInfo.isDeoptEntry(rootFrame.getBCI(), rootFrame.duringCall, rootFrame.rethrowException);
                if (infopoint instanceof DeoptEntryInfopoint) {
                    assert isDeoptEntry;
                } else if (rootFrame.duringCall && isDeoptEntry) {
                    assert infopoint instanceof Call || isSingleSteppingInfopoint(infopoint);
                } else {
                    continue;
                }

                long encodedBci = FrameInfoEncoder.encodeBci(rootFrame.getBCI(), rootFrame.duringCall, rootFrame.rethrowException);
                if (encodedBciMap.containsKey(encodedBci)) {
                    assert encodedBciMap.get(encodedBci).equals(rootFrame) : "duplicate encoded bci " + encodedBci + " in deopt target " + method + " with different debug info:\n\n" + rootFrame +
                                    "\n\n" + encodedBciMap.get(encodedBci);
                }
                encodedBciMap.put(encodedBci, rootFrame);
            }
        }

        return true;
    }

    static boolean canBeUsedForInlining(HostedUniverse universe, HostedMethod caller, HostedMethod callee, int bci, boolean deoptimizeAll) {
        if (DeoptimizationUtils.canDeoptForTesting(universe, caller, deoptimizeAll) && Modifier.isNative(callee.getModifiers())) {
            /*
             * We must not deoptimize in the stubs for native functions, since they don't have a
             * valid bytecode state.
             */
            return false;
        }
        if (DeoptimizationUtils.canDeoptForTesting(universe, caller, deoptimizeAll) && DeoptimizationUtils.containsStackValueNode(universe, callee)) {
            /*
             * We must not inline a method that has stack values and can be deoptimized.
             *
             * Stack allocated memory is not seen by the deoptimization code, i.e., it is not copied
             * in case of deoptimization. Also, pointers to it can be used for arbitrary address
             * arithmetic, so we would not know how to update derived pointers into stack memory
             * during deoptimization. Therefore, we cannot allow methods that allocate stack memory
             * for runtime compilation. To remove this limitation, we would need to change how we
             * handle stack allocated memory in Graal.
             */
            return false;
        }

        if (caller.isDeoptTarget()) {
            if (caller.compilationInfo.isDeoptEntry(bci, true, false)) {
                /*
                 * The call can be on the stack for a deoptimization, so we need an actual
                 * non-inlined invoke to deoptimize too.
                 *
                 * We could lift this restriction by providing an explicit deopt entry point (with
                 * the correct exception handling edges) in addition to the inlined method.
                 */
                return false;
            }
            if (SubstrateCompilationDirectives.singleton().isDeoptInliningExclude(callee)) {
                /*
                 * The graphs for runtime compilation have an intrinisic for the callee, which might
                 * alter the behavior. Be safe and do not inline, otherwise we might optimize too
                 * aggressively.
                 *
                 * For example, the Truffle method CompilerDirectives.inCompiledCode is
                 * intrinisified to return a constant with the opposite value than returned by the
                 * method we would inline here, i.e., we would constant-fold away the compiled-code
                 * only code (which is the code we need deoptimization entry points for).
                 */
                return false;
            }
        }

        return true;
    }

    private static boolean isSingleSteppingInfopoint(Infopoint infopoint) {
        return infopoint.reason == InfopointReason.METHOD_START ||
                        infopoint.reason == InfopointReason.METHOD_END ||
                        infopoint.reason == InfopointReason.BYTECODE_POSITION;
    }
}
