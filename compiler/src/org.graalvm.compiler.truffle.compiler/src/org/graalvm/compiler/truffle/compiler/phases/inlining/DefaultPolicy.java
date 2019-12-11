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

import java.util.Comparator;
import java.util.PriorityQueue;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;
import org.graalvm.options.OptionValues;

import static org.graalvm.compiler.truffle.compiler.TruffleCompilerOptions.getPolyglotOptionValue;

final class DefaultPolicy implements InliningPolicy {

    private static final int MAX_DEPTH = 15;
    private static final Comparator<CallNode> CALL_NODE_COMPARATOR = (o1, o2) -> Double.compare(o2.getRootRelativeFrequency(), o1.getRootRelativeFrequency());
    private final OptionValues options;
    private int expandedCount = 0;

    DefaultPolicy(OptionValues options) {
        this.options = options;
    }

    private static PriorityQueue<CallNode> getQueue(CallTree tree, CallNode.State state) {
        PriorityQueue<CallNode> queue = new PriorityQueue<>(CALL_NODE_COMPARATOR);
        for (CallNode child : tree.getRoot().getChildren()) {
            if (child.getState() == state) {
                queue.add(child);
            }
        }
        return queue;
    }

    private static void doInline(CallNode candidate, PriorityQueue<CallNode> inlineQueue) {
        candidate.inline();
        for (CallNode child : candidate.getChildren()) {
            if (child.getState() == CallNode.State.Expanded) {
                inlineQueue.add(child);
            }
        }
    }

    @Override
    public void run(CallTree tree) {
        expand(tree);
        inline(tree);
    }

    private void inline(CallTree tree) {
        final int inliningBudget = getPolyglotOptionValue(options, PolyglotCompilerOptions.InliningInliningBudget);
        final PriorityQueue<CallNode> inlineQueue = getQueue(tree, CallNode.State.Expanded);
        CallNode candidate;
        while ((candidate = inlineQueue.poll()) != null) {
            if (candidate.isForced()) {
                doInline(candidate, inlineQueue);
                continue;
            }
            if (tree.getRoot().getIR().getNodeCount() + candidate.getIR().getNodeCount() > inliningBudget) {
                break;
            }
            doInline(candidate, inlineQueue);
        }
    }

    private void expand(CallTree tree) {
        final int expansionBudget = getPolyglotOptionValue(options, PolyglotCompilerOptions.InliningExpansionBudget);
        final PriorityQueue<CallNode> expandQueue = getQueue(tree, CallNode.State.Cutoff);
        CallNode candidate;
        while ((candidate = expandQueue.poll()) != null) {
            if (candidate.isForced()) {
                doExpand(candidate, expandQueue);
                continue;
            }
            if (expandedCount > expansionBudget) {
                break;
            }
            final Integer maximumRecursiveInliningValue = getPolyglotOptionValue(options, PolyglotCompilerOptions.InliningRecursionDepth);
            if (candidate.getRecursionDepth() > maximumRecursiveInliningValue || candidate.getDepth() > MAX_DEPTH) {
                continue;
            }
            doExpand(candidate, expandQueue);
        }
    }

    private void doExpand(CallNode candidate, PriorityQueue<CallNode> expandQueue) {
        candidate.expand();
        expandedCount += candidate.getIR().getNodeCount();
        for (CallNode child : candidate.getChildren()) {
            if (child.getState() == CallNode.State.Cutoff) {
                expandQueue.add(child);
            }
        }
    }
}
