/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.foreign.phases;

import static jdk.graal.compiler.debug.DebugContext.VERY_DETAILED_LEVEL;

import java.lang.foreign.Arena;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.word.LocationIdentity;

import com.oracle.svm.core.nodes.ClusterNode;
import com.oracle.svm.core.nodes.foreign.MemoryArenaValidInScopeNode;
import com.oracle.svm.core.nodes.foreign.ScopedMemExceptionHandlerClusterNode.ClusterBeginNode;
import com.oracle.svm.core.nodes.foreign.ScopedMemExceptionHandlerClusterNode.ExceptionInputNode;
import com.oracle.svm.core.nodes.foreign.ScopedMemExceptionHandlerClusterNode.ExceptionPathNode;
import com.oracle.svm.core.nodes.foreign.ScopedMemExceptionHandlerClusterNode.RegularPathNode;
import com.oracle.svm.core.nodes.foreign.ScopedMethodNode;

import jdk.graal.compiler.core.common.cfg.CFGLoop;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeStack;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardPhiNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.SafepointNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph.RecursiveVisitor;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.RecursivePhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.graph.ReentrantBlockIterator;
import jdk.graal.compiler.phases.graph.ReentrantBlockIterator.BlockIteratorClosure;
import jdk.graal.compiler.phases.schedule.SchedulePhase;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.internal.foreign.MemorySessionImpl;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Implements support for shared arenas on SVM. An {@link Arena} can be shared across multiple
 * threads by {@link Arena#ofShared()} in which case closing of the arena needs to be recognized by
 * other threads concurrently accessing such an arena.
 *
 * For details on the exception path see the Javadoc in scoped memory access substitutions in {@code
 * Target_jdk_internal_misc_ScopedMemoryAccess}. Please read that first before reading the doc of
 * this phase as it implements the abstract idea expressed in the substitution code.
 *
 * The general pattern for checking session validity can be seen in
 * {@link com.oracle.svm.core.foreign.SubstrateForeignUtil#sessionExceptionHandler(MemorySessionImpl, Object, long)}.
 * 
 * The rest of this doc talks about finding and inserting control flow modeling the validity checks
 * for a memory session. The general invariant that must always hold is that there must be no
 * safepoint between the check of a memory access and the memory access itself. The idea is roughly
 * explained by example in the following pieces of code:
 *
 * <pre>
 * scopeCheck {
 *     safepoint();
 *     // needs a check because safepoint happened before, could have closed arena
 *     useSession();
 * }
 * </pre>
 *
 * <pre>
 * scopeCheck {
 *     // no check needed because no safepoint happened since the scope check
 *     useSession();
 *     safepoint();
 * }
 * </pre>
 *
 * <pre>
 * scopeCheck {
 *     while (header) {
 *         // Needs a check because a safepoint may have happened along the loop backedge.
 *         useSession();
 *         safepoint();
 *     }
 * }
 * </pre>
 *
 * <pre>
 * scopeCheck {
 *     while (header) {
 *         // needs a check not because its dominated by a safepoint but inside a loop if there is
 *         // a single safepoint possible the entire loop needs checking
 *         useSession();
 *         useSession(); // does not need a check, because we already checked before a dominating
 *                       // node and no safepoint in between yet.
 *         safepoint();
 *     }
 * }
 * </pre>
 *
 * We analyze the code and ensure that every access that uses a {@link MemorySessionImpl} or a value
 * associated with a session is checked for potential concurrent close. Concurrent closing on SVM
 * can only happen at a safepoint. Thus, we analyze the graph for a safepoint. By dominance we try
 * to use a minimal set of checks, in a sense that every path dominated by a safepoint (or in a loop
 * where there is a safepoint on any backedge) checks a minimal number of times.
 *
 * We do this by analyzing the dominator tree of a program. Every time we see the entry of a scoped
 * method (checked by special nodes {@link MemoryArenaValidInScopeNode} inserted by the parser) we
 * start analyzing memory access patterns for shared arena usage. We mark all of those accesses
 * together with the respective session. Later we minimize the checks by scanning over the
 * {@link ControlFlowGraph} in reverse post order only inserting the minimal amount of session
 * checks.
 *
 * As a running example consider the following piece of code
 *
 * <pre>
 * void foo(MemorySession m1) {
 *     scopedAccess(m1);
 *     {
 *         if (sth) {
 *             memAccess(m1);
 *             memAccess(m1);
 *             memAccess(m1);
 *         }
 *         memAccess(m1);
 *         safepoint();
 *         memAccess(m1);
 *         memAccess(m1);
 *     }
 *     scopedAccess(m1);
 *     {
 *         for (int i = 0; i < firstLimit; i++) {
 *             code();
 *             memAccess(m1);
 *             for (int j = 0; j < secondLimit; j++) {
 *                 moreCode();
 *                 memAccess(m1);
 *                 safepoint();
 *                 memAccess(m1);
 *             }
 *             memAccess(m1);
 *             evenMoreCode();
 *             memAccess(m1);
 *         }
 *     }
 * }
 * </pre>
 *
 * We process the dominator tree of the program and record all accesses. We record per node (see
 * {@code processNode in this file}) a set of {@link ScopedAccess} - such can be a safepoint or a
 * regular memory operation. If we are inside a loop we also have to record a safepoint entry per
 * session for every loop header between the scope start and this safepoint: this is necessary as
 * any path in a nested loop that can do a safepoint.
 *
 * See the following pseudo code with annotations as a rough depiction of what is going on
 *
 * <pre>
 * void foo(MemorySession m1) {
 *     scopedAccess(m1); // start check m1
 *     {
 *         if (sth) {
 *             memAccess(m1); // m1 access
 *             memAccess(m1); // m1 access
 *             memAccess(m1); // m1 access
 *         }
 *         memAccess(m1); // m1 access
 *         safepoint();  // safepoint potentially closing m1
 *         memAccess(m1); // m1 access
 *         memAccess(m1); // m1 access
 *     } // stop check m1
 *     scopedAccess(m1); // start check m1
 *     {
 *         for (int i = 0; i < firstLimit; i++) { // header of loop with safepoint potentially
 *                                                // closing m1
 *             code();
 *             memAccess(m1); // m1 access
 *             for (int j = 0; j < secondLimit; j++) { // header of loop with safepoint potentially
 *                                                     // closing m1
 *                 moreCode();
 *                 memAccess(m1); // m1 access
 *                 safepoint();  // safepoint potentially closing m1
 *                 memAccess(m1); // m1 access
 *             }
 *             memAccess(m1); // m1 access
 *             evenMoreCode();
 *             memAccess(m1); // m1 access
 *         }
 *     }
 * }
 * </pre>
 *
 * In order to transport this information during optimziation we create a so called "sugared" graph.
 * That is a mapping of the nodes of the structured graph to scope using accesses. For this we
 * record a mapping of each node to a list of scoped accesses
 * {@code EconomicMap<Node, List<ScopedAccess>>}. A {@code ScopedAccess} can be a safepoint or a
 * memory access. Every time we process a memory access or a safepoint we record an entry. If we are
 * processing a safepoint node every value associated with an open scope is enqueued for checking.
 *
 * The following pseudo code should outline the "sugared" graph concept before optimization
 *
 * <pre>
 * void foo(MemorySession m1) {
 *     scopedAccess(m1); // <- memoryAccessEntry(m1)
 *     {
 *         if (sth) {
 *             memAccess(m1); // <- memoryAccessEntry(m1)
 *             memAccess(m1);// <- memoryAccessEntry(m1)
 *             memAccess(m1);// <- memoryAccessEntry(m1)
 *         }
 *         memAccess(m1); // <- memoryAccessEntry(m1)
 *         safepoint(); // <- safepointEntry(m1)
 *         memAccess(m1); // <- memoryAccessEntry(m1)
 *         memAccess(m1);// <- memoryAccessEntry(m1)
 *     }
 *     scopedAccess(m1);
 *     {
 *         for (int i = 0; i < firstLimit; i++) { // <- safepointEntry(m1)
 *             code();
 *             memAccess(m1); // <- memoryAccessEntry(m1)
 *             for (int j = 0; j < secondLimit; j++) {// <- safepointEntry(m1)
 *                 moreCode();
 *                 memAccess(m1);// <- memoryAccessEntry(m1)
 *                 safepoint();// // <- safepointEntry(m1)
 *                 memAccess(m1);// <- memoryAccessEntry(m1)
 *             }
 *             memAccess(m1);// <- memoryAccessEntry(m1)
 *             evenMoreCode();
 *             memAccess(m1);// <- memoryAccessEntry(m1)
 *         }
 *     }
 * }
 * </pre>
 *
 *
 * With this "sugared" graph we perform a reverse post order iteration of the control flow graph.
 * This is implemented in {@link MinimalSessionChecks}: we use a scan line based approach going over
 * every node we encounter. We encode this "scan line" in a {@link NodeBitMap}. Every time we find a
 * safepoint entry in the sugared graph we mark the associated node to be checked. The associated
 * node representes the corresponding memory session. If its marked with 1 we need to check its
 * validity before we can access it the next time. This however is done lazily, only if an access is
 * encountered we actually check the validity. Marking a session for validity checking means setting
 * the value in the node bit map to 1. This means it has to be checked. If we encounter an access
 * operation we duplicate the checking control flow and clear the bit. Only a safepoint entry will
 * mark the bit again so repetitive access will NOT be checked. Also this approach should guarantee
 * a minimal lazy throwing semantic== this means we only throw on a closed session if we are
 * actually accessing it after close. If there is a close we are inside a loop that accessed it but
 * we are after the safepoint and the loop is about to finish we do not check it again.
 *
 * For the example above using the scanline approach would create the following final code:
 *
 * <pre>
 * void foo(MemorySession m1) {
 *     if (sth) { // no checks needed here
 *         memAccess(m1);
 *         memAccess(m1);
 *         memAccess(m1);
 *     }
 *     memAccess(m1);
 *     safepoint(); // first check after safepoint
 *     checkSessionValid(m1);
 *     memAccess(m1);
 *     memAccess(m1);
 *
 *     for (int i = 0; i < firstLimit; i++) {
 *         code();
 *         // check because safepoint on a backedge could have caused invalidation, we do not know
 *         // which backedge, that is impossible to know so we have to check
 *         checkSessionValid(m1);
 *         memAccess(m1);
 *         for (int j = 0; j < secondLimit; j++) {
 *             moreCode(); // same, in inner loop safepoint could have happened, need to check
 *             checkSessionValid(m1);
 *             memAccess(m1);
 *             safepoint(); // check after safepoint
 *             checkSessionValid(m1);
 *             memAccess(m1);
 *         }
 *         // check because the inner loop contains a safepoint, it can be we safepoint but dont
 *         // check because no access happened
 *         checkSessionValid(m1);
 *         memAccess(m1);
 *         evenMoreCode();
 *         memAccess(m1);
 *     }
 * }
 * </pre>
 *
 * For loop backedges we have to use a conservative approach - if any backedge may safepoint we need
 * to check on every enclosing loop because the inner loop could have been executed any time we go
 * through a backedge (and visit the loop header).
 */
public class SubstrateOptimizeSharedArenaAccessPhase extends BasePhase<MidTierContext> implements RecursivePhase {

    public interface OptimizeSharedArenaConfig {
        /**
         * Tests if calls to the given callee may remain in the critical region of
         * an @Scoped-annotated method. Usually, this is the case if (1) the callee does not do any
         * safepoint checks (recursively), or (2) the callee does not access native memory of a
         * shared arena AND exits with an exception. The second case normally covers methods that
         * are part of the path that checks the 'checkValidStateRaw' and throws an exception
         * otherwise.
         */
        boolean isSafeCallee(ResolvedJavaMethod method);
    }

    final CanonicalizerPhase canonicalizer;
    final OptimizeSharedArenaConfig config;

    public SubstrateOptimizeSharedArenaAccessPhase(CanonicalizerPhase canonicalizer, OptimizeSharedArenaConfig config) {
        this.canonicalizer = canonicalizer;
        this.config = config;
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(NotApplicable.unlessRunAfter(this, StageFlag.SAFEPOINTS_INSERTION, graphState));
    }

    @Override
    public boolean shouldApply(StructuredGraph graph) {
        return graph.getNodes().filter(MemoryArenaValidInScopeNode.class).count() > 0 || graph.getNodes().filter(ScopedMethodNode.class).count() > 0;
    }

    /**
     * Log building, processing and duplication of shared arena exception handlers to stdout:
     * development only, creates a lot of output.
     */
    public static final boolean LOG_SHARED_ARENAS = false;

    /**
     * Unconditionally run a schedule before this phase and after every larger graph rewrite. Very
     * runtime intensive - only use during development.
     */
    public static final boolean RUN_SHARED_ARENA_SCHEDULE_VERIFICATION = false;

    /**
     * Only apply the exception handler duplication for methods matching the debug dump filter.
     * Helps to narrow down problems in large image builds, only use during development.
     */
    public static final boolean ONLY_DUPLICATE_DUMP_METHODS = false;

    /**
     * Also dump large sets of nodes as IGV graphs during exception handler duplication - only use
     * during development.
     */
    public static final boolean DUMP_LARGE_NODE_SETS = false;

    /**
     * Verify that there are no invokes left in the scoped methods after application of this phase.
     */
    public static final boolean VERIFY_NO_DOMINATED_CALLS = true;

    static class SessionStateRawGraphCluster {
        MemoryArenaValidInScopeNode scopeStart;
        ClusterBeginNode clusterBegin;
        FixedNode firstClusterNode;
        ExceptionPathNode clusterExceptionEnd;
        RegularPathNode clusterNonExceptionEnd;
        ExceptionInputNode session;

        private boolean validForBuilding() {
            assert scopeStart != null : Assertions.errorMessage("Must not be null");
            assert clusterBegin != null : Assertions.errorMessage("Must not be null", scopeStart);
            assert clusterExceptionEnd != null : Assertions.errorMessage("Must not be null", scopeStart);
            assert clusterNonExceptionEnd != null : Assertions.errorMessage("Must not be null", scopeStart);
            return true;
        }

        /**
         * If we are not seeing the exceptional part of the exception handler method call it
         * normally means either the session was unconditionally null and we can never throw or we
         * never see a arena.close() operation during static analysis in which case we also can
         * never throw.
         */
        private boolean isNullSessionOrNeverClosedArena() {
            return scopeStart != null && clusterBegin != null && clusterNonExceptionEnd != null && clusterExceptionEnd == null;
        }

        static SessionStateRawGraphCluster build(MemoryArenaValidInScopeNode arenaNode) {
            if (LOG_SHARED_ARENAS) {
                TTY.printf("Building cluster for %s in %s%n", arenaNode, arenaNode.graph());
            }
            SessionStateRawGraphCluster cluster = new SessionStateRawGraphCluster();
            for (Node usage : arenaNode.usages()) {
                if (usage instanceof ClusterBeginNode clusterBegin) {
                    assert cluster.clusterBegin == null;
                    cluster.clusterBegin = clusterBegin;
                } else if (usage instanceof RegularPathNode clusterNonExceptionEnd) {
                    assert cluster.clusterNonExceptionEnd == null;
                    cluster.clusterNonExceptionEnd = clusterNonExceptionEnd;
                } else if (usage instanceof ExceptionPathNode clusterExceptionEnd) {
                    assert cluster.clusterExceptionEnd == null;
                    cluster.clusterExceptionEnd = clusterExceptionEnd;
                } else if (usage instanceof ExceptionInputNode clusterInput) {
                    assert cluster.session == null;
                    cluster.session = clusterInput;
                }
            }
            cluster.scopeStart = arenaNode;
            if (cluster.isNullSessionOrNeverClosedArena()) {
                return null;
            }
            assert cluster.validForBuilding();
            cluster.collectNodes();
            return cluster;
        }

        NodeBitMap clusterNodes;

        private void collectNodes() {
            assert clusterNodes == null;
            final StructuredGraph graph = scopeStart.graph();
            clusterNodes = graph.createNodeBitMap();

            clusterNodes.mark(clusterBegin);

            NodeStack toProcess = new NodeStack();
            toProcess.push(clusterExceptionEnd);
            toProcess.push(clusterNonExceptionEnd);

            // make sure any nodes in between are also properly marked
            firstClusterNode = clusterBegin;
            FixedNode f = clusterBegin;
            while (!(f.predecessor() instanceof IfNode)) {
                clusterNodes.mark(f);
                firstClusterNode = f;
                ensureUniqueStates(f);
                f = (FixedNode) f.predecessor();
            }

            // first collect fixed nodes
            while (!toProcess.isEmpty()) {
                Node cur = toProcess.pop();
                if (clusterNodes.isMarked(cur)) {
                    continue;
                }
                clusterNodes.mark(cur);
                for (Node pred : cur.cfgPredecessors()) {
                    toProcess.push(pred);
                }
                ensureUniqueStates(cur);
            }

            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After duplicating states for collection of cluster");

            toProcess.push(clusterExceptionEnd);
            toProcess.push(clusterNonExceptionEnd);

            clusterNodes.mark(session);
            clusterNodes.mark(scopeStart);

            if (DUMP_LARGE_NODE_SETS) {
                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Cluster data clusterBegin=%s, firstClusterNode=%s, clusterExceptionEnd=%s, clusterNonExceptionEnd=%s,session=%s",
                                clusterBegin, firstClusterNode, clusterExceptionEnd, clusterNonExceptionEnd, session);
                graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Fixed cluster start node %s and regular nodes are %s", firstClusterNode, clusterNodes);
            }

            boolean change = true;
            int iterations = 0;
            while (change) {
                change = false;
                int before = clusterNodes.count();
                // process a second time and all inputs
                while (!toProcess.isEmpty()) {
                    Node cur = toProcess.pop();

                    // only the session node marks a boundary to which we should stop processing
                    // inputs
                    if (cur == firstClusterNode || cur == scopeStart) {
                        continue;
                    }

                    for (Node pred : cur.cfgPredecessors()) {
                        toProcess.push(pred);
                    }
                    for (Node input : cur.inputs()) {
                        visitUntilCluster(clusterNodes, input);
                    }
                    // phis need duplication
                    if (cur instanceof MergeNode m) {
                        for (PhiNode phi : m.phis()) {
                            clusterNodes.checkAndMarkInc(phi);
                            for (Node input : phi.inputs()) {
                                visitUntilCluster(clusterNodes, input);
                            }
                        }
                    }

                    // some nodes need duplication because of the edge type not being able to handle
                    // phis
                    for (Position p : cur.inputPositions()) {
                        Node input = p.get(cur);
                        if (input == null) {
                            continue;
                        }
                        if (p.getInputType() == InputType.State || p.getInputType() == InputType.Extension) {
                            // we cannot create phis for extensions or framestates, thus force
                            // duplication of them
                            clusterNodes.mark(input);
                            for (Node assocInput : input.inputs()) {
                                visitUntilCluster(clusterNodes, assocInput);
                            }
                        }
                    }
                }
                change = clusterNodes.count() > before;
                if (iterations++ > MAX_CLUSTER_ITERATIONS) {
                    throw GraalError.shouldNotReachHere("Cluster nodes not stabilizing");
                }
            }
            // no need to duplicate the scope
            clusterNodes.clear(scopeStart);
        }

        private static void ensureUniqueStates(Node cur) {
            // make sure states are unique info points for example also reference states and
            // themselves are not deopt nodes, thus force it
            for (Position p : cur.inputPositions()) {
                Node input = p.get(cur);
                if (input == null) {
                    continue;
                }
                if (p.getInputType() == InputType.State) {
                    // we cannot phi state edges
                    FrameState fs = (FrameState) input;
                    p.set(cur, fs.duplicateWithVirtualState());
                }
            }
        }

    }

    private static final int MAX_CLUSTER_ITERATIONS = 8;

    private static void visitUntilCluster(NodeBitMap inCluster, Node start) {
        NodeBitMap visited = start.graph().createNodeBitMap();
        visitUntilCluster(inCluster, start, visited, "");
    }

    @SuppressWarnings("unused")
    private static void visitUntilCluster(NodeBitMap inCluster, Node start, NodeBitMap visited, String p) {
        if (LOG_SHARED_ARENAS) {
            TTY.printf("%sVisiting %s%n", p, start);
        }
        if (visited.isMarkedAndGrow(start)) {
            return;
        }
        if (inCluster.isMarkedAndGrow(start)) {
            return;
        }
        if (start instanceof PhiNode phi) {
            /*
             * A phi is only part of the cluster if its merge is part of the cluster, else we stop
             * here and its explicitly never part of the cluster.
             */
            if (!inCluster.contains(phi.merge())) {
                // the phi must never be part of the cluster
                assert !inCluster.isMarked(phi) : "Must not mark phi if merge is not part of the cluster " + phi;
                visited.mark(phi);
                return;
            }
        }
        visited.mark(start);
        boolean inputsInCluster = false;
        String inputIn = LOG_SHARED_ARENAS ? "" : null;
        for (Node input : start.inputs()) {
            visitUntilCluster(inCluster, input, visited, LOG_SHARED_ARENAS ? p + "\t" : null);
            boolean inputInCluster = inCluster.isMarkedAndGrow(input);
            if (LOG_SHARED_ARENAS && inputInCluster) {
                inputIn += input.toString() + ",";
            }
            inputsInCluster = inputInCluster || inputsInCluster;
        }
        if (inputsInCluster) {
            if (LOG_SHARED_ARENAS) {
                TTY.printf("%sMarking %s, inputs in are[%s]%n", p, start, inputIn);
            }
            inCluster.markAndGrow(start);
        }
    }

    private static void scheduleVerify(StructuredGraph graph) {
        if (RUN_SHARED_ARENA_SCHEDULE_VERIFICATION) {
            SchedulePhase.runWithoutContextOptimizations(graph);
        }
    }

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {
        scheduleVerify(graph);
        if (ONLY_DUPLICATE_DUMP_METHODS) {
            if (!graph.getDebug().isDumpEnabledForMethod()) {
                cleanupClusterNodes(graph, context, null);
                return;
            }
        }
        cleanupClusterNodes(graph, context, insertSessionChecks(graph, context));
    }

    private EconomicSet<DominatedCall> insertSessionChecks(StructuredGraph graph, MidTierContext context) {
        if (graph.getNodes().filter(ScopedMethodNode.class).count() == 0) {
            /*
             * We are, for whatever reason, compiling the exception handler template method, we do
             * not verify no calls and we do not duplicate any session checks inside.
             */
            return null;
        }
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).modifiableBlocks(true).connectBlocks(true).computeFrequency(true).computeLoops(true).computeDominators(true)
                        .computePostdominators(true)
                        .build();
        // Compute the graph with all the necessary data about scoped memory accesses.
        EconomicSet<DominatedCall> calls = EconomicSet.create();
        EconomicMap<Node, List<ScopedAccess>> sugaredGraph = enumerateScopedAccesses(config, cfg, context, calls);
        if (sugaredGraph != null) {
            ReentrantBlockIterator.apply(new MinimalSessionChecks(graph, sugaredGraph, cfg, calls), cfg.getStartBlock());
        }
        return calls;
    }

    /*
     * Now iterate the CFG in reverse post order. Based on the previous analysis of the control flow
     * graph we know where safepoints happened and we know where we are accessing scoped memory. Now
     * if there is a safepoint happening we must check the associated session accesses. If we
     * checked one we can stop checking until the next safepoint.
     */
    static class MinimalSessionChecks extends BlockIteratorClosure<NodeBitMap> {
        private final StructuredGraph graph;
        private final EconomicMap<Node, List<ScopedAccess>> sugaredGraph;
        private final ControlFlowGraph cfg;
        private final EconomicSet<DominatedCall> calls;

        MinimalSessionChecks(StructuredGraph graph, EconomicMap<Node, List<ScopedAccess>> sugaredGraph, ControlFlowGraph cfg, EconomicSet<DominatedCall> calls) {
            this.graph = graph;
            this.sugaredGraph = sugaredGraph;
            this.cfg = cfg;
            this.calls = calls;
        }

        @Override
        protected NodeBitMap processBlock(HIRBlock block, NodeBitMap currentState) {
            NodeBitMap effectiveCurrentState = currentState;
            for (FixedNode f : block.getNodes()) {
                List<ScopedAccess> accesses = sugaredGraph.get(f);
                if (accesses == null) {
                    continue;
                }

                // check for safepoints first
                for (ScopedAccess access : accesses) {
                    if (access instanceof ScopedSafepoint se) {
                        /*
                         * Note: we mark the arena here and not the session. Some nodes ref the
                         * session other the arena or offsets, we group all under the arena node and
                         * use that one as a "must check" pattern.
                         */
                        if (effectiveCurrentState == null) {
                            effectiveCurrentState = graph.createNodeBitMap();
                        }
                        effectiveCurrentState.mark(se.arenaNode);
                    }
                }

                // check for safepoints first
                for (ScopedAccess access : accesses) {
                    if (access instanceof ScopedSafepoint) {
                        continue;
                    }
                    ScopedMemoryAccess sma = (ScopedMemoryAccess) access;
                    if (effectiveCurrentState == null) {
                        effectiveCurrentState = graph.createNodeBitMap();
                    } else if (effectiveCurrentState.isMarked(sma.scope.defNode)) {
                        // check and then clear
                        effectiveCurrentState.clear(sma.scope.defNode);
                        duplicateCheckValidStateRaw(graph, sma.scope, (FixedNode) f.predecessor(), calls);
                        GraalError.guarantee(cfg.blockFor(sma.scope.defNode).dominates(cfg.blockFor(f)), "%s must dominate %s", sma.scope.defNode, f);
                        scheduleVerify(graph);
                    }
                }

            }
            return effectiveCurrentState;
        }

        @Override
        protected NodeBitMap merge(HIRBlock merge, List<NodeBitMap> states) {
            /*
             * If any of the predecessors indicated a safepoint for the given node we must assume
             * path could have yielded a safepoint, thus issue a safepoint state.
             */
            NodeBitMap resultMap = null;
            for (NodeBitMap other : states) {
                if (other == null) {
                    continue;
                }
                if (resultMap == null) {
                    // first time we actually found a non-null entry
                    resultMap = other.copy();
                    continue;
                }
                resultMap.markAll(other);
            }
            return resultMap;
        }

        @Override
        protected NodeBitMap getInitialState() {
            // avoid eagerly returning a map that is empty most of the time
            return null;
        }

        @Override
        protected NodeBitMap cloneState(NodeBitMap oldState) {
            return oldState == null ? null : oldState.copy();
        }
    }

    /**
     * Cleanup all remaining {@link ClusterNode} in the graph. After session check expansion they
     * are not needed any more.
     */
    private void cleanupClusterNodes(StructuredGraph graph, MidTierContext context, EconomicSet<DominatedCall> calls) {
        for (MemoryArenaValidInScopeNode scopeNode : graph.getNodes().filter(MemoryArenaValidInScopeNode.class).snapshot()) {
            scopeNode.delete(0);
        }
        // also cleanup the original cluster nodes
        for (Node n : graph.getNodes()) {
            if (n instanceof ClusterNode clusterNode) {
                clusterNode.delete();
            }
        }
        canonicalizer.apply(graph, context);
        scheduleVerify(graph);

        if (VERIFY_NO_DOMINATED_CALLS) {
            if (calls != null) {
                for (DominatedCall call : calls) {
                    if (call.invoke.isAlive()) {
                        throw GraalError.shouldNotReachHere("After inserting all session checks call " + call.invoke + " was not inlined and could access a session");
                    }
                }
            }
        }

    }

    /**
     * Definition of an access to a value representing a memory session.
     */
    static class ScopedAccess {
        protected final ValueNode session;

        ScopedAccess(ValueNode session) {
            this.session = session;
        }
    }

    /**
     * A safepoint call in a region that is dominated by a scoped memory access call. All such
     * safepoints can close shared arenas.
     */
    static class ScopedSafepoint extends ScopedAccess {
        final MemoryArenaValidInScopeNode arenaNode;

        ScopedSafepoint(ValueNode session, MemoryArenaValidInScopeNode arenaNode) {
            super(session);
            this.arenaNode = arenaNode;
        }

    }

    /**
     * A scoped memory access in a region that is dominated by a scoped memory access call. All such
     * accesses have to check their session(s) if a safepoint potentially happened in between.
     */
    static class ScopedMemoryAccess extends ScopedAccess {

        final ReachingDefScope scope;

        ScopedMemoryAccess(ValueNode session, ReachingDefScope scope) {
            super(session);
            this.scope = scope;
        }
    }

    /**
     * The "scope" opened by a scoped memory access in which the associated memory session needs to
     * remain open else an exception has to be thrown.
     */
    private static class ReachingDefScope {
        private final MemoryArenaValidInScopeNode defNode;

        ReachingDefScope(MemoryArenaValidInScopeNode defNode) {
            this.defNode = defNode;
        }

        private SessionStateRawGraphCluster defCluster;

        private boolean isPartOfCluster(Node n) {
            if (defCluster == null) {
                defCluster = SessionStateRawGraphCluster.build(defNode);
            }
            if (defCluster == null) {
                // Note that it's possible this cluster may not be constructible. In such cases,
                // aborting is necessary. This scenario occurs when we've already determined that
                // session access checks are unnecessary due to certain conditions, such as the
                // absence of concurrent threads or the lack of arena.close() invocations.
                return false;
            }
            return defCluster.clusterNodes.contains(n);
        }
    }

    record DominatedCall(MemoryArenaValidInScopeNode defNode, Invoke invoke) {

    }

    private static EconomicMap<Node, List<ScopedAccess>> enumerateScopedAccesses(OptimizeSharedArenaConfig config, ControlFlowGraph cfg, MidTierContext context,
                    EconomicSet<DominatedCall> dominatedCalls) {
        EconomicMap<Node, List<ScopedAccess>> nodeAccesses = EconomicMap.create();
        final ResolvedJavaType memorySessionType = context.getMetaAccess().lookupJavaType(MemorySessionImpl.class);
        assert memorySessionType != null;

        ControlFlowGraph.RecursiveVisitor<Integer> visitor = new RecursiveVisitor<>() {
            final Deque<ReachingDefScope> defs = new ArrayDeque<>();
            final Deque<ScopedMethodNode> scopes = new ArrayDeque<>();
            final Deque<Runnable> actions = new ArrayDeque<>();

            @Override
            public Integer enter(HIRBlock b) {
                int newDominatingValues = 0;
                int newScopesToPop = 0;
                Deque<ScopedMethodNode> scopesToRepush = new ArrayDeque<>();

                for (FixedNode f : b.getNodes()) {
                    if (f instanceof MemoryArenaValidInScopeNode mas) {
                        defs.push(new ReachingDefScope(mas));
                        newDominatingValues++;
                    } else if (f instanceof ScopedMethodNode scope) {
                        if (scope.getType() == ScopedMethodNode.Type.START) {
                            scopes.push(scope);
                            newScopesToPop++;
                        } else if (scope.getType() == ScopedMethodNode.Type.END) {
                            ScopedMethodNode start = scopes.pop();
                            scopesToRepush.push(start);
                            assert scope.getStart() == start : Assertions.errorMessage("Must match", start, scope, scope.getStart());
                        } else {
                            throw GraalError.shouldNotReachHere("Unknown type " + scope.getType());
                        }
                    } else {
                        processNode(f);
                    }
                }

                final int finalNewDominatingValues = newDominatingValues;
                final int finalNewScopesToPop = newScopesToPop;

                actions.push(new Runnable() {

                    @Override
                    public void run() {
                        // remove all the dominated values
                        for (int i = 0; i < finalNewDominatingValues; i++) {
                            defs.pop();
                        }
                        for (int i = 0; i < finalNewScopesToPop; i++) {
                            scopes.pop();
                        }
                        for (int i = 0; i < scopesToRepush.size(); i++) {
                            scopes.push(scopesToRepush.pop());
                        }
                    }
                });

                return 1;
            }

            private void processNode(FixedNode f) {
                if (!scopes.isEmpty() && f instanceof Invoke i) {
                    if (i.getTargetMethod() != null && !config.isSafeCallee(i.getTargetMethod())) {
                        if (!defs.isEmpty()) {
                            dominatedCalls.add(new DominatedCall(defs.peek().defNode, i));
                        }
                    }
                }
                if (f instanceof ClusterNode) {
                    return;
                }
                for (ReachingDefScope existingDef : defs) {
                    if (existingDef.isPartOfCluster(f)) {
                        return;
                    }
                }
                if (f instanceof SafepointNode safepoint) {
                    for (ReachingDefScope existingDef : defs) {
                        for (Node scopeAssociatedVal : existingDef.defNode.inputs()) {
                            EconomicSet<CFGLoop<HIRBlock>> allLoopsToCheck = visitEveryLoopHeaderInBetween(existingDef, safepoint);
                            if (allLoopsToCheck != null) {
                                for (CFGLoop<HIRBlock> loop : allLoopsToCheck) {
                                    // Mark the loop header as well as safepointing
                                    LoopBeginNode lb = (LoopBeginNode) loop.getHeader().getBeginNode();
                                    cacheSafepointPosition(existingDef, (ValueNode) scopeAssociatedVal, lb);

                                }
                            }

                            // also check when we are actually doing the safepoint
                            cacheSafepointPosition(existingDef, (ValueNode) scopeAssociatedVal, f);

                        }
                    }
                } else if (f instanceof MemoryAccess) {
                    // only care about memory access nodes
                    for (ReachingDefScope existingDef : defs) {
                        scopedVal: for (Node scopeAssociatedVal : existingDef.defNode.inputs()) {
                            for (Node input : f.inputs()) {
                                if (visitInputsUntil(scopeAssociatedVal, input)) {
                                    cacheAccessPosition(existingDef, (ValueNode) scopeAssociatedVal, f);
                                    continue scopedVal;
                                }
                            }
                        }
                    }
                }
            }

            private void cacheAccessPosition(ReachingDefScope existingDef, ValueNode scopeAssociatedVal, FixedNode f) {
                List<ScopedAccess> existingAccesses = nodeAccesses.get(f);
                if (existingAccesses == null) {
                    existingAccesses = new ArrayList<>();
                }
                existingAccesses.add(new ScopedMemoryAccess(scopeAssociatedVal, existingDef));
                nodeAccesses.put(f, existingAccesses);
            }

            private void cacheSafepointPosition(ReachingDefScope existingDef, ValueNode scopeAssociatedVal, FixedNode f) {
                List<ScopedAccess> existingAccesses = nodeAccesses.get(f);
                if (existingAccesses == null) {
                    existingAccesses = new ArrayList<>();
                    nodeAccesses.put(f, existingAccesses);
                }
                existingAccesses.add(new ScopedSafepoint(scopeAssociatedVal, existingDef.defNode));
            }

            private static boolean visitInputsUntil(Node key, Node start) {
                NodeStack toProcess = new NodeStack();
                toProcess.push(start);
                NodeBitMap visited = start.graph().createNodeBitMap();
                while (!toProcess.isEmpty()) {
                    Node cur = toProcess.pop();
                    if (visited.isMarked(cur)) {
                        continue;
                    }
                    visited.mark(cur);
                    // fixed nodes are checked themselves
                    if (cur instanceof FixedNode) {
                        continue;
                    }
                    if (cur == key) {
                        return true;
                    }
                    for (Node input : cur.inputs()) {
                        toProcess.push(input);
                    }
                }
                return false;
            }

            private EconomicSet<CFGLoop<HIRBlock>> visitEveryLoopHeaderInBetween(ReachingDefScope def, FixedNode usage) {
                FixedNode cur = (FixedNode) usage.predecessor();
                NodeBitMap processedNodes = cfg.graph.createNodeBitMap();
                EconomicSet<CFGLoop<HIRBlock>> loopsToCheck = null;
                outer: while (cur != def.defNode && cur != null) {
                    HIRBlock currentBlock = cfg.blockFor(cur);
                    for (FixedNode f : GraphUtil.predecessorIterable(cur)) {
                        if (processedNodes.isMarked(f)) {
                            continue;
                        }
                        /*
                         * We are processing a loop: if usage is inside a loop or a path that goes
                         * over the body of a loop, any path in that loop might cause a state node
                         * to be executed that references the scoped session, thus, if we go over a
                         * loop, ensure to process all nodes of that loop body as well.
                         */
                        CFGLoop<HIRBlock> cfgLoop = cfg.blockFor(f).getLoop();
                        if (cfgLoop != null) {
                            if (loopsToCheck == null) {
                                loopsToCheck = EconomicSet.create();
                            }
                            loopsToCheck.add(cfgLoop);
                        }
                        if (f == def.defNode) {
                            break outer;
                        }
                        if (f == currentBlock.getBeginNode()) {
                            // go to the next one
                            cur = currentBlock.getDominator().getEndNode();
                            continue outer;
                        }
                        // only mark non block boundary nodes to avoid redoing any block boundary
                        // iterations
                        processedNodes.mark(f);
                    }
                }
                return loopsToCheck;
            }

            @Override
            public void exit(HIRBlock b, Integer pushedForBlock) {
                for (int i = 0; i < pushedForBlock; i++) {
                    actions.pop().run();
                }
            }

        };

        cfg.visitDominatorTreeDefault(visitor);
        return nodeAccesses.size() > 0 ? nodeAccesses : null;
    }

    private static void duplicateCheckValidStateRaw(StructuredGraph graph, ReachingDefScope def, FixedNode fixedUsage, EconomicSet<DominatedCall> calls) {
        SessionStateRawGraphCluster cluster = SessionStateRawGraphCluster.build(def.defNode);

        if (cluster == null) {
            // do nothing, the session is probably null, all good
            graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "Aborting %s with usage %s because cluster is null", def, fixedUsage);
            return;
        }

        Iterator<DominatedCall> it = calls.iterator();
        while (it.hasNext()) {
            if (cluster.clusterNodes.contains(it.next().invoke.asNode())) {
                /*
                 * Normally we do not allow any non-inlined invokes in scope annotated methods
                 * because we cannot guarantee then that a session is not accessed in the callee.
                 * For the calls however in the exception handling portion of a failed
                 * isSessionValid check we are only calling things we control and those callees are
                 * safe to not inline.
                 */
                it.remove();
            }

        }

        if (cluster.clusterNodes.isMarked(fixedUsage)) {
            // naturally the cluster references itself
            return;
        }

        if (DUMP_LARGE_NODE_SETS) {
            graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before duplicating cluster at %s %s with cluster nodes %s", def.defNode, fixedUsage, cluster.clusterNodes);
        }
        EconomicMap<Node, Node> duplicates = graph.addDuplicates(cluster.clusterNodes, graph, cluster.clusterNodes.count(), null, false);
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After duplicating cluster at %s %s", def.defNode, fixedUsage);

        if (LOG_SHARED_ARENAS) {
            TTY.printf("Duplication map %s%n", duplicates);
        }

        FixedWithNextNode exceptionPath = cluster.clusterExceptionEnd;
        FixedNode exceptionPathNext = exceptionPath.next();
        MergeNode mergeException = graph.add(new MergeNode());
        EndNode exceptionPathNewEnd = graph.add(new EndNode());
        EndNode exceptionPathOldEnd = graph.add(new EndNode());

        ExceptionPathNode epn = cluster.clusterExceptionEnd;
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before creating new exception phi for old val %s", epn);

        exceptionPath.setNext(null);
        exceptionPath.setNext(exceptionPathOldEnd);
        mergeException.addForwardEnd(exceptionPathOldEnd);
        mergeException.addForwardEnd(exceptionPathNewEnd);
        mergeException.setNext(exceptionPathNext);

        ((FixedWithNextNode) duplicates.get(cluster.clusterExceptionEnd)).setNext(exceptionPathNewEnd);
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After merging exception CF path in %s", mergeException);

        // now add the entire section into the control flow before the actual node with state
        assert fixedUsage instanceof FixedWithNextNode : Assertions.errorMessage("Must be a fixed node we can hang a check into", fixedUsage);

        FixedWithNextNode insertPoint = (FixedWithNextNode) fixedUsage.asFixedNode();
        FixedNode insertPointNext = insertPoint.next();
        insertPoint.setNext(null);
        FixedNode duplicateClusterEntry = (FixedNode) duplicates.get(cluster.firstClusterNode);
        insertPoint.setNext(duplicateClusterEntry);
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After hanging check into regular CF %s", insertPoint);
        Node nonExceptionEndDuplicate = duplicates.get(cluster.clusterNonExceptionEnd);
        ((FixedWithNextNode) nonExceptionEndDuplicate).setNext(insertPointNext);
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After going back into after check for regular cf %s", insertPointNext);

        EconomicMap<PhiKey, PhiNode> createdPhis = EconomicMap.create(Equivalence.DEFAULT);
        for (Node clusterNode : cluster.clusterNodes) {
            List<Node> clusterNodeUsages = clusterNode.usages().snapshot();
            graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "Processing cluster node %s with usages %s", clusterNode, clusterNodeUsages);
            for (Node usage : clusterNodeUsages) {
                if (!cluster.clusterNodes.isMarked(usage)) {
                    for (Position p : usage.inputPositions()) {
                        Node input = p.get(usage);
                        if (input == clusterNode) {
                            PhiKey key = new PhiKey(input, p.getInputType());
                            PhiNode phi = useOrCreatePhi(graph, input, p, createdPhis, cluster.clusterNodes, mergeException, key, usage, duplicates);
                            graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "Usage %s of %s outside of cluster", usage, input);
                            graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "Before setting input %s of %s to %s", input, usage, phi);
                            p.set(usage, phi);
                            graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "Setting input %s of %s to %s", input, usage, phi);
                        }
                    }
                }
            }
        }
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After creating phis at %s", mergeException);

        // delete all cluster nodes to ensure the Arena node has only the expected usages
        for (Node n : duplicates.getValues()) {
            if (n instanceof ClusterNode c) {
                c.delete();
            }
        }
        graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "After deleting leftover clusters");
    }

    private static PhiNode useOrCreatePhi(StructuredGraph graph, Node input, Position p, EconomicMap<PhiKey, PhiNode> createdPhis, NodeBitMap clusterNodes, MergeNode merge, PhiKey key, Node usage,
                    EconomicMap<Node, Node> duplicates) {
        PhiNode phi = createdPhis.get(key);
        if (phi == null) {
            phi = switch (p.getInputType()) {
                case Value ->
                    graph.addWithoutUnique(new ValuePhiNode(((ValueNode) input).stamp(NodeView.DEFAULT).unrestricted(), merge));
                case Memory -> graph.addWithoutUnique(new MemoryPhiNode(merge, getLocationIdentity(input)));
                case Guard -> graph.addWithoutUnique(new GuardPhiNode(merge));
                default -> throw GraalError.shouldNotReachHere(
                                String.format("Unexpected edge type %s from %s to %s, [cluster nodes %s]", p.getInputType(), input, usage, clusterNodes)); // ExcludeFromJacocoGeneratedReport
            };
            createdPhis.put(key, phi);
            phi.addInput((ValueNode) input);
            phi.addInput((ValueNode) duplicates.get(input));
            graph.getDebug().dump(VERY_DETAILED_LEVEL, graph, "Created phi %s for original cluster node %s", phi, input);
        }
        return phi;
    }

    private static LocationIdentity getLocationIdentity(Node node) {
        if (node instanceof MemoryPhiNode) {
            return ((MemoryPhiNode) node).getLocationIdentity();
        } else if (node instanceof MemoryAccess) {
            return ((MemoryAccess) node).getLocationIdentity();
        } else if (MemoryKill.isSingleMemoryKill(node)) {
            return ((SingleMemoryKill) node).getKilledLocationIdentity();
        } else if (MemoryKill.isMultiMemoryKill(node)) {
            return LocationIdentity.any();
        } else {
            throw GraalError.shouldNotReachHere("unexpected node as part of memory graph: " + node); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static class PhiKey {
        Node input;
        InputType type;

        PhiKey(Node input, InputType type) {
            super();
            this.input = input;
            this.type = type;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PhiKey) {
                return input == ((PhiKey) obj).input && type == ((PhiKey) obj).type;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return input.hashCode() * type.hashCode();
        }
    }

}
