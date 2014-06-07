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
package com.oracle.truffle.api.nodes.serial;

import java.lang.reflect.*;
import java.util.*;

import sun.misc.*;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeClass;
import com.oracle.truffle.api.nodes.NodeUtil.NodeField;
import com.oracle.truffle.api.nodes.NodeUtil.NodeFieldKind;
import com.oracle.truffle.api.source.*;

/**
 * Experimental API. May change without notice.
 */
public final class PostOrderDeserializer {

    private static final Unsafe unsafe = loadUnsafe();

    private final SerializerConstantPool cp;

    private final HierarchicalStack stack = new HierarchicalStack();

    /**
     * Constructs a new serializer using a custom {@link SerializerConstantPool} implementation. For
     * the {@link SerializerConstantPool} implementation at least the following methods must be
     * implemented:
     * <ul>
     * <li>{@link SerializerConstantPool#getInt(int)}</li>
     * <li>{@link SerializerConstantPool#getClass(int)}</li>
     * </ul>
     */
    public PostOrderDeserializer(SerializerConstantPool cp) {
        this.cp = cp;
    }

    /**
     * Deserializes the byte stream and returns the deserialized Truffle AST node.
     *
     * @param bytes the trimmed byte array containing the serialized data
     * @param expectedType the expected root node type. Throws an exception if the root node is not
     *            assignable from this type.
     * @return the deserialized Truffle AST represented by the root Node.
     *
     * @throws UnsupportedConstantPoolTypeException thrown if a type is encountered that is not
     *             supported by the constant pool implementation.
     */
    @SuppressWarnings("unchecked")
    public <T extends Node> T deserialize(byte[] bytes, Class<T> expectedType) throws UnsupportedConstantPoolTypeException {
        VariableLengthIntBuffer buffer = new VariableLengthIntBuffer(bytes);

        while (buffer.hasRemaining()) {
            int classCPI = buffer.get();
            if (classCPI == VariableLengthIntBuffer.NULL) {
                pushNode(null);
            } else {
                Class<?> clazz = cp.getClass(classCPI);
                if (clazz.isArray()) {
                    int lengthCPI = buffer.get();
                    if (lengthCPI == VariableLengthIntBuffer.NULL) {
                        pushArray(null);
                    } else {
                        pushArray((Node[]) Array.newInstance(clazz.getComponentType(), cp.getInt(lengthCPI)));
                    }
                } else {
                    pushNode(invokeDeserialize(buffer, clazz.asSubclass(Node.class)));
                }
            }
        }
        T returnNode = (T) popNode(null, expectedType);

        assert stack.dynamicStack.isEmpty();

        return returnNode;
    }

    private void pushNode(Node node) {
        stack.push(node);
    }

    private void pushArray(Node[] array) {
        stack.pushStack(array);
    }

    private Node[] popArray(Node parent, Class<?> expectedType) {
        Node[] array = (Node[]) stack.popStack();
        if (array != null) {
            assertType(array, expectedType);
            for (int i = 0; i < array.length; i++) {
                updateParent(parent, array[i]);
            }
        }
        return array;
    }

    private Node popNode(Node parent, Class<?> expectedType) {
        Object o = stack.pop();
        assertType(o, expectedType);
        updateParent(parent, (Node) o);
        return (Node) o;
    }

    private static void assertType(Object o, Class<?> expectedType) {
        if (o != null && !expectedType.isAssignableFrom(o.getClass())) {
            throw new AssertionError("Expected element type '" + expectedType.getName() + "' but was '" + o.getClass().getName() + "'.");
        }
    }

    private Node invokeDeserialize(VariableLengthIntBuffer buffer, Class<? extends Node> nodeClass) throws UnsupportedConstantPoolTypeException {
        if (nodeClass == null) {
            return null;
        }

        Object object;
        try {
            object = unsafe.allocateInstance(nodeClass);
        } catch (InstantiationException e) {
            throw new RuntimeException("Unable to allocate truffle node " + nodeClass, e);
        }
        if (!(object instanceof Node)) {
            throw new RuntimeException("Class is not a truffle node " + nodeClass);
        }

        Node node = (Node) object;

        NodeField[] nodeFields = NodeClass.get(nodeClass).getFields();
        deserializeChildrenFields(node, nodeFields);
        deserializeChildFields(node, nodeFields);
        deserializeDataFields(buffer, node, nodeFields);

        return node;
    }

    private void deserializeDataFields(VariableLengthIntBuffer buffer, Node nodeInstance, NodeField[] nodeFields) throws UnsupportedConstantPoolTypeException {
        for (int i = 0; i < nodeFields.length; i++) {
            NodeField field = nodeFields[i];
            if (field.getKind() == NodeFieldKind.DATA) {
                Class<?> fieldClass = field.getType();
                long offset = field.getOffset();

                // source sections are not serialized
                // TODO add support for source sections
                if (field.getType().isAssignableFrom(SourceSection.class)) {
                    continue;
                }

                int cpi = buffer.get();
                if (cpi == VariableLengthIntBuffer.NULL) {
                    if (fieldClass == int.class) {
                        unsafe.putInt(nodeInstance, offset, 0);
                    } else if (fieldClass == long.class) {
                        unsafe.putLong(nodeInstance, offset, 0L);
                    } else if (fieldClass == float.class) {
                        unsafe.putFloat(nodeInstance, offset, 0.0F);
                    } else if (fieldClass == double.class) {
                        unsafe.putDouble(nodeInstance, offset, 0.0D);
                    } else if (fieldClass == byte.class) {
                        unsafe.putByte(nodeInstance, offset, (byte) 0);
                    } else if (fieldClass == short.class) {
                        unsafe.putShort(nodeInstance, offset, (short) 0);
                    } else if (fieldClass == char.class) {
                        unsafe.putChar(nodeInstance, offset, (char) 0);
                    } else if (fieldClass == boolean.class) {
                        unsafe.putBoolean(nodeInstance, offset, false);
                    } else {
                        unsafe.putObject(nodeInstance, offset, null);
                    }
                } else {
                    if (fieldClass == int.class) {
                        unsafe.putInt(nodeInstance, offset, cp.getInt(cpi));
                    } else if (fieldClass == long.class) {
                        unsafe.putLong(nodeInstance, offset, cp.getLong(cpi));
                    } else if (fieldClass == float.class) {
                        unsafe.putFloat(nodeInstance, offset, cp.getFloat(cpi));
                    } else if (fieldClass == double.class) {
                        unsafe.putDouble(nodeInstance, offset, cp.getDouble(cpi));
                    } else if (fieldClass == byte.class) {
                        unsafe.putByte(nodeInstance, offset, (byte) cp.getInt(cpi));
                    } else if (fieldClass == short.class) {
                        unsafe.putShort(nodeInstance, offset, (short) cp.getInt(cpi));
                    } else if (fieldClass == char.class) {
                        unsafe.putChar(nodeInstance, offset, (char) cp.getInt(cpi));
                    } else if (fieldClass == boolean.class) {
                        unsafe.putBoolean(nodeInstance, offset, cp.getInt(cpi) == 1 ? true : false);
                    } else if (fieldClass == Integer.class) {
                        unsafe.putObject(nodeInstance, offset, cp.getInt(cpi));
                    } else if (fieldClass == Long.class) {
                        unsafe.putObject(nodeInstance, offset, cp.getLong(cpi));
                    } else if (fieldClass == Float.class) {
                        unsafe.putObject(nodeInstance, offset, cp.getFloat(cpi));
                    } else if (fieldClass == Double.class) {
                        unsafe.putObject(nodeInstance, offset, cp.getDouble(cpi));
                    } else if (fieldClass == Byte.class) {
                        unsafe.putObject(nodeInstance, offset, (byte) cp.getInt(cpi));
                    } else if (fieldClass == Short.class) {
                        unsafe.putObject(nodeInstance, offset, (short) cp.getInt(cpi));
                    } else if (fieldClass == Character.class) {
                        unsafe.putObject(nodeInstance, offset, (char) cp.getInt(cpi));
                    } else if (fieldClass == Boolean.class) {
                        unsafe.putObject(nodeInstance, offset, cp.getInt(cpi) == 1 ? Boolean.TRUE : Boolean.FALSE);
                    } else {
                        unsafe.putObject(nodeInstance, offset, cp.getObject(fieldClass, cpi));
                    }
                }
            }
        }
    }

    private void deserializeChildFields(Node parent, NodeField[] nodeFields) {
        for (int i = nodeFields.length - 1; i >= 0; i--) {
            NodeField field = nodeFields[i];
            if (field.getKind() == NodeFieldKind.CHILD) {
                unsafe.putObject(parent, field.getOffset(), popNode(parent, field.getType()));
            }
        }
    }

    private void deserializeChildrenFields(Node parent, NodeField[] nodeFields) {
        for (int i = nodeFields.length - 1; i >= 0; i--) {
            NodeField field = nodeFields[i];
            if (field.getKind() == NodeFieldKind.CHILDREN) {
                unsafe.putObject(parent, field.getOffset(), popArray(parent, field.getType()));
            }
        }
    }

    private static Node updateParent(Node parent, Node child) {
        if (child != null) {
            long parentOffset = NodeClass.get(child.getClass()).getParentOffset();
            unsafe.putObject(child, parentOffset, parent);
        }
        return child;
    }

    private static Unsafe loadUnsafe() {
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

    private static class HierarchicalStack {

        private static final Object NULL_STACK = new Object();

        private final List<Object> dynamicStack = new ArrayList<>();

        void pushStack(Object[] array) {
            if (array == null) {
                dynamicStack.add(NULL_STACK);
            } else {
                dynamicStack.add(new FixedSizeNodeStack(array));
            }
        }

        private FixedSizeNodeStack getTosStack() {
            if (dynamicStack.isEmpty()) {
                return null;
            }
            Object peekTos = dynamicStack.get(dynamicStack.size() - 1);
            if (peekTos != null && peekTos.getClass() == FixedSizeNodeStack.class) {
                return (FixedSizeNodeStack) peekTos;
            }
            return null;
        }

        Object[] popStack() {
            Object tos = dynamicStack.remove(dynamicStack.size() - 1);
            if (tos == NULL_STACK) {
                return null;
            }
            return ((FixedSizeNodeStack) tos).getArray();
        }

        void push(Object o) {
            FixedSizeNodeStack tosStack = getTosStack();
            if (tosStack != null && !tosStack.isFull()) {
                tosStack.push(o);
            } else {
                dynamicStack.add(o);
            }
        }

        Object pop() {
            FixedSizeNodeStack tosStack = getTosStack();
            Object value;
            if (tosStack != null) {
                assert !tosStack.isEmpty();
                value = tosStack.pop();
            } else {
                value = dynamicStack.remove(dynamicStack.size() - 1);
            }
            assert value != NULL_STACK;
            return value;
        }

    }

    private static class FixedSizeNodeStack {

        private final Object[] array;

        private int tos;

        FixedSizeNodeStack(Object[] array) {
            this.array = array;
        }

        boolean isFull() {
            return tos == array.length;
        }

        boolean isEmpty() {
            return tos == 0;
        }

        private void push(Object node) {
            if (tos >= array.length) {
                throw new ArrayIndexOutOfBoundsException();
            }
            unsafe.putObject(array, Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * (long) (tos++), node);
        }

        private Object pop() {
            if (tos <= 0) {
                throw new ArrayIndexOutOfBoundsException();
            }
            return unsafe.getObject(array, Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * (long) (--tos));
        }

        private Object[] getArray() {
            return array;
        }
    }

}
