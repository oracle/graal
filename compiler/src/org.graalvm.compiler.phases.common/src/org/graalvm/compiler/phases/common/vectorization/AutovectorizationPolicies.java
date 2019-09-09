package org.graalvm.compiler.phases.common.vectorization;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ValueNode;

import java.util.Set;

public interface AutovectorizationPolicies {

    /**
     * Estimate the savings of executing the pack rather than two separate instructions.
     *
     * @param s1 Candidate left element of Pack
     * @param s2 Candidate right element of Pack
     * @param packSet PackSet, for membership checks
     * @return Savings in an arbitrary unit and can be negative.
     */
    int estSavings(BlockInfo blockInfo, Set<Pair<ValueNode, ValueNode>> packSet, Node s1, Node s2);

}
