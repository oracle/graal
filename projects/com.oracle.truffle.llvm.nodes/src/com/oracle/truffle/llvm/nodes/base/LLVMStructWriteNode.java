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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeap;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMProfiledMemMove;

public abstract class LLVMStructWriteNode extends LLVMNode {

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
        public Object executeWrite(LLVMGlobalVariable address, boolean value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            globalAccess.putI1(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariable address, byte value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            globalAccess.putI8(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariable address, short value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            globalAccess.putI16(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariable address, int value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            globalAccess.putI32(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariable address, long value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            globalAccess.putI64(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariable address, float value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            globalAccess.putFloat(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariable address, double value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            globalAccess.putDouble(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariable address, LLVM80BitFloat value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            LLVMMemory.put80BitFloat(globalAccess.getNativeLocation(address), value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, LLVMAddress value) {
            LLVMMemory.putAddress(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariable address, LLVMAddress value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            globalAccess.putAddress(address, value);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, LLVMGlobalVariable value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            LLVMMemory.putAddress(address, globalAccess.getNativeLocation(value));
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariable address, LLVMGlobalVariable value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess1,
                        @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess2) {
            LLVMMemory.putAddress(globalAccess1.getNativeLocation(address), globalAccess2.getNativeLocation(value));
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, LLVMFunction value) {
            LLVMHeap.putFunctionPointer(address, value.getFunctionPointer());
            return null;
        }
    }

    public abstract static class LLVMCompoundStructWriteNode extends LLVMStructWriteNode {

        private final int size;
        private final LLVMProfiledMemMove profiledMemMove;

        public LLVMCompoundStructWriteNode(int size) {
            this.size = size;
            this.profiledMemMove = new LLVMProfiledMemMove();
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, LLVMAddress value) {
            profiledMemMove.memmove(address, value, size);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariable address, LLVMAddress value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            profiledMemMove.memmove(globalAccess.getNativeLocation(address), value, size);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMAddress address, LLVMGlobalVariable value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            profiledMemMove.memmove(address, globalAccess.getNativeLocation(value), size);
            return null;
        }

        @Specialization
        public Object executeWrite(LLVMGlobalVariable address, LLVMGlobalVariable value, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess1,
                        @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess2) {
            profiledMemMove.memmove(globalAccess1.getNativeLocation(address), globalAccess2.getNativeLocation(value), size);
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
