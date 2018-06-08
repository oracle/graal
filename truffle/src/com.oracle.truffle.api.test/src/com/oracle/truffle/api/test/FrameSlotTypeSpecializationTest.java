/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleRuntime;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * <h3>Specializing Frame Slot Types</h3>
 *
 * <p>
 * Dynamically typed languages can speculate on the type of a frame slot and only fall back at run
 * time to a more generic type if necessary. The new type of a frame slot can be set using the
 * {@link FrameDescriptor#setFrameSlotKind(FrameSlot, FrameSlotKind)} method.
 * </p>
 *
 * <p>
 * The next part of the Truffle API introduction is at
 * {@link com.oracle.truffle.api.test.ReturnTypeSpecializationTest}.
 * </p>
 */
public class FrameSlotTypeSpecializationTest {

    @Test
    public void test() {
        TruffleRuntime runtime = Truffle.getRuntime();
        FrameDescriptor frameDescriptor = new FrameDescriptor();
        FrameSlot slot = frameDescriptor.addFrameSlot("localVar", FrameSlotKind.Int);
        TestRootNode rootNode = new TestRootNode(frameDescriptor, new IntAssignLocal(slot, new StringTestChildNode()), new IntReadLocal(slot));
        CallTarget target = runtime.createCallTarget(rootNode);
        Assert.assertEquals(FrameSlotKind.Int, frameDescriptor.getFrameSlotKind(slot));
        Object result = target.call();
        Assert.assertEquals("42", result);
        Assert.assertEquals(FrameSlotKind.Object, frameDescriptor.getFrameSlotKind(slot));
    }

    class TestRootNode extends RootNode {

        @Child TestChildNode left;
        @Child TestChildNode right;

        TestRootNode(FrameDescriptor descriptor, TestChildNode left, TestChildNode right) {
            super(null, descriptor);
            this.left = left;
            this.right = right;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            left.execute(frame);
            return right.execute(frame);
        }
    }

    abstract class TestChildNode extends Node {

        protected TestChildNode() {
        }

        abstract Object execute(VirtualFrame frame);
    }

    abstract class FrameSlotNode extends TestChildNode {

        protected final FrameSlot slot;

        FrameSlotNode(FrameSlot slot) {
            this.slot = slot;
        }
    }

    class StringTestChildNode extends TestChildNode {

        @Override
        Object execute(VirtualFrame frame) {
            return "42";
        }

    }

    class IntAssignLocal extends FrameSlotNode {

        @Child private TestChildNode value;

        IntAssignLocal(FrameSlot slot, TestChildNode value) {
            super(slot);
            this.value = value;
        }

        @Override
        Object execute(VirtualFrame frame) {
            Object o = value.execute(frame);
            if (o instanceof Integer) {
                frame.setInt(slot, (Integer) o);
            } else {
                frame.getFrameDescriptor().setFrameSlotKind(slot, FrameSlotKind.Object);
                frame.setObject(slot, o);
                this.replace(new ObjectAssignLocal(slot, value));
            }
            return null;
        }
    }

    class ObjectAssignLocal extends FrameSlotNode {

        @Child private TestChildNode value;

        ObjectAssignLocal(FrameSlot slot, TestChildNode value) {
            super(slot);
            this.value = value;
        }

        @Override
        Object execute(VirtualFrame frame) {
            Object o = value.execute(frame);
            frame.getFrameDescriptor().setFrameSlotKind(slot, FrameSlotKind.Object);
            frame.setObject(slot, o);
            return null;
        }
    }

    class IntReadLocal extends FrameSlotNode {

        IntReadLocal(FrameSlot slot) {
            super(slot);
        }

        @Override
        Object execute(VirtualFrame frame) {
            try {
                return frame.getInt(slot);
            } catch (FrameSlotTypeException e) {
                return this.replace(new ObjectReadLocal(slot)).execute(frame);
            }
        }
    }

    class ObjectReadLocal extends FrameSlotNode {

        ObjectReadLocal(FrameSlot slot) {
            super(slot);
        }

        @Override
        Object execute(VirtualFrame frame) {
            try {
                return frame.getObject(slot);
            } catch (FrameSlotTypeException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
