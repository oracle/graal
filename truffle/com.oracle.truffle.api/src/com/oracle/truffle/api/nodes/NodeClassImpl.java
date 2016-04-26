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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind;

/**
 * Information about a {@link Node} class. A single instance of this class is allocated for every
 * subclass of {@link Node} that is used.
 */
@SuppressWarnings("deprecation")
final class NodeClassImpl extends NodeClass {
    private static final NodeFieldAccessor[] EMPTY_NODE_FIELD_ARRAY = new NodeFieldAccessor[0];

    // The comprehensive list of all fields.
    private final NodeFieldAccessor[] fields;
    private final NodeFieldAccessor parentField;
    private final NodeFieldAccessor nodeClassField;

    private final Class<? extends Node> clazz;

    NodeClassImpl(Class<? extends Node> clazz) {
        super(clazz);
        if (!Node.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException();
        }

        List<NodeFieldAccessor> fieldsList = new ArrayList<>();
        NodeFieldAccessor parentFieldTmp = null;
        NodeFieldAccessor nodeClassFieldTmp = null;

        try {
            Field field = Node.class.getDeclaredField("parent");
            assert Node.class.isAssignableFrom(field.getType());
            parentFieldTmp = NodeFieldAccessor.create(NodeFieldKind.PARENT, field);
            field = Node.class.getDeclaredField("nodeClass");
            assert NodeClass.class.isAssignableFrom(field.getType());
            nodeClassFieldTmp = NodeFieldAccessor.create(NodeFieldKind.NODE_CLASS, field);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Node field not found", e);
        }

        collectInstanceFields(clazz, fieldsList);

        this.fields = fieldsList.toArray(EMPTY_NODE_FIELD_ARRAY);
        this.nodeClassField = nodeClassFieldTmp;
        this.parentField = parentFieldTmp;
        this.clazz = clazz;
    }

    private static void collectInstanceFields(Class<? extends Object> clazz, List<NodeFieldAccessor> fieldsList) {
        if (clazz.getSuperclass() != null) {
            collectInstanceFields(clazz.getSuperclass(), fieldsList);
        }
        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field field : declaredFields) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }

            NodeFieldAccessor nodeField;
            if (field.getDeclaringClass() == Node.class && (field.getName().equals("parent") || field.getName().equals("nodeClass"))) {
                continue;
            } else if (field.getAnnotation(Child.class) != null) {
                checkChildField(field);
                nodeField = NodeFieldAccessor.create(NodeFieldKind.CHILD, field);
            } else if (field.getAnnotation(Children.class) != null) {
                checkChildrenField(field);
                nodeField = NodeFieldAccessor.create(NodeFieldKind.CHILDREN, field);
            } else {
                nodeField = NodeFieldAccessor.create(NodeFieldKind.DATA, field);
            }
            fieldsList.add(nodeField);
        }
    }

    @Override
    public NodeFieldAccessor getNodeClassField() {
        return nodeClassField;
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

    @Override
    public NodeFieldAccessor getParentField() {
        return parentField;
    }

    @Override
    public int hashCode() {
        return clazz.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof NodeClassImpl) {
            NodeClassImpl other = (NodeClassImpl) obj;
            return clazz.equals(other.clazz);
        }
        return false;
    }

    @Override
    public Iterator<Node> makeIterator(Node node) {
        assert clazz.isInstance(node);
        return new NodeIterator(this, node);
    }

    @Override
    public Class<? extends Node> getType() {
        return clazz;
    }

    @Override
    protected Iterable<NodeFieldAccessor> getNodeFields() {
        return getNodeFields(null);
    }

    /**
     * Functional interface equivalent to {@code Predicate<NodeFieldAccessor>}.
     */
    private interface NodeFieldFilter {
        boolean test(NodeFieldAccessor field);
    }

    private Iterable<NodeFieldAccessor> getNodeFields(final NodeFieldFilter filter) {
        return new Iterable<NodeFieldAccessor>() {
            public Iterator<NodeFieldAccessor> iterator() {
                return new Iterator<NodeFieldAccessor>() {
                    private int cursor = -1;
                    {
                        forward();
                    }

                    private void forward() {
                        for (int i = cursor + 1; i < fields.length; i++) {
                            NodeFieldAccessor field = fields[i];
                            if (filter == null || filter.test(field)) {
                                cursor = i;
                                return;
                            }
                        }
                        cursor = fields.length;
                    }

                    public boolean hasNext() {
                        assert cursor >= 0;
                        return cursor < fields.length;
                    }

                    public NodeFieldAccessor next() {
                        if (hasNext()) {
                            NodeFieldAccessor next = fields[cursor];
                            forward();
                            return next;
                        } else {
                            throw new NoSuchElementException();
                        }
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    @Override
    public NodeFieldAccessor[] getFields() {
        return iterableToArray(getNodeFields());
    }

    @Override
    public NodeFieldAccessor[] getChildFields() {
        return iterableToArray(getNodeFields(new NodeFieldFilter() {
            public boolean test(NodeFieldAccessor field) {
                return field.isChildField();
            }
        }));
    }

    @Override
    public NodeFieldAccessor[] getChildrenFields() {
        return iterableToArray(getNodeFields(new NodeFieldFilter() {
            public boolean test(NodeFieldAccessor field) {
                return field.isChildrenField();
            }
        }));
    }

    @Override
    public NodeFieldAccessor[] getCloneableFields() {
        return iterableToArray(getNodeFields(new NodeFieldFilter() {
            public boolean test(NodeFieldAccessor field) {
                return field.isCloneableField();
            }
        }));
    }

    private static NodeFieldAccessor[] iterableToArray(Iterable<NodeFieldAccessor> fields) {
        ArrayList<NodeFieldAccessor> fieldList = new ArrayList<>();
        for (NodeFieldAccessor field : fields) {
            fieldList.add(field);
        }
        return fieldList.toArray(EMPTY_NODE_FIELD_ARRAY);
    }

    private static final class NodeIterator implements Iterator<Node> {
        private final NodeFieldAccessor[] fields;
        private final Node node;

        private int fieldIndex;
        private Node next;
        private int childrenIndex;
        private Object[] children;

        protected NodeIterator(NodeClassImpl nodeClass, Node node) {
            this.fields = nodeClass.fields;
            this.node = node;
            advance();
        }

        private void advance() {
            if (advanceChildren()) {
                return;
            }
            while (fieldIndex < fields.length) {
                NodeFieldAccessor field = fields[fieldIndex];
                fieldIndex++;
                if (field.isChildField()) {
                    next = (Node) field.getObject(node);
                    return;
                } else if (field.isChildrenField()) {
                    children = (Object[]) field.getObject(node);
                    childrenIndex = 0;
                    if (advanceChildren()) {
                        return;
                    }
                }
            }
            next = null;
        }

        private boolean advanceChildren() {
            if (children == null) {
                return false;
            } else if (childrenIndex < children.length) {
                next = (Node) children[childrenIndex];
                childrenIndex++;
                return true;
            } else {
                children = null;
                childrenIndex = 0;
                return false;
            }
        }

        public boolean hasNext() {
            return next != null;
        }

        public Node next() {
            Node result = next;
            if (result == null) {
                throw new NoSuchElementException();
            }
            advance();
            return result;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
