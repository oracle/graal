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
package com.oracle.truffle.llvm.nodes.memory.load;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.nodes.intrinsics.interop.LLVMTruffleManagedMalloc.ManagedMallocObject;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMBoxedPrimitive;
import com.oracle.truffle.llvm.runtime.LLVMVirtualAllocationAddress;
import com.oracle.truffle.llvm.runtime.LLVMFunctionHandle;
import com.oracle.truffle.llvm.runtime.LLVMIVarBit;
import com.oracle.truffle.llvm.runtime.LLVMTruffleObject;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariable;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.memory.LLVMHeap;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class LLVMDirectLoadNode {

    @NodeChild(type = LLVMExpressionNode.class)
    @NodeField(name = "bitWidth", type = int.class)
    public abstract static class LLVMIVarBitDirectLoadNode extends LLVMExpressionNode {

        public abstract int getBitWidth();

        @Specialization
        public LLVMIVarBit executeI64(LLVMAddress addr) {
            return LLVMMemory.getIVarBit(addr, getBitWidth());
        }

        @Specialization
        public LLVMIVarBit executeI64(LLVMGlobalVariable addr, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            return LLVMMemory.getIVarBit(globalAccess.getNativeLocation(addr), getBitWidth());
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVM80BitFloatDirectLoadNode extends LLVMExpressionNode {

        @Specialization
        public LLVM80BitFloat executeDouble(LLVMAddress addr) {
            return LLVMMemory.get80BitFloat(addr);
        }

        @Specialization
        public LLVM80BitFloat executeDouble(LLVMGlobalVariable addr, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            return LLVMMemory.get80BitFloat(globalAccess.getNativeLocation(addr));
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMFunctionDirectLoadNode extends LLVMExpressionNode {

        @Specialization
        public LLVMFunctionHandle executeAddress(LLVMAddress addr) {
            return LLVMFunctionHandle.createHandle(LLVMHeap.getFunctionPointer(addr));
        }

        @Specialization
        public LLVMFunctionHandle executeAddress(LLVMGlobalVariable addr, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            return LLVMFunctionHandle.createHandle(LLVMHeap.getFunctionPointer(globalAccess.getNativeLocation(addr)));
        }
    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMAddressDirectLoadNode extends LLVMExpressionNode {

        @Child protected ForeignToLLVM toLLVM = ForeignToLLVM.create(ForeignToLLVMType.POINTER);

        @Specialization
        public LLVMAddress executeAddress(LLVMAddress addr) {
            return LLVMMemory.getAddress(addr);
        }

        @Specialization
        public LLVMAddress executeLLVMByteArrayAddress(LLVMVirtualAllocationAddress address) {
            return LLVMAddress.fromLong(address.getI64());
        }

        @Specialization
        public Object executeAddress(LLVMGlobalVariable addr, @Cached("createGlobalAccess()") LLVMGlobalVariableAccess globalAccess) {
            return globalAccess.get(addr);
        }

        @Specialization
        public Object executeManagedMalloc(ManagedMallocObject addr) {
            return addr.get(0);
        }

        @Specialization
        public Object executeLLVMBoxedPrimitive(LLVMBoxedPrimitive addr) {
            if (addr.getValue() instanceof Long) {
                return LLVMMemory.getAddress((long) addr.getValue());
            } else {
                CompilerDirectives.transferToInterpreter();
                throw new IllegalAccessError("Cannot access memory with address: " + addr.getValue());
            }
        }

        @Specialization(guards = "objectIsManagedMalloc(addr)")
        public Object executeIndirectedManagedMalloc(LLVMTruffleObject addr) {
            return toLLVM.executeWithTarget(((ManagedMallocObject) addr.getObject()).get((int) (addr.getOffset() / LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES)));
        }

        @Specialization(guards = "!objectIsManagedMalloc(addr)")
        public Object executeIndirectedForeign(LLVMTruffleObject addr, @Cached("createForeignReadNode()") Node foreignRead) {
            try {
                return toLLVM.executeWithTarget(ForeignAccess.sendRead(foreignRead, addr.getObject(), (int) (addr.getOffset() / LLVMExpressionNode.ADDRESS_SIZE_IN_BYTES)));
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new UnsupportedOperationException(e);
            }
        }

        protected boolean objectIsManagedMalloc(LLVMTruffleObject addr) {
            return addr.getObject() instanceof ManagedMallocObject;
        }

        protected Node createForeignReadNode() {
            return Message.READ.createNode();
        }
    }

    public static final class LLVMGlobalVariableDirectLoadNode extends LLVMExpressionNode {

        protected final LLVMGlobalVariable descriptor;
        @Child private LLVMGlobalVariableAccess access = createGlobalAccess();

        public LLVMGlobalVariableDirectLoadNode(LLVMGlobalVariable descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            return access.get(descriptor);
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMStructDirectLoadNode extends LLVMExpressionNode {

        @Specialization
        public LLVMAddress executeAddress(LLVMAddress addr) {
            return addr; // we do not actually load the struct into a virtual register
        }
    }

}
