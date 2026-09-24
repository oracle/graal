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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.invoke.MethodHandles;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.object.Shape;

@RunWith(Parameterized.class)
public class PutAllTest {
    @Parameterized.Parameters(name = "cached={0}")
    public static Collection<Boolean> data() {
        return Arrays.asList(false, true);
    }

    @Parameterized.Parameter public boolean cached;

    private DynamicObject.PutAllNode createNode() {
        return cached ? DynamicObject.PutAllNode.create() : DynamicObject.PutAllNode.getUncached();
    }

    private static Shape root(boolean fields, boolean casts, boolean shared) {
        return Shape.newBuilder().layout(fields ? TestDynamicObjectDefault.class : TestDynamicObjectMinimal.class, MethodHandles.lookup()).allowImplicitCastIntToDouble(
                        casts).allowImplicitCastIntToLong(casts).propertyAssumptions(!shared).shared(shared).build();
    }

    private static Object[] keys(int count) {
        Object[] keys = new Object[count];
        for (int i = 0; i < count; i++) {
            keys[i] = ("key" + i).intern();
        }
        return keys;
    }

    private static Object[] integers(int count) {
        Object[] values = new Object[count];
        for (int i = 0; i < count; i++) {
            values[i] = i;
        }
        return values;
    }

    private static DynamicObject object(Shape root, Object[] keys, Object[] values) {
        DynamicObject object = root.getLayoutClass() == TestDynamicObjectDefault.class ? new TestDynamicObjectDefault(root) : new TestDynamicObjectMinimal(root);
        sequential(object, keys, values, null);
        return object;
    }

    private static void sequential(DynamicObject object, Object[] keys, Object[] values, int[] flags) {
        for (int i = 0; i < keys.length; i++) {
            if (flags == null) {
                DynamicObject.PutNode.getUncached().execute(object, keys[i], values[i]);
            } else {
                DynamicObject.PutNode.getUncached().executeWithFlags(object, keys[i], values[i], flags[i]);
            }
        }
    }

    private static void checkValues(DynamicObject object, Object[] keys, Object[] values) {
        for (int i = 0; i < keys.length; i++) {
            Object expected = values[i];
            Class<?> type = DOTestAsserts.getLocationType(object.getShape().getProperty(keys[i]).getLocation());
            if (expected instanceof Integer integer) {
                if (type == double.class) {
                    expected = integer.doubleValue();
                } else if (type == long.class) {
                    expected = integer.longValue();
                }
            }
            assertEquals(keys[i].toString(), expected, DynamicObject.GetNode.getUncached().execute(object, keys[i], null));
        }
    }

    @Test
    public void representationChangesReuseSequentialShape() {
        Object[] keys = keys(40);
        Object[] initial = integers(keys.length);
        for (boolean fields : new boolean[]{false, true}) {
            for (boolean casts : new boolean[]{false, true}) {
                for (boolean batchFirst : new boolean[]{false, true}) {
                    Shape root = root(fields, casts, false);
                    DynamicObject batch = object(root, keys, initial);
                    DynamicObject scalar = object(root, keys, initial);
                    DynamicObject sibling = object(root, keys, initial);
                    Object[] values = new Object[keys.length];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = switch (i % 4) {
                            case 0 -> "string" + i;
                            case 1 -> i + 0.5;
                            case 2 -> (long) i + Integer.MAX_VALUE;
                            default -> null;
                        };
                    }
                    if (!batchFirst) {
                        sequential(scalar, keys, values, null);
                    }
                    createNode().execute(batch, keys, values);
                    if (batchFirst) {
                        sequential(scalar, keys, values, null);
                    }
                    DynamicObject.UpdateShapeNode.getUncached().execute(batch);
                    assertSame(scalar.getShape(), batch.getShape());
                    assertEquals(Arrays.asList(keys), batch.getShape().getKeyList());
                    checkValues(batch, keys, values);
                    DynamicObject.UpdateShapeNode.getUncached().execute(sibling);
                    checkValues(sibling, keys, initial);
                }
            }
        }
    }

    @Test
    public void flagsDoNotLeakIntoGeneralizedSuccessors() {
        Object[] keys = keys(32);
        Object[] initial = integers(keys.length);
        for (boolean batchFirst : new boolean[]{false, true}) {
            Shape root = root(true, true, false);
            DynamicObject batch = object(root, keys, initial);
            DynamicObject scalar = object(root, keys, initial);
            DynamicObject sibling = object(root, keys, initial);
            Shape before = batch.getShape();
            var assumption = before.getPropertyAssumption(keys[0]);
            Object[] values = new Object[keys.length];
            int[] flags = new int[keys.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = "value" + i;
                flags[i] = i + 1;
            }
            if (!batchFirst) {
                sequential(scalar, keys, values, flags);
            }
            createNode().executeWithFlags(batch, keys, values, flags);
            if (batchFirst) {
                sequential(scalar, keys, values, flags);
            }
            assertFalse(before.isValid());
            assertFalse(assumption.isValid());
            DynamicObject.UpdateShapeNode.getUncached().execute(batch);
            assertSame(scalar.getShape(), batch.getShape());
            DynamicObject.UpdateShapeNode.getUncached().execute(sibling);
            for (int i = 0; i < keys.length; i++) {
                assertEquals(flags[i], batch.getShape().getProperty(keys[i]).getFlags());
                assertEquals(0, sibling.getShape().getProperty(keys[i]).getFlags());
            }
            checkValues(batch, keys, values);
            checkValues(sibling, keys, initial);
        }
    }

    @Test
    public void untouchedPropertiesMoveSafely() {
        Object[] all = keys(48);
        Object[] initial = integers(all.length);
        initial[3] = new Object();
        initial[9] = new Object();
        for (boolean fields : new boolean[]{false, true}) {
            Shape root = root(fields, false, false);
            DynamicObject object = object(root, all, initial);
            Shape before = object.getShape();
            Object[] updates = {all[40], all[0], all[7], all[13]};
            Object[] values = {true, "a", "b", 1.5};
            createNode().execute(object, updates, values);
            assertNotEquals(before.getProperty(all[3]).getLocation(), object.getShape().getProperty(all[3]).getLocation());
            Object[] expected = initial.clone();
            expected[40] = values[0];
            expected[0] = values[1];
            expected[7] = values[2];
            expected[13] = values[3];
            checkValues(object, all, expected);
        }
    }

    @Test
    public void cachedSourceAndTargetBecomeObsolete() {
        Object[] keys = keys(24);
        Object[] initial = integers(keys.length);
        Shape root = root(true, true, false);
        DynamicObject[] objects = new DynamicObject[12];
        for (int i = 0; i < objects.length; i++) {
            objects[i] = object(root, keys, initial);
        }
        var node = createNode();
        node.execute(objects[0], keys, initial);
        Object[] values = new Object[keys.length];
        Arrays.fill(values, 0.5);
        int[] flags = new int[keys.length];
        Arrays.fill(flags, 7);
        node.executeWithFlags(objects[1], keys, values, flags);
        Shape target = objects[1].getShape();
        DynamicObject.PutNode.getUncached().execute(objects[1], keys[0], "widen again");
        assertFalse(target.isValid());
        for (int i = 2; i < objects.length; i++) {
            node.executeWithFlags(objects[i], keys, values, flags);
            checkValues(objects[i], keys, values);
            assertTrue(objects[i].getShape().isValid());
        }
    }

    @Test
    public void modesAndFallbacks() {
        Object[] keys = {"a", "b", new HiddenKey("hidden")};
        Object[] initial = {1, 2, 3};
        Object[] values = {"a", 0.5, null};
        for (boolean shared : new boolean[]{false, true}) {
            Shape root = root(true, false, shared);
            DynamicObject object = object(root, keys, initial);
            createNode().executeIfAbsent(object, keys, values);
            checkValues(object, keys, initial);
            createNode().executeWithFlagsIfPresent(object, keys, values, new int[]{1, 2, 3});
            checkValues(object, keys, values);
            createNode().executeIfPresent(object, new Object[]{"missing", "a"}, new Object[]{42, "changed"});
            assertFalse(object.getShape().hasProperty("missing"));
            createNode().executeWithFlags(object, new Object[]{"new", "b", "new", "a"}, new Object[]{4, "b", "last", null}, new int[]{5, 6, 7, 8});
            checkValues(object, new Object[]{"new", "b", "a"}, new Object[]{"last", "b", null});
            assertEquals(7, object.getShape().getProperty("new").getFlags());
            createNode().execute(object, new Object[]{"b", new String("b"), "a"}, new Object[]{3, 2.5, "again"});
            checkValues(object, new Object[]{"b", "a"}, new Object[]{2.5, "again"});
        }
        Shape root = Shape.newBuilder().layout(TestDynamicObjectMinimal.class, MethodHandles.lookup()).addConstantProperty("a", 1, 0).build();
        DynamicObject object = object(root, new Object[]{"b"}, new Object[]{2});
        createNode().executeWithFlags(object, new Object[]{"a", "b"}, new Object[]{"a", "b"}, new int[]{2, 3});
        checkValues(object, new Object[]{"a", "b"}, new Object[]{"a", "b"});
    }

    @Test
    public void presenceSkipsPreserveModesAndDuplicates() {
        DynamicObject object = object(root(false, false, false), new Object[]{"x", "y"}, new Object[]{1, 2});
        createNode().executeWithFlagsIfAbsent(object, new Object[]{"new", "x", "new", "y"},
                        new Object[]{10, "skip x", 20, "skip y"}, new int[]{3, 7, 9, 7});
        checkValues(object, new Object[]{"x", "y", "new"}, new Object[]{1, 2, 10});
        assertEquals(0, object.getShape().getProperty("x").getFlags());
        assertEquals(0, object.getShape().getProperty("y").getFlags());
        assertEquals(3, object.getShape().getProperty("new").getFlags());

        createNode().executeWithFlagsIfPresent(object, new Object[]{"x", "missing", "y", "missing"},
                        new Object[]{2, "skip", 3, "skip again"}, new int[]{5, 7, 6, 9});
        checkValues(object, new Object[]{"x", "y", "new"}, new Object[]{2, 3, 10});
        assertEquals(5, object.getShape().getProperty("x").getFlags());
        assertEquals(6, object.getShape().getProperty("y").getFlags());
        assertFalse(object.getShape().hasProperty("missing"));
    }

    @Test
    public void putAllDuplicateKeyModes() {
        DynamicObject object = new TestDynamicObjectMinimal(root(false, false, false));
        var put = createNode();
        put.executeIfAbsent(object, new Object[]{"x", "x"}, new Object[]{1, 2});
        assertEquals(1, DynamicObject.GetNode.getUncached().execute(object, "x", null));
        put.executeWithFlagsIfAbsent(object, new Object[]{"y", "y"}, new Object[]{3, 4}, new int[]{5, 6});
        assertEquals(3, DynamicObject.GetNode.getUncached().execute(object, "y", null));
        assertEquals(5, object.getShape().getProperty("y").getFlags());
        put.executeIfPresent(object, new Object[]{"x", "missing", "x"}, new Object[]{7, 8, 9});
        assertEquals(9, DynamicObject.GetNode.getUncached().execute(object, "x", null));
        assertFalse(DynamicObject.ContainsKeyNode.getUncached().execute(object, "missing"));
        put.execute(object, new Object[]{"x", "x"}, new Object[]{10, 11});
        assertEquals(11, DynamicObject.GetNode.getUncached().execute(object, "x", null));
    }

    @Test
    public void ifAbsentWithAndWithoutFlagsAtSameCallSite() {
        Object[] keys = {"existing", "new", "new"};
        Object[] values = {10, 20, 30};
        int[] flags = {7, 8, 9};
        for (boolean flaggedFirst : new boolean[]{true, false}) {
            Shape root = root(false, false, false);
            var node = createNode();
            for (int call = 0; call < 4; call++) {
                DynamicObject object = new TestDynamicObjectMinimal(root);
                DynamicObject.PutNode.getUncached().executeWithFlags(object, "existing", 1, 5);
                boolean withFlags = (call % 2 == 0) == flaggedFirst;
                if (withFlags) {
                    node.executeWithFlagsIfAbsent(object, keys, values, flags);
                } else {
                    node.executeIfAbsent(object, keys, values);
                }
                assertEquals(1, DynamicObject.GetNode.getUncached().execute(object, "existing", null));
                assertEquals(5, object.getShape().getProperty("existing").getFlags());
                assertEquals(20, DynamicObject.GetNode.getUncached().execute(object, "new", null));
                assertEquals(withFlags ? 8 : 0, object.getShape().getProperty("new").getFlags());
            }
        }
    }

    @FunctionalInterface
    private interface PutAllOperation {
        void execute(DynamicObject.PutAllNode node, DynamicObject object, Object[] keys, Object[] values);
    }

    @Test
    public void cachedKeyLengthMismatchFallsBack() {
        Assume.assumeTrue(cached);
        PutAllOperation[] operations = {
                        DynamicObject.PutAllNode::execute,
                        (node, object, keys, values) -> node.executeWithFlags(object, keys, values, new int[keys.length]),
                        DynamicObject.PutAllNode::executeIfAbsent,
                        (node, object, keys, values) -> node.executeWithFlagsIfAbsent(object, keys, values, new int[keys.length]),
                        DynamicObject.PutAllNode::executeIfPresent,
                        (node, object, keys, values) -> node.executeWithFlagsIfPresent(object, keys, values, new int[keys.length]),
        };
        Shape root = root(false, false, false);
        Object[] initialKeys = {"a", "b"};
        Object[] initialValues = {0, 1};
        Object[] firstValues = {2, 3};
        Object[] shorterKeys = {"a"};
        Object[] shorterValues = {4};
        for (PutAllOperation operation : operations) {
            DynamicObject first = object(root, initialKeys, initialValues);
            DynamicObject actual = object(root, initialKeys, initialValues);
            DynamicObject expected = object(root, initialKeys, initialValues);
            var node = createNode();
            operation.execute(node, first, initialKeys, firstValues);
            operation.execute(node, actual, shorterKeys, shorterValues);
            operation.execute(DynamicObject.PutAllNode.getUncached(), expected, shorterKeys, shorterValues);
            assertSame(expected.getShape(), actual.getShape());
            for (Object key : initialKeys) {
                assertEquals(DynamicObject.GetNode.getUncached().execute(expected, key, null), DynamicObject.GetNode.getUncached().execute(actual, key, null));
                assertEquals(expected.getShape().getProperty(key).getFlags(), actual.getShape().getProperty(key).getFlags());
            }
        }
    }

    @Test
    public void callerKeyArrayMutation() {
        assertCallerKeyArrayMutation(DynamicObject.PutAllNode::execute);
        assertCallerKeyArrayMutation((node, object, keys, values) -> node.executeWithFlags(object, keys, values, new int[]{7}));
    }

    @Test
    public void callerKeyArrayMutationIfAbsent() {
        assertCallerKeyArrayMutation(DynamicObject.PutAllNode::executeIfAbsent);
        assertCallerKeyArrayMutation((node, object, keys, values) -> node.executeWithFlagsIfAbsent(object, keys, values, new int[]{7}));
    }

    @Test
    public void callerKeyArrayMutationIfPresent() {
        assertCallerKeyArrayMutation(DynamicObject.PutAllNode::executeIfPresent);
        assertCallerKeyArrayMutation((node, object, keys, values) -> node.executeWithFlagsIfPresent(object, keys, values, new int[]{7}));
    }

    private void assertCallerKeyArrayMutation(PutAllOperation operation) {
        Assume.assumeTrue(!cached || DynamicObject.class.desiredAssertionStatus());
        Shape root = root(false, false, false);
        var node = createNode();
        Object[] keys = {"a"};
        Object[] initialKeys = {"a"};
        Object[] initialValues = {0};
        Object[] values = {42};
        for (String key : new String[]{"a", "b", "a"}) {
            DynamicObject actual = object(root, initialKeys, initialValues);
            DynamicObject expected = object(root, initialKeys, initialValues);
            assertSame(expected.getShape(), actual.getShape());
            keys[0] = key;
            if (cached && key.equals("b")) {
                AssertionError error = assertThrows(AssertionError.class, () -> operation.execute(node, actual, keys, values));
                assertTrue(error.getMessage(), error.getMessage().contains("Cached keys array"));
                assertSame(expected.getShape(), actual.getShape());
                assertEquals(0, DynamicObject.GetNode.getUncached().execute(actual, "a", null));
                return;
            }
            operation.execute(node, actual, keys, values);
            operation.execute(DynamicObject.PutAllNode.getUncached(), expected, keys, values);
            assertSame(expected.getShape(), actual.getShape());
            for (String propertyKey : new String[]{"a", "b"}) {
                assertEquals(DynamicObject.GetNode.getUncached().execute(expected, propertyKey, null),
                                DynamicObject.GetNode.getUncached().execute(actual, propertyKey, null));
            }
        }
    }

    @Test
    public void callerKeyArrayMutationIsDetectedBeforeWrites() {
        Assume.assumeTrue(!cached || DynamicObject.class.desiredAssertionStatus());
        Shape root = root(false, false, false);
        Object[] keys = {"a", "b"};
        DynamicObject first = object(root, keys, new Object[]{0, 0});
        DynamicObject second = object(root, keys, new Object[]{0, 0});
        Shape startShape = second.getShape();
        Object[] values = {42, 43};
        var node = createNode();
        node.execute(first, keys, values);
        keys[1] = "c";
        if (cached) {
            AssertionError error = assertThrows(AssertionError.class, () -> node.execute(second, keys, values));
            assertTrue(error.getMessage(), error.getMessage().contains("Cached keys array"));
            assertSame(startShape, second.getShape());
            checkValues(second, new Object[]{"a", "b"}, new Object[]{0, 0});
            assertFalse(DynamicObject.ContainsKeyNode.getUncached().execute(second, "c"));
        } else {
            node.execute(second, keys, values);
            checkValues(second, new Object[]{"a", "b", "c"}, new Object[]{42, 0, 43});
        }
    }

    @Test
    public void duplicateIfAbsentPlanChecksEarlierKeys() {
        Assume.assumeTrue(!cached || DynamicObject.class.desiredAssertionStatus());
        Shape root = root(false, false, false);
        DynamicObject first = new TestDynamicObjectMinimal(root);
        DynamicObject second = new TestDynamicObjectMinimal(root);
        Object[] keys = {"a", "a", "b"};
        Object[] values = {1, 2, 3};
        var node = createNode();
        node.executeIfAbsent(first, keys, values);
        checkValues(first, new Object[]{"a", "b"}, new Object[]{1, 3});
        keys[1] = "b";
        if (cached) {
            AssertionError error = assertThrows(AssertionError.class, () -> node.executeIfAbsent(second, keys, values));
            assertTrue(error.getMessage(), error.getMessage().contains("Cached keys array"));
            assertSame(root, second.getShape());
            assertFalse(DynamicObject.ContainsKeyNode.getUncached().execute(second, "a"));
            assertFalse(DynamicObject.ContainsKeyNode.getUncached().execute(second, "b"));
        } else {
            node.executeIfAbsent(second, keys, values);
            checkValues(second, new Object[]{"a", "b"}, new Object[]{1, 2});
        }
    }

    @Test
    public void valuesAndFlagsArraysCanChangeBetweenCalls() {
        Shape root = root(false, false, false);
        Object[] keys = {"a"};
        DynamicObject first = object(root, keys, new Object[]{0});
        DynamicObject second = object(root, keys, new Object[]{0});
        Object[] values = {1};
        int[] flags = {3};
        var node = createNode();
        node.executeWithFlags(first, keys, values, flags);
        values[0] = 2;
        flags[0] = 7;
        node.executeWithFlags(second, keys, values, flags);
        assertEquals(2, DynamicObject.GetNode.getUncached().execute(second, "a", null));
        assertEquals(7, second.getShape().getProperty("a").getFlags());
    }

    @Test
    public void dynamicTypeAndShapeFlagsSurviveReplay() {
        Shape root = root(true, false, false);
        Object[] keys = keys(16);
        DynamicObject object = object(root, Arrays.copyOf(keys, 8), integers(8));
        Object type = new Object();
        DynamicObject.SetDynamicTypeNode.getUncached().execute(object, type);
        DynamicObject.SetShapeFlagsNode.getUncached().execute(object, 13);
        sequential(object, Arrays.copyOfRange(keys, 8, keys.length), integers(8), null);
        Object[] values = new Object[keys.length];
        Arrays.fill(values, "value");
        createNode().execute(object, keys, values);
        assertSame(type, object.getShape().getDynamicType());
        assertEquals(13, object.getShape().getFlags());
        checkValues(object, keys, values);
    }

    @Test
    public void randomizedOverlappingUpdates() {
        Random random = new Random(894723);
        Object[] all = keys(24);
        Object[] initial = integers(all.length);
        for (int trial = 0; trial < 20; trial++) {
            Shape root = root(trial % 2 == 0, trial % 3 == 0, false);
            DynamicObject batch = object(root, all, initial);
            DynamicObject scalar = object(root, all, initial);
            var node = createNode();
            for (int round = 0; round < 8; round++) {
                List<Object> shuffled = new ArrayList<>(Arrays.asList(all));
                Collections.shuffle(shuffled, random);
                Object[] keys = shuffled.subList(0, 8).toArray();
                Object[] values = new Object[keys.length];
                int[] flags = new int[keys.length];
                for (int i = 0; i < keys.length; i++) {
                    values[i] = switch (random.nextInt(5)) {
                        case 0 -> random.nextInt();
                        case 1 -> random.nextDouble();
                        case 2 -> random.nextLong();
                        case 3 -> null;
                        default -> "value" + i;
                    };
                    flags[i] = random.nextInt(8);
                }
                node.executeWithFlags(batch, keys, values, flags);
                sequential(scalar, keys, values, flags);
                DynamicObject.UpdateShapeNode.getUncached().execute(batch);
                assertSame(scalar.getShape(), batch.getShape());
                for (Object key : all) {
                    assertEquals(DynamicObject.GetNode.getUncached().execute(scalar, key, null), DynamicObject.GetNode.getUncached().execute(batch, key, null));
                }
            }
        }
    }

    @Test
    public void descendantMissedByAncestorInvalidationStillMigrates() throws ReflectiveOperationException {
        Object[] keys = {"a", "b", "c", "tail"};
        Object tail = new Object();
        Object[] initial = {1, 2, 3, tail};
        Shape root = root(false, false, false);
        DynamicObject object = object(root, keys, initial);
        DynamicObject sibling = object(root, keys, initial);
        Shape canonical = object.getShape();

        // Model a child created concurrently with ancestor invalidation, not yet in the
        // transition cache traversed by markObsolete. Storage matches the canonical child.
        var getParent = Shape.class.getDeclaredMethod("getParent");
        var getTransition = Shape.class.getDeclaredMethod("getTransitionFromParent");
        getParent.setAccessible(true);
        getTransition.setAccessible(true);
        Object transition = getTransition.invoke(canonical);
        var makeShape = Shape.class.getDeclaredMethod("makeShapeWithAddedProperty", Shape.class, transition.getClass());
        makeShape.setAccessible(true);
        Shape detached = (Shape) makeShape.invoke(null, getParent.invoke(canonical), transition);
        var shapeField = DynamicObject.class.getDeclaredField("shape");
        shapeField.setAccessible(true);
        shapeField.set(object, detached);

        DynamicObject.PutNode.getUncached().execute(sibling, "a", "generalize ancestor");
        assertFalse(canonical.isValid());
        assertTrue(detached.isValid());
        createNode().execute(object, new Object[]{"b", "c"}, new Object[]{"b", "c"});
        checkValues(object, keys, new Object[]{1, "b", "c", tail});
        assertFalse(detached.isValid());
    }

    @Test
    public void preparationDoesNotRetainValues() {
        Object[] keys = keys(24);
        Shape root = root(true, false, false);
        DynamicObject object = object(root, keys, integers(keys.length));
        var node = createNode();
        List<WeakReference<Object>> refs = replaceReferenceValues(node, object, keys);
        System.gc();
        for (var ref : refs) {
            assertNull("Bulk preparation must not retain written values", ref.get());
        }
        Reference.reachabilityFence(object);
        Reference.reachabilityFence(node);
        Reference.reachabilityFence(root);
    }

    private static List<WeakReference<Object>> replaceReferenceValues(DynamicObject.PutAllNode node, DynamicObject object, Object[] keys) {
        Object[] values = new Object[keys.length];
        List<WeakReference<Object>> refs = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            values[i] = new Object();
            refs.add(new WeakReference<>(values[i]));
        }
        node.execute(object, keys, values);
        Object[] replacements = new Object[keys.length];
        Arrays.fill(replacements, "replacement");
        node.execute(object, keys, replacements);
        return refs;
    }

    @Test
    public void concurrentGeneralizationOnSeparateObjects() throws Exception {
        Shape root = root(true, true, false);
        Object[] keys = keys(48);
        Object[] initial = integers(keys.length);
        DynamicObject[] objects = new DynamicObject[8];
        for (int i = 0; i < objects.length; i++) {
            objects[i] = object(root, keys, initial);
        }
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(objects.length)) {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < objects.length; t++) {
                int thread = t;
                var node = createNode();
                futures.add(executor.submit(() -> {
                    start.await();
                    Object[] values = new Object[keys.length];
                    int[] flags = new int[keys.length];
                    Arrays.fill(flags, thread + 1);
                    for (int i = 0; i < values.length; i++) {
                        values[i] = thread % 2 == 0 ? "value" + i : i + 0.5;
                    }
                    node.executeWithFlags(objects[thread], keys, values, flags);
                    checkValues(objects[thread], keys, values);
                    for (Object key : keys) {
                        assertEquals(thread + 1, objects[thread].getShape().getProperty(key).getFlags());
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }
    }
}
