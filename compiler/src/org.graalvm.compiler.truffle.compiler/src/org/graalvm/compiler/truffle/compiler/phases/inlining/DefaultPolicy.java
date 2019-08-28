/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.phases.inlining;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.compiler.SharedTruffleCompilerOptions;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions;

final class DefaultPolicy implements InliningPolicy {

    private static final int MAX_DEPTH = 15;
    private final OptionValues optionValues;
    private int expandedCount = 0;

    DefaultPolicy(OptionValues optionValues) {
        this.optionValues = optionValues;
    }

    @Override
    public void afterExpand(CallNode callNode) {
        expandedCount += callNode.getIR().getNodeCount();
    }

    @Override
    public void run(CallTree tree) {
        final int expansionBudget = TruffleCompilerOptions.TruffleInliningExpansionBudget.getValue(optionValues);
        CallNode candidate;
        while ((candidate = getNodeToExpand(tree)) != null) {
            if (candidate.isForced()) {
                candidate.expand();
                continue;
            }
            if (expandedCount > expansionBudget) {
                break;
            }
            final Integer maximumRecursiveInliningValue = SharedTruffleCompilerOptions.TruffleMaximumRecursiveInlining.getValue(optionValues);
            if (candidate.getRecursionDepth() > maximumRecursiveInliningValue && candidate.getDepth() > MAX_DEPTH) {
                break;
            }
            candidate.expand();
        }
        final int inliningBudget = TruffleCompilerOptions.TruffleInliningInliningBudget.getValue(optionValues);
        while ((candidate = getNodeToInline(tree)) != null) {
            if (candidate.isForced()) {
                candidate.inline();
                continue;
            }
            if (tree.getRoot().getIR().getNodeCount() + candidate.getIR().getNodeCount() > inliningBudget) {
                break;
            }
            candidate.inline();
        }
    }

    private CallNode getNodeToInline(CallTree tree) {
        List<CallNode> edge = new ArrayList<>();
        gatherEdge(tree.getRoot(), edge, CallNode.State.Expanded, CallNode.State.Inlined, null);
        edge.sort((o1, o2) -> Double.compare(o2.getRootRelativeFrequency(), o1.getRootRelativeFrequency()));
        return edge.size() > 0 ? edge.get(0) : null;
    }

    private CallNode getNodeToExpand(CallTree tree) {
        List<CallNode> edge = new ArrayList<>();
        gatherEdge(tree.getRoot(), edge, CallNode.State.Cutoff, CallNode.State.Expanded, CallNode.State.Inlined);
        edge.sort((o1, o2) -> Double.compare(o2.getRootRelativeFrequency(), o1.getRootRelativeFrequency()));
        return edge.size() > 0 ? edge.get(0) : null;
    }

    private void gatherEdge(CallNode node, List<CallNode> edge, CallNode.State state, CallNode.State continueState1, CallNode.State continueState2) {
        if (node.getState() == state) {
            edge.add(node);
            return;
        }
        if (node.getState() == continueState1 || node.getState() == continueState2) {
            for (CallNode child : node.getChildren()) {
                gatherEdge(child, edge, state, continueState1, continueState2);
            }
        }
    }
}
