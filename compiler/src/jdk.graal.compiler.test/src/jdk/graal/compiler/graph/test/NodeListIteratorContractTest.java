/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.options.OptionValues;

/// Checks that NodeList-backed iterators obey the Iterator exhaustion contract.
public class NodeListIteratorContractTest extends GraphTest {
    @NodeInfo
    static final class TestNode extends Node {
        static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);

        @Input NodeInputList<TestNode> inputs;

        protected TestNode(TestNode... inputs) {
            super(TYPE);
            this.inputs = new NodeInputList<>(this, inputs);
        }
    }

    private Graph graph;
    private TestNode child;

    @Before
    public void createGraph() {
        Graal.getRuntime();
        OptionValues options = getOptions();
        graph = new Graph(options, getDebug(options));
        child = graph.add(new TestNode());
    }

    @Test
    public void emptyListIteratorThrowsNoSuchElementException() {
        TestNode empty = graph.add(new TestNode());
        assertNextAfterExhaustionThrows(empty.inputs.iterator());
    }

    @Test
    public void populatedListIteratorThrowsNoSuchElementExceptionAfterLastElement() {
        TestNode oneElement = graph.add(new TestNode(child));
        Iterator<TestNode> iterator = oneElement.inputs.iterator();
        Assert.assertEquals(child, iterator.next());
        assertNextAfterExhaustionThrows(iterator);
    }

    private static void assertNextAfterExhaustionThrows(Iterator<?> iterator) {
        Assert.assertFalse(iterator.hasNext());
        Assert.assertThrows(NoSuchElementException.class, iterator::next);
    }
}
