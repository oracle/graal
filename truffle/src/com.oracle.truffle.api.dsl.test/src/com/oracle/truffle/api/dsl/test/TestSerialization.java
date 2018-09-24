/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
