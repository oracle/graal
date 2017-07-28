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
package org.graalvm.compiler.truffle;

import com.oracle.truffle.api.RootCallTarget;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeClass;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.WritableByteChannel;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import org.graalvm.graphio.ProtocolImpl;
import org.graalvm.compiler.truffle.GraphPrintVisitor.EdgeType;
import org.graalvm.compiler.truffle.GraphPrintVisitor.NodeElement;
import org.graalvm.graphio.GraphStructure;

/**
 * Utility class for creating output for the ideal graph visualizer.
 *
 * @since 0.8 or earlier
 */
final class GraphPrintVisitor extends ProtocolImpl<RootCallTarget, NodeElement, NodeClass, EdgeType, Void, Void, Void, Void, Void>
                implements GraphStructure<RootCallTarget, NodeElement, NodeClass, EdgeType> {
    private Map<Object, NodeElement> nodeMap;
    private List<EdgeElement> edgeList;
    private Map<Object, NodeElement> prevNodeMap;
    private int id;
    private String currentGraphName;

    @Override
    public RootCallTarget graph(RootCallTarget current, Object obj) {
        return obj instanceof RootCallTarget ? (RootCallTarget) obj : null;
    }

    @Override
    public NodeClass nodeClass(Object o) {
        Object obj = o;
        if (obj instanceof NodeElement) {
            obj = ((NodeElement) obj).node;
        }
        if (obj instanceof NodeClass) {
            return (NodeClass) obj;
        }
        if (obj instanceof RootCallTarget) {
            obj = ((RootCallTarget) obj).getRootNode();
        }
        if (obj instanceof Node) {
            Node node = (Node) obj;
            return NodeClass.get(node.getClass());
        }
        return null;
    }

    @Override
    protected Class<?> findJavaClass(NodeClass clazz) {
        return clazz.getType();
    }

    @Override
    public String nameTemplate(NodeClass clazz) {
        return "{p#label}";
    }

    @Override
    public int nodeId(NodeElement n) {
        return n.id;
    }

    @Override
    protected void findExtraNodes(NodeElement node, Collection<? super NodeElement> extraNodes) {
    }

    @Override
    public boolean nodeHasPredecessor(NodeElement node) {
        return false;
    }

    @Override
    public int nodesCount(RootCallTarget info) {
        return nodeMap.size();
    }

    @Override
    public Iterable<NodeElement> nodes(RootCallTarget info) {
        return nodeMap.values();
    }

    @Override
    public void nodeProperties(RootCallTarget info, NodeElement node, Map<String, Object> props) {
        for (Map.Entry<String, Object> entry : node.getProperties().entrySet()) {
            final Object value = entry.getValue();
            props.put(entry.getKey(), Objects.toString(value));
        }
    }

    @Override
    public EdgeType portInputs(NodeClass nodeClass) {
        return EdgeType.PARENT;
    }

    @Override
    public EdgeType portOutputs(NodeClass nodeClass) {
        return EdgeType.CHILDREN;
    }

    @Override
    public int portSize(EdgeType port) {
        return 1;
    }

    @Override
    public boolean edgeDirect(EdgeType port, int index) {
        return false;
    }

    @Override
    public String edgeName(EdgeType edges, int index) {
        return edges == EdgeType.PARENT ? "Parent" : "Children";
    }

    @Override
    public Object edgeType(EdgeType port, int index) {
        return port;
    }

    @Override
    public Collection<? extends NodeElement> edgeNodes(RootCallTarget graph, NodeElement node, EdgeType edges, int index) {
        return edges == EdgeType.PARENT ? Collections.emptyList() : node.out;
    }

    @Override
    protected List<NodeElement> findBlockNodes(RootCallTarget info, Void block) {
        return Collections.emptyList();
    }

    @Override
    protected int findBlockId(Void sux) {
        return -1;
    }

    @Override
    protected List<Void> findBlocks(RootCallTarget graph) {
        return Collections.emptyList();
    }

    @Override
    protected List<Void> findBlockSuccessors(Void block) {
        return Collections.emptyList();
    }

    @Override
    protected String formatTitle(RootCallTarget graph, int ident, String format, Object... args) {
        return String.format(format, args) + " [" + ident + "]";
    }

    @Override
    protected String findJavaTypeName(Object obj) {
        if (obj instanceof Class<?>) {
            return ((Class<?>) obj).getName();
        }
        return null;
    }

    @Override
    protected Void findMethod(Object obj) {
        return null;
    }

    @Override
    protected byte[] findMethodCode(Void method) {
        return null;
    }

    @Override
    protected int findMethodModifiers(Void method) {
        return 0;
    }

    @Override
    protected Void findMethodSignature(Void method) {
        return null;
    }

    @Override
    protected String findMethodName(Void method) {
        return null;
    }

    @Override
    protected Object findMethodDeclaringClass(Void method) {
        return null;
    }

    @Override
    protected int findFieldModifiers(Void field) {
        return 0;
    }

    @Override
    protected String findFieldTypeName(Void field) {
        return null;
    }

    @Override
    protected String findFieldName(Void field) {
        return null;
    }

    @Override
    protected Object findFieldDeclaringClass(Void field) {
        return null;
    }

    @Override
    protected Void findJavaField(Object object) {
        return null;
    }

    @Override
    protected Void findSignature(Object object) {
        return null;
    }

    @Override
    protected int findSignatureParameterCount(Void signature) {
        return 0;
    }

    @Override
    protected String findSignatureParameterTypeName(Void signature, int index) {
        return null;
    }

    @Override
    protected String findSignatureReturnTypeName(Void signature) {
        return null;
    }

    @Override
    protected Void findNodeSourcePosition(Object object) {
        return null;
    }

    @Override
    protected Void findNodeSourcePositionMethod(Void pos) {
        return null;
    }

    @Override
    protected Void findNodeSourcePositionCaller(Void pos) {
        return null;
    }

    @Override
    protected int findNodeSourcePositionBCI(Void pos) {
        return 0;
    }

    @Override
    protected StackTraceElement findMethodStackTraceElement(Void method, int bci, Void pos) {
        return null;
    }

    static class NodeElement {
        final Object node;
        final int id;
        final Map<String, Object> properties;
        final List<NodeElement> in = new ArrayList<>();
        final List<NodeElement> out = new ArrayList<>();

        NodeElement(Object node, int id) {
            this.node = node;
            this.id = id;
            this.properties = new LinkedHashMap<>();
        }

        int getId() {
            return id;
        }

        Map<String, Object> getProperties() {
            return properties;
        }
    }

    static class EdgeElement {
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

        NodeElement getFrom() {
            return from;
        }

        NodeElement getTo() {
            return to;
        }

        int getIndex() {
            return index;
        }

        String getLabel() {
            return label;
        }
    }

    GraphPrintVisitor(WritableByteChannel ch) throws IOException {
        super(ch);
        structure = this;
    }

    /** @since 0.8 or earlier */
    GraphPrintVisitor beginGraph(String graphName) {
        this.currentGraphName = graphName;

        // save old nodes
        prevNodeMap = nodeMap;
        nodeMap = new IdentityHashMap<>();
        edgeList = new ArrayList<>();

        return this;
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

    NodeElement getElementByObject(Object obj) {
        return nodeMap.get(obj);
    }

    void createElementForNode(Object node) {
        boolean exists = nodeMap.containsKey(node);
        if (!exists) {
            int nodeId = !exists ? oldOrNextId(node) : nextId();
            nodeMap.put(node, new NodeElement(node, nodeId));

            String className = className(node.getClass());
            setNodeProperty(node, "label", dropNodeSuffix(className));
            NodeInfo nodeInfo = node.getClass().getAnnotation(NodeInfo.class);
            if (nodeInfo != null) {
                setNodeProperty(node, "cost", nodeInfo.cost());
                if (!nodeInfo.shortName().isEmpty()) {
                    setNodeProperty(node, "shortName", nodeInfo.shortName());
                }
            }
            if (node instanceof Node) {
                readNodeProperties((Node) node);
                copyDebugProperties((Node) node);
            }
        }
    }

    static String className(Class<?> clazz) {
        String name = clazz.getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static String dropNodeSuffix(String className) {
        return className.replaceFirst("Node$", "");
    }

    void setNodeProperty(Object node, String propertyName, Object value) {
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
        for (Object field : findNodeFields(nodeClass)) {
            if (isDataField(nodeClass, field)) {
                String key = findFieldName(nodeClass, field);
                if (!getElementByObject(node).getProperties().containsKey(key)) {
                    Object value = findFieldValue(nodeClass, field, node);
                    setNodeProperty(node, key, value);
                }
            }
        }
    }

    private static boolean isDataField(NodeClass nodeClass, Object field) {
        return !isChildField(nodeClass, field) && !isChildrenField(nodeClass, field);
    }

    void connectNodes(Object a, Object b, String label) {
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

        final EdgeElement element = new EdgeElement(fromNode, toNode, count, label);
        edgeList.add(element);
        fromNode.out.add(toNode);
        toNode.in.add(fromNode);
    }

    /** @since 0.8 or earlier */
    GraphPrintVisitor visit(Object node) throws IOException {
        // if node is visited once again, skip
        if (getElementByObject(node) != null) {
            return this;
        }
        visit(node, new DefaultGraphPrintHandler());
        return this;
    }

    GraphPrintVisitor visit(Object node, GraphPrintHandler handler) throws IOException {
        handler.visit(node, new GraphPrintAdapter());

        if (node instanceof RootCallTarget) {
            print((RootCallTarget) node, null, nextId(), currentGraphName);
        }
        if (node instanceof RootNode) {
            print(((RootNode) node).getCallTarget(), null, nextId(), currentGraphName);
        }

        return this;
    }

    private static LinkedHashMap<String, Node> findNamedNodeChildren(Node node) {
        LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
        NodeClass nodeClass = NodeClass.get(node);

        for (Object field : findNodeFields(nodeClass)) {
            if (isChildField(nodeClass, field)) {
                Object value = findFieldObject(nodeClass, field, node);
                if (value != null) {
                    nodes.put(findFieldName(nodeClass, field), (Node) value);
                }
            } else if (isChildrenField(nodeClass, field)) {
                Object value = findFieldObject(nodeClass, field, node);
                if (value != null) {
                    Object[] children = (Object[]) value;
                    for (int i = 0; i < children.length; i++) {
                        if (children[i] != null) {
                            nodes.put(findFieldName(nodeClass, field) + "[" + i + "]", (Node) children[i]);
                        }
                    }
                }
            }
        }

        return nodes;
    }

    private static Object findFieldValue(NodeClass nodeClass, Object field, Node node) {
        return callOnNodeClass(Object.class, "getFieldValue", nodeClass, field, node);
    }

    private static Iterable<?> findNodeFields(NodeClass nodeClass) {
        return callOnNodeClass(Iterable.class, "getNodeFields", nodeClass);
    }

    private static boolean isChildField(NodeClass nodeClass, Object field) {
        return callOnNodeClass(Boolean.class, "isChildField", nodeClass, field);
    }

    private static boolean isChildrenField(NodeClass nodeClass, Object field) {
        return callOnNodeClass(Boolean.class, "isChildrenField", nodeClass, field);
    }

    private static Object findFieldObject(NodeClass nodeClass, Object field, Node node) {
        return callOnNodeClass(Object.class, "getFieldObject", nodeClass, field, node);
    }

    private static String findFieldName(NodeClass nodeClass, Object field) {
        return callOnNodeClass(String.class, "getFieldName", nodeClass, field);
    }

    private static final Map<String, Method> METHODS = new HashMap<>();

    private static <T> T callOnNodeClass(Class<T> returnType, String name, NodeClass thiz, Object... args) {
        Method m = METHODS.get(name);
        if (m == null) {
            for (Method candidate : NodeClass.class.getDeclaredMethods()) {
                if (candidate.getName().equals(name)) {
                    m = candidate;
                    m.setAccessible(true);
                    break;
                }
            }
            assert m != null;
            METHODS.put(name, m);
        }
        try {
            return returnType.cast(m.invoke(thiz, args));
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** @since 0.8 or earlier */
    class GraphPrintAdapter {
        /**
         * Default constructor.
         *
         * @since 0.8 or earlier
         */
        GraphPrintAdapter() {
        }

        /** @since 0.8 or earlier */
        void createElementForNode(Object node) {
            GraphPrintVisitor.this.createElementForNode(node);
        }

        /** @since 0.8 or earlier */
        void visit(Object node) throws IOException {
            GraphPrintVisitor.this.visit(node);
        }

        /** @since 0.8 or earlier */
        void visit(Object node, GraphPrintHandler handler) throws IOException {
            GraphPrintVisitor.this.visit(node, handler);
        }

        /** @since 0.8 or earlier */
        void connectNodes(Object node, Object child) {
            GraphPrintVisitor.this.connectNodes(node, child, null);
        }

        /** @since 0.8 or earlier */
        void connectNodes(Object node, Object child, String label) {
            GraphPrintVisitor.this.connectNodes(node, child, label);
        }

        /** @since 0.8 or earlier */
        void setNodeProperty(Object node, String propertyName, Object value) {
            GraphPrintVisitor.this.setNodeProperty(node, propertyName, value);
        }

        /** @since 0.8 or earlier */
        boolean visited(Object node) {
            return GraphPrintVisitor.this.getElementByObject(node) != null;
        }
    }

    /** @since 0.8 or earlier */
    interface GraphPrintHandler {
        /** @since 0.8 or earlier */
        void visit(Object node, GraphPrintAdapter printer);
    }

    private static final class DefaultGraphPrintHandler implements GraphPrintHandler {
        @Override
        public void visit(Object node, GraphPrintAdapter printer) {
            printer.createElementForNode(node);

            if (node instanceof Node) {
                for (Map.Entry<String, Node> child : findNamedNodeChildren((Node) node).entrySet()) {
                    try {
                        printer.visit(child.getValue());
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    printer.connectNodes(node, child.getValue(), child.getKey());
                }
            }
        }
    }

    enum EdgeType {
        PARENT,
        CHILDREN;
    }
}
