/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.memory.UnsafeArrayAccess;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMDirectLoadNode {

    @NodeField(name = "bitWidth", type = int.class)
    public abstract static class LLVMIVarBitDirectLoadNode extends LLVMAbstractLoadNode {

        public abstract int getBitWidth();

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVMIVarBit doIVarBitNative(LLVMNativePointer addr) {
            return getLLVMMemoryCached().getIVarBit(addr, getBitWidth());
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected Object doIVarBitDerefHandle(LLVMNativePointer addr) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr));
        }

        @Specialization
        protected Object doForeign(LLVMManagedPointer addr) {
            byte[] result = new byte[getByteSize()];
            LLVMManagedPointer currentPtr = addr;
            for (int i = result.length - 1; i >= 0; i--) {
                result[i] = (Byte) getForeignReadNode().executeRead(currentPtr.getObject(), currentPtr.getOffset(), ForeignToLLVMType.I8);
                currentPtr = currentPtr.increment(I8_SIZE_IN_BYTES);
            }
            return LLVMIVarBit.create(getBitWidth(), result, getBitWidth(), false);
        }

        private int getByteSize() {
            assert getBitWidth() % Byte.SIZE == 0;
            return getBitWidth() / Byte.SIZE;
        }
    }

    public abstract static class LLVM80BitFloatDirectLoadNode extends LLVMAbstractLoadNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVM80BitFloat do80BitFloatNative(LLVMNativePointer addr) {
            return getLLVMMemoryCached().get80BitFloat(addr);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected Object do80BitFloatDerefHandle(LLVMNativePointer addr) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr));
        }

        @Specialization
        @ExplodeLoop
        protected LLVM80BitFloat doForeign(LLVMManagedPointer addr) {
            byte[] result = new byte[LLVM80BitFloat.BYTE_WIDTH];
            LLVMManagedPointer currentPtr = addr;
            for (int i = 0; i < result.length; i++) {
                result[i] = (byte) getForeignReadNode().executeRead(currentPtr.getObject(), currentPtr.getOffset(), ForeignToLLVMType.I8);
                currentPtr = currentPtr.increment(I8_SIZE_IN_BYTES);
            }
            return LLVM80BitFloat.fromBytes(result);
        }
    }

    public abstract static class LLVMPointerDirectLoadNode extends LLVMAbstractLoadNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVMNativePointer doNativePointer(LLVMNativePointer addr) {
            return getLLVMMemoryCached().getPointer(addr);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected Object doDerefHandle(LLVMNativePointer addr) {
            return doIndirectedForeign(getDerefHandleGetReceiverNode().execute(addr));
        }

        @Specialization
        protected LLVMNativePointer doLLVMByteArrayAddress(LLVMVirtualAllocationAddress address,
                        @Cached("getUnsafeArrayAccess()") UnsafeArrayAccess memory) {
            return LLVMNativePointer.create(address.getI64(memory));
        }

        @Specialization
        protected Object doIndirectedForeign(LLVMManagedPointer addr) {
            return getForeignReadNode().executeRead(addr.getObject(), addr.getOffset(), ForeignToLLVMType.POINTER);
        }
    }

    public abstract static class LLVMStructDirectLoadNode extends LLVMAbstractLoadNode {

        @Specialization
        protected LLVMPointer doPointer(LLVMPointer addr) {
            return addr; // we do not actually load the struct into a virtual register
        }
    }
}
