/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.lang.invoke.MethodHandles;
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

@RunWith(Parameterized.class)
public class DynamicObjectNodesTest extends AbstractPolyglotTest {

    public enum TestRun {
        CACHED,
        UNCACHED;

        public boolean isCached() {
            return this == CACHED;
        }
    }

    @Parameter(0) public TestRun run;
    @Parameter(1) public Supplier<? extends DynamicObject> emptyObjectSupplier;

    private DynamicObject createEmpty() {
        return emptyObjectSupplier.get();
    }

    @Parameters
    public static Collection<Object[]> parameters() {
        Collection<Object[]> params = new ArrayList<>();

        Shape shapeMin = Shape.newBuilder().build();
        Supplier<? extends DynamicObject> minimalSupplier = () -> new TestDynamicObjectMinimal(shapeMin);
        addParams(params, minimalSupplier);

        Shape shapeDef = Shape.newBuilder().layout(TestDynamicObjectDefault.class, MethodHandles.lookup()).build();
        Supplier<? extends DynamicObject> defaultSupplier = () -> new TestDynamicObjectDefault(shapeDef);
        addParams(params, defaultSupplier);

        return params;
    }

    private static void addParams(Collection<Object[]> params, Supplier<? extends Object> supplier) {
        for (TestRun run : TestRun.values()) {
            params.add(new Object[]{run, supplier});
        }
    }

    private DynamicObject.GetNode createGetNode() {
        return switch (run) {
            case CACHED -> DynamicObject.GetNode.create();
            case UNCACHED -> DynamicObject.GetNode.getUncached();
        };
    }

    private DynamicObject.PutNode createPutNode() {
        return switch (run) {
            case CACHED -> DynamicObject.PutNode.create();
            case UNCACHED -> DynamicObject.PutNode.getUncached();
        };
    }

    private DynamicObject.PutConstantNode createPutConstantNode() {
        return switch (run) {
            case CACHED -> DynamicObject.PutConstantNode.create();
            case UNCACHED -> DynamicObject.PutConstantNode.getUncached();
        };
    }

    private DynamicObject.CopyPropertiesNode createCopyPropertiesNode() {
        return switch (run) {
            case CACHED -> DynamicObject.CopyPropertiesNode.create();
            case UNCACHED -> DynamicObject.CopyPropertiesNode.getUncached();
        };
    }

    private DynamicObject.RemoveKeyNode createRemoveKeyNode() {
        return switch (run) {
            case CACHED -> DynamicObject.RemoveKeyNode.create();
            case UNCACHED -> DynamicObject.RemoveKeyNode.getUncached();
        };
    }

    private DynamicObject.ContainsKeyNode createContainsKeyNode() {
        return switch (run) {
            case CACHED -> DynamicObject.ContainsKeyNode.create();
            case UNCACHED -> DynamicObject.ContainsKeyNode.getUncached();
        };
    }

    private DynamicObject.GetDynamicTypeNode createGetDynamicTypeNode() {
        return switch (run) {
            case CACHED -> DynamicObject.GetDynamicTypeNode.create();
            case UNCACHED -> DynamicObject.GetDynamicTypeNode.getUncached();
        };
    }

    private DynamicObject.SetDynamicTypeNode createSetDynamicTypeNode() {
        return switch (run) {
            case CACHED -> DynamicObject.SetDynamicTypeNode.create();
            case UNCACHED -> DynamicObject.SetDynamicTypeNode.getUncached();
        };
    }

    private DynamicObject.GetShapeFlagsNode createGetShapeFlagsNode() {
        return switch (run) {
            case CACHED -> DynamicObject.GetShapeFlagsNode.create();
            case UNCACHED -> DynamicObject.GetShapeFlagsNode.getUncached();
        };
    }

    private DynamicObject.ResetShapeNode createResetShapeNode() {
        return switch (run) {
            case CACHED -> DynamicObject.ResetShapeNode.create();
            case UNCACHED -> DynamicObject.ResetShapeNode.getUncached();
        };
    }

    private DynamicObject.SetShapeFlagsNode createSetShapeFlagsNode() {
        return switch (run) {
            case CACHED -> DynamicObject.SetShapeFlagsNode.create();
            case UNCACHED -> DynamicObject.SetShapeFlagsNode.getUncached();
        };
    }

    private DynamicObject.AddShapeFlagsNode createAddShapeFlagsNode() {
        return switch (run) {
            case CACHED -> DynamicObject.AddShapeFlagsNode.create();
            case UNCACHED -> DynamicObject.AddShapeFlagsNode.getUncached();
        };
    }

    private DynamicObject.IsSharedNode createIsSharedNode() {
        return switch (run) {
            case CACHED -> DynamicObject.IsSharedNode.create();
            case UNCACHED -> DynamicObject.IsSharedNode.getUncached();
        };
    }

    private DynamicObject.MarkSharedNode createMarkSharedNode() {
        return switch (run) {
            case CACHED -> DynamicObject.MarkSharedNode.create();
            case UNCACHED -> DynamicObject.MarkSharedNode.getUncached();
        };
    }

    private DynamicObject.SetPropertyFlagsNode createSetPropertyFlagsNode() {
        return switch (run) {
            case CACHED -> DynamicObject.SetPropertyFlagsNode.create();
            case UNCACHED -> DynamicObject.SetPropertyFlagsNode.getUncached();
        };
    }

    private DynamicObject.SetPropertyFlagsNode createSetPropertyFlagsNodeForKey(@SuppressWarnings("unused") Object k) {
        return switch (run) {
            case CACHED -> DynamicObject.SetPropertyFlagsNode.create();
            case UNCACHED -> DynamicObject.SetPropertyFlagsNode.getUncached();
        };
    }

    private DynamicObject.GetPropertyFlagsNode createGetPropertyFlagsNode() {
        return switch (run) {
            case CACHED -> DynamicObject.GetPropertyFlagsNode.create();
            case UNCACHED -> DynamicObject.GetPropertyFlagsNode.getUncached();
        };
    }

    private DynamicObject.GetPropertyFlagsNode createGetPropertyFlagsNodeForKey(@SuppressWarnings("unused") Object k) {
        return switch (run) {
            case CACHED -> DynamicObject.GetPropertyFlagsNode.create();
            case UNCACHED -> DynamicObject.GetPropertyFlagsNode.getUncached();
        };
    }

    private DynamicObject.GetPropertyNode createGetPropertyNode() {
        return switch (run) {
            case CACHED -> DynamicObject.GetPropertyNode.create();
            case UNCACHED -> DynamicObject.GetPropertyNode.getUncached();
        };
    }

    private DynamicObject.GetPropertyNode createGetPropertyNodeForKey(@SuppressWarnings("unused") Object k) {
        return switch (run) {
            case CACHED -> DynamicObject.GetPropertyNode.create();
            case UNCACHED -> DynamicObject.GetPropertyNode.getUncached();
        };
    }

    private DynamicObject.GetKeyArrayNode createGetKeyArrayNode() {
        return switch (run) {
            case CACHED -> DynamicObject.GetKeyArrayNode.create();
            case UNCACHED -> DynamicObject.GetKeyArrayNode.getUncached();
        };
    }

    private DynamicObject.GetPropertyArrayNode createGetPropertyArrayNode() {
        return switch (run) {
            case CACHED -> DynamicObject.GetPropertyArrayNode.create();
            case UNCACHED -> DynamicObject.GetPropertyArrayNode.getUncached();
        };
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

        var getNode = createGetNode();
        assertEquals(v1, getNode.execute(o1, k1, null));
        assertEquals(v1, getNode.executeInt(o1, k1, null));
        assertEquals(v1, getNode.execute(o2, k1, null));
        assertEquals(v1, getNode.executeInt(o2, k1, null));

        String v2 = "asdf";
        uncachedSet(o1, k1, v2);

        getNode = createGetNode();
        assertEquals(v2, getNode.execute(o1, k1, null));
        try {
            getNode.executeInt(o1, k1, null);
            fail();
        } catch (UnexpectedResultException e) {
            assertEquals(v2, e.getResult());
        }
        assertEquals(v1, getNode.execute(o2, k1, null));
        assertEquals(v1, getNode.executeInt(o2, k1, null));

        String missingKey = "missing";
        var getMissingKey = createGetNode();
        assertEquals(null, getMissingKey.execute(o1, missingKey, null));
        assertEquals(404, getMissingKey.executeInt(o1, missingKey, 404));
        var getMissingKey2 = createGetNode();
        assertEquals(null, getMissingKey2.execute(o1, missingKey, null));
        assertEquals(404, getMissingKey2.executeInt(o1, missingKey, 404));
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

        var setNode = createPutNode();
        setNode.execute(o1, key1, intval2);
        assertEquals(intval2, uncachedGet(o1, key1));
        setNode.execute(o1, key1, intval1);
        assertEquals(intval1, uncachedGet(o1, key1));
        setNode.execute(o2, key1, intval2);
        assertEquals(intval2, uncachedGet(o2, key1));
        setNode.execute(o2, key1, intval1);
        assertEquals(intval1, uncachedGet(o2, key1));
        assertSame(o1.getShape(), o2.getShape());

        String strval1 = "asdf";
        setNode.execute(o1, key1, strval1);
        assertEquals(strval1, uncachedGet(o1, key1));

        String key2 = "key2";
        String strval2 = "qwer";
        var setNode2 = createPutNode();
        setNode2.execute(o1, key2, strval2);
        assertEquals(strval2, uncachedGet(o1, key2));
        setNode2.execute(o1, key2, intval1);
        assertEquals(intval1, uncachedGet(o1, key2));

        var setNode3 = createPutNode();
        setNode3.execute(o1, key2, strval1);
        assertEquals(strval1, uncachedGet(o1, key2));
        // assertTrue(setNode3.accepts(o1));
        var setNode4 = createPutNode();
        setNode4.execute(o1, key2, intval2);
        assertEquals(intval2, uncachedGet(o1, key2));
        // assertTrue(setNode4.accepts(o1));
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

        var setNode = createPutNode();
        assertTrue(setNode.executeIfPresent(o1, key1, intval2));
        assertEquals(intval2, uncachedGet(o1, key1));
        assertTrue(setNode.executeIfPresent(o1, key1, intval1));
        assertEquals(intval1, uncachedGet(o1, key1));
        assertTrue(setNode.executeIfPresent(o2, key1, intval2));
        assertEquals(intval2, uncachedGet(o2, key1));
        assertTrue(setNode.executeIfPresent(o2, key1, intval1));
        assertEquals(intval1, uncachedGet(o2, key1));
        assertSame(o1.getShape(), o2.getShape());
        assertTrue(setNode.executeWithFlagsIfPresent(o2, key1, intval2, 0b0));
        assertEquals(intval2, uncachedGet(o2, key1));
        assertSame(o1.getShape(), o2.getShape());

        assertTrue(setNode.executeWithFlagsIfPresent(o2, key1, intval1, 0b11));
        assertEquals(intval1, uncachedGet(o2, key1));
        assertEquals(0b11, uncachedGetPropertyFlags(o2, key1, -1));
        assertTrue(setNode.executeWithFlagsIfPresent(o2, key1, intval2, 0b0));
        assertEquals(intval2, uncachedGet(o2, key1));
        assertEquals(0b0, uncachedGetPropertyFlags(o2, key1, -1));

        String strval1 = "asdf";
        setNode.execute(o1, key1, strval1);
        assertEquals(strval1, uncachedGet(o1, key1));

        String key2 = "key2";
        String strval2 = "qwer";
        var setNode2 = createPutNode();
        var containsKeyNode2 = createContainsKeyNode();
        assertFalse(DynamicObject.ContainsKeyNode.getUncached().execute(o1, key2));
        assertFalse(setNode2.executeIfPresent(o1, key2, strval2));
        assertFalse(setNode2.executeWithFlagsIfPresent(o1, key2, strval2, 0b11));
        assertFalse(containsKeyNode2.execute(o1, key2));
        assertEquals(null, uncachedGet(o1, key2));

        setNode2.execute(o1, key2, strval2);
        assertTrue(DynamicObject.ContainsKeyNode.getUncached().execute(o1, key2));
        assertEquals(strval2, uncachedGet(o1, key2));
        assertEquals(0, uncachedGetPropertyFlags(o1, key2, -1));
        setNode2.executeWithFlags(o2, key2, strval2, 0b11);
        assertTrue(DynamicObject.ContainsKeyNode.getUncached().execute(o2, key2));
        assertEquals(strval2, uncachedGet(o2, key2));
        assertEquals(0b11, uncachedGetPropertyFlags(o2, key2, -1));

        var setNode3 = createPutNode();
        assertTrue(setNode3.executeIfPresent(o1, key2, intval1));
        assertEquals(intval1, uncachedGet(o1, key2));
    }

    @Test
    public void testPutIfAbsent() {
        String key1 = "key1";
        String key2 = "key2";
        int intval1 = 42;
        int intval2 = 43;
        DynamicObject o1 = createEmpty();
        DynamicObject o2 = createEmpty();
        assertSame(o1.getShape(), o2.getShape());

        var setNode = createPutNode();
        assertTrue(setNode.executeIfAbsent(o1, key1, intval1));
        assertTrue(setNode.executeWithFlagsIfAbsent(o2, key1, intval1, 0));
        assertSame(o1.getShape(), o2.getShape());
        assertEquals(intval1, uncachedGet(o1, key1));
        assertEquals(intval1, uncachedGet(o2, key1));
        assertEquals(0, uncachedGetPropertyFlags(o1, key1, -1));
        assertEquals(0, uncachedGetPropertyFlags(o2, key1, -1));

        Shape shapeBefore = o1.getShape();
        assertFalse(setNode.executeIfAbsent(o1, key1, intval2));
        assertFalse(setNode.executeWithFlagsIfAbsent(o1, key1, intval2, 0b11));
        assertEquals(intval1, uncachedGet(o1, key1));
        Shape shapeAfter = o1.getShape();
        assertSame(shapeBefore, shapeAfter);

        assertTrue(setNode.executeWithFlagsIfAbsent(o1, key2, intval2, 0b11));
        assertTrue(setNode.executeIfAbsent(o2, key2, intval2));
        assertEquals(intval2, uncachedGet(o1, key2));
        assertEquals(intval2, uncachedGet(o2, key2));
        assertEquals(0b11, uncachedGetPropertyFlags(o1, key2, -1));
        assertEquals(0, uncachedGetPropertyFlags(o2, key2, -1));
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

        var setNode1 = createPutNode();
        setNode1.execute(o1, k1, v2);
        assertEquals(v2, uncachedGet(o1, k1));
        assertEquals(0, uncachedGetProperty(o1, k1).getFlags());
        setNode1.execute(o1, k1, v1);
        assertEquals(v1, uncachedGet(o1, k1));
        setNode1.execute(o2, k1, v2);
        assertEquals(v2, uncachedGet(o2, k1));
        setNode1.execute(o2, k1, v1);
        assertEquals(v1, uncachedGet(o2, k1));
        assertSame(o1.getShape(), o2.getShape());

        assertEquals(v1, uncachedGet(o3, k1));
        assertSame(o1.getShape(), o3.getShape());
        uncachedPut(o3, k1, v1);
        assertEquals(v1, uncachedGet(o3, k1));
        assertEquals(0, uncachedGetProperty(o3, k1).getFlags());
        assertSame(o1.getShape(), o3.getShape());

        String v3 = "asdf";
        setNode1.execute(o1, k1, v3);
        assertEquals(v3, uncachedGet(o1, k1));

        String k2 = "key2";
        String v4 = "qwer";
        var setNode2 = createPutNode();

        setNode2.execute(o1, k2, v4);
        assertEquals(v4, uncachedGet(o1, k2));
        setNode2.execute(o1, k2, v1);
        assertEquals(v1, uncachedGet(o1, k2));

        int f2 = 0x42;
        var setNode3 = createPutNode();

        setNode3.executeWithFlags(o3, k1, v1, f2);
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
        var setNode1 = createPutNode();
        setNode1.executeWithFlags(o1, k1, v2, flags);
        assertEquals(v2, uncachedGet(o1, k1));
        assertEquals(flags, uncachedGetProperty(o1, k1).getFlags());
        setNode1.executeWithFlags(o1, k1, v1, flags);
        assertEquals(v1, uncachedGet(o1, k1));
        setNode1.executeWithFlags(o2, k1, v2, flags);
        assertEquals(v2, uncachedGet(o2, k1));
        setNode1.executeWithFlags(o2, k1, v1, flags);
        assertEquals(v1, uncachedGet(o2, k1));
        assertSame(o1.getShape(), o2.getShape());

        assertEquals(v1, uncachedGet(o3, k1));
        assertNotSame(o1.getShape(), o3.getShape());
        uncachedPut(o3, k1, v1, flags);
        assertEquals(v1, uncachedGet(o3, k1));
        assertEquals(flags, uncachedGetProperty(o3, k1).getFlags());
        // assertSame(o1.getShape(), o3.getShape());

        String v3 = "asdf";
        setNode1.executeWithFlags(o1, k1, v3, flags);
        assertEquals(v3, uncachedGet(o1, k1));

        String k2 = "key2";
        String v4 = "qwer";
        var setNode2 = createPutNode();

        setNode2.execute(o1, k2, v4);
        assertEquals(v4, uncachedGet(o1, k2));
        setNode2.execute(o1, k2, v1);
        assertEquals(v1, uncachedGet(o1, k2));

        int f2 = 0x42;
        var setNode3 = createPutNode();

        setNode3.executeWithFlags(o3, k1, v1, f2);
        assertEquals(v1, uncachedGet(o3, k1));
        assertEquals(f2, uncachedGetProperty(o3, k1).getFlags());
    }

    @Test
    public void testCopyProperties() {
        var getNode = createGetNode();
        var setNode = createPutNode();
        var copyPropertiesNode = createCopyPropertiesNode();
        var getPropertyFlagsNode = createGetPropertyFlagsNode();

        DynamicObject o1 = createEmpty();
        setNode.executeWithFlags(o1, "key1", 1, 1);
        setNode.executeWithFlags(o1, "key2", 2, 2);
        DynamicObject o2 = createEmpty();
        copyPropertiesNode.execute(o1, o2);
        assertEquals(1, getNode.execute(o1, "key1", null));
        assertEquals(2, getNode.execute(o1, "key2", null));
        assertEquals(1, getPropertyFlagsNode.execute(o1, "key1", 0));
        assertEquals(2, getPropertyFlagsNode.execute(o1, "key2", 0));
        assertSame(o1.getShape(), o2.getShape());
    }

    @Test
    public void testTypeIdAndShapeFlags() {
        Object myType = newObjectType();
        int flags = 42;
        String key = "key1";

        DynamicObject.SetDynamicTypeNode setDynamicTypeNode = createSetDynamicTypeNode();
        DynamicObject.GetDynamicTypeNode getDynamicTypeNode = createGetDynamicTypeNode();
        DynamicObject.GetShapeFlagsNode getShapeFlagsNode = createGetShapeFlagsNode();
        DynamicObject.SetShapeFlagsNode setShapeFlagsNode = createSetShapeFlagsNode();
        var putNode = createPutNode();

        DynamicObject o1 = createEmpty();
        setDynamicTypeNode.execute(o1, myType);
        assertSame(myType, getDynamicTypeNode.execute(o1));

        DynamicObject o2 = createEmpty();
        setDynamicTypeNode.execute(o2, myType);
        assertSame(myType, getDynamicTypeNode.execute(o2));
        assertSame(o1.getShape(), o2.getShape());

        DynamicObject o3 = createEmpty();
        setShapeFlagsNode.execute(o3, flags);
        setDynamicTypeNode.execute(o3, myType);
        assertSame(myType, getDynamicTypeNode.execute(o3));
        assertEquals(flags, getShapeFlagsNode.execute(o3));

        DynamicObject o4 = createEmpty();
        setShapeFlagsNode.execute(o4, flags);
        putNode.execute(o4, key, 42);
        setDynamicTypeNode.execute(o4, myType);
        putNode.execute(o4, key, "value");
        assertSame(myType, getDynamicTypeNode.execute(o4));

        assertSame(myType, getDynamicTypeNode.execute(o4));
        assertSame(myType, getDynamicTypeNode.execute(o4));
        Object myType2 = newObjectType();
        setDynamicTypeNode.execute(o4, myType2);
        assertSame(myType2, getDynamicTypeNode.execute(o4));
    }

    @Test
    public void testShapeFlags() {
        final int flags = 0b101010;

        DynamicObject.SetDynamicTypeNode setDynamicTypeNode = createSetDynamicTypeNode();
        DynamicObject.GetShapeFlagsNode getShapeFlagsNode = createGetShapeFlagsNode();
        DynamicObject.SetShapeFlagsNode setShapeFlagsNode = createSetShapeFlagsNode();
        DynamicObject.MarkSharedNode markSharedNode = createMarkSharedNode();

        DynamicObject o1 = createEmpty();
        setShapeFlagsNode.execute(o1, flags);
        assertEquals(flags, getShapeFlagsNode.execute(o1));

        DynamicObject o2 = createEmpty();
        setShapeFlagsNode.execute(o2, flags);
        assertEquals(flags, getShapeFlagsNode.execute(o2));
        assertSame(o1.getShape(), o2.getShape());

        DynamicObject o3 = createEmpty();
        setShapeFlagsNode.execute(o3, 1);
        setDynamicTypeNode.execute(o3, newObjectType());
        setShapeFlagsNode.execute(o3, flags);
        setDynamicTypeNode.execute(o3, newObjectType());
        assertEquals(flags, getShapeFlagsNode.execute(o2));

        DynamicObject o4 = createEmpty();
        setShapeFlagsNode.execute(o4, flags);
        markSharedNode.execute(o4);
        assertEquals(flags, getShapeFlagsNode.execute(o2));

        assertEquals(flags, getShapeFlagsNode.execute(o4));
        int flags2 = 43;
        setShapeFlagsNode.execute(o4, flags2);
        // assertEquals(run != TestRun.CACHED, cached.accepts(o4));
        assertEquals(flags2, getShapeFlagsNode.execute(o4));
    }

    @Test
    public void testUpdateShapeFlags() {
        int f1 = 0xf;
        int f2 = 0x10;
        int f3 = 0x1f;

        DynamicObject.GetShapeFlagsNode getShapeFlagsNode = createGetShapeFlagsNode();
        DynamicObject.SetShapeFlagsNode setShapeFlagsNode = createSetShapeFlagsNode();

        DynamicObject o1 = createEmpty();
        uncachedPut(o1, "key", 42, 0);
        assertTrue(setShapeFlagsNode.execute(o1, f1));
        assertEquals(f1, getShapeFlagsNode.execute(o1));
        assertEquals(f1, o1.getShape().getFlags());
        assertTrue(updateShapeFlags(getShapeFlagsNode, setShapeFlagsNode, o1, f -> f | f2));
        assertEquals(f3, getShapeFlagsNode.execute(o1));
        assertEquals(f3, o1.getShape().getFlags());
    }

    private static boolean updateShapeFlags(DynamicObject.GetShapeFlagsNode getShapeFlagsNode, DynamicObject.SetShapeFlagsNode setShapeFlagsNode, DynamicObject obj, IntUnaryOperator updateFunction) {
        int oldFlags = getShapeFlagsNode.execute(obj);
        int newFlags = updateFunction.applyAsInt(oldFlags);
        if (oldFlags == newFlags) {
            return false;
        }
        return setShapeFlagsNode.execute(obj, newFlags);
    }

    @Test
    public void testHasAddShapeFlags() {
        final int flags = 0b101010;

        DynamicObject.GetShapeFlagsNode getShapeFlagsNode = createGetShapeFlagsNode();
        DynamicObject.SetShapeFlagsNode setShapeFlagsNode = createSetShapeFlagsNode();
        DynamicObject.AddShapeFlagsNode addShapeFlagsNode = createAddShapeFlagsNode();

        DynamicObject o1 = createEmpty();
        setShapeFlagsNode.execute(o1, flags);
        assertEquals(flags, getShapeFlagsNode.execute(o1));
        assertTrue(hasShapeFlags(getShapeFlagsNode, o1, flags));
        assertTrue(hasShapeFlags(getShapeFlagsNode, o1, 0b10));
        assertFalse(hasShapeFlags(getShapeFlagsNode, o1, 0b11));
        addShapeFlagsNode.execute(o1, 0b1);
        assertTrue(hasShapeFlags(getShapeFlagsNode, o1, 0b11));
        assertEquals(flags | 0b1, getShapeFlagsNode.execute(o1));
    }

    static boolean hasShapeFlags(DynamicObject.GetShapeFlagsNode getShapeFlagsNode, DynamicObject obj, int flags) {
        return (getShapeFlagsNode.execute(obj) & flags) == flags;
    }

    @Test
    public void testMakeShared() {
        String key = "key";

        DynamicObject.IsSharedNode isSharedNode = createIsSharedNode();
        DynamicObject.MarkSharedNode markSharedNode = createMarkSharedNode();
        var putNode = createPutNode();
        var containsKeyNode = createContainsKeyNode();

        DynamicObject o1 = createEmpty();
        assertFalse(isSharedNode.execute(o1));
        markSharedNode.execute(o1);
        assertTrue(isSharedNode.execute(o1));
        putNode.execute(o1, key, "value");
        assertTrue(isSharedNode.execute(o1));
        assertTrue(containsKeyNode.execute(o1, key));
    }

    @Test
    public void testPropertyFlags() {
        String k1 = "key1";
        int v1 = 42;
        int v2 = 43;
        int f1 = 0xf;
        int f2 = 0x10;
        int f3 = 0x1f;

        DynamicObject.SetPropertyFlagsNode setPropertyFlagsNode = createSetPropertyFlagsNodeForKey(k1);
        DynamicObject.GetPropertyFlagsNode getPropertyFlagsNode = createGetPropertyFlagsNodeForKey(k1);
        DynamicObject.GetPropertyNode getPropertyNode = createGetPropertyNodeForKey(k1);

        DynamicObject o1 = createEmpty();
        uncachedPut(o1, k1, v1, 0);
        assertTrue(setPropertyFlagsNode.execute(o1, k1, f1));
        assertEquals(f1, getPropertyFlagsNode.execute(o1, k1, -1));
        assertEquals(f1, uncachedGetProperty(o1, k1).getFlags());
        assertTrue(updatePropertyFlags(getPropertyNode, setPropertyFlagsNode, o1, k1, f -> f | f2));
        assertEquals(f3, getPropertyFlagsNode.execute(o1, k1, -1));
        assertEquals(f3, uncachedGetProperty(o1, k1).getFlags());

        Shape before = o1.getShape();
        assertTrue(setPropertyFlagsNode.execute(o1, k1, f3));
        assertFalse(updatePropertyFlags(getPropertyNode, setPropertyFlagsNode, o1, k1, f -> f | f2));
        assertEquals(f3, getPropertyFlagsNode.execute(o1, k1, -1));
        assertEquals(f3, uncachedGetProperty(o1, k1).getFlags());
        assertSame(before, o1.getShape());

        DynamicObject o2 = createEmpty();
        uncachedPut(o2, k1, v2, 0);
        assertTrue(setPropertyFlagsNode.execute(o2, k1, f1));
        assertEquals(f1, getPropertyFlagsNode.execute(o2, k1, -1));
        assertTrue(updatePropertyFlags(getPropertyNode, setPropertyFlagsNode, o2, k1, f -> f | f2));
        assertEquals(f3, getPropertyFlagsNode.execute(o2, k1, -1));
        assertSame(o1.getShape(), o2.getShape());

        DynamicObject o3 = createEmpty();
        assertFalse(setPropertyFlagsNode.execute(o3, k1, f1));
    }

    private static boolean updatePropertyFlags(DynamicObject.GetPropertyNode getPropertyNode,
                    DynamicObject.SetPropertyFlagsNode setPropertyFlagsNode,
                    DynamicObject obj,
                    String key,
                    IntUnaryOperator updateFunction) {
        Property property = getPropertyNode.execute(obj, key);
        if (property == null) {
            return false;
        }
        int oldFlags = property.getFlags();
        int newFlags = updateFunction.applyAsInt(oldFlags);
        if (oldFlags == newFlags) {
            return false;
        }
        return setPropertyFlagsNode.execute(obj, key, newFlags);
    }

    @Test
    public void testRemove() {
        int v1 = 42;
        int v2 = 43;
        Object v3 = "value";

        String k1 = "key1";
        String k2 = "key2";
        String k3 = "key3";
        String k4 = "key4";

        DynamicObject o1 = createEmpty();
        uncachedPut(o1, k1, v1, 0);
        uncachedPut(o1, k2, v2, 0);
        uncachedPut(o1, k3, v3, 0);

        var removeKey1 = createRemoveKeyNode();
        var removeKey3 = createRemoveKeyNode();
        var removeKey4 = createRemoveKeyNode();
        var getKey1 = createGetNode();
        var getKey2 = createGetNode();

        assertFalse(removeKey4.execute(o1, k4));
        assertTrue(removeKey3.execute(o1, k3));
        assertEquals(Arrays.asList(k1, k2), getKeyList(o1));
        uncachedPut(o1, k3, v3, 0);
        assertEquals(Arrays.asList(k1, k2, k3), getKeyList(o1));
        assertTrue(removeKey3.execute(o1, k3));
        assertEquals(Arrays.asList(k1, k2), getKeyList(o1));
        uncachedPut(o1, k3, v3, 0);
        assertTrue(removeKey1.execute(o1, k1));
        assertEquals(Arrays.asList(k2, k3), getKeyList(o1));
        uncachedPut(o1, k1, v1, 0);
        assertEquals(Arrays.asList(k2, k3, k1), getKeyList(o1));
        assertTrue(removeKey3.execute(o1, k3));
        assertEquals(Arrays.asList(k2, k1), getKeyList(o1));
        assertEquals(v1, getKey1.execute(o1, k1, null));
        assertEquals(v2, getKey2.execute(o1, k2, null));
    }

    @Test
    public void testResetShape() {
        int v1 = 42;
        int v2 = 43;

        DynamicObject.GetShapeFlagsNode getShapeFlagsNode = createGetShapeFlagsNode();
        DynamicObject.ResetShapeNode resetShapeNode = createResetShapeNode();

        DynamicObject o1 = createEmpty();
        Shape emptyShape = o1.getShape();
        uncachedPut(o1, "key1", v1, 0);
        uncachedPut(o1, "key2", v2, 0);
        resetShapeNode.execute(o1, emptyShape);
        assertSame(emptyShape, o1.getShape());

        assumeTrue("new layout only", isNewLayout());
        int flags = 0xf;
        DynamicObject o2 = createEmpty();
        Shape newEmptyShape = Shape.newBuilder().shapeFlags(flags).build();
        uncachedPut(o2, "key1", v1, 0);
        uncachedPut(o2, "key2", v2, 0);
        resetShapeNode.execute(o2, newEmptyShape);
        assertSame(newEmptyShape, o2.getShape());
        assertEquals(flags, getShapeFlagsNode.execute(o2));
    }

    @Test
    public void testGetKeysAndProperties() {
        int v1 = 42;
        int v2 = 43;
        Object v3 = "value";

        DynamicObject.GetKeyArrayNode getKeyArrayNode = createGetKeyArrayNode();
        DynamicObject.GetPropertyArrayNode getPropertyArrayNode = createGetPropertyArrayNode();

        DynamicObject o1 = createEmpty();
        uncachedPut(o1, "key1", v1, 1);
        uncachedPut(o1, "key2", v2, 2);
        uncachedPut(o1, "key3", v3, 3);

        Object[] keyArray = getKeyArrayNode.execute(o1);
        Property[] properties = getPropertyArrayNode.execute(o1);
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
        var removeKey1 = createRemoveKeyNode();
        removeKey1.execute(o1, "key1");
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

        DynamicObject.GetPropertyNode getPropertyNode = createGetPropertyNode();
        DynamicObject.GetPropertyFlagsNode getPropertyFlagsNode = createGetPropertyFlagsNode();

        // assertTrue(lib.accepts(o1));
        for (int i = 1; i <= 3; i++) {
            Object key = "key" + i;
            assertSame(o1.getShape().getProperty(key), getPropertyNode.execute(o1, key));
            assertEquals(i, o1.getShape().getProperty(key).getFlags());
            assertEquals(i, getPropertyFlagsNode.execute(o1, key, -1));
        }
        // assertTrue(lib.accepts(o1));
    }

    @Test
    public void testPutConstant1() {
        DynamicObject o1 = createEmpty();
        String k1 = "key1";
        int v1 = 42;
        int v2 = 43;
        int flags = 0xf;

        var putConstantNode = createPutConstantNode();
        var putNode = createPutNode();

        putConstantNode.executeWithFlags(o1, k1, v1, 0);
        assertTrue(o1.getShape().getProperty(k1).getLocation().isConstant());
        assertEquals(0, o1.getShape().getProperty(k1).getFlags());
        assertEquals(v1, uncachedGet(o1, k1));

        putConstantNode.executeWithFlags(o1, k1, v1, flags);
        assertTrue(o1.getShape().getProperty(k1).getLocation().isConstant());
        assertEquals(flags, o1.getShape().getProperty(k1).getFlags());
        assertEquals(v1, uncachedGet(o1, k1));

        putNode.execute(o1, k1, v2);
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

        var putConstantNode = createPutConstantNode();
        var putNode = createPutNode();

        putConstantNode.executeWithFlags(o1, k1, v1, 0);
        assertTrue(o1.getShape().getProperty(k1).getLocation().isConstant());
        assertEquals(0, o1.getShape().getProperty(k1).getFlags());
        assertEquals(v1, uncachedGet(o1, k1));

        putNode.executeWithFlags(o1, k1, v2, flags);
        if (isNewLayout()) {
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

        TestNestedDispatchGetNode node = adoptNode(TestNestedDispatchGetNodeGen.create()).get();
        assertEquals(42, node.execute(o1));
        assertEquals(42, node.execute(o1));
        assertEquals(42, node.execute(o2));
        assertEquals(42, node.execute(o1));
        assertEquals(42, node.execute(o2));
        assertEquals(42, node.execute(o2));
    }

    @Test
    public void testPropertyAndShapeFlags() {
        DynamicObject o1 = createEmpty();
        fillObjectWithProperties(o1, false);
        updateAllFlags(o1, 3);
        DynamicObject o2 = createEmpty();
        fillObjectWithProperties(o2, true);
        DynamicObject o3 = createEmpty();
        fillObjectWithProperties(o3, false);
        DynamicObject.PutNode.getUncached().execute(o1, "k13", false);
        updateAllFlags(o2, 3);
        updateAllFlags(o3, 3);
        var getNode = createGetNode();
        assertEquals(1, getNode.execute(o3, "k13", null));
    }

    private void fillObjectWithProperties(DynamicObject obj, boolean b) {
        DynamicObject.PutNode putNode = createPutNode();

        for (int i = 0; i < 20; i++) {
            Object value;
            if (i % 2 == 0) {
                if (i == 14) {
                    value = "string";
                } else {
                    value = new ArrayList<>();
                }
            } else {
                if (b && i == 13) {
                    value = new ArrayList<>();
                } else {
                    value = 1;
                }
            }
            int flags = (i == 17 || i == 13) ? 1 : 3;
            putNode.executeWithFlags(obj, "k" + i, value, flags);
        }
    }

    private void updateAllFlags(DynamicObject obj, int flags) {
        DynamicObject.SetPropertyFlagsNode setPropertyFlagsNode = createSetPropertyFlagsNode();
        DynamicObject.GetPropertyArrayNode getPropertyArrayNode = createGetPropertyArrayNode();

        for (Property property : getPropertyArrayNode.execute(obj)) {
            int oldFlags = property.getFlags();
            int newFlags = oldFlags | flags;
            if (newFlags != oldFlags) {
                Object key = property.getKey();
                setPropertyFlagsNode.execute(obj, key, newFlags);
            }
        }

        DynamicObject.SetShapeFlagsNode setShapeFlags = createSetShapeFlagsNode();
        setShapeFlags.execute(obj, flags);
    }

    private static void uncachedPut(DynamicObject obj, Object key, Object value) {
        DynamicObject.PutNode.getUncached().execute(obj, key, value);
    }

    private static void uncachedPut(DynamicObject obj, Object key, Object value, int flags) {
        DynamicObject.PutNode.getUncached().executeWithFlags(obj, key, value, flags);
    }

    private static void uncachedSet(DynamicObject obj, Object key, Object value) {
        DynamicObject.PutNode.getUncached().executeIfPresent(obj, key, value);
    }

    private static Object uncachedGet(DynamicObject obj, Object key) {
        return DynamicObject.GetNode.getUncached().execute(obj, key, null);
    }

    private static Property uncachedGetProperty(DynamicObject obj, Object key) {
        return DynamicObject.GetPropertyNode.getUncached().execute(obj, key);
    }

    private static int uncachedGetPropertyFlags(DynamicObject obj, Object key, int defaultValue) {
        return DynamicObject.GetPropertyFlagsNode.getUncached().execute(obj, key, defaultValue);
    }

    private static Object newObjectType() {
        return new Object() {
        };
    }

    private static List<Object> getKeyList(DynamicObject obj) {
        return Arrays.asList(DynamicObject.GetKeyArrayNode.getUncached().execute(obj));
    }

    private static boolean isNewLayout() {
        return true;
    }

    @GenerateInline(false)
    public abstract static class TestGet extends Node {
        public abstract Object execute(DynamicObject obj);

        @Specialization
        public Object doGet(DynamicObject obj,
                        @Cached DynamicObject.GetNode get) {
            return get.execute(obj, "test", null);
        }
    }
}
