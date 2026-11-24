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
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.options.OptionValues;

/// Checks the exhaustion behavior of NodeClass edge-position iterators.
public class EdgesIteratorContractTest extends GraphTest {
    @NodeInfo
    static final class TestNode extends Node {
        static final NodeClass<TestNode> TYPE = NodeClass.create(TestNode.class);

        @Input Node input;

        protected TestNode(Node input) {
            super(TYPE);
            this.input = input;
        }
    }

    private Graph graph;
    private TestNode holder;
    private TestNode input;

    @Before
    public void createGraph() {
        Graal.getRuntime();
        OptionValues options = getOptions();
        graph = new Graph(options, getDebug(options));
        input = graph.add(new TestNode(null));
        holder = graph.add(new TestNode(input));
    }

    @Test
    public void exhaustedInputPositionIteratorThrowsNoSuchElementException() {
        Iterator<Position> iterator = holder.inputPositions().iterator();
        Assert.assertTrue(iterator.hasNext());
        Assert.assertSame(input, iterator.next().get(holder));
        Assert.assertFalse(iterator.hasNext());
        Assert.assertThrows(NoSuchElementException.class, iterator::next);
    }
}
