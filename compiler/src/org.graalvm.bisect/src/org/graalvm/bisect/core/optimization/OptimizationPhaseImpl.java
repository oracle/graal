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
package org.graalvm.bisect.core.optimization;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class OptimizationPhaseImpl implements OptimizationPhase {
    private final String name;
    private List<OptimizationTreeNode> children = null;

    public OptimizationPhaseImpl(String name) {
        this.name = name;
    }

    public void addChild(OptimizationTreeNode optimizationTreeNode) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(optimizationTreeNode);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<OptimizationTreeNode> getChildren() {
        if (children == null) {
            return List.of();
        }
        return children;
    }

    @Override
    public List<Optimization> getOptimizationsRecursive() {
        List<Optimization> optimizations = new ArrayList<>();
        Deque<OptimizationTreeNode> stack = new ArrayDeque<>();
        stack.add(this);
        while (!stack.isEmpty()) {
            OptimizationTreeNode treeNode = stack.pop();
            if (treeNode instanceof OptimizationPhase) {
                for (OptimizationTreeNode childNode : ((OptimizationPhase) treeNode).getChildren()) {
                    stack.push(childNode);
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
        if (!(object instanceof OptimizationPhaseImpl)) {
            return false;
        }
        OptimizationPhaseImpl other = (OptimizationPhaseImpl) object;
        return Objects.equals(name, other.name) && Objects.equals(children, other.children);
    }

    @Override
    public int hashCode() {
        return name.hashCode() + ((children == null) ? -1 : children.hashCode());
    }
}
