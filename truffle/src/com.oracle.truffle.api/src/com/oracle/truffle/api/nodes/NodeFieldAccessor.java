/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;

import sun.misc.Unsafe;

/**
 * Information about a field in a {@link Node} class.
 *
 * @since 0.8 or earlier
 */
@Deprecated
public abstract class NodeFieldAccessor {
    /** @since 0.8 or earlier */
    public enum NodeFieldKind {
        /**
         * The reference to the {@link NodeClass}.
         *
         * @since 0.8 or earlier
         */
        NODE_CLASS,
        /**
         * The single {@link Node#getParent() parent} field.
         *
         * @since 0.8 or earlier
         */
        PARENT,
        /**
         * A field annotated with {@link Child}.
         *
         * @since 0.8 or earlier
         */
        CHILD,
        /**
         * A field annotated with {@link Children}.
         *
         * @since 0.8 or earlier
         */
        CHILDREN,
        /**
         * A normal non-child data field of the node.
         *
         * @since 0.8 or earlier
         */
        DATA
    }

    private static final boolean USE_UNSAFE = Boolean.parseBoolean(System.getProperty("truffle.unsafe", "true"));

    private final NodeFieldKind kind;
    private final Class<?> declaringClass;
    private final String name;
    /** @since 0.8 or earlier */
    protected final Class<?> type;

    /** @since 0.8 or earlier */
    protected NodeFieldAccessor(NodeFieldKind kind, Class<?> declaringClass, String name, Class<?> type) {
        this.kind = kind;
        this.declaringClass = declaringClass;
        this.name = name;
        /** @since 0.8 or earlier */
        this.type = type;
    }

    /** @since 0.8 or earlier */
    protected static NodeFieldAccessor create(NodeFieldKind kind, Field field) {
        if (USE_UNSAFE) {
            return new UnsafeNodeField(kind, field);
        } else {
            return new ReflectionNodeField(kind, field);
        }
    }

    /** @since 0.8 or earlier */
    public NodeFieldKind getKind() {
        return kind;
    }

    /** @since 0.8 or earlier */
    public Class<?> getType() {
        return type;
    }

    /** @since 0.8 or earlier */
    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    /** @since 0.8 or earlier */
    public String getName() {
        return name;
    }

    /**
     * @deprecated The visibility of this method will be reduced to protected. Do not use.
     * @since 0.8 or earlier
     */
    @Deprecated
    public abstract void putObject(Node receiver, Object value);

    /**
     * @deprecated The visibility of this method will be reduced to protected. Do not use.
     * @since 0.8 or earlier
     */
    @Deprecated
    public abstract Object getObject(Node receiver);

    /** @since 0.8 or earlier */
    public abstract Object loadValue(Node node);

    /** @since 0.14 */
    @Override
    public String toString() {
        return getDeclaringClass().getName() + "." + getName();
    }

    /**
     * @since 0.8 or earlier
     * @deprecated scheduled for removal from the API
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public abstract static class AbstractUnsafeNodeFieldAccessor extends NodeFieldAccessor {
        /**
         * @since 0.8 or earlier
         * @deprecated scheduled for removal from the API
         */
        @Deprecated
        protected AbstractUnsafeNodeFieldAccessor(NodeFieldKind kind, Class<?> declaringClass, String name, Class<?> type) {
            super(kind, declaringClass, name, type);
        }

        /**
         * @since 0.8 or earlier
         * @deprecated scheduled for removal from the API
         */
        @Deprecated
        public abstract long getOffset();

        /**
         * @since 0.8 or earlier
         * @deprecated scheduled for removal from the API
         */
        @Deprecated
        @Override
        public void putObject(Node receiver, Object value) {
            if (!type.isPrimitive() && value == null || type.isInstance(value)) {
                unsafe.putObject(receiver, getOffset(), value);
            } else {
                throw illegalArgumentException(value);
            }
        }

        private IllegalArgumentException illegalArgumentException(Object value) {
            return new IllegalArgumentException("Cannot set " + getType().getName() + " field " + toString() + " to " + (value == null ? "null" : value.getClass().getName()));
        }

        /**
         * @since 0.8 or earlier
         * @deprecated scheduled for removal from the API
         */
        @Deprecated
        @Override
        public Object getObject(Node receiver) {
            if (!type.isPrimitive()) {
                return unsafe.getObject(receiver, getOffset());
            } else {
                throw new IllegalArgumentException();
            }
        }

        /**
         * @since 0.8 or earlier
         * @deprecated scheduled for removal from the API
         */
        @Deprecated
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

    @SuppressWarnings("deprecation")
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

    @SuppressWarnings("deprecation")
    private static final class ReflectionNodeField extends NodeFieldAccessor {
        private final Field field;

        protected ReflectionNodeField(NodeFieldKind kind, Field field) {
            super(kind, field.getDeclaringClass(), field.getName(), field.getType());
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
