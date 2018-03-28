/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.memory.load;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalReadNode.ReadObjectNode;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.UnsafeArrayAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;

public abstract class LLVMDirectLoadNode {

    @NodeField(name = "bitWidth", type = int.class)
    public abstract static class LLVMIVarBitDirectLoadNode extends LLVMLoadNode {

        public abstract int getBitWidth();

        @Specialization
        protected LLVMIVarBit doI64(LLVMAddress addr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getIVarBit(addr, getBitWidth());
        }

        @Specialization
        protected LLVMIVarBit doI64(LLVMGlobal addr,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getIVarBit(globalAccess.executeWithTarget(addr), getBitWidth());
        }

        @Specialization
        protected Object doForeign(LLVMTruffleObject addr,
                        @Cached("createForeignRead()") LLVMForeignReadNode foreignRead) {
            byte[] result = new byte[getByteSize()];
            LLVMTruffleObject currentPtr = addr;
            for (int i = result.length - 1; i >= 0; i--) {
                result[i] = (Byte) foreignRead.execute(currentPtr);
                currentPtr = currentPtr.increment(I8_SIZE_IN_BYTES);
            }
            return LLVMIVarBit.create(getBitWidth(), result, getBitWidth(), false);
        }

        protected LLVMForeignReadNode createForeignRead() {
            return new LLVMForeignReadNode(ForeignToLLVMType.I8);
        }

        private int getByteSize() {
            assert getBitWidth() % Byte.SIZE == 0;
            return getBitWidth() / Byte.SIZE;
        }
    }

    public abstract static class LLVM80BitFloatDirectLoadNode extends LLVMLoadNode {

        @Specialization
        protected LLVM80BitFloat doDouble(LLVMAddress addr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.get80BitFloat(addr);
        }

        @Specialization
        protected LLVM80BitFloat doDouble(LLVMGlobal addr,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.get80BitFloat(globalAccess.executeWithTarget(addr));
        }

        @Specialization
        protected LLVM80BitFloat doForeign(LLVMTruffleObject addr,
                        @Cached("createForeignRead()") LLVMForeignReadNode foreignRead) {
            byte[] result = new byte[LLVM80BitFloat.BYTE_WIDTH];
            LLVMTruffleObject currentPtr = addr;
            for (int i = 0; i < result.length; i++) {
                result[i] = (Byte) foreignRead.execute(currentPtr);
                currentPtr = currentPtr.increment(I8_SIZE_IN_BYTES);
            }
            return LLVM80BitFloat.fromBytes(result);
        }

        protected LLVMForeignReadNode createForeignRead() {
            return new LLVMForeignReadNode(ForeignToLLVMType.I8);
        }
    }

    public abstract static class LLVMFunctionDirectLoadNode extends LLVMLoadNode {

        @Specialization
        protected LLVMAddress doAddress(LLVMAddress addr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return LLVMAddress.fromLong(memory.getFunctionPointer(addr));
        }

        @Specialization
        protected LLVMAddress doAddress(LLVMGlobal addr,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return LLVMAddress.fromLong(memory.getFunctionPointer(globalAccess.executeWithTarget(addr)));
        }

        static LLVMForeignReadNode createForeignRead() {
            return new LLVMForeignReadNode(ForeignToLLVMType.POINTER);
        }

        @Specialization
        protected Object doForeign(LLVMTruffleObject addr,
                        @Cached("createForeignRead()") LLVMForeignReadNode foreignRead) {
            return foreignRead.execute(addr);
        }
    }

    public abstract static class LLVMAddressDirectLoadNode extends LLVMLoadNode {

        @Child protected ForeignToLLVM toLLVM = ForeignToLLVM.create(ForeignToLLVMType.POINTER);

        @Specialization
        protected LLVMAddress doAddress(LLVMAddress addr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            return memory.getAddress(addr);
        }

        @Specialization
        protected LLVMAddress doLLVMByteArrayAddress(LLVMVirtualAllocationAddress address,
                        @Cached("getUnsafeArrayAccess()") UnsafeArrayAccess memory) {
            return LLVMAddress.fromLong(address.getI64(memory));
        }

        @Specialization
        protected Object doAddress(LLVMGlobal addr,
                        @Cached("create()") ReadObjectNode globalAccess) {
            return globalAccess.execute(addr);
        }

        @Specialization
        protected Object doLLVMBoxedPrimitive(LLVMBoxedPrimitive addr,
                        @Cached("getLLVMMemory()") LLVMMemory memory) {
            if (addr.getValue() instanceof Long) {
                return memory.getAddress((long) addr.getValue());
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalAccessError("Cannot access memory with address: " + addr.getValue());
            }
        }

        @Specialization
        protected Object doIndirectedForeign(LLVMTruffleObject addr,
                        @Cached("createForeignReadNode()") LLVMForeignReadNode foreignRead) {
            return foreignRead.execute(addr);
        }

        protected LLVMForeignReadNode createForeignReadNode() {
            return new LLVMForeignReadNode(ForeignToLLVMType.POINTER);
        }
    }

    public static final class LLVMGlobalDirectLoadNode extends LLVMExpressionNode {

        protected final LLVMGlobal descriptor;
        @Child private ReadObjectNode access = ReadObjectNode.create();

        public LLVMGlobalDirectLoadNode(LLVMGlobal descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return access.execute(descriptor);
        }
    }

    public abstract static class LLVMStructDirectLoadNode extends LLVMLoadNode {

        @Specialization
        protected LLVMAddress doAddress(LLVMAddress addr) {
            return addr; // we do not actually load the struct into a virtual register
        }

        @Specialization
        protected LLVMTruffleObject doTruffleObject(LLVMTruffleObject addr) {
            return addr; // we do not actually load the struct into a virtual register
        }
    }
}
