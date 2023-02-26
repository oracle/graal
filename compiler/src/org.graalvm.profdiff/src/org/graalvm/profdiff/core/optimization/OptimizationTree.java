/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.core.optimization;

import org.graalvm.profdiff.core.VerbosityLevel;
import org.graalvm.profdiff.util.Writer;

/**
 * An optimization tree of a compilation unit. The root phase is a dummy {@link OptimizationPhase},
 * which holds all optimization phases. Each {@link OptimizationTreeNode node} of the optimization
 * tree is either an {@link OptimizationPhase} or an individual {@link Optimization}. An
 * optimization is always a leaf node. The children of an optimization phase are its directly
 * invoked subphases and performed optimizations.
 *
 * Example of an optimization tree:
 *
 * <pre>
 *                                   RootPhase
 *                     _____________/    |    \_____________
 *                    /                  |                  \
 *                 LowTier            MidTier            HighTier
 *            ______/  \___              |                   |
 *           /             \     CanonicalizerPhase         ...
 *   LoopPeelingPhase      ...     ___|     |__________
 *           |                    /                    \
 *      LoopPeeling       CfgSimplification      CanonicalReplacement
 *       at bci 122           at bci 13                at bci 25
 *                                          {replacedNodeClass: ValuePhi,
 *                                           canonicalNodeClass: Constant}
 * </pre>
 */
public class OptimizationTree {
    /**
     * The root optimization phase of this compilation unit, which holds all optimization phases
     * applied in this compilation.
     */
    private final OptimizationPhase root;

    public OptimizationTree(OptimizationPhase root) {
        this.root = root;
    }

    /**
     * Gets the root optimization phase of this compilation unit, which holds all optimization
     * phases applied in a compilation.
     *
     * @return the root optimization phase
     */
    public OptimizationPhase getRoot() {
        return root;
    }

    /**
     * Removes all subtrees from {@link #root the optimization tree} which are optimization phases
     * in {@link OptimizationPhase#isVeryDetailedCategory() the very detailed category}.
     */
    public void removeVeryDetailedPhases() {
        root.removeIf(optimizationTreeNode -> {
            if (!(optimizationTreeNode instanceof OptimizationPhase)) {
                return false;
            }
            OptimizationPhase optimizationPhase = (OptimizationPhase) optimizationTreeNode;
            return optimizationPhase.isVeryDetailedCategory();
        });
    }

    /**
     * Sorts children of all {@link OptimizationPhase#isUnorderedCategory() unordered phases} in
     * {@link #root the optimization tree} according to
     * {@link OptimizationTreeNode#compareTo(OptimizationTreeNode) their comparator}.
     */
    public void sortUnorderedPhases() {
        root.forEach(optimizationTreeNode -> {
            if (!(optimizationTreeNode instanceof OptimizationPhase)) {
                return;
            }
            OptimizationPhase optimizationPhase = (OptimizationPhase) optimizationTreeNode;
            if (optimizationPhase.isUnorderedCategory()) {
                optimizationPhase.getChildren().sort(OptimizationTreeNode::compareTo);
            }
        });
    }

    /**
     * Preprocesses the optimization tree according to the provided verbosity level.
     *
     * @param verbosityLevel the verbosity level
     */
    public void preprocess(VerbosityLevel verbosityLevel) {
        if (verbosityLevel.shouldRemoveVeryDetailedPhases()) {
            removeVeryDetailedPhases();
        }
        if (verbosityLevel.shouldSortUnorderedPhases()) {
            sortUnorderedPhases();
        }
    }

    /**
     * Prints the optimization tree with a header in preorder using the destination writer.
     *
     * @param writer the destination writer
     */
    public void write(Writer writer) {
        writer.writeln("Optimization tree");
        writer.increaseIndent();
        root.writeRecursive(writer);
        writer.decreaseIndent();
    }
}
