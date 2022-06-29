/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases;

import static org.graalvm.compiler.core.common.GraalOptions.Intrinsify;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Optional;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.compiler.core.phases.HighTier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedGuardNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LogicNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.AbstractInliningPhase;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.contract.NodeCostUtil;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.truffle.common.TruffleCompilerRuntime;
import org.graalvm.compiler.truffle.compiler.PartialEvaluator;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Domain specific inlining phase for Truffle interpreters during host compilation.
 *
 * For more details on this phase see the <a href=
 * "https://github.com/oracle/graal/blob/master/truffle/docs/HostCompilation.md">documentation</a>
 */
public class TruffleHostInliningPhase extends AbstractInliningPhase {

    public static class Options {
        @Option(help = "Whether Truffle host inlining is enabled.")//
        public static final OptionKey<Boolean> TruffleHostInlining = new OptionKey<>(true);

        @Option(help = "Maximum budget for Truffle host inlining for runtime compiled methods.")//
        public static final OptionKey<Integer> TruffleHostInliningBaseBudget = new OptionKey<>(5_000);

        @Option(help = "Maximum budget for Truffle host inlining for runtime compiled methods with a BytecodeInterpreterSwitch annotation.")//
        public static final OptionKey<Integer> TruffleHostInliningByteCodeInterpreterBudget = new OptionKey<>(100_000);

        @Option(help = "When logging is activated for this phase enables printing of only explored, but ultimately not inlined call trees.")//
        public static final OptionKey<Boolean> TruffleHostInliningPrintExplored = new OptionKey<>(false);

        @Option(help = "Determines the maximum call depth for exploration during host inlining.")//
        public static final OptionKey<Integer> TruffleHostInliningMaxExplorationDepth = new OptionKey<>(1000);

        @Option(help = "Maximum number of subtree invokes for a subtree to get inlined until it is considered too complex.")//
        public static final OptionKey<Integer> TruffleHostInliningMaxSubtreeInvokes = new OptionKey<>(10);

    }

    static final String INDENT = "  ";
    private static final int TRIVIAL_SIZE = 30;
    private static final int TRIVIAL_INVOKES = 0;

    protected final CanonicalizerPhase canonicalizer;

    public TruffleHostInliningPhase(CanonicalizerPhase canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    protected boolean isEnabledFor(ResolvedJavaMethod method) {
        return isBytecodeInterpreterSwitch(method);
    }

    private boolean isTransferToInterpreterMethod(ResolvedJavaMethod method) {
        return TruffleCompilerRuntime.getRuntimeIfAvailable().isTransferToInterpreterMethod(translateMethod(method));
    }

    private boolean isInInterpreter(ResolvedJavaMethod targetMethod) {
        return TruffleCompilerRuntime.getRuntimeIfAvailable().isInInterpreter(translateMethod(targetMethod));
    }

    protected boolean isTruffleBoundary(ResolvedJavaMethod targetMethod) {
        return TruffleCompilerRuntime.getRuntimeIfAvailable().isTruffleBoundary(translateMethod(targetMethod));
    }

    private boolean isBytecodeInterpreterSwitch(ResolvedJavaMethod targetMethod) {
        return TruffleCompilerRuntime.getRuntimeIfAvailable().isBytecodeInterpreterSwitch(translateMethod(targetMethod));
    }

    private boolean isInliningCutoff(ResolvedJavaMethod targetMethod) {
        return TruffleCompilerRuntime.getRuntimeIfAvailable().isInliningCutoff(translateMethod(targetMethod));
    }

    protected ResolvedJavaMethod translateMethod(ResolvedJavaMethod method) {
        return method;
    }

    @Override
    protected final void run(StructuredGraph graph, HighTierContext highTierContext) {
        ResolvedJavaMethod method = graph.method();
        if (!isEnabledFor(method)) {
            /*
             * Make sure this method only applies to interpreter methods. We check this early as we
             * assume that there are much more non-interpreter methods than interpreter methods in a
             * typical Truffle interpreter.
             */
            return;
        }

        runImpl(new InliningPhaseContext(highTierContext, graph, TruffleCompilerRuntime.getRuntimeIfAvailable(), isBytecodeInterpreterSwitch(method)));
    }

    private void runImpl(InliningPhaseContext context) {
        int sizeLimit;
        final ResolvedJavaMethod rootMethod = context.graph.method();

        if (context.isBytecodeSwitch) {
            /*
             * We use a significantly higher limit for method with @BytecodeInterpreterSwitch
             * annotation. In the future, we may even consider disabling the limit for such methods
             * all together and fail if the graph becomes too big.
             */
            sizeLimit = Options.TruffleHostInliningByteCodeInterpreterBudget.getValue(context.graph.getOptions());
        } else {
            sizeLimit = Options.TruffleHostInliningBaseBudget.getValue(context.graph.getOptions());
        }

        if (sizeLimit < 0) {
            /*
             * Host inlining phase was disabled for this method.
             */
            return;
        }

        final DebugContext debug = context.graph.getDebug();
        debug.dump(DebugContext.VERBOSE_LEVEL, context.graph, "Before Truffle host inlining");

        CallTree root = new CallTree(rootMethod);

        int round = 0;
        int inlineIndex = 0;
        int previousInlineIndex = -1;
        boolean budgetLimitReached = false;
        List<CallTree> toProcess = null;
        EconomicSet<Node> canonicalizableNodes = EconomicSet.create();

        int graphSize = 0;
        int beforeGraphSize = 0;
        try {
            /*
             * We perform inlining until we reach a fixed point, no further calls were inlined in
             * the previous round.
             */
            while (previousInlineIndex < inlineIndex) {
                previousInlineIndex = inlineIndex;

                if (!canonicalizableNodes.isEmpty()) {
                    /*
                     * We canonicalize potentially showing more inlining opportunities.
                     */
                    canonicalizer.applyIncremental(context.graph, context.highTierContext, canonicalizableNodes);
                    canonicalizableNodes.clear();
                }

                graphSize = NodeCostUtil.computeNodesSize(context.graph.getNodes());
                if (round == 0) {
                    beforeGraphSize = graphSize;
                    root.graphSize = graphSize;
                    root.children = exploreGraph(context, null, root, context.graph, round, sizeLimit, 0);
                    toProcess = new ArrayList<>(root.children.size());
                    toProcess.addAll(root.children);
                } else {
                    budgetLimitReached = false;
                    toProcess.clear();
                    exploreAndQueueInlinableCalls(context, root, toProcess, round, sizeLimit);
                }

                /*
                 * First pass for all invokes that are force inlined. This is a fixed point
                 * algorithm in order to transitively resolve force inlines. This is currently used
                 * for bytecode switches that are composed of multiple methods.
                 */
                List<CallTree> newTargets;
                List<CallTree> targets = toProcess;
                do {
                    newTargets = new ArrayList<>();
                    for (CallTree call : targets) {
                        if (!call.forceShallowInline) {
                            /*
                             * We only care about force inlines for this pass.
                             */
                            continue;
                        }
                        if (!shouldInline(context, call)) {
                            /*
                             * Some other condition might prevent inlining here.
                             */
                            continue;
                        }

                        assert call.children == null && call.exploredIndex == -1 : "force shallow inline already explored";
                        call.children = exploreGraph(context, null, call, lookupGraph(context, call.getTargetMethod()), round, sizeLimit, 0);
                        newTargets.addAll(call.children);

                        graphSize += call.graphSize;
                        inline(context, canonicalizableNodes, call, inlineIndex++, false);
                    }

                    toProcess.addAll(newTargets);
                    targets = newTargets;

                    // fixed point until no new targets are found through shallow inlining
                } while (!newTargets.isEmpty());

                recomputeSubtreeStatistics(context, root);

                // ORDER BY call.subTreeInvokes ASC, call.subTreeSize ASC
                Collections.sort(toProcess);

                /*
                 * Second pass for everything else. We use a priority queue to prioritize work on
                 * the most promising methods first in case the budget gets tight.
                 */
                for (CallTree call : toProcess) {
                    if (!shouldInline(context, call)) {
                        /*
                         * This may happen if certain invokes became dead code due to inlining of
                         * other invokes. A bit surprisingly this may happen even if no
                         * canonicalization is happening in between inlines. E.g. if the CFG was
                         * killed, see InliningUtil.finishInlining.
                         */
                        continue;
                    }

                    if (!isInBudget(call, graphSize, sizeLimit)) {
                        continue;
                    }

                    assert !call.forceShallowInline : "should already be inlined";

                    graphSize += call.subTreeSize;
                    inline(context, canonicalizableNodes, call, inlineIndex++, true);

                    if (debug.isDumpEnabled(DebugContext.VERY_DETAILED_LEVEL)) {
                        debug.dump(DebugContext.VERY_DETAILED_LEVEL, context.graph, "After Truffle host inlining %s", call.getTargetMethod().format("%H.%n(%P)"));
                    }
                }

                debug.dump(DebugContext.DETAILED_LEVEL, context.graph, "After Truffle host inlining round %s", round);
                round++;
            }

        } finally {
            if (debug.isLogEnabled()) {
                if (budgetLimitReached) {
                    debug.log("Warning method host inlining limit exceeded limit %s with graph size %s.", sizeLimit, graphSize);
                }

                int diff = graphSize - beforeGraphSize;
                long total = totalCostCounter.addAndGet(diff);

                /*
                 * The call tree is very convenient to debug host inlining decisions in addition to
                 * IGV. To use pass
                 * -H:Log=TruffleHostInliningPhase,~TruffleHostInliningPhase.CanonicalizerPhase to
                 * filter noise by canonicalization.
                 */
                debug.log("Truffle host inlining completed after %s rounds. Graph cost changed from %s to %s after inlining (total cost %s): %n%s", round, beforeGraphSize, graphSize, total,
                                printCallTree(context, root));
            }
        }

    }

    private static final AtomicLong totalCostCounter = new AtomicLong();

    /*
     * Inline decisions might have changed due to invokes dying or subtree invoke limit reached, so
     * we need to compute the statistics again with a separate pass on the call tree.
     */
    private int recomputeSubtreeStatistics(InliningPhaseContext context, CallTree caller) {
        int subtreeSize = caller.graphSize;
        int subtreeInvokes = caller.invokes;
        int invokes = 0;
        if (caller.children != null) {
            for (CallTree callee : caller.children) {
                if (callee.isInlined() || shouldInline(context, callee)) {
                    subtreeSize += recomputeSubtreeStatistics(context, callee);
                    subtreeInvokes += callee.subTreeInvokes;
                } else if (countToInvokes(callee)) {
                    subtreeInvokes++;
                    invokes++;
                }
            }
        }
        caller.invokes = invokes;
        caller.subTreeInvokes = subtreeInvokes;
        caller.subTreeSize = subtreeSize;
        return subtreeSize;
    }

    private static boolean isInBudget(CallTree call, int graphSize, int sizeLimit) {
        if (call.forceShallowInline) {
            return true;
        }
        int newSize = graphSize + call.subTreeSize;
        if (newSize <= sizeLimit) {
            call.reason = "within budget";
            return true;
        }

        boolean trivial = call.subTreeInvokes == TRIVIAL_INVOKES && call.subTreeSize < TRIVIAL_SIZE;
        if (trivial) {
            call.reason = "out of budget but simple enough";
            return true;
        }

        call.reason = "Out of budget";
        return false;
    }

    /**
     * Explores the specified graph and returns the list of callees that are potentially inlineable.
     * Recursive exploration will only be triggered for calls that are already inlinable.
     * <p>
     * This method follows the same rules as the {@link PartialEvaluator} for recursive exploration.
     * For example, methods dominated by a call to
     * {@link CompilerDirectives#transferToInterpreterAndInvalidate()} are not inlined or explored.
     * The same applies to calls protected by {@link CompilerDirectives#inInterpreter()} or methods
     * annotated by {@link TruffleBoundary}.
     */
    private List<CallTree> exploreGraph(InliningPhaseContext context, CallTree root, CallTree caller, StructuredGraph graph,
                    int exploreRound, int exploreBudget, int depth) {
        caller.exploredIndex = exploreRound;

        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, false, true, false);
        EconomicSet<AbstractBeginNode> deoptimizedBlocks = EconomicSet.create();
        EconomicSet<AbstractBeginNode> inInterpreterBlocks = EconomicSet.create();
        List<CallTree> children = new ArrayList<>();

        /*
         * We traverse the graph in reverse post order to detect all deoptimized blocks before the
         * actual invoke. This allows us to not inline calls that were preceded by a transfer to
         * interpreter.
         */
        for (Block block : cfg.reversePostOrder()) {
            if (block.getEndNode() instanceof IfNode) {
                /*
                 * Calls protected by inInterpreter within if conditions must mark all false
                 * successors blocks including blocks dominated by the block as protected
                 * inIntepreter.
                 */
                IfNode ifNode = (IfNode) block.getEndNode();
                LogicNode condition = ifNode.condition();
                for (Node input : condition.inputs()) {
                    if (input instanceof Invoke) {
                        ResolvedJavaMethod targetMethod = ((Invoke) input).getTargetMethod();
                        if (targetMethod != null && isInInterpreter(targetMethod)) {
                            inInterpreterBlocks.add(ifNode.falseSuccessor());
                            break;
                        }
                    }
                }
            }

            boolean guardedByInInterpreter = false;

            for (FixedNode node : block.getNodes()) {
                if (root != null && root.explorationIncomplete) {
                    /*
                     * Stop exploration as soon as the root gets marked incomplete, meaning too big
                     * to explore.
                     */
                    return children;
                }

                if (node instanceof FixedGuardNode) {
                    /*
                     * Some if conditions may have already been converted to guards at this point.
                     * For guards that are protected inInterpreter blocks we need to mark all
                     * following blocks as inInterpreter blocks. We also mark all following fixed
                     * nodes as inInterpeter by setting a local variable guardedByInInterpreter to
                     * true.
                     */
                    FixedGuardNode guard = (FixedGuardNode) node;
                    if (guard.isNegated()) {
                        LogicNode condition = guard.condition();
                        for (Node input : condition.inputs()) {
                            if (input instanceof Invoke) {
                                ResolvedJavaMethod targetMethod = ((Invoke) input).getTargetMethod();
                                if (targetMethod != null && isInInterpreter(targetMethod)) {

                                    Block dominatedSilbling = block.getFirstDominated();
                                    while (dominatedSilbling != null) {
                                        inInterpreterBlocks.add(dominatedSilbling.getBeginNode());
                                        dominatedSilbling = dominatedSilbling.getDominatedSibling();
                                    }
                                    guardedByInInterpreter = true;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (!(node instanceof Invoke)) {
                    continue;
                }

                Invoke invoke = (Invoke) node;
                ResolvedJavaMethod newTargetMethod = invoke.getTargetMethod();
                if (newTargetMethod == null) {
                    continue;
                }

                boolean deoptimized = caller.deoptimized || isBlockOrDominatorContainedIn(block, deoptimizedBlocks);
                if (isTransferToInterpreterMethod(newTargetMethod)) {
                    /*
                     * If we detect a deopt we mark the entire block as a deoptimized block. It is
                     * probably rare that a deopt is found in the middle of a block, but that deopt
                     * would propagate to the entire block anyway.
                     */
                    deoptimizedBlocks.add(block.getBeginNode());

                    /*
                     * The deoptimization should propagate to the block of the caller if the first
                     * block of a method is a deopt. Maybe this could be better?
                     */
                    if (block.getBeginNode() == graph.start()) {
                        caller.propagatesDeopt = true;
                    }
                }
                boolean inInterpreter = guardedByInInterpreter || caller.inInterpreter || isBlockOrDominatorContainedIn(block, inInterpreterBlocks);

                /*
                 * The idea is to support composed bytecodes witches from multiple methods. For that
                 * we always need to inline all bytecodes witches first.
                 */
                boolean forceShallowInline = context.isBytecodeSwitch && (caller.forceShallowInline || caller.parent == null) && isBytecodeInterpreterSwitch(invoke.getTargetMethod());

                CallTree callee = new CallTree(caller, invoke, deoptimized, inInterpreter, forceShallowInline);
                children.add(callee);

                if (forceShallowInline) {
                    /*
                     * We explore later for force shallow inline.
                     */
                    continue;
                }

                /*
                 * The explore root determines where the statistics for the subtree are collected.
                 */
                CallTree exploreRoot;
                if (root == null) {
                    exploreRoot = callee;
                } else {
                    exploreRoot = root;
                }

                if (shouldInline(context, callee)) {
                    callee.children = exploreInlinableCall(context, exploreRoot, callee, exploreRound, exploreBudget, depth);

                    if (callee.propagatesDeopt) {
                        deoptimizedBlocks.add(block.getBeginNode());

                        /*
                         * Propagate even further up if the invoke is again in the first block.
                         */
                        if (block.getBeginNode() == graph.start()) {
                            caller.propagatesDeopt = true;
                        }
                    }

                    caller.subTreeInvokes += callee.subTreeInvokes;
                } else {
                    /*
                     * We do not count deoptimized invokes as actual invokes. We do not want that
                     * slow-path code influences inline prioritization. Note that invokes are still
                     * accounted for in the node cost.
                     */
                    if (countToInvokes(callee)) {

                        /*
                         * We propagate the subTreeSize to all callers until we reach the
                         * exploreRoot.
                         */
                        caller.subTreeInvokes++;
                        caller.invokes++;
                    }
                }

                if (!caller.isInlined() && caller.subTreeInvokes >= context.maxSubtreeInvokes) {
                    /*
                     * We can just as well stop exploring if we already reached the number of
                     * invokes.
                     */
                    if (!context.printExplored) {
                        /*
                         * We only stop exploring if we do not print the explored.
                         */
                        caller.explorationIncomplete = true;
                        return children;
                    }
                }
            }
        }
        return children;
    }

    /**
     * Returns <code>true</code> if the current block or one of its dominators is contained in the
     * blocks set. The blocks set contains begin nodes of each block to check. For example, this
     * phase traces which blocks are containing calls to transferToInterpreter. This method is used
     * to check whether the current block is deoptimized itself or one of the current blocks
     * dominators is.
     */
    private static boolean isBlockOrDominatorContainedIn(Block currentBlock, EconomicSet<AbstractBeginNode> blocks) {
        if (blocks.isEmpty()) {
            return false;
        }
        Block dominator = currentBlock;
        while (dominator != null) {
            if (blocks.contains(dominator.getBeginNode())) {
                return true;
            }
            dominator = dominator.getDominator();
        }
        return false;
    }

    /**
     * Explores an already {@link #shouldInline(InliningPhaseContext, CallTree) inlinable} call
     * recursively. Determines whether the budget is exceeded when recursively exploring the call
     * tree. So the outcome of this method may also be that the exploration was
     * {@link CallTree#explorationIncomplete incomplete}.
     */
    private List<CallTree> exploreInlinableCall(InliningPhaseContext context, CallTree exploreRoot, CallTree callee, int exploreRound, int exploreBudget, int depth) {
        StructuredGraph calleeGraph;
        ResolvedJavaMethod targetMethod;
        if (callee.invoke.getInvokeKind().isDirect()) {
            targetMethod = callee.getTargetMethod();
        } else {
            assert callee.monomorphicTargetMethod != null;
            targetMethod = callee.monomorphicTargetMethod;
        }

        calleeGraph = lookupGraph(context, targetMethod);
        assert calleeGraph != null : "There must be a graph available for an inlinable call.";
        callee.graphSize = NodeCostUtil.computeNodesSize(calleeGraph.getNodes());
        if (shouldContinueExploring(context, exploreBudget, callee.graphSize, depth)) {
            return exploreGraph(context, exploreRoot, callee, calleeGraph, exploreRound, exploreBudget - callee.graphSize, depth + 1);
        } else {
            /*
             * We reached the limits of what we want to explore. This means this call is unlikely to
             * ever get inlined. From now on we should finish the recursive exploration as soon as
             * possible to avoid unnecessary overhead.
             */
            exploreRoot.explorationIncomplete = true;
            return Collections.emptyList();
        }
    }

    private static boolean shouldContinueExploring(InliningPhaseContext context, int exploreBudget, int graphSize, int depth) {
        int useBudget = Math.max(TRIVIAL_SIZE, exploreBudget);
        return graphSize <= useBudget && depth < Options.TruffleHostInliningMaxExplorationDepth.getValue(context.options);
    }

    /**
     * Returns <code>true</code> if a call counts to the invoke heuristic for host inlining. We
     * deliberately do not want to count invokes in slow-paths or dead invokes for this statistics
     * as they should not influence decisions.
     */
    private static boolean countToInvokes(CallTree call) {
        if (call.deoptimized) {
            return false;
        }
        if (call.inInterpreter) {
            return false;
        }
        if (call.propagatesDeopt) {
            return false;
        }
        /*
         * This is an intrinsified or dead invoke. Do no count them.
         */
        String failureMessage = InliningUtil.checkInvokeConditions(call.invoke);
        if (failureMessage != null) {
            return false;
        }

        return true;
    }

    /**
     * Returns <code>true</code> if a call tree should get inlined, otherwise <code>false</code>.
     * This method does not yet make determine wheter the call site is in budget. See
     * {@link #isInBudget(CallTree, int, int)} for that.
     */
    private boolean shouldInline(InliningPhaseContext context, CallTree call) {
        if (call.parent == null) {
            // root always inlinable
            return true;
        }

        Invoke invoke = call.invoke;
        if (call.isInlined()) {
            return false;
        }

        if (!(invoke.callTarget() instanceof MethodCallTargetNode)) {
            call.reason = "not a method call target";
            return false;
        }

        ResolvedJavaMethod targetMethod = invoke.getTargetMethod();
        if (!shouldInlineTarget(context, call, targetMethod)) {
            return false;
        }

        String failureMessage = InliningUtil.checkInvokeConditions(call.invoke);
        if (failureMessage != null) {
            call.reason = failureMessage;
            return false;
        }

        if (!invoke.getInvokeKind().isDirect() && !shouldInlineMonomorphic(context, call, targetMethod)) {
            return false;
        }

        if (isTransferToInterpreterMethod(targetMethod)) {
            /*
             * Always inline the transfer to interpreter method.
             */
            return true;
        }

        if (isInInterpreter(targetMethod)) {
            /*
             * Always inline inInterpreter method.
             */
            return true;
        }

        if (call.forceShallowInline) {
            /*
             * Always force inline bytecode switches into bytecode switches.
             */
            return true;
        }

        if (call.deoptimized) {
            /*
             * The block of the call was deoptimized or the deoptimization propagated through a call
             * to another method that was always deoptimizing.
             */
            call.reason = "dominated by transferToInterpreter()";
            return false;
        }

        if (call.inInterpreter) {
            /*
             * The block of the call was deoptimized or the deoptimization propagated through a call
             * to another method that was always deoptimizing.
             */
            call.reason = "protected by inInterpreter()";
            return false;
        }

        /*
         * If a method always is deoptimized, we can exclude it from inlining as it is likely not a
         * common path. For example CompilerDirectives.shouldNotReachHere.
         */
        if (call.propagatesDeopt) {
            call.reason = "propagates transferToInterpreter";
            return false;
        }

        if (isInliningCutoff(targetMethod)) {
            call.reason = "method annotated with @InliningCutoff";
            return false;
        }

        /*
         * More than one non-slow-path invoke. This may happen if method has many truffle boundary
         * or non-direct virtual calls.
         */
        if (call.subTreeInvokes >= context.maxSubtreeInvokes) {
            call.reason = "call has too many fast-path invokes - too complex, please optimize, see truffle/docs/HostOptimization.md";
            return false;
        }

        if (call.explorationIncomplete) {
            /*
             * We have given up exploring the method as it was too big. We cannot really make a
             * confident inlining decision in such cases, so it is better to remain conservative
             * here.
             */
            call.reason = "too big to explore";
            return false;
        }

        if (call.parent.isRecursive(targetMethod)) {
            /*
             * Recursions are not bound to happen for runtime compiled methods, so they are unlikely
             * to actually be used within runtime compiled methods. There are still some corner
             * cases where this could happen, e.g. recursive nodes. Hence we still need to ignore
             * recursions for host Truffle inlining as there recursive nodes typically don't become
             * constants in host compilations.
             */
            call.reason = "recursive";
            return false;
        }

        if (context.highTierContext.getReplacements().hasSubstitution(targetMethod, context.graph.getOptions())) {
            call.reason = "has substituion";
            return false;
        }

        if (isTruffleBoundary(targetMethod)) {
            /*
             * Similar to runtime compilations, truffle boundary calls indicate the slow path
             * execution of a mode. We shouldn't force any additional inlining heuristics for such
             * methods as we do not know
             */
            call.reason = "truffle boundary";
            return false;
        }

        if (isBytecodeInterpreterSwitch(targetMethod)) {
            call.reason = "bytecode interpreter switch must not be inlined";
            return false;
        }

        // seems to be quite expensive so do this last
        ProfilingInfo info = context.graph.getProfilingInfo(targetMethod);
        if (info != null && new OptimisticOptimizations(context.graph.getProfilingInfo(targetMethod), context.options).lessOptimisticThan(context.highTierContext.getOptimisticOptimizations())) {
            call.reason = "the callee uses less optimistic optimizations than caller";
            return false;
        }

        return true;
    }

    private static boolean shouldInlineTarget(InliningPhaseContext context, CallTree call, ResolvedJavaMethod targetMethod) {
        if (targetMethod == null) {
            call.reason = "target method is not resolved";
            return false;
        }

        if (!targetMethod.canBeInlined()) {
            /*
             * Respect user guided never inline annotations.
             */
            call.reason = "target method not inlinable";
            return false;
        }

        if (targetMethod.isNative() && !(Intrinsify.getValue(context.options) &&
                        context.highTierContext.getReplacements().getInlineSubstitution(targetMethod, call.invoke.bci(), call.invoke.getInlineControl(), context.graph.trackNodeSourcePosition(), null,
                                        context.graph.allowAssumptions(),
                                        context.options) != null)) {
            call.reason = "target method is a non-intrinsic native method";
            return false;
        }

        if (!targetMethod.getDeclaringClass().isInitialized()) {
            call.reason = "target method's class is not initialized";
            return false;
        }
        return true;
    }

    /**
     * Invoked for each round after the first round to explore and decide inlining decisions for
     * inlinable subtrees which are not yet inlined.
     */
    private void exploreAndQueueInlinableCalls(InliningPhaseContext context, CallTree call, Collection<CallTree> toProcess, int round, int exploreBudget) {
        if (call.isInlined()) {
            for (CallTree callee : call.children) {
                exploreAndQueueInlinableCalls(context, callee, toProcess, round, exploreBudget);
            }
        } else {
            if (shouldInline(context, call)) {
                if (call.exploredIndex == -1) {
                    /*
                     * If a call is not yet explored but shouldInline returns true, this means it
                     * just became inlinable through canonicalization. So we need to explore before
                     * we can make a decision on it.
                     */
                    call.subTreeInvokes = 0;
                    call.subTreeSize = 0;
                    call.children = exploreInlinableCall(context, call, call, round, exploreBudget, 1);
                }
                toProcess.add(call);
            }
        }
    }

    /**
     * Returns <code>true</code> if the a non direct call should be inlined using type checked
     * inlining, else <code>false</code>. This depends on whether type profiling is available and
     * whether this kind of speculation is enabled.
     */
    private static boolean shouldInlineMonomorphic(InliningPhaseContext context, CallTree call, ResolvedJavaMethod targetMethod) {
        Invoke invoke = call.invoke;
        JavaTypeProfile typeProfile = ((MethodCallTargetNode) invoke.callTarget()).getTypeProfile();
        if (typeProfile == null) {
            call.reason = "not direct call: no type profile";
            return false;
        }

        if (typeProfile.getNotRecordedProbability() != 0.0D) {
            call.reason = "not direct call: type might be imprecise";
            return false;
        }

        JavaTypeProfile.ProfiledType[] ptypes = typeProfile.getTypes();
        if (ptypes == null || ptypes.length <= 0) {
            call.reason = "not direct call: no parameter types in profile";
            return false;
        }

        if (ptypes.length != 1) {
            call.reason = "not direct call: polymorphic inlining not supported";
            return false;
        }

        SpeculationLog speculationLog = context.graph.getSpeculationLog();
        if (speculationLog == null) {
            call.reason = "not direct call: no speculation log";
            return false;
        }

        final OptimisticOptimizations optimisticOpts = context.highTierContext.getOptimisticOptimizations();
        if (!optimisticOpts.inlineMonomorphicCalls(context.options)) {
            call.reason = "not direct call: inlining monomorphic calls is disabled";
            return false;
        }

        SpeculationLog.SpeculationReason speculationReason = InliningUtil.createSpeculation(invoke, typeProfile);
        if (!speculationLog.maySpeculate(speculationReason)) {
            call.reason = "not direct call: speculation disabled";
            return false;
        }

        ResolvedJavaType type = ptypes[0].getType();
        ResolvedJavaMethod concrete = type.resolveConcreteMethod(targetMethod, invoke.getContextType());
        if (!shouldInlineTarget(context, call, concrete)) {
            return false;
        }
        call.monomorphicTargetMethod = concrete;

        return true;
    }

    private void inline(InliningPhaseContext context, EconomicSet<Node> canonicalizableNodes, CallTree call, int inlineIndex, boolean recursive) {
        assert call.invoke.asFixedNode().graph() == context.graph : "invalid graph";
        assert call.children != null : "Call not yet explored or marked incomplete.";
        assert shouldInline(context, call) : "Call should be inlined.";

        call.reason = null;
        call.inlinedIndex = inlineIndex;
        UnmodifiableEconomicMap<Node, Node> oldToNew = inline(context, canonicalizableNodes, call);

        // update new invokes
        for (CallTree child : call.children) {
            child.invoke = (Invoke) oldToNew.get(child.invoke.asFixedNode());
            assert child.invoke != null : "new invoke not found";
        }

        if (!recursive) {
            /*
             * For force shallow inlining we just inline one level.
             */
            return;
        }

        for (CallTree child : call.children) {
            /*
             * We need to recheck whether the call is still inlinable. We cannot reuse a previous
             * decision as the call may have become dead in the meantime.
             */
            if (shouldInline(context, child)) {
                inline(context, canonicalizableNodes, child, inlineIndex, false);
            }
        }
    }

    private UnmodifiableEconomicMap<Node, Node> inline(InliningPhaseContext context, EconomicSet<Node> canonicalizableNodes, CallTree call) {
        Invoke invoke = call.invoke;
        ResolvedJavaMethod targetMethod;
        if (invoke.getInvokeKind().isDirect()) {
            targetMethod = invoke.getTargetMethod();
        } else {
            targetMethod = call.monomorphicTargetMethod;
            assert targetMethod != null;
            JavaTypeProfile typeProfile = ((MethodCallTargetNode) invoke.callTarget()).getTypeProfile();
            SpeculationLog.SpeculationReason speculationReason = InliningUtil.createSpeculation(invoke, typeProfile);
            SpeculationLog speculationLog = context.graph.getSpeculationLog();

            ResolvedJavaType resolvedType = typeProfile.getTypes()[0].getType();
            InliningUtil.insertTypeGuard(context.highTierContext, invoke, resolvedType, speculationLog.speculate(speculationReason));
            InliningUtil.replaceInvokeCallTarget(invoke, context.graph, InvokeKind.Special, targetMethod);
        }
        StructuredGraph inlineGraph = lookupGraph(context, targetMethod);
        AtomicReference<UnmodifiableEconomicMap<Node, Node>> duplicates = new AtomicReference<>();
        canonicalizableNodes.addAll(InliningUtil.inlineForCanonicalization(invoke, inlineGraph, true, targetMethod,
                        (d) -> duplicates.set(d),
                        "Truffle Host Inlining",
                        "Truffle Host Inlining"));
        return duplicates.get();
    }

    private StructuredGraph lookupGraph(InliningPhaseContext context, ResolvedJavaMethod method) {
        StructuredGraph graph = context.graphCache.get(method);
        if (graph == null) {
            graph = parseGraph(context.highTierContext, context.graph, method);
            context.graphCache.put(method, graph);
        }
        return graph;
    }

    @SuppressWarnings("try")
    protected StructuredGraph parseGraph(HighTierContext context, StructuredGraph graph, ResolvedJavaMethod method) {
        DebugContext debug = graph.getDebug();
        StructuredGraph newGraph = new StructuredGraph.Builder(graph.getOptions(), debug, graph.allowAssumptions())//
                        .method(method).trackNodeSourcePosition(graph.trackNodeSourcePosition()) //
                        .profileProvider(graph.getProfileProvider()) //
                        .speculationLog(graph.getSpeculationLog()).build();

        try (DebugContext.Scope s = debug.scope("InlineGraph", newGraph)) {
            if (!graph.isUnsafeAccessTrackingEnabled()) {
                newGraph.disableUnsafeAccessTracking();
            }
            if (context.getGraphBuilderSuite() != null) {
                context.getGraphBuilderSuite().apply(newGraph, context);
            }
            assert newGraph.start().next() != null : "graph needs to be populated by the GraphBuilderSuite " + method + ", " + method.canBeInlined();

            new DeadCodeEliminationPhase(Optional).apply(newGraph);
            canonicalizer.apply(newGraph, context);
            return newGraph;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private String printCallTree(InliningPhaseContext context, CallTree root) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream writer = new PrintStream(out, true);
        printTree(context, writer, INDENT, root, maxLabelWidth(context, root, 0, 1));
        return new String(out.toByteArray());
    }

    private int maxLabelWidth(InliningPhaseContext context, CallTree tree, int maxLabelWidth, int depth) {
        int maxLabel = 0;
        if (tree.parent == null) {
            // we do not care about the root label length, it does not print any properties
            maxLabel = 0;
        } else {
            maxLabel = Math.max(tree.buildLabel().length() + (depth * INDENT.length()), maxLabelWidth);
        }
        if (tree.children != null) {
            if (Options.TruffleHostInliningPrintExplored.getValue(context.options) || tree.inlinedIndex != -1 || tree.parent == null) {
                for (CallTree child : tree.children) {
                    maxLabel = maxLabelWidth(context, child, maxLabel, depth + 1);
                }
            }
        }
        return maxLabel;
    }

    private void printTree(InliningPhaseContext context, PrintStream out, String indent, CallTree tree, int maxLabelWidth) {
        out.printf("%s%n", tree.toString(indent, maxLabelWidth));
        if (tree.children != null) {
            if (Options.TruffleHostInliningPrintExplored.getValue(context.options) || tree.inlinedIndex != -1 || tree.parent == null) {
                for (CallTree child : tree.children) {
                    printTree(context, out, indent + INDENT, child, maxLabelWidth);
                }
            }
        }
    }

    public static void install(HighTier highTier, OptionValues options) {
        TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntimeIfAvailable();
        if (rt == null) {
            return;
        }
        if (!Options.TruffleHostInlining.getValue(options)) {
            return;
        }
        TruffleHostInliningPhase phase = new TruffleHostInliningPhase(highTier.createCanonicalizerPhase());
        ListIterator<BasePhase<? super HighTierContext>> insertionPoint = highTier.findPhase(AbstractInliningPhase.class);
        if (insertionPoint == null) {
            highTier.prependPhase(phase);
            return;
        }
        insertionPoint.previous();
        insertionPoint.add(phase);
    }

    public static void installInlineInvokePlugin(Plugins plugins, OptionValues options) {
        if (Options.TruffleHostInlining.getValue(options)) {
            plugins.prependInlineInvokePlugin(new BytecodeParserInlineInvokePlugin());
        }
    }

    public static boolean shouldDenyTrivialInlining(ResolvedJavaMethod callee) {
        TruffleCompilerRuntime r = TruffleCompilerRuntime.getRuntimeIfAvailable();
        assert r != null;
        return (r.isBytecodeInterpreterSwitch(callee) || r.isInliningCutoff(callee) || r.isTruffleBoundary(callee) || r.isInInterpreter(callee) || r.isTransferToInterpreterMethod(callee));
    }

    static final class BytecodeParserInlineInvokePlugin implements InlineInvokePlugin {

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod targetMethod, ValueNode[] args) {
            TruffleCompilerRuntime rt = TruffleCompilerRuntime.getRuntimeIfAvailable();
            if (rt != null && shouldDenyTrivialInlining(targetMethod)) {
                /*
                 * We deny bytecode parser inlining for any method that is relevant for Truffle host
                 * inlining. This is important otherwise we might miss some PE boundaries during
                 * application.
                 */
                return InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
            }
            return null;
        }

    }

    static final class InliningPhaseContext {

        final HighTierContext highTierContext;
        final StructuredGraph graph;
        final OptionValues options;
        final TruffleCompilerRuntime truffle;
        final boolean isBytecodeSwitch;
        final int maxSubtreeInvokes;
        final boolean printExplored;

        /**
         * Caches graphs for a single run of this phase. This is not just a performance optimization
         * but also needed for correctness as invokes from individual graphs are remembered in the
         * CallTree.
         */
        final EconomicMap<ResolvedJavaMethod, StructuredGraph> graphCache = EconomicMap.create(Equivalence.DEFAULT);

        InliningPhaseContext(HighTierContext context, StructuredGraph graph, TruffleCompilerRuntime truffle, boolean isBytecodeSwitch) {
            this.highTierContext = context;
            this.graph = graph;
            this.options = graph.getOptions();
            this.truffle = truffle;
            this.isBytecodeSwitch = isBytecodeSwitch;
            this.maxSubtreeInvokes = Options.TruffleHostInliningMaxSubtreeInvokes.getValue(options);
            this.printExplored = Options.TruffleHostInliningPrintExplored.getValue(options);
        }

    }

    /**
     * The tree of all explored and inlined invokes of a graph. This graph is kept intact even if
     * invokes are inlined so represent the original call stack. This allows to detect recursions
     * and capture information determined during exploration.
     */
    static final class CallTree implements Comparable<CallTree> {

        /**
         * The invoke node in the parent graph.
         */
        Invoke invoke;

        /**
         * The parent caller. Note that since trivial inlining in SVM is already run this might not
         * reflect a real caller in the Java bytecodes.
         */
        final CallTree parent;
        List<CallTree> children;

        /**
         * True if this method is deoptimized, this means that it is dominated by a call to
         * transferToInterpreter.
         */
        final boolean deoptimized;

        /**
         * True if invoke is contained in a block protected by a call to if (inInterpreter()). Code
         * protected by such conditions are potentially not designed for PE and therefore
         * assumptions taken by this inlining heuristic do not apply.
         */
        final boolean inInterpreter;

        /**
         * True if method should be force shallow inlined.
         */
        final boolean forceShallowInline;

        /**
         * True if this method contains a deopt in its first block.
         */
        boolean propagatesDeopt;

        /**
         * The reason why inlining failed or succeeded.
         */
        String reason;

        /**
         * Gets set if the invoke gets inlined.
         */
        int inlinedIndex = -1;

        /**
         * We remember the previous target method to make debug printing work even after the invoke
         * was inlined.
         */
        ResolvedJavaMethod cachedTargetMethod;

        /**
         * Set if a monomorphic call site was resolved. We remember the decision from
         * {@link TruffleHostInliningPhase#shouldInlineMonomorphic(InliningPhaseContext, CallTree, ResolvedJavaMethod)}
         * to speed up later exploration.
         */
        ResolvedJavaMethod monomorphicTargetMethod;

        /**
         * Number of non-inlinable invokes in the subtree that are not dominated by a transfer to
         * interpreter call.
         */
        int subTreeInvokes;

        int invokes;

        /**
         * Sum of all Graal nodes of the entire subtree of all methods that were determined to be
         * inlined during subtree exploration.
         */
        int subTreeSize;

        /**
         * Cost of of all graal nodes in this method. The size is computed during exploration.
         */
        int graphSize;

        /**
         * True if the subtree could not fully be explored.
         */
        boolean explorationIncomplete;

        int exploredIndex = -1;

        CallTree(CallTree parent, Invoke invoke, boolean deoptimized, boolean inInterpreter, boolean forceShallowInline) {
            this.invoke = invoke;
            this.deoptimized = deoptimized;
            this.inInterpreter = inInterpreter;
            this.parent = parent;
            this.cachedTargetMethod = invoke.getTargetMethod();
            this.forceShallowInline = forceShallowInline;
            Objects.requireNonNull(cachedTargetMethod);
        }

        CallTree(ResolvedJavaMethod root) {
            this.invoke = null;
            this.deoptimized = false;
            this.inInterpreter = false;
            this.forceShallowInline = false;
            this.cachedTargetMethod = root;
            this.parent = null;
        }

        ResolvedJavaMethod getTargetMethod() {
            if (invoke == null) {
                return cachedTargetMethod;
            }
            ResolvedJavaMethod targetMethod = invoke.getTargetMethod();
            if (targetMethod != null) {
                this.cachedTargetMethod = targetMethod;
            } else {
                targetMethod = cachedTargetMethod;
            }
            return targetMethod;
        }

        boolean isRoot() {
            return parent == null;
        }

        boolean isInlined() {
            return inlinedIndex != -1 || isRoot();
        }

        @Override
        public int compareTo(CallTree o) {
            int compare = Integer.compare(subTreeInvokes, o.subTreeInvokes);
            if (compare == 0) {
                return Integer.compare(subTreeSize, o.subTreeSize);
            }
            return compare;
        }

        private boolean isRecursive(ResolvedJavaMethod other) {
            if (getTargetMethod().equals(other)) {
                return true;
            }
            if (parent != null) {
                return parent.isRecursive(other);
            }
            return false;
        }

        public String toString(String indent, int maxIndent) {
            ResolvedJavaMethod targetMethod = getTargetMethod();
            if (invoke == null) {
                return "Root[" + targetMethod.format("%H.%n") + "]";
            } else {
                return String.format(
                                "%-" + maxIndent +
                                                "s [inlined %4s, explored %4s, monomorphic %5s, deopt %5s, inInterpreter %5s, propDeopt %5s, graphSize %4s, subTreeCost %4s, invokes %4s, subTreeInvokes %4s, forced %5s, incomplete %5s,  reason %s]",
                                indent + buildLabel(), inlinedIndex, exploredIndex, monomorphicTargetMethod != null, deoptimized, inInterpreter, propagatesDeopt, graphSize, subTreeSize, invokes,
                                subTreeInvokes,
                                forceShallowInline,
                                explorationIncomplete,
                                reason);
            }
        }

        private String buildLabel() {
            String label;
            if (reason == null && inlinedIndex == -1) {
                label = "NEW";
            } else if (inlinedIndex != -1) {
                label = "INLINE";
            } else {
                if (invoke.isAlive()) {
                    label = "CUTOFF";
                } else {
                    label = "DEAD";
                }
            }
            StringBuilder b = new StringBuilder(label);
            b.append(" ");
            String name = getTargetMethod().format("%H.%n(%p)");
            if (name.length() > 140) {
                name = getTargetMethod().format("%H.%n(...)");
            }
            b.append(name);
            return b.toString();
        }

        @Override
        public String toString() {
            return toString("", 1);
        }

    }

}
