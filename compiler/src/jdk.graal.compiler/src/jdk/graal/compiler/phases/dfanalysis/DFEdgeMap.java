/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package jdk.graal.compiler.phases.dfanalysis;

import java.util.ArrayList;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

/**
 * Map to track Reachability of edges in the CFG. Read access is guaranteed to be non-null.
 */
public final class DFEdgeMap<T> {
    public record CFGEdge(HIRBlock from, HIRBlock to) {
        public CFGEdge {
            GraalError.guarantee(from.containsSucc(to), "CFG edge from %s to %s does not exist!", from, to);
        }

        @Override
        public String toString() {
            return "CFGEdge[%s, %s]".formatted(blockPrettyString(from), blockPrettyString(to));
        }
    }

    /**
     * Reachability is effectively tracked as a lattice allowing for very similar reasoning about
     * reachability as we use for data flow. In this context UNKNOWN is our strongest value,
     * REACHABLE is our weakest value and UNREACHABLE sits in between. For each control flow edge we
     * compute a trace through this lattice in the same way as we do for data flow.
     */
    public enum Reachability {
        REACHABLE,
        UNREACHABLE,
        UNKNOWN
    }

    private final EconomicMap<CFGEdge, Reachability> reachMap = EconomicMap.create();
    private final DFAnalysis<T> analysis;
    private final AnalysisDomainDefinition<T> domain;

    DFEdgeMap(DFAnalysis<T> analysis, AnalysisDomainDefinition<T> domain) {
        this.analysis = analysis;
        this.domain = domain;
    }

    /**
     * Update Reachability in a safe manner. All PHIs and inferred facts that now become reachable
     * through this update are rescheduled using the work list.
     *
     * @return true if the reachability of the given edge has been updated.
     */
    boolean update(CFGEdge edge, boolean reachable) {
        Reachability previous = get(edge);
        if (reachable) {
            if (previous == Reachability.UNREACHABLE) {
                analysis.debug.log(DebugContext.VERY_DETAILED_LEVEL, " updating %s to REACHABLE", edge);
                reachMap.put(edge, Reachability.REACHABLE);
                AbstractBeginNode begin = edge.to().getBeginNode();
                if (begin instanceof AbstractMergeNode merge) {
                    // schedule all PHIs that just became reachable and may further contribute
                    // to the analysis (by not being evaluated to UNRESTRICTED already)
                    for (ValuePhiNode phi : merge.phis().filter(ValuePhiNode.class)) {
                        if (!domain.isOfInterest(phi)) {
                            // analysis is not interested in this phi
                            continue;
                        }
                        T phiElem = analysis.elemMap.getOrUnevaluated(phi);
                        if (!domain.isEqual(domain.unrestricted(phiElem), phiElem)) {
                            analysis.workList.schedule(phi);
                        }
                    }
                }
                for (InferredFactNode<?> possibleFact : begin.usages().filter(InferredFactNode.class)) {
                    InferredFactNode<T> fact = possibleFact.tryCast(analysis.elementType);
                    if (fact == null) {
                        GraalError.shouldNotReachHere("Expected InferredFactNode<" + analysis.elementType.getSimpleName() +
                                        ">, found foreign InferredFactNode<" + possibleFact.elementType.getSimpleName() + ">");
                    }
                    if (!domain.isEqual(fact.getAdditionalInformation(), analysis.elemMap.getOrUnevaluated(fact))) {
                        // we need to reschedule inferred facts that contribute to data flow
                        // information when they become reachable
                        analysis.workList.schedule(fact);
                    }
                }
                return true;
            } else {
                return false;
            }
        } else {
            if (previous == Reachability.UNKNOWN) {
                analysis.debug.log(DebugContext.VERY_DETAILED_LEVEL, " updating %s to UNREACHABLE", edge);
                reachMap.put(edge, Reachability.UNREACHABLE);
                return true;
            } else if (previous == Reachability.REACHABLE) {
                throw GraalError.shouldNotReachHere("Raising reachability of CFGEdge %s is not permitted".formatted(edge));
            } else {
                return false;
            }
        }
    }

    public Reachability get(HIRBlock from, HIRBlock to) {
        return get(new CFGEdge(from, to));
    }

    public Reachability get(CFGEdge target) {
        if (reachMap.containsKey(target)) {
            return reachMap.get(target);
        }
        return Reachability.UNKNOWN;
    }

    public Iterable<CFGEdge> getKeys() {
        return reachMap.getKeys();
    }

    @Override
    public String toString() {
        return reachMap.toString();
    }

    /**
     * Produces a nicely formatted multiline String representation of the given map for debugging
     * purposes.
     */
    public String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append("EdgeMap: {");
        ArrayList<String> elems = new ArrayList<>();
        MapCursor<CFGEdge, Reachability> cursor = reachMap.getEntries();
        while (cursor.advance()) {
            CFGEdge node = cursor.getKey();
            Reachability reach = cursor.getValue();
            elems.add(String.format("    %s := %s", node, reach));
        }
        if (!elems.isEmpty()) {
            sb.append('\n').append(String.join(",\n", elems)).append('\n');
        } else {
            sb.append(' ');
        }
        sb.append('}');
        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    public static String blockPrettyString(HIRBlock block) {
        return "%s[%s_%s]".formatted(block, block.getBeginNode().getId(), block.getEndNode().getId());
    }
}
