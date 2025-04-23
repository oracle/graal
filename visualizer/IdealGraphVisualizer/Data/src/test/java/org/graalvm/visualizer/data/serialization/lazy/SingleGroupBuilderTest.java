/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.data.serialization.lazy;

import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import jdk.graal.compiler.graphio.parsing.model.Property;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author sdedic
 */
public class SingleGroupBuilderTest extends BinaryDataTestBase {

    public SingleGroupBuilderTest(String name) {
        super(name);
    }

    private InputGraph findGraph(String nameprefix) {
        Group g = (Group) checkDocument.getElements().get(0);
        for (FolderElement fe : g.getElements()) {
            if (fe.getName().startsWith(nameprefix)) {
                return (InputGraph) fe;
            }
        }
        fail("Graph does not exist: " + nameprefix);
        return null;
    }

    private InputGraph findNestedGraph(InputGraph g, int nodeId) throws Exception {
        InputNode in = g.getNode(nodeId);
        assertNotNull(in);

        assertNotNull("Subgraphs must not be null", in.getSubgraphs());
        assertFalse("Subgraph must be loaded", in.getSubgraphs().isEmpty());

        InputGraph found = null;
        for (InputGraph sg : in.getSubgraphs()) {
            if (sg.getName().endsWith(": op")) {
                found = sg;
                break;
            }
        }
        assertNotNull(found);
        assertTrue("Lazy loading of nested graphs is not yet supported", found instanceof InputGraph && !(found instanceof LazyGraph));
        return found;
    }

    private void assertNoGraphInNodeProperties(InputNode n, String prefix) throws Exception {
        for (Property p : n.getProperties()) {
            assertFalse(Objects.toString(p.getValue()).startsWith(prefix));
        }
    }

    /**
     * Checks that nested graphs load OK
     *
     * @throws Exception
     */
    public void testNestedGraphLoads() throws Exception {
        loadData("nested2.bgv");
        reader.parse();
        InputGraph g = findGraph("51: After phase");

        findNestedGraph(g, 588);
        assertNoGraphInNodeProperties(g.getNode(588), "Graph 588: op");
    }

    /**
     * Check that nested graphs are loaded OK in lazy graphs
     *
     * @throws Exception
     */
    public void testNestedGraphsLoadAsFullInLazyGraphs() throws Exception {
        loadData("nested2.bgv");
        SingleGroupBuilder.setLargeEntryThreshold(10);
        reader.parse();
        InputGraph g = findGraph("51: After phase");
        findNestedGraph(g, 588);
        assertNoGraphInNodeProperties(g.getNode(588), "Graph 588: op");
    }

    /**
     * Checks loading for 2nd nesting level
     *
     * @throws Exception
     */
    public void testDoubleNestedGraph() throws Exception {
        loadData("nested2.bgv");
        reader.parse();
        InputGraph g = findGraph("51: After phase");
        assertNoGraphInNodeProperties(g.getNode(599), "Graph 599: op");
        InputGraph nested = findNestedGraph(g, 599);
        assertNoGraphInNodeProperties(nested.getNode(0), "Graph 0: op");
        findNestedGraph(nested, 0);
    }

    public void testGraphAndGroupCounts() throws Exception {
        loadData("mega2.bgv");
        reader.parse();

        Deque<Group> toProcess = new ArrayDeque();
        toProcess.addAll((Collection<Group>) checkDocument.getElements());
        int processed = 0;
        while (!toProcess.isEmpty()) {
            Collection<? extends FolderElement> els;

            processed++;
            Group g = toProcess.poll();
            if (g instanceof Group.LazyContent) {
                els = ((Group.LazyContent<List<? extends FolderElement>>) g).completeContents(null).get();
            } else {
                els = g.getElements();
            }
            Collection<? extends FolderElement> foundGraphs = els.stream().filter((e) -> e instanceof InputGraph).collect(Collectors.toList());
            int graphCount = g.getGraphsCount();
            Collection<? extends InputGraph> graphs = g.getGraphs();

            assertEquals(foundGraphs.size(), graphCount);
            assertEquals(foundGraphs, graphs);

            toProcess.addAll((List<Group>) els.stream().filter((e) -> !(e instanceof InputGraph)).collect(Collectors.toList()));
        }
    }

}
