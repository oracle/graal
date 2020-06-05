/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.nodes;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.TruffleOptions;

/**
 * Don't use. There is more lightweight replacement - the <code>org.graalvm.graphio</code> API
 * provided as part of the GraalVM compiler project.
 *
 *
 * @since 0.8 or earlier
 * @deprecated This class references XML API which is in its own separate module on JDK9. Such
 *             dependency makes the Truffle API too "heavy weight" and as such it is scheduled for
 *             removal.
 */
@SuppressWarnings("deprecated")
@Deprecated
public class GraphPrintVisitor implements Closeable {
    /** @since 0.8 or earlier */
    public static final String GraphVisualizerAddress = "127.0.0.1";
    /** @since 0.8 or earlier */
    public static final int GraphVisualizerPort = 4444;
    private static final String DEFAULT_GRAPH_NAME = "truffle tree";

    private Map<Object, NodeElement> nodeMap;
    private List<EdgeElement> edgeList;
    private Map<Object, NodeElement> prevNodeMap;
    private int id;
    private Impl xmlstream;
    private OutputStream outputStream;
    private int openGroupCount;
    private int openGraphCount;
    private String currentGraphName;

    private static class NodeElement {
        private final int id;
        private final Map<String, Object> properties;

        NodeElement(int id) {
            super();
            this.id = id;
            this.properties = new LinkedHashMap<>();
        }

        public int getId() {
            return id;
        }

        public Map<String, Object> getProperties() {
            return properties;
        }
    }

    private static class EdgeElement {
        private final NodeElement from;
        private final NodeElement to;
        private final int index;
        private final String label;

        EdgeElement(NodeElement from, NodeElement to, int index, String label) {
            this.from = from;
            this.to = to;
            this.index = index;
            this.label = label;
        }

        public NodeElement getFrom() {
            return from;
        }

        public NodeElement getTo() {
            return to;
        }

        public int getIndex() {
            return index;
        }

        public String getLabel() {
            return label;
        }
    }

    private interface Impl {
        void writeStartDocument();

        void writeEndDocument();

        void writeStartElement(String name);

        void writeEndElement();

        void writeAttribute(String name, String value);

        void writeCharacters(String text);

        void flush();

        void close();
    }

    @SuppressWarnings("all")
    private static class XMLImpl implements Impl {
        // uses fully qualified name to prevent mx to add "require java.xml" when compiling on JDK9
        private static final javax.xml.stream.XMLOutputFactory XML_OUTPUT_FACTORY = javax.xml.stream.XMLOutputFactory.newInstance();
        // uses fully qualified name to prevent mx to add "require java.xml" when compiling on JDK9
        private final javax.xml.stream.XMLStreamWriter xmlstream;

        XMLImpl(OutputStream outputStream) {
            try {
                this.xmlstream = XML_OUTPUT_FACTORY.createXMLStreamWriter(outputStream);
                // uses fully qualified name to prevent mx to add "require java.xml" when compiling
                // on JDK9
            } catch (javax.xml.stream.XMLStreamException | javax.xml.stream.FactoryConfigurationError e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void writeStartDocument() {
            try {
                xmlstream.writeStartDocument();
                // uses fully qualified name to prevent mx to add "require java.xml" when compiling
                // on JDK9
            } catch (javax.xml.stream.XMLStreamException e) {
                throw new RuntimeException(e);
            }
        }

        public void writeEndDocument() {
            try {
                xmlstream.writeEndDocument();
                // uses fully qualified name to prevent mx to add "require java.xml" when compiling
                // on JDK9
            } catch (javax.xml.stream.XMLStreamException e) {
                throw new RuntimeException(e);
            }
        }

        public void writeStartElement(String name) {
            try {
                xmlstream.writeStartElement(name);
                // uses fully qualified name to prevent mx to add "require java.xml" when compiling
                // on JDK9
            } catch (javax.xml.stream.XMLStreamException e) {
                throw new RuntimeException(e);
            }
        }

        public void writeEndElement() {
            try {
                xmlstream.writeEndElement();
                // uses fully qualified name to prevent mx to add "require java.xml" when compiling
                // on JDK9
            } catch (javax.xml.stream.XMLStreamException e) {
                throw new RuntimeException(e);
            }
        }

        public void writeAttribute(String name, String value) {
            try {
                xmlstream.writeAttribute(name, value);
                // uses fully qualified name to prevent mx to add "require java.xml" when compiling
                // on JDK9
            } catch (javax.xml.stream.XMLStreamException e) {
                throw new RuntimeException(e);
            }
        }

        public void writeCharacters(String text) {
            try {
                xmlstream.writeCharacters(text);
                // uses fully qualified name to prevent mx to add "require java.xml" when compiling
                // on JDK9
            } catch (javax.xml.stream.XMLStreamException e) {
                throw new RuntimeException(e);
            }
        }

        public void flush() {
            try {
                xmlstream.flush();
                // uses fully qualified name to prevent mx to add "require java.xml" when compiling
                // on JDK9
            } catch (javax.xml.stream.XMLStreamException e) {
                throw new RuntimeException(e);
            }
        }

        public void close() {
            try {
                xmlstream.close();
                // uses fully qualified name to prevent mx to add "require java.xml" when compiling
                // on JDK9
            } catch (javax.xml.stream.XMLStreamException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** @since 0.8 or earlier */
    public GraphPrintVisitor() {
        this(new ByteArrayOutputStream());
    }

    /** @since 0.8 or earlier */
    public GraphPrintVisitor(OutputStream outputStream) {
        this.outputStream = outputStream;
        this.xmlstream = createImpl(outputStream);
        this.xmlstream.writeStartDocument();
        this.xmlstream.writeStartElement("graphDocument");
    }

    private static Impl createImpl(OutputStream outputStream) {
        return new XMLImpl(outputStream);
    }

    private void ensureOpen() {
        if (xmlstream == null) {
            throw new IllegalStateException("printer is closed");
        }
    }

    /** @since 0.8 or earlier */
    public GraphPrintVisitor beginGroup(String groupName) {
        ensureOpen();
        maybeEndGraph();
        openGroupCount++;
        xmlstream.writeStartElement("group");
        xmlstream.writeStartElement("properties");

        if (!groupName.isEmpty()) {
            // set group name
            xmlstream.writeStartElement("p");
            xmlstream.writeAttribute("name", "name");
            xmlstream.writeCharacters(groupName);
            xmlstream.writeEndElement();
        }

        xmlstream.writeEndElement(); // properties

        // forget old nodes
        prevNodeMap = null;
        nodeMap = new IdentityHashMap<>();
        edgeList = new ArrayList<>();

        return this;
    }

    /** @since 0.8 or earlier */
    public GraphPrintVisitor endGroup() {
        ensureOpen();
        if (openGroupCount <= 0) {
            throw new IllegalArgumentException("no open group");
        }
        maybeEndGraph();
        openGroupCount--;

        xmlstream.writeEndElement(); // group

        return this;
    }

    /** @since 0.8 or earlier */
    public GraphPrintVisitor beginGraph(String graphName) {
        ensureOpen();
        if (openGroupCount == 0) {
            beginGroup(graphName);
        }
        maybeEndGraph();
        openGraphCount++;

        this.currentGraphName = graphName;

        // save old nodes
        prevNodeMap = nodeMap;
        nodeMap = new IdentityHashMap<>();
        edgeList = new ArrayList<>();

        return this;
    }

    private void maybeEndGraph() {
        if (openGraphCount > 0) {
            endGraph();
            assert openGraphCount == 0;
        }
    }

    /** @since 0.8 or earlier */
    public GraphPrintVisitor endGraph() {
        ensureOpen();
        if (openGraphCount <= 0) {
            throw new IllegalArgumentException("no open graph");
        }
        openGraphCount--;

        xmlstream.writeStartElement("graph");

        xmlstream.writeStartElement("properties");

        // set graph name
        xmlstream.writeStartElement("p");
        xmlstream.writeAttribute("name", "name");
        xmlstream.writeCharacters(currentGraphName);
        xmlstream.writeEndElement();

        xmlstream.writeEndElement(); // properties

        xmlstream.writeStartElement("nodes");
        writeNodes();
        xmlstream.writeEndElement(); // nodes

        xmlstream.writeStartElement("edges");
        writeEdges();
        xmlstream.writeEndElement(); // edges

        xmlstream.writeEndElement(); // graph

        xmlstream.flush();

        return this;
    }

    private void writeNodes() {
        for (NodeElement node : nodeMap.values()) {
            xmlstream.writeStartElement("node");
            xmlstream.writeAttribute("id", String.valueOf(node.getId()));

            xmlstream.writeStartElement("properties");
            for (Map.Entry<String, Object> property : node.getProperties().entrySet()) {
                xmlstream.writeStartElement("p");
                xmlstream.writeAttribute("name", property.getKey());
                xmlstream.writeCharacters(safeToString(property.getValue()));
                xmlstream.writeEndElement(); // p
            }
            xmlstream.writeEndElement(); // properties

            xmlstream.writeEndElement(); // node
        }
    }

    private void writeEdges() {
        for (EdgeElement edge : edgeList) {
            xmlstream.writeStartElement("edge");

            xmlstream.writeAttribute("from", String.valueOf(edge.getFrom().getId()));
            xmlstream.writeAttribute("to", String.valueOf(edge.getTo().getId()));
            xmlstream.writeAttribute("index", String.valueOf(edge.getIndex()));
            if (edge.getLabel() != null) {
                xmlstream.writeAttribute("label", edge.getLabel());
            }

            xmlstream.writeEndElement(); // edge
        }
    }

    /** @since 0.8 or earlier */
    @Override
    public String toString() {
        if (outputStream instanceof ByteArrayOutputStream) {
            return new String(((ByteArrayOutputStream) outputStream).toByteArray(), Charset.forName("UTF-8"));
        }
        return super.toString();
    }

    /** @since 0.8 or earlier */
    public void printToFile(File f) {
        close();
        if (outputStream instanceof ByteArrayOutputStream) {
            try (OutputStream os = new FileOutputStream(f)) {
                os.write(((ByteArrayOutputStream) outputStream).toByteArray());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /** @since 0.8 or earlier */
    public void printToSysout() {
        close();
        if (outputStream instanceof ByteArrayOutputStream) {
            PrintStream out = System.out;
            out.println(toString());
        }
    }

    /** @since 0.8 or earlier */
    public void printToNetwork(boolean ignoreErrors) {
        close();
        if (outputStream instanceof ByteArrayOutputStream) {
            try (Socket socket = new Socket(GraphVisualizerAddress, GraphVisualizerPort); BufferedOutputStream os = new BufferedOutputStream(socket.getOutputStream(), 0x4000)) {
                os.write(((ByteArrayOutputStream) outputStream).toByteArray());
            } catch (IOException e) {
                if (!ignoreErrors) {
                    e.printStackTrace();
                }
            }
        }
    }

    /** @since 0.8 or earlier */
    public void close() {
        if (xmlstream == null) {
            return;
        }
        while (openGroupCount > 0) {
            endGroup();
        }
        assert openGraphCount == 0 && openGroupCount == 0;

        xmlstream.writeEndElement(); // graphDocument
        xmlstream.writeEndDocument();
        xmlstream.flush();
        xmlstream.close();
        xmlstream = null;
    }

    private int nextId() {
        return id++;
    }

    private int oldOrNextId(Object node) {
        if (null != prevNodeMap && prevNodeMap.containsKey(node)) {
            NodeElement nodeElem = prevNodeMap.get(node);
            return nodeElem.getId();
        } else {
            return nextId();
        }
    }

    final NodeElement getElementByObject(Object obj) {
        return nodeMap.get(obj);
    }

    final void createElementForNode(Object node) {
        boolean exists = nodeMap.containsKey(node);
        if (!exists) {
            int nodeId = oldOrNextId(node);
            nodeMap.put(node, new NodeElement(nodeId));

            String className = NodeUtil.className(node.getClass());
            setNodeProperty(node, "name", dropNodeSuffix(className));
            NodeInfo nodeInfo = node.getClass().getAnnotation(NodeInfo.class);
            if (nodeInfo != null) {
                setNodeProperty(node, "cost", nodeInfo.cost());
                if (!nodeInfo.shortName().isEmpty()) {
                    setNodeProperty(node, "shortName", nodeInfo.shortName());
                }
            }
            setNodeProperty(node, "class", className);
            if (node instanceof Node) {
                readNodeProperties((Node) node);
                copyDebugProperties((Node) node);
            }
        }
    }

    private static String dropNodeSuffix(String className) {
        return className.replaceFirst("Node$", "");
    }

    final void setNodeProperty(Object node, String propertyName, Object value) {
        NodeElement nodeElem = getElementByObject(node);
        nodeElem.getProperties().put(propertyName, value);
    }

    private void copyDebugProperties(Node node) {
        Map<String, Object> debugProperties = node.getDebugProperties();
        for (Map.Entry<String, Object> property : debugProperties.entrySet()) {
            setNodeProperty(node, property.getKey(), property.getValue());
        }
    }

    private void readNodeProperties(Node node) {
        NodeClass nodeClass = NodeClass.get(node);
        for (Object field : nodeClass.getNodeFieldArray()) {
            if (isDataField(nodeClass, field)) {
                String key = nodeClass.getFieldName(field);
                if (!getElementByObject(node).getProperties().containsKey(key)) {
                    Object value = nodeClass.getFieldValue(field, node);
                    setNodeProperty(node, key, value);
                }
            }
        }
    }

    private static boolean isDataField(NodeClass nodeClass, Object field) {
        return !nodeClass.isChildField(field) && !nodeClass.isChildrenField(field);
    }

    final void connectNodes(Object a, Object b, String label) {
        NodeElement fromNode = getElementByObject(a);
        NodeElement toNode = getElementByObject(b);
        if (fromNode == null || toNode == null) {
            return;
        }

        // count existing to-edges
        int count = 0;
        for (EdgeElement e : edgeList) {
            if (e.getTo() == toNode) {
                ++count;
            }
        }

        edgeList.add(new EdgeElement(fromNode, toNode, count, label));
    }

    /** @since 0.8 or earlier */
    public GraphPrintVisitor visit(Object node) {
        if (openGraphCount == 0) {
            beginGraph(DEFAULT_GRAPH_NAME);
        }

        // if node is visited once again, skip
        if (getElementByObject(node) != null) {
            return this;
        }

        // respect node's custom handler
        if (!TruffleOptions.AOT && NodeUtil.findAnnotation(node.getClass(), CustomGraphPrintHandler.class) != null) {
            visit(node, createGraphPrintHandlerFromClass(NodeUtil.findAnnotation(node.getClass(), CustomGraphPrintHandler.class).handler()));
        } else if (NodeUtil.findAnnotation(node.getClass(), NullGraphPrintHandler.class) != null) {
            // ignore
        } else {
            visit(node, new DefaultGraphPrintHandler());
        }

        return this;
    }

    /** @since 0.8 or earlier */
    public GraphPrintVisitor visit(Object node, GraphPrintHandler handler) {
        if (openGraphCount == 0) {
            beginGraph(DEFAULT_GRAPH_NAME);
        }

        handler.visit(node, new GraphPrintAdapter());

        return this;
    }

    private static GraphPrintHandler createGraphPrintHandlerFromClass(Class<? extends GraphPrintHandler> customHandlerClass) {
        try {
            return customHandlerClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static LinkedHashMap<String, Node> findNamedNodeChildren(Node node) {
        LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
        NodeClass nodeClass = NodeClass.get(node);

        for (Object field : nodeClass.getNodeFieldArray()) {
            if (nodeClass.isChildField(field)) {
                Object value = nodeClass.getFieldObject(field, node);
                if (value != null) {
                    nodes.put(nodeClass.getFieldName(field), (Node) value);
                }
            } else if (nodeClass.isChildrenField(field)) {
                Object value = nodeClass.getFieldObject(field, node);
                if (value != null) {
                    Object[] children = (Object[]) value;
                    for (int i = 0; i < children.length; i++) {
                        if (children[i] != null) {
                            nodes.put(nodeClass.getFieldName(field) + "[" + i + "]", (Node) children[i]);
                        }
                    }
                }
            }
        }

        return nodes;
    }

    private static String safeToString(Object value) {
        try {
            return String.valueOf(value);
        } catch (Throwable ex) {
            return value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value));
        }
    }

    /** @since 0.8 or earlier */
    public class GraphPrintAdapter {
        /**
         * Default constructor.
         *
         * @since 0.8 or earlier
         */
        public GraphPrintAdapter() {
        }

        /** @since 0.8 or earlier */
        public void createElementForNode(Object node) {
            GraphPrintVisitor.this.createElementForNode(node);
        }

        /** @since 0.8 or earlier */
        public void visit(Object node) {
            GraphPrintVisitor.this.visit(node);
        }

        /** @since 0.8 or earlier */
        public void visit(Object node, GraphPrintHandler handler) {
            GraphPrintVisitor.this.visit(node, handler);
        }

        /** @since 0.8 or earlier */
        public void connectNodes(Object node, Object child) {
            GraphPrintVisitor.this.connectNodes(node, child, null);
        }

        /** @since 0.8 or earlier */
        public void connectNodes(Object node, Object child, String label) {
            GraphPrintVisitor.this.connectNodes(node, child, label);
        }

        /** @since 0.8 or earlier */
        public void setNodeProperty(Object node, String propertyName, Object value) {
            GraphPrintVisitor.this.setNodeProperty(node, propertyName, value);
        }

        /** @since 0.8 or earlier */
        public boolean visited(Object node) {
            return GraphPrintVisitor.this.getElementByObject(node) != null;
        }
    }

    /** @since 0.8 or earlier */
    public interface GraphPrintHandler {
        /** @since 0.8 or earlier */
        void visit(Object node, GraphPrintAdapter printer);
    }

    private static final class DefaultGraphPrintHandler implements GraphPrintHandler {
        @SuppressWarnings("all")
        public void visit(Object node, GraphPrintAdapter printer) {
            printer.createElementForNode(node);

            if (node instanceof Node) {
                for (Map.Entry<String, Node> child : findNamedNodeChildren((Node) node).entrySet()) {
                    printer.visit(child.getValue());
                    printer.connectNodes(node, child.getValue(), child.getKey());
                }
            }
        }
    }

    /** @since 0.8 or earlier */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface CustomGraphPrintHandler {
        /** @since 0.8 or earlier */
        Class<? extends GraphPrintHandler> handler();
    }

    /** @since 0.8 or earlier */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface NullGraphPrintHandler {
    }
}
