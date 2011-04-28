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
        DummyNode n1 = new DummyNode(2, 1, null);

        Graph g1 = new Graph();

        DummyNode n2 = new DummyNode(1, 1, g1);
        NullNode null1 = new NullNode(g1);
        DummyNode n3 = new DummyNode(0, 0, g1);
        n2.dummySetInput(0, null1);
        n2.dummySetSuccessor(0, n3);
        
        assertSame(null1, n2.getInput(0));
        assertSame(n3, n2.getSuccessor(0));
        
        for(Node in : n1.getInputs())
            assertNotNull(in);
        for(Node sux : n1.getSuccessors())
            assertNotNull(sux);
        assertEquals(n1.getInputs().size(), 2);
        assertEquals(n1.getSuccessors().size(), 1);
    }
    
    @Test
    public void testReplace() {
        Graph g2 = new Graph();
        
        NullNode null2 = new NullNode(g2);
        NullNode null3 = new NullNode(g2);
        NullNode null4 = new NullNode(g2);
        NullNode null5 = new NullNode(g2);
        
        DummyOp2 o1 = new DummyOp2(null2, null3, g2);
        DummyOp2 o2 = new DummyOp2(o1, null4, g2);
        DummyOp2 o3 = new DummyOp2(o2, null4, g2);
        DummyOp2 o4 = new DummyOp2(null5, null5, g2);
        
        o2.replace(o4);
        
        assertTrue(o1.getUsages().contains(o4));
        assertTrue(null4.getUsages().contains(o4));
        assertFalse(o3.getInputs().contains(o2));
        assertTrue(o3.getInputs().contains(o4));
    }

    private static class DummyNode extends Node {

        public DummyNode(int inputs, int successors, Graph graph) {
            super(inputs, successors, graph);
        }

        public DummyNode(Node[] inputs, Node[] successors, Graph graph) {
            super(inputs, successors, graph);
        }
        
        public void dummySetInput(int idx, Node n) {
            this.setInput(idx, n);
        }
        
        public void dummySetSuccessor(int idx, Node n) {
            this.setSuccessor(idx, n);
        }

        @Override
        public Node cloneNode(Graph into) {
            return new DummyNode(this.getInputs().asArray(), this.getSuccessors().asArray(), into);
        }

    }
    
    private static class DummyOp2 extends Node{

        public DummyOp2(Node x, Node y, Graph graph) {
            super(new Node[] {x, y}, new Node[] {}, graph);
        }
        
        public Node x() {
            return this.getInput(0);
        }

        public Node y() {
            return this.getInput(1);
        }
        
        @Override
        public Node cloneNode(Graph into) {
            return new DummyOp2(x(), y(), into); // this may create a Node which has inputs which do not belong to its graph
        }
        
    }
}
