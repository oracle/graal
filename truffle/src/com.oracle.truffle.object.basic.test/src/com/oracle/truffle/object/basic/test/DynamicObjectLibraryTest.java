/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.object.basic.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.api.object.Layout;
import com.oracle.truffle.api.object.ObjectType;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.test.AbstractParametrizedLibraryTest;
import com.oracle.truffle.object.DynamicObjectImpl;

@RunWith(Parameterized.class)
public class DynamicObjectLibraryTest extends AbstractParametrizedLibraryTest {
    @Parameter(1) public Supplier<? extends DynamicObject> emptyObjectSupplier;

    private DynamicObject createEmpty() {
        return emptyObjectSupplier.get();
    }

    @Parameters
    public static Collection<Object[]> parameters() {
        Collection<Object[]> params = new ArrayList<>();

        Object objectType = newObjectType();
        Layout layout = Layout.createLayout();
        Shape shape = layout.createShape((ObjectType) objectType);
        Supplier<? extends Object> doSupplier = () -> shape.newInstance();
        addParams(params, doSupplier);

        Shape shapeMin = Shape.newBuilder().build();
        Supplier<? extends DynamicObject> minimalSupplier = () -> new TestDynamicObjectMinimal(shapeMin);
        addParams(params, minimalSupplier);

        Shape shapeDef = Shape.newBuilder().layout(TestDynamicObjectDefault.class).build();
        Supplier<? extends DynamicObject> defaultSupplier = () -> new TestDynamicObjectDefault(shapeDef);
        addParams(params, defaultSupplier);

        return params;
    }

    private static void addParams(Collection<Object[]> params, Supplier<? extends Object> supplier) {
        for (TestRun run : TestRun.values()) {
            params.add(new Object[]{run, supplier});
        }
    }

    private DynamicObjectLibrary createDispatchedLibrary() {
        if (run == TestRun.DISPATCHED_CACHED || run == TestRun.CACHED) {
            return adopt(DynamicObjectLibrary.getFactory().createDispatched(5));
        }
        return DynamicObjectLibrary.getUncached();
    }

    private DynamicObjectLibrary createLibraryForReceiver(DynamicObject receiver) {
        DynamicObjectLibrary objectLibrary = createLibrary(DynamicObjectLibrary.class, receiver);
        assertTrue(objectLibrary.accepts(receiver));
        return objectLibrary;
    }

    private DynamicObjectLibrary createLibraryForReceiverAndKey(DynamicObject receiver, Object key) {
        assertFalse(key instanceof DynamicObject);
        DynamicObjectLibrary objectLibrary = createLibrary(DynamicObjectLibrary.class, receiver);
        assertTrue(objectLibrary.accepts(receiver));
        return objectLibrary;
    }

    private DynamicObjectLibrary createLibraryForKey(Object key) {
        assertFalse(key instanceof DynamicObject);
        return createDispatchedLibrary();
    }

    @Test
    public void testGet1() throws UnexpectedResultException {
        DynamicObject o1 = createEmpty();
        String k1 = "key1";
        int v1 = 42;
        uncachedPut(o1, k1, v1, 0);
        DynamicObject o2 = createEmpty();
        uncachedPut(o2, k1, v1, 0);
        assertSame(o1.getShape(), o2.getShape());

        DynamicObjectLibrary getNode = createLibraryForReceiverAndKey(o1, k1);
        assertEquals(v1, getNode.getOrDefault(o1, k1, null));
        assertEquals(v1, getNode.getIntOrDefault(o1, k1, null));
        assertEquals(v1, getNode.getOrDefault(o2, k1, null));
        assertEquals(v1, getNode.getIntOrDefault(o2, k1, null));

        String v2 = "asdf";
        uncachedSet(o1, k1, v2);

        getNode = createLibraryForKey(k1);
        assertEquals(v2, getNode.getOrDefault(o1, k1, null));
        try {
            getNode.getIntOrDefault(o1, k1, null);
            fail();
        } catch (UnexpectedResultException e) {
            assertEquals(v2, e.getResult());
        }
        assertEquals(v1, getNode.getOrDefault(o2, k1, null));
        assertEquals(v1, getNode.getIntOrDefault(o2, k1, null));

        String missingKey = "missing";
        DynamicObjectLibrary getMissingKey;
        getMissingKey = createLibraryForReceiverAndKey(o1, missingKey);
        assertEquals(null, getMissingKey.getOrDefault(o1, missingKey, null));
        assertEquals(404, getMissingKey.getIntOrDefault(o1, missingKey, 404));
        getMissingKey = createLibraryForReceiver(o1);
        assertEquals(null, getMissingKey.getOrDefault(o1, missingKey, null));
        assertEquals(404, getMissingKey.getIntOrDefault(o1, missingKey, 404));
    }

    @Test
    public void testPut1() {
        DynamicObject o1 = createEmpty();
        String key1 = "key1";
        int intval1 = 42;
        int intval2 = 43;
        uncachedPut(o1, key1, intval1, 0);
        DynamicObject o2 = createEmpty();
        uncachedPut(o2, key1, intval1, 0);
        assertSame(o1.getShape(), o2.getShape());

        DynamicObjectLibrary setNode = createLibraryForReceiverAndKey(o1, key1);
        setNode.put(o1, key1, intval2);
        assertEquals(intval2, uncachedGet(o1, key1));
        setNode.putInt(o1, key1, intval1);
        assertEquals(intval1, uncachedGet(o1, key1));
        setNode.put(o2, key1, intval2);
        assertEquals(intval2, uncachedGet(o2, key1));
        setNode.putInt(o2, key1, intval1);
        assertEquals(intval1, uncachedGet(o2, key1));
        assertSame(o1.getShape(), o2.getShape());

        String strval1 = "asdf";
        setNode.put(o1, key1, strval1);
        assertEquals(strval1, uncachedGet(o1, key1));

        String key2 = "key2";
        String strval2 = "qwer";
        DynamicObjectLibrary setNode2 = createLibraryForReceiverAndKey(o1, key2);
        setNode2.put(o1, key2, strval2);
        assertEquals(strval2, uncachedGet(o1, key2));
        setNode2.putInt(o1, key2, intval1);
        assertEquals(intval1, uncachedGet(o1, key2));

        DynamicObjectLibrary setNode3 = createLibraryForReceiverAndKey(o1, key2);
        setNode3.put(o1, key2, strval1);
        assertEquals(strval1, uncachedGet(o1, key2));
        assertTrue(setNode3.accepts(o1));
        DynamicObjectLibrary setNode4 = createLibraryForReceiverAndKey(o1, key2);
        setNode4.putInt(o1, key2, intval2);
        assertEquals(intval2, uncachedGet(o1, key2));
        assertTrue(setNode4.accepts(o1));
    }

    @Test
    public void testPutIfPresent() {
        DynamicObject o1 = createEmpty();
        String key1 = "key1";
        int intval1 = 42;
        int intval2 = 43;
        uncachedPut(o1, key1, intval1, 0);
        DynamicObject o2 = createEmpty();
        uncachedPut(o2, key1, intval1, 0);
        assertSame(o1.getShape(), o2.getShape());

        DynamicObjectLibrary setNode = createLibraryForKey(key1);
        assertTrue(setNode.putIfPresent(o1, key1, intval2));
        assertEquals(intval2, uncachedGet(o1, key1));
        assertTrue(setNode.putIfPresent(o1, key1, intval1));
        assertEquals(intval1, uncachedGet(o1, key1));
        assertTrue(setNode.putIfPresent(o2, key1, intval2));
        assertEquals(intval2, uncachedGet(o2, key1));
        assertTrue(setNode.putIfPresent(o2, key1, intval1));
        assertEquals(intval1, uncachedGet(o2, key1));
        assertSame(o1.getShape(), o2.getShape());

        String strval1 = "asdf";
        setNode.put(o1, key1, strval1);
        assertEquals(strval1, uncachedGet(o1, key1));

        String key2 = "key2";
        String strval2 = "qwer";
        DynamicObjectLibrary setNode2 = createLibraryForReceiverAndKey(o1, key2);
        assertFalse(DynamicObjectLibrary.getUncached().containsKey(o1, key2));
        assertFalse(setNode2.putIfPresent(o1, key2, strval2));
        assertTrue(setNode2.accepts(o1));
        assertFalse(setNode2.containsKey(o1, key2));
        assertEquals(null, uncachedGet(o1, key2));

        setNode2.put(o1, key2, strval2);
        assertEquals(run != TestRun.CACHED, setNode2.accepts(o1));
        assertTrue(DynamicObjectLibrary.getUncached().containsKey(o1, key2));
        assertEquals(strval2, uncachedGet(o1, key2));

        DynamicObjectLibrary setNode3 = createLibraryForReceiverAndKey(o1, key2);
        assertTrue(setNode3.putIfPresent(o1, key2, intval1));
        assertEquals(intval1, uncachedGet(o1, key2));
    }

    @Test
    public void testPut2() {
        DynamicObject o1 = createEmpty();
        DynamicObject o2 = createEmpty();
        DynamicObject o3 = createEmpty();
        String k1 = "key1";
        int v1 = 42;
        int v2 = 43;
        uncachedPut(o3, k1, v1, 0);

        DynamicObjectLibrary setNode1 = createLibraryForKey(k1);
        setNode1.put(o1, k1, v2);
        assertEquals(v2, uncachedGet(o1, k1));
        assertEquals(0, uncachedGetProperty(o1, k1).getFlags());
        setNode1.put(o1, k1, v1);
        assertEquals(v1, uncachedGet(o1, k1));
        setNode1.put(o2, k1, v2);
        assertEquals(v2, uncachedGet(o2, k1));
        setNode1.put(o2, k1, v1);
        assertEquals(v1, uncachedGet(o2, k1));
        assertSame(o1.getShape(), o2.getShape());

        assertEquals(v1, uncachedGet(o3, k1));
        assertSame(o1.getShape(), o3.getShape());
        uncachedPut(o3, k1, v1);
        assertEquals(v1, uncachedGet(o3, k1));
        assertEquals(0, uncachedGetProperty(o3, k1).getFlags());
        assertSame(o1.getShape(), o3.getShape());

        String v3 = "asdf";
        setNode1.put(o1, k1, v3);
        assertEquals(v3, uncachedGet(o1, k1));

        String k2 = "key2";
        String v4 = "qwer";
        DynamicObjectLibrary setNode2 = createLibraryForKey(k2);

        setNode2.put(o1, k2, v4);
        assertEquals(v4, uncachedGet(o1, k2));
        setNode2.putInt(o1, k2, v1);
        assertEquals(v1, uncachedGet(o1, k2));

        int f2 = 0x42;
        DynamicObjectLibrary setNode3 = createLibraryForKey(k1);

        setNode3.putWithFlags(o3, k1, v1, f2);
        assertEquals(v1, uncachedGet(o3, k1));
        assertEquals(f2, uncachedGetProperty(o3, k1).getFlags());
    }

    @Test
    public void testPutWithFlags1() {
        DynamicObject o1 = createEmpty();
        DynamicObject o2 = createEmpty();
        DynamicObject o3 = createEmpty();
        String k1 = "key1";
        int v1 = 42;
        int v2 = 43;
        uncachedPut(o3, k1, v1, 0);

        int flags = 0xf;
        DynamicObjectLibrary setNode1 = createLibraryForKey(k1);
        setNode1.putWithFlags(o1, k1, v2, flags);
        assertEquals(v2, uncachedGet(o1, k1));
        assertEquals(flags, uncachedGetProperty(o1, k1).getFlags());
        setNode1.putWithFlags(o1, k1, v1, flags);
        assertEquals(v1, uncachedGet(o1, k1));
        setNode1.putWithFlags(o2, k1, v2, flags);
        assertEquals(v2, uncachedGet(o2, k1));
        setNode1.putWithFlags(o2, k1, v1, flags);
        assertEquals(v1, uncachedGet(o2, k1));
        assertSame(o1.getShape(), o2.getShape());

        assertEquals(v1, uncachedGet(o3, k1));
        assertNotSame(o1.getShape(), o3.getShape());
        uncachedPut(o3, k1, v1, flags);
        assertEquals(v1, uncachedGet(o3, k1));
        assertEquals(flags, uncachedGetProperty(o3, k1).getFlags());
        // assertSame(o1.getShape(), o3.getShape());

        String v3 = "asdf";
        setNode1.putWithFlags(o1, k1, v3, flags);
        assertEquals(v3, uncachedGet(o1, k1));

        String k2 = "key2";
        String v4 = "qwer";
        DynamicObjectLibrary setNode2 = createLibraryForKey(k2);

        setNode2.put(o1, k2, v4);
        assertEquals(v4, uncachedGet(o1, k2));
        setNode2.putInt(o1, k2, v1);
        assertEquals(v1, uncachedGet(o1, k2));

        int f2 = 0x42;
        DynamicObjectLibrary setNode3 = createLibraryForKey(k1);

        setNode3.putWithFlags(o3, k1, v1, f2);
        assertEquals(v1, uncachedGet(o3, k1));
        assertEquals(f2, uncachedGetProperty(o3, k1).getFlags());
    }

    @Test
    public void testTypeIdAndShapeFlags() {
        DynamicObjectLibrary lib = createDispatchedLibrary();
        Object myType = newObjectType();
        int flags = 42;
        String key = "key1";

        DynamicObject o1 = createEmpty();
        lib.setDynamicType(o1, myType);
        assertSame(myType, lib.getDynamicType(o1));

        DynamicObject o2 = createEmpty();
        lib.setDynamicType(o2, myType);
        assertSame(myType, lib.getDynamicType(o2));
        assertSame(o1.getShape(), o2.getShape());

        DynamicObject o3 = createEmpty();
        lib.setShapeFlags(o3, flags);
        lib.setDynamicType(o3, myType);
        assertSame(myType, lib.getDynamicType(o3));
        assertEquals(flags, lib.getShapeFlags(o3));

        DynamicObject o4 = createEmpty();
        lib.setShapeFlags(o4, flags);
        lib.put(o4, key, 42);
        lib.setDynamicType(o4, myType);
        lib.put(o4, key, "value");
        assertSame(myType, lib.getDynamicType(o4));

        DynamicObjectLibrary cached = createLibraryForReceiver(o4);
        assertSame(myType, cached.getDynamicType(o4));
        assertSame(myType, cached.getDynamicType(o4));
        Object myType2 = newObjectType();
        cached.setDynamicType(o4, myType2);
        assertEquals(run != TestRun.CACHED, cached.accepts(o4));
        assertSame(myType2, lib.getDynamicType(o4));
    }

    @Test
    public void testShapeFlags() {
        DynamicObjectLibrary lib = createDispatchedLibrary();
        int flags = 42;

        DynamicObject o1 = createEmpty();
        lib.setShapeFlags(o1, flags);
        assertEquals(flags, lib.getShapeFlags(o1));

        DynamicObject o2 = createEmpty();
        lib.setShapeFlags(o2, flags);
        assertEquals(flags, lib.getShapeFlags(o2));
        assertSame(o1.getShape(), o2.getShape());

        DynamicObject o3 = createEmpty();
        lib.setShapeFlags(o3, 1);
        lib.setDynamicType(o3, newObjectType());
        lib.setShapeFlags(o3, flags);
        lib.setDynamicType(o3, newObjectType());
        assertEquals(flags, lib.getShapeFlags(o2));

        DynamicObject o4 = createEmpty();
        lib.setShapeFlags(o4, flags);
        lib.markShared(o4);
        assertEquals(flags, lib.getShapeFlags(o2));

        DynamicObjectLibrary cached = createLibraryForReceiver(o4);
        assertEquals(flags, cached.getShapeFlags(o4));
        assertEquals(flags, cached.getShapeFlags(o4));
        int flags2 = 43;
        cached.setShapeFlags(o4, flags2);
        assertEquals(run != TestRun.CACHED, cached.accepts(o4));
        assertEquals(flags2, lib.getShapeFlags(o4));
    }

    @Test
    public void testUpdateShapeFlags() {
        int f1 = 0xf;
        int f2 = 0x10;
        int f3 = 0x1f;

        DynamicObjectLibrary lib = createDispatchedLibrary();
        DynamicObject o1 = createEmpty();
        uncachedPut(o1, "key", 42, 0);
        assertTrue(lib.setShapeFlags(o1, f1));
        assertEquals(f1, lib.getShapeFlags(o1));
        assertEquals(f1, o1.getShape().getFlags());
        assertTrue(updateShapeFlags(lib, o1, f -> f | f2));
        assertEquals(f3, lib.getShapeFlags(o1));
        assertEquals(f3, o1.getShape().getFlags());
    }

    private static boolean updateShapeFlags(DynamicObjectLibrary lib, DynamicObject obj, IntUnaryOperator updateFunction) {
        int oldFlags = lib.getShapeFlags(obj);
        int newFlags = updateFunction.applyAsInt(oldFlags);
        if (oldFlags == newFlags) {
            return false;
        }
        return lib.setShapeFlags(obj, newFlags);
    }

    @Test
    public void testMakeShared() {
        DynamicObjectLibrary lib = createDispatchedLibrary();

        DynamicObject o1 = createEmpty();
        assertFalse(lib.isShared(o1));
        lib.markShared(o1);
        assertTrue(lib.isShared(o1));
        lib.put(o1, "key", "value");
        assertTrue(lib.isShared(o1));
        assertTrue(lib.containsKey(o1, "key"));
    }

    @Test
    public void testPropertyFlags() {
        String k1 = "key1";
        int v1 = 42;
        int v2 = 43;
        int f1 = 0xf;
        int f2 = 0x10;
        int f3 = 0x1f;

        DynamicObjectLibrary lib = createLibraryForKey(k1);
        DynamicObject o1 = createEmpty();
        uncachedPut(o1, k1, v1, 0);
        assertTrue(lib.setPropertyFlags(o1, k1, f1));
        assertEquals(f1, lib.getPropertyFlagsOrDefault(o1, k1, -1));
        assertEquals(f1, uncachedGetProperty(o1, k1).getFlags());
        assertTrue(updatePropertyFlags(lib, o1, k1, f -> f | f2));
        assertEquals(f3, lib.getPropertyFlagsOrDefault(o1, k1, -1));
        assertEquals(f3, uncachedGetProperty(o1, k1).getFlags());

        Shape before = o1.getShape();
        assertTrue(lib.setPropertyFlags(o1, k1, f3));
        assertFalse(updatePropertyFlags(lib, o1, k1, f -> f | f2));
        assertEquals(f3, lib.getPropertyFlagsOrDefault(o1, k1, -1));
        assertEquals(f3, uncachedGetProperty(o1, k1).getFlags());
        assertSame(before, o1.getShape());

        DynamicObject o2 = createEmpty();
        uncachedPut(o2, k1, v2, 0);
        assertTrue(lib.setPropertyFlags(o2, k1, f1));
        assertEquals(f1, lib.getPropertyFlagsOrDefault(o2, k1, -1));
        assertTrue(updatePropertyFlags(lib, o2, k1, f -> f | f2));
        assertEquals(f3, lib.getPropertyFlagsOrDefault(o2, k1, -1));
        assertSame(o1.getShape(), o2.getShape());

        DynamicObject o3 = createEmpty();
        assertFalse(lib.setPropertyFlags(o3, k1, f1));
    }

    private static boolean updatePropertyFlags(DynamicObjectLibrary lib, DynamicObject obj, String key, IntUnaryOperator updateFunction) {
        Property property = lib.getProperty(obj, key);
        if (property == null) {
            return false;
        }
        int oldFlags = property.getFlags();
        int newFlags = updateFunction.applyAsInt(oldFlags);
        if (oldFlags == newFlags) {
            return false;
        }
        return lib.setPropertyFlags(obj, key, newFlags);
    }

    @Test
    public void testRemove() {
        int v1 = 42;
        int v2 = 43;
        Object v3 = "value";

        DynamicObjectLibrary lib = createDispatchedLibrary();
        DynamicObject o1 = createEmpty();
        uncachedPut(o1, "key1", v1, 0);
        uncachedPut(o1, "key2", v2, 0);
        uncachedPut(o1, "key3", v3, 0);

        assertFalse(lib.removeKey(o1, "key4"));
        assertTrue(lib.removeKey(o1, "key3"));
        assertEquals(Arrays.asList("key1", "key2"), getKeyList(o1));
        uncachedPut(o1, "key3", v3, 0);
        assertEquals(Arrays.asList("key1", "key2", "key3"), getKeyList(o1));
        assertTrue(lib.removeKey(o1, "key3"));
        assertEquals(Arrays.asList("key1", "key2"), getKeyList(o1));
        uncachedPut(o1, "key3", v3, 0);
        assertTrue(lib.removeKey(o1, "key1"));
        assertEquals(Arrays.asList("key2", "key3"), getKeyList(o1));
        uncachedPut(o1, "key1", v1, 0);
        assertEquals(Arrays.asList("key2", "key3", "key1"), getKeyList(o1));
        assertTrue(lib.removeKey(o1, "key3"));
        assertEquals(Arrays.asList("key2", "key1"), getKeyList(o1));
        assertEquals(v1, lib.getOrDefault(o1, "key1", null));
        assertEquals(v2, lib.getOrDefault(o1, "key2", null));
    }

    @Test
    public void testResetShape() {
        int v1 = 42;
        int v2 = 43;

        DynamicObjectLibrary lib = createDispatchedLibrary();
        DynamicObject o1 = createEmpty();
        Shape emptyShape = o1.getShape();
        uncachedPut(o1, "key1", v1, 0);
        uncachedPut(o1, "key2", v2, 0);
        lib.resetShape(o1, emptyShape);
        assertSame(emptyShape, o1.getShape());

        assumeTrue("new layout only", isNewLayout(o1));
        int flags = 0xf;
        DynamicObject o2 = createEmpty();
        Shape newEmptyShape = Shape.newBuilder().shapeFlags(flags).build();
        uncachedPut(o2, "key1", v1, 0);
        uncachedPut(o2, "key2", v2, 0);
        lib.resetShape(o2, newEmptyShape);
        assertSame(newEmptyShape, o2.getShape());
        assertEquals(flags, lib.getShapeFlags(o2));
    }

    @Test
    public void testGetKeysAndProperties() {
        int v1 = 42;
        int v2 = 43;
        Object v3 = "value";

        DynamicObjectLibrary lib = createDispatchedLibrary();
        DynamicObject o1 = createEmpty();
        uncachedPut(o1, "key1", v1, 1);
        uncachedPut(o1, "key2", v2, 2);
        uncachedPut(o1, "key3", v3, 3);

        Object[] keyArray = lib.getKeyArray(o1);
        Property[] properties = lib.getPropertyArray(o1);
        assertEquals(Arrays.asList("key1", "key2", "key3"), getKeyList(o1));
        assertEquals(3, o1.getShape().getPropertyCount());
        for (int i = 0, j = 1; i < 3; i++, j++) {
            assertEquals(keyArray[i], properties[i].getKey());
            assertEquals(j, properties[i].getFlags());
        }
    }

    @Test
    public void testGetKeysAndPropertiesFromShape() {
        int v1 = 42;
        int v2 = 43;
        Object v3 = "value";

        DynamicObject o1 = createEmpty();
        uncachedPut(o1, "key1", v1, 1);
        uncachedPut(o1, "key2", v2, 2);
        uncachedPut(o1, "key3", v3, 3);

        Object[] keyArray = getKeyList(o1).toArray();
        Property[] properties = o1.getShape().getPropertyList().toArray(new Property[0]);
        assertEquals(Arrays.asList("key1", "key2", "key3"), getKeyList(o1));
        assertEquals(3, o1.getShape().getPropertyCount());
        for (int i = 0, j = 1; i < 3; i++, j++) {
            assertEquals(keyArray[i], properties[i].getKey());
            assertEquals(j, properties[i].getFlags());
        }
    }

    @Test
    public void testAllPropertiesMatch() {
        int v1 = 42;
        int v2 = 43;
        Object v3 = "value";

        DynamicObject o1 = createEmpty();
        uncachedPut(o1, "key1", v1, 41);
        uncachedPut(o1, "key2", v2, 42);
        uncachedPut(o1, "key3", v3, 43);

        assertFalse(o1.getShape().allPropertiesMatch(p -> p.getFlags() >= 42));
        DynamicObjectLibrary lib2 = createLibraryForKey("key1");
        lib2.removeKey(o1, "key1");
        assertTrue(o1.getShape().allPropertiesMatch(p -> p.getFlags() >= 42));
    }

    @Test
    public void testGetProperty() {
        int v1 = 42;
        int v2 = 43;
        Object v3 = "value";

        DynamicObject o1 = createEmpty();
        uncachedPut(o1, "key1", v1, 1);
        uncachedPut(o1, "key2", v2, 2);
        uncachedPut(o1, "key3", v3, 3);

        DynamicObjectLibrary lib = createLibraryForReceiver(o1);
        assertTrue(lib.accepts(o1));
        for (int i = 1; i <= 3; i++) {
            Object key = "key" + i;
            assertSame(o1.getShape().getProperty(key), lib.getProperty(o1, key));
            assertEquals(i, o1.getShape().getProperty(key).getFlags());
            assertEquals(i, lib.getPropertyFlagsOrDefault(o1, key, -1));
        }
        assertTrue(lib.accepts(o1));
    }

    @Test
    public void testPutConstant1() {
        DynamicObject o1 = createEmpty();
        String k1 = "key1";
        int v1 = 42;
        int v2 = 43;
        int flags = 0xf;

        DynamicObjectLibrary setNode1 = createLibraryForKey(k1);

        setNode1.putConstant(o1, k1, v1, 0);
        assertTrue(o1.getShape().getProperty(k1).getLocation().isConstant());
        assertEquals(0, o1.getShape().getProperty(k1).getFlags());
        assertEquals(v1, uncachedGet(o1, k1));

        setNode1.putConstant(o1, k1, v1, flags);
        assertTrue(o1.getShape().getProperty(k1).getLocation().isConstant());
        assertEquals(flags, o1.getShape().getProperty(k1).getFlags());
        assertEquals(v1, uncachedGet(o1, k1));

        setNode1.put(o1, k1, v2);
        assertFalse(o1.getShape().getProperty(k1).getLocation().isConstant());
        assertEquals(flags, o1.getShape().getProperty(k1).getFlags());
        assertEquals(v2, uncachedGet(o1, k1));
    }

    @Test
    public void testPutConstant2() {
        DynamicObject o1 = createEmpty();
        String k1 = "key1";
        int v1 = 42;
        int v2 = 43;
        int flags = 0xf;

        DynamicObjectLibrary setNode1 = createLibraryForKey(k1);

        setNode1.putConstant(o1, k1, v1, 0);
        assertTrue(o1.getShape().getProperty(k1).getLocation().isConstant());
        assertEquals(0, o1.getShape().getProperty(k1).getFlags());
        assertEquals(v1, uncachedGet(o1, k1));

        setNode1.putWithFlags(o1, k1, v2, flags);
        if (isNewLayout(o1)) {
            assertFalse(o1.getShape().getProperty(k1).getLocation().isConstant());
        }
        assertEquals(flags, o1.getShape().getProperty(k1).getFlags());
        assertEquals(v2, uncachedGet(o1, k1));
    }

    @Test
    public void testCachedShape() {
        String key = "testKey";
        DynamicObject o1 = createEmpty();
        DynamicObject o2 = createEmpty();
        DynamicObject o3 = createEmpty();

        uncachedPut(o1, key, o2);
        uncachedPut(o2, key, o3);
        uncachedPut(o3, key, 42);

        TestNestedDispatchNode node = adopt(TestNestedDispatchNodeGen.create());
        assertEquals(42, node.execute(o1));
        assertEquals(42, node.execute(o1));
        assertEquals(42, node.execute(o2));
        assertEquals(42, node.execute(o1));
        assertEquals(42, node.execute(o2));
        assertEquals(42, node.execute(o2));
    }

    private static void uncachedPut(DynamicObject obj, Object key, Object value) {
        DynamicObjectLibrary.getUncached().put(obj, key, value);
    }

    private static void uncachedPut(DynamicObject obj, Object key, Object value, int flags) {
        DynamicObjectLibrary.getUncached().putWithFlags(obj, key, value, flags);
    }

    private static void uncachedSet(DynamicObject obj, Object key, Object value) {
        DynamicObjectLibrary.getUncached().putIfPresent(obj, key, value);
    }

    private static Object uncachedGet(DynamicObject obj, Object key) {
        return DynamicObjectLibrary.getUncached().getOrDefault(obj, key, null);
    }

    private static Property uncachedGetProperty(DynamicObject obj, Object key) {
        return DynamicObjectLibrary.getUncached().getProperty(obj, key);
    }

    private static Object newObjectType() {
        return new com.oracle.truffle.api.object.ObjectType();
    }

    private List<Object> getKeyList(DynamicObject obj) {
        DynamicObjectLibrary objectLibrary = createLibrary(DynamicObjectLibrary.class, obj);
        return Arrays.asList(objectLibrary.getKeyArray(obj));
    }

    private static boolean isNewLayout(DynamicObject obj) {
        return !(obj instanceof DynamicObjectImpl);
    }
}
