/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
