/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.memory.UnsafeArrayAccess;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMToNativeNode;

public abstract class LLVMDirectLoadNode {

    @NodeField(name = "bitWidth", type = int.class)
    public abstract static class LLVMIVarBitDirectLoadNode extends LLVMAbstractLoadNode {

        public abstract int getBitWidth();

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVMIVarBit doI64(LLVMAddress addr) {
            return getLLVMMemoryCached().getIVarBit(addr, getBitWidth());
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected Object doI64DerefHandle(LLVMAddress addr) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr));
        }

        @Specialization
        protected LLVMIVarBit doI64(LLVMGlobal addr,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
            return getLLVMMemoryCached().getIVarBit(globalAccess.executeWithTarget(addr), getBitWidth());
        }

        @Specialization(guards = "addr.isNative()")
        protected Object doI64(LLVMTruffleObject addr) {
            return doI64(addr.asNative());
        }

        @Specialization(guards = "addr.isManaged()")
        protected Object doForeign(LLVMTruffleObject addr) {
            byte[] result = new byte[getByteSize()];
            LLVMTruffleObject currentPtr = addr;
            for (int i = result.length - 1; i >= 0; i--) {
                result[i] = (Byte) getForeignReadNode().execute(currentPtr);
                currentPtr = currentPtr.increment(I8_SIZE_IN_BYTES);
            }
            return LLVMIVarBit.create(getBitWidth(), result, getBitWidth(), false);
        }

        @Override
        protected LLVMForeignReadNode createForeignRead() {
            return new LLVMForeignReadNode(ForeignToLLVMType.I8);
        }

        private int getByteSize() {
            assert getBitWidth() % Byte.SIZE == 0;
            return getBitWidth() / Byte.SIZE;
        }
    }

    public abstract static class LLVM80BitFloatDirectLoadNode extends LLVMAbstractLoadNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVM80BitFloat doDouble(LLVMAddress addr) {
            return getLLVMMemoryCached().get80BitFloat(addr);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected Object doDoubleDerefHandle(LLVMAddress addr) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr));
        }

        @Specialization
        protected LLVM80BitFloat doDouble(LLVMGlobal addr,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
            return getLLVMMemoryCached().get80BitFloat(globalAccess.executeWithTarget(addr));
        }

        @Specialization(guards = "addr.isNative()")
        protected LLVM80BitFloat doDouble(LLVMTruffleObject addr) {
            return doDouble(addr.asNative());
        }

        @Specialization(guards = "addr.isManaged()")
        protected LLVM80BitFloat doForeign(LLVMTruffleObject addr) {
            byte[] result = new byte[LLVM80BitFloat.BYTE_WIDTH];
            LLVMTruffleObject currentPtr = addr;
            for (int i = 0; i < result.length; i++) {
                result[i] = (Byte) getForeignReadNode().execute(currentPtr);
                currentPtr = currentPtr.increment(I8_SIZE_IN_BYTES);
            }
            return LLVM80BitFloat.fromBytes(result);
        }

        @Override
        protected LLVMForeignReadNode createForeignRead() {
            return new LLVMForeignReadNode(ForeignToLLVMType.I8);
        }
    }

    public abstract static class LLVMFunctionDirectLoadNode extends LLVMAbstractLoadNode {

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVMAddress doAddress(LLVMAddress addr) {
            return LLVMAddress.fromLong(getLLVMMemoryCached().getFunctionPointer(addr));
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected Object doAddressDerefHandle(LLVMAddress addr) {
            return doForeign(getDerefHandleGetReceiverNode().execute(addr));
        }

        @Specialization
        protected LLVMAddress doAddress(LLVMGlobal addr,
                        @Cached("createToNativeWithTarget()") LLVMToNativeNode globalAccess) {
            return LLVMAddress.fromLong(getLLVMMemoryCached().getFunctionPointer(globalAccess.executeWithTarget(addr)));
        }

        @Override
        LLVMForeignReadNode createForeignRead() {
            return new LLVMForeignReadNode(ForeignToLLVMType.POINTER);
        }

        @Specialization(guards = "addr.isNative()")
        protected Object doAddress(LLVMTruffleObject addr) {
            return doAddress(addr.asNative());
        }

        @Specialization(guards = "addr.isManaged()")
        protected Object doForeign(LLVMTruffleObject addr) {
            return getForeignReadNode().execute(addr);
        }
    }

    public abstract static class LLVMAddressDirectLoadNode extends LLVMAbstractLoadNode {

        @Child protected ForeignToLLVM toLLVM = ForeignToLLVM.create(ForeignToLLVMType.POINTER);

        @Specialization(guards = "!isAutoDerefHandle(addr)")
        protected LLVMAddress doAddress(LLVMAddress addr) {
            return getLLVMMemoryCached().getAddress(addr);
        }

        @Specialization(guards = "isAutoDerefHandle(addr)")
        protected Object doAddressDerefHandle(LLVMAddress addr) {
            return doIndirectedForeign(getDerefHandleGetReceiverNode().execute(addr));
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
        protected Object doLLVMBoxedPrimitive(LLVMBoxedPrimitive addr) {
            if (addr.getValue() instanceof Long) {
                return getLLVMMemoryCached().getAddress((long) addr.getValue());
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalAccessError("Cannot access memory with address: " + addr.getValue());
            }
        }

        @Specialization(guards = {"addr.isNative()", "!isAutoDerefHandle(addr.asNative())"})
        protected Object doAddress(LLVMTruffleObject addr) {
            return doAddress(addr.asNative());
        }

        @Specialization(guards = {"addr.isNative()", "isAutoDerefHandle(addr.asNative())"})
        protected Object doAddressDerefHandle(LLVMTruffleObject addr) {
            return doAddressDerefHandle(addr.asNative());
        }

        @Specialization(guards = "addr.isManaged()")
        protected Object doIndirectedForeign(LLVMTruffleObject addr) {
            return getForeignReadNode().execute(addr);
        }

        @Override
        protected LLVMForeignReadNode createForeignRead() {
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

    public abstract static class LLVMStructDirectLoadNode extends LLVMAbstractLoadNode {

        @Specialization
        protected LLVMAddress doAddress(LLVMAddress addr) {
            return addr; // we do not actually load the struct into a virtual register
        }

        @Specialization
        protected LLVMTruffleObject doTruffleObject(LLVMTruffleObject addr) {
            return addr; // we do not actually load the struct into a virtual register
        }

        @Override
        LLVMForeignReadNode createForeignRead() {
            throw new AssertionError("should not reach here");
        }
    }
}
