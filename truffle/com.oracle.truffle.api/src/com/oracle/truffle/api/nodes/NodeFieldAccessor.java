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

import sun.misc.Unsafe;

import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;

/**
 * Information about a field in a {@link Node} class.
 */
public abstract class NodeFieldAccessor {

    public enum NodeFieldKind {
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
    private final Class<?> declaringClass;
    private final String name;
    protected final Class<?> type;

    protected NodeFieldAccessor(NodeFieldKind kind, Class<?> declaringClass, String name, Class<?> type) {
        this.kind = kind;
        this.declaringClass = declaringClass;
        this.name = name;
        this.type = type;
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

    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    public String getName() {
        return name;
    }

    /**
     * @deprecated The visibility of this method will be reduced to protected. Do not use.
     */
    @Deprecated
    public abstract void putObject(Node receiver, Object value);

    public abstract Object getObject(Node receiver);

    public abstract Object loadValue(Node node);

    public abstract static class AbstractUnsafeNodeFieldAccessor extends NodeFieldAccessor {

        protected AbstractUnsafeNodeFieldAccessor(NodeFieldKind kind, Class<?> declaringClass, String name, Class<?> type) {
            super(kind, declaringClass, name, type);
        }

        public abstract long getOffset();

        @SuppressWarnings("deprecation")
        @Override
        public void putObject(Node receiver, Object value) {
            if (!type.isPrimitive() && value == null || type.isInstance(value)) {
                unsafe.putObject(receiver, getOffset(), value);
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public Object getObject(Node receiver) {
            if (!type.isPrimitive()) {
                return unsafe.getObject(receiver, getOffset());
            } else {
                throw new IllegalArgumentException();
            }
        }

        @Override
        public Object loadValue(Node node) {
            if (type == boolean.class) {
                return unsafe.getBoolean(node, getOffset());
            } else if (type == byte.class) {
                return unsafe.getByte(node, getOffset());
            } else if (type == short.class) {
                return unsafe.getShort(node, getOffset());
            } else if (type == char.class) {
                return unsafe.getChar(node, getOffset());
            } else if (type == int.class) {
                return unsafe.getInt(node, getOffset());
            } else if (type == long.class) {
                return unsafe.getLong(node, getOffset());
            } else if (type == float.class) {
                return unsafe.getFloat(node, getOffset());
            } else if (type == double.class) {
                return unsafe.getDouble(node, getOffset());
            } else {
                return unsafe.getObject(node, getOffset());
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
    }

    private static final class UnsafeNodeField extends AbstractUnsafeNodeFieldAccessor {
        private final long offset;

        protected UnsafeNodeField(NodeFieldKind kind, Field field) {
            super(kind, field.getDeclaringClass(), field.getName(), field.getType());
            this.offset = AbstractUnsafeNodeFieldAccessor.unsafe.objectFieldOffset(field);
        }

        @Override
        public long getOffset() {
            return offset;
        }
    }

    private static final class ReflectionNodeField extends NodeFieldAccessor {
        private final Field field;

        protected ReflectionNodeField(NodeFieldKind kind, Field field) {
            super(kind, field.getDeclaringClass(), field.getName(), field.getType());
            this.field = field;
            field.setAccessible(true);
        }

        @SuppressWarnings("deprecation")
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
