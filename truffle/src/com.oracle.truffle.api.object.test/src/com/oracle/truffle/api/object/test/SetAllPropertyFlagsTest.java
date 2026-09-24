/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Property;
import com.oracle.truffle.api.object.Shape;

@RunWith(Parameterized.class)
public class SetAllPropertyFlagsTest {
    @Parameterized.Parameters(name = "cached={0}")
    public static Collection<Boolean> data() {
        return Arrays.asList(false, true);
    }

    @Parameterized.Parameter public boolean cached;

    private DynamicObject.SetAllPropertyFlagsNode createNode() {
        return cached ? DynamicObject.SetAllPropertyFlagsNode.create() : DynamicObject.SetAllPropertyFlagsNode.getUncached();
    }

    private static Shape root(boolean shared) {
        return Shape.newBuilder().layout(TestDynamicObjectMinimal.class, MethodHandles.lookup()).propertyAssumptions(true).shared(shared).build();
    }

    private static Object[] keys(int count) {
        Object[] keys = new Object[count];
        for (int i = 0; i < count; i++) {
            keys[i] = ("key" + i).intern();
        }
        return keys;
    }

    private static DynamicObject object(Shape root, Object[] keys) {
        DynamicObject object = new TestDynamicObjectMinimal(root);
        for (int i = 0; i < keys.length; i++) {
            DynamicObject.PutNode.getUncached().execute(object, keys[i], i);
        }
        return object;
    }

    private static void sequential(DynamicObject object, Object[] keys, int[] flags) {
        for (int i = 0; i < keys.length; i++) {
            DynamicObject.SetPropertyFlagsNode.getUncached().execute(object, keys[i], flags[i]);
        }
    }

    private static void checkValues(DynamicObject object, Object[] keys) {
        assertEquals(Arrays.stream(keys).filter(key -> !(key instanceof HiddenKey)).count(), object.getShape().getPropertyCount());
        for (int i = 0; i < keys.length; i++) {
            assertEquals(i, DynamicObject.GetNode.getUncached().execute(object, keys[i], null));
        }
    }

    @Test
    public void callerKeyArrayMutation() {
        Assume.assumeTrue(!cached || DynamicObject.class.desiredAssertionStatus());
        Object[] keys = {"x"};
        Shape root = root(false);
        DynamicObject first = object(root, new Object[]{"x", "y"});
        DynamicObject second = object(root, new Object[]{"x", "y"});
        var node = createNode();
        node.execute(first, keys, 7);
        keys[0] = "y";
        if (cached) {
            AssertionError error = assertThrows(AssertionError.class, () -> node.execute(second, keys, 7));
            assertTrue(error.getMessage(), error.getMessage().contains("Cached keys or flags array"));
            assertEquals(0, second.getShape().getProperty("y").getFlags());
        } else {
            node.execute(second, keys, 7);
            assertEquals(7, second.getShape().getProperty("y").getFlags());
        }
        assertEquals(0, second.getShape().getProperty("x").getFlags());
    }

    @Test
    public void callerFlagsArrayMutation() {
        Assume.assumeTrue(!cached || DynamicObject.class.desiredAssertionStatus());
        Object[] keys = {"x"};
        int[] flags = {3};
        Shape root = root(false);
        DynamicObject first = object(root, keys);
        DynamicObject second = object(root, keys);
        var node = createNode();
        node.execute(first, keys, flags);
        flags[0] = 7;
        if (cached) {
            AssertionError error = assertThrows(AssertionError.class, () -> node.execute(second, keys, flags));
            assertTrue(error.getMessage(), error.getMessage().contains("Cached keys or flags array"));
            assertEquals(0, second.getShape().getProperty("x").getFlags());
        } else {
            node.execute(second, keys, flags);
            assertEquals(7, second.getShape().getProperty("x").getFlags());
        }
    }

    @Test
    public void callerKeyArrayMutationChecksUntouchedProperties() {
        Assume.assumeTrue(!cached || DynamicObject.class.desiredAssertionStatus());
        HiddenKey hidden = new HiddenKey("hidden");
        Object[] all = {hidden, "y", "z"};
        Object[] keys = {hidden, "z"};
        Shape root = root(false);
        DynamicObject first = object(root, all);
        DynamicObject second = object(root, all);
        DynamicObject.SetPropertyFlagsNode.getUncached().execute(first, "y", 7);
        DynamicObject.SetPropertyFlagsNode.getUncached().execute(second, "y", 7);
        Shape startShape = second.getShape();
        var node = createNode();
        node.execute(first, keys, 7);
        keys[0] = "y";
        // The requested y/z flags match the cached target, but hidden must remain unchanged.
        if (cached) {
            AssertionError error = assertThrows(AssertionError.class, () -> node.execute(second, keys, 7));
            assertTrue(error.getMessage(), error.getMessage().contains("Cached keys or flags array"));
            assertSame(startShape, second.getShape());
            assertEquals(0, second.getShape().getProperty("z").getFlags());
        } else {
            node.execute(second, keys, 7);
            assertEquals(7, second.getShape().getProperty("z").getFlags());
        }
        assertEquals(0, second.getShape().getProperty(hidden).getFlags());
        assertEquals(7, second.getShape().getProperty("y").getFlags());
        checkValues(second, all);
    }

    @Test
    public void callerKeyArrayMutationChecksNoopPlan() {
        Assume.assumeTrue(!cached || DynamicObject.class.desiredAssertionStatus());
        Object[] all = {"x", "y"};
        Object[] keys = {"x", "y"};
        int[] flags = {0, 7};
        Shape root = root(false);
        DynamicObject first = object(root, all);
        DynamicObject second = object(root, all);
        DynamicObject.SetPropertyFlagsNode.getUncached().execute(first, "y", 7);
        DynamicObject.SetPropertyFlagsNode.getUncached().execute(second, "y", 7);
        Shape startShape = second.getShape();
        var node = createNode();
        node.execute(first, keys, flags);
        assertSame(startShape, first.getShape());
        keys[0] = "y";
        // The duplicate changes y and restores it: final flags match, but the cached no-op plan is no longer valid.
        if (cached) {
            AssertionError error = assertThrows(AssertionError.class, () -> node.execute(second, keys, flags));
            assertTrue(error.getMessage(), error.getMessage().contains("Cached keys or flags array"));
            assertSame(startShape, second.getShape());
        } else {
            node.execute(second, keys, flags);
        }
        assertEquals(0, second.getShape().getProperty("x").getFlags());
        assertEquals(7, second.getShape().getProperty("y").getFlags());
        checkValues(second, all);
    }

    @Test
    public void sequentialShapeIdentityAndLocations() {
        Object[] keys = keys(40);
        for (boolean batchFirst : new boolean[]{false, true}) {
            Shape root = root(false);
            DynamicObject a = object(root, keys);
            DynamicObject b = object(root, keys);
            Shape before = a.getShape();
            int[] flags = new int[keys.length];
            for (int i = 0; i < flags.length; i++) {
                flags[i] = i % 4;
            }
            if (!batchFirst) {
                sequential(a, keys, flags);
            }
            createNode().execute(b, keys, flags);
            if (batchFirst) {
                sequential(a, keys, flags);
            }
            assertSame(a.getShape(), b.getShape());
            List<Property> properties = b.getShape().getPropertyList();
            for (int i = 0; i < keys.length; i++) {
                assertSame(keys[i], properties.get(i).getKey());
                assertSame(before.getProperty(keys[i]).getLocation(), b.getShape().getProperty(keys[i]).getLocation());
                assertEquals(flags[i], b.getShape().getProperty(keys[i]).getFlags());
            }
            checkValues(b, keys);
        }
    }

    @Test
    public void masksMissingKeysAndNoops() {
        Object[] keys = {"x", "y", new HiddenKey("hidden")};
        DynamicObject object = object(root(false), keys);
        var node = createNode();
        Shape initialShape = object.getShape();
        node.execute(object, new Object[0], new int[0]);
        assertSame(initialShape, object.getShape());
        node.execute(object, new Object[]{"absent"}, 17);
        assertSame(initialShape, object.getShape());
        node.execute(object, keys, 3);
        for (Object key : keys) {
            assertEquals(3, object.getShape().getProperty(key).getFlags());
        }
        Shape flags3 = object.getShape();
        node.execute(object, keys, 3);
        assertSame(flags3, object.getShape());
        node.executeAdd(object, keys, 4);
        for (Object key : keys) {
            assertEquals(7, object.getShape().getProperty(key).getFlags());
        }
        node.executeRemove(object, keys, 2);
        for (Object key : keys) {
            assertEquals(5, object.getShape().getProperty(key).getFlags());
        }
        node.executeRemoveAndAdd(object, keys, 5, 1);
        for (Object key : keys) {
            assertEquals(1, object.getShape().getProperty(key).getFlags());
        }
        node.execute(object, keys, new int[]{3, 3, 3});
        assertSame(flags3, object.getShape());
        checkValues(object, keys);
    }

    @Test
    public void duplicateKeysAndReplacementArrays() {
        Shape root = root(false);
        Object[] all = {"x", "y"};
        DynamicObject a = object(root, all);
        DynamicObject b = object(root, all);
        var node = createNode();
        Object[] keys = {"x", "y"};
        int[] flags = {1, 2};
        node.execute(a, keys, flags);
        keys = new Object[]{"y", "x"};
        flags = new int[]{3, 4};
        node.execute(b, keys, flags);
        assertEquals(4, b.getShape().getProperty("x").getFlags());
        assertEquals(3, b.getShape().getProperty("y").getFlags());
        DynamicObject c = object(root, all);
        node.execute(c, new Object[]{"x", "x"}, new int[]{9, 0});
        assertEquals(0, c.getShape().getProperty("x").getFlags());
        checkValues(c, all);
    }

    @Test
    public void repeatedNoopsAndChangesPreserveSequentialSemantics() {
        Object[] all = {"x", "y"};
        Object[] keys = {"missing", "missing", "x", "y", "x", "y", "x"};
        int[][] cases = {
                        {1, 2, 0, 0, 0, 4, 3},
                        {1, 2, 0, 0, 3, 4, 3},
                        {1, 2, 3, 0, 0, 4, 0},
                        {1, 2, 3, 0, 5, 0, 0},
                        {1, 2, 0, 0, 0, 0, 0}
        };
        for (int[] flags : cases) {
            Shape root = root(false);
            DynamicObject batch = object(root, all);
            DynamicObject scalar = object(root, all);
            Shape before = batch.getShape();
            Assumption x = before.getPropertyAssumption("x");
            Assumption missing = before.getPropertyAssumption("missing");
            assertTrue(x.isValid());
            createNode().execute(batch, keys, flags);
            boolean changedX = flags[2] != 0 || flags[4] != 0 || flags[6] != 0;
            assertEquals(!changedX, x.isValid());
            assertTrue(missing.isValid());
            sequential(scalar, keys, flags);
            assertSame(scalar.getShape(), batch.getShape());
            assertEquals(flags[6], batch.getShape().getProperty("x").getFlags());
            assertEquals(flags[5], batch.getShape().getProperty("y").getFlags());
            checkValues(batch, all);
        }
    }

    @Test
    public void duplicateKeysWithNoopsAndMasks() {
        Object[] all = {"x", "y"};
        Object[] keys = {"missing", "missing", "x", "x", "y"};
        int[][] flagCases = {{1, 2, 0, 3, 4}, {1, 2, 0, 0, 0}, {1, 2, 3, 0, 4}, {1, 2, 3, 5, 4}};
        for (int[] flags : flagCases) {
            Shape root = root(false);
            DynamicObject batch = object(root, all);
            DynamicObject scalar = object(root, all);
            createNode().execute(batch, keys, flags);
            sequential(scalar, keys, flags);
            assertSame(scalar.getShape(), batch.getShape());
            assertEquals(flags[3], batch.getShape().getProperty("x").getFlags());
            assertEquals(flags[4], batch.getShape().getProperty("y").getFlags());
            checkValues(batch, all);
        }
        DynamicObject object = object(root(false), all);
        createNode().execute(object, all, new int[]{3, 4});
        createNode().executeRemove(object, keys, 4);
        assertEquals(3, object.getShape().getProperty("x").getFlags());
        assertEquals(0, object.getShape().getProperty("y").getFlags());
        checkValues(object, all);
    }

    @Test
    public void inputValidationPrecedesChanges() {
        DynamicObject object = object(root(false), new Object[]{"x"});
        Shape before = object.getShape();
        assertThrows(IllegalArgumentException.class, () -> createNode().execute(object, new Object[]{"x"}, new int[]{1, 2}));
        assertSame(before, object.getShape());
    }

    @Test
    public void sharedAndConstantProperties() {
        for (boolean shared : new boolean[]{false, true}) {
            Shape root = Shape.newBuilder().layout(TestDynamicObjectMinimal.class, MethodHandles.lookup()).shared(shared).addConstantProperty("constant", 42, 0).build();
            DynamicObject object = object(root, new Object[]{"x", "y"});
            createNode().execute(object, new Object[]{"constant", "x", "y"}, 7);
            assertEquals(42, DynamicObject.GetNode.getUncached().execute(object, "constant", null));
            for (Object key : new Object[]{"constant", "x", "y"}) {
                assertEquals(7, object.getShape().getProperty(key).getFlags());
            }
        }
    }

    @Test
    public void assumptionsAndCycles() {
        Object[] keys = {"x", "y", "z"};
        DynamicObject object = object(root(false), keys);
        Shape before = object.getShape();
        Assumption x = before.getPropertyAssumption("x");
        Assumption y = before.getPropertyAssumption("y");
        Assumption missing = before.getPropertyAssumption("missing");
        assertTrue(x.isValid());
        assertTrue(y.isValid());
        var node = createNode();
        node.execute(object, new Object[]{"x", "y"}, 3);
        assertFalse(x.isValid());
        assertFalse(y.isValid());
        assertTrue(missing.isValid());
        node.execute(object, new Object[]{"x", "y"}, 0);
        assertSame(before, object.getShape());
        assertFalse(before.getPropertyAssumption("x").isValid());
        assertFalse(before.getPropertyAssumption("y").isValid());
        DynamicObject.ResetShapeNode.getUncached().execute(object, before.getRoot());
        assertFalse(missing.isValid());
    }

    @Test
    public void metadataAndDirectReplacementHistory() {
        Object[] keys = keys(12);
        Shape root = root(false);
        DynamicObject a = new TestDynamicObjectMinimal(root);
        DynamicObject b = new TestDynamicObjectMinimal(root);
        for (DynamicObject object : new DynamicObject[]{a, b}) {
            for (int i = 0; i < keys.length; i++) {
                DynamicObject.PutNode.getUncached().execute(object, keys[i], i);
                if (i == 4) {
                    DynamicObject.PutConstantNode.getUncached().execute(object, "constant", 42);
                    DynamicObject.SetPropertyFlagsNode.getUncached().execute(object, "constant", 1);
                }
            }
        }
        Object type = new Object();
        for (DynamicObject object : new DynamicObject[]{a, b}) {
            DynamicObject.SetDynamicTypeNode.getUncached().execute(object, type);
            DynamicObject.SetShapeFlagsNode.getUncached().execute(object, 13);
        }
        int[] flags = new int[keys.length];
        Arrays.fill(flags, 3);
        createNode().execute(a, keys, flags);
        sequential(b, keys, flags);
        assertSame(a.getShape(), b.getShape());
        assertSame(type, a.getShape().getDynamicType());
        assertEquals(13, a.getShape().getFlags());
        assertEquals(42, DynamicObject.GetNode.getUncached().execute(a, "constant", null));
        assertEquals(1, a.getShape().getProperty("constant").getFlags());
        for (int i = 0; i < keys.length; i++) {
            assertEquals(i, DynamicObject.GetNode.getUncached().execute(a, keys[i], null));
        }
    }

    @Test
    public void randomizedOverlappingBatches() {
        Object[] keys = keys(18);
        Shape root = root(false);
        DynamicObject a = object(root, keys);
        DynamicObject b = object(root, keys);
        var node = createNode();
        Random random = new Random(42);
        for (int iteration = 0; iteration < 80; iteration++) {
            List<Object> subset = new ArrayList<>();
            for (Object key : keys) {
                if (random.nextBoolean()) {
                    subset.add(key);
                }
            }
            java.util.Collections.shuffle(subset, random);
            Object[] changed = subset.toArray();
            int[] flags = new int[changed.length];
            for (int i = 0; i < flags.length; i++) {
                flags[i] = random.nextInt(8);
            }
            node.execute(a, changed, flags);
            sequential(b, changed, flags);
            assertSame(a.getShape(), b.getShape());
            checkValues(a, keys);
        }
    }

    @Test
    public void obsoleteSourceAndCachedTarget() {
        Object[] keys = {"x", "y", "z"};
        Shape root = root(false);
        DynamicObject first = object(root, keys);
        DynamicObject second = object(root, keys);
        var node = createNode();
        node.execute(first, keys, 3);
        Shape oldTarget = first.getShape();
        DynamicObject.PutNode.getUncached().execute(first, "x", "generalized");
        DynamicObject peer = object(root, keys);
        Shape oldSource = second.getShape();
        DynamicObject.PutNode.getUncached().execute(peer, "x", "obsolete source");
        assertFalse(oldSource.isValid());
        node.execute(second, keys, 3);
        assertEquals(0, DynamicObject.GetNode.getUncached().execute(second, "x", null));
        assertTrue(second.getShape().isValid());
        for (Object key : keys) {
            assertEquals(3, second.getShape().getProperty(key).getFlags());
        }
        assertNotSame(oldTarget, first.getShape());
    }

    @Test
    public void putAllWithFlagsUpdatesExistingProperties() {
        Object[] keys = keys(16);
        Shape root = root(false);
        DynamicObject a = object(root, keys);
        DynamicObject b = object(root, keys);
        var put = cached ? DynamicObject.PutAllNode.create() : DynamicObject.PutAllNode.getUncached();
        Object[] values = new Object[keys.length];
        int[] flags = new int[keys.length];
        Arrays.fill(values, 42);
        Arrays.fill(flags, 7);
        put.executeWithFlags(a, keys, values, flags);
        sequential(b, keys, flags);
        assertSame(a.getShape(), b.getShape());
        for (Object key : keys) {
            assertEquals(42, DynamicObject.GetNode.getUncached().execute(a, key, null));
        }
        Arrays.fill(flags, 1);
        put.executeWithFlagsIfPresent(a, keys, values, flags);
        for (Object key : keys) {
            assertEquals(1, a.getShape().getProperty(key).getFlags());
        }
        put.executeWithFlagsIfAbsent(a, keys, values, new int[keys.length]);
        for (Object key : keys) {
            assertEquals(1, a.getShape().getProperty(key).getFlags());
        }
        put.executeWithFlags(a, new Object[]{"new", "x", keys[0], keys[0]}, new Object[]{1, 2, "a", 2.5}, new int[]{3, 4, 5, 6});
        assertEquals(6, a.getShape().getProperty(keys[0]).getFlags());
        assertEquals(2.5, DynamicObject.GetNode.getUncached().execute(a, keys[0], null));
    }

    @Test(timeout = 30000)
    public void concurrentBatchPublication() throws Exception {
        Object[] keys = keys(24);
        Shape root = root(false);
        int count = 8;
        List<DynamicObject> objects = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            objects.add(object(root, keys));
        }
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(count);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (DynamicObject object : objects) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    go.await();
                    createNode().execute(object, keys, 3);
                    return null;
                }));
            }
            ready.await();
            go.countDown();
            for (Future<?> result : results) {
                result.get();
            }
            for (DynamicObject object : objects) {
                assertSame(objects.get(0).getShape(), object.getShape());
                checkValues(object, keys);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
