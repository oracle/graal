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

import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;

import sun.misc.Unsafe;

/**
 * Information about a {@link Node} class. A single instance of this class is allocated for every
 * subclass of {@link Node} that is used.
 */
final class NodeClassImpl extends NodeClass {
    private static final NodeFieldData[] EMPTY_NODE_FIELD_ARRAY = new NodeFieldData[0];

    private final NodeFieldData[] fields;
    private final Class<? extends Node> clazz;
    private final boolean replaceAllowed;

    NodeClassImpl(Class<? extends Node> clazz) {
        super(clazz);
        if (!Node.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException();
        }
        List<NodeFieldData> fieldsList = new ArrayList<>();
        collectInstanceFields(clazz, fieldsList);

        Collections.sort(fieldsList, new Comparator<NodeFieldData>() {
            public int compare(NodeFieldData o1, NodeFieldData o2) {
                return Integer.compare(order(o1), order(o2));
            }

            private int order(NodeFieldData nodeField) {
                return isChildField(nodeField) ? 0 : (isChildrenField(nodeField) ? 0 : (isCloneableField(nodeField) ? 1 : 2));
            }
        });

        if (clazz.getAnnotation(DenyReplace.class) != null) {
            if (!Modifier.isFinal(clazz.getModifiers())) {
                throw new IllegalStateException("@DenyReplace can only be used for final classes.");
            }
            replaceAllowed = false;
        } else {
            replaceAllowed = true;
        }

        this.fields = fieldsList.toArray(EMPTY_NODE_FIELD_ARRAY);
        this.clazz = clazz;
    }

    @Override
    protected boolean isReplaceAllowed() {
        return replaceAllowed;
    }

    private static void collectInstanceFields(Class<? extends Object> clazz, List<NodeFieldData> fieldsList) {
        if (clazz.getSuperclass() != null) {
            collectInstanceFields(clazz.getSuperclass(), fieldsList);
        }
        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field field : declaredFields) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                continue;
            }

            NodeFieldData nodeField;
            if (field.getDeclaringClass() == Node.class && (field.getName().equals("parent"))) {
                continue;
            }
            nodeField = createField(field);
            fieldsList.add(nodeField);
        }
    }

    private static NodeFieldData createField(Field field) {
        if (field.getAnnotation(Child.class) != null) {
            checkChildField(field);
            return new NodeFieldData(NodeFieldKind.CHILD, field);
        } else if (field.getAnnotation(Children.class) != null) {
            checkChildrenField(field);
            return new NodeFieldData(NodeFieldKind.CHILDREN, field);
        } else {
            return new NodeFieldData(NodeFieldKind.DATA, field);
        }
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
    Field[] getAccessedFields() {
        /*
         * Any field read using unsafe is returned here.
         */
        Field[] reflectionFields = new Field[fields.length];
        for (int i = 0; i < fields.length; i++) {
            try {
                reflectionFields[i] = fields[i].declaringClass.getDeclaredField(fields[i].name);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
        return reflectionFields;
    }

    private static boolean isNodeType(Class<?> clazz) {
        return Node.class.isAssignableFrom(clazz) || (clazz.isInterface() && NodeInterface.class.isAssignableFrom(clazz));
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
    protected Object[] getNodeFieldArray() {
        return fields;
    }

    @Override
    protected void putFieldObject(Object field, Node receiver, Object value) {
        ((NodeFieldData) field).putObject(receiver, value);
    }

    @Override
    protected Object getFieldObject(Object field, Node receiver) {
        return ((NodeFieldData) field).getObject(receiver);
    }

    @Override
    protected Object getFieldValue(Object field, Node receiver) {
        return ((NodeFieldData) field).getObjectOrPrimitive(receiver);
    }

    @Override
    protected Class<?> getFieldType(Object field) {
        return ((NodeFieldData) field).type;
    }

    @Override
    protected String getFieldName(Object field) {
        return ((NodeFieldData) field).name;
    }

    @Override
    protected boolean isChildField(Object field) {
        return ((NodeFieldData) field).kind == NodeFieldKind.CHILD;
    }

    @Override
    protected boolean isChildrenField(Object field) {
        return ((NodeFieldData) field).kind == NodeFieldKind.CHILDREN;
    }

    @Override
    protected boolean isCloneableField(Object field) {
        return ((NodeFieldData) field).clonable;
    }

    @Override
    boolean nodeFieldsOrderedByKind() {
        return true;
    }

    enum NodeFieldKind {
        CHILD,
        CHILDREN,
        DATA
    }

    static final class NodeFieldData {

        final NodeFieldKind kind;
        final Class<?> type;
        final String name;
        final Class<?> declaringClass;
        final long offset;
        final boolean clonable;

        @SuppressWarnings("deprecation"/* JDK-8277863 */)
        NodeFieldData(NodeFieldKind kind, Field field) {
            this.kind = kind;
            this.type = field.getType();
            this.name = field.getName();
            this.declaringClass = field.getDeclaringClass();
            this.offset = UNSAFE.objectFieldOffset(field);
            this.clonable = kind == NodeFieldKind.DATA && NodeCloneable.class.isAssignableFrom(field.getType());
        }

        long getOffset() {
            return offset;
        }

        public void putObject(Node receiver, Object value) {
            assert validateAccess(receiver, value);
            UNSAFE.putObject(receiver, getOffset(), value);
        }

        private boolean validateAccess(Node receiver, Object value) {
            if (type.isPrimitive() || !type.isInstance(value)) {
                throw illegalArgumentException(value);
            }
            if (kind != NodeFieldKind.CHILD) {
                Object oldValue = getObject(receiver);
                if (oldValue == null || value == null) {
                    if (oldValue != value) {
                        throw illegalArgumentException(value);
                    }
                } else {
                    if (oldValue.getClass() != value.getClass()) {
                        assert !(value instanceof Node) || ((Node) value).getNodeClass().isReplaceAllowed() : "type change not allowed if replace not allowed";
                        assert !(oldValue instanceof Node) || ((Node) oldValue).getNodeClass().isReplaceAllowed() : "type change not allowed if replace not allowed";
                        throw illegalArgumentException(value);
                    }
                }
            }
            return true;
        }

        private IllegalArgumentException illegalArgumentException(Object value) {
            return new IllegalArgumentException("Cannot set " + type.getName() + " field " + toString() + " to " + (value == null ? "null" : value.getClass().getName()));
        }

        public Object getObject(Node receiver) {
            if (!type.isPrimitive()) {
                return UNSAFE.getObject(receiver, getOffset());
            } else {
                throw new IllegalArgumentException();
            }
        }

        public Object getObjectOrPrimitive(Node node) {
            if (type == boolean.class) {
                return UNSAFE.getBoolean(node, getOffset());
            } else if (type == byte.class) {
                return UNSAFE.getByte(node, getOffset());
            } else if (type == short.class) {
                return UNSAFE.getShort(node, getOffset());
            } else if (type == char.class) {
                return UNSAFE.getChar(node, getOffset());
            } else if (type == int.class) {
                return UNSAFE.getInt(node, getOffset());
            } else if (type == long.class) {
                return UNSAFE.getLong(node, getOffset());
            } else if (type == float.class) {
                return UNSAFE.getFloat(node, getOffset());
            } else if (type == double.class) {
                return UNSAFE.getDouble(node, getOffset());
            } else {
                return getObject(node);
            }
        }

        private static final Unsafe UNSAFE = getUnsafe();

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

        @Override
        public String toString() {
            return declaringClass.getName() + "." + name;
        }
    }
}
