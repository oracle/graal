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

import java.lang.reflect.*;

import sun.misc.*;

import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeUtil.FieldOffsetProvider;

/**
 * Information about a field in a {@link Node} class.
 */
public abstract class NodeFieldAccessor {

    public static enum NodeFieldKind {
        /** The reference to the {@link NodeClass}. */
        NODE_CLASS,
        /** The single {@link Node#getParent() parent} field. */
        PARENT,
        /** A field annotated with {@link Child}. */
        CHILD,
        /** A field annotated with {@link Children}. */
        CHILDREN,
        /** A normal non-child data field of the node. */
        DATA
    }

    private static final boolean USE_UNSAFE = Boolean.getBoolean("truffle.unsafe");

    private final NodeFieldKind kind;
    private final String name;
    protected final Class<?> type;
    protected final long offset;

    protected NodeFieldAccessor(NodeFieldKind kind, Field field) {
        this.kind = kind;
        this.type = field.getType();
        this.name = field.getName();
        this.offset = unsafeFieldOffsetProvider.objectFieldOffset(field);
    }

    protected static NodeFieldAccessor create(NodeFieldKind kind, Field field) {
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
        if (obj instanceof NodeFieldAccessor) {
            NodeFieldAccessor other = (NodeFieldAccessor) obj;
            return offset == other.offset && name.equals(other.name) && type.equals(other.type) && kind.equals(other.kind);
        }
        return false;
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

    private static final class UnsafeNodeField extends NodeFieldAccessor {

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

    private static final class ReflectionNodeField extends NodeFieldAccessor {
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

}
