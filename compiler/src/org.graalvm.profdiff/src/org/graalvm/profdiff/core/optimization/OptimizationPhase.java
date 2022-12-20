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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents an optimization phase in the optimization tree. Allows the children (either
 * optimization phases or directly performed {@link Optimization optimizations}) to be incrementally
 * added.
 *
 * An example of an optimization phase is a {@code LoopPeelingPhase}. The children of this phase are
 * the individual loop peelings (each performed at some position). An example of an optimization
 * phase that triggers other (children) phases is the {@code IterativeConditionalEliminationPhase}
 * or phase suites.
 *
 * The list of children of this node in the optimization tree is the list of subphases and
 * optimizations triggered in this phase.
 */
public class OptimizationPhase extends OptimizationTreeNode {
    /**
     * Constructs an optimization phase.
     *
     * @param name the name of this optimization phase, which corresponds to the name of its class
     */
    public OptimizationPhase(String name) {
        super(name);
    }

    /**
     * Suffixes of optimization phases names which produce many optimizations with little individual
     * impact.
     */
    private static final String[] veryDetailedPhaseSuffixes = {"CanonicalizerPhase", "DeadCodeEliminationPhase", "InliningPhase"};

    /**
     * Returns whether this optimization phase produces many optimizations with little individual
     * impact.
     */
    public boolean isVeryDetailedCategory() {
        return Arrays.stream(veryDetailedPhaseSuffixes).anyMatch(suffix -> getName().endsWith(suffix));
    }

    /**
     * Suffixes of optimizations phase names where the order of optimizations performed and
     * subphases triggered by them is unimportant.
     */
    private static final String[] unorderedPhaseSuffixes = {
                    "CanonicalizerPhase",
                    "DeadCodeEliminationPhase",
                    "PartialEscapePhase",
                    "FloatingReadPhase",
                    "UseTrappingNullChecksPhase",
                    "ReassociationPhase",
                    "ConvertDeoptimizeToGuardPhase",
                    "ConditionalEliminationPhase",
    };

    /**
     * Returns whether the order of optimizations performed and subphases triggered by this phase is
     * unimportant.
     */
    public boolean isUnorderedCategory() {
        return Arrays.stream(unorderedPhaseSuffixes).anyMatch(suffix -> getName().endsWith(suffix));
    }

    /**
     * Creates and returns a list of all optimizations performed directly in this phase and
     * indirectly in its subphases, preserving the order.
     *
     * @return the list of direct and indirect optimizations
     */
    public List<Optimization> getOptimizationsRecursive() {
        List<Optimization> optimizations = new ArrayList<>();
        forEach(node -> {
            if (node instanceof Optimization) {
                Optimization optimization = (Optimization) node;
                optimizations.add(optimization);
            }
        });
        return optimizations;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof OptimizationPhase)) {
            return false;
        }
        OptimizationPhase other = (OptimizationPhase) object;
        return getName().equals(other.getName()) && getChildren().equals(other.getChildren());
    }

    @Override
    public int hashCode() {
        return getName().hashCode() + getChildren().hashCode();
    }
}
