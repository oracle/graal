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
import java.nio.*;

import sun.misc.*;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.NodeClass;
import com.oracle.truffle.api.nodes.NodeUtil.NodeField;
import com.oracle.truffle.api.nodes.NodeUtil.NodeFieldKind;
import com.oracle.truffle.api.source.*;

/**
 * Experimental API. May change without notice.
 */
public final class PostOrderSerializer {

    private static final Unsafe unsafe = loadUnsafe();

    private final SerializerConstantPool cp;

    /**
     * Constructs a new deserializer using a custom {@link SerializerConstantPool} implementation.
     * For the {@link SerializerConstantPool} implementation at least the following methods must be
     * implemented:
     * <ul>
     * <li>{@link SerializerConstantPool#putInt(int)}</li>
     * <li>{@link SerializerConstantPool#putClass(Class)}</li>
     * </ul>
     */
    public PostOrderSerializer(SerializerConstantPool cp) {
        this.cp = cp;
    }

    /**
     * Serializes the node AST and returns the serialized data as byte array.
     *
     * @param node the root node that represents the Truffle AST that should be serialized.
     * @return a trimmed byte array that contains the serialized data.
     *
     * @throws UnsupportedConstantPoolTypeException thrown if a type is encountered that is not
     *             supported by the constant pool implementation.
     */
    public byte[] serialize(Node node) throws UnsupportedConstantPoolTypeException {
        VariableLengthIntBuffer buffer = new VariableLengthIntBuffer(ByteBuffer.allocate(512));
        serialize(buffer, node);
        return buffer.getBytes();
    }

    private void serialize(VariableLengthIntBuffer buffer, Node node) throws UnsupportedConstantPoolTypeException {
        if (node == null) {
            buffer.put(VariableLengthIntBuffer.NULL);
            return;
        }
        Class<? extends Node> nodeClass = node.getClass();

        NodeField[] nodeFields = NodeClass.get(nodeClass).getFields();
        serializeChildFields(buffer, node, nodeFields);
        serializeChildrenFields(buffer, node, nodeFields);
        buffer.put(cp.putClass(node.getClass()));
        serializeDataFields(buffer, node, nodeFields);
    }

    private void serializeDataFields(VariableLengthIntBuffer buffer, Node node, NodeField[] nodeFields) throws UnsupportedConstantPoolTypeException {
        for (int i = 0; i < nodeFields.length; i++) {
            NodeField field = nodeFields[i];
            if (field.getKind() == NodeFieldKind.DATA) {
                Class<?> fieldClass = field.getType();
                long offset = field.getOffset();
                int cpi;

                if (field.getType().isAssignableFrom(SourceSection.class)) {
                    continue;
                }

                if (fieldClass == int.class) {
                    cpi = cp.putInt(unsafe.getInt(node, offset));
                } else if (fieldClass == long.class) {
                    cpi = cp.putLong(unsafe.getLong(node, offset));
                } else if (fieldClass == float.class) {
                    cpi = cp.putFloat(unsafe.getFloat(node, offset));
                } else if (fieldClass == double.class) {
                    cpi = cp.putDouble(unsafe.getDouble(node, offset));
                } else if (fieldClass == byte.class) {
                    cpi = cp.putInt(unsafe.getByte(node, offset));
                } else if (fieldClass == short.class) {
                    cpi = cp.putInt(unsafe.getShort(node, offset));
                } else if (fieldClass == char.class) {
                    cpi = cp.putInt(unsafe.getChar(node, offset));
                } else if (fieldClass == boolean.class) {
                    cpi = cp.putInt(unsafe.getBoolean(node, offset) ? 1 : 0);
                } else {
                    cpi = serializeDataFieldsObject(node, fieldClass, offset);
                }

                buffer.put(cpi);
            }
        }
    }

    private int serializeDataFieldsObject(Node node, Class<?> fieldClass, long offset) {
        Object value = unsafe.getObject(node, offset);
        if (value == null) {
            return VariableLengthIntBuffer.NULL;
        } else if (fieldClass == Integer.class) {
            return cp.putInt((Integer) value);
        } else if (fieldClass == Long.class) {
            return cp.putLong((Long) value);
        } else if (fieldClass == Float.class) {
            return cp.putFloat((Float) value);
        } else if (fieldClass == Double.class) {
            return cp.putDouble((Double) value);
        } else if (fieldClass == Byte.class) {
            return cp.putInt((Byte) value);
        } else if (fieldClass == Short.class) {
            return cp.putInt((Short) value);
        } else if (fieldClass == Character.class) {
            return cp.putInt((Character) value);
        } else if (fieldClass == Boolean.class) {
            return cp.putInt((Boolean) value ? 1 : 0);
        } else {
            return cp.putObject(fieldClass, value);
        }
    }

    private void serializeChildrenFields(VariableLengthIntBuffer buffer, Node nodeInstance, NodeField[] nodeFields) throws UnsupportedConstantPoolTypeException {
        for (int i = 0; i < nodeFields.length; i++) {
            NodeField field = nodeFields[i];
            if (field.getKind() == NodeFieldKind.CHILDREN) {
                Object childArrayObject = unsafe.getObject(nodeInstance, field.getOffset());
                if (childArrayObject != null && !(childArrayObject instanceof Node[])) {
                    throw new AssertionError("Node children must be instanceof Node[]");
                }

                buffer.put(cp.putClass(field.getType()));

                Node[] childArray = (Node[]) childArrayObject;
                if (childArray == null) {
                    buffer.put(VariableLengthIntBuffer.NULL);
                } else {
                    buffer.put(cp.putInt(childArray.length));

                    for (int j = 0; j < childArray.length; j++) {
                        serialize(buffer, childArray[j]);
                    }
                }
            }
        }
    }

    private void serializeChildFields(VariableLengthIntBuffer buffer, Node nodeInstance, NodeField[] nodeFields) throws UnsupportedConstantPoolTypeException {
        for (int i = 0; i < nodeFields.length; i++) {
            NodeField field = nodeFields[i];
            if (field.getKind() == NodeFieldKind.CHILD) {
                Object childObject = unsafe.getObject(nodeInstance, field.getOffset());
                if (childObject != null && !(childObject instanceof Node)) {
                    throw new AssertionError("Node children must be instanceof Node");
                }
                serialize(buffer, (Node) childObject);
            }
        }
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

}
