/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.graph.test.graphio;

import java.io.ByteArrayOutputStream;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.graalvm.graphio.GraphOutput;
import org.graalvm.graphio.GraphStructure;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

public final class NodeEncodingTest {

    private ByteArrayOutputStream out;

    @Before
    public void initOutput() {
        out = new ByteArrayOutputStream();
    }

    @Test
    public void version40TheNodeIsntDumpedWithItsID() throws Exception {
        runTheNodeIsntDumpedWithItsID(true);
    }

    @Test
    public void defaultVersionTheNodeIsntDumpedWithItsID() throws Exception {
        runTheNodeIsntDumpedWithItsID(false);
    }

    private void runTheNodeIsntDumpedWithItsID(boolean explicitVersion) throws Exception {
        WritableByteChannel w = Channels.newChannel(out);
        MockGraph graph = new MockGraph();
        MockNodeClass clazz = new MockNodeClass("clazz");
        MockNode node = new MockNode(clazz, 33); // random value otherwise not found in the stream
        try (GraphOutput<MockGraph, ?> dump = explicitVersion ? GraphOutput.newBuilder(new MockStructure()).protocolVersion(4, 0).build(w) : GraphOutput.newBuilder(new MockStructure()).build(w)) {
            dump.beginGroup(graph, "test1", "t1", null, 0, Collections.singletonMap("node", node));
            dump.endGroup();
        }

        assertEquals("Nobody asks for id of a node in version 4.0", 0, node.idTested);
        assertByte(false, 33, out.toByteArray());
        assertEquals("Node class of the node has been requested", 1, node.nodeClassRequested);
        assertEquals("Node class template name stored", 1, clazz.nameTemplateQueried);
    }

    private static void assertByte(boolean shouldBeFound, int value, byte[] arr) {
        boolean found = false;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) {
                found = true;
                break;
            }
        }
        if (shouldBeFound == found) {
            return;
        }
        if (shouldBeFound) {
            fail("Value " + value + " not found in\n" + Arrays.toString(arr));
        } else {
            fail("Value " + value + " surprisingly found in\n" + Arrays.toString(arr));
        }
    }

    private static final class MockStructure implements GraphStructure<MockGraph, MockNode, MockNodeClass, MockNodeClass> {

        @Override
        public MockGraph graph(MockGraph currentGraph, Object obj) {
            return obj instanceof MockGraph ? (MockGraph) obj : null;
        }

        @Override
        public Iterable<? extends MockNode> nodes(MockGraph graph) {
            return Collections.emptyList();
        }

        @Override
        public int nodesCount(MockGraph graph) {
            return 0;
        }

        @Override
        public int nodeId(MockNode node) {
            node.idTested++;
            return node.id;
        }

        @Override
        public boolean nodeHasPredecessor(MockNode node) {
            return false;
        }

        @Override
        public void nodeProperties(MockGraph graph, MockNode node, Map<String, ? super Object> properties) {
        }

        @Override
        public MockNodeClass nodeClass(Object obj) {
            if (obj instanceof MockNodeClass) {
                return (MockNodeClass) obj;
            }
            if (obj instanceof MockNode) {
                MockNode n = (MockNode) obj;
                n.nodeClassRequested++;
                return n.clazz;
            }
            return null;
        }

        @Override
        public String nameTemplate(MockNodeClass nodeClass) {
            nodeClass.nameTemplateQueried++;
            return "";
        }

        @Override
        public Object nodeClassType(MockNodeClass nodeClass) {
            return nodeClass.getClass();
        }

        @Override
        public MockNodeClass portInputs(MockNodeClass nodeClass) {
            return nodeClass;
        }

        @Override
        public MockNodeClass portOutputs(MockNodeClass nodeClass) {
            return nodeClass;
        }

        @Override
        public int portSize(MockNodeClass port) {
            return 0;
        }

        @Override
        public boolean edgeDirect(MockNodeClass port, int index) {
            return false;
        }

        @Override
        public String edgeName(MockNodeClass port, int index) {
            return null;
        }

        @Override
        public Object edgeType(MockNodeClass port, int index) {
            return null;
        }

        @Override
        public Collection<? extends MockNode> edgeNodes(MockGraph graph, MockNode node, MockNodeClass port, int index) {
            return null;
        }
    }

    private static final class MockGraph {

    }

    private static final class MockNode {
        private final MockNodeClass clazz;
        private final int id;
        private int idTested;
        private int nodeClassRequested;

        MockNode(MockNodeClass clazz, int id) {
            this.clazz = clazz;
            this.id = id;
        }
    }

    private static final class MockNodeClass {
        private final String name;
        private int nameTemplateQueried;

        MockNodeClass(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "MockNodeClass{" + "name=" + name + '}';
        }
    }
}
