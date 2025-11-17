/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.object.test;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.test.AbstractLibraryTest;

@SuppressWarnings("deprecation")
@RunWith(Parameterized.class)
public abstract class ParametrizedDynamicObjectTest extends AbstractLibraryTest {

    public enum TestRun {
        CACHED_LIBRARY,
        UNCACHED_LIBRARY,
        DISPATCHED_CACHED_LIBRARY,
        DISPATCHED_UNCACHED_LIBRARY,
        CACHED_NODES,
        UNCACHED_NODES;

        public static final TestRun[] DISPATCHED_ONLY = {DISPATCHED_CACHED_LIBRARY, DISPATCHED_UNCACHED_LIBRARY, CACHED_NODES, UNCACHED_NODES};
        public static final TestRun[] UNCACHED_ONLY = {DISPATCHED_UNCACHED_LIBRARY, UNCACHED_NODES};
    }

    @Parameter // first data value (0) is default
    public /* NOT private */ TestRun run;

    protected final DynamicObjectLibraryWrapper createLibrary(Object receiver) {
        return switch (run) {
            case CACHED_LIBRARY -> wrap(adoptNode(com.oracle.truffle.api.object.DynamicObjectLibrary.getFactory().create(receiver)).get());
            case UNCACHED_LIBRARY -> wrap(com.oracle.truffle.api.object.DynamicObjectLibrary.getFactory().getUncached(receiver));
            case DISPATCHED_CACHED_LIBRARY -> wrap(adoptNode(com.oracle.truffle.api.object.DynamicObjectLibrary.getFactory().createDispatched(2)).get());
            case DISPATCHED_UNCACHED_LIBRARY -> wrap(com.oracle.truffle.api.object.DynamicObjectLibrary.getUncached());
            case CACHED_NODES -> new NodesFakeDynamicObjectLibrary();
            case UNCACHED_NODES -> UNCACHED_NODES_LIBRARY;
        };

    }

    protected final DynamicObjectLibraryWrapper createLibrary() {
        assert run != TestRun.CACHED_LIBRARY;
        assert run != TestRun.UNCACHED_LIBRARY;
        return createLibrary(null);
    }

    protected final DynamicObjectLibraryWrapper uncachedLibrary() {
        return switch (run) {
            case CACHED_LIBRARY, UNCACHED_LIBRARY, DISPATCHED_CACHED_LIBRARY, DISPATCHED_UNCACHED_LIBRARY ->
                wrap(com.oracle.truffle.api.object.DynamicObjectLibrary.getUncached());
            case CACHED_NODES, UNCACHED_NODES -> UNCACHED_NODES_LIBRARY;
        };
    }

    protected abstract static class DynamicObjectLibraryWrapper {

        public abstract boolean accepts(Object receiver);

        public abstract Shape getShape(DynamicObject object);

        public abstract Object getOrDefault(DynamicObject object, Object key, Object defaultValue);

        public abstract int getIntOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException;

        public abstract double getDoubleOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException;

        public abstract long getLongOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException;

        public abstract void put(DynamicObject object, Object key, Object value);

        public void putInt(DynamicObject object, Object key, int value) {
            put(object, key, value);
        }

        public void putDouble(DynamicObject object, Object key, double value) {
            put(object, key, value);
        }

        public void putLong(DynamicObject object, Object key, long value) {
            put(object, key, value);
        }

        public abstract boolean putIfPresent(DynamicObject object, Object key, Object value);

        public abstract void putWithFlags(DynamicObject object, Object key, Object value, int flags);

        public abstract void putConstant(DynamicObject object, Object key, Object value, int flags);

        public abstract boolean removeKey(DynamicObject object, Object key);

        public abstract boolean setDynamicType(DynamicObject object, Object type);

        public abstract Object getDynamicType(DynamicObject object);

        public abstract boolean containsKey(DynamicObject object, Object key);

        public abstract int getShapeFlags(DynamicObject object);

        public abstract boolean setShapeFlags(DynamicObject object, int flags);

        public abstract Property getProperty(DynamicObject object, Object key);

        public final int getPropertyFlagsOrDefault(DynamicObject object, Object key, int defaultValue) {
            Property property = getProperty(object, key);
            return property != null ? property.getFlags() : defaultValue;
        }

        public abstract boolean setPropertyFlags(DynamicObject object, Object key, int propertyFlags);

        public abstract void markShared(DynamicObject object);

        public abstract boolean isShared(DynamicObject object);

        public abstract boolean updateShape(DynamicObject object);

        public abstract boolean resetShape(DynamicObject object, Shape otherShape);

        public abstract Object[] getKeyArray(DynamicObject object);

        public abstract Property[] getPropertyArray(DynamicObject object);
    }

    protected static DynamicObjectLibraryWrapper wrap(com.oracle.truffle.api.object.DynamicObjectLibrary library) {
        return new DynamicObjectLibraryWrapper() {

            @Override
            public boolean accepts(Object receiver) {
                return library.accepts(receiver);
            }

            @Override
            public Shape getShape(DynamicObject object) {
                return library.getShape(object);
            }

            @Override
            public Object getOrDefault(DynamicObject object, Object key, Object defaultValue) {
                return library.getOrDefault(object, key, defaultValue);
            }

            @Override
            public int getIntOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException {
                return library.getIntOrDefault(object, key, defaultValue);
            }

            @Override
            public long getLongOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException {
                return library.getLongOrDefault(object, key, defaultValue);
            }

            @Override
            public double getDoubleOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException {
                return library.getDoubleOrDefault(object, key, defaultValue);
            }

            @Override
            public void put(DynamicObject object, Object key, Object value) {
                library.put(object, key, value);
            }

            @Override
            public void putInt(DynamicObject object, Object key, int value) {
                library.putInt(object, key, value);
            }

            @Override
            public void putDouble(DynamicObject object, Object key, double value) {
                library.putDouble(object, key, value);
            }

            @Override
            public void putLong(DynamicObject object, Object key, long value) {
                library.putLong(object, key, value);
            }

            @Override
            public boolean putIfPresent(DynamicObject object, Object key, Object value) {
                return library.putIfPresent(object, key, value);
            }

            @Override
            public void putWithFlags(DynamicObject object, Object key, Object value, int flags) {
                library.putWithFlags(object, key, value, flags);
            }

            @Override
            public void putConstant(DynamicObject object, Object key, Object value, int flags) {
                library.putConstant(object, key, value, flags);
            }

            @Override
            public boolean removeKey(DynamicObject object, Object key) {
                return library.removeKey(object, key);
            }

            @Override
            public boolean setDynamicType(DynamicObject object, Object type) {
                return library.setDynamicType(object, type);
            }

            @Override
            public Object getDynamicType(DynamicObject object) {
                return library.getDynamicType(object);
            }

            @Override
            public boolean containsKey(DynamicObject object, Object key) {
                return library.containsKey(object, key);
            }

            @Override
            public int getShapeFlags(DynamicObject object) {
                return library.getShapeFlags(object);
            }

            @Override
            public boolean setShapeFlags(DynamicObject object, int flags) {
                return library.setShapeFlags(object, flags);
            }

            @Override
            public Property getProperty(DynamicObject object, Object key) {
                return library.getProperty(object, key);
            }

            @Override
            public boolean setPropertyFlags(DynamicObject object, Object key, int propertyFlags) {
                return library.setPropertyFlags(object, key, propertyFlags);
            }

            @Override
            public void markShared(DynamicObject object) {
                library.markShared(object);
            }

            @Override
            public boolean isShared(DynamicObject object) {
                return library.isShared(object);
            }

            @Override
            public boolean updateShape(DynamicObject object) {
                return library.updateShape(object);
            }

            @Override
            public boolean resetShape(DynamicObject object, Shape otherShape) {
                return library.resetShape(object, otherShape);
            }

            @Override
            public Object[] getKeyArray(DynamicObject object) {
                return library.getKeyArray(object);
            }

            @Override
            public Property[] getPropertyArray(DynamicObject object) {
                return library.getPropertyArray(object);
            }
        };
    }

    static final DynamicObjectLibraryWrapper UNCACHED_NODES_LIBRARY = new NodesFakeDynamicObjectLibrary("uncached");

    static class NodesFakeDynamicObjectLibrary extends DynamicObjectLibraryWrapper {

        final DynamicObject.GetNode getNode;
        final DynamicObject.PutNode putNode;
        final DynamicObject.PutConstantNode putConstantNode;
        final DynamicObject.RemoveKeyNode removeKeyNode;
        final DynamicObject.SetDynamicTypeNode setDynamicTypeNode;
        final DynamicObject.GetDynamicTypeNode getDynamicTypeNode;
        final DynamicObject.ContainsKeyNode containsKeyNode;
        final DynamicObject.GetShapeFlagsNode getShapeFlagsNode;
        final DynamicObject.SetShapeFlagsNode setShapeFlagsNode;
        final DynamicObject.GetPropertyNode getPropertyNode;
        final DynamicObject.SetPropertyFlagsNode setPropertyFlagsNode;
        final DynamicObject.MarkSharedNode markSharedNode;
        final DynamicObject.IsSharedNode isSharedNode;
        final DynamicObject.UpdateShapeNode updateShapeNode;
        final DynamicObject.ResetShapeNode resetShapeNode;
        final DynamicObject.GetKeyArrayNode getKeyArrayNode;
        final DynamicObject.GetPropertyArrayNode getPropertyArrayNode;

        NodesFakeDynamicObjectLibrary() {
            getNode = DynamicObject.GetNode.create();
            putNode = DynamicObject.PutNode.create();
            putConstantNode = DynamicObject.PutConstantNode.create();
            removeKeyNode = DynamicObject.RemoveKeyNode.create();
            setDynamicTypeNode = DynamicObject.SetDynamicTypeNode.create();
            getDynamicTypeNode = DynamicObject.GetDynamicTypeNode.create();
            containsKeyNode = DynamicObject.ContainsKeyNode.create();
            getShapeFlagsNode = DynamicObject.GetShapeFlagsNode.create();
            setShapeFlagsNode = DynamicObject.SetShapeFlagsNode.create();
            getPropertyNode = DynamicObject.GetPropertyNode.create();
            setPropertyFlagsNode = DynamicObject.SetPropertyFlagsNode.create();
            markSharedNode = DynamicObject.MarkSharedNode.create();
            isSharedNode = DynamicObject.IsSharedNode.create();
            updateShapeNode = DynamicObject.UpdateShapeNode.create();
            resetShapeNode = DynamicObject.ResetShapeNode.create();
            getKeyArrayNode = DynamicObject.GetKeyArrayNode.create();
            getPropertyArrayNode = DynamicObject.GetPropertyArrayNode.create();
        }

        NodesFakeDynamicObjectLibrary(@SuppressWarnings("unused") String uncached) {
            getNode = DynamicObject.GetNode.getUncached();
            putNode = DynamicObject.PutNode.getUncached();
            putConstantNode = DynamicObject.PutConstantNode.getUncached();
            removeKeyNode = DynamicObject.RemoveKeyNode.getUncached();
            setDynamicTypeNode = DynamicObject.SetDynamicTypeNode.getUncached();
            getDynamicTypeNode = DynamicObject.GetDynamicTypeNode.getUncached();
            containsKeyNode = DynamicObject.ContainsKeyNode.getUncached();
            getShapeFlagsNode = DynamicObject.GetShapeFlagsNode.getUncached();
            setShapeFlagsNode = DynamicObject.SetShapeFlagsNode.getUncached();
            getPropertyNode = DynamicObject.GetPropertyNode.getUncached();
            setPropertyFlagsNode = DynamicObject.SetPropertyFlagsNode.getUncached();
            markSharedNode = DynamicObject.MarkSharedNode.getUncached();
            isSharedNode = DynamicObject.IsSharedNode.getUncached();
            updateShapeNode = DynamicObject.UpdateShapeNode.getUncached();
            resetShapeNode = DynamicObject.ResetShapeNode.getUncached();
            getKeyArrayNode = DynamicObject.GetKeyArrayNode.getUncached();
            getPropertyArrayNode = DynamicObject.GetPropertyArrayNode.getUncached();
        }

        @Override
        public boolean accepts(Object receiver) {
            return true;
        }

        @Override
        public Shape getShape(DynamicObject object) {
            return object.getShape();
        }

        @Override
        public Object getOrDefault(DynamicObject object, Object key, Object defaultValue) {
            return getNode.execute(object, key, defaultValue);
        }

        @Override
        public int getIntOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException {
            return getNode.executeInt(object, key, defaultValue);
        }

        @Override
        public long getLongOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException {
            return getNode.executeLong(object, key, defaultValue);
        }

        @Override
        public double getDoubleOrDefault(DynamicObject object, Object key, Object defaultValue) throws UnexpectedResultException {
            return getNode.executeDouble(object, key, defaultValue);
        }

        @Override
        public void put(DynamicObject object, Object key, Object value) {
            putNode.execute(object, key, value);
        }

        @Override
        public boolean putIfPresent(DynamicObject object, Object key, Object value) {
            return putNode.executeIfPresent(object, key, value);
        }

        @Override
        public void putWithFlags(DynamicObject object, Object key, Object value, int flags) {
            putNode.executeWithFlags(object, key, value, flags);
        }

        @Override
        public void putConstant(DynamicObject object, Object key, Object value, int flags) {
            putConstantNode.executeWithFlags(object, key, value, flags);
        }

        @Override
        public boolean removeKey(DynamicObject object, Object key) {
            return removeKeyNode.execute(object, key);
        }

        @Override
        public boolean setDynamicType(DynamicObject object, Object type) {
            return setDynamicTypeNode.execute(object, type);
        }

        @Override
        public Object getDynamicType(DynamicObject object) {
            return getDynamicTypeNode.execute(object);
        }

        @Override
        public boolean containsKey(DynamicObject object, Object key) {
            return containsKeyNode.execute(object, key);
        }

        @Override
        public int getShapeFlags(DynamicObject object) {
            return getShapeFlagsNode.execute(object);
        }

        @Override
        public boolean setShapeFlags(DynamicObject object, int flags) {
            return setShapeFlagsNode.execute(object, flags);
        }

        @Override
        public Property getProperty(DynamicObject object, Object key) {
            return getPropertyNode.execute(object, key);
        }

        @Override
        public boolean setPropertyFlags(DynamicObject object, Object key, int propertyFlags) {
            return setPropertyFlagsNode.execute(object, key, propertyFlags);
        }

        @Override
        public void markShared(DynamicObject object) {
            markSharedNode.execute(object);
        }

        @Override
        public boolean isShared(DynamicObject object) {
            return isSharedNode.execute(object);
        }

        @Override
        public boolean updateShape(DynamicObject object) {
            return updateShapeNode.execute(object);
        }

        @Override
        public boolean resetShape(DynamicObject object, Shape otherShape) {
            return resetShapeNode.execute(object, otherShape);
        }

        @Override
        public Object[] getKeyArray(DynamicObject object) {
            return getKeyArrayNode.execute(object);
        }

        @Override
        public Property[] getPropertyArray(DynamicObject object) {
            return getPropertyArrayNode.execute(object);
        }
    }

}
