/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.nodes;

import static com.oracle.truffle.api.test.OSUtils.toUnixString;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;

public class NodeUtilTest {

    @Test
    public void testRecursiveIterator1() {
        TestRootNode root = new TestRootNode();
        TestNode testNode = new TestNode();
        root.child0 = testNode;
        root.adoptChildren();

        int count = iterate(NodeUtil.makeRecursiveIterator(root));

        assertThat(count, is(2));
        assertThat(root.visited, is(0));
        assertThat(testNode.visited, is(1));
    }

    @Test
    public void testReplaceReplaced() {
        TestRootNode rootNode = new TestRootNode();
        TestNode replacedNode = new TestNode();
        rootNode.child0 = replacedNode;
        rootNode.adoptChildren();
        rootNode.child0 = null;

        TestNode test1 = new TestNode();
        TestNode test11 = new TestNode();
        TestNode test111 = new TestNode();

        test11.child1 = test111;
        test1.child1 = test11;
        replacedNode.replace(test1);

        Assert.assertSame(rootNode, test1.getParent());
        Assert.assertSame(test1, test11.getParent());
        Assert.assertSame(test11, test111.getParent());
    }

    @Test
    public void testForEachChild() {
        TestRootNode root = new TestRootNode();
        TestForEachNode testForEachNode = new TestForEachNode(1);
        root.child0 = testForEachNode;
        TestNode testNode1 = new TestNode();
        testForEachNode.firstChild = testNode1;
        TestNode testNode2 = new TestNode();
        testForEachNode.children[0] = testNode2;
        TestNode testNode3 = new TestNode();
        testForEachNode.lastChild = testNode3;
        root.adoptChildren();

        int[] count = new int[1];
        NodeUtil.forEachChild(root, new NodeVisitor() {
            public boolean visit(Node node) {
                Assert.assertSame(testForEachNode, node);
                count[0]++;
                return true;
            }
        });
        Assert.assertEquals(1, count[0]);

        count[0] = 0;
        NodeUtil.forEachChild(testForEachNode, new NodeVisitor() {
            public boolean visit(Node node) {
                ((VisitableNode) node).visited++;
                count[0]++;
                return true;
            }
        });
        Assert.assertEquals(3, count[0]);
        Assert.assertEquals(1, testNode1.visited);
        Assert.assertEquals(1, testNode2.visited);
        Assert.assertEquals(1, testNode3.visited);
    }

    @Test
    public void testAccept() {
        TestRootNode root = new TestRootNode();
        TestForEachNode testForEachNode = new TestForEachNode(1);
        root.child0 = testForEachNode;
        TestNode testNode1 = new TestNode();
        testForEachNode.firstChild = testNode1;
        TestNode testNode2 = new TestNode();
        testForEachNode.children[0] = testNode2;
        TestNode testNode3 = new TestNode();
        testForEachNode.lastChild = testNode3;
        root.adoptChildren();

        int[] count = new int[1];
        testForEachNode.accept(new NodeVisitor() {
            public boolean visit(Node node) {
                ((VisitableNode) node).visited++;
                count[0]++;
                return true;
            }
        });

        Assert.assertEquals(4, count[0]);
        Assert.assertEquals(1, testForEachNode.visited);
        Assert.assertEquals(1, testNode1.visited);
        Assert.assertEquals(1, testNode2.visited);
        Assert.assertEquals(1, testNode3.visited);
    }

    @Test
    public void testRecursiveIterator() {
        TestRootNode root = new TestRootNode();
        TestForEachNode testForEachNode = new TestForEachNode(1);
        root.child0 = testForEachNode;
        TestNode testNode1 = new TestNode();
        testForEachNode.firstChild = testNode1;
        TestNode testNode2 = new TestNode();
        testForEachNode.children[0] = testNode2;
        TestNode testNode3 = new TestNode();
        testForEachNode.lastChild = testNode3;
        root.adoptChildren();

        int count = 0;
        Iterable<Node> iterable = () -> NodeUtil.makeRecursiveIterator(testForEachNode);
        for (Node node : iterable) {
            ((VisitableNode) node).visited++;
            count++;
        }

        Assert.assertEquals(4, count);
        Assert.assertEquals(1, testForEachNode.visited);
        Assert.assertEquals(1, testNode1.visited);
        Assert.assertEquals(1, testNode2.visited);
        Assert.assertEquals(1, testNode3.visited);
    }

    @Test
    public void testChildren() {
        TestRootNode root = new TestRootNode();
        TestForEachNode testForEachNode = new TestForEachNode(1);
        root.child0 = testForEachNode;
        TestNode testNode1 = new TestNode();
        testForEachNode.firstChild = testNode1;
        TestNode testNode2 = new TestNode();
        testForEachNode.children[0] = testNode2;
        TestNode testNode3 = new TestNode();
        testForEachNode.lastChild = testNode3;

        int count = 0;
        for (Node node : testForEachNode.getChildren()) {
            ((VisitableNode) node).visited++;
            count++;
        }
        Assert.assertEquals(3, count);
        Assert.assertEquals(1, testNode1.visited);
        Assert.assertEquals(1, testNode2.visited);
        Assert.assertEquals(1, testNode3.visited);
    }

    @Test
    public void testChildrenArray() {
        // 2 children in the array
        TestForEachNode test2children = new TestForEachNode(2);
        TestNode both1 = new TestNode();
        TestNode both2 = new TestNode();
        test2children.children[0] = both1;
        test2children.children[1] = both2;

        int count = 0;
        for (Node node : test2children.getChildren()) {
            ((VisitableNode) node).visited++;
            count++;
        }
        Assert.assertEquals(2, count);
        Assert.assertEquals(1, both1.visited);
        Assert.assertEquals(1, both2.visited);

        // First null
        TestNode testChild1 = new TestNode();
        test2children.children[0] = null;
        test2children.children[1] = testChild1;

        count = 0;
        for (Node node : test2children.getChildren()) {
            ((VisitableNode) node).visited++;
            count++;
        }
        Assert.assertEquals(1, count);
        Assert.assertEquals(1, testChild1.visited);

        // Second null
        TestNode testChild2 = new TestNode();
        test2children.children[0] = testChild2;
        test2children.children[1] = null;

        count = 0;
        for (Node node : test2children.getChildren()) {
            ((VisitableNode) node).visited++;
            count++;
        }
        Assert.assertEquals(1, count);
        Assert.assertEquals(1, testChild2.visited);

        // Both null, go to next child
        TestNode otherChild = new TestNode();
        test2children.children[0] = null;
        test2children.children[1] = null;
        test2children.lastChild = otherChild;

        count = 0;
        for (Node node : test2children.getChildren()) {
            ((VisitableNode) node).visited++;
            count++;
        }
        Assert.assertEquals(1, count);
        Assert.assertEquals(1, otherChild.visited);
    }

    @Test
    public void testPrintCompactTree() {
        String testNodeSimpleName = getSimpleName(TestNode.class);
        String testForEachNodeSimpleName = getSimpleName(TestForEachNode.class);

        TestNode test1 = new TestNode();
        test1.child0 = new TestNode();
        test1.child1 = new TestNode();
        String output = NodeUtil.printCompactTreeToString(test1);
        assertEquals("" +
                        "  " + testNodeSimpleName + "\n" +
                        "    child0 = " + testNodeSimpleName + "\n" +
                        "    child1 = " + testNodeSimpleName + "\n", toUnixString(output));

        TestForEachNode test2 = new TestForEachNode(4);
        test2.firstChild = new TestNode();
        test2.children[1] = test1;
        test2.children[3] = new TestNode();
        test2.lastChild = new TestNode();
        output = NodeUtil.printCompactTreeToString(test2);
        assertEquals("" +
                        "  " + testForEachNodeSimpleName + "\n" +
                        "    firstChild = " + testNodeSimpleName + "\n" +
                        "    children[1] = " + testNodeSimpleName + "\n" +
                        "      child0 = " + testNodeSimpleName + "\n" +
                        "      child1 = " + testNodeSimpleName + "\n" +
                        "    children[3] = " + testNodeSimpleName + "\n" +
                        "    lastChild = " + testNodeSimpleName + "\n", toUnixString(output));
        TestBlockResNode block = new TestBlockResNode(new Node[]{new TestNode(), new TestNode()}, new TestNode());
        String testBlockResNodeSimpleName = getSimpleName(TestBlockResNode.class);
        output = NodeUtil.printCompactTreeToString(block);
        assertEquals("" +
                        "  " + testBlockResNodeSimpleName + "\n" +
                        "    children[0] = " + testNodeSimpleName + "\n" +
                        "    children[1] = " + testNodeSimpleName + "\n" +
                        "    resultNode = " + testNodeSimpleName + "\n", toUnixString(output));
        block = new TestBlockResNode(null, new TestNode());
        output = NodeUtil.printCompactTreeToString(block);
        assertEquals("" +
                        "  " + testBlockResNodeSimpleName + "\n" +
                        "    resultNode = " + testNodeSimpleName + "\n", toUnixString(output));
    }

    private static int iterate(Iterator<Node> iterator) {
        int iterationCount = 0;
        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (node == null) {
                continue;
            }
            if (node instanceof TestNode) {
                ((TestNode) node).visited = iterationCount;
            } else if (node instanceof TestRootNode) {
                ((TestRootNode) node).visited = iterationCount;
            } else {
                throw new AssertionError();
            }
            iterationCount++;
        }
        return iterationCount;
    }

    private static String getSimpleName(Class<?> clazz) {
        return clazz.getName().substring(clazz.getName().lastIndexOf('.') + 1);
    }

    private static class VisitableNode extends Node {
        int visited = 0;
    }

    private static class TestNode extends VisitableNode {

        @Child TestNode child0;
        @Child TestNode child1;

        TestNode() {
        }

    }

    private static class TestRootNode extends RootNode {

        @Child Node child0;

        protected int visited;

        TestRootNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }

    }

    private static class TestForEachNode extends VisitableNode {

        @Child private Node nullChild;
        @SuppressWarnings("unused") private String data1;
        @Child private Node firstChild;
        @Children private final Node[] children;
        @SuppressWarnings("unused") private boolean data2;
        @Child private Node lastChild;

        TestForEachNode(int childrenSize) {
            this.children = new Node[childrenSize];
        }

    }

    private static class TestBlockResNode extends VisitableNode {

        private @Children Node[] children;
        private @Child Node resultNode;

        TestBlockResNode(Node[] body, Node resultNode) {
            this.children = body;
            this.resultNode = resultNode;
        }
    }

}
