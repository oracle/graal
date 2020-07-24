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

    default void push(VirtualFrame frame, int slot, long value) {
        frame.setLong(codeEntry().stackSlot(slot), value);
    }

    default void pushInt(VirtualFrame frame, int slot, int value) {
        push(frame, slot, value & 0xffffffffL);
    }

    default void pushFloat(VirtualFrame frame, int slot, float value) {
        pushInt(frame, slot, Float.floatToRawIntBits(value));
    }

    default void pushDouble(VirtualFrame frame, int slot, double value) {
        push(frame, slot, Double.doubleToRawLongBits(value));
    }

    default long pop(VirtualFrame frame, int slot) {
        try {
            long result = frame.getLong(codeEntry().stackSlot(slot));
            // Needed to avoid keeping track of popped slots in FrameStates.
            frame.setLong(codeEntry().stackSlot(slot), 0L);
            return result;
        } catch (FrameSlotTypeException e) {
            throw new RuntimeException(e);
        }
    }

    default int popInt(VirtualFrame frame, int slot) {
        return (int) pop(frame, slot);
    }

    default float popAsFloat(VirtualFrame frame, int slot) {
        return Float.intBitsToFloat(popInt(frame, slot));
    }

    default double popAsDouble(VirtualFrame frame, int slot) {
        return Double.longBitsToDouble(pop(frame, slot));
    }

}
