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

import org.junit.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

/**
 * <h3>Storing Values in Frame Slots</h3>
 * 
 * <p>
 * The frame is the preferred data structure for passing values between nodes. It can in particular
 * be used for storing the values of local variables of the guest language. The
 * {@link FrameDescriptor} represents the current structure of the frame. The method
 * {@link FrameDescriptor#addFrameSlot(Object, FrameSlotKind)} can be used to create predefined
 * frame slots. The setter and getter methods in the {@link Frame} class can be used to access the
 * current value of a particular frame slot. Values can be removed from a frame via the
 * {@link FrameDescriptor#removeFrameSlot(Object)} method.
 * </p>
 * 
 * <p>
 * There are five primitive types for slots available: {@link java.lang.Boolean},
 * {@link java.lang.Integer}, {@link java.lang.Long}, {@link java.lang.Float}, and
 * {@link java.lang.Double} . It is encouraged to use those types whenever possible. Dynamically
 * typed languages can speculate on the type of a value fitting into a primitive (see
 * {@link FrameSlotTypeSpecializationTest}). When a frame slot is of one of those particular
 * primitive types, its value may only be accessed with the respectively typed getter method (
 * {@link Frame#getBoolean}, {@link Frame#getInt}, {@link Frame#getLong}, {@link Frame#getFloat}, or
 * {@link Frame#getDouble}) or setter method ({@link Frame#setBoolean}, {@link Frame#setInt},
 * {@link Frame#setLong}, {@link Frame#setFloat}, or {@link Frame#setDouble}) in the {@link Frame}
 * class.
 * </p>
 * 
 * <p>
 * The next part of the Truffle API introduction is at
 * {@link com.oracle.truffle.api.test.FrameSlotTypeSpecializationTest}.
 * </p>
 */
public class FrameTest {

    @Test
    public void test() {
        TruffleRuntime runtime = Truffle.getRuntime();
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        String varName = "localVar";
        FrameSlot slot = frameDescriptor.addFrameSlot(varName, FrameSlotKind.Int);
        TestRootNode rootNode = new TestRootNode(frameDescriptor, new AssignLocal(slot), new ReadLocal(slot));
        CallTarget target = runtime.createCallTarget(rootNode);
        Object result = target.call();
        Assert.assertEquals(42, result);
        frameDescriptor.removeFrameSlot(varName);
        boolean slotMissing = false;
        try {
            result = target.call();
        } catch (IllegalArgumentException iae) {
            slotMissing = true;
        }
        Assert.assertTrue(slotMissing);
    }

    class TestRootNode extends RootNode {

        @Child TestChildNode left;
        @Child TestChildNode right;

        public TestRootNode(FrameDescriptor descriptor, TestChildNode left, TestChildNode right) {
            super(null, descriptor);
            this.left = left;
            this.right = right;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return left.execute(frame) + right.execute(frame);
        }
    }

    abstract class TestChildNode extends Node {

        public TestChildNode() {
            super(null);
        }

        abstract int execute(VirtualFrame frame);
    }

    abstract class FrameSlotNode extends TestChildNode {

        protected final FrameSlot slot;

        public FrameSlotNode(FrameSlot slot) {
            this.slot = slot;
        }
    }

    class AssignLocal extends FrameSlotNode {

        AssignLocal(FrameSlot slot) {
            super(slot);
        }

        @Override
        int execute(VirtualFrame frame) {
            frame.setInt(slot, 42);
            return 0;
        }
    }

    class ReadLocal extends FrameSlotNode {

        ReadLocal(FrameSlot slot) {
            super(slot);
        }

        @Override
        int execute(VirtualFrame frame) {
            try {
                return frame.getInt(slot);
            } catch (FrameSlotTypeException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
