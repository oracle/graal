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

import java.util.Objects;

import org.graalvm.profdiff.core.inlining.InliningTreeNode;

/**
 * Provides an equality test of two {@link InliningTreeNode inlining tree nodes} and determines
 * costs of edit operations.
 */
public class InliningTreeEditPolicy extends TreeEditPolicy<InliningTreeNode> {
    /**
     * Tests two inlining tree nodes for equality by comparing the {@link InliningTreeNode#getBCI()
     * bci of their callsites} and {@link InliningTreeNode#getName() method names}.
     *
     * @param node1 the first inlining tree node
     * @param node2 the second inlining tree node
     * @return {@code true} iff the nodes are equal
     */
    @Override
    public boolean nodesEqual(InliningTreeNode node1, InliningTreeNode node2) {
        return node1.getBCI() == node2.getBCI() && node1.isPositive() == node2.isPositive() && Objects.equals(node1.getName(), node2.getName());
    }

    /**
     * Gets the cost of relabeling the first node to the second. If the nodes differ only in the
     * positivity of the inlining decision, but they correspond the same method, the
     * {@link #UNIT_COST unit cost} is returned. Otherwise, the cost of relabeling is
     * {@link #INFINITE_COST infinite}.
     *
     * @param node1 the first node
     * @param node2 the second node
     * @return the unit cost if nodes differ only in positivity, infinity otherwise
     */
    @Override
    public long relabelCost(InliningTreeNode node1, InliningTreeNode node2) {
        assert node1 != node2;
        if (node1.getBCI() == node2.getBCI() && Objects.equals(node1.getName(), node2.getName())) {
            return UNIT_COST;
        }
        return INFINITE_COST;
    }
}
