/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Utility class that manages the special access methods for node instances.
 *
 * @since 0.8 or earlier
 */
public final class NodeUtil {
    /**
     * @deprecated accidentally public - don't use
     * @since 0.8 or earlier
     */
    @Deprecated
    public NodeUtil() {
    }

    static Iterator<Node> makeIterator(Node node) {
        return node.getNodeClass().makeIterator(node);
    }

    /** @since 0.8 or earlier */
    public static Iterator<Node> makeRecursiveIterator(Node node) {
        return new RecursiveNodeIterator(node);
    }

    private static final class RecursiveNodeIterator implements Iterator<Node> {
        private final List<Iterator<Node>> iteratorStack = new ArrayList<>();

        RecursiveNodeIterator(final Node node) {
            iteratorStack.add(new Iterator<Node>() {

                private boolean visited;

                public void remove() {
                    throw new UnsupportedOperationException();
                }

                public Node next() {
                    if (visited) {
                        throw new NoSuchElementException();
                    }
                    visited = true;
                    return node;
                }

                public boolean hasNext() {
                    return !visited;
                }
            });
        }

        public boolean hasNext() {
            return peekIterator() != null;
        }

        public Node next() {
            Iterator<Node> iterator = peekIterator();
            if (iterator == null) {
                throw new NoSuchElementException();
            }

            Node node = iterator.next();
            if (node != null) {
                Iterator<Node> childIterator = makeIterator(node);
                if (childIterator.hasNext()) {
                    iteratorStack.add(childIterator);
                }
            }
            return node;
        }

        private Iterator<Node> peekIterator() {
            int tos = iteratorStack.size() - 1;
            while (tos >= 0) {
                Iterator<Node> iterable = iteratorStack.get(tos);
                if (iterable.hasNext()) {
                    return iterable;
                } else {
                    iteratorStack.remove(tos--);
                }
            }
            return null;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /** @since 0.8 or earlier */
    @SuppressWarnings("unchecked")
    public static <T extends Node> T cloneNode(T orig) {
        return (T) orig.deepCopy();
    }

    static Node deepCopyImpl(Node orig) {
        CompilerAsserts.neverPartOfCompilation("do not call Node.deepCopyImpl from compiled code");
        final Node clone = orig.copy();
        NodeClass nodeClass = clone.getNodeClass();

        clone.setParent(null);

        for (Object field : nodeClass.getNodeFields()) {
            if (nodeClass.isChildField(field)) {
                Node child = (Node) nodeClass.getFieldObject(field, orig);
                if (child != null) {
                    Node clonedChild = child.deepCopy();
                    clonedChild.setParent(clone);
                    nodeClass.putFieldObject(field, clone, clonedChild);
                }
            } else if (nodeClass.isChildrenField(field)) {
                Object[] children = (Object[]) nodeClass.getFieldObject(field, orig);
                if (children != null) {
                    Object[] clonedChildren = (Object[]) Array.newInstance(children.getClass().getComponentType(), children.length);
                    for (int i = 0; i < children.length; i++) {
                        if (children[i] != null) {
                            Node clonedChild = ((Node) children[i]).deepCopy();
                            clonedChild.setParent(clone);
                            clonedChildren[i] = clonedChild;
                        }
                    }
                    nodeClass.putFieldObject(field, clone, clonedChildren);
                }
            } else if (nodeClass.isCloneableField(field)) {
                Object cloneable = nodeClass.getFieldObject(field, clone);
                if (cloneable != null && cloneable == nodeClass.getFieldObject(field, orig)) {
                    nodeClass.putFieldObject(field, clone, ((NodeCloneable) cloneable).clone());
                }
            } else if (nodeClass.nodeFieldsOrderedByKind()) {
                break;
            }
        }
        return clone;
    }

    /** @since 0.8 or earlier */
    public static List<Node> findNodeChildren(Node node) {
        CompilerAsserts.neverPartOfCompilation("do not call Node.findNodeChildren from compiled code");
        List<Node> nodes = new ArrayList<>();
        NodeClass nodeClass = node.getNodeClass();

        for (Object nodeField : nodeClass.getNodeFields()) {
            if (nodeClass.isChildField(nodeField)) {
                Object child = nodeClass.getFieldObject(nodeField, node);
                if (child != null) {
                    nodes.add((Node) child);
                }
            } else if (nodeClass.isChildrenField(nodeField)) {
                Object[] children = (Object[]) nodeClass.getFieldObject(nodeField, node);
                if (children != null) {
                    for (Object child : children) {
                        if (child != null) {
                            nodes.add((Node) child);
                        }
                    }
                }
            } else if (nodeClass.nodeFieldsOrderedByKind()) {
                break;
            }
        }
        return nodes;
    }

    /** @since 0.8 or earlier */
    public static <T extends Node> T nonAtomicReplace(Node oldNode, T newNode, CharSequence reason) {
        oldNode.replaceHelper(newNode, reason);
        return newNode;
    }

    /** @since 0.8 or earlier */
    public static boolean replaceChild(Node parent, Node oldChild, Node newChild) {
        return replaceChild(parent, oldChild, newChild, false);
    }

    /*
     * Fast version of child adoption.
     */
    static void adoptChildrenHelper(Node currentNode) {
        NodeClass clazz = currentNode.getNodeClass();
        for (Object field : clazz.getNodeFields()) {
            if (clazz.isChildField(field)) {
                Object child = clazz.getFieldObject(field, currentNode);
                if (child != null) {
                    Node node = (Node) child;
                    if (node.getParent() != currentNode) {
                        currentNode.adoptHelper(node);
                    }
                }
            } else if (clazz.isChildrenField(field)) {
                Object arrayObject = clazz.getFieldObject(field, currentNode);
                if (arrayObject == null) {
                    continue;
                }
                Object[] array = (Object[]) arrayObject;
                for (int i = 0; i < array.length; i++) {
                    Object child = array[i];
                    if (child != null) {
                        Node node = (Node) child;
                        if (node.getParent() != currentNode) {
                            currentNode.adoptHelper(node);
                        }
                    }
                }
            } else if (clazz.nodeFieldsOrderedByKind()) {
                break;
            }
        }

    }

    /*
     * Slow version of child adoption. Unlike the adoptChildrenHelper this method traverses (and
     * counts) all nodes, i.e. including the ones already adopted.
     */
    static int adoptChildrenAndCountHelper(Node currentNode) {
        int count = 0;
        NodeClass clazz = currentNode.getNodeClass();
        for (Object field : clazz.getNodeFields()) {
            if (clazz.isChildField(field)) {
                Object child = clazz.getFieldObject(field, currentNode);
                if (child != null) {
                    Node node = (Node) child;
                    count += currentNode.adoptAndCountHelper(node);
                }
            } else if (clazz.isChildrenField(field)) {
                Object arrayObject = clazz.getFieldObject(field, currentNode);
                if (arrayObject == null) {
                    continue;
                }
                Object[] array = (Object[]) arrayObject;
                for (int i = 0; i < array.length; i++) {
                    Object child = array[i];
                    if (child != null) {
                        Node node = (Node) child;
                        count += currentNode.adoptAndCountHelper(node);
                    }
                }
            } else if (clazz.nodeFieldsOrderedByKind()) {
                break;
            }
        }
        return count;
    }

    static boolean replaceChild(Node parent, Node oldChild, Node newChild, boolean adopt) {
        CompilerAsserts.neverPartOfCompilation("do not replace Node child from compiled code");
        NodeClass nodeClass = parent.getNodeClass();

        for (Object nodeField : nodeClass.getNodeFields()) {
            if (nodeClass.isChildField(nodeField)) {
                if (nodeClass.getFieldObject(nodeField, parent) == oldChild) {
                    if (adopt) {
                        parent.adoptHelper(newChild);
                    }
                    nodeClass.putFieldObject(nodeField, parent, newChild);
                    return true;
                }
            } else if (nodeClass.isChildrenField(nodeField)) {
                Object arrayObject = nodeClass.getFieldObject(nodeField, parent);
                if (arrayObject != null) {
                    Object[] array = (Object[]) arrayObject;
                    for (int i = 0; i < array.length; i++) {
                        if (array[i] == oldChild) {
                            if (adopt) {
                                parent.adoptHelper(newChild);
                            }
                            try {
                                array[i] = newChild;
                            } catch (ArrayStoreException e) {
                                throw replaceChildIllegalArgumentException(nodeField, array.getClass(), newChild);
                            }
                            return true;
                        }
                    }
                }
            } else if (nodeClass.nodeFieldsOrderedByKind()) {
                break;
            }
        }

        return false;
    }

    private static IllegalArgumentException replaceChildIllegalArgumentException(Object nodeField, Class<?> fieldType, Node newChild) {
        return new IllegalArgumentException("Cannot set element of " + fieldType.getName() + " field " + nodeField + " to " + (newChild == null ? "null" : newChild.getClass().getName()));
    }

    /**
     * Finds the field in a parent node, if any, that holds a specified child node.
     *
     * @return the field (possibly an array) holding the child, {@code null} if not found.
     * @since 0.8 or earlier
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public static NodeFieldAccessor findChildField(Node parent, Node child) {
        assert child != null;
        NodeClass parentNodeClass = parent.getNodeClass();

        for (NodeFieldAccessor field : parentNodeClass.getFields()) {
            if (field.getKind() == NodeFieldAccessor.NodeFieldKind.CHILD) {
                if (field.getObject(parent) == child) {
                    return field;
                }
            } else if (field.getKind() == NodeFieldAccessor.NodeFieldKind.CHILDREN) {
                Object arrayObject = field.getObject(parent);
                if (arrayObject != null) {
                    Object[] array = (Object[]) arrayObject;
                    for (int i = 0; i < array.length; i++) {
                        if (array[i] == child) {
                            return field;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Determines whether a proposed child replacement would be safe: structurally and type.
     *
     * @since 0.8 or earlier
     */
    public static boolean isReplacementSafe(Node parent, Node oldChild, Node newChild) {
        Objects.requireNonNull(oldChild);
        if (parent != null) {
            NodeClass nodeClass = parent.getNodeClass();
            for (Object field : nodeClass.getNodeFields()) {
                if (nodeClass.isChildField(field)) {
                    if (nodeClass.getFieldObject(field, parent) == oldChild) {
                        return nodeClass.getFieldType(field).isAssignableFrom(newChild.getClass());
                    }
                } else if (nodeClass.isChildrenField(field)) {
                    Object arrayObject = nodeClass.getFieldObject(field, parent);
                    if (arrayObject != null) {
                        Object[] array = (Object[]) arrayObject;
                        for (int i = 0; i < array.length; i++) {
                            if (array[i] == oldChild) {
                                return nodeClass.getFieldType(field).getComponentType().isAssignableFrom(newChild.getClass());
                            }
                        }
                    }
                } else if (nodeClass.nodeFieldsOrderedByKind()) {
                    break;
                }
            }
        }
        // if a child was not found the replacement can be considered safe.
        return true;
    }

    /**
     * Executes a closure for every non-null child of the parent node.
     *
     * @return {@code true} if all children were visited, {@code false} otherwise
     * @since 0.8 or earlier
     */
    public static boolean forEachChild(Node parent, NodeVisitor visitor) {
        CompilerAsserts.neverPartOfCompilation("do not iterate over Node children from compiled code");
        Objects.requireNonNull(visitor);
        NodeClass nodeClass = parent.getNodeClass();

        for (Object field : nodeClass.getNodeFields()) {
            if (nodeClass.isChildField(field)) {
                Object child = nodeClass.getFieldObject(field, parent);
                if (child != null) {
                    if (!visitor.visit((Node) child)) {
                        return false;
                    }
                }
            } else if (nodeClass.isChildrenField(field)) {
                Object arrayObject = nodeClass.getFieldObject(field, parent);
                if (arrayObject != null) {
                    Object[] array = (Object[]) arrayObject;
                    for (int i = 0; i < array.length; i++) {
                        Object child = array[i];
                        if (child != null) {
                            if (!visitor.visit((Node) child)) {
                                return false;
                            }
                        }
                    }
                }
            } else if (nodeClass.nodeFieldsOrderedByKind()) {
                break;
            }
        }

        return true;
    }

    static boolean forEachChildRecursive(Node parent, NodeVisitor visitor) {
        NodeClass nodeClass = parent.getNodeClass();

        for (Object field : nodeClass.getNodeFields()) {
            if (nodeClass.isChildField(field)) {
                if (!visitChild((Node) nodeClass.getFieldObject(field, parent), visitor)) {
                    return false;
                }
            } else if (nodeClass.isChildrenField(field)) {
                Object arrayObject = nodeClass.getFieldObject(field, parent);
                if (arrayObject == null) {
                    continue;
                }
                Object[] array = (Object[]) arrayObject;
                for (int i = 0; i < array.length; i++) {
                    if (!visitChild((Node) array[i], visitor)) {
                        return false;
                    }
                }
            } else if (nodeClass.nodeFieldsOrderedByKind()) {
                break;
            }
        }

        return true;
    }

    private static boolean visitChild(Node child, NodeVisitor visitor) {
        if (child == null) {
            return true;
        }
        if (!visitor.visit(child)) {
            return false;
        }
        if (!forEachChildRecursive(child, visitor)) {
            return false;
        }
        return true;
    }

    /** @since 0.8 or earlier */
    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    /**
     * Get the nth parent of a node, where the 0th parent is the node itself. Returns null if there
     * are less than n ancestors.
     *
     * @since 0.8 or earlier
     */
    public static Node getNthParent(Node node, int n) {
        Node parent = node;

        for (int i = 0; i < n; i++) {
            parent = parent.getParent();

            if (parent == null) {
                return null;
            }
        }

        return parent;
    }

    /**
     * Find annotation in class/interface hierarchy.
     *
     * @since 0.8 or earlier
     */
    public static <T extends Annotation> T findAnnotation(Class<?> clazz, Class<T> annotationClass) {
        if (clazz.getAnnotation(annotationClass) != null) {
            return clazz.getAnnotation(annotationClass);
        } else {
            if (!TruffleOptions.AOT) {
                for (Class<?> intf : clazz.getInterfaces()) {
                    if (intf.getAnnotation(annotationClass) != null) {
                        return intf.getAnnotation(annotationClass);
                    }
                }
            }
            if (clazz.getSuperclass() != null) {
                return findAnnotation(clazz.getSuperclass(), annotationClass);
            }
        }
        return null;
    }

    /** @since 0.8 or earlier */
    public static <T> T findParent(Node start, Class<T> clazz) {
        Node parent = start.getParent();
        if (parent == null) {
            return null;
        } else if (clazz.isInstance(parent)) {
            return clazz.cast(parent);
        } else {
            return findParent(parent, clazz);
        }
    }

    /** @since 0.8 or earlier */
    public static <T> List<T> findAllParents(Node start, Class<T> clazz) {
        List<T> parents = new ArrayList<>();
        T parent = findParent(start, clazz);
        while (parent != null) {
            parents.add(parent);
            parent = findParent((Node) parent, clazz);
        }
        return parents;
    }

    /** @since 0.8 or earlier */
    public static List<Node> collectNodes(Node parent, Node child) {
        List<Node> nodes = new ArrayList<>();
        Node current = child;
        while (current != null) {
            nodes.add(current);
            if (current == parent) {
                return nodes;
            }
            current = current.getParent();
        }
        throw new IllegalArgumentException("Node " + parent + " is not a parent of " + child + ".");
    }

    /** @since 0.8 or earlier */
    public static <T> T findFirstNodeInstance(Node root, Class<T> clazz) {
        if (clazz.isInstance(root)) {
            return clazz.cast(root);
        }
        for (Node child : root.getChildren()) {
            T node = findFirstNodeInstance(child, clazz);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /** @since 0.8 or earlier */
    public static <T> List<T> findAllNodeInstances(final Node root, final Class<T> clazz) {
        final List<T> nodeList = new ArrayList<>();
        root.accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (clazz.isInstance(node)) {
                    nodeList.add(clazz.cast(node));
                }
                return true;
            }
        });
        return nodeList;
    }

    /** @since 0.8 or earlier */
    public static int countNodes(Node root) {
        return countNodes(root, NodeCountFilter.NO_FILTER);
    }

    /** @since 0.8 or earlier */
    public static int countNodes(Node root, NodeCountFilter filter) {
        NodeCounter counter = new NodeCounter(filter);
        root.accept(counter);
        return counter.count;
    }

    /** @since 0.8 or earlier */
    public interface NodeCountFilter {
        /** @since 0.8 or earlier */
        NodeCountFilter NO_FILTER = new NodeCountFilter() {

            public boolean isCounted(Node node) {
                return true;
            }
        };

        /** @since 0.8 or earlier */
        boolean isCounted(Node node);

    }

    /** @since 0.8 or earlier */
    public static String printCompactTreeToString(Node node) {
        StringWriter out = new StringWriter();
        printCompactTree(new PrintWriter(out), null, node, 1);
        return out.toString();
    }

    /** @since 0.8 or earlier */
    public static void printCompactTree(OutputStream out, Node node) {
        printCompactTree(new PrintWriter(out), null, node, 1);
    }

    private static void printCompactTree(PrintWriter p, Node parent, Node node, int level) {
        if (node == null) {
            return;
        }
        for (int i = 0; i < level; i++) {
            p.print("  ");
        }
        if (parent == null) {
            p.println(nodeName(node));
        } else {
            p.print(getNodeFieldName(parent, node, "unknownField"));
            p.print(" = ");
            p.println(nodeName(node));
        }

        for (Node child : node.getChildren()) {
            printCompactTree(p, node, child, level + 1);
        }
        p.flush();
    }

    /** @since 0.8 or earlier */
    public static String printSourceAttributionTree(Node node) {
        StringWriter out = new StringWriter();
        printSourceAttributionTree(new PrintWriter(out), null, node, 1);
        return out.toString();
    }

    /** @since 0.8 or earlier */
    public static void printSourceAttributionTree(OutputStream out, Node node) {
        printSourceAttributionTree(new PrintWriter(out), null, node, 1);
    }

    /** @since 0.8 or earlier */
    public static void printSourceAttributionTree(PrintWriter out, Node node) {
        printSourceAttributionTree(out, null, node, 1);
    }

    private static void printSourceAttributionTree(PrintWriter p, Node parent, Node node, int level) {
        if (node == null) {
            return;
        }
        if (parent == null) {
            // Add some preliminary information before starting with the root node
            final SourceSection sourceSection = node.getSourceSection();
            if (sourceSection != null) {
                final String txt = sourceSection.getSource().getCharacters().toString();
                p.println("Full source len=(" + txt.length() + ")  ___" + txt + "___");
                p.println("AST source attribution:");
            }
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("| ");
        }

        if (parent != null) {
            sb.append(getNodeFieldName(parent, node, ""));
        }

        sb.append("  (" + node.getClass().getSimpleName() + ")  ");

        sb.append(printSyntaxTags(node));

        sb.append(displaySourceAttribution(node));
        p.println(sb.toString());

        for (Node child : node.getChildren()) {
            printSourceAttributionTree(p, node, child, level + 1);
        }
        p.flush();
    }

    private static String getNodeFieldName(Node parent, Node node, String defaultName) {
        NodeClass nodeClass = parent.getNodeClass();
        for (Object field : nodeClass.getNodeFields()) {
            if (nodeClass.isChildField(field)) {
                if (nodeClass.getFieldObject(field, parent) == node) {
                    return nodeClass.getFieldName(field);
                }
            } else if (nodeClass.isChildrenField(field)) {
                int index = 0;
                for (Object arrayNode : (Object[]) nodeClass.getFieldObject(field, parent)) {
                    if (arrayNode == node) {
                        return nodeClass.getFieldName(field) + "[" + index + "]";
                    }
                    index++;
                }
            } else if (nodeClass.nodeFieldsOrderedByKind()) {
                break;
            }
        }
        return defaultName;
    }

    /**
     * Originally returned the <em>tags</em> if any, associated with a node; now unsupported.
     *
     * @since 0.8 or earlier
     */
    public static String printSyntaxTags(final Object node) {
        if ((node instanceof Node) && ((Node) node).getSourceSection() != null) {
            return ((Node) node).getSourceSection().toString();
        }
        return "";
    }

    /**
     * Prints a human readable form of a {@link Node} AST to the given {@link PrintStream}. This
     * print method does not check for cycles in the node structure.
     *
     * @param out the stream to print to.
     * @param node the root node to write
     * @since 0.8 or earlier
     */
    public static void printTree(OutputStream out, Node node) {
        printTree(new PrintWriter(out), node);
    }

    /** @since 0.8 or earlier */
    public static String printTreeToString(Node node) {
        StringWriter out = new StringWriter();
        printTree(new PrintWriter(out), node);
        return out.toString();
    }

    /** @since 0.8 or earlier */
    public static void printTree(PrintWriter p, Node node) {
        printTree(p, node, 1);
        p.println();
        p.flush();
    }

    private static void printTree(PrintWriter p, Node node, int level) {
        if (node == null) {
            p.print("null");
            return;
        }

        p.print(nodeName(node));

        ArrayList<Object> childFields = new ArrayList<>();
        String sep = "";
        p.print("(");
        NodeClass nodeClass = NodeClass.get(node);
        for (Object field : nodeClass.getNodeFields()) {
            if (nodeClass.isChildField(field) || nodeClass.isChildrenField(field)) {
                childFields.add(field);
            } else {
                p.print(sep);
                sep = ", ";

                p.print(nodeClass.getFieldName(field));
                p.print(" = ");
                p.print(nodeClass.getFieldValue(field, node));
            }
        }
        p.print(")");

        if (childFields.size() != 0) {
            p.print(" {");
            for (Object field : nodeClass.getNodeFields()) {
                printNewLine(p, level);
                p.print(nodeClass.getFieldName(field));

                Object value = nodeClass.getFieldValue(field, node);
                if (value == null) {
                    p.print(" = null ");
                } else if (nodeClass.isChildField(field)) {
                    p.print(" = ");
                    printTree(p, (Node) value, level + 1);
                } else if (nodeClass.isChildrenField(field)) {
                    printChildren(p, level, value);
                }
            }
            printNewLine(p, level - 1);
            p.print("}");
        }
    }

    private static void printChildren(PrintWriter p, int level, Object value) {
        String sep;
        Object[] children = (Object[]) value;
        p.print(" = [");
        sep = "";
        for (Object child : children) {
            p.print(sep);
            sep = ", ";
            printTree(p, (Node) child, level + 1);
        }
        p.print("]");
    }

    private static void printNewLine(PrintWriter p, int level) {
        p.println();
        for (int i = 0; i < level; i++) {
            p.print("    ");
        }
    }

    private static String nodeName(Node node) {
        return className(node.getClass());
    }

    static String className(Class<?> clazz) {
        String name = clazz.getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    private static String displaySourceAttribution(Node node) {
        final SourceSection section = node.getSourceSection();
        if (section == null) {
            return "";
        }
        if (section.getSource() == null) {
            // TODO we can remove this block if SourceSection#createUnavailable was removed, because
            // then source cannot become null anymore.
            return "source: <unknown>";
        }
        final String srcText = section.getCharacters().toString();
        final StringBuilder sb = new StringBuilder();
        sb.append("source:");
        sb.append(" (" + section.getCharIndex() + "," + (section.getCharEndIndex() - 1) + ")");
        sb.append(" line=" + section.getStartLine());
        sb.append(" len=" + srcText.length());
        sb.append(" text=\"" + srcText + "\"");
        return sb.toString();
    }

    /** @since 0.8 or earlier */
    public static boolean verify(Node root) {
        Iterable<Node> children = root.getChildren();
        for (Node child : children) {
            if (child != null) {
                if (child.getParent() != root) {
                    throw new AssertionError(toStringWithClass(child) + ": actual parent=" + toStringWithClass(child.getParent()) + " expected parent=" + toStringWithClass(root));
                }
                verify(child);
            }
        }
        return true;
    }

    private static String toStringWithClass(Object obj) {
        return obj == null ? "null" : obj + "(" + obj.getClass().getName() + ")";
    }

    static void traceRewrite(Node oldNode, Node newNode, CharSequence reason) {
        if (TruffleOptions.TraceRewritesFilterFromCost != null) {
            if (filterByKind(oldNode, TruffleOptions.TraceRewritesFilterFromCost)) {
                return;
            }
        }

        if (TruffleOptions.TraceRewritesFilterToCost != null) {
            if (filterByKind(newNode, TruffleOptions.TraceRewritesFilterToCost)) {
                return;
            }
        }

        String filter = TruffleOptions.TraceRewritesFilterClass;
        Class<? extends Node> from = oldNode.getClass();
        Class<? extends Node> to = newNode.getClass();
        if (filter != null && (filterByContainsClassName(from, filter) || filterByContainsClassName(to, filter))) {
            return;
        }

        final SourceSection reportedSourceSection = oldNode.getEncapsulatingSourceSection();

        PrintStream out = System.out;
        out.printf("[truffle]   rewrite %-50s |From %-40s |To %-40s |Reason %s %s%n", oldNode.toString(), formatNodeInfo(oldNode), formatNodeInfo(newNode),
                        reason != null && reason.length() > 0 ? reason : "unknown", formatLocation(reportedSourceSection));
    }

    private static String formatLocation(SourceSection sourceSection) {
        if (sourceSection == null) {
            return "";
        }

        if (sourceSection.getSource() == null) {
            // TODO we can remove this block if SourceSection#createUnavailable was removed, because
            // then source cannot become null anymore.
            return "at <Unknown>";
        } else {
            return "at " + String.format("%s:%d", sourceSection.getSource().getName(),
                            sourceSection.getStartLine());
        }
    }

    private static String formatNodeInfo(Node node) {
        String cost = "?";
        switch (node.getCost()) {
            case NONE:
                cost = "G";
                break;
            case MONOMORPHIC:
                cost = "M";
                break;
            case POLYMORPHIC:
                cost = "P";
                break;
            case MEGAMORPHIC:
                cost = "G";
                break;
            default:
                cost = "?";
                break;
        }
        return cost + " " + nodeName(node);
    }

    private static boolean filterByKind(Node node, NodeCost cost) {
        return node.getCost() == cost;
    }

    private static boolean filterByContainsClassName(Class<? extends Node> from, String filter) {
        Class<?> currentFrom = from;
        while (currentFrom != null) {
            if (currentFrom.getName().contains(filter)) {
                return false;
            }
            currentFrom = currentFrom.getSuperclass();
        }
        return true;
    }

    private static final class NodeCounter implements NodeVisitor {

        public int count;
        private final NodeCountFilter filter;

        NodeCounter(NodeCountFilter filter) {
            this.filter = filter;
        }

        public boolean visit(Node node) {
            if (filter.isCounted(node)) {
                count++;
            }
            return true;
        }

    }
}
