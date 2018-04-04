/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.asm.support;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMAddressStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI16StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;

public abstract class LLVMAMD64WriteNode extends Node {
    public static final long MASK_16 = 0xFFFFFFFFFFFF0000L;
    public static final long MASK_32 = 0xFFFFFFFF00000000L;

    @Child private LLVMAMD64RegisterToLongNode readRegister;

    @Child private LLVMStoreNode storeAddress = LLVMAddressStoreNodeGen.create(PrimitiveType.I64, null, null);
    @Child private LLVMStoreNode storeI8 = LLVMI8StoreNodeGen.create(null, null);
    @Child private LLVMStoreNode storeI16 = LLVMI16StoreNodeGen.create(null, null);
    @Child private LLVMStoreNode storeI32 = LLVMI32StoreNodeGen.create(null, null);
    @Child private LLVMStoreNode storeI64 = LLVMI64StoreNodeGen.create(null, null);

    private final int shift;
    private final long mask;

    public abstract void execute(VirtualFrame frame, Object location, Object value);

    public LLVMAMD64WriteNode() {
        this(0);
    }

    public LLVMAMD64WriteNode(int shift) {
        this.shift = shift;
        this.mask = ~((long) LLVMExpressionNode.I8_MASK << shift);
        readRegister = LLVMAMD64RegisterToLongNodeGen.create();
    }

    @Specialization(guards = "!isFrameSlot(addr)")
    protected void doI8(Object addr, byte value) {
        storeI8.executeWithTarget(addr, value);
    }

    @Specialization(guards = "!isFrameSlot(addr)")
    protected void doI16(Object addr, short value) {
        storeI16.executeWithTarget(addr, value);
    }

    @Specialization(guards = "!isFrameSlot(addr)")
    protected void doI32(Object addr, int value) {
        storeI32.executeWithTarget(addr, value);
    }

    @Specialization(guards = "!isFrameSlot(addr)")
    protected void doI64(Object addr, long value) {
        storeI64.executeWithTarget(addr, value);
    }

    @Specialization(guards = "!isFrameSlot(addr)")
    protected void doObject(Object addr, Object value) {
        storeAddress.executeWithTarget(addr, value);
    }

    @Specialization
    protected void doI8(VirtualFrame frame, FrameSlot slot, byte value) {
        long reg = readRegister.execute(frame, slot);
        long val = (reg & mask) | (Byte.toUnsignedLong(value) << shift);
        frame.setLong(slot, val);
    }

    @Specialization
    protected void doI16(VirtualFrame frame, FrameSlot slot, short value) {
        long reg = readRegister.execute(frame, slot);
        long val = (reg & MASK_16) | Short.toUnsignedLong(value);
        frame.setLong(slot, val);
    }

    @Specialization
    protected void doI32(VirtualFrame frame, FrameSlot slot, int value) {
        long val = Integer.toUnsignedLong(value);
        frame.setLong(slot, val);
    }

    @Specialization
    protected void doI64(VirtualFrame frame, FrameSlot slot, long value) {
        frame.setLong(slot, value);
    }

    @Specialization
    protected void doAddress(VirtualFrame frame, FrameSlot slot, LLVMAddress value) {
        frame.setLong(slot, value.getVal());
    }

    protected static boolean isFrameSlot(Object o) {
        return o instanceof FrameSlot;
    }
}
