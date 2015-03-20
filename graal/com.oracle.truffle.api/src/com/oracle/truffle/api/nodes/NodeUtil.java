/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.security.*;
import java.util.*;

import sun.misc.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.ProbeNode.WrapperNode;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.source.*;

/**
 * Utility class that manages the special access methods for node instances.
 */
public final class NodeUtil {
    private static final boolean USE_UNSAFE = Boolean.getBoolean("truffle.unsafe");

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
    public abstract static class NodeField {

        private final NodeFieldKind kind;
        private final String name;
        protected final Class<?> type;
        protected final long offset;

        protected NodeField(NodeFieldKind kind, Field field) {
            this.kind = kind;
            this.type = field.getType();
            this.name = field.getName();
            this.offset = unsafeFieldOffsetProvider.objectFieldOffset(field);
        }

        protected static NodeField create(NodeFieldKind kind, Field field) {
            if (USE_UNSAFE) {
                return new UnsafeNodeField(kind, field);
            } else {
                return new ReflectionNodeField(kind, field);
            }
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

        public abstract void putObject(Node receiver, Object value);

        public abstract Object getObject(Node receiver);

        public abstract Object loadValue(Node node);

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

    private static final class UnsafeNodeField extends NodeField {
        protected UnsafeNodeField(NodeFieldKind kind, Field field) {
            super(kind, field);
        }

        @Override
        public void putObject(Node receiver, Object value) {
            if (!type.isPrimitive() && value == null || type.isInstance(value)) {
                unsafe.putObject(receiver, offset, value);
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public Object getObject(Node receiver) {
            if (!type.isPrimitive()) {
                return unsafe.getObject(receiver, offset);
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
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
    }

    private static final class ReflectionNodeField extends NodeField {
        private final Field field;

        protected ReflectionNodeField(NodeFieldKind kind, Field field) {
            super(kind, field);
            this.field = field;
            field.setAccessible(true);
        }

        @Override
        public void putObject(Node receiver, Object value) {
            assert !type.isPrimitive() && value == null || type.isInstance(value);
            try {
                field.set(receiver, value);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public Object getObject(Node receiver) {
            assert !type.isPrimitive();
            try {
                return field.get(receiver);
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public Object loadValue(Node node) {
            try {
                if (type == boolean.class) {
                    return field.getBoolean(node);
                } else if (type == byte.class) {
                    return field.getByte(node);
                } else if (type == short.class) {
                    return field.getShort(node);
                } else if (type == char.class) {
                    return field.getChar(node);
                } else if (type == int.class) {
                    return field.getInt(node);
                } else if (type == long.class) {
                    return field.getLong(node);
                } else if (type == float.class) {
                    return field.getFloat(node);
                } else if (type == double.class) {
                    return field.getDouble(node);
                } else {
                    return field.get(node);
                }
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
    }

    /**
     * Information about a {@link Node} class. A single instance of this class is allocated for
     * every subclass of {@link Node} that is used.
     */
    public static final class NodeClass {
        private static final ClassValue<NodeClass> nodeClasses = new ClassValue<NodeClass>() {
            @SuppressWarnings("unchecked")
            @Override
            protected NodeClass computeValue(final Class<?> clazz) {
                assert Node.class.isAssignableFrom(clazz);
                return AccessController.doPrivileged(new PrivilegedAction<NodeClass>() {
                    public NodeClass run() {
                        return new NodeClass((Class<? extends Node>) clazz);
                    }
                });
            }
        };

        // The comprehensive list of all fields.
        private final NodeField[] fields;
        // Separate arrays for the frequently accessed fields.
        private final NodeField parentField;
        private final NodeField[] childFields;
        private final NodeField[] childrenFields;
        private final NodeField[] cloneableFields;

        private final Class<? extends Node> clazz;

        public static NodeClass get(Class<? extends Node> clazz) {
            return nodeClasses.get(clazz);
        }

        public NodeClass(Class<? extends Node> clazz) {
            List<NodeField> fieldsList = new ArrayList<>();
            NodeField parentFieldTmp = null;
            List<NodeField> childFieldList = new ArrayList<>();
            List<NodeField> childrenFieldList = new ArrayList<>();
            List<NodeField> cloneableFieldList = new ArrayList<>();

            for (Field field : getAllFields(clazz)) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }

                NodeField nodeField;
                if (field.getDeclaringClass() == Node.class && field.getName().equals("parent")) {
                    assert Node.class.isAssignableFrom(field.getType());
                    nodeField = NodeField.create(NodeFieldKind.PARENT, field);
                    parentFieldTmp = nodeField;
                } else if (field.getAnnotation(Child.class) != null) {
                    checkChildField(field);
                    nodeField = NodeField.create(NodeFieldKind.CHILD, field);
                    childFieldList.add(nodeField);
                } else if (field.getAnnotation(Children.class) != null) {
                    checkChildrenField(field);
                    nodeField = NodeField.create(NodeFieldKind.CHILDREN, field);
                    childrenFieldList.add(nodeField);
                } else {
                    nodeField = NodeField.create(NodeFieldKind.DATA, field);
                    if (NodeCloneable.class.isAssignableFrom(field.getType())) {
                        cloneableFieldList.add(nodeField);
                    }
                }
                fieldsList.add(nodeField);
            }

            if (parentFieldTmp == null) {
                throw new AssertionError("parent field not found");
            }

            this.fields = fieldsList.toArray(new NodeField[fieldsList.size()]);
            this.parentField = parentFieldTmp;
            this.childFields = childFieldList.toArray(new NodeField[childFieldList.size()]);
            this.childrenFields = childrenFieldList.toArray(new NodeField[childrenFieldList.size()]);
            this.cloneableFields = cloneableFieldList.toArray(new NodeField[cloneableFieldList.size()]);
            this.clazz = clazz;
        }

        private static boolean isNodeType(Class<?> clazz) {
            return Node.class.isAssignableFrom(clazz) || (clazz.isInterface() && NodeInterface.class.isAssignableFrom(clazz));
        }

        private static void checkChildField(Field field) {
            if (!isNodeType(field.getType())) {
                throw new AssertionError("@Child field type must be a subclass of Node or an interface extending NodeInterface (" + field + ")");
            }
            if (Modifier.isFinal(field.getModifiers())) {
                throw new AssertionError("@Child field must not be final (" + field + ")");
            }
        }

        private static void checkChildrenField(Field field) {
            if (!(field.getType().isArray() && isNodeType(field.getType().getComponentType()))) {
                throw new AssertionError("@Children field type must be an array of a subclass of Node or an interface extending NodeInterface (" + field + ")");
            }
            if (!Modifier.isFinal(field.getModifiers())) {
                throw new AssertionError("@Children field must be final (" + field + ")");
            }
        }

        public NodeField[] getFields() {
            return fields;
        }

        public NodeField getParentField() {
            return parentField;
        }

        public NodeField[] getChildFields() {
            return childFields;
        }

        public NodeField[] getChildrenFields() {
            return childrenFields;
        }

        @Override
        public int hashCode() {
            return clazz.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof NodeClass) {
                NodeClass other = (NodeClass) obj;
                return clazz.equals(other.clazz);
            }
            return false;
        }

        public Iterator<Node> makeIterator(Node node) {
            assert clazz.isInstance(node);
            return new NodeIterator(node);
        }

        private final class NodeIterator implements Iterator<Node> {
            private final Node node;
            private final int childrenCount;
            private int index;

            protected NodeIterator(Node node) {
                this.node = node;
                this.index = 0;
                this.childrenCount = childrenCount();
            }

            private int childrenCount() {
                int nodeCount = childFields.length;
                for (NodeField childrenField : childrenFields) {
                    Object[] children = ((Object[]) childrenField.getObject(node));
                    if (children != null) {
                        nodeCount += children.length;
                    }
                }
                return nodeCount;
            }

            private Node nodeAt(int idx) {
                int nodeCount = childFields.length;
                if (idx < nodeCount) {
                    return (Node) childFields[idx].getObject(node);
                } else {
                    for (NodeField childrenField : childrenFields) {
                        Object[] nodeArray = (Object[]) childrenField.getObject(node);
                        if (idx < nodeCount + nodeArray.length) {
                            return (Node) nodeArray[idx - nodeCount];
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

            public boolean hasNext() {
                return index < childrenCount;
            }

            public Node next() {
                try {
                    return nodeAt(index);
                } finally {
                    forward();
                }
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        }
    }

    static Iterator<Node> makeIterator(Node node) {
        return NodeClass.get(node.getClass()).makeIterator(node);
    }

    public static Iterator<Node> makeRecursiveIterator(Node node) {
        return new RecursiveNodeIterator(node);
    }

    private static final class RecursiveNodeIterator implements Iterator<Node> {
        private final List<Iterator<Node>> iteratorStack = new ArrayList<>();

        public RecursiveNodeIterator(final Node node) {
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
        return (T) orig.deepCopy();
    }

    static Node deepCopyImpl(Node orig) {
        final Node clone = orig.copy();
        NodeClass nodeClass = NodeClass.get(clone.getClass());

        nodeClass.parentField.putObject(clone, null);

        for (NodeField childField : nodeClass.childFields) {
            Node child = (Node) childField.getObject(orig);
            if (child != null) {
                Node clonedChild = child.deepCopy();
                nodeClass.parentField.putObject(clonedChild, clone);
                childField.putObject(clone, clonedChild);
            }
        }
        for (NodeField childrenField : nodeClass.childrenFields) {
            Object[] children = (Object[]) childrenField.getObject(orig);
            if (children != null) {
                Object[] clonedChildren = (Object[]) Array.newInstance(children.getClass().getComponentType(), children.length);
                for (int i = 0; i < children.length; i++) {
                    if (children[i] != null) {
                        Node clonedChild = ((Node) children[i]).deepCopy();
                        clonedChildren[i] = clonedChild;
                        nodeClass.parentField.putObject(clonedChild, clone);
                    }
                }
                childrenField.putObject(clone, clonedChildren);
            }
        }
        for (NodeField cloneableField : nodeClass.cloneableFields) {
            Object cloneable = cloneableField.getObject(clone);
            if (cloneable != null && cloneable == cloneableField.getObject(orig)) {
                cloneableField.putObject(clone, ((NodeCloneable) cloneable).clone());
            }
        }
        return clone;
    }

    public static List<Node> findNodeChildren(Node node) {
        List<Node> nodes = new ArrayList<>();
        NodeClass nodeClass = NodeClass.get(node.getClass());

        for (NodeField nodeField : nodeClass.childFields) {
            Object child = nodeField.getObject(node);
            if (child != null) {
                nodes.add((Node) child);
            }
        }
        for (NodeField nodeField : nodeClass.childrenFields) {
            Object[] children = (Object[]) nodeField.getObject(node);
            if (children != null) {
                for (Object child : children) {
                    if (child != null) {
                        nodes.add((Node) child);
                    }
                }
            }
        }

        return nodes;
    }

    public static <T extends Node> T nonAtomicReplace(Node oldNode, T newNode, CharSequence reason) {
        oldNode.replaceHelper(newNode, reason);
        return newNode;
    }

    public static boolean replaceChild(Node parent, Node oldChild, Node newChild) {
        NodeClass nodeClass = NodeClass.get(parent.getClass());

        for (NodeField nodeField : nodeClass.getChildFields()) {
            if (nodeField.getObject(parent) == oldChild) {
                assert assertAssignable(nodeField, newChild);
                nodeField.putObject(parent, newChild);
                return true;
            }
        }

        for (NodeField nodeField : nodeClass.getChildrenFields()) {
            Object arrayObject = nodeField.getObject(parent);
            if (arrayObject != null) {
                Object[] array = (Object[]) arrayObject;
                for (int i = 0; i < array.length; i++) {
                    if (array[i] == oldChild) {
                        assert assertAssignable(nodeField, newChild);
                        array[i] = newChild;
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean assertAssignable(NodeField field, Object newValue) {
        if (newValue == null) {
            return true;
        }
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
        throw new IllegalArgumentException();
    }

    /**
     * Finds the field in a parent node, if any, that holds a specified child node.
     *
     * @return the field (possibly an array) holding the child, {@code null} if not found.
     */
    public static NodeField findChildField(Node parent, Node child) {
        assert child != null;
        NodeClass parentNodeClass = NodeClass.get(parent.getClass());

        for (NodeField field : parentNodeClass.getChildFields()) {
            if (field.getObject(parent) == child) {
                return field;
            }
        }

        for (NodeField field : parentNodeClass.getChildrenFields()) {
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
        return null;
    }

    /**
     * Determines whether a proposed child replacement would be safe: structurally and type.
     */
    public static boolean isReplacementSafe(Node parent, Node oldChild, Node newChild) {
        assert newChild != null;
        if (parent != null) {
            final NodeField field = findChildField(parent, oldChild);
            if (field != null) {
                switch (field.getKind()) {
                    case CHILD:
                        return field.getType().isAssignableFrom(newChild.getClass());
                    case CHILDREN:
                        return field.getType().getComponentType().isAssignableFrom(newChild.getClass());
                    default:
                        throw new IllegalStateException();
                }
            }
        }
        return false;
    }

    /**
     * Executes a closure for every non-null child of the parent node.
     *
     * @return {@code true} if all children were visited, {@code false} otherwise
     */
    public static boolean forEachChild(Node parent, NodeVisitor visitor) {
        Objects.requireNonNull(visitor);
        NodeClass parentNodeClass = NodeClass.get(parent.getClass());

        for (NodeField field : parentNodeClass.getChildFields()) {
            Object child = field.getObject(parent);
            if (child != null) {
                if (!visitor.visit((Node) child)) {
                    return false;
                }
            }
        }

        for (NodeField field : parentNodeClass.getChildrenFields()) {
            Object arrayObject = field.getObject(parent);
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
        }

        return true;
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

    /**
     * Like {@link #findAllNodeInstances(Node, Class)} but do not visit children of found nodes.
     */
    public static <T> List<T> findNodeInstancesShallow(final Node root, final Class<T> clazz) {
        final List<T> nodeList = new ArrayList<>();
        root.accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (clazz.isInstance(node)) {
                    nodeList.add(clazz.cast(node));
                    return false;
                }
                return true;
            }
        });
        return nodeList;
    }

    public static int countNodes(Node root) {
        Iterator<Node> nodeIterator = makeRecursiveIterator(root);
        int count = 0;
        while (nodeIterator.hasNext()) {
            nodeIterator.next();
            count++;
        }
        return count;
    }

    public static int countNodes(Node root, NodeCountFilter filter) {
        Iterator<Node> nodeIterator = makeRecursiveIterator(root);
        int count = 0;
        while (nodeIterator.hasNext()) {
            Node node = nodeIterator.next();
            if (node != null && filter.isCounted(node)) {
                count++;
            }
        }
        return count;
    }

    public interface NodeCountFilter {

        boolean isCounted(Node node);

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
            p.print(getNodeFieldName(parent, node, "unknownField"));
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
        NodeField[] fields = NodeClass.get(parent.getClass()).fields;
        for (NodeField field : fields) {
            Object value = field.loadValue(parent);
            if (field.getKind() == NodeFieldKind.CHILD && value == node) {
                return field.getName();
            } else if (field.getKind() == NodeFieldKind.CHILDREN) {
                int index = 0;
                for (Object arrayNode : (Object[]) value) {
                    if (arrayNode == node) {
                        return field.getName() + "[" + index + "]";
                    }
                    index++;
                }
            }
        }
        return defaultName;
    }

    /**
     * Returns a string listing the {@linkplain SyntaxTag syntax tags}, if any, associated with a
     * node:
     * <ul>
     * <li>"[{@linkplain StandardSyntaxTag#STATEMENT STATEMENT},
     * {@linkplain StandardSyntaxTag#ASSIGNMENT ASSIGNMENT}]" if tags have been applied;</li>
     * <li>"[]" if the node supports tags, but none are present; and</li>
     * <li>"" if the node does not support tags.</li>
     * </ul>
     */
    public static String printSyntaxTags(final Object node) {
        if (node instanceof WrapperNode) {
            final Probe probe = ((WrapperNode) node).getProbe();
            final Collection<SyntaxTag> syntaxTags = probe.getSyntaxTags();
            final StringBuilder sb = new StringBuilder();
            String prefix = "";
            sb.append("[");
            for (SyntaxTag tag : syntaxTags) {
                sb.append(prefix);
                prefix = ",";
                sb.append(tag.toString());
            }
            sb.append("]");
            return sb.toString();
        }
        return "";
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
        return node.getClass().getSimpleName();
    }

    private static String displaySourceAttribution(Node node) {
        final SourceSection section = node.getSourceSection();
        if (section instanceof NullSourceSection) {
            return "source: " + section.getShortDescription();
        }
        if (section != null) {
            final String srcText = section.getCode();
            final StringBuilder sb = new StringBuilder();
            sb.append("source:");
            sb.append(" (" + section.getCharIndex() + "," + (section.getCharEndIndex() - 1) + ")");
            sb.append(" line=" + section.getLineLocation().getLineNumber());
            sb.append(" len=" + srcText.length());
            sb.append(" text=\"" + srcText + "\"");
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
        out.printf("[truffle]   rewrite %-50s |From %-40s |To %-40s |Reason %s%s%n", oldNode.toString(), formatNodeInfo(oldNode), formatNodeInfo(newNode),
                        reason != null && reason.length() > 0 ? reason : "unknown", reportedSourceSection != null ? " at " + reportedSourceSection.getShortDescription() : "");
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
        return cost + " " + node.getClass().getSimpleName();
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
}
