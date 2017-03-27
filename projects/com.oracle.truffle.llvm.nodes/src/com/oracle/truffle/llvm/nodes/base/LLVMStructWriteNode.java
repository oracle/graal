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

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeap;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions.MemCopyNode;

public abstract class LLVMStructWriteNode extends Node {

    public abstract Object executeWrite(VirtualFrame frame, Object address, Object value);

    public abstract static class LLVMPrimitiveStructWriteNode extends LLVMStructWriteNode {

        @Specialization
        public Object executeWrite(LLVMAddress address, boolean value) {
            LLVMMemory.putI1(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, byte value) {
            LLVMMemory.putI8(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, short value) {
            LLVMMemory.putI16(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, int value) {
            LLVMMemory.putI32(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, long value) {
            LLVMMemory.putI64(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, float value) {
            LLVMMemory.putFloat(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, double value) {
            LLVMMemory.putDouble(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, LLVM80BitFloat value) {
            LLVMMemory.put80BitFloat(address, value);
            return null;
        }

        // global variable descriptor

        @Specialization
        public Object executeWrite(LLVMGlobalVariableDescriptor address, boolean value) {
            LLVMMemory.putI1(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariableDescriptor address, byte value) {
            LLVMMemory.putI8(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariableDescriptor address, short value) {
            LLVMMemory.putI16(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariableDescriptor address, int value) {
            LLVMMemory.putI32(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariableDescriptor address, long value) {
            LLVMMemory.putI64(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariableDescriptor address, float value) {
            LLVMMemory.putFloat(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariableDescriptor address, double value) {
            LLVMMemory.putDouble(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariableDescriptor address, LLVM80BitFloat value) {
            LLVMMemory.put80BitFloat(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, LLVMAddress value) {
            LLVMMemory.putAddress(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariableDescriptor address, LLVMAddress value) {
            LLVMMemory.putAddress(address.getNativeAddress(), value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, LLVMGlobalVariableDescriptor value) {
            LLVMMemory.putAddress(address, value.getNativeAddress());
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariableDescriptor address, LLVMGlobalVariableDescriptor value) {
            LLVMMemory.putAddress(address.getNativeAddress(), value.getNativeAddress());
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, LLVMFunction value) {
            LLVMHeap.putFunctionIndex(address, value.getFunctionIndex());
            return null;
        }
    }

    public abstract static class LLVMCompoundStructWriteNode extends LLVMStructWriteNode {

        private int size;

        @Child private MemCopyNode memCopy;

        public LLVMCompoundStructWriteNode(LLVMNativeFunctions heapFunctions, int size) {
            this.size = size;
            this.memCopy = heapFunctions.createMemCopyNode();
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, LLVMAddress value) {
            memCopy.execute(address, value, size);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariableDescriptor address, LLVMAddress value) {
            memCopy.execute(address.getNativeAddress(), value, size);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, LLVMGlobalVariableDescriptor value) {
            memCopy.execute(address, value.getNativeAddress(), size);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariableDescriptor address, LLVMGlobalVariableDescriptor value) {
            memCopy.execute(address.getNativeAddress(), value.getNativeAddress(), size);
            return null;
        }

    }

    public abstract static class LLVMEmptyStructWriteNode extends LLVMStructWriteNode {

        @Specialization
        @SuppressWarnings("unused")
        public Object executeWrite(LLVMAddress address, Object value) {
            return null;
        }

    }

}
