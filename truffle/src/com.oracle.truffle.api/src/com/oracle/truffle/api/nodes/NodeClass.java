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
        @Override
        protected NodeClass computeValue(final Class<?> clazz) {
            return AccessController.doPrivileged(new PrivilegedAction<NodeClass>() {
                public NodeClass run() {
                    return new NodeClassImpl(clazz.asSubclass(Node.class));
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
        return new NodeIterator(this, node, getNodeFieldArray());
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
    @Deprecated
    protected abstract Iterable<? extends Object> getNodeFields();

    /** @since 20.2 */
    protected abstract Object[] getNodeFieldArray();

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
     * <li>{@link Node.Child @Child} or {@link Node.Children @Children} fields
     * <li>{@link NodeCloneable} fields
     * <li>Other fields
     * </ul>
     */
    boolean nodeFieldsOrderedByKind() {
        return false;
    }
}
