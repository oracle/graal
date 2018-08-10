/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FrameDescriptorTest {

    private FrameSlot s1;
    private FrameSlot s2;
    private FrameSlot s3;

    @Test
    public void localsDefaultValue() throws FrameSlotTypeException {
        Object defaultValue = "default";
        FrameDescriptor d = new FrameDescriptor(defaultValue);
        s1 = d.addFrameSlot("v1");
        s2 = d.addFrameSlot("v2");
        s3 = d.addFrameSlot("v3");
        VirtualFrame f = Truffle.getRuntime().createVirtualFrame(new Object[]{1, 2}, d);

        assertFrame(f, d);
        assertFrame(f.materialize(), d);
    }

    private void assertFrame(Frame f, FrameDescriptor d) throws FrameSlotTypeException {
        assertEquals("Three slots", 3, d.getSize());
        assertEquals("Three slots list", 3, d.getSlots().size());
        assertEquals("1st slot", d.getSlots().get(0), s1);
        assertEquals("2nd slot", d.getSlots().get(1), s2);
        assertEquals("3rd slot", d.getSlots().get(2), s3);
        assertEquals("default", f.getObject(s1));
        assertEquals("default", f.getObject(s2));
        f.setInt(s3, (int) f.getArguments()[0]);
        assertEquals(1, f.getInt(s3));
    }

    @Test
    public void nullDefaultValue() {
        assertNull(new FrameDescriptor().getDefaultValue());
    }

    @Test
    public void copy() {
        Object defaultValue = "default";
        FrameDescriptor d = new FrameDescriptor(defaultValue);
        s1 = d.addFrameSlot("v1", "i1", FrameSlotKind.Boolean);
        s2 = d.addFrameSlot("v2", "i2", FrameSlotKind.Float);

        assertEquals(2, d.getSize());
        assertEquals("i2", d.getSlots().get(1).getInfo());
        assertEquals(FrameSlotKind.Float, d.getFrameSlotKind(d.getSlots().get(1)));

        FrameDescriptor copy = d.copy();
        assertEquals(2, copy.getSize());
        assertEquals("Info is copied", "i2", copy.getSlots().get(1).getInfo());
        assertEquals("Kind isn't copied", FrameSlotKind.Illegal, copy.getFrameSlotKind(copy.getSlots().get(1)));
    }

    @Test
    public void version() {
        FrameDescriptor d = new FrameDescriptor();
        s1 = d.addFrameSlot("v1", "i1", FrameSlotKind.Boolean);
        s2 = d.addFrameSlot("v2", "i2", FrameSlotKind.Float);

        Assumption version;
        version = d.getVersion();
        assertTrue(version.isValid());
        // add slot
        s3 = d.addFrameSlot("v3", "i3", FrameSlotKind.Int);
        assertEquals(3, d.getSize());
        assertFalse(version.isValid());
        version = d.getVersion();
        assertTrue(version.isValid());
        assertSame("1st slot", s1, d.getSlots().get(0));
        assertSame("2nd slot", s2, d.getSlots().get(1));
        assertSame("3rd slot", s3, d.getSlots().get(2));

        // change kind
        d.setFrameSlotKind(s3, FrameSlotKind.Object);
        assertFalse(version.isValid());
        version = d.getVersion();
        assertTrue(version.isValid());

        // remove slot
        d.removeFrameSlot("v3");
        assertFalse(version.isValid());
        version = d.getVersion();
        assertTrue(version.isValid());
    }

    @Test
    public void notInFrameAssumption() {
        FrameDescriptor d = new FrameDescriptor();
        Assumption[] ass = new Assumption[]{d.getNotInFrameAssumption("v1"), d.getNotInFrameAssumption("v2"), d.getNotInFrameAssumption("v3")};
        assertTrue(ass[0].isValid());
        assertTrue(ass[1].isValid());
        assertTrue(ass[2].isValid());
        s1 = d.addFrameSlot("v1", "i1", FrameSlotKind.Boolean);
        assertFalse(ass[0].isValid());
        assertTrue(ass[1].isValid());
        assertTrue(ass[2].isValid());
        s2 = d.addFrameSlot("v2", "i2", FrameSlotKind.Float);
        assertFalse(ass[0].isValid());
        assertFalse(ass[1].isValid());
        assertTrue(ass[2].isValid());
        s3 = d.addFrameSlot("v3", "i3", FrameSlotKind.Int);
        assertFalse(ass[0].isValid());
        assertFalse(ass[1].isValid());
        assertFalse(ass[2].isValid());

        for (String identifier : new String[]{"v1", "v2", "v3"}) {
            try {
                d.getNotInFrameAssumption(identifier);
                fail("expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                // expected
            }
        }
        d.getNotInFrameAssumption("v4");
    }

    @Test
    public void removeFrameSlot() throws FrameSlotTypeException {
        TruffleRuntime runtime = Truffle.getRuntime();
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        FrameSlot slot1 = frameDescriptor.addFrameSlot("var1", FrameSlotKind.Object);
        FrameSlot slot2 = frameDescriptor.addFrameSlot("var2", FrameSlotKind.Object);
        Frame frame = runtime.createMaterializedFrame(new Object[0], frameDescriptor);
        frame.setObject(slot1, "a");
        frame.setObject(slot2, "b");
        assertEquals("a", frame.getObject(slot1));
        assertEquals("b", frame.getObject(slot2));
        assertEquals(2, frameDescriptor.getSize());

        frameDescriptor.removeFrameSlot("var1");
        assertNull(frameDescriptor.findFrameSlot("var1"));
        assertEquals("b", frame.getObject(slot2));
        assertEquals(2, frameDescriptor.getSize());
        assertEquals(1, frameDescriptor.copy().getSize());

        FrameSlot slot3 = frameDescriptor.addFrameSlot("var3", FrameSlotKind.Object);
        FrameSlot slot4 = frameDescriptor.addFrameSlot("var4", FrameSlotKind.Object);
        assertEquals("b", frame.getObject(slot2));
        assertEquals(null, frame.getObject(slot3));
        assertEquals(null, frame.getObject(slot4));
        assertEquals(4, frameDescriptor.getSize());
        assertEquals(3, frameDescriptor.copy().getSize());

        frame.setObject(slot3, "c");
        frame.setObject(slot4, "d");
        assertEquals("b", frame.getObject(slot2));
        assertEquals("c", frame.getObject(slot3));
        assertEquals("d", frame.getObject(slot4));
    }
}
