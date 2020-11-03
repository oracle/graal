/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.graalvm.wasm.WasmCodeEntry;

public interface WasmNodeInterface {
    WasmCodeEntry codeEntry();

    /* LOCALS operations */

    default long getLong(VirtualFrame frame, int slot) {
        try {
            return frame.getLong(codeEntry().localSlot(slot));
        } catch (FrameSlotTypeException e) {
            throw new RuntimeException(e);
        }
    }

    default int getInt(VirtualFrame frame, int slot) {
        try {
            return frame.getInt(codeEntry().localSlot(slot));
        } catch (FrameSlotTypeException e) {
            throw new RuntimeException(e);
        }
    }

    default float getFloat(VirtualFrame frame, int slot) {
        try {
            return frame.getFloat(codeEntry().localSlot(slot));
        } catch (FrameSlotTypeException e) {
            throw new RuntimeException(e);
        }
    }

    default double getDouble(VirtualFrame frame, int slot) {
        try {
            return frame.getDouble(codeEntry().localSlot(slot));
        } catch (FrameSlotTypeException e) {
            throw new RuntimeException(e);
        }
    }

    default void setLong(VirtualFrame frame, int slot, long value) {
        frame.setLong(codeEntry().localSlot(slot), value);
    }

    default void setInt(VirtualFrame frame, int slot, int value) {
        frame.setInt(codeEntry().localSlot(slot), value);
    }

    default void setFloat(VirtualFrame frame, int slot, float value) {
        frame.setFloat(codeEntry().localSlot(slot), value);
    }

    default void setDouble(VirtualFrame frame, int slot, double value) {
        frame.setDouble(codeEntry().localSlot(slot), value);
    }

    /* STACK operations */

    default void push(long[] stack, int slot, long value) {
        stack[slot] = value;
    }

    default void pushInt(long[] stack, int slot, int value) {
        push(stack, slot, value & 0xffffffffL);
    }

    default void pushFloat(long[] stack, int slot, float value) {
        pushInt(stack, slot, Float.floatToRawIntBits(value));
    }

    default void pushDouble(long[] stack, int slot, double value) {
        push(stack, slot, Double.doubleToRawLongBits(value));
    }

    default long pop(long[] stack, int slot) {
        long result = stack[slot];
        if (CompilerDirectives.inCompiledCode()) {
            // Needed to avoid keeping track of popped slots in FrameStates.
            stack[slot] = 0L;
        }
        return result;
    }

    default int popInt(long[] stack, int slot) {
        return (int) pop(stack, slot);
    }

    default float popAsFloat(long[] stack, int slot) {
        return Float.intBitsToFloat(popInt(stack, slot));
    }

    default double popAsDouble(long[] stack, int slot) {
        return Double.longBitsToDouble(pop(stack, slot));
    }

}
