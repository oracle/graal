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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

/**
 * Represents an optimization phase in the optimization tree. Allows the children (either
 * optimization phases or directly performed {@link Optimization optimizations}) to be incrementally
 * added.
 *
 * An example of an optimization phase is a {@code LoopPeelingPhase}. The children of this phase are
 * the individual loop peelings (each performed at some position). An example of an optimization
 * phase that triggers other (children) phases is the {@code IterativeConditionalEliminationPhase}
 * or phase suites.
 */
public class OptimizationPhase implements OptimizationTreeNode {
    /**
     * The name of this optimization phase, which corresponds to the name of its class.
     */
    private final String name;

    /**
     * The list of children of this node in the optimization tree, i.e., the list of subphases and
     * optimizations triggered in this phase. If there are no children, the field can be
     * {@code null} to save space.
     */
    private List<OptimizationTreeNode> children = null;

    public OptimizationPhase(String name) {
        this.name = name;
    }

    public void addChild(OptimizationTreeNode optimizationTreeNode) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(optimizationTreeNode);
    }

    /**
     * Gets the name of the optimization phase.
     *
     * @return the name of the optimization phase
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Gets the list of optimization phases triggered inside this phase.
     *
     * @return the list of phases triggered by this phase
     */
    @Override
    public List<OptimizationTreeNode> getChildren() {
        if (children == null) {
            return List.of();
        }
        return children;
    }

    /**
     * Creates and returns a list of all optimizations performed directly in this phase and
     * indirectly in its subphases, preserving the order.
     *
     * @return the list of direct and indirect optimizations
     */
    public List<Optimization> getOptimizationsRecursive() {
        List<Optimization> optimizations = new ArrayList<>();
        Deque<OptimizationTreeNode> stack = new ArrayDeque<>();
        stack.add(this);
        while (!stack.isEmpty()) {
            OptimizationTreeNode treeNode = stack.pop();
            if (treeNode instanceof OptimizationPhase) {
                List<OptimizationTreeNode> treeNodeChildren = treeNode.getChildren();
                ListIterator<OptimizationTreeNode> iterator = treeNodeChildren.listIterator(treeNodeChildren.size());
                while (iterator.hasPrevious()) {
                    stack.push(iterator.previous());
                }
            } else {
                assert treeNode instanceof Optimization;
                optimizations.add((Optimization) treeNode);
            }
        }
        return optimizations;
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof OptimizationPhase)) {
            return false;
        }
        OptimizationPhase other = (OptimizationPhase) object;
        return Objects.equals(name, other.name) && Objects.equals(children, other.children);
    }

    @Override
    public int hashCode() {
        return name.hashCode() + ((children == null) ? -1 : children.hashCode());
    }
}
