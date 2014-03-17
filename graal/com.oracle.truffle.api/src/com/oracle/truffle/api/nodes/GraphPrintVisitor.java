/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import java.io.*;
import java.lang.annotation.*;
import java.net.*;
import java.util.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

import org.w3c.dom.*;

import com.oracle.truffle.api.nodes.NodeUtil.NodeClass;
import com.oracle.truffle.api.nodes.NodeUtil.NodeField;
import com.oracle.truffle.api.nodes.NodeUtil.NodeFieldKind;

/**
 * Utility class for creating output for the ideal graph visualizer.
 */
public class GraphPrintVisitor {

    public static final String GraphVisualizerAddress = "127.0.0.1";
    public static final int GraphVisualizerPort = 4444;

    private Document dom;
    private Map<Object, Element> nodeMap;
    private List<Element> edgeList;
    private Map<Object, Element> prevNodeMap;
    private int id;
    private Element graphDocument;
    private Element groupElement;
    private Element graphElement;
    private Element nodesElement;
    private Element edgesElement;

    public GraphPrintVisitor() {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();

            dom = db.newDocument();
        } catch (ParserConfigurationException ex) {
            throw new RuntimeException(ex);
        }

        graphDocument = dom.createElement("graphDocument");
        dom.appendChild(graphDocument);
    }

    public GraphPrintVisitor beginGroup(String groupName) {
        groupElement = dom.createElement("group");
        graphDocument.appendChild(groupElement);
        Element properties = dom.createElement("properties");
        groupElement.appendChild(properties);

        if (!groupName.isEmpty()) {
            // set group name
            Element propName = dom.createElement("p");
            propName.setAttribute("name", "name");
            propName.setTextContent(groupName);
            properties.appendChild(propName);
        }

        // forget old nodes
        nodeMap = prevNodeMap = null;
        edgeList = null;

        return this;
    }

    public GraphPrintVisitor beginGraph(String graphName) {
        if (null == groupElement) {
            beginGroup("");
        } else if (null != prevNodeMap) {
            // TODO: difference (create removeNode,removeEdge elements)
        }

        graphElement = dom.createElement("graph");
        groupElement.appendChild(graphElement);
        Element properties = dom.createElement("properties");
        graphElement.appendChild(properties);
        nodesElement = dom.createElement("nodes");
        graphElement.appendChild(nodesElement);
        edgesElement = dom.createElement("edges");
        graphElement.appendChild(edgesElement);

        // set graph name
        Element propName = dom.createElement("p");
        propName.setAttribute("name", "name");
        propName.setTextContent(graphName);
        properties.appendChild(propName);

        // save old nodes
        prevNodeMap = nodeMap;
        nodeMap = new HashMap<>();
        edgeList = new ArrayList<>();

        return this;
    }

    @Override
    public String toString() {
        if (null != dom) {
            try {
                Transformer tr = TransformerFactory.newInstance().newTransformer();
                tr.setOutputProperty(OutputKeys.INDENT, "yes");
                tr.setOutputProperty(OutputKeys.METHOD, "xml");
                tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

                StringWriter strWriter = new StringWriter();
                tr.transform(new DOMSource(dom), new StreamResult(strWriter));
                return strWriter.toString();
            } catch (TransformerException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    public void printToFile(File f) {
        try {
            Transformer tr = TransformerFactory.newInstance().newTransformer();
            tr.setOutputProperty(OutputKeys.INDENT, "yes");
            tr.setOutputProperty(OutputKeys.METHOD, "xml");
            tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            tr.transform(new DOMSource(dom), new StreamResult(new FileOutputStream(f)));
        } catch (TransformerException | FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void printToSysout() {
        try {
            Transformer tr = TransformerFactory.newInstance().newTransformer();
            tr.setOutputProperty(OutputKeys.INDENT, "yes");
            tr.setOutputProperty(OutputKeys.METHOD, "xml");
            tr.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            tr.transform(new DOMSource(dom), new StreamResult(System.out));
        } catch (TransformerException e) {
            e.printStackTrace();
        }
    }

    public void printToNetwork(boolean ignoreErrors) {
        try {
            Transformer tr = TransformerFactory.newInstance().newTransformer();
            tr.setOutputProperty(OutputKeys.METHOD, "xml");

            Socket socket = new Socket(GraphVisualizerAddress, GraphVisualizerPort);
            BufferedOutputStream stream = new BufferedOutputStream(socket.getOutputStream(), 0x4000);
            tr.transform(new DOMSource(dom), new StreamResult(stream));
        } catch (TransformerException | IOException e) {
            if (!ignoreErrors) {
                e.printStackTrace();
            }
        }
    }

    private String nextId() {
        return String.valueOf(id++);
    }

    private String oldOrNextId(Object node) {
        if (null != prevNodeMap && prevNodeMap.containsKey(node)) {
            Element nodeElem = prevNodeMap.get(node);
            return nodeElem.getAttribute("id");
        } else {
            return nextId();
        }
    }

    protected Element getElementByObject(Object op) {
        return nodeMap.get(op);
    }

    protected void createElementForNode(Object node) {
        boolean exists = nodeMap.containsKey(node);
        if (!exists || NodeUtil.findAnnotation(node.getClass(), GraphDuplicate.class) != null) {
            Element nodeElem = dom.createElement("node");
            nodeElem.setAttribute("id", !exists ? oldOrNextId(node) : nextId());
            nodeMap.put(node, nodeElem);
            Element properties = dom.createElement("properties");
            nodeElem.appendChild(properties);
            nodesElement.appendChild(nodeElem);

            setNodeProperty(node, "name", node.getClass().getSimpleName().replaceFirst("Node$", ""));
            NodeInfo nodeInfo = node.getClass().getAnnotation(NodeInfo.class);
            if (nodeInfo != null) {
                setNodeProperty(node, "cost", nodeInfo.cost());
                if (!nodeInfo.shortName().isEmpty()) {
                    setNodeProperty(node, "shortName", nodeInfo.shortName());
                }
            }
            setNodeProperty(node, "class", node.getClass().getSimpleName());
            if (node instanceof Node) {
                readNodeProperties((Node) node);
                copyDebugProperties((Node) node);
            }
        }
    }

    private Element getPropertyElement(Object node, String propertyName) {
        Element nodeElem = getElementByObject(node);
        Element propertiesElem = (Element) nodeElem.getElementsByTagName("properties").item(0);
        if (propertiesElem == null) {
            return null;
        }

        NodeList propList = propertiesElem.getElementsByTagName("p");
        for (int i = 0; i < propList.getLength(); i++) {
            Element p = (Element) propList.item(i);
            if (propertyName.equals(p.getAttribute("name"))) {
                return p;
            }
        }
        return null;
    }

    protected void setNodeProperty(Object node, String propertyName, Object value) {
        Element nodeElem = getElementByObject(node);
        Element propElem = getPropertyElement(node, propertyName); // if property exists, replace
                                                                   // its value
        if (null == propElem) { // if property doesn't exist, create one
            propElem = dom.createElement("p");
            propElem.setAttribute("name", propertyName);
            nodeElem.getElementsByTagName("properties").item(0).appendChild(propElem);
        }
        propElem.setTextContent(String.valueOf(value));
    }

    private void copyDebugProperties(Node node) {
        Map<String, Object> debugProperties = node.getDebugProperties();
        for (Map.Entry<String, Object> property : debugProperties.entrySet()) {
            setNodeProperty(node, property.getKey(), property.getValue());
        }
    }

    private void readNodeProperties(Node node) {
        NodeField[] fields = NodeClass.get(node.getClass()).getFields();
        for (NodeField field : fields) {
            if (field.getKind() == NodeFieldKind.DATA) {
                String key = field.getName();
                if (getPropertyElement(node, key) == null) {
                    Object value = field.loadValue(node);
                    setNodeProperty(node, key, value);
                }
            }
        }
    }

    protected void connectNodes(Object a, Object b, String label) {
        if (nodeMap.get(a) == null || nodeMap.get(b) == null) {
            return;
        }

        String fromId = nodeMap.get(a).getAttribute("id");
        String toId = nodeMap.get(b).getAttribute("id");

        // count existing to-edges
        int count = 0;
        for (Element e : edgeList) {
            if (e.getAttribute("to").equals(toId)) {
                ++count;
            }
        }

        Element edgeElem = dom.createElement("edge");
        edgeElem.setAttribute("from", fromId);
        edgeElem.setAttribute("to", toId);
        edgeElem.setAttribute("index", String.valueOf(count));
        if (label != null) {
            edgeElem.setAttribute("label", label);
        }
        edgesElement.appendChild(edgeElem);
        edgeList.add(edgeElem);
    }

    public GraphPrintVisitor visit(Object node) {
        if (null == graphElement) {
            beginGraph("truffle tree");
        }

        // if node is visited once again, skip
        if (getElementByObject(node) != null && NodeUtil.findAnnotation(node.getClass(), GraphDuplicate.class) == null) {
            return this;
        }

        // respect node's custom handler
        if (NodeUtil.findAnnotation(node.getClass(), CustomGraphPrintHandler.class) != null) {
            Class<? extends GraphPrintHandler> customHandlerClass = NodeUtil.findAnnotation(node.getClass(), CustomGraphPrintHandler.class).handler();
            try {
                GraphPrintHandler customHandler = customHandlerClass.newInstance();
                customHandler.visit(node, new GraphPrintAdapter());
            } catch (InstantiationException | IllegalAccessException e) {
                assert false : e;
            }
        } else if (NodeUtil.findAnnotation(node.getClass(), NullGraphPrintHandler.class) != null) {
            // ignore
        } else {
            // default handler
            createElementForNode(node);

            if (node instanceof Node) {
                for (Map.Entry<String, Node> child : findNamedNodeChildren((Node) node).entrySet()) {
                    visit(child.getValue());
                    connectNodes(node, child.getValue(), child.getKey());
                }
            }
        }

        return this;
    }

    private static LinkedHashMap<String, Node> findNamedNodeChildren(Node node) {
        LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
        NodeClass nodeClass = NodeClass.get(node.getClass());

        if (node instanceof CallNode) {
            CallNode callNode = ((CallNode) node);
            RootNode inlinedRoot = callNode.getCurrentRootNode();
            if (inlinedRoot != null && callNode.isInlined()) {
                nodes.put("inlinedRoot", inlinedRoot);
            }
        }
        for (NodeField field : nodeClass.getFields()) {
            NodeFieldKind kind = field.getKind();
            if (kind == NodeFieldKind.CHILD || kind == NodeFieldKind.CHILDREN) {
                Object value = field.loadValue(node);
                if (value != null) {
                    if (kind == NodeFieldKind.CHILD) {
                        nodes.put(field.getName(), (Node) value);
                    } else if (kind == NodeFieldKind.CHILDREN) {
                        Object[] children = (Object[]) value;
                        for (int i = 0; i < children.length; i++) {
                            if (children[i] != null) {
                                nodes.put(field.getName() + "[" + i + "]", (Node) children[i]);
                            }
                        }
                    }
                }
            }
        }

        return nodes;
    }

    public class GraphPrintAdapter {

        public void createElementForNode(Object node) {
            GraphPrintVisitor.this.createElementForNode(node);
        }

        public void visit(Object node) {
            GraphPrintVisitor.this.visit(node);
        }

        public void connectNodes(Object node, Object child) {
            GraphPrintVisitor.this.connectNodes(node, child, null);
        }

        public void setNodeProperty(Object node, String propertyName, Object value) {
            GraphPrintVisitor.this.setNodeProperty(node, propertyName, value);
        }
    }

    public interface GraphPrintHandler {

        void visit(Object node, GraphPrintAdapter gPrinter);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface CustomGraphPrintHandler {

        Class<? extends GraphPrintHandler> handler();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface NullGraphPrintHandler {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface GraphDuplicate {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface HiddenField {
    }
}
