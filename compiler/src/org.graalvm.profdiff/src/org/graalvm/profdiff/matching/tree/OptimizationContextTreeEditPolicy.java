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
package org.graalvm.profdiff.matching.tree;

import org.graalvm.profdiff.core.OptimizationContextTreeNode;
import org.graalvm.profdiff.core.inlining.InliningTreeNode;

import java.util.Objects;

public class OptimizationContextTreeEditPolicy extends TreeEditPolicy<OptimizationContextTreeNode> {
    @Override
    public boolean nodesEqual(OptimizationContextTreeNode node1, OptimizationContextTreeNode node2) {
        if (node1.getOriginalOptimization() != null && node2.getOriginalOptimization() != null) {
            return node1.getOriginalOptimization().equals(node2.getOriginalOptimization());
        } else if (node1.getOriginalInliningTreeNode() != null && node2.getOriginalInliningTreeNode() != null) {
            InliningTreeNode inliningTreeNode1 = node1.getOriginalInliningTreeNode();
            InliningTreeNode inliningTreeNode2 = node2.getOriginalInliningTreeNode();
            return inliningTreeNode1.getBCI() == inliningTreeNode2.getBCI() && inliningTreeNode1.isPositive() == inliningTreeNode2.isPositive() &&
                            Objects.equals(inliningTreeNode1.getName(), inliningTreeNode2.getName());
        }
        return !node1.hasOriginalNode() && !node2.hasOriginalNode();
    }

    @Override
    public long relabelCost(OptimizationContextTreeNode node1, OptimizationContextTreeNode node2) {
        assert node1 != node2;
        if (node1.getOriginalInliningTreeNode() != null && node2.getOriginalInliningTreeNode() != null) {
            InliningTreeNode inliningTreeNode1 = node1.getOriginalInliningTreeNode();
            InliningTreeNode inliningTreeNode2 = node2.getOriginalInliningTreeNode();
            if (inliningTreeNode1.getBCI() == inliningTreeNode2.getBCI() && Objects.equals(inliningTreeNode1.getName(), inliningTreeNode2.getName())) {
                return UNIT_COST;
            }
        }
        return INFINITE_COST;
    }
}
