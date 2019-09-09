package org.graalvm.compiler.phases.common.vectorization;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ValueNode;

import java.util.Iterator;
import java.util.Set;

public class DefaultAutovectorizationPolicies implements AutovectorizationPolicies {

    @Override
    public int estSavings(BlockInfo blockInfo, Set<Pair<ValueNode, ValueNode>> packSet, Node s1, Node s2) {
        // Savings originating from inputs
        int saveIn = 1; // Save 1 instruction as executing 2 in parallel.

        outer: // labelled outer loop so that hasNext check is performed for left
        for (Iterator<Node> leftInputIt = s1.inputs().iterator(); leftInputIt.hasNext();) {
            for (Iterator<Node> rightInputIt = s2.inputs().iterator(); leftInputIt.hasNext() && rightInputIt.hasNext();) {
                final Node leftInput = leftInputIt.next();
                final Node rightInput = rightInputIt.next();

                if (leftInput == rightInput) {
                    continue outer;
                }

                if (blockInfo.adjacent(leftInput, rightInput)) {
                    // Inputs are adjacent in memory, this is good.
                    saveIn += 2; // Not necessarily packed, but good because packing is easy.
                } else if (packSet.contains(Pair.create(leftInput, rightInput))) {
                    saveIn += 2; // Inputs already packed, so we don't need to pack these.
                } else {
                    saveIn -= 2; // Not adjacent, not packed. Inputs need to be packed in a
                    // vector for candidate.
                }
            }
        }

        // Savings originating from result
        int ct = 0; // the number of usages that are packed
        int saveUse = 0;
        for (Node s1Usage : s1.usages()) {
            for (Pair<ValueNode, ValueNode> pack : packSet) {
                if (pack.getLeft() != s1Usage) {
                    continue;
                }

                for (Node s2Usage : s2.usages()) {
                    if (pack.getRight() != s2Usage) {
                        continue;
                    }

                    ct++;

                    if (blockInfo.adjacent(s1Usage, s2Usage)) {
                        saveUse += 2;
                    }
                }
            }
        }

        // idk, c2 does this though
        if (ct < s1.getUsageCount()) {
            saveUse += 1;
        }
        if (ct < s2.getUsageCount()) {
            saveUse += 1;
        }

        // TODO: investigate this formula - can't have negative savings
        return Math.max(saveIn, saveUse);
    }

}
