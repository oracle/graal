/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.*;
import java.util.*;

import sun.misc.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;

/**
 * Utility class that manages the special access methods for node instances.
 */
public final class NodeUtil {

    /**
     * Interface that allows the customization of field offsets used for {@link Unsafe} field
     * accesses.
     */
    public interface FieldOffsetProvider {

        long objectFieldOffset(Field field);

        int getTypeSize(Class<?> clazz);
    }

    private static final FieldOffsetProvider unsafeFieldOffsetProvider = new FieldOffsetProvider() {

        @Override
        public long objectFieldOffset(Field field) {
            return unsafe.objectFieldOffset(field);
        }

        @Override
        public int getTypeSize(Class<?> clazz) {
            if (!clazz.isPrimitive()) {
                return Unsafe.ARRAY_OBJECT_INDEX_SCALE;
            } else if (clazz == int.class) {
                return Unsafe.ARRAY_INT_INDEX_SCALE;
            } else {
                throw new UnsupportedOperationException("unsupported field type: " + clazz);
            }
        }
    };

    public static enum NodeFieldKind {
        /** The single {@link Node#getParent() parent} field. */
        PARENT,
        /** A field annotated with {@link Child}. */
        CHILD,
        /** A field annotated with {@link Children}. */
        CHILDREN,
        /** A normal non-child data field of the node. */
        DATA
    }

    /**
     * Information about a field in a {@link Node} class.
     */
    public static final class NodeField {

        private final NodeFieldKind kind;
        private final Class<?> type;
        private final String name;
        private long offset;

        protected NodeField(NodeFieldKind kind, Class<?> type, String name, long offset) {
            this.kind = kind;
            this.type = type;
            this.name = name;
            this.offset = offset;
        }

        public NodeFieldKind getKind() {
            return kind;
        }

        public Class<?> getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public long getOffset() {
            return offset;
        }

        public Object loadValue(Node node) {
            if (type == boolean.class) {
                return unsafe.getBoolean(node, offset);
            } else if (type == byte.class) {
                return unsafe.getByte(node, offset);
            } else if (type == short.class) {
                return unsafe.getShort(node, offset);
            } else if (type == char.class) {
                return unsafe.getChar(node, offset);
            } else if (type == int.class) {
                return unsafe.getInt(node, offset);
            } else if (type == long.class) {
                return unsafe.getLong(node, offset);
            } else if (type == float.class) {
                return unsafe.getFloat(node, offset);
            } else if (type == double.class) {
                return unsafe.getDouble(node, offset);
            } else {
                return unsafe.getObject(node, offset);
            }
        }

        @Override
        public int hashCode() {
            return kind.hashCode() | type.hashCode() | name.hashCode() | ((Long) offset).hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof NodeField) {
                NodeField other = (NodeField) obj;
                return offset == other.offset && name.equals(other.name) && type.equals(other.type) && kind.equals(other.kind);
            }
            return false;
        }
    }

    /**
     * Information about a {@link Node} class. A single instance of this class is allocated for
     * every subclass of {@link Node} that is used.
     */
    public static final class NodeClass {

        private static final Map<Class<?>, NodeClass> nodeClasses = new IdentityHashMap<>();

        // The comprehensive list of all fields.
        private final NodeField[] fields;
        // Separate arrays for the frequently accessed field offsets.
        private final long parentOffset;
        private final long[] childOffsets;
        private final long[] childrenOffsets;

        public static NodeClass get(Class<? extends Node> clazz) {
            NodeClass nodeClass = nodeClasses.get(clazz);
            if (nodeClass == null) {
                nodeClass = new NodeClass(clazz, unsafeFieldOffsetProvider);
                nodeClasses.put(clazz, nodeClass);
            }
            return nodeClass;
        }

        public NodeClass(Class<? extends Node> clazz, FieldOffsetProvider fieldOffsetProvider) {
            List<NodeField> fieldsList = new ArrayList<>();
            List<Long> parentOffsetsList = new ArrayList<>();
            List<Long> childOffsetsList = new ArrayList<>();
            List<Long> childrenOffsetsList = new ArrayList<>();

            for (Field field : getAllFields(clazz)) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }

                NodeFieldKind kind;
                if (Node.class.isAssignableFrom(field.getType()) && field.getName().equals("parent") && field.getDeclaringClass() == Node.class) {
                    kind = NodeFieldKind.PARENT;
                    parentOffsetsList.add(fieldOffsetProvider.objectFieldOffset(field));
                } else if (Node.class.isAssignableFrom(field.getType()) && field.getAnnotation(Child.class) != null) {
                    kind = NodeFieldKind.CHILD;
                    childOffsetsList.add(fieldOffsetProvider.objectFieldOffset(field));
                    assert !Modifier.isFinal(field.getModifiers()) : "child field must not be final (\"" + field.getName() + "\", " + clazz + ")";
                } else if (field.getType().isArray() && Node.class.isAssignableFrom(field.getType().getComponentType()) && field.getAnnotation(Children.class) != null) {
                    kind = NodeFieldKind.CHILDREN;
                    childrenOffsetsList.add(fieldOffsetProvider.objectFieldOffset(field));
                    assert Modifier.isFinal(field.getModifiers()) : "children array field must be final (\"" + field.getName() + "\", " + clazz + ")";
                } else {
                    kind = NodeFieldKind.DATA;
                }
                fieldsList.add(new NodeField(kind, field.getType(), field.getName(), fieldOffsetProvider.objectFieldOffset(field)));
            }
            this.fields = fieldsList.toArray(new NodeField[fieldsList.size()]);
            assert parentOffsetsList.size() == 1 : "must have exactly one parent field";
            this.parentOffset = parentOffsetsList.get(0);
            this.childOffsets = toLongArray(childOffsetsList);
            this.childrenOffsets = toLongArray(childrenOffsetsList);
        }

        public NodeField[] getFields() {
            return fields;
        }

        public long getParentOffset() {
            return parentOffset;
        }

        public long[] getChildOffsets() {
            return childOffsets;
        }

        public long[] getChildrenOffsets() {
            return childrenOffsets;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(fields) ^ Arrays.hashCode(childOffsets) ^ Arrays.hashCode(childrenOffsets) ^ ((Long) parentOffset).hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof NodeClass) {
                NodeClass other = (NodeClass) obj;
                return Arrays.equals(fields, other.fields) && Arrays.equals(childOffsets, other.childOffsets) && Arrays.equals(childrenOffsets, other.childrenOffsets) &&
                                parentOffset == other.parentOffset;
            }
            return false;
        }
    }

    static class NodeIterator implements Iterator<Node> {

        private final Node node;
        private final NodeClass nodeClass;
        private final int childrenCount;
        private int index;

        protected NodeIterator(Node node) {
            this.node = node;
            this.index = 0;
            this.nodeClass = NodeClass.get(node.getClass());
            this.childrenCount = childrenCount();
        }

        private int childrenCount() {
            int nodeCount = nodeClass.childOffsets.length;
            for (long fieldOffset : nodeClass.childrenOffsets) {
                Node[] children = ((Node[]) unsafe.getObject(node, fieldOffset));
                if (children != null) {
                    nodeCount += children.length;
                }
            }
            return nodeCount;
        }

        private Node nodeAt(int idx) {
            int nodeCount = nodeClass.childOffsets.length;
            if (idx < nodeCount) {
                return (Node) unsafe.getObject(node, nodeClass.childOffsets[idx]);
            } else {
                for (long fieldOffset : nodeClass.childrenOffsets) {
                    Node[] nodeArray = (Node[]) unsafe.getObject(node, fieldOffset);
                    if (idx < nodeCount + nodeArray.length) {
                        return nodeArray[idx - nodeCount];
                    }
                    nodeCount += nodeArray.length;
                }
            }
            return null;
        }

        private void forward() {
            if (index < childrenCount) {
                index++;
            }
        }

        @Override
        public boolean hasNext() {
            return index < childrenCount;
        }

        @Override
        public Node next() {
            try {
                return nodeAt(index);
            } finally {
                forward();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static long[] toLongArray(List<Long> list) {
        long[] array = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    private static final Unsafe unsafe = getUnsafe();

    private static Unsafe getUnsafe() {
        try {
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Node> T cloneNode(T orig) {
        final Node clone = orig.copy();
        NodeClass nodeClass = NodeClass.get(clone.getClass());

        unsafe.putObject(clone, nodeClass.parentOffset, null);

        for (long fieldOffset : nodeClass.childOffsets) {
            Node child = (Node) unsafe.getObject(orig, fieldOffset);
            if (child != null) {
                Node clonedChild = cloneNode(child);
                unsafe.putObject(clonedChild, nodeClass.parentOffset, clone);
                unsafe.putObject(clone, fieldOffset, clonedChild);
            }
        }
        for (long fieldOffset : nodeClass.childrenOffsets) {
            Node[] children = (Node[]) unsafe.getObject(orig, fieldOffset);
            if (children != null) {
                Node[] clonedChildren = (Node[]) Array.newInstance(children.getClass().getComponentType(), children.length);
                for (int i = 0; i < children.length; i++) {
                    if (children[i] != null) {
                        Node clonedChild = cloneNode(children[i]);
                        clonedChildren[i] = clonedChild;
                        unsafe.putObject(clonedChild, nodeClass.parentOffset, clone);
                    }
                }
                unsafe.putObject(clone, fieldOffset, clonedChildren);
            }
        }
        return (T) clone;
    }

    public static List<Node> findNodeChildren(Node node) {
        List<Node> nodes = new ArrayList<>();
        NodeClass nodeClass = NodeClass.get(node.getClass());

        for (long fieldOffset : nodeClass.childOffsets) {
            Object child = unsafe.getObject(node, fieldOffset);
            if (child != null) {
                nodes.add((Node) child);
            }
        }
        for (long fieldOffset : nodeClass.childrenOffsets) {
            Node[] children = (Node[]) unsafe.getObject(node, fieldOffset);
            if (children != null) {
                for (Node child : children) {
                    if (child != null) {
                        nodes.add(child);
                    }
                }
            }
        }

        return nodes;
    }

    public static boolean replaceChild(Node parent, Node oldChild, Node newChild) {
        NodeClass nodeClass = NodeClass.get(parent.getClass());

        for (long fieldOffset : nodeClass.getChildOffsets()) {
            if (unsafe.getObject(parent, fieldOffset) == oldChild) {
                assert assertAssignable(nodeClass, fieldOffset, newChild);
                unsafe.putObject(parent, fieldOffset, newChild);
                return true;
            }
        }

        for (long fieldOffset : nodeClass.getChildrenOffsets()) {
            Object arrayObject = unsafe.getObject(parent, fieldOffset);
            if (arrayObject != null) {
                assert arrayObject instanceof Node[] : "Children array must be instanceof Node[] ";
                Node[] array = (Node[]) arrayObject;
                for (int i = 0; i < array.length; i++) {
                    if (array[i] == oldChild) {
                        assert assertAssignable(nodeClass, fieldOffset, newChild);
                        array[i] = newChild;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean assertAssignable(NodeClass clazz, long fieldOffset, Object newValue) {
        if (newValue == null) {
            return true;
        }
        for (NodeField field : clazz.getFields()) {
            if (field.getOffset() == fieldOffset) {
                if (field.getKind() == NodeFieldKind.CHILD) {
                    if (field.getType().isAssignableFrom(newValue.getClass())) {
                        return true;
                    } else {
                        assert false : "Child class " + newValue.getClass().getName() + " is not assignable to field \"" + field.getName() + "\" of type " + field.getType().getName();
                        return false;
                    }
                } else if (field.getKind() == NodeFieldKind.CHILDREN) {
                    if (field.getType().getComponentType().isAssignableFrom(newValue.getClass())) {
                        return true;
                    } else {
                        assert false : "Child class " + newValue.getClass().getName() + " is not assignable to field \"" + field.getName() + "\" of type " + field.getType().getName();
                        return false;
                    }
                }
            }
        }
        throw new IllegalArgumentException();
    }

    /** Returns all declared fields in the class hierarchy. */
    private static Field[] getAllFields(Class<? extends Object> clazz) {
        Field[] declaredFields = clazz.getDeclaredFields();
        if (clazz.getSuperclass() != null) {
            return concat(getAllFields(clazz.getSuperclass()), declaredFields);
        }
        return declaredFields;
    }

    public static <T> T[] concat(T[] first, T[] second) {
        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    /**
     * Get the nth parent of a node, where the 0th parent is the node itself. Returns null if there
     * are less than n ancestors.
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

    /** find annotation in class/interface hierarchy. */
    public static <T extends Annotation> T findAnnotation(Class<?> clazz, Class<T> annotationClass) {
        if (clazz.getAnnotation(annotationClass) != null) {
            return clazz.getAnnotation(annotationClass);
        } else {
            for (Class<?> intf : clazz.getInterfaces()) {
                if (intf.getAnnotation(annotationClass) != null) {
                    return intf.getAnnotation(annotationClass);
                }
            }
            if (clazz.getSuperclass() != null) {
                return findAnnotation(clazz.getSuperclass(), annotationClass);
            }
        }
        return null;
    }

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

    public static <T> List<T> findAllParents(Node start, Class<T> clazz) {
        List<T> parents = new ArrayList<>();
        T parent = findParent(start, clazz);
        while (parent != null) {
            parents.add(parent);
            parent = findParent((Node) parent, clazz);
        }
        return parents;
    }

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

    public static <T> T findFirstNodeInstance(Node root, Class<T> clazz) {
        for (Node childNode : findNodeChildren(root)) {
            if (clazz.isInstance(childNode)) {
                return clazz.cast(childNode);
            } else {
                T node = findFirstNodeInstance(childNode, clazz);
                if (node != null) {
                    return node;
                }
            }
        }
        return null;
    }

    public static <T extends Node> List<T> findAllNodeInstances(final Node root, final Class<T> clazz) {
        final List<T> nodeList = new ArrayList<>();
        root.accept(new NodeVisitor() {

            @SuppressWarnings("unchecked")
            @Override
            public boolean visit(Node node) {
                if (clazz.isInstance(node)) {
                    nodeList.add((T) node);
                }
                return true;
            }
        });
        return nodeList;
    }

    // Don't visit found node instances.
    public static <T extends Node> List<T> findNodeInstancesShallow(final Node root, final Class<T> clazz) {
        final List<T> nodeList = new ArrayList<>();
        root.accept(new NodeVisitor() {

            @SuppressWarnings("unchecked")
            @Override
            public boolean visit(Node node) {
                if (clazz.isInstance(node)) {
                    nodeList.add((T) node);
                    return false;
                }
                return true;
            }
        });
        return nodeList;
    }

    /** Find node instances within current function only (not in nested functions). */
    public static <T extends Node> List<T> findNodeInstancesInFunction(final Node root, final Class<T> clazz) {
        final List<T> nodeList = new ArrayList<>();
        root.accept(new NodeVisitor() {

            @SuppressWarnings("unchecked")
            @Override
            public boolean visit(Node node) {
                if (clazz.isInstance(node)) {
                    nodeList.add((T) node);
                } else if (node instanceof RootNode && node != root) {
                    return false;
                }
                return true;
            }
        });
        return nodeList;
    }

    public static <I> List<I> findNodeInstancesInFunctionInterface(final Node root, final Class<I> clazz) {
        final List<I> nodeList = new ArrayList<>();
        root.accept(new NodeVisitor() {

            @SuppressWarnings("unchecked")
            @Override
            public boolean visit(Node node) {
                if (clazz.isInstance(node)) {
                    nodeList.add((I) node);
                } else if (node instanceof RootNode && node != root) {
                    return false;
                }
                return true;
            }
        });
        return nodeList;
    }

    public static int countNodes(Node root) {
        return countNodes(root, null, false);
    }

    public static int countNodes(Node root, NodeCountFilter filter) {
        return countNodes(root, filter, false);
    }

    public static int countNodes(Node root, NodeCountFilter filter, boolean visitInlinedCallNodes) {
        NodeCountVisitor nodeCount = new NodeCountVisitor(filter, visitInlinedCallNodes);
        root.accept(nodeCount);
        return nodeCount.nodeCount;
    }

    public interface NodeCountFilter {

        boolean isCounted(Node node);

    }

    private static final class NodeCountVisitor implements NodeVisitor {

        private final boolean visitInlinedCallNodes;
        int nodeCount;
        private final NodeCountFilter filter;

        private NodeCountVisitor(NodeCountFilter filter, boolean visitInlinedCallNodes) {
            this.filter = filter;
            this.visitInlinedCallNodes = visitInlinedCallNodes;
        }

        @Override
        public boolean visit(Node node) {
            if (filter == null || filter.isCounted(node)) {
                nodeCount++;
            }

            if (visitInlinedCallNodes && node instanceof DirectCallNode) {
                DirectCallNode call = (DirectCallNode) node;
                if (call.isInlined()) {
                    Node target = ((RootCallTarget) call.getCurrentCallTarget()).getRootNode();
                    if (target != null) {
                        target.accept(this);
                    }
                }
            }

            return true;
        }

    }

    public static String printCompactTreeToString(Node node) {
        StringWriter out = new StringWriter();
        printCompactTree(new PrintWriter(out), null, node, 1);
        return out.toString();
    }

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
            String fieldName = "unknownField";
            NodeField[] fields = NodeClass.get(parent.getClass()).fields;
            for (NodeField field : fields) {
                Object value = field.loadValue(parent);
                if (value == node) {
                    fieldName = field.getName();
                    break;
                } else if (value instanceof Node[]) {
                    int index = 0;
                    for (Node arrayNode : (Node[]) value) {
                        if (arrayNode == node) {
                            fieldName = field.getName() + "[" + index + "]";
                            break;
                        }
                        index++;
                    }
                }
            }
            p.print(fieldName);
            p.print(" = ");
            p.println(nodeName(node));
        }

        for (Node child : node.getChildren()) {
            printCompactTree(p, node, child, level + 1);
        }
        p.flush();
    }

    public static String printSourceAttributionTree(Node node) {
        StringWriter out = new StringWriter();
        printSourceAttributionTree(new PrintWriter(out), null, node, 1);
        return out.toString();
    }

    public static void printSourceAttributionTree(OutputStream out, Node node) {
        printSourceAttributionTree(new PrintWriter(out), null, node, 1);
    }

    private static void printSourceAttributionTree(PrintWriter p, Node parent, Node node, int level) {
        if (node == null) {
            return;
        }
        if (parent == null) {
            // Add some preliminary information before starting with the root node
            final SourceSection sourceSection = node.getSourceSection();
            if (sourceSection != null) {
                final String txt = sourceSection.getSource().getCode();
                p.println("Full source len=(" + txt.length() + ")  ___" + txt + "___");
                p.println("AST source attribution:");
            }
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("| ");
        }

        if (parent != null) {
            String childName = "";
            NodeField[] fields = NodeClass.get(parent.getClass()).fields;
            for (NodeField field : fields) {
                Object value = field.loadValue(parent);
                if (value == node) {
                    childName = field.getName();
                    break;
                } else if (value instanceof Node[]) {
                    int index = 0;
                    for (Node arrayNode : (Node[]) value) {
                        if (arrayNode == node) {
                            childName = field.getName() + "[" + index + "]";
                            break;
                        }
                        index++;
                    }
                }
            }
            sb.append(childName);
        }

        sb.append("  (" + node.getClass().getSimpleName() + ")  ");
        sb.append(displaySourceAttribution(node));
        p.println(sb.toString());

        for (Node child : node.getChildren()) {
            printSourceAttributionTree(p, node, child, level + 1);
        }
        p.flush();
    }

    /**
     * Prints a human readable form of a {@link Node} AST to the given {@link PrintStream}. This
     * print method does not check for cycles in the node structure.
     *
     * @param out the stream to print to.
     * @param node the root node to write
     */
    public static void printTree(OutputStream out, Node node) {
        printTree(new PrintWriter(out), node);
    }

    public static String printTreeToString(Node node) {
        StringWriter out = new StringWriter();
        printTree(new PrintWriter(out), node);
        return out.toString();
    }

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

        ArrayList<NodeField> childFields = new ArrayList<>();
        String sep = "";
        p.print("(");
        for (NodeField field : NodeClass.get(node.getClass()).fields) {
            if (field.getKind() == NodeFieldKind.CHILD || field.getKind() == NodeFieldKind.CHILDREN) {
                childFields.add(field);
            } else if (field.getKind() == NodeFieldKind.DATA) {
                p.print(sep);
                sep = ", ";

                p.print(field.getName());
                p.print(" = ");
                p.print(field.loadValue(node));
            }
        }
        p.print(")");

        if (childFields.size() != 0) {
            p.print(" {");
            for (NodeField field : childFields) {
                printNewLine(p, level);
                p.print(field.getName());

                Object value = field.loadValue(node);
                if (value == null) {
                    p.print(" = null ");
                } else if (field.getKind() == NodeFieldKind.CHILD) {
                    p.print(" = ");
                    printTree(p, (Node) value, level + 1);
                } else if (field.getKind() == NodeFieldKind.CHILDREN) {
                    Node[] children = (Node[]) value;
                    p.print(" = [");
                    sep = "";
                    for (Node child : children) {
                        p.print(sep);
                        sep = ", ";
                        printTree(p, child, level + 1);
                    }
                    p.print("]");
                }
            }
            printNewLine(p, level - 1);
            p.print("}");
        }
    }

    private static void printNewLine(PrintWriter p, int level) {
        p.println();
        for (int i = 0; i < level; i++) {
            p.print("    ");
        }
    }

    private static String nodeName(Node node) {
        return node.getClass().getSimpleName();
    }

    private static String displaySourceAttribution(Node node) {
        final SourceSection section = node.getSourceSection();
        if (section != null) {
            final String srcText = section.getCode();
            final StringBuilder sb = new StringBuilder();
            sb.append("source:  len=" + srcText.length());
            sb.append(" (" + section.getCharIndex() + "," + (section.getCharEndIndex() - 1) + ")");
            sb.append(" ___" + srcText + "___");
            return sb.toString();
        }
        return "";
    }

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
}
