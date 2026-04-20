/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package jdk.graal.compiler.phases.dfanalysis;

import static jdk.graal.compiler.phases.dfanalysis.DFAnalysis.isLastNodeInBlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.iterators.NodePredicate;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.MultiGuardNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;

/**
 * <p>
 * Implementation of a priority based work list. This work list consists of 2 queues. Elements are
 * preferably taken from the first queue. Only if the first queue is empty, the element with the
 * lowest priority from the second queue is taken. The work list is considered empty if both queues
 * are empty.
 * </p>
 * <p>
 * The first queue, called the value queue, is a standard queue without priority used for normal
 * value flow nodes, meaning floating value nodes without an easy connection to the CFG. Elements
 * are preferably taken from this queue.
 * </p>
 * <p>
 * The second queue is a priority queue used for nodes associated with control flow (like control
 * split nodes, phi nodes, ...). The priority for elements in this queue ensures that all possibly
 * available information regarding reachability and input values is calculated before evaluating
 * phis.
 * </p>
 * <p>
 * Calculating the priorities used in this queue is done via the following steps:<br>
 * Each CFG block has a "base priority" value n (calculated using
 * {@link WorkList#calculatePriorities}). Nodes closely related to control flow, like fixed nodes or
 * phis, within each block are further associated with one of five priority classes within the block
 * by scaling and offsetting the base priority. Nodes within a class have no ordering relative to
 * each other.
 * </p>
 *
 * <pre>
 * Priority layout:
 *                  |             base priority class n              |
 * ... | reschedule | inferred | begin | body | control | reschedule | inferred | ...
 *                  |   5n+0   | 5n+1  | 5n+2 |  5n+3   |    5n+4    |
 * </pre>
 *
 * <p>
 * Each base priority n is scaled to fit 5 classes. The first class is used to ensure that inserted
 * inferences into branches are processed before anything in the respective branch. The second class
 * is used for BeginNodes or nodes related to BeginNodes like PhiNodes and ValueProxyNodes. The
 * third class is used for nodes in the middle of a block while the fourth class is used for Nodes
 * involved in control flow calculation like ControlSplitNodes. Also, AbstractBeginNodes are
 * scheduled in this class because they are used to denote control flow blocks to advance
 * reachability analysis after a block has been fully evaluated. This ordering ensures that values
 * produced by nodes in the given block are available when evaluating the control split at the end
 * of a block. The fifth class is used for rescheduled loop phis. Rescheduled phis are placed in the
 * base priority class of the deepest associated loop end, therefore requiring their own subclass to
 * ensure they are processed after the entire loop.
 * </p>
 */
class WorkList {
    private enum PriorityClass {
        INFERRED,
        BEGIN,
        BODY,
        CONTROL,
        RESCHEDULE
    }

    private record PriorityElement(ValueNode node, long priority) {
    }

    private static final long PRIORITY_SCALING_FACTOR = PriorityClass.values().length;

    /**
     * <p>
     * Calculate base priorities for all CFG blocks. This is done by calculating the minimum visit
     * depth for each CFG block in a reverse postorder traversal.
     * </p>
     * <p>
     * Each block is assigned a base priority that is 1 larger than the largest base priority of all
     * of its predecessors not including back-edges of loops. Additionally, loop exit blocks are
     * assigned a priority 1 larger than the maximum priority of its predecessor and all loop ends
     * in the loop that this block is exiting. This is to ensure that any block depending on the
     * result of a loop is evaluated after evaluation of the loop has finished.
     * </p>
     * <p>
     * To traverse the graph in the order of execution flow, the numeric value of the base priority
     * metric is later interpreted as a penalty on priority, meaning elements with lower numeric
     * priority value get evaluated first.
     * </p>
     *
     * @return a map of block-ids to their calculated priorities in the form of an int[].
     */
    private static int[] calculatePriorities(ControlFlowGraph cfg) {
        int[] priorityMap = new int[cfg.getBlocks().length];
        Arrays.fill(priorityMap, -1);
        Queue<HIRBlock> workList = new ArrayDeque<>();

        // initialize
        HIRBlock startBlock = cfg.getStartBlock();
        priorityMap[startBlock.getId()] = 0;
        for (int i = 0; i < startBlock.getSuccessorCount(); i++) {
            workList.add(startBlock.getSuccessorAt(i));
        }

        workLoop: while (!workList.isEmpty()) {
            CompilationAlarm.checkProgress(cfg.graph);
            HIRBlock block = workList.remove();
            if (priorityMap[block.getId()] >= 0) {
                // we already have a priority for this block
                continue;
            }

            int prio = -1;
            for (int i = 0; i < block.getPredecessorCount(); i++) {
                HIRBlock predecessor = block.getPredecessorAt(i);
                if (predecessor.isLoopEnd()) {
                    // ignore back edges in priority calculation
                    continue;
                }
                int predPrio = priorityMap[block.getPredecessorAt(i).getId()];
                if (predPrio < 0) {
                    // this block will come up later
                    continue workLoop;
                }
                prio = Math.max(prio, predPrio);
            }
            if (block.getBeginNode() instanceof LoopExitNode exit) {
                for (LoopEndNode end : exit.loopBegin().loopEnds()) {
                    int endPrio = priorityMap[cfg.blockFor(end).getId()];
                    if (endPrio < 0) {
                        // if we hit a loop end that has not been evaluated yet, we try again
                        // later
                        continue workLoop;
                    }
                    prio = Math.max(prio, endPrio);
                }
            }
            prio++;
            priorityMap[block.getId()] = prio;

            for (int i = 0; i < block.getSuccessorCount(); i++) {
                workList.add(block.getSuccessorAt(i));
            }

            if (block.getEndNode() instanceof LoopEndNode end) {
                for (LoopExitNode exit : end.loopBegin().loopExits()) {
                    workList.add(cfg.blockFor(exit));
                }
            }
        }

        if (Assertions.detailedAssertionsEnabled(cfg.graph.getOptions())) {
            for (HIRBlock block : cfg.getBlocks()) {
                GraalError.guarantee(priorityMap[block.getId()] >= 0, "Base priorities must be non-negative");
                int thisPriority = priorityMap[block.getId()];
                // the priority of the block must be larger than its non-back-edge parents
                for (int i = 0; i < block.getPredecessorCount(); i++) {
                    if (block.getPredecessorAt(i).getEndNode() instanceof LoopEndNode loopEnd && loopEnd.loopBegin() == block.getBeginNode()) {
                        // this is a back-edge, no check required here
                    } else {
                        // this is not a back-edge we do check priority calculation results here
                        GraalError.guarantee(thisPriority > priorityMap[block.getPredecessorAt(i).getId()],
                                        "The priority of a block must always be higher than all of its non-back-edge parents");
                    }
                }
                if (block.getBeginNode() instanceof LoopExitNode exit) {
                    /*
                     * Additionally, since this block is a loop exit, this block should be pulled
                     * below the entire loop (i.e. this block should have a higher base priority
                     * value than every loop end in the associated loop).
                     */
                    for (LoopEndNode end : exit.loopBegin().loopEnds()) {
                        GraalError.guarantee(thisPriority > priorityMap[cfg.blockFor(end).getId()], "Loop exits must be below the entire loop");
                    }
                }
            }
        }

        return priorityMap;
    }

    /** Map of CFG-blocks to base priorities. Indexed by block-ids. */
    private final int[] priorities;
    /** Set of scheduled nodes to prevent double scheduling. */
    private final EconomicSet<ValueNode> scheduled;
    /** Set of phis for which we currently assume their back edges to be unreachable. */
    private final EconomicSet<AbstractMergeNode> optimisticLoopBegins;
    private final Queue<ValueNode> valueQueue;
    private final PriorityQueue<PriorityElement> prioQueue;
    private final ControlFlowGraph cfg;
    private final DebugContext debug;

    final Map<ValueNode, Set<ValueNode>> patternInputs;

    WorkList(ControlFlowGraph cfg, DebugContext debug) {
        this.priorities = calculatePriorities(cfg);
        this.scheduled = EconomicSet.create();
        this.optimisticLoopBegins = EconomicSet.create();
        this.valueQueue = new ArrayDeque<>();
        this.prioQueue = new PriorityQueue<>(Comparator.comparingLong(PriorityElement::priority));
        this.cfg = cfg;
        this.debug = debug;
        patternInputs = new HashMap<>();
    }

    public void initialize(StructuredGraph graph, NodePredicate isStartingPoint) {
        for (Node nd : graph.getNodes()) {
            if (nd instanceof ValueNode vn) {
                if (isStartingPoint.test(vn)) {
                    schedule(vn);
                }
            }
        }
    }

    public boolean hasNext() {
        return !scheduled.isEmpty();
    }

    public ValueNode next() {
        ValueNode nextNode;
        if (!valueQueue.isEmpty()) {
            nextNode = valueQueue.remove();
        } else if (!prioQueue.isEmpty()) {
            nextNode = prioQueue.remove().node();
        } else {
            throw GraalError.shouldNotReachHere("Trying to retrieve element from empty work list");
        }
        scheduled.remove(nextNode);
        if (nextNode instanceof LoopBeginNode loopBegin) {
            optimisticLoopBegins.remove(loopBegin);
        }
        return nextNode;
    }

    public void scheduleUsages(ValueNode node) {
        boolean watchOutForMemory = MemoryKill.isMemoryKill(node);
        for (Node u : node.usages()) {
            if (u instanceof ValueNode value) {
                if (watchOutForMemory && DFAnalysis.isMemoryUsage(node, value)) {
                    // this node can have memory usages which we can not reason about
                    continue;
                }
                schedule(value);
            }
        }
    }

    public void schedule(HIRBlock block) {
        // blocks are denoted by their last block
        schedule(block.getEndNode());
    }

    public void schedule(ValueNode node) {
        if (node instanceof ValuePhiNode phi) {
            /*
             * We do not schedule PHIs directly, we rather schedule their associated MERGE node to
             * allow for full evaluation of all PHIs at the given merge at the same time.
             */
            schedule(phi.merge());
            return;
        }
        boolean newlyScheduled = scheduled.add(node);
        if (patternInputs.containsKey(node)) {
            // if this is a pattern input, we also need to schedule the pattern output
            for (ValueNode pOut : patternInputs.get(node)) {
                if (!scheduled.contains(pOut)) {
                    schedule(pOut);
                }
            }
        }
        if (!newlyScheduled) {
            // if the given node was already scheduled, do nothing
            return;
        }

        long prio = switch (node) {
            case InferredFactNode<?> fact -> blockToPrio(fact.getGuard(), PriorityClass.INFERRED);
            case AbstractMergeNode merge -> blockToPrio(merge, PriorityClass.BEGIN);
            case ValueProxyNode proxy -> blockToPrio(proxy.proxyPoint(), PriorityClass.BEGIN);
            case ControlSplitNode split -> blockToPrio(split, PriorityClass.CONTROL);
            case FixedNode fixed -> isLastNodeInBlock(cfg, fixed)
                            /*
                             * The last node in a block is scheduled to propagate reachability to
                             * the control flow edges flowing out of the block. Normally we schedule
                             * FixedNodes in BODY, but if we slightly delay (to CONTROL) nodes that
                             * would normally be scheduled in BODY, it does not matter as long as
                             * its value flow part is evaluated before propagating control flow.
                             */
                            ? blockToPrio(fixed, PriorityClass.CONTROL)
                            /*
                             * To enforce some sort of top-down order for efficiency, we schedule
                             * fixed nodes with priority, even if we could just treat them like
                             * floating nodes.
                             */
                            : blockToPrio(fixed, PriorityClass.BODY);
            case PiNode pi -> {
                /*
                 * For pis, we try to find a connection to the cfg. If we fail to do so, we simply
                 * fall back to the default priority for floating nodes, namely -1.
                 */
                GuardingNode anchor = pi.getGuard();
                if (anchor instanceof FixedNode fn) {
                    yield blockToPrio(fn, PriorityClass.BEGIN);
                } else if (anchor instanceof MultiGuardNode mg) {
                    long deepest = -1;
                    for (ValueNode b : mg.getGuards()) {
                        if (b instanceof FixedNode fn) {
                            deepest = Math.max(deepest, blockToPrio(fn, PriorityClass.BEGIN));
                        } else {
                            // If we have non-fixed nodes, we default to standard -1 priority
                            yield -1;
                        }
                    }
                    yield deepest;
                } else if (anchor instanceof GuardNode gn) {
                    yield gn.getAnchor() instanceof FixedNode fn ? blockToPrio(fn, PriorityClass.BEGIN) : -1;
                } else {
                    yield -1;
                }
            }
            /*
             * This is a floating node without an easy connection into the CFG. We schedule this
             * node without an explicit priority.
             */
            default -> -1;
        };
        if (prio < 0) {
            debug.log(DebugContext.VERY_DETAILED_LEVEL, " scheduling %s without priority", node);
            valueQueue.add(node);
        } else {
            debug.log(DebugContext.VERY_DETAILED_LEVEL, " scheduling %s with priority %s", node, prio);
            prioQueue.add(new PriorityElement(node, prio));
        }
    }

    public void unschedule(ArrayList<ValueNode> toUnschedule) {
        if (toUnschedule.isEmpty()) {
            return;
        }
        EconomicSet<ValueNode> toDo = EconomicSet.create(toUnschedule.size());
        boolean needsValueRemoval = false;
        boolean needsPrioRemoval = false;
        for (ValueNode n : toUnschedule) {
            if (scheduled.contains(n)) {
                toDo.add(n);
                scheduled.remove(n);
                if (!needsPrioRemoval && !needsValueRemoval) {
                    switch (n) {
                        // nodes scheduled with priority
                        case InferredFactNode<?> ignored -> needsPrioRemoval = true;
                        case ValuePhiNode ignored -> needsPrioRemoval = true;
                        case ValueProxyNode ignored -> needsPrioRemoval = true;
                        case AbstractBeginNode ignored -> needsPrioRemoval = true;
                        case ControlSplitNode ignored -> needsPrioRemoval = true;
                        case FixedNode ignored -> needsPrioRemoval = true;
                        // nodes scheduled without priority
                        default -> needsValueRemoval = true;
                    }
                }
            }
        }
        if (needsValueRemoval) {
            valueQueue.removeIf(n -> toDo.contains(n));
        }
        if (needsPrioRemoval) {
            prioQueue.removeIf(n -> toDo.contains(n.node));
        }
    }

    /**
     * This method schedules the given LoopBegin after its entire associated loop. It is used to
     * check an initial optimistic assumption for the associated phis after the associated parts of
     * the loop are evaluated once, as well as to block reevaluation of the LoopBegin after updating
     * until all associated parts of the loop had a chance to be reevaluated.
     *
     * @param begin the LoopBegin in question.
     * @param optimistic denotes if this LoopBegin has just been evaluated optimistically.
     */
    public void rescheduleLoopBegin(LoopBeginNode begin, boolean optimistic) {
        if (scheduled.add(begin)) {
            if (optimistic) {
                optimisticLoopBegins.add(begin);
            }
            /*
             * For rescheduling a LoopBegin, we use the base priority class of its deepest end to
             * ensure the entire loop is processed before reevaluating the LoopBegin. Additionally,
             * for priority calculation for a LoopBegin to be reevaluated, the RESCHEDULE class is
             * used.
             */
            long prio = blockToPrio(getDeepestLoopEnd(begin), PriorityClass.RESCHEDULE);
            debug.log(DebugContext.VERY_DETAILED_LEVEL, " rescheduling %s with priority %s", begin, prio);
            prioQueue.add(new PriorityElement(begin, prio));
        }
    }

    private FixedNode getDeepestLoopEnd(LoopBeginNode begin) {
        int priority = -1;
        FixedNode deepest = begin;
        for (LoopEndNode end : begin.loopEnds()) {
            int endPrio = priorities[cfg.blockFor(end).getId()];
            if (priority < endPrio) {
                priority = endPrio;
                deepest = end;
            }
        }
        return deepest;
    }

    public boolean isOptimisticLoopPhi(ValueNode value) {
        return value instanceof ValuePhiNode phi && optimisticLoopBegins.contains(phi.merge());
    }

    private long blockToPrio(Node nodeInBlock, PriorityClass prioClass) {
        // inferred (+0) | begin (+1) | body (+2) | split (+3) | reschedule (+4)
        return priorities[cfg.blockFor(nodeInBlock).getId()] * PRIORITY_SCALING_FACTOR + prioClass.ordinal();
    }

    /**
     * Generates a pretty multiline string representation of this work list for debugging purposes.
     */
    @SuppressWarnings({"unused", "deprecation"})
    public String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append("WorkList: {");
        sb.append("\n    prios: {");
        ArrayList<String> elems = new ArrayList<>();
        HIRBlock[] blocks = Arrays.copyOf(cfg.getBlocks(), cfg.getBlocks().length);
        Arrays.sort(blocks, Comparator.comparingInt(b -> b.getId()));
        for (int i = 0; i < priorities.length; i++) {
            elems.add(String.format("        %s := %s", DFEdgeMap.blockPrettyString(blocks[i]), priorities[blocks[i].getId()]));
        }
        if (!elems.isEmpty()) {
            sb.append("\n").append(String.join(",\n", elems)).append("\n    ");
        } else {
            sb.append(' ');
        }
        sb.append("}");

        sb.append("\n    elems: {");
        elems.clear();
        for (ValueNode n : valueQueue) {
            elems.add(String.format("        [ -1] %s", n));
        }
        TreeSet<PriorityElement> tpq = new TreeSet<>(Comparator.comparingLong(PriorityElement::priority));
        tpq.addAll(prioQueue);
        for (PriorityElement p : tpq) {
            elems.add(String.format("        [%s%s] %s", p.node() instanceof ValuePhiNode phi && optimisticLoopBegins.contains(phi.merge()) ? "r" : " ", p.priority(), p.node()));
        }
        if (!elems.isEmpty()) {
            sb.append('\n').append(String.join(",\n", elems)).append("\n    ");
        } else {
            sb.append(' ');
        }
        sb.append("}\n    patternMap: {");
        if (patternInputs.isEmpty()) {
            sb.append(' ');
        } else {
            ArrayList<ValueNode> uppers = new ArrayList<>(patternInputs.keySet());
            uppers.sort(Comparator.comparingInt(n -> n.getId()));
            boolean isFirst = true;
            for (ValueNode upper : uppers) {
                ArrayList<ValueNode> results = new ArrayList<>(patternInputs.get(upper));
                results.sort(Comparator.comparingInt(n -> n.getId()));
                if (isFirst) {
                    isFirst = false;
                } else {
                    sb.append(',');
                }
                sb.append("\n        ").append(upper.toString()).append(" -> ").append(results);
            }
            sb.append("\n    ");
        }
        sb.append("}\n}");
        return sb.toString();
    }
}
