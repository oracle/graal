/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph;

import static org.junit.Assert.*;

import org.junit.Test;

public class NodeTest {

    @Test
    public void testBasics() {

        Graph g1 = new Graph();

        DummyNode n1 = new DummyNode(2, 1, g1);
        DummyNode n2 = new DummyNode(1, 1, g1);
        DummyNode n3 = new DummyNode(0, 0, g1);
        n2.dummySetInput(0, Node.Null);
        n2.dummySetSuccessor(0, n3);

        assertSame(Node.Null, n2.inputs().get(0));
        assertSame(n3, n2.successors().get(0));
        assertEquals(n1.inputs().size(), 2);
        assertEquals(n1.successors().size(), 1);
    }

    @Test
    public void testReplace() {
        Graph g2 = new Graph();

        DummyOp2 o1 = new DummyOp2(Node.Null, Node.Null, g2);
        DummyOp2 o2 = new DummyOp2(o1, Node.Null, g2);
        DummyOp2 o3 = new DummyOp2(o2, Node.Null, g2);
        DummyOp2 o4 = new DummyOp2(Node.Null, Node.Null, g2);

        o2.replace(o4);

        assertFalse(o3.inputs().contains(o2));
        assertTrue(o3.inputs().contains(o4));
    }

    private static class DummyNode extends Node {

        private final int inputCount;
        private final int successorCount;

        public DummyNode(int inputCount, int successorCount, Graph graph) {
            super(inputCount, successorCount, graph);
            this.inputCount = inputCount;
            this.successorCount = successorCount;
        }

        @Override
        protected int inputCount() {
            return super.inputCount() + inputCount;
        }

        @Override
        protected int successorCount() {
            return super.inputCount() + successorCount;
        }

        public void dummySetInput(int idx, Node n) {
            inputs().set(idx, n);
        }

        public void dummySetSuccessor(int idx, Node n) {
            successors().set(idx, n);
        }

        @Override
        public Node copy(Graph into) {
            return new DummyNode(inputCount, successorCount, into);
        }

    }

    public static class DummyOp2 extends Node {

        public static final int SUCCESSOR_COUNT = 0;
        public static final int INPUT_COUNT = 2;
        public static final int INPUT_X = 0;
        public static final int INPUT_Y = 1;

        public DummyOp2(Node x, Node y, Graph graph) {
            this(graph);
            setX(x);
            setY(y);
        }
        public DummyOp2(Graph graph) {
            super(INPUT_COUNT, SUCCESSOR_COUNT, graph);
        }

        @Override
        protected int inputCount() {
            return super.inputCount() + INPUT_COUNT;
        }

        public Node x() {
            return inputs().get(super.inputCount() + INPUT_X);
        }

        public Node y() {
            return inputs().get(super.inputCount() + INPUT_Y);
        }

        public Node setX(Node n) {
            return inputs().set(super.inputCount() + INPUT_X, n);
        }

        public Node setY(Node n) {
            return inputs().set(super.inputCount() + INPUT_Y, n);
        }

        @Override
        public Node copy(Graph into) {
            return new DummyOp2(into);
        }
    }
}
