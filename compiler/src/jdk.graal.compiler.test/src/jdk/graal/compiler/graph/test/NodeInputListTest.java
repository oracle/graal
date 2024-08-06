/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

public class NodeInputListTest extends GraphTest {

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static final class TestNode extends Node {
        public static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);

        @Input NodeInputList<TestNode> inputs;

        protected TestNode(TestNode... nodes) {
            super(TYPE);
            this.inputs = new NodeInputList<>(this, nodes);
        }
    }

    @Test
    public void testRepeatedTrimAndReplace() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));

        TestNode a = graph.add(new TestNode());
        TestNode b = graph.add(new TestNode());
        TestNode c = graph.add(new TestNode());

        TestNode root = graph.add(new TestNode(a, b, c));
        assertContents(root.inputs, a, b, c);

        b.replaceAtUsagesAndDelete(null);
        assertContents(root.inputs, a, null, c);

        root.inputs.trim();
        assertContents(root.inputs, a, c);

        TestNode d = graph.add(new TestNode());
        c.replaceAtUsagesAndDelete(d);
        assertContents(root.inputs, a, d);

        root.inputs.trim();
        assertContents(root.inputs, a, d);
    }

    @Test
    public void testNodeListToString() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));

        TestNode a = graph.add(new TestNode());
        TestNode b = graph.add(new TestNode());
        TestNode c = graph.add(new TestNode());

        TestNode root = graph.add(new TestNode(a, b, c));
        Assert.assertEquals("[0|Test, 1|Test, 2|Test]", root.inputs.toString()); // NodeInputList
        Assert.assertEquals("inputs=[0|Test, 1|Test, 2|Test]", root.inputs().toString()); // NodeIterable
    }

    private static void assertContents(NodeInputList<TestNode> actual, TestNode... expected) {
        Assert.assertEquals("list size", expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals("element " + i, expected[i], actual.get(i));
        }
    }
}
