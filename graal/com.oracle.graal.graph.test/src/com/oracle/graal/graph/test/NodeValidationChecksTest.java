/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph.test;

import org.junit.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;

public class NodeValidationChecksTest {

    @NodeInfo
    static final class TestNode extends Node {
        public static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);

        @Input TestNode input;
        @Successor TestNode successor;

        public TestNode(TestNode input, TestNode successor) {
            super(TYPE);
            this.input = input;
            this.successor = successor;
        }
    }

    @Test
    public void testInputNotAlive() {
        Graph graph = new Graph();
        TestNode node = new TestNode(null, null);
        try {
            graph.add(new TestNode(node, null));
            Assert.fail("Exception expected.");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("Input"));
            Assert.assertTrue(e.getMessage().contains("not alive"));
        }
    }

    @Test
    public void testSuccessorNotAlive() {
        Graph graph = new Graph();
        TestNode node = new TestNode(null, null);
        try {
            graph.add(new TestNode(null, node));
            Assert.fail("Exception expected.");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("Successor"));
            Assert.assertTrue(e.getMessage().contains("not alive"));
        }
    }
}
