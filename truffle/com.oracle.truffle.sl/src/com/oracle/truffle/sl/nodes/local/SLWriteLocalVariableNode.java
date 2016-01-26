/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.sl.nodes.local;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.sl.nodes.SLExpressionNode;

/**
 * Node to write a local variable to a function's {@link VirtualFrame frame}. The Truffle frame API
 * allows to store primitive values of all Java primitive types, and Object values.
 */
@NodeChild("valueNode")
@NodeField(name = "slot", type = FrameSlot.class)
public abstract class SLWriteLocalVariableNode extends SLExpressionNode {

    public SLWriteLocalVariableNode(SourceSection src) {
        super(src);
    }

    /**
     * Returns the descriptor of the accessed local variable. The implementation of this method is
     * created by the Truffle DSL based on the {@link NodeField} annotation on the class.
     */
    protected abstract FrameSlot getSlot();

    /**
     * Specialized method to write a primitive {@code long} value. This is only possible if the
     * local variable also has currently the type {@code long}, therefore a Truffle DSL
     * {@link #isLongKind(VirtualFrame) custom guard} is specified.
     */
    @Specialization(guards = "isLongKind(frame)")
    protected long writeLong(VirtualFrame frame, long value) {
        frame.setLong(getSlot(), value);
        return value;
    }

    @Specialization(guards = "isBooleanKind(frame)")
    protected boolean writeBoolean(VirtualFrame frame, boolean value) {
        frame.setBoolean(getSlot(), value);
        return value;
    }

    /**
     * Generic write method that works for all possible types.
     * <p>
     * Why is this method annotated with {@link Specialization} and not {@link Fallback}? For a
     * {@link Fallback} method, the Truffle DSL generated code would try all other specializations
     * first before calling this method. We know that all these specializations would fail their
     * guards, so there is no point in calling them. Since this method takes a value of type
     * {@link Object}, it is guaranteed to never fail, i.e., once we are in this specialization the
     * node will never be re-specialized.
     */
    @Specialization(contains = {"writeLong", "writeBoolean"})
    protected Object write(VirtualFrame frame, Object value) {
        if (getSlot().getKind() != FrameSlotKind.Object) {
            /*
             * The local variable has still a primitive type, we need to change it to Object. Since
             * the variable type is important when the compiler optimizes a method, we also discard
             * compiled code.
             */
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getSlot().setKind(FrameSlotKind.Object);
        }
        frame.setObject(getSlot(), value);
        return value;
    }

    /**
     * Guard function that the local variable has the type {@code long}.
     */
    @SuppressWarnings("unused")
    protected boolean isLongKind(VirtualFrame frame) {
        return isKind(FrameSlotKind.Long);
    }

    @SuppressWarnings("unused")
    protected boolean isBooleanKind(VirtualFrame frame) {
        return isKind(FrameSlotKind.Boolean);
    }

    private boolean isKind(FrameSlotKind kind) {
        if (getSlot().getKind() == kind) {
            /* Success: the frame slot has the expected kind. */
            return true;
        } else if (getSlot().getKind() == FrameSlotKind.Illegal) {
            /*
             * This is the first write to this local variable. We can set the type to the one we
             * expect. Since the variable type is important when the compiler optimizes a method, we
             * also discard compiled code.
             */
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getSlot().setKind(kind);
            return true;
        } else {
            /*
             * Failure: the frame slot has the wrong kind, the Truffle DSL generated code will
             * choose a different specialization.
             */
            return false;
        }
    }
}
