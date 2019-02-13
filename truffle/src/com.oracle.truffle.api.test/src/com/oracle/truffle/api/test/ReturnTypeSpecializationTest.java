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
import com.oracle.truffle.api.nodes.UnexpectedResultException;

/**
 * <h3>Specializing Return Types</h3>
 *
 * <p>
 * In order to avoid boxing and/or type casts on the return value of a node, the return value the
 * method for executing a node can have a specific type and need not be of type
 * {@link java.lang.Object}. For dynamically typed languages, this return type is something that
 * should be speculated on. When the speculation fails and the child node cannot return the
 * appropriate type of value, it can use an {@link UnexpectedResultException} to still pass the
 * result to the caller. In such a case, the caller must rewrite itself to a more general version in
 * order to avoid future failures of this kind.
 * </p>
 */
public class ReturnTypeSpecializationTest {

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

        TestChildNode() {
        }

        abstract Object execute(VirtualFrame frame);

        int executeInt(VirtualFrame frame) throws UnexpectedResultException {
            Object result = execute(frame);
            if (result instanceof Integer) {
                return (Integer) result;
            }
            throw new UnexpectedResultException(result);
        }
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
            try {
                int result = value.executeInt(frame);
                frame.setInt(slot, result);
            } catch (UnexpectedResultException e) {
                frame.getFrameDescriptor().setFrameSlotKind(slot, FrameSlotKind.Object);
                frame.setObject(slot, e.getResult());
                replace(new ObjectAssignLocal(slot, value));
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
                return replace(new ObjectReadLocal(slot)).execute(frame);
            }
        }

        @Override
        int executeInt(VirtualFrame frame) throws UnexpectedResultException {
            try {
                return frame.getInt(slot);
            } catch (FrameSlotTypeException e) {
                return replace(new ObjectReadLocal(slot)).executeInt(frame);
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
