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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;

public class WasmCodeEntry {
    @CompilationFinal(dimensions = 1) private byte[] data;
    @CompilationFinal(dimensions = 1) private FrameSlot[] localSlots;
    @CompilationFinal(dimensions = 1) private FrameSlot[] stackSlots;
    @CompilationFinal(dimensions = 1) private byte[] localTypes;


    public WasmCodeEntry(byte[] data) {
        this.data = data;
        this.localSlots = null;
        this.stackSlots = null;
        this.localTypes = null;
    }

    public byte[] data() {
        return data;
    }

    public FrameSlot localSlot(int index) {
        return localSlots[index];
    }

    public FrameSlot stackSlot(int index) {
        return stackSlots[index];
    }

    public void initLocalSlots(FrameDescriptor frameDescriptor) {
        localSlots = new FrameSlot[localTypes.length];
        for (int i = 0; i != localTypes.length; ++i) {
            FrameSlot stackSlot = frameDescriptor.addFrameSlot(i, frameSlotKind(localTypes[i]));
            localSlots[i] = stackSlot;
        }
    }

    private static FrameSlotKind frameSlotKind(byte valueType) {
        switch (valueType) {
            case ValueTypes.I32_TYPE:
                return FrameSlotKind.Int;
            case ValueTypes.I64_TYPE:
                return FrameSlotKind.Long;
            case ValueTypes.F32_TYPE:
                return FrameSlotKind.Float;
            case ValueTypes.F64_TYPE:
                return FrameSlotKind.Double;
            default:
                Assert.fail(String.format("Unknown value type: 0x%02X", valueType));
        }
        return null;
    }

    public void initStackSlots(FrameDescriptor frameDescriptor, int maxStackSize) {
        stackSlots = new FrameSlot[maxStackSize];
        for (int i = 0; i != maxStackSize; ++i) {
            FrameSlot stackSlot = frameDescriptor.addFrameSlot(localSlots.length + i, FrameSlotKind.Long);
            stackSlots[i] = stackSlot;
        }
    }

    public void setLocalTypes(byte[] localTypes) {
        this.localTypes = localTypes;
    }

    public byte localType(int index) {
        return localTypes[index];
    }
}
