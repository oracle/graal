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
package com.oracle.truffle.llvm.nodes.memory;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMCompareExchangeNodeGen.LLVMCMPXCHInternalNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.LLVMGlobalVariableDescriptor;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

@NodeChildren({@NodeChild(type = LLVMExpressionNode.class, value = "address"), @NodeChild(type = LLVMExpressionNode.class, value = "comparisonValue"),
                @NodeChild(type = LLVMExpressionNode.class, value = "newValue")})
public abstract class LLVMCompareExchangeNode extends LLVMExpressionNode {

    @Child private LLVMCMPXCHInternalNode cmpxch = LLVMCMPXCHInternalNodeGen.create();

    abstract static class LLVMCMPXCHInternalNode extends LLVMNode {

        public abstract Object executeWithTarget(LLVMAddress address, Object cmpValue, Object newValue);

        @Specialization
        public Object execute(LLVMAddress address, byte comparisonValue, byte newValue) {
            byte value = LLVMMemory.getI8(address);
            if (value == comparisonValue) {
                LLVMMemory.putI8(address, newValue);
            }
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, short comparisonValue, short newValue) {
            short value = LLVMMemory.getI16(address);
            if (value == comparisonValue) {
                LLVMMemory.putI16(address, newValue);
            }
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, int comparisonValue, int newValue) {
            int value = LLVMMemory.getI32(address);
            if (value == comparisonValue) {
                LLVMMemory.putI32(address, newValue);
            }
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, long comparisonValue, long newValue) {
            long value = LLVMMemory.getI64(address);
            if (value == comparisonValue) {
                LLVMMemory.putI64(address, newValue);
            }
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, float comparisonValue, float newValue) {
            float value = LLVMMemory.getFloat(address);
            if (value == comparisonValue) {
                LLVMMemory.putFloat(address, newValue);
            }
            return null;
        }

        @Specialization
        public Object execute(LLVMAddress address, double comparisonValue, double newValue) {
            double value = LLVMMemory.getDouble(address);
            if (value == comparisonValue) {
                LLVMMemory.putDouble(address, newValue);
            }
            return null;
        }
    }

    @Specialization
    public Object execute(LLVMAddress address, Object comparisonValue, Object newValue) {
        return cmpxch.executeWithTarget(address, comparisonValue, newValue);
    }

    @Specialization
    public Object execute(LLVMGlobalVariableDescriptor address, Object comparisonValue, Object newValue) {
        return cmpxch.executeWithTarget(address.getNativeAddress(), comparisonValue, newValue);
    }

}
