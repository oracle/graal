/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.graph.test.graphio.parsing.model;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NAME;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import jdk.graal.compiler.util.EconomicHashSet;

public class GroupTest {
    @Test
    public void testGetChildNodeIds() {
        final Group g = new Group(null);
        final InputGraph graph1 = InputGraph.createTestGraph("1");
        final InputGraph graph2 = InputGraph.createTestGraph("2");
        graph1.addNode(new InputNode(1));
        graph1.addNode(new InputNode(2));
        graph2.addNode(new InputNode(2));
        graph2.addNode(new InputNode(3));
        g.addElement(graph1);
        g.addElement(graph2);
        assertEquals(g.getChildNodeIds(), new EconomicHashSet<>(Arrays.asList(1, 2, 3)));
    }

    @Test
    public void testChildrenCounts() {
        final Group g = new Group(null);
        final Group g2 = new Group(g);
        g2.getProperties().setProperty(PROPNAME_NAME, "a");
        final Group g3 = new Group(g);
        g3.getProperties().setProperty(PROPNAME_NAME, "b");

        final InputGraph graph1 = InputGraph.createTestGraph("1");
        final InputGraph graph2 = InputGraph.createTestGraph("2");

        g.addElement(graph1);
        g.addElement(g2);
        g.addElement(graph2);
        g.addElement(g3);

        assertEquals(4, g.getElements().size());
        assertEquals(2, g.getGraphsCount());
    }
}
