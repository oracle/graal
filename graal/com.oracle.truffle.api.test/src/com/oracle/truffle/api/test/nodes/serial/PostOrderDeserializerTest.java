/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.test.nodes.serial;

import java.nio.*;
import java.util.*;

import org.junit.*;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.serial.*;
import com.oracle.truffle.api.test.nodes.serial.TestNodes.EmptyNode;
import com.oracle.truffle.api.test.nodes.serial.TestNodes.NodeWithArray;
import com.oracle.truffle.api.test.nodes.serial.TestNodes.NodeWithFields;
import com.oracle.truffle.api.test.nodes.serial.TestNodes.NodeWithOneChild;
import com.oracle.truffle.api.test.nodes.serial.TestNodes.NodeWithThreeChilds;
import com.oracle.truffle.api.test.nodes.serial.TestNodes.NodeWithTwoArray;
import com.oracle.truffle.api.test.nodes.serial.TestNodes.NodeWithTwoChilds;
import com.oracle.truffle.api.test.nodes.serial.TestNodes.StringNode;

public class PostOrderDeserializerTest {

    private PostOrderDeserializer d;
    private TestSerializerConstantPool cp;

    @Before
    public void setUp() {
        cp = new TestSerializerConstantPool();
        d = new PostOrderDeserializer(cp);
    }

    @After
    public void tearDown() {
        d = null;
        cp = null;
    }

    private Node deserialize(byte[] bytes) {
        return d.deserialize(bytes, Node.class);
    }

    @Test
    public void testNull() {
        createCP();
        Node ast = deserialize(createBytes(VariableLengthIntBuffer.NULL));
        Assert.assertNull(ast);
    }

    @Test
    public void testSingleNode() {
        createCP(EmptyNode.class);
        Node expectedAst = new EmptyNode();
        Node ast = deserialize(createBytes(0));
        assertAST(expectedAst, ast);
    }

    @Test
    public void testThreeChilds() {
        createCP(EmptyNode.class, NodeWithThreeChilds.class);
        Node expectedAst = new NodeWithThreeChilds(new EmptyNode(), null, new EmptyNode());
        Node ast = deserialize(createBytes(0, VariableLengthIntBuffer.NULL, 0, 1));
        assertAST(expectedAst, ast);
    }

    @Test
    public void testFields() {
        createCP(NodeWithFields.class, "test", Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Float.MIN_VALUE, Float.MAX_VALUE, Double.MIN_VALUE, Double.MAX_VALUE,
                        (int) Character.MIN_VALUE, (int) Character.MAX_VALUE, (int) Short.MIN_VALUE, (int) Short.MAX_VALUE, (int) Byte.MIN_VALUE, (int) Byte.MAX_VALUE, 1);
        NodeWithFields expectedAst = new NodeWithFields("test", Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Float.MIN_VALUE, Float.MAX_VALUE, Double.MIN_VALUE,
                        Double.MAX_VALUE, Character.MIN_VALUE, Character.MAX_VALUE, Short.MIN_VALUE, Short.MAX_VALUE, Byte.MIN_VALUE, Byte.MAX_VALUE, Boolean.TRUE, Boolean.FALSE);
        Node ast = deserialize(createBytes(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 10));
        assertAST(expectedAst, ast);
    }

    @Test
    public void testFieldsNull() {
        createCP(NodeWithFields.class, "test", 0, 0L, 0.0F, 0.0D);
        NodeWithFields expectedAst = new NodeWithFields("test", 0, null, 0L, null, 0f, null, 0d, null, (char) 0, null, (short) 0, null, (byte) 0, null, false, null);
        int nil = VariableLengthIntBuffer.NULL;
        Node ast = deserialize(createBytes(0, 1, 2, nil, 3, nil, 4, nil, 5, nil, 2, nil, 2, nil, 2, nil, 2, nil));
        assertAST(expectedAst, ast);
    }

    @Test
    public void testNullChilds() {
        createCP(Node[].class, NodeWithArray.class);
        Node expectedAst = new NodeWithArray(null);
        Node ast = deserialize(createBytes(0, VariableLengthIntBuffer.NULL, 1));
        assertAST(expectedAst, ast);
    }

    @Test
    public void testNChilds() {
        Node expectedAst = new NodeWithArray(new Node[]{new EmptyNode(), new NodeWithArray(new Node[]{new EmptyNode(), new EmptyNode(), new EmptyNode()}), new EmptyNode(), new EmptyNode()});
        createCP(Node[].class, 4, EmptyNode.class, 3, NodeWithArray.class);
        Node ast = deserialize(createBytes(0, 1, 2, 0, 3, 2, 2, 2, 4, 2, 2, 4));

        assertAST(expectedAst, ast);
    }

    @Test
    public void test2xNChilds() {
        Node expectedAst = new NodeWithTwoArray(new Node[]{new StringNode("a0"), new StringNode("a1")}, new Node[]{new StringNode("b0"), new StringNode("b1"), new StringNode("b2")});
        createCP(Node[].class, 2, StringNode.class, "a0", "a1", 3, "b0", "b1", "b2", NodeWithTwoArray.class);
        Node ast = deserialize(createBytes(0, 1, 2, 3, 2, 4, 0, 5, 2, 6, 2, 7, 2, 8, 9));

        assertAST(expectedAst, ast);
    }

    @Test
    public void testBug0() {
        Node expectedAst = new NodeWithArray(new Node[]{new NodeWithOneChild(new EmptyNode())});

        createCP(Node[].class, 1, EmptyNode.class, NodeWithOneChild.class, NodeWithArray.class);
        Node ast = deserialize(createBytes(0, 1, 2, 3, 4));
        assertAST(expectedAst, ast);
    }

    @Test
    public void testBug1() {
        Node expectedAst = new NodeWithArray(new Node[]{new NodeWithTwoChilds(new EmptyNode(), new EmptyNode())});

        createCP(Node[].class, 1, EmptyNode.class, NodeWithTwoChilds.class, NodeWithArray.class);
        Node ast = deserialize(createBytes(0, 1, 2, 2, 3, 4));
        assertAST(expectedAst, ast);
    }

    private static void assertAST(Node expectedAst, Node actualAst) {
        if (expectedAst == null) {
            Assert.assertNull(actualAst);
            return;
        }

        Assert.assertNotNull(actualAst);
        // fields are asserted using the corresponding equals implementation
        Assert.assertEquals(expectedAst, actualAst);

        Iterable<Node> expectedChildIterator = expectedAst.getChildren();
        Iterator<Node> actualChildIterator = actualAst.getChildren().iterator();
        for (Node node : expectedChildIterator) {
            Assert.assertTrue(actualChildIterator.hasNext());
            assertAST(node, actualChildIterator.next());
        }
        Assert.assertFalse(actualChildIterator.hasNext());
    }

    private static byte[] createBytes(int... refs) {
        VariableLengthIntBuffer buf = new VariableLengthIntBuffer(ByteBuffer.allocate(512));
        for (int i = 0; i < refs.length; i++) {
            buf.put(refs[i]);
        }
        return buf.getBytes();
    }

    private void createCP(Object... cpData) {
        for (int i = 0; i < cpData.length; i++) {
            Object object = cpData[i];

            cp.putObject(object.getClass(), object);

        }
    }

}
