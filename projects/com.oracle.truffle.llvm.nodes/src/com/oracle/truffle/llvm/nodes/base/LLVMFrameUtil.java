/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.base;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.AllocationResult;

public class LLVMFrameUtil {

    public static boolean getI1(VirtualFrame frame, FrameSlot frameSlot) {
        return FrameUtil.getBooleanSafe(frame, frameSlot);
    }

    public static byte getI8(VirtualFrame frame, FrameSlot frameSlot) {
        return FrameUtil.getByteSafe(frame, frameSlot);
    }

    public static short getI16(VirtualFrame frame, FrameSlot frameSlot) {
        return (short) FrameUtil.getIntSafe(frame, frameSlot);
    }

    public static int getI32(VirtualFrame frame, FrameSlot frameSlot) {
        return FrameUtil.getIntSafe(frame, frameSlot);
    }

    public static long getI64(VirtualFrame frame, FrameSlot frameSlot) {
        return FrameUtil.getLongSafe(frame, frameSlot);
    }

    public static float getFloat(VirtualFrame frame, FrameSlot frameSlot) {
        return FrameUtil.getFloatSafe(frame, frameSlot);
    }

    public static double getDouble(VirtualFrame frame, FrameSlot frameSlot) {
        return FrameUtil.getDoubleSafe(frame, frameSlot);
    }

    public static LLVMAddress getAddress(VirtualFrame frame, FrameSlot frameSlot) {
        return (LLVMAddress) FrameUtil.getObjectSafe(frame, frameSlot);
    }

    public static LLVMIVarBit getIVarbit(VirtualFrame frame, FrameSlot frameSlot) {
        return (LLVMIVarBit) FrameUtil.getObjectSafe(frame, frameSlot);
    }

    public static LLVM80BitFloat get80BitFloat(VirtualFrame frame, FrameSlot frameSlot) {
        return (LLVM80BitFloat) FrameUtil.getObjectSafe(frame, frameSlot);
    }

    public static LLVMAddress allocateMemory(LLVMStack stack, VirtualFrame frame, FrameSlot stackPointerSlot, int size, int alignment) {
        LLVMAddress stackPointer = LLVMFrameUtil.getAddress(frame, stackPointerSlot);
        AllocationResult allocResult = stack.allocateMemory(stackPointer, size, alignment);
        frame.setObject(stackPointerSlot, allocResult.getStackPointer());
        return allocResult.getAllocatedMemory();
    }

}
