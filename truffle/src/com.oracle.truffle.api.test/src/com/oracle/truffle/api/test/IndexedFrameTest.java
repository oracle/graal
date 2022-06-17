/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.function.Consumer;

import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

public class IndexedFrameTest {

    private static final Object DEFAULT = new Object();

    private static void testWithFrames(FrameDescriptor descriptor, Consumer<Frame> test) {
        Object[] args = new Object[]{};
        test.accept(Truffle.getRuntime().createVirtualFrame(args, descriptor));
        test.accept(Truffle.getRuntime().createMaterializedFrame(args, descriptor));

        RootNode rootNode = new RootNode(null, descriptor) {

            @Override
            public Object execute(VirtualFrame frame) {
                return null;
            }
        };
        rootNode.getCallTarget().call();
    }

    private static void assertIsType(Frame frame, int slot, FrameSlotKind k) {
        assertEquals(k == FrameSlotKind.Boolean, frame.isBoolean(slot));
        assertEquals(k == FrameSlotKind.Byte, frame.isByte(slot));
        assertEquals(k == FrameSlotKind.Int, frame.isInt(slot));
        assertEquals(k == FrameSlotKind.Long, frame.isLong(slot));
        assertEquals(k == FrameSlotKind.Double, frame.isDouble(slot));
        assertEquals(k == FrameSlotKind.Float, frame.isFloat(slot));
        assertEquals(k == FrameSlotKind.Object, frame.isObject(slot));
        assertEquals(k == FrameSlotKind.Static, frame.isStatic(slot));
    }

    @Test
    public void testSlotIds() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.defaultValue(DEFAULT);
        int slot0 = builder.addSlot(FrameSlotKind.Object, "name1", "info");
        int slotRegion = builder.addSlots(5, FrameSlotKind.Illegal);
        int slot1 = builder.addSlot(FrameSlotKind.Int, "name2", "info");
        int slot2 = builder.addSlot(FrameSlotKind.Long, "nameEquals", null);
        int slot3 = builder.addSlot(FrameSlotKind.Double, "nameEquals", null);

        // slot indexes are ordered as expected
        assertEquals(0, slot0);
        assertEquals(1, slotRegion);
        assertEquals(6, slot1);
        assertEquals(7, slot2);
        assertEquals(8, slot3);

        FrameDescriptor descriptor = builder.build();

        // initial sizes in the frame descriptor
        assertEquals(9, descriptor.getNumberOfSlots());
        assertEquals(0, descriptor.getNumberOfAuxiliarySlots());
    }

    @Test
    public void testInitialSlotValue() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.defaultValue(DEFAULT);
        builder.addSlot(FrameSlotKind.Object, "name1", "info");
        builder.addSlots(5, FrameSlotKind.Illegal);
        builder.addSlot(FrameSlotKind.Int, "name2", "info");
        builder.addSlot(FrameSlotKind.Long, "nameEquals", null);
        builder.addSlot(FrameSlotKind.Double, "nameEquals", null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            // slots start out as "object" with default value
            for (int slot = 0; slot < descriptor.getNumberOfSlots(); slot++) {
                assertIsType(frame, slot, FrameSlotKind.Object);
                assertSame(DEFAULT, frame.getObject(slot));
            }
        });
    }

    @Test
    public void testInitialStaticSlotValue() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.defaultValue(DEFAULT);
        builder.addSlot(FrameSlotKind.Static, "name1", "info");

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            // slots start out as "static" with default value
            for (int slot = 0; slot < descriptor.getNumberOfSlots(); slot++) {
                assertIsType(frame, slot, FrameSlotKind.Static);
                assertSame(DEFAULT, frame.getObjectStatic(slot));
            }
        });
    }

    @Test
    public void testAuxiliarySlots() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.defaultValue(DEFAULT);
        int slotsId = builder.addSlots(100, FrameSlotKind.Long);
        assertEquals(0, slotsId);
        FrameDescriptor descriptor = builder.build();

        assertEquals(0, descriptor.getNumberOfAuxiliarySlots());
        assertTrue(descriptor.getAuxiliarySlots().isEmpty());

        int slot0 = descriptor.findOrAddAuxiliarySlot("foo");
        int slot1 = descriptor.findOrAddAuxiliarySlot(IndexedFrameTest.class);
        int slot2 = descriptor.findOrAddAuxiliarySlot(DEFAULT);
        // ensure that "equals" is used:
        int slot3 = descriptor.findOrAddAuxiliarySlot(new String("foo"));

        assertEquals(3, descriptor.getNumberOfAuxiliarySlots());
        assertEquals(3, descriptor.getAuxiliarySlots().size());
        assertEquals(slot3, slot0);
        assertNotEquals(slot0, slot1);
        assertNotEquals(slot0, slot2);
        assertNotEquals(slot1, slot3);

        int[] slots = new int[]{slot0, slot1, slot2, slot3};
        testWithFrames(descriptor, frame -> {
            // slots start out with null values
            for (int slot : slots) {
                assertNull(frame.getAuxiliarySlot(slot));
            }
            for (int slot : slots) {
                frame.setAuxiliarySlot(slot, DEFAULT);
            }
            for (int slot : slots) {
                assertSame(DEFAULT, frame.getAuxiliarySlot(slot));
            }
        });

        descriptor.disableAuxiliarySlot("foo");
        assertEquals(2, descriptor.getAuxiliarySlots().size());
        // there's no guarantee that disabling a slot will reduce the slot count
        assertTrue(descriptor.getNumberOfAuxiliarySlots() >= 2 && descriptor.getNumberOfAuxiliarySlots() <= 3);

        descriptor.disableAuxiliarySlot(IndexedFrameTest.class);
        assertEquals(1, descriptor.getAuxiliarySlots().size());
        // there's no guarantee that disabling a slot will reduce the slot count
        assertTrue(descriptor.getNumberOfAuxiliarySlots() >= 1 && descriptor.getNumberOfAuxiliarySlots() <= 3);

        descriptor.disableAuxiliarySlot(DEFAULT);
        assertEquals(0, descriptor.getAuxiliarySlots().size());
        // all auxiliary slots disabled: storage should shrink
        assertEquals(0, descriptor.getNumberOfAuxiliarySlots());
    }

    @Test
    public void testTypeSwitching() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        int slot0 = builder.addSlot(FrameSlotKind.Object, "name1", "info");
        int slotRegion = builder.addSlots(5, FrameSlotKind.Illegal);
        int slot1 = builder.addSlot(FrameSlotKind.Int, "name2", "info");
        int slot2 = builder.addSlot(FrameSlotKind.Long, "nameEquals", null);
        int slot3 = builder.addSlot(FrameSlotKind.Double, "nameEquals", null);
        assertEquals(0, slot0);
        assertEquals(1, slotRegion);
        assertEquals(6, slot1);
        assertEquals(7, slot2);
        assertEquals(8, slot3);

        FrameDescriptor descriptor = builder.build();
        assertEquals(9, descriptor.getNumberOfSlots());
        assertEquals(0, descriptor.getNumberOfAuxiliarySlots());

        VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(new Object[]{}, descriptor);
        for (int slot = 0; slot < descriptor.getNumberOfSlots(); slot++) {
            assertTrue(frame.isObject(slot));
            assertNull(frame.getObject(slot));
            frame.setBoolean(slot, false);
            assertIsType(frame, slot, FrameSlotKind.Boolean);
            assertEquals(false, frame.getBoolean(slot));
            frame.setByte(slot, (byte) 1);
            assertIsType(frame, slot, FrameSlotKind.Byte);
            assertEquals((byte) 1, frame.getByte(slot));
            frame.setInt(slot, 2);
            assertIsType(frame, slot, FrameSlotKind.Int);
            assertEquals(2, frame.getInt(slot));
            frame.setLong(slot, 3L);
            assertIsType(frame, slot, FrameSlotKind.Long);
            assertEquals(3L, frame.getLong(slot));
            frame.setDouble(slot, 4d);
            assertIsType(frame, slot, FrameSlotKind.Double);
            assertEquals(4d, frame.getDouble(slot), 0d);
            frame.setLong(slot, 5L);
            assertIsType(frame, slot, FrameSlotKind.Long);
            assertEquals(5L, frame.getLong(slot));
            frame.setFloat(slot, 6f);
            assertIsType(frame, slot, FrameSlotKind.Float);
            assertEquals(6f, frame.getFloat(slot), 0f);
            frame.setObject(slot, "foo");
            assertIsType(frame, slot, FrameSlotKind.Object);
            assertEquals("foo", frame.getObject(slot));
            frame.clear(slot);
            assertIsType(frame, slot, FrameSlotKind.Illegal);
        }
    }

    // region static modes

    private static final String ASSERTION_ERROR_EXPECTED = "Should have thrown assertion error";
    private static final String SWITCH_STATIC_NON_STATIC = "Cannot switch between static and non-static slot kind";
    private static final String NON_STATIC_READ_OF_STATIC_SLOT = "Cannot read static frame slot with non-static API";
    private static final String STATIC_READ_OF_NON_STATIC_SLOT = "Cannot read non-static frame slot with static API";
    private static final String STATIC_WRITE_OF_NON_STATIC_SLOT = "Cannot write non-static frame slot with static API";
    private static final String NON_STATIC_WRITE_OF_STATIC_SLOT = "Cannot write static frame slot with non-static API";
    private static final String STATIC_COPY_OF_NON_STATIC_SLOT = "Cannot copy to or from a non-static frame slot with static copy API";
    private static final String STATIC_CLEAR_OF_NON_STATIC_SLOT = "Cannot clear non-static frame slot with static clear API";
    private static final String OUT_OF_BOUNDS_EXPECTED = "Expected out of bounds exception";

    @Test
    public void testNoStaticFrame() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Object, "name1", "info");
        builder.addSlots(5, FrameSlotKind.Illegal);
        builder.addSlot(FrameSlotKind.Int, "name2", "info");
        builder.addSlot(FrameSlotKind.Long, "nameEquals", null);
        builder.addSlot(FrameSlotKind.Double, "nameEquals", null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            for (int i = 0; i < descriptor.getNumberOfSlots(); i++) {
                assertIsType(frame, i, FrameSlotKind.Object);
            }
        });
    }

    @Test
    public void testAllStaticFrame() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Static, "name1", "info");
        builder.addSlots(5, FrameSlotKind.Static);
        builder.addSlot(FrameSlotKind.Static, "name2", "info");
        builder.addSlot(FrameSlotKind.Static, "nameEquals", null);
        builder.addSlot(FrameSlotKind.Static, "nameEquals", null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            for (int i = 0; i < descriptor.getNumberOfSlots(); i++) {
                assertIsType(frame, i, FrameSlotKind.Static);
            }
        });
    }

    @Test
    public void testMixedStaticFrame() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Object, "name1", "info");
        builder.addSlots(5, FrameSlotKind.Illegal);
        builder.addSlot(FrameSlotKind.Int, "name2", "info");
        builder.addSlot(FrameSlotKind.Static, "nameEquals", null);
        builder.addSlot(FrameSlotKind.Double, "nameEquals", null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            for (int i = 0; i < 7; i++) {
                assertIsType(frame, i, FrameSlotKind.Object);
            }
            assertIsType(frame, 7, FrameSlotKind.Static);
            assertIsType(frame, 8, FrameSlotKind.Object);
        });
    }

    // endregion

    // region FrameDescriptor slot switching

    @Test
    public void testSwitchFrameSlotKindFromNonStaticToStatic() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Object, null, null);

        FrameDescriptor descriptor = builder.build();

        try {
            descriptor.setSlotKind(0, FrameSlotKind.Static);
            fail(ASSERTION_ERROR_EXPECTED);
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains(SWITCH_STATIC_NON_STATIC));
        }
    }

    @Test
    public void testSwitchFrameSlotKindFromStaticToNonStatic() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Static, null, null);

        FrameDescriptor descriptor = builder.build();

        try {
            descriptor.setSlotKind(0, FrameSlotKind.Object);
            fail(ASSERTION_ERROR_EXPECTED);
        } catch (AssertionError e) {
            assertTrue(e.getMessage().contains(SWITCH_STATIC_NON_STATIC));
        }
    }

    // endregion

    // region illegal frame access

    @Test
    public void testReadNonStaticPrimitivesWithStaticAPI() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Byte, null, null);
        builder.addSlot(FrameSlotKind.Boolean, null, null);
        builder.addSlot(FrameSlotKind.Int, null, null);
        builder.addSlot(FrameSlotKind.Long, null, null);
        builder.addSlot(FrameSlotKind.Float, null, null);
        builder.addSlot(FrameSlotKind.Double, null, null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            int i = 0;
            frame.setByte(i, (byte) 42);
            try {
                frame.getByteStatic(i);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_READ_OF_NON_STATIC_SLOT));
            }
            i++;
            frame.setBoolean(i, true);
            try {
                frame.getBooleanStatic(i);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_READ_OF_NON_STATIC_SLOT));
            }
            i++;
            frame.setInt(i, 42);
            try {
                frame.getIntStatic(i);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_READ_OF_NON_STATIC_SLOT));
            }
            i++;
            frame.setLong(i, 42L);
            try {
                frame.getLongStatic(i);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_READ_OF_NON_STATIC_SLOT));
            }
            i++;
            frame.setFloat(i, 1.5f);
            try {
                frame.getFloatStatic(i);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_READ_OF_NON_STATIC_SLOT));
            }
            i++;
            frame.setDouble(i, 1.5);
            try {
                frame.getDoubleStatic(i);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_READ_OF_NON_STATIC_SLOT));
            }
        });
    }

    @Test
    public void testReadNonStaticObjectWithStaticAPI() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Object, null, null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            frame.setObject(0, new Object());
            try {
                frame.getObjectStatic(0);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_READ_OF_NON_STATIC_SLOT));
            }
        });
    }

    @Test
    public void testReadStaticPrimitivesWithNonStaticAPI() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Static, null, null); // Byte
        builder.addSlot(FrameSlotKind.Static, null, null); // Boolean
        builder.addSlot(FrameSlotKind.Static, null, null); // Int
        builder.addSlot(FrameSlotKind.Static, null, null); // Long
        builder.addSlot(FrameSlotKind.Static, null, null); // Float
        builder.addSlot(FrameSlotKind.Static, null, null); // Double

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            int i = 0;
            frame.setByteStatic(i, (byte) 42);
            try {
                frame.getByte(i);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_READ_OF_STATIC_SLOT));
            }
            i++;
            frame.setBooleanStatic(i, true);
            try {
                frame.getBoolean(i);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_READ_OF_STATIC_SLOT));
            }
            i++;
            frame.setIntStatic(i, 42);
            try {
                frame.getInt(i);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_READ_OF_STATIC_SLOT));
            }
            i++;
            frame.setLongStatic(i, 42L);
            try {
                frame.getLong(i);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_READ_OF_STATIC_SLOT));
            }
            i++;
            frame.setFloatStatic(i, 1.5f);
            try {
                frame.getFloat(i);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_READ_OF_STATIC_SLOT));
            }
            i++;
            frame.setDoubleStatic(i, 1.5);
            try {
                frame.getDouble(i);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_READ_OF_STATIC_SLOT));
            }
        });
    }

    @Test
    public void testReadStaticObjectWithNonStaticAPI() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Static, null, null); // Object

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            frame.setObjectStatic(0, new Object());
            try {
                frame.getObject(0);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_READ_OF_STATIC_SLOT));
            }
        });
    }

    @Test
    public void testWriteNonStaticPrimitivesWithStaticAPI() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Byte, null, null);
        builder.addSlot(FrameSlotKind.Boolean, null, null);
        builder.addSlot(FrameSlotKind.Int, null, null);
        builder.addSlot(FrameSlotKind.Long, null, null);
        builder.addSlot(FrameSlotKind.Float, null, null);
        builder.addSlot(FrameSlotKind.Double, null, null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            int i = 0;
            frame.setByte(i, (byte) 42);
            try {
                frame.setByteStatic(i, (byte) 43);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_WRITE_OF_NON_STATIC_SLOT));
            }
            i++;
            frame.setBoolean(i, true);
            try {
                frame.setBooleanStatic(i, false);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_WRITE_OF_NON_STATIC_SLOT));
            }
            i++;
            frame.setInt(i, 42);
            try {
                frame.setIntStatic(i, 43);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_WRITE_OF_NON_STATIC_SLOT));
            }
            i++;
            frame.setLong(i, 42L);
            try {
                frame.setLongStatic(i, 43L);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_WRITE_OF_NON_STATIC_SLOT));
            }
            i++;
            frame.setFloat(i, 1.5f);
            try {
                frame.setFloatStatic(i, 1.6f);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_WRITE_OF_NON_STATIC_SLOT));
            }
            i++;
            frame.setDouble(i, 1.5);
            try {
                frame.setDoubleStatic(i, 1.6);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_WRITE_OF_NON_STATIC_SLOT));
            }
        });
    }

    @Test
    public void testWriteNonStaticObjectWithStaticAPI() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Object, null, null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            frame.setObject(0, new Object());
            try {
                frame.setObjectStatic(0, new Object());
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_WRITE_OF_NON_STATIC_SLOT));
            }
        });
    }

    @Test
    public void testWriteStaticPrimitivesWithNonStaticAPI() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Static, null, null); // Byte
        builder.addSlot(FrameSlotKind.Static, null, null); // Boolean
        builder.addSlot(FrameSlotKind.Static, null, null); // Int
        builder.addSlot(FrameSlotKind.Static, null, null); // Long
        builder.addSlot(FrameSlotKind.Static, null, null); // Float
        builder.addSlot(FrameSlotKind.Static, null, null); // Double

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            int i = 0;
            frame.setByteStatic(i, (byte) 42);
            try {
                frame.setByte(i, (byte) 43);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_WRITE_OF_STATIC_SLOT));
            }
            i++;
            frame.setBooleanStatic(i, true);
            try {
                frame.setBoolean(i, false);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_WRITE_OF_STATIC_SLOT));
            }
            i++;
            frame.setIntStatic(i, 42);
            try {
                frame.setInt(i, 43);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_WRITE_OF_STATIC_SLOT));
            }
            i++;
            frame.setLongStatic(i, 42L);
            try {
                frame.setLong(i, 43L);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_WRITE_OF_STATIC_SLOT));
            }
            i++;
            frame.setFloatStatic(i, 1.5f);
            try {
                frame.setFloat(i, 1.6f);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_WRITE_OF_STATIC_SLOT));
            }
            i++;
            frame.setDoubleStatic(i, 1.5);
            try {
                frame.setDouble(i, 1.6);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_WRITE_OF_STATIC_SLOT));
            }
        });
    }

    @Test
    public void testWriteStaticObjectWithNonStaticAPI() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Static, null, null); // Object

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            frame.setObjectStatic(0, new Object());
            try {
                frame.setObject(0, new Object());
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_WRITE_OF_STATIC_SLOT));
            }
        });
    }

    @Test
    public void testCopyNonStaticSlotWithStaticAPI() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Int, null, null);
        builder.addSlot(FrameSlotKind.Int, null, null);
        builder.addSlot(FrameSlotKind.Object, null, null);
        builder.addSlot(FrameSlotKind.Object, null, null);
        builder.addSlot(FrameSlotKind.Static, null, null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            frame.setInt(0, 42);
            frame.setObject(2, new Object());
            frame.setIntStatic(4, 0);
            try {
                frame.copyPrimitiveStatic(0, 1);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_COPY_OF_NON_STATIC_SLOT));
            }
            try {
                frame.copyObjectStatic(2, 3);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_COPY_OF_NON_STATIC_SLOT));
            }
            try {
                frame.copyPrimitiveStatic(0, 4);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_COPY_OF_NON_STATIC_SLOT));
            }
            try {
                frame.copyPrimitiveStatic(4, 0);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_COPY_OF_NON_STATIC_SLOT));
            }
            try {
                frame.copyObjectStatic(2, 4);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_COPY_OF_NON_STATIC_SLOT));
            }
            try {
                frame.copyObjectStatic(4, 2);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_COPY_OF_NON_STATIC_SLOT));
            }
        });
    }

    @Test
    public void testCopyStaticSlotWithNonStaticAPI() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Int, null, null);
        builder.addSlot(FrameSlotKind.Object, null, null);
        builder.addSlot(FrameSlotKind.Static, null, null);
        builder.addSlot(FrameSlotKind.Static, null, null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            frame.setInt(0, 42);
            frame.setObject(1, new Object());
            frame.setIntStatic(2, 0);
            frame.setObjectStatic(3, new Object());
            try {
                frame.copy(0, 2);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_WRITE_OF_STATIC_SLOT));
            }
            try {
                frame.copy(1, 2);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_WRITE_OF_STATIC_SLOT));
            }
            try {
                frame.copy(2, 3);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_READ_OF_STATIC_SLOT));
            }
            try {
                frame.copy(2, 0);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_READ_OF_STATIC_SLOT));
            }
            try {
                frame.copy(2, 1);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_READ_OF_STATIC_SLOT));
            }
        });
    }

    @Test
    public void testClearNonStaticSlotWithStaticAPI() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Int, null, null);
        builder.addSlot(FrameSlotKind.Object, null, null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            frame.setInt(0, 42);
            frame.setObject(1, new Object());
            try {
                frame.clearPrimitiveStatic(0);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_CLEAR_OF_NON_STATIC_SLOT));
            }
            try {
                frame.clearObjectStatic(1);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(STATIC_CLEAR_OF_NON_STATIC_SLOT));
            }
        });
    }

    @Test
    public void testClearStaticSlotWithNonStaticAPI() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Static, null, null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            frame.setIntStatic(0, 42);
            try {
                frame.clear(0);
                fail(ASSERTION_ERROR_EXPECTED);
            } catch (AssertionError e) {
                assertTrue(e.getMessage().contains(NON_STATIC_WRITE_OF_STATIC_SLOT));
            }
        });
    }

    @Test
    public void testOutOfBoundsOnStaticAPI() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Static, null, null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            try {
                frame.setIntStatic(-1, 42);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index -1"));
            }
            try {
                frame.setIntStatic(1, 42);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index 1"));
            }
            try {
                frame.getIntStatic(-1);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index -1"));
            }
            try {
                frame.getIntStatic(1);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index 1"));
            }
            try {
                frame.copyPrimitiveStatic(0, 1);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index 1"));
            }
            try {
                frame.copyPrimitiveStatic(1, 0);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index 1"));
            }
            try {
                frame.copyPrimitiveStatic(0, -1);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index -1"));
            }
            try {
                frame.copyPrimitiveStatic(-1, 0);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index -1"));
            }
            try {
                frame.copyObjectStatic(0, 1);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index 1"));
            }
            try {
                frame.copyObjectStatic(1, 0);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index 1"));
            }
            try {
                frame.copyObjectStatic(0, -1);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index -1"));
            }
            try {
                frame.copyObjectStatic(-1, 0);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains(("Index -1")));
            }
            try {
                frame.clearPrimitiveStatic(-1);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index -1"));
            }
            try {
                frame.clearPrimitiveStatic(1);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index 1"));
            }
            try {
                frame.clearObjectStatic(-1);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index -1"));
            }
            try {
                frame.clearObjectStatic(1);
                fail(OUT_OF_BOUNDS_EXPECTED);
            } catch (IndexOutOfBoundsException e) {
                assertTrue(e.getMessage().contains("Index 1"));
            }
        });
    }
    // endregion

    // region static frame access

    @Test
    public void testMixedStaticPrimitives() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Static, null, null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            frame.setIntStatic(0, 0);
            assertEquals(0, frame.getByteStatic(0));
            assertFalse(frame.getBooleanStatic(0));
            assertEquals(0, frame.getIntStatic(0));
            assertEquals(0L, frame.getLongStatic(0));
            assertEquals(0f, frame.getFloatStatic(0), 0.0001f);
            assertEquals(0d, frame.getDoubleStatic(0), 0.0001);
            frame.setFloatStatic(0, 1.5f);
            assertEquals(0, frame.getByteStatic(0));
            assertTrue(frame.getBooleanStatic(0));
            assertEquals(1069547520, frame.getIntStatic(0));
            assertEquals(1069547520L, frame.getLongStatic(0));
            assertEquals(1.5f, frame.getFloatStatic(0), 0.0001f);
            assertEquals(5.28426686226703555032619594522E-315, frame.getDoubleStatic(0), 0.0001);
        });
    }

    @Test
    public void testStaticObject() {
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        builder.addSlot(FrameSlotKind.Static, null, null);

        FrameDescriptor descriptor = builder.build();

        testWithFrames(descriptor, frame -> {
            Object o = new Object();
            frame.setObjectStatic(0, o);
            assertEquals(o, frame.getObjectStatic(0));
        });
    }
    // endregion
}
