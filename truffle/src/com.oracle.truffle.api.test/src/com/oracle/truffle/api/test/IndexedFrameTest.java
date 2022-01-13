/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

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
    }

    @SuppressWarnings("deprecation")
    private static int getLegacySize(FrameDescriptor descriptor) {
        return descriptor.getSize();
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
        assertEquals(0, getLegacySize(descriptor));
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
        assertEquals(0, getLegacySize(descriptor));

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
}
