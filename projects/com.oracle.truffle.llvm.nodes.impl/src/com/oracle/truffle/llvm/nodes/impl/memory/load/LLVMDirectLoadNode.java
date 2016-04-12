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
package com.oracle.truffle.llvm.nodes.impl.memory.load;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionRegistry;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMIVarBitNode;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.types.LLVMIVarBit;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

public abstract class LLVMDirectLoadNode {

    @NodeChild(type = LLVMAddressNode.class)
    @NodeField(name = "bitWidth", type = int.class)
    public abstract static class LLVMIVarBitDirectLoadNode extends LLVMIVarBitNode {

        public abstract int getBitWidth();

        @Specialization
        public LLVMIVarBit executeI64(LLVMAddress addr) {
            return LLVMMemory.getIVarBit(addr, getBitWidth());
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVM80BitFloatDirectLoadNode extends LLVM80BitFloatNode {

        @Specialization
        public LLVM80BitFloat executeDouble(LLVMAddress addr) {
            return LLVMMemory.get80BitFloat(addr);
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    @NodeField(type = LLVMFunctionRegistry.class, name = "functionRegistry")
    public abstract static class LLVMFunctionDirectLoadNode extends LLVMFunctionNode {

        public abstract LLVMFunctionRegistry getFunctionRegistry();

        @Specialization
        public LLVMFunctionDescriptor executeAddress(LLVMAddress addr) {
            return getFunctionRegistry().createFromIndex(LLVMHeap.getFunctionIndex(addr));
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMAddressDirectLoadNode extends LLVMAddressNode {

        @Specialization
        public LLVMAddress executeAddress(LLVMAddress addr) {
            return LLVMMemory.getAddress(addr);
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMStructDirectLoadNode extends LLVMAddressNode {

        @Specialization
        public LLVMAddress executeAddress(LLVMAddress addr) {
            return addr; // we do not actually load the struct into a virtual register
        }
    }

}
