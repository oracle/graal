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

import static com.oracle.svm.common.meta.MultiMethod.DEOPT_TARGET_METHOD;

import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.MethodFlowsGraph;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.PointsToAnalysisMethod;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.deopt.DeoptEntryInfopoint;
import com.oracle.svm.core.deopt.DeoptTest;
import com.oracle.svm.core.graal.GraalConfiguration;
import com.oracle.svm.core.graal.code.StubCallingConvention;
import com.oracle.svm.core.graal.nodes.DeoptTestNode;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.graal.snippets.DeoptTester;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodePredicate;
import jdk.graal.compiler.graph.iterators.NodePredicates;
import jdk.graal.compiler.lir.RedundantMoveElimination;
import jdk.graal.compiler.lir.alloc.RegisterAllocationPhase;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.lir.phases.PostAllocationOptimizationPhase;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.BoxNodeOptimizationPhase;
import jdk.graal.compiler.phases.common.FixReadsPhase;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.replacements.nodes.MacroInvokable;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import jdk.graal.compiler.virtual.phases.ea.ReadEliminationPhase;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class DeoptimizationUtils {

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

    /**
     * Returns true if a method should be considered as deoptimization source. This is only a
     * feature for testing. Note that usually all image compiled methods cannot deoptimize.
     *
     * Note this should only be called within CompileQueue#parseAheadOfTimeCompiledMethods
     */
    public static boolean canDeoptForTesting(AnalysisMethod method, boolean deoptimizeAll, Supplier<Boolean> graphChecker) {
        if (SubstrateCompilationDirectives.singleton().isRegisteredForDeoptTesting(method)) {
            return true;
        }

        if (method.getName().equals("<clinit>")) {
            /* Cannot deoptimize into static initializers. */
            return false;
        }

        if (method.getAnnotation(DeoptTest.class) != null) {
            return true;
        }

        if (!deoptimizeAll) {
            /* When DeoptimizeAll is not set, then only methods marked via DeoptTest can deopt. */
            return false;
        }

        if (!graphChecker.get()) {
            return false;
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
        if (method.isIntrinsicMethod()) {
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
         * usually not deoptimizable. This needs to change as soon as we want to runtime compile our
         * synthetic annotation methods.
         */
        if (method.getCode() == null) {
            return false;
        }

        return true;

    }

    private static boolean containsStackValueNode(HostedUniverse universe, HostedMethod method) {
        return universe.getBigBang().getHostVM().containsStackValueNode(method.wrapped);
    }

    /**
     * Returns true if a method should be considered as deoptimization source. This is only a
     * feature for testing. Note that usually all image compiled methods cannot deoptimize.
     */
    static boolean canDeoptForTesting(HostedUniverse universe, HostedMethod method, boolean deoptimizeAll) {
        return canDeoptForTesting(method.wrapped, deoptimizeAll, () -> containsStackValueNode(universe, method));
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

    public static boolean isDeoptEntry(HostedMethod method, CompilationResult compilation, Infopoint infopoint) {
        BytecodeFrame topFrame = infopoint.debugInfo.frame();
        BytecodeFrame rootFrame = topFrame;
        while (rootFrame.caller() != null) {
            rootFrame = rootFrame.caller();
        }
        assert rootFrame.getMethod().equals(method);

        boolean isBciDeoptEntry = method.compilationInfo.isDeoptEntry(rootFrame.getBCI(), FrameState.StackState.of(rootFrame));
        if (isBciDeoptEntry) {
            /*
             * When an infopoint's bci corresponds to a deoptimization entrypoint, it does not
             * necessarily mean that the infopoint itself is for a deoptimization entrypoint. This
             * is because the infopoint can also be for present debugging purposes and happen to
             * have the same bci. Further checks are needed to determine actual deoptimization
             * entrypoints.
             */
            assert topFrame == rootFrame : "Deoptimization target has inlined frame: " + topFrame;
            if (topFrame.duringCall) {
                /*
                 * During call entrypoints must always be linked to a call.
                 */
                VMError.guarantee(infopoint instanceof Call, "Unexpected infopoint type: %s%nFrame: %s", infopoint, topFrame);
                return compilation.isValidCallDeoptimizationState((Call) infopoint);
            } else {
                /*
                 * Other deoptimization entrypoints correspond to an DeoptEntryOp.
                 */
                return infopoint instanceof DeoptEntryInfopoint;
            }
        }

        return false;
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
        assert createGraphChecker(graph, AOT_COMPILATION_INVALID_NODES).get() : "Invalid nodes in deopt target: " + graph;

        for (Infopoint infopoint : result.getInfopoints()) {
            if (infopoint.debugInfo != null) {
                DebugInfo debugInfo = infopoint.debugInfo;
                if (!debugInfo.hasFrame()) {
                    continue;
                }

                if (isDeoptEntry(method, result, infopoint)) {
                    BytecodeFrame frame = debugInfo.frame();
                    long encodedBci = FrameInfoEncoder.encodeBci(frame.getBCI(), FrameState.StackState.of(frame));

                    BytecodeFrame previous = encodedBciMap.put(encodedBci, frame);
                    assert previous == null : "duplicate encoded bci " + encodedBci + " in deopt target " + method + " found.\n\n" + frame +
                                    "\n\n" + previous;
                }

            }
        }

        return true;
    }

    static boolean canBeUsedForInlining(HostedUniverse universe, HostedMethod caller, HostedMethod callee, int bci) {

        boolean callerDeoptForTesting = caller.compilationInfo.canDeoptForTesting();
        if (callerDeoptForTesting && Modifier.isNative(callee.getModifiers())) {
            /*
             * We must not deoptimize in the stubs for native functions, since they don't have a
             * valid bytecode state.
             */
            return false;
        }
        if (callerDeoptForTesting && DeoptimizationUtils.containsStackValueNode(universe, callee)) {
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

        if (callerDeoptForTesting != callee.compilationInfo.canDeoptForTesting()) {
            /*
             * We cannot inline a method into another with non-matching canDeoptForTesting settings.
             * This would allow deoptimizations to occur in places where a deoptimization target
             * will not exist.
             */
            return false;
        }

        if (caller.isDeoptTarget()) {
            if (caller.compilationInfo.isDeoptEntry(bci, FrameState.StackState.AfterPop)) {
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

    public interface DeoptTargetRetriever {
        ResolvedJavaMethod getDeoptTarget(ResolvedJavaMethod method);
    }

    public static void registerDeoptEntriesForDeoptTesting(PointsToAnalysis bb, StructuredGraph graph, PointsToAnalysisMethod aMethod) {
        assert aMethod.isOriginalMethod();
        /*
         * Register all FrameStates as DeoptEntries.
         *
         * Because this graph will have its flowgraph immediately updated after registration, there
         * is no reason to make this method's flowgraph a stub on creation.
         */
        Collection<ResolvedJavaMethod> recomputeMethods = DeoptimizationUtils.registerDeoptEntries(graph, true,
                        (deoptEntryMethod -> ((PointsToAnalysisMethod) deoptEntryMethod).getOrCreateMultiMethod(DEOPT_TARGET_METHOD)));

        AnalysisMethod deoptMethod = aMethod.getMultiMethod(DEOPT_TARGET_METHOD);
        if (deoptMethod != null && SubstrateCompilationDirectives.singleton().isRegisteredDeoptTarget(deoptMethod)) {
            /*
             * If there exists a deopt target for this method, then it is allowed to deopt.
             */
            SubstrateCompilationDirectives.singleton().registerForDeoptTesting(aMethod);
        }

        /*
         * If new frame states are found, then redo the type flow.
         */
        for (ResolvedJavaMethod method : recomputeMethods) {
            assert MultiMethod.isDeoptTarget(method);
            ((PointsToAnalysisMethod) method).getTypeFlow().updateFlowsGraph(bb, MethodFlowsGraph.GraphKind.FULL, null, true);
        }
    }

    /**
     * @return the DeoptTarget methods which had new frame registered.
     */
    public static Collection<ResolvedJavaMethod> registerDeoptEntries(StructuredGraph graph, boolean isRoot, DeoptTargetRetriever deoptRetriever) {

        Set<ResolvedJavaMethod> changedMethods = new HashSet<>();
        for (FrameState frameState : graph.getNodes(FrameState.TYPE)) {
            if (frameState.hasExactlyOneUsage()) {
                Node usage = frameState.usages().first();
                if (!isRoot && usage == graph.start()) {
                    /*
                     * During method inlining, the FrameState associated with the StartNode
                     * disappears. Therefore, this frame state cannot be a deoptimization target.
                     */
                    continue;
                } else if (usage instanceof Invoke invoke && invoke.stateAfter() == frameState) {
                    /*
                     * If the FrameState is followed immediately by a dead end, then this state can
                     * never be reached and does not need to be registered.
                     */
                    FixedNode next = invoke.next();
                    while (next instanceof AbstractBeginNode) {
                        next = ((AbstractBeginNode) next).next();
                    }
                    if (next instanceof LoweredDeadEndNode) {
                        continue;
                    }
                }
            }

            /*
             * We need to make sure that all inlined caller frames are available for deoptimization
             * too.
             */
            for (FrameState inlineState = frameState; inlineState != null; inlineState = inlineState.outerFrameState()) {
                if (inlineState.bci >= 0) {
                    ResolvedJavaMethod method = deoptRetriever.getDeoptTarget(inlineState.getMethod());
                    if (SubstrateCompilationDirectives.singleton().registerDeoptEntry(inlineState, method)) {
                        changedMethods.add(method);
                    }
                }
            }
        }

        for (Node n : graph.getNodes()) {
            /*
             * graph.getInvokes() only iterates invokes that have a MethodCallTarget, so by using it
             * we would miss invocations that are already intrinsified to an indirect call.
             *
             * The FrameState for the invoke (which is visited by the above loop) is the state after
             * the call (where deoptimization that happens after the call has returned will continue
             * execution). We also need to register the state during the call (where deoptimization
             * while the call is on the stack will continue execution).
             *
             * MacroInvokable nodes may revert back to an invoke; therefore we must also register
             * the state during the reverted call.
             *
             * Note that the bci of the Invoke and the bci of the FrameState of the Invoke are
             * different: the Invoke has the bci of the invocation bytecode, the FrameState has the
             * bci of the next bytecode after the invoke.
             */
            FrameState stateDuring = null;
            if (n instanceof Invoke invoke) {
                stateDuring = invoke.stateAfter().duplicateModifiedDuringCall(invoke.bci(), invoke.asNode().getStackKind());
            } else if (n instanceof MacroInvokable macro) {
                stateDuring = macro.stateAfter().duplicateModifiedDuringCall(macro.bci(), macro.asNode().getStackKind());
            }

            if (stateDuring != null) {
                assert stateDuring.getStackState() == FrameState.StackState.AfterPop : stateDuring;
                ResolvedJavaMethod method = deoptRetriever.getDeoptTarget(stateDuring.getMethod());
                if (SubstrateCompilationDirectives.singleton().registerDeoptEntry(stateDuring, method)) {
                    changedMethods.add(method);
                }
            }
        }

        return changedMethods;
    }

    /*
     * Stack allocated memory is not seen by the deoptimization code, i.e., it is not copied in case
     * of deoptimization. Also, pointers to it can be used for arbitrary address arithmetic, so we
     * would not know how to update derived pointers into stack memory during deoptimization.
     * Therefore, we cannot allow methods that allocate stack memory for runtime compilation. To
     * remove this limitation, we would need to change how we handle stack allocated memory in
     * Graal.
     *
     * We also do not allow class initialization at run time to ensure the partial evaluator does
     * not constant fold uninitialized fields.
     */
    public static final NodePredicate RUNTIME_COMPILATION_INVALID_NODES = n -> NodePredicates.isA(StackValueNode.class).or(NodePredicates.isA(EnsureClassInitializedNode.class)).test(n);

    public static final NodePredicate AOT_COMPILATION_INVALID_NODES = n -> NodePredicates.isA(StackValueNode.class).test(n);

    /**
     * @return Supplier which returns true if the graph does not violate the checks.
     */
    public static Supplier<Boolean> createGraphChecker(StructuredGraph graph, NodePredicate invalidNodes) {
        return () -> {
            if (!graph.method().getDeclaringClass().isInitialized()) {
                /*
                 * All types which are used at run time should build-time initialized. This ensures
                 * the partial evaluator does not constant fold uninitialized fields.
                 */
                return false;
            }

            if (graph.getNodes().filter(invalidNodes).isNotEmpty()) {
                return false;
            }

            return true;
        };
    }
}
