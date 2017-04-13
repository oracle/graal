/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.api.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;

public class FrameDescriptorTest {

    private FrameSlot s1;
    private FrameSlot s2;
    private FrameSlot s3;

    @Test
    public void localsDefaultValue() throws Exception {
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
    public void copy() throws Exception {
        Object defaultValue = "default";
        FrameDescriptor d = new FrameDescriptor(defaultValue);
        s1 = d.addFrameSlot("v1", "i1", FrameSlotKind.Boolean);
        s2 = d.addFrameSlot("v2", "i2", FrameSlotKind.Float);

        assertEquals(2, d.getSize());
        assertEquals(d.getSlots().get(1).getInfo(), "i2");
        assertEquals(d.getSlots().get(1).getKind(), FrameSlotKind.Float);
        assertEquals(d.getSlots().get(1).getIndex(), 1);

        FrameDescriptor copy = d.copy();
        assertEquals(2, copy.getSize());
        assertEquals(1, copy.getSlots().get(1).getIndex());
        assertEquals("Info is copied", "i2", copy.getSlots().get(1).getInfo());
        assertEquals("Kind isn't copied", FrameSlotKind.Illegal, copy.getSlots().get(1).getKind());
    }

    @Test
    public void shallowCopy() {
        Object defaultValue = "default";
        FrameDescriptor d = new FrameDescriptor(defaultValue);
        s1 = d.addFrameSlot("v1", "i1", FrameSlotKind.Boolean);
        s2 = d.addFrameSlot("v2", "i2", FrameSlotKind.Float);

        assertEquals(2, d.getSize());
        final FrameSlot first = d.getSlots().get(1);
        assertEquals(first.getInfo(), "i2");
        assertEquals(first.getKind(), FrameSlotKind.Float);
        assertEquals(first.getIndex(), 1);

        FrameDescriptor copy = d.shallowCopy();

        assertEquals(2, copy.getSize());
        final FrameSlot firstCopy = copy.getSlots().get(1);
        assertEquals("Info is copied", firstCopy.getInfo(), "i2");
        assertEquals("Kind is copied", firstCopy.getKind(), FrameSlotKind.Float);
        assertEquals(firstCopy.getIndex(), 1);

        firstCopy.setKind(FrameSlotKind.Int);
        assertEquals("Kind is changed", firstCopy.getKind(), FrameSlotKind.Int);
        assertEquals("Kind is changed in original too!", first.getKind(), FrameSlotKind.Int);
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
        s3.setKind(FrameSlotKind.Object);
        assertFalse(version.isValid());
        version = d.getVersion();
        assertTrue(version.isValid());

        // remove slot
        d.removeFrameSlot("v3");
        assertEquals(2, d.getSize());
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
}
