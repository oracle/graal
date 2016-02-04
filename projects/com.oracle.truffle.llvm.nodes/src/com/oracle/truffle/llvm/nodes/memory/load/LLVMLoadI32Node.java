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
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadI32NodeFactory.LLVMLoadDirectI32NodeGen;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

public abstract class LLVMLoadI32Node extends LLVMI32Node {

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMLoadDirectI32Node extends LLVMLoadI32Node {

        @Specialization
        public int executeI32(LLVMAddress addr) {
            return LLVMMemory.getI32(addr);
        }

    }

    public static class LLVMUninitializedLoadI32Node extends LLVMLoadI32Node {

        @Child private LLVMAddressNode addressNode;

        public LLVMUninitializedLoadI32Node(LLVMAddressNode addressNode) {
            this.addressNode = addressNode;
        }

        @Override
        public int executeI32(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            int val = LLVMMemory.getI32(addressNode.executePointee(frame));
            replace(new LLVMLoadValueProfiledI32Node(addressNode, val));
            return val;
        }

    }

    public static class LLVMLoadValueProfiledI32Node extends LLVMLoadI32Node {

        private final int profiledValue;
        @Child private LLVMAddressNode addressNode;

        public LLVMLoadValueProfiledI32Node(LLVMAddressNode addressNode, int profiledValue) {
            this.addressNode = addressNode;
            this.profiledValue = profiledValue;
        }

        @Override
        public int executeI32(VirtualFrame frame) {
            int value = LLVMMemory.getI32(addressNode.executePointee(frame));
            if (value == profiledValue) {
                return profiledValue;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                replace(LLVMLoadDirectI32NodeGen.create(addressNode));
                return value;
            }
        }

    }

}
