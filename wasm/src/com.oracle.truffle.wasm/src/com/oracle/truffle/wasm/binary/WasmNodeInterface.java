/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.binary;

import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.VirtualFrame;

public interface WasmNodeInterface extends WasmTracing {
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
            return frame.getLong(codeEntry().stackSlot(slot));
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
