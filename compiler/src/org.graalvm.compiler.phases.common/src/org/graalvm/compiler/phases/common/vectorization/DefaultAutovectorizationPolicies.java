/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.phases.common.vectorization;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ValueNode;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultAutovectorizationPolicies implements AutovectorizationPolicies {

    @Override
    public int estSavings(AutovectorizationContext context, Set<Pair<ValueNode, ValueNode>> packSet, Node s1, Node s2) {
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

                if (context.getBlockInfo().adjacent(leftInput, rightInput)) {
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

                    if (context.getBlockInfo().adjacent(s1Usage, s2Usage)) {
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

    private static int scorePack(AutovectorizationContext context, Pack pack, Map<Node, Pack> nodeToPackMap) {
        int score = 0;

        final List<ValueNode> elements = pack.getElements();
        for (int i = 0; i < elements.size(); i++) {
            final ValueNode node = elements.get(i);

            for (Node input : node.inputs()) {
                final Pack inputPack = nodeToPackMap.get(input);
                if (inputPack != null && inputPack.getElements().size() > i && inputPack.getElements().get(i) == input) {
                    score += scorePack(context, inputPack, nodeToPackMap);
                } else if (context.supported(input)) {
                    score++;
                }
            }
        }

        return score;
    }

    @Override
    public void filterPacks(AutovectorizationContext context, Set<Pack> combinedPackSet) {
        final Map<Node, Pack> nodeToPackMap = combinedPackSet.stream().flatMap(pack -> pack.getElements().stream().map(node -> Pair.create(node, pack))).collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        int score = 0;

        for (Pack pack : combinedPackSet) {
            score += scorePack(context, pack, nodeToPackMap);
        }

        if (score > 0) {
            combinedPackSet.clear();
        }
    }

}
