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

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;

/**
 * Information about a {@link Node} class. A single instance of this class is allocated for every
 * subclass of {@link Node} that is used.
 *
 * @since 0.8 or earlier
 */
public abstract class NodeClass {
    private static final ClassValue<NodeClass> nodeClasses = new ClassValue<NodeClass>() {
        @SuppressWarnings("unchecked")
        @Override
        protected NodeClass computeValue(final Class<?> clazz) {
            assert Node.class.isAssignableFrom(clazz);
            return AccessController.doPrivileged(new PrivilegedAction<NodeClass>() {
                public NodeClass run() {
                    return new NodeClassImpl((Class<? extends Node>) clazz);
                }
            });
        }
    };

    /** @since 0.8 or earlier */
    public static NodeClass get(Class<? extends Node> clazz) {
        return nodeClasses.get(clazz);
    }

    /** @since 0.8 or earlier */
    public static NodeClass get(Node node) {
        return node.getNodeClass();
    }

    /** @since 0.8 or earlier */
    public NodeClass(@SuppressWarnings("unused") Class<? extends Node> clazz) {
    }

    /** @since 0.8 or earlier */
    @SuppressWarnings("deprecation")
    @Deprecated
    public NodeFieldAccessor getNodeClassField() {
        return null;
    }

    /** @since 0.8 or earlier */
    @SuppressWarnings("deprecation")
    @Deprecated
    public NodeFieldAccessor[] getCloneableFields() {
        return null;
    }

    /** @since 0.8 or earlier */
    @SuppressWarnings("deprecation")
    @Deprecated
    public NodeFieldAccessor[] getFields() {
        return null;
    }

    /** @since 0.8 or earlier */
    @SuppressWarnings("deprecation")
    @Deprecated
    public NodeFieldAccessor getParentField() {
        return null;
    }

    /** @since 0.8 or earlier */
    @SuppressWarnings("deprecation")
    @Deprecated
    public NodeFieldAccessor[] getChildFields() {
        return null;
    }

    /** @since 0.8 or earlier */
    @SuppressWarnings("deprecation")
    @Deprecated
    public NodeFieldAccessor[] getChildrenFields() {
        return null;
    }

    /** @since 0.8 or earlier */
    public Iterator<Node> makeIterator(Node node) {
        return new NodeIterator(this, node, getNodeFields().iterator());
    }

    /**
     * The {@link Class} this <code>NodeClass</code> has been {@link #NodeClass(java.lang.Class)
     * created for}.
     *
     * @return the clazz of node this <code>NodeClass</code> describes
     * @since 0.8 or earlier
     */
    public abstract Class<? extends Node> getType();

    /** @since 0.14 */
    protected abstract Iterable<? extends Object> getNodeFields();

    /** @since 0.14 */
    protected abstract void putFieldObject(Object field, Node receiver, Object value);

    /** @since 0.14 */
    protected abstract Object getFieldObject(Object field, Node receiver);

    /** @since 0.14 */
    protected abstract Object getFieldValue(Object field, Node receiver);

    /** @since 0.14 */
    protected abstract Class<?> getFieldType(Object field);

    /** @since 0.14 */
    protected abstract String getFieldName(Object field);

    /** @since 0.14 */
    protected abstract boolean isChildField(Object field);

    /** @since 0.14 */
    protected abstract boolean isChildrenField(Object field);

    /** @since 0.14 */
    protected abstract boolean isCloneableField(Object field);

    /**
     * If and only if this method returns {@code true}, {@link #getNodeFields()} adheres to the
     * following iteration order.
     * <ul>
     * <li>{@link Node.Child @Child} fields
     * <li>{@link Node.Children @Children} fields
     * <li>{@link NodeCloneable} fields
     * <li>Other fields
     * </ul>
     */
    boolean nodeFieldsOrderedByKind() {
        return false;
    }
}
