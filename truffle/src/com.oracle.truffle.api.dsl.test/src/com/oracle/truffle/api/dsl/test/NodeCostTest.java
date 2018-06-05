/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test;

import static com.oracle.truffle.api.dsl.test.TestHelper.executeWith;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.NodeCostTestFactory.CostTestNodeFactory;
import com.oracle.truffle.api.dsl.test.NodeCostTestFactory.CostWithNodeInfoNodeFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

public class NodeCostTest {

    @Test
    public void testNodeCost() {
        TestRootNode<CostTestNode> node = TestHelper.createRoot(CostTestNodeFactory.getInstance());
        assertEquals(NodeCost.UNINITIALIZED, node.getNode().getCost());
        assertEquals(21, executeWith(node, 21));
        assertEquals(NodeCost.MONOMORPHIC, node.getNode().getCost());
        assertEquals(true, executeWith(node, true));
        assertEquals(NodeCost.POLYMORPHIC, node.getNode().getCost());
        assertEquals("s", executeWith(node, "s"));
        assertEquals(NodeCost.MONOMORPHIC, node.getNode().getCost());
    }

    @NodeChild
    abstract static class CostTestNode extends ValueNode {

        @Specialization
        int costInt(int left) {
            return left;
        }

        @Specialization
        boolean costBool(boolean left) {
            return left;
        }

        @Specialization(replaces = {"costInt", "costBool"})
        Object cost(Object left) {
            return left;
        }

    }

    @Test
    public void testNodeCostWithNodeInfo() {
        TestRootNode<CostWithNodeInfoNode> node = TestHelper.createRoot(CostWithNodeInfoNodeFactory.getInstance());
        assertEquals(NodeCost.NONE, node.getNode().getCost());
        assertEquals(21, executeWith(node, 21));
        assertEquals(NodeCost.NONE, node.getNode().getCost());
        assertEquals(true, executeWith(node, true));
        assertEquals(NodeCost.NONE, node.getNode().getCost());
        assertEquals("s", executeWith(node, "s"));
        assertEquals(NodeCost.NONE, node.getNode().getCost());
    }

    @NodeInfo(cost = NodeCost.NONE)
    @NodeChild
    abstract static class CostWithNodeInfoNode extends ValueNode {

        @Specialization
        int costInt(int left) {
            return left;
        }

        @Specialization
        boolean costBool(boolean left) {
            return left;
        }

        @Specialization(replaces = {"costInt", "costBool"})
        Object cost(Object left) {
            return left;
        }

    }

}
