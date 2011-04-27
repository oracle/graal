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
    public void testReplace() {
        DummyNode n1 = new DummyNode(2, 1, null);

        Graph g1 = new Graph();

        DummyNode n2 = new DummyNode(1, 1, g1);
        assertNotSame(n1, n2);
    }

    private static class DummyNode extends Node {

        public DummyNode(int inputs, int successors, Graph graph) {
            super(inputs, successors, graph);
        }

        public DummyNode(Node[] inputs, Node[] successors, Graph graph) {
            super(inputs, successors, graph);
        }

        @Override
        public Node cloneNode(Graph into) {
            return new DummyNode(this.getInputs().asArray(), this.getSuccessors().asArray(), into);
        }

    }
}
