/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package jdk.graal.compiler.phases.dfanalysis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.phases.dfanalysis.DFEdgeMap.CFGEdge;
import jdk.graal.compiler.phases.dfanalysis.DFEdgeMap.Reachability;
import jdk.vm.ci.meta.TriState;

/**
 * This class is a wrapper around a {@code Map<ValueNode, LE_TYPE>} providing some extra
 * functionality for convenience.
 * 
 * @param <LE_TYPE>
 */
public final class DFAMap<LE_TYPE> {
    private record ValueUpdate<T>(int action, ValueNode node, T elem, Object[] reason) {
        @Override
        public String toString() {
            return "[node=" + node + ", elem=" + elem + ']';
        }

        public static String getAction(int act) {
            return switch (act) {
                case 0 -> "update";
                case 1 -> "reset";
                case 2 -> "reachability";
                default -> "unknown";
            };
        }
    }

    private final Class<LE_TYPE> genericType;
    private final DFAnalysis<LE_TYPE> analysis;
    private final EconomicMap<ValueNode, LE_TYPE> internalMap;
    private final List<ValueUpdate<LE_TYPE>> trace;

    public DFAMap(Class<LE_TYPE> genericType, DFAnalysis<LE_TYPE> analysis, boolean saveTrace) {
        this.genericType = genericType;
        this.analysis = analysis;
        internalMap = EconomicMap.create();
        trace = saveTrace ? new ArrayList<>() : null;
    }

    /*
     * Publicly, this map acts as a read-only map
     */

    public MapCursor<ValueNode, LE_TYPE> getEntries() {
        return internalMap.getEntries();
    }

    public boolean isEvaluated(ValueNode key) {
        return internalMap.containsKey(key);
    }

    public LE_TYPE getOrUnevaluated(ValueNode key) {
        LE_TYPE result = get(key);
        if (result != null) {
            return result;
        }
        return unevaluatedElemFor(key);
    }

    public LE_TYPE getOrUnrestricted(ValueNode key) {
        LE_TYPE result = get(key);
        if (result != null) {
            return result;
        }
        return unrestrictedElemFor(key);
    }

    public boolean hasNewlyEvaluatedInputs(ValueNode node) {
        int nowUneval = analysis.domain.countUnevaluatedInputs(node, this);
        if (nowUneval < 0) {
            return false;
        }
        if (!analysis.nodesWithUnevaluatedInputs.containsKey(node)) {
            return true;
        }
        return nowUneval < analysis.nodesWithUnevaluatedInputs.get(node);
    }

    public void registerPattern(ValueNode patternOut, ValueNode upperNode) {
        analysis.workList.patternInputs.computeIfAbsent(upperNode, k -> new HashSet<>()).add(patternOut);
    }

    public void registerPattern(ValueNode patternOut, ValueNode upperNode1, ValueNode upperNode2) {
        registerPattern(patternOut, upperNode1);
        registerPattern(patternOut, upperNode2);
    }

    public void registerPattern(ValueNode patternOut, ValueNode upperNode1, ValueNode upperNode2, ValueNode upperNode3) {
        registerPattern(patternOut, upperNode1, upperNode2);
        registerPattern(patternOut, upperNode3);
    }

    public void registerPattern(ValueNode patternOut, ValueNode upperNode1, ValueNode upperNode2, ValueNode upperNode3, ValueNode upperNode4) {
        registerPattern(patternOut, upperNode1, upperNode2, upperNode3);
        registerPattern(patternOut, upperNode4);
    }

    @SuppressWarnings("unused")
    public void registerPattern(ValueNode patternOut, ValueNode upperNode1, ValueNode upperNode2, ValueNode upperNode3, ValueNode upperNode4, ValueNode... moreUppers) {
        registerPattern(patternOut, upperNode1, upperNode2, upperNode3, upperNode4);
        for (ValueNode i : moreUppers) {
            registerPattern(patternOut, i);
        }
    }

    /*
     * Private access helpers
     */

    private LE_TYPE get(ValueNode key) {
        return internalMap.get(key);
    }

    private LE_TYPE unevaluatedElemFor(ValueNode node) {
        if (node instanceof InferredFactNode<?> iFact && iFact.isOfGeneric(analysis.elementType)) {
            return unevaluatedElemFor(iFact.getOriginalNode());
        }
        return analysis.domain.unevaluated(node);
    }

    private LE_TYPE unrestrictedElemFor(ValueNode node) {
        if (node instanceof InferredFactNode<?> iFact && iFact.isOfGeneric(analysis.elementType)) {
            return unrestrictedElemFor(iFact.getOriginalNode());
        }
        return analysis.domain.unrestricted(node);
    }

    /*
     * Package visible methods to modify the internal state
     */

    /**
     * To be used for updating a value found for a given node.
     *
     * @return true if the elem has been updated.
     * @throws RuntimeException if the new element is stronger than the already stored element.
     */
    boolean update(ValueNode node, LE_TYPE updateElem) throws RuntimeException {
        Objects.requireNonNull(node);
        LE_TYPE curOrUnevaluated = getOrUnevaluated(node);
        if (!analysis.domain.isUnevaluated(curOrUnevaluated) && analysis.domain.isUnevaluated(updateElem)) {
            throw GraalError.shouldNotReachHere("Updating any element to UNEVALUATED is not permitted. " +
                            "To indicate that no information can be inferred for %s, please instead use UNRESTRICTED".formatted(node));
        }

        LE_TYPE nuElem = Objects.requireNonNull(updateElem);
        if (analysis.domain.isUnevaluated(nuElem)) {
            nuElem = analysis.domain.unrestricted(nuElem);
        }
        if (analysis.domain.isUnevaluated(curOrUnevaluated) && analysis.domain.isUnrestricted(nuElem)) {
            // no new information
            return false;
        }

        TriState nuWeakerPrev = analysis.domain.isWeakerThan(nuElem, curOrUnevaluated);
        GraalError.guarantee(nuWeakerPrev.isKnown(), "Updating %s from %s to an incomparable %s is not allowed", node, curOrUnevaluated, nuElem);

        if (nuWeakerPrev.isTrue()) {
            analysis.debug.log(DebugContext.VERY_DETAILED_LEVEL, " updating %s from '%s' to '%s'", node, curOrUnevaluated, nuElem);
            internalMap.put(node, nuElem);
            if (trace != null) {
                ArrayList<Object> rs = new ArrayList<>();
                if (node instanceof ValuePhiNode phi) {
                    HIRBlock toBlk = analysis.cfg.blockFor(phi.merge());
                    for (int i = 0; i < phi.valueCount(); i++) {
                        ValueNode vn = phi.valueAt(i);
                        HIRBlock inBlk = analysis.cfg.blockFor(phi.merge().phiPredecessorAt(i));
                        rs.add("%s%s~%s-{%s}".formatted(DFEdgeMap.blockPrettyString(inBlk), analysis.edgeMap.get(inBlk, toBlk), vn, get(vn)));
                    }
                } else {
                    for (ValueNode vn : node.inputs().filter(ValueNode.class)) {
                        rs.add("%s-{%s}".formatted(vn, get(vn)));
                    }
                }
                trace.add(new ValueUpdate<>(0, node, nuElem, rs.toArray()));
            }
            return true;
        } else if (!analysis.domain.isEqual(nuElem, curOrUnevaluated)) {
            throw GraalError.shouldNotReachHere(
                            "Updating %s from %s to %s is not permitted because the new value would be stronger which would break monotonicity".formatted(node, curOrUnevaluated, nuElem));
        } else {
            return false;
        }
    }

    /**
     * Resets the given node and all its transitive usages. This method must never (directly or
     * transitively) reset inputs of PHI nodes that were already used to calculate the domain
     * element for the PHI.
     */
    void resetNodeAndUsages(ValueNode root) {
        if (!isEvaluated(root)) {
            return;
        }
        GraalError.guarantee(!(root instanceof ValuePhiNode), "We be should never reset a PHI node like %s", root);
        ArrayList<ValueNode> resetNodes = new ArrayList<>();
        ArrayDeque<ValueNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            CompilationAlarm.checkProgress(root.graph());
            ValueNode cur = queue.remove();
            if (trace != null) {
                trace.add(new ValueUpdate<>(1, cur, getOrUnevaluated(cur), new Object[]{root}));
            }
            internalMap.removeKey(cur);
            resetNodes.add(cur);
            for (Node usage : cur.usages()) {
                if (usage instanceof ValueNode vUsage && isEvaluated(vUsage)) {
                    // this node has been evaluated before
                    if (usage instanceof ValuePhiNode uPhi) {
                        /*
                         * When resetting an input edge to a phi that was already evaluated, we must
                         * enforce that no lasting decision for the given phi was taken which took
                         * the information we are about to reset into account.
                         */
                        if (analysis.workList.isOptimisticLoopPhi(uPhi)) {
                            /*
                             * Resetting back edges into a loop phi that only has been evaluated
                             * optimistically is permissible, because no lasting decision has been
                             * taken based on this input, because back edges are deemed unreachable
                             * when optimistically evaluating a loop phi.
                             */
                            GraalError.guarantee(!uPhi.valueAt(0).equals(usage), "LoopPHI %s has already been evaluated once and received a value due to %s. " +
                                            "We must not touch values upon which lasting effects were calculated!", uPhi, usage);
                        } else {
                            /*
                             * We must only change unreachable input edges since those did not
                             * contribute to the final result of the PHI node yet.
                             */
                            for (AbstractEndNode associatedEnd : getEndsForPhiInput(uPhi, cur)) {
                                /*
                                 * We can only reset inputs of PHIs that we did not evaluate yet or
                                 * if the input edge we try to reset is UNREACHABLE, otherwise the
                                 * edge we reset has contributed to calculating the PHI result.
                                 */
                                CFGEdge mergeEdge = new CFGEdge(analysis.cfg.blockFor(associatedEnd), analysis.cfg.blockFor(uPhi.merge()));
                                GraalError.guarantee(analysis.edgeMap.get(mergeEdge) == Reachability.UNREACHABLE,
                                                "Must not change reachable input %s (%s) of PHI (%s) via resetting", cur, mergeEdge, uPhi);
                            }
                        }
                        // we do not schedule phis because we have already done the necessary
                        // work
                    } else {
                        // schedule this previously evaluated node for resetting
                        queue.add(vUsage);
                    }
                }

                // additional check if no control flow decision has been taken on the basis of
                // results that are dropped here
                if (usage instanceof ControlSplitNode split) {
                    /*
                     * If all successors are unevaluated, the block itself has not generated any
                     * control flow information.
                     */
                    boolean hasBeenEvaluated = false;
                    /*
                     * If all successors are unreachable, the block itself has been considered
                     * unreachable until now and has therefore not generated meaningful control flow
                     * information.
                     */
                    boolean allSuccessorsUnreachable = true;
                    HIRBlock from = analysis.cfg.blockFor(split);
                    for (int i = 0; i < from.getSuccessorCount(); i++) {
                        Reachability toIthSuc = analysis.edgeMap.get(from, from.getSuccessorAt(i));
                        if (toIthSuc != Reachability.UNKNOWN) {
                            hasBeenEvaluated = true;
                        }
                        if (toIthSuc != Reachability.UNREACHABLE) {
                            allSuccessorsUnreachable = false;
                        }
                    }
                    GraalError.guarantee(!hasBeenEvaluated || allSuccessorsUnreachable,
                                    "Must not reset %s (by resetting %s) which has already been used to calculate reachability in %s", usage, root, split);
                    // adding here may lead to duplicate entries, but this is no problem
                    // resetNodes.add(split);
                }
            }
        }

        /*
         * We also need to remove all reset nodes from the work list. If an evaluation is needed in
         * the future, these nodes will come up again naturally.
         */
        analysis.workList.unschedule(resetNodes);
    }

    Class<LE_TYPE> getGenericType() {
        return genericType;
    }

    static List<AbstractEndNode> getEndsForPhiInput(ValuePhiNode phi, ValueNode input) {
        AbstractMergeNode merge = phi.merge();
        List<AbstractEndNode> associatedEnds = new ArrayList<>();
        for (int i = 0; i < phi.valueCount(); i++) {
            if (phi.valueAt(i).equals(input)) {
                associatedEnds.add(merge.phiPredecessorAt(i));
            }
        }
        GraalError.guarantee(!associatedEnds.isEmpty(), "Did not find associated CFG edge (%s usages=[%s] -> %s predecessors=[%s])",
                        input, input.usages().snapshot(), phi, phi.values().snapshot());
        return associatedEnds;
    }

    void recordPropagateReachability(ValueNode end, CFGEdge edge, DFEdgeMap<LE_TYPE> edgeMap, boolean onlyProp) {
        if (trace != null) {
            trace.add(new ValueUpdate<>(2, end, null, new Object[]{onlyProp, edge, edgeMap.get(edge)}));
        }
    }

    /**
     * Produces a nicely formatted multiline String representation of the given map.
     */
    @SuppressWarnings("deprecation")
    public String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append("DFAMap<").append(genericType.getSimpleName()).append(">: {\n    elems: {");
        ArrayList<String> elems = new ArrayList<>();
        SortedSet<ValueNode> keys = new TreeSet<>(Comparator.comparingInt(Node::getId));
        for (ValueNode k : internalMap.getKeys()) {
            keys.add(k);
        }
        for (ValueNode k : keys) {
            elems.add(String.format("        %s := %s", k, get(k)));
        }
        if (!elems.isEmpty()) {
            sb.append('\n').append(String.join(",\n", elems)).append("\n    ");
        } else {
            sb.append(' ');
        }
        sb.append("}\n    history: {");
        elems.clear();
        if (trace != null) {
            EconomicMap<Node, List<LE_TYPE>> histMap = EconomicMap.create();
            for (ValueUpdate<LE_TYPE> act : trace) {
                histMap.putIfAbsent(act.node(), new ArrayList<>());
                LE_TYPE result = act.action() == 1 ? analysis.domain.unevaluated(act.elem()) : act.elem();
                histMap.get(act.node()).add(result);
            }
            for (ValueNode k : keys) {
                final ArrayList<String> iar = new ArrayList<>();
                histMap.get(k).forEach(l -> iar.add("%s".formatted(l)));
                elems.add(String.format("        %s  ===  [%s]", k, String.join(" --> ", iar)));
            }
        }
        if (!elems.isEmpty()) {
            sb.append('\n').append(String.join(",\n", elems)).append("\n    ");
        } else {
            sb.append(' ');
        }
        sb.append("}\n}");
        return sb.toString();
    }

    /**
     * Produces a nicely formatted multiline String representation of the trace recorded throughout
     * the optimization.
     */
    public String printTrace() {
        StringBuilder sb = new StringBuilder();
        sb.append("Trace: {");
        if (trace == null) {
            sb.append(" not recorded, use '-Djdk.graal.CcpRecordTrace=true' to enable tracing for CCP phase }");
        } else if (trace.isEmpty()) {
            sb.append(" }");
        } else {
            for (ValueUpdate<LE_TYPE> ud : trace) {
                sb.append("\n    %s <<%s>> %s (with cause %s)".formatted(ud.node(), ValueUpdate.getAction(ud.action()), ud.elem(), Arrays.toString(ud.reason())));
            }
            sb.append("\n}");
        }
        return sb.toString();
    }
}
