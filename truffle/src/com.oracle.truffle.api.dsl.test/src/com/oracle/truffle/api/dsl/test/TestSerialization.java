/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.TestSerializationFactory.SerializedNodeFactory;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.TestRootNode;
import com.oracle.truffle.api.dsl.test.TypeSystemTest.ValueNode;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeUtil;

public class TestSerialization {

    @Test
    public void testUpdateRoot() {
        /* Tests the unexpected polymorphic case. */
        TestRootNode<SerializedNode> node = TestHelper.createRoot(SerializedNodeFactory.getInstance());
        assertEquals(true, executeWith(node, true));
        assertEquals(21, executeWith(node, 21));
        assertEquals("s", executeWith(node, "s"));
        assertEquals(3, node.getNode().invocations);
        assertEquals(NodeCost.POLYMORPHIC, node.getNode().getCost());

        TestRootNode<SerializedNode> copiedNode = NodeUtil.cloneNode(node);
        copiedNode.adoptChildren();
        assertTrue(copiedNode != node);
        assertEquals(true, executeWith(copiedNode, true));
        assertEquals(21, executeWith(copiedNode, 21));
        assertEquals("s", executeWith(copiedNode, "s"));
        assertEquals(6, copiedNode.getNode().invocations);
    }

    @NodeChild
    abstract static class SerializedNode extends ValueNode {

        int invocations;

        @Specialization
        int add(int left) {
            invocations++;
            return left;
        }

        @Specialization
        boolean add(boolean left) {
            invocations++;
            return left;
        }

        @Specialization
        String add(String left) {
            invocations++;
            return left;
        }

    }

}
