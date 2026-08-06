/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.InliningLog;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.options.EnumOptionKey;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.AbstractInliningPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.inlining.DirectedInliningRules;
import jdk.graal.compiler.phases.common.inlining.InliningPhase;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.common.priorityinline.data.BenefitKind;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.DeletedNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.GenericNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.InlineCacheNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.ParentNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;
import jdk.graal.compiler.phases.common.priorityinline.tuning.TuningPolicy;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This phase analyses the call graph, and inlines calls when it decides that inlining is
 * beneficial.
 *
 * There are two major stages of this phase -- expansion and inlining. During the expansion stage,
 * parts of the call graph that are considered important get recursively expanded. During the
 * inlining stage, the expanded call graph is analyzed, and calls that have clear benefit are
 * inlined into the root method. These two stages alternate until the entire phase reaches one of
 * the termination conditions.
 *
 * In what follows, expansion and inlining are explained in detail. We start by covering two
 * preliminaries: the call graph data structure and the priority tracking inside the call graph.
 *
 *
 * <h2>Call graph</h2>
 *
 * Nodes of the call graph are one of the following:
 *
 * <ul>
 * <li>Subgraph -- an expanded method that was not yet inlined into the root method. Its children
 * are the calls in its body.</li>
 * <li>Cutoff -- a method that was not yet expanded (since a call graph is potentially infinite,
 * some methods do not get expanded).</li>
 * <li>Indirect -- represents a call that cannot be inlined, either because it is a virtual
 * dispatch, or native, or recursive, or something else.</li>
 * <li>Deleted -- represents a child call that existed, but canonicalizations/optimizations removed
 * it from the parent's body (it is important to keep these for cost-benefit analysis).</li>
 * <li>InlineCache -- represents a call with a profile of the dispatch type. Has a direct-call child
 * for each dispatch type above a threshold.</li>
 * <li>Generic -- represents a generic call in the inline cache node.</li>
 * </ul>
 *
 * Nodes of the call graph need to be analysed and annotated with specific information before
 * inlining can take place. Every time the expansion stage modifies the call graph, this information
 * generally needs to be recomputed. However, for specific kinds of information, the call graph can
 * be updated incrementally -- in this case, inlining must only update the parts of the call graph
 * that were modified by expansion since the last inlining.
 *
 * To allow incremental analysis, each node of the call graph contains aggregated information about
 * one subtree. Here are some of the examples:
 *
 * <ul>
 * <li>number call nodes in the subtree</li>
 * <li>maximum call depth of the subtree</li>
 * <li>total number of compiler graph nodes in all the calls in the subtree</li>
 * <li>is there a some call graph node that needs its parameters enhanced</li>
 * <li>local benefit from inlining the respective node</li>
 * <li>cost-benefit tuple of the respective node</li>
 * <li>total number of indirect (non-inlineable) calls in the subtree</li>
 * <li>total number of potentially inlineable calls in the subtree</li>
 * </ul>
 *
 * Note that each piece of information listed above can be recomputed from the node's children if
 * they contain the same already computed information, without diving into the subtree. Every time
 * the expansion stage descends on some path from the root in the call graph, it recomputes this
 * information on its way back to the root.
 *
 *
 * <h2>Priority tracking</h2>
 *
 * The expansion stage is biased towards picking nodes that are more likely to be inlined. To track
 * these priorities, each node of the call graph maintains its own priority and the highest priority
 * of the Cutoff nodes in its subtree. Priority of non-inlineable nodes is kept minimum, while
 * cutoff node priority is computed from their local benefit. The priority of Subgraph/InlineCache
 * nodes in the graph is then:
 *
 * <cite> The highest priority of any Cutoff node in its subtree minus the expansion penalty for
 * that subtree. </cite>
 *
 * The expansion penalty is computed so that subtrees that are more likely to be fully expanded
 * (i.e. have less Cutoff nodes) are expanded before subtrees that are unlikely to be fully
 * expanded. Other information such as node frequency also influences the penalty.
 *
 * Each call graph node maintains a list of its children, and also a priority queue of children that
 * need to be visited for expansion, sorted in the priority order. This priority queue is called the
 * <b>expansion queue</b>. The call graph and the priority queues at each node effectively form a
 * global priority queue. The benefit of having this queue spread throughout the call graph is that
 * it can be incrementally updated - for example, if the total number of Cutoff nodes in any subtree
 * affects the priority of the Cutoff nodes in that subtree, then priority queue does not need to be
 * updated for all the Cutoff nodes in that subtree, but only for the call graph nodes on the path
 * to the root.
 *
 *
 * <h2>Expansion stage</h2>
 *
 * In the expansion stage, some number of nodes that were previously Cutoff nodes must be replaced
 * with other nodes. These Cutoff nodes are picked based on the priorities assigned to them when
 * they were created. The expansion stage proceeds as follows:
 *
 * <ol>
 * <li>Set the expansion budget to the total number of active Cutoff nodes in the subtree.</li>
 * <li>Recursively descend the call graph by taking the child with the highest priority from the
 * expansion queue at each node.</li>
 * <li>At each Subgraph/InlineCache node, pick subtrees from the expansion queue until one of the
 * following happens:
 * <ul>
 * <li>There is no more expansion budget for the current expansion stage.</li>
 * <li>There are no more elements on the expansion queue of the current node.</li>
 * <li>The highest priority element in the expansion queue has *lower* priority than the highest
 * priority seen in some of the ancestor calls, indicating that the expansion should switch to
 * another, entirely different node in the call graph.</li>
 * </ul>
 * </li>
 * <li>Each time that a subtree is processed, update information, priority and the expansion node of
 * the current node based on the children. Since cost-benefit analysis is costly, mark that the node
 * needs its cost-benefit updated. Similarly, mark that a node needs its parameters enhanced with
 * the callsite arguments.</li>
 * <li>If the Cutoff node is too deep, needs a high expansion footprint or otherwise uninteresting,
 * postpone its expansion until the next stage.</li>
 * <li>If the Cutoff node is extremely infrequent, or is forbidden due to recursion depth, replace
 * it with an Indirect node.</li>
 * <li>Otherwise, if the node is a Cutoff, replace it with a Subgraph, InlineCache or Indirect node.
 * </li>
 * </ol>
 *
 *
 * <h2>Inlining stage</h2>
 *
 * The local benefit of a Cutoff node is assessed from comparing how much better callsite argument
 * types are compared to parameter types, and multiplying that with the frequency. The local benefit
 * of a Subgraph node is equal to the number of parameter canonicalizations, multiplied with the
 * frequency.
 *
 * The cost-benefit analysis aims to assess the cost-benefit of inlining a particular method. For
 * any method in the call graph, the cost-benefit is computed as in the following example. Assume
 * that we have a call graph with methods R, A, B, C, D, E, F and G, where R is the root method, A
 * is currently analysed, and the nodes D and F were marked as inlined, as shown in the following
 * figure. The analysis works bottom-up, so the nodes deeper in the call graph have already been
 * analyzed.
 *
 * <pre>
 *       R
 *      /
 *     A  <-- currently analysed
 *    / \
 *   B   C
 *      / \
 *    [D]  E
 *    / \
 *  [F]  G
 * </pre>
 *
 * The question that cost-benefit analysis needs to answer whether there is benefit in inlining A
 * into R? To answer this, the cost-benefit analysis must first determine what are the cost and
 * benefit of inlining methods below A, if A was the root method being compiled. This is because, if
 * we decide to inline A, we will inline at least those methods that it would have inlined itself if
 * it were the root. Once we have the cost-benefit tuple for A, we can see if inlining A into R can
 * improve the overall cost-benefit of R, and if R can inline some additional descendants that A did
 * not inline. If this is the case, we mark A for inlining.
 *
 * We analyze the cost and benefit of A as follows:
 *
 * <ul>
 * <li>Take the local benefit of A, and subtract the local benefit of inlining its children, B and
 * C. Use that to compute the initial cost-benefit tuple (lb(A) - lb(B) - lb(C), cost(A)), where
 * cost is the compiler node count.</li>
 * <li>Sort the children of A into a benefit-per-cost-based priority queue.</li>
 * <li>As long as the cost-benefit queue is non-empty, remove the highest ranking child, say C, and
 * see if inlining it into A would increase benefit-per-cost ratio of the currently analysed node A,
 * that is, if (lb(A) - lb(B) - lb(C) + lb(C)) / (cost(A) + cost(C)) > (lb(A) - lb(B) - lb(C)) /
 * cost(A).</li>
 * <li>If it cannot, then other children cannot either because they have worse benefit-per-cost, and
 * we are done.</li>
 * <li>If it can, then mark the child, say C, as inlined, and add C's non-inlined descendants E and
 * G to cost-benefit queue.</li>
 * </ul>
 *
 * To determine whether to inline A into R, we recursively repeat the procedure for A's parent R.
 *
 * This analysis will mark nodes inlined if they improve the benefit-per-cost ratio of some of their
 * ancestors. It will also assign larger benefit-cost tuples to nodes that have managed to inline
 * large parts of their subtrees. Parents that have at least one child with a large benefit-cost
 * ratio are more likely to inline more of their non-inlined descendants (in the example above, A is
 * more likely to inline E and G, which C failed to inline).
 *
 * The inlining stage does the cost-benefit analysis summarized above, and then inlines the nodes
 * that were marked inlined. Concretely, it proceeds as follows:
 *
 * <ol>
 * <li>Incrementally update parts of the call graph that need parameter enhancement. Effectively,
 * replace their parameters with callsite arguments, and apply canonicalization. If possible,
 * replace children calls that were canonicalized away with Deleted nodes. It is important to do
 * this after expansion, since then the Deleted nodes carry a larger weight.</li>
 * <li>Incrementally update parts of the call graph that need their cost-benefit analysis update.
 * </li>
 * <li>Traverse the call graph from the root in priority-based order, where priority is determined
 * by cost-benefit. As long as *the benefit from inlining is positive*, and *falls within the
 * inlining budget*, mark the method as inlined and keep exploring the call graph through its
 * non-inlined children.</li>
 * <li>In breadth-first order, inline all reachable calls that have been marked as inlined.</li>
 * <li>Check if inlining made some calls disappear at the root, and replace those with Deleted
 * nodes.</li>
 * <li>Restore invariants at the root, such as information about subtrees.</li>
 * </ol>
 *
 *
 * <h2>Termination conditions</h2>
 *
 * The termination conditions for the overall inlining phase are the following:
 *
 * <ul>
 * <li>The call graph had been completely expanded, and there are no more Cutoff nodes.</li>
 * <li>Call graph is too big, either in terms of total number of call graph nodes, or in terms of
 * the total number of compiler nodes in all expanded subgraphs.</li>
 * <li>Inlined code is too big, measured in the total number of compiler nodes inlined into the root
 * method.</li>
 * <li>There were no nodes inlined in two consecutive inlining stages (this can mean that there are
 * leftover Cutoff nodes, but they are not interesting enough to be expanded).</li>
 * </ul>
 *
 *
 * <h2>Other considerations</h2>
 *
 * To prevent inlining recursive calls, this phase tracks how often specific methods were
 * encountered in the call graph. In particular:
 *
 * <ul>
 * <li>Recursive methods get exponential priority penalties with respect to their inlining depth,
 * and are less likely to be expanded.</li>
 * <li>Recursive methods with more than a specific number of occurrences in the call graph are never
 * expanded if they recurse.</li>
 * </ul>
 *
 * Parameters are enhanced with additional type information after the expansion takes place. This
 * enhancement can remove some nodes, and convert Indirect or InlineCache nodes into Cutoff nodes.
 */
public abstract class AbstractPriorityInliningPhase extends AbstractInliningPhase {

    enum TrackInliningMode {
        none(false, false),
        interactive(true, true),
        dump(true, false);

        private boolean shouldTrack;
        private boolean isInteractive;

        TrackInliningMode(boolean shouldTrack, boolean isInteractive) {
            this.shouldTrack = shouldTrack;
            this.isInteractive = isInteractive;
        }

        public boolean shouldTrack() {
            return shouldTrack;
        }

        public boolean isInteractive() {
            return isInteractive;
        }
    }

    /** Options shared by all priority-inlining phase implementations. */
    public static class Options {
        //@formatter:off
        @Option(help = "Track inlining statistics (inlining duration, call tree size, compiler node counts, and the number of callsites). One of: none, interactive", type = OptionType.Debug, stability = OptionStability.EXPERIMENTAL)
        public static final OptionKey<TrackInliningMode> TrackInliningStatistics = new EnumOptionKey<>(TrackInliningMode.none);

        @Option(help = "Unconditionally inline all methods matching the pattern using the priority-based inliner. " +
                        "See the MethodFilter option for a description of the pattern syntax.", type = OptionType.Debug)
        public static final OptionKey<String> PriorityForceInline = new OptionKey<>(null);

        @Option(help = "Never inline methods matching the pattern using the priority-based inliner. " +
                        "See the MethodFilter option for a description of the pattern syntax.", type = OptionType.Debug)
        public static final OptionKey<String> PriorityNeverInline = new OptionKey<>(null);
        //@formatter:on
    }

    private static final InliningStatistics inliningStatistics = new InliningStatistics();
    private static final long NANOS_IN_MILLI = 1_000_000;
    @SharedGlobalPhaseState private static volatile boolean shutdownHookAdded;

    protected final CanonicalizerPhase canonicalizer;
    private final InliningProvider inliningProvider;
    private final TrackInliningMode trackInliningMode;
    private final PolicyFactory policyFactory;
    private final MethodFilter priorityForceInlineFilter;
    protected final DirectedInliningRules.RuleSet directedRules;

    protected AbstractPriorityInliningPhase(CanonicalizerPhase canonicalizer, OptionValues options, InliningProvider inliningProvider) {
        this(canonicalizer, options, inliningProvider, inliningProvider.policy(options));
    }

    protected InliningProvider getInliningProvider() {
        return inliningProvider;
    }

    protected AbstractPriorityInliningPhase(CanonicalizerPhase canonicalizer, OptionValues options, InliningProvider inliningProvider, PolicyFactory policyFactory) {
        this.canonicalizer = canonicalizer;
        this.inliningProvider = inliningProvider;
        this.policyFactory = policyFactory;
        this.trackInliningMode = Options.TrackInliningStatistics.getValue(options);
        this.priorityForceInlineFilter = Options.PriorityForceInline.getValue(options) == null ? null
                        : MethodFilter.parse(Options.PriorityForceInline.getValue(options));
        this.directedRules = DirectedInliningRules.parse(InliningPhase.Options.DirectedInline.getValue(options), InliningPhase.Options.DirectedDontInline.getValue(options),
                        InliningPhase.Options.DirectedInliningRulesFile.getValue(options));
    }

    private static void registerInliningStatisticsShutdownHook() {
        if (!shutdownHookAdded) {
            synchronized (InliningStatistics.class) {
                if (!shutdownHookAdded) {
                    try {
                        Runtime.getRuntime().addShutdownHook(new Thread(inliningStatistics::printAll));
                        shutdownHookAdded = true;
                    } catch (IllegalStateException ise) {
                        // VM is already in process of shutting down - ignore
                    }
                }
            }
        }
    }

    protected AbstractPriorityInliningPhase(CanonicalizerPhase canonicalizer, OptionValues options) {
        this(canonicalizer, options, new DefaultInliningProvider());
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        super.notApplicableTo(graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.FINAL_PARTIAL_ESCAPE, graphState));
    }

    @Override
    protected boolean isForceInlinedTarget(ResolvedJavaMethod targetMethod, Invoke invoke) {
        if (directedRules.matchesDontInline(invoke, targetMethod)) {
            return false;
        }
        boolean forceInlined = directedRules.matchesInlineOrPrefix(invoke, targetMethod, null) ||
                        super.isForceInlinedTarget(targetMethod, invoke) ||
                        priorityForceInlineFilter != null && priorityForceInlineFilter.matches(targetMethod);
        if (!forceInlined) {
            return false;
        }
        return !isExcludedRootInvoke(invoke);
    }

    private boolean isExcludedRootInvoke(Invoke invoke) {
        if (isRootGraphInvoke(invoke)) {
            /*
             * Restricting the inliner to an explicit subset of root invokes only excludes the
             * non-selected root invokes from this pass. Descendants under the allowed roots are
             * still explored normally and must continue to satisfy force-inline verification.
             */
            List<Invoke> rootInvokeAllowed = inliningProvider.rootInvokeAllowed(invoke.asNode().graph());
            if (rootInvokeAllowed != null && !rootInvokeAllowed.contains(invoke)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void runInlining(StructuredGraph graph, HighTierContext context) {
        runInstance(graph, context);
    }

    public CallTree runInstance(StructuredGraph graph, HighTierContext context) {
        Instance instance = createInstance(graph, context);
        instance.run(context);
        return instance.callTree;
    }

    public Instance createInstance(StructuredGraph graph, HighTierContext context) {
        OptionValues options = graph.getOptions();
        return createInstance(graph, context, policyFactory.createExpanderPolicy(options, context), policyFactory.createInlinerPolicy(options), policyFactory.createTuningPolicy(options));
    }

    protected Instance createInstance(StructuredGraph graph, HighTierContext context, Expander.Policy expanderPolicy, Inliner.Policy inlinerPolicy, TuningPolicy analysis) {
        final TimerKey optimizationDuration = DebugContext.timer(getClass().getSimpleName() + "_optimizations");
        final TimerKey totalDuration = DebugContext.timer(getClass().getSimpleName() + "_total");
        final TimerKey expanderExtraAnalysisDuration = DebugContext.timer(getClass().getSimpleName() + "_extraAnalysis");
        return new Instance(context, graph, optimizationDuration, totalDuration, expanderExtraAnalysisDuration, expanderPolicy, inlinerPolicy, analysis);
    }

    protected SubgraphNode createRootNode(GraphCache<ResolvedJavaMethod, StructuredGraph> graphCache, StructuredGraph graph) {
        return new SubgraphNode(null, null, 1.0, graphCache.createRef(null, graph), true, null, null, null, EnumSet.noneOf(BenefitKind.class), false);
    }

    protected CallTree createCallTree(HighTierContext context, StructuredGraph graph, Expander.Policy expanderPolicy, TuningPolicy analysis) {
        GraphCache<ResolvedJavaMethod, StructuredGraph> graphCache = new GraphCache<>();
        SubgraphNode root = createRootNode(graphCache, graph);
        return new CallTree(canonicalizer, expanderPolicy, analysis, context, inliningProvider, graphCache, root, graph.getOptions(), directedRules);
    }

    public class Instance {
        private final CallTree callTree;
        private final Expander expander;
        private final Inliner inliner;
        private final Optimizer optimizer;
        final TimerKey totalDuration;
        final TimerKey optimizationDuration;
        final TimerKey expanderExtraAnalysisDuration;

        protected Instance(HighTierContext context, StructuredGraph graph, TimerKey optimizationDuration, TimerKey totalDuration, TimerKey expanderExtraAnalysisDuration,
                        Expander.Policy expanderPolicy, Inliner.Policy inlinerPolicy, TuningPolicy analysis) {
            this.optimizationDuration = optimizationDuration;
            this.expanderExtraAnalysisDuration = expanderExtraAnalysisDuration;
            this.totalDuration = totalDuration;
            this.callTree = createCallTree(context, graph, expanderPolicy, analysis);
            this.expander = new Expander(expanderPolicy, analysis, expanderExtraAnalysisDuration);
            this.inliner = new Inliner(inlinerPolicy, analysis);
            this.optimizer = new Optimizer(optimizationDuration);
        }

        public CallTree callTree() {
            return callTree;
        }

        @SuppressWarnings("try")
        public void run(CoreProviders coreProviders) {
            SubgraphNode root = callTree.root();
            StructuredGraph rootGraph = root.getReadonlySubgraph();
            DebugContext debug = callTree.getDebug();
            int totalRounds = 0;
            try (DebugCloseable c = totalDuration.start(debug)) {
                callTree.initialize();
                InliningLog inliningLog = rootGraph.getInliningLog();
                if (inliningLog != null) {
                    inliningLog.checkInvariants(rootGraph);
                }

                try (DebugContext.Scope s = debug.scope("PriorityCallGraph", callTree)) {
                    totalRounds = inlineUntilConvergence(coreProviders);
                    inlineForceInlined(coreProviders);
                } catch (Throwable e) {
                    debug.handle(e);
                }

                callTree.devirtualizeHotCallees(coreProviders);

                expander.policy().applyPostPhases(rootGraph, callTree.getContext());

                for (CallTreeNode child : root.children()) {
                    Invoke invoke = child.invoke();
                    String reason = child.getDontInlineCause().longDescription();
                    rootGraph.notifyInliningDecision(invoke, false, "PriorityInliningPhase", null, null, null, invoke.getTargetMethod(), reason);
                    if (invoke.callTarget() != null) {
                        InliningUtil.traceNotInlinedMethod(invoke, child.getDepth(), invoke.getTargetMethod(), child.getDontInlineCause().shortDescription());
                    }
                }
            }

            if (trackInliningMode.shouldTrack()) {
                registerInliningStatisticsShutdownHook();
                String methodName = rootGraph.method().format("%H.%n");
                int invokesLeft = rootGraph.getNodes(MethodCallTargetNode.TYPE).count();
                inliningStatistics.enter(methodName, totalDuration.getCurrentValue(debug) / NANOS_IN_MILLI, callTree.getNodeCount(), root.getSubtreeTotalCompilerNodeCount(),
                                rootGraph.getNodeCount(), callTree.getTotalNodesParsed(),
                                optimizationDuration.getCurrentValue(debug) / NANOS_IN_MILLI, optimizer.getEscapeAnalysisCount(), invokesLeft, totalRounds,
                                expanderExtraAnalysisDuration.getCurrentValue(debug) / NANOS_IN_MILLI, callTree.state().numMethodsInlined(), expander.policy().getExtraStatisticsMetric(callTree));
            }
            if (trackInliningMode.isInteractive()) {
                inliningStatistics.logLast();
            }
        }

        @SuppressWarnings("try")
        private int inlineUntilConvergence(CoreProviders coreProviders) {
            DebugContext debug = callTree.getDebug();
            while (inliner.policy().shouldContinueInlining(callTree, optimizer, coreProviders)) {
                debug.dump(DebugContext.VERBOSE_LEVEL, callTree, "round %d, before expansion", callTree.state().round());

                // Policy initialization
                inliner.policy().beforeRound(callTree, optimizer, coreProviders);
                expander.policy().beforeRound(callTree);

                // Run expander round.
                callTree.state().setHasExpandedSinceLastRound(false);
                expander.run(callTree, coreProviders, callTree.state().round());
                assert checkInvariants(callTree.root());

                // Run inlining round.
                callTree.state().setInlinedSinceLastExpansion(false);
                inliner.run(callTree, coreProviders, callTree.state().round());
                assert checkInvariants(callTree.root());
                debug.dump(DebugContext.VERBOSE_LEVEL, callTree, "round %d, after inlining", callTree.state().round());

                // Run optimization round.
                optimizer.performPeeling(callTree, coreProviders);
                Inliner.removeDeletedChildren(callTree);
                callTree.state().incRound();
            }
            debug.dump(DebugContext.VERBOSE_LEVEL, callTree, "call tree at the end");
            return callTree.state().round();
        }

        /**
         * Repeatedly expand and inline force-inlined invokes that remain or become direct children
         * of the root after the policy driven algorithm has converged or stopped due to its limits.
         * Recursive force-inlined invokes still terminate because recursive expansion is limited:
         * when a recursive invoke has been expanded often enough, expansion produces a non-inlineable
         * {@link GenericNode} instead of a {@link ParentNode}.
         */
        private void inlineForceInlined(CoreProviders coreProviders) {
            boolean changed;
            do {
                changed = false;

                for (CallTreeNode child : callTree.root().children().snapshot()) {
                    if (directedRules.inlineRules() != null &&
                                    child instanceof InlineCacheNode inlineCacheNode &&
                                    markForceInlinedInlineCacheChildren(inlineCacheNode, coreProviders)) {
                        inliner.inlineForceInlinedRootChildAndRestoreInvariants(callTree, inlineCacheNode);
                        assert checkInvariants(callTree.root());
                        changed = true;
                        continue;
                    }
                    if (!child.isForceInlined()) {
                        continue;
                    }
                    CallTreeNode forceInlinedChild = child;
                    if (child instanceof CutoffNode cutoff) {
                        forceInlinedChild = expander.expandFinalForceInlinedRootCutoff(cutoff, coreProviders, callTree.state().round());
                    }
                    if (forceInlinedChild instanceof ParentNode parentNode && forceInlinedChild.isForceInlined()) {
                        inliner.inlineForceInlinedRootChildAndRestoreInvariants(callTree, parentNode);
                        assert checkInvariants(callTree.root());
                    }
                    changed = true;
                }
            } while (changed);
        }

        private boolean markForceInlinedInlineCacheChildren(InlineCacheNode inlineCacheNode, CoreProviders coreProviders) {
            boolean hasForceInlinedChild = false;
            for (CallTreeNode child : inlineCacheNode.children().snapshot()) {
                if (!child.isForceInlined()) {
                    continue;
                }
                CallTreeNode forceInlinedChild = child;
                if (child instanceof CutoffNode cutoff) {
                    forceInlinedChild = expander.expandFinalForceInlinedCutoff(cutoff, coreProviders, callTree.state().round());
                }
                if (forceInlinedChild instanceof ParentNode && forceInlinedChild.isForceInlined()) {
                    Inliner.setForceInlineCause(callTree, forceInlinedChild);
                    forceInlinedChild.markInlined();
                    hasForceInlinedChild = true;
                }
            }
            return hasForceInlinedChild;
        }

        private boolean checkInvariants(CallTreeNode node) {
            // Check that corresponding invokes are alive.
            for (CallTreeNode child : node.children()) {
                if (child instanceof DeletedNode) {
                    continue;
                }
                assert child.invoke().asNode().isAlive() : child;
            }
            // Check recursively.
            for (CallTreeNode child : node.children()) {
                checkInvariants(child);
            }
            return true;
        }
    }

    @Override
    public boolean checkContract() {
        return false;
    }
}
