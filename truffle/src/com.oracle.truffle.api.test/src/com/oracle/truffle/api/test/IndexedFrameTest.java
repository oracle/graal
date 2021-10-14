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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;

public class IndexedFrameTest {

    @SuppressWarnings("deprecation")
    @Test
    public void testSlotIds() {
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
        assertEquals(0, descriptor.getSize());

        VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(new Object[]{}, descriptor);
        for (int slot = 0; slot < descriptor.getNumberOfSlots(); slot++) {
            assertTrue(frame.isObject(slot));
            assertNull(frame.getObject(slot));
        }
    }

    @SuppressWarnings("deprecation")
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
        assertEquals(0, descriptor.getSize());

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

    private static void assertIsType(VirtualFrame frame, int slot, FrameSlotKind k) {
        assertEquals(k == FrameSlotKind.Boolean, frame.isBoolean(slot));
        assertEquals(k == FrameSlotKind.Byte, frame.isByte(slot));
        assertEquals(k == FrameSlotKind.Int, frame.isInt(slot));
        assertEquals(k == FrameSlotKind.Long, frame.isLong(slot));
        assertEquals(k == FrameSlotKind.Double, frame.isDouble(slot));
        assertEquals(k == FrameSlotKind.Float, frame.isFloat(slot));
        assertEquals(k == FrameSlotKind.Object, frame.isObject(slot));
    }
}
