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

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NAME;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_SHORT_NAME;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.*;

import org.netbeans.junit.NbTestCase;

import jdk.graal.compiler.graphio.GraphElements;
import jdk.graal.compiler.graphio.GraphLocations;
import jdk.graal.compiler.graphio.GraphOutput;
import jdk.graal.compiler.graphio.GraphStructure;
import jdk.graal.compiler.graphio.parsing.*;
import jdk.graal.compiler.graphio.parsing.BinaryReader.Method;
import jdk.graal.compiler.graphio.parsing.Builder.Node;
import jdk.graal.compiler.graphio.parsing.model.*;
import jdk.graal.compiler.graphio.parsing.model.Properties;

public class BinaryReaderMockTest extends NbTestCase {

    public BinaryReaderMockTest(String name) {
        super(name);
    }

    public void testNodeEncodingInVersion50() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WritableByteChannel w = Channels.newChannel(out);
        MockGraph graph = new MockGraph();
        MockNodeClass clazz = new MockNodeClass("org.my.pkg.MockNode");
        MockNode node = new MockNode(clazz, 33);
        try (GraphOutput<MockGraph, ?> dump = GraphOutput.newBuilder(new MockStructure()).protocolVersion(5, 0).build(w)) {
            dump.beginGroup(graph, "test1", "t1", null, 97, Collections.singletonMap("node", node));
            dump.endGroup();
        }

        BinarySource source = new BinarySource(null, Channels.newChannel(new ByteArrayInputStream(out.toByteArray())));
        final MockBuilder b = new MockBuilder();
        BinaryReader r = new BinaryReader(source, b);
        GraphDocument doc = r.parse();
        assertNotNull("Something is parsed", doc);
        assertNotNull("Group also parsed", b.group);

        b.assertMethod("test1", "t1", 97);

        Object obj = b.group.getProperties().get("node");
        assertTrue("It is node: " + obj, obj instanceof Node);
        Node readNode = (Node) obj;
        assertEquals(node.id, readNode.id);
    }

    public void testNodeEncodingInVersion50WithMethods() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WritableByteChannel w = Channels.newChannel(out);
        MockGraph graph = new MockGraph();
        MockNodeClass clazz = new MockNodeClass("org.my.pkg.MockNode");
        MockNode node = new MockNode(clazz, 33);
        MockMethod method = new MockMethod("mockMethod");
        MockPosition pos = new MockPosition(node, method, null, 97);
        Map<String, Object> props = new HashMap<>();
        props.put("node", node);
        props.put("location", pos);
        try (
                GraphOutput<MockGraph, MockMethod> dump = GraphOutput.newBuilder(new MockStructure()).
                        elements(new MockElements()).
                        protocolVersion(5, 0).build(w)) {
            dump.beginGroup(graph, "test1", "t1", method, 97, props);
            dump.endGroup();
        }

        BinarySource source = new BinarySource(null, Channels.newChannel(new ByteArrayInputStream(out.toByteArray())));
        final MockBuilder b = new MockBuilder();
        BinaryReader r = new BinaryReader(source, b);
        GraphDocument doc = r.parse();
        assertNotNull("Something is parsed", doc);
        assertNotNull("Group also parsed", b.group);

        b.assertMethod("test1", "t1", 97);

        Object obj = b.group.getProperties().get("node");
        assertTrue("It is node: " + obj, obj instanceof Node);
        Node readNode = (Node) obj;
        assertEquals(node.id, readNode.id);

        obj = b.group.getProperties().get("location");
        assertTrue("It is node: " + obj, obj instanceof LocationStackFrame);
        LocationStackFrame elem = (LocationStackFrame) obj;
        assertEquals("org.graalvm.visualizer.data.serialization.lazy.BinaryReaderMockTest$MockMethod.mockMethod(unknown:97) [bci:97]", elem.toString());
    }

    public void testNodeEncodingInVersion60WithMethods() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WritableByteChannel w = Channels.newChannel(out);
        MockGraph graph = new MockGraph();
        MockNodeClass clazz = new MockNodeClass("org.my.pkg.MockNode");
        MockNode node = new MockNode(clazz, 33);
        MockMethod method = new MockMethod("mockMethod");
        MockPosition pos = new MockPosition(node, method, null, 97);
        Map<String, Object> props = new HashMap<>();
        props.put("node", node);
        props.put("location", pos);
        try (
                GraphOutput<MockGraph, MockMethod> dump = GraphOutput.newBuilder(new MockStructure()).
                        elements(new MockElements()).
                        protocolVersion(6, 0).build(w)) {
            dump.beginGroup(graph, "test1", "t1", method, 97, props);
            dump.endGroup();
        }

        BinarySource source = new BinarySource(null, Channels.newChannel(new ByteArrayInputStream(out.toByteArray())));
        final MockBuilder b = new MockBuilder();
        BinaryReader r = new BinaryReader(source, b);
        GraphDocument doc = r.parse();
        assertNotNull("Something is parsed", doc);
        assertNotNull("Group also parsed", b.group);

        b.assertMethod("test1", "t1", 97);

        Object obj = b.group.getProperties().get("node");
        assertTrue("It is node: " + obj, obj instanceof Node);
        Node readNode = (Node) obj;
        assertEquals(node.id, readNode.id);

        obj = b.group.getProperties().get("location");
        assertTrue("It is node: " + obj, obj instanceof LocationStackFrame);
        LocationStackFrame elem = (LocationStackFrame) obj;
        assertEquals("org.graalvm.visualizer.data.serialization.lazy.BinaryReaderMockTest$MockMethod.mockMethod(unknown:97) [bci:97]", elem.toString());
    }

    public void testNodeEncodingInVersion60WithMethodsAndStrata() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WritableByteChannel w = Channels.newChannel(out);
        MockGraph graph = new MockGraph();
        MockNodeClass clazz = new MockNodeClass("org.my.pkg.MockNode");
        MockNode node = new MockNode(clazz, 33);
        MockMethod method = new MockMethod("mockMethod");
        MockPosition pos = new MockPosition(node, method, null, 3);
        Map<String, Object> props = new HashMap<>();
        props.put("node", node);
        props.put("location", pos);
        try (
                GraphOutput<MockGraph, MockMethod> dump = GraphOutput.newBuilder(new MockStructure()).
                        elementsAndLocations(new MockElements(), new MockLocations()).
                        protocolVersion(6, 0).build(w)) {
            dump.beginGroup(graph, "test1", "t1", method, 97, props);
            dump.endGroup();
        }

        BinarySource source = new BinarySource(null, Channels.newChannel(new ByteArrayInputStream(out.toByteArray())));
        final MockBuilder b = new MockBuilder();
        BinaryReader r = new BinaryReader(source, b);
        GraphDocument doc = r.parse();
        assertNotNull("Something is parsed", doc);
        assertNotNull("Group also parsed", b.group);

        b.assertMethod("test1", "t1", 97);

        Object obj = b.group.getProperties().get("node");
        assertTrue("It is node: " + obj, obj instanceof Node);
        Node readNode = (Node) obj;
        assertEquals(node.id, readNode.id);

        obj = b.group.getProperties().get("location");
        assertTrue("It is node: " + obj, obj instanceof LocationStackFrame);
        LocationStackFrame elem = (LocationStackFrame) obj;
        assertEquals("org.graalvm.visualizer.data.serialization.lazy.BinaryReaderMockTest$MockMethod.mockMethod(wellknown:3)(wellknown:3)(wellknown:3) [bci:3]", elem.toString());
    }

    public void testMixingTwoOutputsInOneStream() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        WritableByteChannel w = Channels.newChannel(out);
        MockGraph graph = new MockGraph();
        MockNodeClass clazz = new MockNodeClass("org.my.pkg.MockNode");
        MockNode node = new MockNode(clazz, 33);
        try (GraphOutput<Void, ?> wrapper = GraphOutput.newBuilder(new VoidStructure()).protocolVersion(5, 0).build(w)) {
            wrapper.beginGroup(null, "wrap1", "w1", null, 37, null);
            try (GraphOutput<MockGraph, ?> dump = GraphOutput.newBuilder(new MockStructure()).build(wrapper)) {
                dump.beginGroup(graph, "test1", "t1", null, 73, Collections.singletonMap("node", node));
                dump.endGroup();
            }
            wrapper.endGroup();
        } catch (java.lang.Error ex) {
            assertTrue(ex.getCause() instanceof java.nio.channels.ClosedChannelException);
            // Graph_IO now closes outer channel when closing inner one.
        }

        BinarySource source = new BinarySource(null, Channels.newChannel(new ByteArrayInputStream(out.toByteArray())));
        final MockBuilder b = new MockBuilder();
        BinaryReader r = new BinaryReader(source, b);
        GraphDocument doc = r.parse();
        assertNotNull("Something is parsed", doc);
        assertNotNull("Group also parsed", b.group);

        b.assertMethod("wrap1", "w1", 37);
        b.assertMethod("test1", "t1", 73);

        Object obj = b.group.getProperties().get("node");
        assertTrue("It is node: " + obj, obj instanceof Node);
        Node readNode = (Node) obj;
        assertEquals(node.id, readNode.id);
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
        public MockNode node(Object obj) {
            if (obj instanceof MockNode) {
                MockNode n = (MockNode) obj;
                return n;
            }
            return null;
        }

        @Override
        public MockNodeClass classForNode(MockNode node) {
            return node.clazz;
        }

        @Override
        public MockNodeClass nodeClass(Object obj) {
            if (obj instanceof MockNodeClass) {
                return (MockNodeClass) obj;
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

    private static final class MockMethod {
        private final String name;

        MockMethod(String name) {
            this.name = name;
        }

    }

    private static final class MockSignature {
        private final String name;

        MockSignature(String name) {
            this.name = name;
        }
    }

    private static final class MockField {

    }

    private static final class MockPosition {

        private final MockNode node;
        private final MockMethod method;
        private final MockPosition caller;
        private final int bci;

        MockPosition(MockNode node, MockMethod method, MockPosition caller, int bci) {
            this.node = node;
            this.method = method;
            this.caller = caller;
            this.bci = bci;
        }
    }

    private static final class MockElements implements GraphElements<MockMethod, MockField, MockSignature, MockPosition> {

        @Override
        public MockMethod method(Object obj) {
            if (obj instanceof MockMethod) {
                return (MockMethod) obj;
            }
            return null;
        }

        @Override
        public byte[] methodCode(MockMethod method) {
            return new byte[0];
        }

        @Override
        public int methodModifiers(MockMethod method) {
            return 0;
        }

        @Override
        public MockSignature methodSignature(MockMethod method) {
            return new MockSignature(method.name);
        }

        @Override
        public String methodName(MockMethod method) {
            return method.name;
        }

        @Override
        public Object methodDeclaringClass(MockMethod method) {
            return method.getClass();
        }

        @Override
        public MockField field(Object object) {
            return null;
        }

        @Override
        public int fieldModifiers(MockField field) {
            return 0;
        }

        @Override
        public String fieldTypeName(MockField field) {
            return null;
        }

        @Override
        public String fieldName(MockField field) {
            return null;
        }

        @Override
        public Object fieldDeclaringClass(MockField field) {
            return null;
        }

        @Override
        public MockSignature signature(Object object) {
            if (object instanceof MockSignature) {
                return (MockSignature) object;
            }
            return null;
        }

        @Override
        public int signatureParameterCount(MockSignature signature) {
            return 0;
        }

        @Override
        public String signatureParameterTypeName(MockSignature signature, int index) {
            return null;
        }

        @Override
        public String signatureReturnTypeName(MockSignature signature) {
            return signature.name;
        }

        @Override
        public MockPosition nodeSourcePosition(Object object) {
            if (object instanceof MockPosition) {
                return (MockPosition) object;
            }
            return null;
        }

        @Override
        public MockMethod nodeSourcePositionMethod(MockPosition pos) {
            return pos.method;
        }

        @Override
        public MockPosition nodeSourcePositionCaller(MockPosition pos) {
            return pos.caller;
        }

        @Override
        public int nodeSourcePositionBCI(MockPosition pos) {
            return pos.bci;
        }

        @Override
        public StackTraceElement methodStackTraceElement(MockMethod method, int bci, MockPosition pos) {
            return new StackTraceElement("unknown", method.name, "unknown", bci);
        }
    }

    private static final class MockLocations implements GraphLocations<MockMethod, MockPosition, MockPosition> {

        @Override
        public Iterable<MockPosition> methodLocation(MockMethod method, int bci, MockPosition pos) {
            return Collections.nCopies(bci, pos);
        }

        @Override
        public String locationLanguage(MockPosition location) {
            return "Jazyk";
        }

        @Override
        public URI locationURI(MockPosition location) throws URISyntaxException {
            return new URI(null, "wellknown", null);
        }

        @Override
        public int locationLineNumber(MockPosition location) {
            return location.bci;
        }

        @Override
        public int locationOffsetStart(MockPosition location) {
            return -1;
        }

        @Override
        public int locationOffsetEnd(MockPosition location) {
            return -1;
        }

    }

    private static final class MockGraph {

    }

    private static final class MockNode {
        final MockNodeClass clazz;
        final int id;

        MockNode(MockNodeClass clazz, int id) {
            this.clazz = clazz;
            this.id = id;
        }
    }

    private static final class MockNodeClass {
        final String name;
        int nameTemplateQueried;

        MockNodeClass(String name) {
            this.name = name;
        }
    }

    private static class MockBuilder implements Builder {
        private final ConstantPool cp;
        Group group;
        final LinkedList<String> methodName = new LinkedList<>();
        final LinkedList<String> methodShortName = new LinkedList<>();
        final LinkedList<Integer> methodBci = new LinkedList<>();

        public MockBuilder() {
            cp = new ConstantPool();
        }

        @Override
        public void startDocumentHeader() {
        }

        @Override
        public void endDocumentHeader() {
        }

        @Override
        public void setModelControl(ModelControl ctrl) {
        }

        @Override
        public void addBlockEdge(int from, int to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addNodeToBlock(int nodeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void end() {
        }

        @Override
        public void endBlock(int id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputGraph endGraph() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void endGroup() {
        }

        @Override
        public void endNode(int nodeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConstantPool getConstantPool() {
            return cp;
        }

        @Override
        public Properties getNodeProperties(int nodeId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPropertySize(int size) {
        }

        @Override
        public void inputEdge(Port p, int from, int to, char num, int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void makeBlockEdges() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void makeGraphEdges() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markGraphDuplicate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resetStreamData() {
            throw new UnsupportedOperationException();
        }

        @Override
        public GraphDocument rootDocument() {
            return new GraphDocument();
        }

        @Override
        public void setGroupName(String name, String shortName) {
            group.getProperties().setProperty(PROPNAME_NAME, name);
            group.getProperties().setProperty(PROPNAME_SHORT_NAME, shortName);
        }

        @Override
        public void setMethod(String name, String shortName, int bci, Method method) {
            this.methodName.add(name);
            this.methodShortName.add(shortName);
            this.methodBci.add(bci);
        }

        public void assertMethod(String name, String shortName, int bci) {
            if (this.methodName.isEmpty()) {
                fail("No more methods when seeking for " + name);
            }
            assertEquals(name, this.methodName.removeFirst());
            assertEquals(shortName, this.methodShortName.removeFirst());
            assertEquals((Integer) bci, this.methodBci.removeFirst());
        }

        @Override
        public void setNodeName(NodeClass nodeClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setNodeProperty(String key, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setProperty(String key, Object value) {
            group.getProperties().setProperty(key, value);
        }

        @Override
        public void start() {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputBlock startBlock(int id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputBlock startBlock(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputGraph startGraph(int dumpId, String format, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void startGraphContents(InputGraph g) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Group startGroup() {
            return group = new Group(null, null);
        }

        @Override
        public void startGroupContent() {
        }

        @Override
        public void startNestedProperty(String propertyKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void startNode(int nodeId, boolean hasPredecessors, NodeClass nodeClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void startRoot() {
        }

        @Override
        public void successorEdge(Port p, int from, int to, char num, int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public NameTranslator prepareNameTranslator() {
            return null;
        }

        @Override
        public void reportLoadingError(String logMessage, List<String> parentNames) {
        }

        @Override
        public void graphContentDigest(byte[] dg) {
        }

    }

    private static final class VoidStructure implements GraphStructure<Void, Void, Void, Void> {

        @Override
        public Void graph(Void currentGraph, Object obj) {
            return null;
        }

        @Override
        public Iterable<? extends Void> nodes(Void graph) {
            return null;
        }

        @Override
        public int nodesCount(Void graph) {
            return 0;
        }

        @Override
        public int nodeId(Void node) {
            return 0;
        }

        @Override
        public boolean nodeHasPredecessor(Void node) {
            return false;
        }

        @Override
        public void nodeProperties(Void graph, Void node, Map<String, ? super Object> properties) {
        }

        @Override
        public Void node(Object obj) {
            return null;
        }

        @Override
        public Void nodeClass(Object obj) {
            return null;
        }

        @Override
        public Void classForNode(Void node) {
            return null;
        }

        @Override
        public String nameTemplate(Void nodeClass) {
            return null;
        }

        @Override
        public Object nodeClassType(Void nodeClass) {
            return null;
        }

        @Override
        public Void portInputs(Void nodeClass) {
            return null;
        }

        @Override
        public Void portOutputs(Void nodeClass) {
            return null;
        }

        @Override
        public int portSize(Void port) {
            return 0;
        }

        @Override
        public boolean edgeDirect(Void port, int index) {
            return false;
        }

        @Override
        public String edgeName(Void port, int index) {
            return null;
        }

        @Override
        public Object edgeType(Void port, int index) {
            return null;
        }

        @Override
        public Collection<? extends Void> edgeNodes(Void graph, Void node, Void port, int index) {
            return null;
        }
    }

}
