/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import sun.misc.*;

/**
 * Utility class that manages the special access methods for node instances.
 */
public class NodeUtil {

    public static final class NodeClass {

        private final Class parentClass;
        private final long[] nodeFieldOffsets;
        private final Class[] nodeFieldClasses;
        private final long[] nodeArrayFieldOffsets;
        private final Class[] nodeArrayFieldClasses;
        private final long parentOffset;
        private final long[] nodeDataFieldOffsets;
        private final Class< ? >[] nodeDataFieldClasses;

        private static final Map<Class< ? >, NodeClass> nodeClasses = new IdentityHashMap<>();

        public static NodeClass get(Class< ? > clazz) {
            NodeClass nodeClass = nodeClasses.get(clazz);
            if (nodeClass == null) {
                nodeClass = new NodeClass(clazz);
                nodeClasses.put(clazz, nodeClass);
            }
            return nodeClass;
        }

        private NodeClass(Class< ? > clazz) {
            // scan object fields
            Class< ? > parentClassTmp = null;
            List<Long> nodeFieldOffsetsList = new ArrayList<>();
            List<Class< ? >> nodeFieldClassesList = new ArrayList<>();
            List<Long> nodeArrayFieldOffsetsList = new ArrayList<>();
            List<Class< ? >> nodeArrayFieldClassesList = new ArrayList<>();
            List<Long> nodeDataFieldOffsetList = new ArrayList<>();
            List<Class< ? >> nodeDataFieldClassList = new ArrayList<>();
            Field[] fields = getAllFields(clazz);
            long parentOffsetTemp = -1;
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }

                // Node fields
                if (Node.class.isAssignableFrom(field.getType())) {
                    if (!field.getName().equals("parent")) {
                        nodeFieldOffsetsList.add(unsafe.objectFieldOffset(field));
                        nodeFieldClassesList.add(field.getType());
                    } else {
                        parentOffsetTemp = unsafe.objectFieldOffset(field);
                        parentClassTmp = field.getType();
                    }
                } else if (field.getType().getComponentType() != null && Node.class.isAssignableFrom(field.getType().getComponentType())) {
                    nodeArrayFieldOffsetsList.add(unsafe.objectFieldOffset(field));
                    nodeArrayFieldClassesList.add(field.getType());
                } else {
                    nodeDataFieldOffsetList.add(unsafe.objectFieldOffset(field));
                    nodeDataFieldClassList.add(field.getType());
                }
            }
            this.parentClass = parentClassTmp;
            this.nodeFieldOffsets = toLongArray(nodeFieldOffsetsList);
            this.nodeFieldClasses = nodeFieldClassesList.toArray(new Class[nodeFieldClassesList.size()]);
            this.nodeArrayFieldOffsets = toLongArray(nodeArrayFieldOffsetsList);
            this.nodeArrayFieldClasses = nodeArrayFieldClassesList.toArray(new Class[nodeArrayFieldClassesList.size()]);
            this.nodeDataFieldOffsets = toLongArray(nodeDataFieldOffsetList);
            this.nodeDataFieldClasses = nodeDataFieldClassList.toArray(new Class< ? >[nodeDataFieldClassList.size()]);

            this.parentOffset = parentOffsetTemp;
        }
    }

    public static class NodeIterator implements Iterator<Node> {

        private final Node node;
        private final NodeClass nodeClass;
        private final int childrenCount;
        private int index;

        public NodeIterator(Node node) {
            this(node, 0);
        }

        public NodeIterator(Node node, int index) {
            this.node = node;
            this.index = index;
            this.nodeClass = NodeClass.get(node.getClass());
            this.childrenCount = childrenCount();
        }

        private int childrenCount() {
            int nodeCount = nodeClass.nodeFieldOffsets.length;
            for (long fieldOffset : nodeClass.nodeArrayFieldOffsets) {
                Node[] children = ((Node[]) unsafe.getObject(node, fieldOffset));
                if (children != null) {
                    nodeCount += children.length;
                }
            }
            return nodeCount;
        }

        private Node nodeAt(int idx) {
            int nodeCount = nodeClass.nodeFieldOffsets.length;
            if (idx < nodeCount) {
                return (Node) unsafe.getObject(node, nodeClass.nodeFieldOffsets[idx]);
            } else {
                for (long fieldOffset : nodeClass.nodeArrayFieldOffsets) {
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
        Class< ? extends Node> clazz = orig.getClass();
        NodeClass nodeClass = NodeClass.get(clazz);
        Node clone = orig.copy();
        if (clone == null) {
            return null;
        }

        unsafe.putObject(clone, nodeClass.parentOffset, null);

        for (long fieldOffset : nodeClass.nodeFieldOffsets) {
            Node child = (Node) unsafe.getObject(orig, fieldOffset);
            if (child != null) {
                Node clonedChild = cloneNode(child);
                if (clonedChild == null) {
                    return null;
                }

                unsafe.putObject(clonedChild, nodeClass.parentOffset, clone);
                unsafe.putObject(clone, fieldOffset, clonedChild);
            }
        }
        for (long fieldOffset : nodeClass.nodeArrayFieldOffsets) {
            Node[] children = (Node[]) unsafe.getObject(orig, fieldOffset);
            if (children != null) {
                Node[] clonedChildren = new Node[children.length];
                for (int i = 0; i < children.length; i++) {
                    Node clonedChild = cloneNode(children[i]);
                    if (clonedChild == null) {
                        return null;
                    }

                    clonedChildren[i] = clonedChild;
                    unsafe.putObject(clonedChild, nodeClass.parentOffset, clone);
                }
                unsafe.putObject(clone, fieldOffset, clonedChildren);
            }
        }
        return (T) clone;
    }

    public static List<Object> findNodeChildren(Object node) {
        List<Object> nodes = new ArrayList<>();
        NodeClass nodeClass = NodeClass.get(node.getClass());

        for (long fieldOffset : nodeClass.nodeFieldOffsets) {
            Object child = unsafe.getObject(node, fieldOffset);
            if (child != null) {
                nodes.add(child);
            }
        }
        for (long fieldOffset : nodeClass.nodeArrayFieldOffsets) {
            Object[] children = (Object[]) unsafe.getObject(node, fieldOffset);
            if (children != null) {
                nodes.addAll(Arrays.asList(children));
            }
        }

        return nodes;
    }

    public static void replaceChild(Node parent, Node oldChild, Node newChild) {
        NodeClass nodeClass = NodeClass.get(parent.getClass());

        for (long fieldOffset : nodeClass.nodeFieldOffsets) {
            if (unsafe.getObject(parent, fieldOffset) == oldChild) {
                unsafe.putObject(parent, fieldOffset, newChild);
            }
        }
        for (long fieldOffset : nodeClass.nodeArrayFieldOffsets) {
            Node[] array = (Node[]) unsafe.getObject(parent, fieldOffset);
            if (array != null) {
                for (int i = 0; i < array.length; i++) {
                    if (array[i] == oldChild) {
                        array[i] = newChild;
                        return;
                    }
                }
            }
        }
    }

    public static long[] getNodeDataFieldOffsets(Class< ? > nodeClass) {
        NodeClass clazz = NodeClass.get(nodeClass);
        return Arrays.copyOf(clazz.nodeDataFieldOffsets, clazz.nodeDataFieldClasses.length);
    }

    public static Class[] getNodeDataFieldClasses(Class< ? > nodeClass) {
        NodeClass clazz = NodeClass.get(nodeClass);
        return Arrays.copyOf(clazz.nodeDataFieldClasses, clazz.nodeDataFieldClasses.length);
    }

    public static long getNodeParentOffset(Class< ? > nodeClass) {
        NodeClass clazz = NodeClass.get(nodeClass);
        return clazz.parentOffset;
    }

    /** Returns the number of Node field declarations in the class hierarchy. */
    public static long[] getNodeFieldOffsets(Class< ? > nodeClass) {
        NodeClass clazz = NodeClass.get(nodeClass);
        return Arrays.copyOf(clazz.nodeFieldOffsets, clazz.nodeFieldOffsets.length);
    }

    /** Returns the number of Node[] declaration in the class hierarchy. */
    public static long[] getNodeFieldArrayOffsets(Class< ? > nodeClass) {
        NodeClass clazz = NodeClass.get(nodeClass);
        return Arrays.copyOf(clazz.nodeArrayFieldOffsets, clazz.nodeArrayFieldOffsets.length);
    }

    public static Class[] getNodeFieldArrayClasses(Class< ? > nodeClass) {
        NodeClass clazz = NodeClass.get(nodeClass);
        return Arrays.copyOf(clazz.nodeArrayFieldClasses, clazz.nodeArrayFieldClasses.length);
    }

    public static Class getNodeParentClass(Class< ? > nodeClass) {
        return NodeClass.get(nodeClass).parentClass;
    }

    public static Class[] getNodeFieldClasses(Class< ? > nodeClass) {
        NodeClass clazz = NodeClass.get(nodeClass);
        return Arrays.copyOf(clazz.nodeFieldClasses, clazz.nodeFieldClasses.length);
    }

    /** Returns all declared fields in the class hierarchy. */
    public static Field[] getAllFields(Class< ? extends Object> clazz) {
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

    /** find annotation in class/interface hierarchy. */
    public static <T extends Annotation> T findAnnotation(Class< ? > clazz, Class<T> annotationClass) {
        if (clazz.getAnnotation(annotationClass) != null) {
            return clazz.getAnnotation(annotationClass);
        } else {
            for (Class< ? > intf : clazz.getInterfaces()) {
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

    @SuppressWarnings("unchecked")
    public static <T extends Node> T findParent(final Node start, final Class<T> clazz) {
        assert start != null;
        if (clazz.isInstance(start.getParent())) {
            return (T) start.getParent();
        } else {
            return start.getParent() != null ? findParent(start.getParent(), clazz) : null;
        }
    }

    @SuppressWarnings("unchecked")
    public static <I> I findParentInterface(final Node start, final Class<I> clazz) {
        assert start != null;
        if (clazz.isInstance(start.getParent())) {
            return (I) start.getParent();
        } else {
            return (start.getParent() != null ? findParentInterface(start.getParent(), clazz) : null);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T findFirstNodeInstance(Object root, Class<T> clazz) {
        List<Object> childNodes = findNodeChildren(root);

        for (Object childNode : childNodes) {
            if (clazz.isInstance(childNode)) {
                return (T) childNode;
            } else {
                return findFirstNodeInstance(childNode, clazz);
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

    public static String printTreeToString(Node node) {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        printTree(new PrintStream(byteOut), node);
        try {
            byteOut.flush();
        } catch (IOException e) {
        }
        return new String(byteOut.toByteArray());
    }

    /**
     * Prints a human readable form of a {@link Node} AST to the given {@link PrintStream}. This print method does not
     * check for cycles in the node structure.
     *
     * @param p the {@link PrintStream} to print to.
     * @param node the root node to write
     */
    public static void printTree(PrintStream p, Node node) {
        printTree(p, node, new NodeTreeResolver());
    }

    /**
     * Prints a human readable form of a tree to the given {@link PrintStream}. The {@link TreeResolver} interface needs
     * to be implemented to specify how the method can read the tree from plain a object.
     *
     * @param p the {@link PrintStream} to print to.
     * @param o the root object to be printed.
     * @param resolver an implementation of a tree resolver
     */
    public static void printTree(PrintStream p, Object o, TreeResolver resolver) {
        printTree(p, o, resolver, 1);
        p.println();
    }

    private static void printTree(PrintStream p, Object node, TreeResolver resolver, int level) {
        if (node == null) {
            p.print("null");
            return;
        }

        p.print(node.getClass().getSimpleName());

        Field[] fields = NodeUtil.getAllFields(node.getClass());
        p.print("(");

        ArrayList<Field> childFields = new ArrayList<>();

        boolean first = true;
        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            if (Modifier.isStatic(field.getModifiers()) || resolver.isFiltered(field)) {
                continue;
            }
            if (resolver.isChildObject(field) || resolver.isChildArrayObject(field)) {
                childFields.add(field);
            }
            if (!resolver.isDataField(field)) {
                continue;
            }
            if (!first) {
                p.print(", ");
            } else {
                first = false;
            }

            Object value = getObjectValue(node, field.getType(), unsafe.objectFieldOffset(field));

            p.print(field.getName());
            p.print(" = ");
            p.print(resolver.toString(value));
        }
        p.print(")");

        if (childFields.size() != 0) {
            p.print(" {");
        }

        // I refetch the fields to get declaration order.
        for (int i = 0; i < childFields.size(); i++) {
            Field field = childFields.get(i);
            Class< ? > fieldClass = field.getType();
            String name = field.getName();

            long offset = unsafe.objectFieldOffset(field);

            Object value = getObjectValue(node, fieldClass, offset);

            printNewLine(p, level);
            p.print(name);
            if (value == null) {
                p.print(" = null ");
            } else if (resolver.isChildObject(field)) {
                p.print(" = ");
                printTree(p, value, resolver, level + 1);
            } else if (resolver.isChildArrayObject(field)) {
                Object[] objectArray = resolver.convertToArray(field, value);
                if (objectArray.length == 0) {
                    p.print(" = []");
                } else {
                    p.print(" = [");
                    for (int j = 0; j < objectArray.length; j++) {
                        printTree(p, objectArray[j], resolver, level + 1);
                        if (j < objectArray.length - 1) {
                            p.print(", ");
                        }
                    }
                    p.print("]");
                }
            } else {
                assert false;
            }
        }

        if (childFields.size() != 0) {
            printNewLine(p, level - 1);
            p.print("}");
        }
    }

    private static Object getObjectValue(Object base, Class< ? > fieldClass, long offset) {
        if (fieldClass == boolean.class) {
            return unsafe.getBoolean(base, offset);
        } else if (fieldClass == byte.class) {
            return unsafe.getByte(base, offset);
        } else if (fieldClass == short.class) {
            return unsafe.getShort(base, offset);
        } else if (fieldClass == char.class) {
            return unsafe.getChar(base, offset);
        } else if (fieldClass == int.class) {
            return unsafe.getInt(base, offset);
        } else if (fieldClass == long.class) {
            return unsafe.getLong(base, offset);
        } else if (fieldClass == float.class) {
            return unsafe.getFloat(base, offset);
        } else if (fieldClass == double.class) {
            return unsafe.getDouble(base, offset);
        } else {
            return unsafe.getObject(base, offset);
        }
    }

    private static void printNewLine(PrintStream p, int level) {
        p.println();
        for (int i = 0; i < level; i++) {
            p.print("    ");
        }
    }

    private static class NodeTreeResolver implements TreeResolver {

        @Override
        public boolean isFiltered(Field f) {
            if (f.getName().equals("parent")) {
                return true;
            }
            return f.isSynthetic();
        }

        @Override
        public boolean isDataField(Field f) {
            return !isChildArrayObject(f) && !isChildObject(f);
        }

        @Override
        public boolean isChildObject(Field f) {
            return Node.class.isAssignableFrom(f.getType());
        }

        @Override
        public boolean isChildArrayObject(Field f) {
            return f.getType().getComponentType() != null && Node.class.isAssignableFrom(f.getType().getComponentType());
        }

        @Override
        public Object[] convertToArray(Field f, Object data) {
            return (Object[]) data;
        }

        @Override
        public String toString(Object o) {
            return o == null ? "null" : o.toString();
        }
    }

    /**
     * Specifies how a tree can be built from plain objects.
     */
    public interface TreeResolver {

        /**
         * Returns true if a {@link Field} is filtered from the tree.
         *
         * @param f the field to check
         */
        boolean isFiltered(Field f);

        /**
         * Returns true if a {@link Field} is a field that contains a data value which should not be traversed
         * recursively.
         *
         * @param f the field to check
         * @return true if a the given field is a data field else false.
         */
        boolean isDataField(Field f);

        /**
         * Returns true if a {@link Field} is a field that contains an {@link Object} which should be recursively
         * visited.
         *
         * @param f the field to check
         * @return true if a the given field is a child field else false.
         */
        boolean isChildObject(Field f);

        /**
         * Returns true if a {@link Field} is a field that contains any kind of list/array structure which itself holds
         * values that should be recursively visited.
         *
         * @param f the field to check
         * @return true if a the given field is a child array/list field else false.
         */
        boolean isChildArrayObject(Field f);

        /**
         * Converts an given child array object to array which can be traversed. This is especially useful to convert
         * any kind of list structure to a traversable array.
         *
         * @param f the field for meta data needed to convert the data {@link Object}.
         * @param value the actual value of the child array/list object.
         * @return the converted {@link Object} array.
         */
        Object[] convertToArray(Field f, Object value);

        /**
         * Returns a human readable string for any data field object in the tree.
         *
         * @param o the object to convert to string.
         * @return the converted string
         */
        String toString(Object o);
    }

}
