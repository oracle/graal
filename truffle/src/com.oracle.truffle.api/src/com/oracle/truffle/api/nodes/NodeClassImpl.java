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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;

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

    private final Class<? extends Node> clazz;

    NodeClassImpl(Class<? extends Node> clazz) {
        super(clazz);
        if (!Node.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException();
        }

        List<NodeFieldAccessor> fieldsList = new ArrayList<>();
        NodeFieldAccessor parentFieldTmp = null;

        try {
            Field field = Node.class.getDeclaredField("parent");
            assert Node.class.isAssignableFrom(field.getType());
            parentFieldTmp = NodeFieldAccessor.create(NodeFieldAccessor.NodeFieldKind.PARENT, field);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Node field not found", e);
        }

        collectInstanceFields(clazz, fieldsList);

        Collections.sort(fieldsList, new Comparator<NodeFieldAccessor>() {
            public int compare(NodeFieldAccessor o1, NodeFieldAccessor o2) {
                return Integer.compare(order(o1), order(o2));
            }

            private int order(NodeFieldAccessor nodeField) {
                return isChildField(nodeField) ? 0 : (isChildrenField(nodeField) ? 0 : (isCloneableField(nodeField) ? 1 : 2));
            }
        });

        this.fields = fieldsList.toArray(EMPTY_NODE_FIELD_ARRAY);
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
                nodeField = NodeFieldAccessor.create(NodeFieldAccessor.NodeFieldKind.CHILD, field);
            } else if (field.getAnnotation(Children.class) != null) {
                checkChildrenField(field);
                nodeField = NodeFieldAccessor.create(NodeFieldAccessor.NodeFieldKind.CHILDREN, field);
            } else {
                nodeField = NodeFieldAccessor.create(NodeFieldAccessor.NodeFieldKind.DATA, field);
            }
            fieldsList.add(nodeField);
        }
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
        return new NodeIterator(this, node, fields);
    }

    @Override
    public Class<? extends Node> getType() {
        return clazz;
    }

    @Override
    protected Iterable<NodeFieldAccessor> getNodeFields() {
        return getNodeFields(null);
    }

    @Override
    protected NodeFieldAccessor[] getNodeFieldArray() {
        return fields;
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
        return getNodeFieldArray();
    }

    @Override
    public NodeFieldAccessor[] getChildFields() {
        return iterableToArray(getNodeFields(new NodeFieldFilter() {
            public boolean test(NodeFieldAccessor field) {
                return isChildField(field);
            }
        }));
    }

    @Override
    public NodeFieldAccessor[] getChildrenFields() {
        return iterableToArray(getNodeFields(new NodeFieldFilter() {
            public boolean test(NodeFieldAccessor field) {
                return isChildrenField(field);
            }
        }));
    }

    @Override
    public NodeFieldAccessor[] getCloneableFields() {
        return iterableToArray(getNodeFields(new NodeFieldFilter() {
            public boolean test(NodeFieldAccessor field) {
                return isCloneableField(field);
            }
        }));
    }

    private static NodeFieldAccessor[] iterableToArray(Iterable<NodeFieldAccessor> fields) {
        ArrayList<NodeFieldAccessor> fieldList = new ArrayList<>();
        for (NodeFieldAccessor field : fields) {
            fieldList.add(field);
        }
        return fieldList.toArray(new NodeFieldAccessor[0]);
    }

    @Override
    protected void putFieldObject(Object field, Node receiver, Object value) {
        ((NodeFieldAccessor) field).putObject(receiver, value);
    }

    @Override
    protected Object getFieldObject(Object field, Node receiver) {
        return ((NodeFieldAccessor) field).getObject(receiver);
    }

    @Override
    protected Object getFieldValue(Object field, Node receiver) {
        return ((NodeFieldAccessor) field).loadValue(receiver);
    }

    @Override
    protected Class<?> getFieldType(Object field) {
        return ((NodeFieldAccessor) field).getType();
    }

    @Override
    protected String getFieldName(Object field) {
        return ((NodeFieldAccessor) field).getName();
    }

    @Override
    protected boolean isChildField(Object field) {
        return ((NodeFieldAccessor) field).getKind() == NodeFieldAccessor.NodeFieldKind.CHILD;
    }

    @Override
    protected boolean isChildrenField(Object field) {
        return ((NodeFieldAccessor) field).getKind() == NodeFieldAccessor.NodeFieldKind.CHILDREN;
    }

    @Override
    protected boolean isCloneableField(Object field) {
        return ((NodeFieldAccessor) field).getKind() == NodeFieldAccessor.NodeFieldKind.DATA && NodeCloneable.class.isAssignableFrom(((NodeFieldAccessor) field).getType());
    }

    @Override
    boolean nodeFieldsOrderedByKind() {
        return true;
    }

}
