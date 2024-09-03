/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graph.test;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_UNKNOWN;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_UNKNOWN;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;

public class NodeValidationChecksTest extends GraphTest {

    @NodeInfo(cycles = CYCLES_UNKNOWN, size = SIZE_UNKNOWN)
    static final class TestNode extends Node {
        public static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);

        @Input TestNode input;
        @Successor TestNode successor;

        protected TestNode(TestNode input, TestNode successor) {
            super(TYPE);
            this.input = input;
            this.successor = successor;
        }
    }

    @Test
    public void testInputNotAlive() {
        Graph graph = new Graph(getOptions(), getDebug());
        TestNode node = new TestNode(null, null);
        try {
            graph.add(new TestNode(node, null));
            Assert.fail("Exception expected.");
        } catch (GraalError e) {
            Assert.assertTrue(e.getMessage().contains("input"));
            Assert.assertTrue(e.getMessage().contains("not alive"));
        }
    }

    @Test
    public void testSuccessorNotAlive() {
        Graph graph = new Graph(getOptions(), getDebug());
        TestNode node = new TestNode(null, null);
        try {
            graph.add(new TestNode(null, node));
            Assert.fail("Exception expected.");
        } catch (GraalError e) {
            Assert.assertTrue(e.getMessage().contains("successor"));
            Assert.assertTrue(e.getMessage().contains("not alive"));
        }
    }
}
