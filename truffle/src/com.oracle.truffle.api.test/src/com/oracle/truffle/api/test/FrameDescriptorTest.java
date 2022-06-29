/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class FrameDescriptorTest {

    @BeforeClass
    public static void runWithWeakEncapsulationOnly() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
    }

    private int s1;
    private int s2;
    private int s3;

    @Test
    public void localsDefaultValue() throws FrameSlotTypeException {
        Object defaultValue = "default";
        var builder = FrameDescriptor.newBuilder().defaultValue(defaultValue);
        s1 = builder.addSlot(FrameSlotKind.Illegal, "v1", null);
        s2 = builder.addSlot(FrameSlotKind.Illegal, "v2", null);
        s3 = builder.addSlot(FrameSlotKind.Illegal, "v3", null);
        FrameDescriptor descriptor = builder.build();
        VirtualFrame f = Truffle.getRuntime().createVirtualFrame(new Object[]{1, 2}, descriptor);

        assertFrame(f, descriptor);
        assertFrame(f.materialize(), descriptor);
    }

    private void assertFrame(Frame f, FrameDescriptor d) throws FrameSlotTypeException {
        assertEquals("Three slots", 3, d.getNumberOfSlots());
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
        var builder = FrameDescriptor.newBuilder().defaultValue(defaultValue);
        s1 = builder.addSlot(FrameSlotKind.Boolean, "v1", "i1");
        s2 = builder.addSlot(FrameSlotKind.Float, "v2", "i2");

        FrameDescriptor d = builder.build();

        assertEquals(2, d.getNumberOfSlots());
        assertEquals("i2", d.getSlotInfo(s2));
        assertEquals(FrameSlotKind.Float, d.getSlotKind(s2));

        FrameDescriptor copy = d.copy();
        assertEquals(2, copy.getNumberOfSlots());
        assertEquals("Info is copied", "i2", d.getSlotInfo(s2));
        assertEquals("Kind isn't copied", FrameSlotKind.Float, d.getSlotKind(s2));
    }

}
