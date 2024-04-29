/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.graal.nodes.ThrowBytecodeExceptionNode;
import com.oracle.svm.core.graal.phases.RemoveUnwindPhase;
import com.oracle.svm.core.graal.snippets.NonSnippetLowerings;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.common.FinalCanonicalizerPhase;
import jdk.graal.compiler.phases.common.FrameStateAssignmentPhase;
import jdk.graal.compiler.phases.tiers.LowTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;

@AutomaticallyRegisteredFeature
class ReduceImplicitExceptionStackTraceInformationFeature implements InternalFeature {
    @Override
    public void registerGraalPhases(Providers providers, Suites suites, boolean hosted) {
        if (hosted && SubstrateOptions.ReduceImplicitExceptionStackTraceInformation.getValue()) {
            /*
             * Add as late as possible, before the final canonicalization. A canonicalization is
             * necessary because this phase can make other nodes unreachable, and the canonicalizer
             * cleans that up.
             */
            ListIterator<BasePhase<? super LowTierContext>> finalCanonicalizer = suites.getLowTier().findPhase(FinalCanonicalizerPhase.class);
            if (finalCanonicalizer == null) {
                throw VMError.shouldNotReachHere("In a reduced phase plan without a final canonicalization, the " +
                                SubstrateOptions.ReduceImplicitExceptionStackTraceInformation.getName() + " option must be disabled.");
            }
            finalCanonicalizer.previous();
            finalCanonicalizer.add(new ReduceImplicitExceptionStackTraceInformationPhase());
        }
    }
}

/**
 * This phase reduces the runtime metadata for implicit exceptions, at the cost of stack trace
 * precision.
 * <p>
 * Implicit exceptions are represented by {@link BytecodeExceptionNode} after bytecode parsing. The
 * {@link RemoveUnwindPhase} already converts {@link BytecodeExceptionNode} that directly lead to an
 * {@link UnwindNode} with a {@link ThrowBytecodeExceptionNode}, but that node still has the full
 * inlined {@link FrameState} information. Both {@link BytecodeExceptionNode} and
 * {@link ThrowBytecodeExceptionNode} are lowered to {@link ForeignCallNode} by
 * {@link NonSnippetLowerings}. The foreign calls use various descriptors defined in
 * {@link ImplicitExceptions}.
 * <p>
 * This phase is designed to run late in the lower tier of the compilation pipeline, after all of
 * the above has happened. This has several advantages:
 * <ul>
 * <li>We do not need to find out anymore if the implicit exception can be caught inside the method
 * or is always unwound, this is decided by {@link RemoveUnwindPhase}</li>
 * <li>We do not need to find out if we are in a method that must not allocate, the lowering already
 * picks the proper foreign call descriptor in {@link ImplicitExceptions}</li>
 * <li>We do not need to worry about the "state after" for {@link ForeignCallNode} and
 * {@link MergeNode} because we are already after the {@link FrameStateAssignmentPhase}</li>
 * <li>We do not need to worry about {@link LoopExitNode}. When {@link RemoveUnwindPhase} produces a
 * {@link ThrowBytecodeExceptionNode} it removes all {@link LoopExitNode} that are between the
 * implicit exception point and the {@link UnwindNode}. The compiler is happy with that because
 * control flow terminates at the {@link ThrowBytecodeExceptionNode}. But when this phase inserts a
 * {@link MergeNode} for implicit exceptions from different loops, we would need to insert
 * {@link LoopExitNode} to make high-tier and mid-tier optimization phases happy. At the end of the
 * low tier, just before scheduling, no compiler phase cares about {@link LoopExitNode}
 * anymore.</li>
 * </ul>
 *
 * In this phase, more optimizations are done for {@link ForeignCallNode} that come from
 * {@link ThrowBytecodeExceptionNode} (called "throw descriptors" for simplicity) is more complete
 * compared to {@link ForeignCallNode} that come from {@link BytecodeExceptionNode} (called "create
 * descriptors"):
 * <p>
 * For "throw descriptors", we know that control flow ends, which means that at that point the
 * method cannot hold any locks anymore. We can drop all inlining information, and reduce the frame
 * state to an all-empty state of just the root compilation unit. All such "throw descriptors" for
 * the same exception class can be merged to a single foreign call.
 * <p>
 * For "create descriptors", locking and escape analysis complicate the handling: We cannot drop any
 * frame state that has locking information. While we can drop all local variables still in that
 * frame, all caller frame states remain unchanged, and therefore also need virtual object
 * information for escape analyzed objects. Merging multiple "create descriptors" for the same
 * exception class is therefore too complicated to be worth it. Since in a typical application there
 * are more than 5x as many "throw descriptors" than "create descriptors", that does not lead to too
 * much loss of optimization.
 * <p>
 * For both "throw descriptors" and "create descriptors", the foreign call is changed to a variant
 * that does not take any arguments. That means in addition to inlining information we also loose
 * detailed information for the exception message about which array element cannot be accessed,
 * which object can not be cast, ... Removing this information is a major part of the code size
 * reduction achieved by this phase, because it avoids the machine code to move all these values
 * into the proper argument registers.
 */
class ReduceImplicitExceptionStackTraceInformationPhase extends BasePhase<LowTierContext> {

    private static final Map<ForeignCallDescriptor, ForeignCallDescriptor> optimizedCreateDescriptors = Map.ofEntries(
                    Map.entry(ImplicitExceptions.CREATE_NULL_POINTER_EXCEPTION, ImplicitExceptions.CREATE_OPT_NULL_POINTER_EXCEPTION),
                    Map.entry(ImplicitExceptions.CREATE_OUT_OF_BOUNDS_EXCEPTION, ImplicitExceptions.CREATE_OPT_OUT_OF_BOUNDS_EXCEPTION),
                    Map.entry(ImplicitExceptions.CREATE_INTRINSIC_OUT_OF_BOUNDS_EXCEPTION, ImplicitExceptions.CREATE_OPT_OUT_OF_BOUNDS_EXCEPTION),
                    Map.entry(ImplicitExceptions.CREATE_CLASS_CAST_EXCEPTION, ImplicitExceptions.CREATE_OPT_CLASS_CAST_EXCEPTION),
                    Map.entry(ImplicitExceptions.CREATE_ARRAY_STORE_EXCEPTION, ImplicitExceptions.CREATE_OPT_ARRAY_STORE_EXCEPTION),
                    Map.entry(ImplicitExceptions.CREATE_INCOMPATIBLE_CLASS_CHANGE_ERROR, ImplicitExceptions.CREATE_OPT_INCOMPATIBLE_CLASS_CHANGE_ERROR),
                    /*
                     * The remaining descriptors are not changed by this phase. But frame states are
                     * still cleared, so there is still a benefit.
                     */
                    Map.entry(ImplicitExceptions.GET_CACHED_NULL_POINTER_EXCEPTION, ImplicitExceptions.GET_CACHED_NULL_POINTER_EXCEPTION),
                    Map.entry(ImplicitExceptions.GET_CACHED_OUT_OF_BOUNDS_EXCEPTION, ImplicitExceptions.GET_CACHED_OUT_OF_BOUNDS_EXCEPTION),
                    Map.entry(ImplicitExceptions.GET_CACHED_CLASS_CAST_EXCEPTION, ImplicitExceptions.GET_CACHED_CLASS_CAST_EXCEPTION),
                    Map.entry(ImplicitExceptions.GET_CACHED_ARRAY_STORE_EXCEPTION, ImplicitExceptions.GET_CACHED_ARRAY_STORE_EXCEPTION),
                    Map.entry(ImplicitExceptions.GET_CACHED_INCOMPATIBLE_CLASS_CHANGE_ERROR, ImplicitExceptions.GET_CACHED_INCOMPATIBLE_CLASS_CHANGE_ERROR));
    private static final Map<ForeignCallDescriptor, ForeignCallDescriptor> optimizedThrowDescriptors = Map.ofEntries(
                    Map.entry(ImplicitExceptions.THROW_NEW_NULL_POINTER_EXCEPTION, ImplicitExceptions.THROW_OPT_NULL_POINTER_EXCEPTION),
                    Map.entry(ImplicitExceptions.THROW_NEW_OUT_OF_BOUNDS_EXCEPTION_WITH_ARGS, ImplicitExceptions.THROW_OPT_OUT_OF_BOUNDS_EXCEPTION),
                    Map.entry(ImplicitExceptions.THROW_NEW_INTRINSIC_OUT_OF_BOUNDS_EXCEPTION, ImplicitExceptions.THROW_OPT_OUT_OF_BOUNDS_EXCEPTION),
                    Map.entry(ImplicitExceptions.THROW_NEW_CLASS_CAST_EXCEPTION_WITH_ARGS, ImplicitExceptions.THROW_OPT_CLASS_CAST_EXCEPTION),
                    Map.entry(ImplicitExceptions.THROW_NEW_CLASS_CAST_EXCEPTION, ImplicitExceptions.THROW_OPT_CLASS_CAST_EXCEPTION),
                    Map.entry(ImplicitExceptions.THROW_NEW_ARRAY_STORE_EXCEPTION_WITH_ARGS, ImplicitExceptions.THROW_OPT_ARRAY_STORE_EXCEPTION),
                    Map.entry(ImplicitExceptions.THROW_NEW_ARRAY_STORE_EXCEPTION, ImplicitExceptions.THROW_OPT_ARRAY_STORE_EXCEPTION),
                    Map.entry(ImplicitExceptions.THROW_NEW_INCOMPATIBLE_CLASS_CHANGE_ERROR, ImplicitExceptions.THROW_OPT_INCOMPATIBLE_CLASS_CHANGE_ERROR),
                    /*
                     * The remaining descriptors are not changed by this phase. But frame states are
                     * still cleared, and multiple usages in the same method are merged to a single
                     * one, so there is still a benefit.
                     */
                    Map.entry(ImplicitExceptions.THROW_CACHED_NULL_POINTER_EXCEPTION, ImplicitExceptions.THROW_CACHED_NULL_POINTER_EXCEPTION),
                    Map.entry(ImplicitExceptions.THROW_CACHED_OUT_OF_BOUNDS_EXCEPTION, ImplicitExceptions.THROW_CACHED_OUT_OF_BOUNDS_EXCEPTION),
                    Map.entry(ImplicitExceptions.THROW_CACHED_CLASS_CAST_EXCEPTION, ImplicitExceptions.THROW_CACHED_CLASS_CAST_EXCEPTION),
                    Map.entry(ImplicitExceptions.THROW_CACHED_ARRAY_STORE_EXCEPTION, ImplicitExceptions.THROW_CACHED_ARRAY_STORE_EXCEPTION),
                    Map.entry(ImplicitExceptions.THROW_CACHED_INCOMPATIBLE_CLASS_CHANGE_ERROR, ImplicitExceptions.THROW_CACHED_INCOMPATIBLE_CLASS_CHANGE_ERROR));

    @Override
    protected void run(StructuredGraph graph, LowTierContext context) {
        EconomicMap<ForeignCallDescriptor, FixedNode> optimizedThrowReplacements = EconomicMap.create();

        /* We need a snapshot so that new ForeignCallNode added by this phase are not processed. */
        for (var node : graph.getNodes().filter(ForeignCallNode.class).snapshot()) {
            if (optimizedCreateDescriptors.containsKey(node.getDescriptor())) {
                clearFrameStateForOptimizedCreate(node);
            } else if (optimizedThrowDescriptors.containsKey(node.getDescriptor())) {
                combineForeignCallForOptimizedThrow(node, optimizedThrowReplacements);
            }
        }
    }

    /**
     * Graal IR before this method: a {@link ForeignCallNode} with a frame state that has inlining
     * and local variables filled.
     *
     * GraalIR after this method: a new {@link ForeignCallNode} with a different call target
     * descriptor. The frame state has all local variables cleared, but all inlining is preserved.
     */
    private static void clearFrameStateForOptimizedCreate(ForeignCallNode originalForeignCall) {
        var graph = originalForeignCall.graph();
        var newDescriptor = optimizedCreateDescriptors.get(originalForeignCall.getDescriptor());

        FrameState originalState = originalForeignCall.stateDuring();
        /*
         * Local variables are cleared, but inlining information and locks are preserved. For outer
         * states and locks we also need to preserve virtual object mappings. Some of the virtual
         * object mappings might be unnecessary, but it would be tedious to find and filter them.
         */
        FrameState newState = graph.add(new FrameState(originalState.outerFrameState(), originalState.getCode(), 0,
                        originalState.values().subList(0, originalState.locksSize()),
                        originalState.localsSize(), 0, originalState.locksSize(), FrameState.StackState.AfterPop, false,
                        originalState.monitorIds(), originalState.virtualObjectMappings(), null));

        ForeignCallNode newForeignCall = graph.add(new ForeignCallNode(newDescriptor, originalForeignCall.stamp(NodeView.DEFAULT), List.of()));
        newForeignCall.setStateDuring(newState);
        graph.replaceFixedWithFixed(originalForeignCall, newForeignCall);
    }

    /**
     * GraalIR before this method: a {@link ForeignCallNode} that never returns, i.e., control flow
     * after it is dead.
     *
     * GraalIR after this method:a new {@link ForeignCallNode} that never returns, with all local
     * variables cleared and no inlining; or a control flow merge to such a previously inserted
     * {@link ForeignCallNode}.
     */
    private static void combineForeignCallForOptimizedThrow(ForeignCallNode originalForeignCall, EconomicMap<ForeignCallDescriptor, FixedNode> replacements) {
        var graph = originalForeignCall.graph();
        var newDescriptor = optimizedThrowDescriptors.get(originalForeignCall.getDescriptor());
        var existingReplacement = replacements.get(newDescriptor);

        ForeignCallNode newForeignCall;
        FixedNode newSuccessor;
        if (existingReplacement == null) {
            /*
             * First occurrence of that exception class. Replace the foreign call with a new foreign
             * call to the optimized runtime method, with a frame state that is top-level only and
             * has all local variables cleared.
             */
            FrameState outermostState = originalForeignCall.stateDuring();
            while (outermostState.outerFrameState() != null) {
                outermostState = outermostState.outerFrameState();
            }
            /* Drop all inlining, all local variables, and all locking. */
            var newStateDuring = graph.add(new FrameState(null, outermostState.getCode(), 0,
                            List.of(),
                            outermostState.localsSize(), 0, 0, FrameState.StackState.AfterPop, false,
                            null, null, null));

            newForeignCall = graph.add(new ForeignCallNode(newDescriptor, originalForeignCall.stamp(NodeView.DEFAULT), List.of()));
            newForeignCall.setStateDuring(newStateDuring);
            /*
             * The foreign call does not return, and the exception it throws is unwound to the
             * caller frame.
             */
            newForeignCall.setNext(graph.add(new LoweredDeadEndNode()));
            replacements.put(newDescriptor, newForeignCall);
            newSuccessor = newForeignCall;

        } else {
            MergeNode replacementMerge;
            if (existingReplacement instanceof ForeignCallNode) {
                /*
                 * Second occurrence of that exception class. We already have the optimized foreign
                 * call from the first occurrence, now we need to insert a control flow merge so
                 * that we can use it from more than one place.
                 */
                newForeignCall = (ForeignCallNode) existingReplacement;
                replacementMerge = graph.add(new MergeNode());
                replacements.put(newDescriptor, replacementMerge);

                EndNode firstEnd = graph.add(new EndNode());
                newForeignCall.replaceAtPredecessor(firstEnd);
                replacementMerge.addForwardEnd(firstEnd);
                replacementMerge.setNext(newForeignCall);

            } else {
                /*
                 * Third or more occurrence of that exception class. We already have the foreign
                 * call and the control flow merge, we only need to add a new edge to that merge.
                 */
                replacementMerge = (MergeNode) existingReplacement;
                newForeignCall = (ForeignCallNode) replacementMerge.next();
            }

            EndNode newEnd = graph.add(new EndNode());
            replacementMerge.addForwardEnd(newEnd);
            newSuccessor = newEnd;
        }
        /*
         * Replace the original foreign call with the replacement one, and remove the original
         * foreign call.
         */
        originalForeignCall.replaceAtUsages(newForeignCall);
        originalForeignCall.replaceAtPredecessor(newSuccessor);
        /*
         * Usually this just kills the LoweredDeadEndNode that is the successor of the original
         * foreign call. But there are corner cases where more dead control flow gets killed.
         */
        GraphUtil.killCFG(originalForeignCall);
    }
}
