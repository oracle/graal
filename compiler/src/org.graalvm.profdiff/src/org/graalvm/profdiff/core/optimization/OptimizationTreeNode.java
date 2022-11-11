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

import java.util.Comparator;
import java.util.Objects;

import org.graalvm.collections.EconomicMapUtil;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.profdiff.core.TreeNode;

/**
 * Marks a node in the optimization tree. The nodes in the optimization tree are phases and
 * individual optimizations, which are always leaf nodes. The children of an optimization phase are
 * its subphases and performed optimizations.
 */
public abstract class OptimizationTreeNode extends TreeNode<OptimizationTreeNode> implements Comparable<OptimizationTreeNode> {
    protected OptimizationTreeNode(String name) {
        super(name);
    }

    /**
     * Compares {@link Optimization#getPosition() positions} of optimizations lexicographically.
     */
    private static final Comparator<UnmodifiableEconomicMap<String, Integer>> POSITION_COMPARATOR = EconomicMapUtil.lexicographicalComparator(
                    Comparator.nullsFirst(String::compareTo), Comparator.nullsFirst(Integer::compareTo));

    /**
     * Compares {@link Optimization#getProperties() properties} of optimizations lexicographically
     * by converting values to strings.
     */
    private static final Comparator<UnmodifiableEconomicMap<String, Object>> PROPERTIES_COMPARATOR = EconomicMapUtil.lexicographicalComparator(
                    Comparator.nullsFirst(String::compareTo), Comparator.comparing(Objects::toString));

    /**
     * Compares optimization tree nodes according to their content. {@link Optimization
     * Optimizations} come before {@link OptimizationPhase optimization phases}. Optimization phases
     * are compared according to their names. Optimizations are compared lexicographically according
     * to their {@link Optimization#getName() optimization name}, {@link Optimization#getEventName()
     * event name}, {@link Optimization#getPosition() position} and
     * {@link Optimization#getProperties() properties} (in this order).
     *
     * @param node the object to be compared.
     * @return the result of the comparison
     */
    @Override
    public int compareTo(OptimizationTreeNode node) {
        if (this instanceof OptimizationPhase) {
            if (node instanceof OptimizationPhase) {
                return getName().compareTo(node.getName());
            } else {
                assert node instanceof Optimization;
                return 1;
            }
        } else {
            assert this instanceof Optimization;
            if (node instanceof OptimizationPhase) {
                return -1;
            } else {
                assert node instanceof Optimization;
                Optimization self = (Optimization) this;
                Optimization other = (Optimization) node;
                int order = self.getName().compareTo(other.getName());
                if (order != 0) {
                    return order;
                }
                order = self.getEventName().compareTo(other.getEventName());
                if (order != 0) {
                    return order;
                }
                order = POSITION_COMPARATOR.compare(self.getPosition(), other.getPosition());
                if (order != 0) {
                    return order;
                }
                return PROPERTIES_COMPARATOR.compare(self.getProperties(), other.getProperties());
            }
        }
    }
}
