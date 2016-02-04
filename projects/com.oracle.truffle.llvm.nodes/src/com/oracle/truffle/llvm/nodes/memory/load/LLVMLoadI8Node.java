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
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadI8NodeFactory.LLVMLoadDirectI8NodeGen;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

public abstract class LLVMLoadI8Node extends LLVMI8Node {

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMLoadDirectI8Node extends LLVMLoadI8Node {

        @Specialization
        public byte executeI8(LLVMAddress addr) {
            return LLVMMemory.getI8(addr);
        }

    }

    public static class LLVMUninitializedLoadI8Node extends LLVMLoadI8Node {

        @Child private LLVMAddressNode addressNode;

        public LLVMUninitializedLoadI8Node(LLVMAddressNode addressNode) {
            this.addressNode = addressNode;
        }

        @Override
        public byte executeI8(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            byte val = LLVMMemory.getI8(addressNode.executePointee(frame));
            replace(new LLVMLoadValueProfiledI8Node(addressNode, val));
            return val;
        }

    }

    public static class LLVMLoadValueProfiledI8Node extends LLVMLoadI8Node {

        private final byte profiledValue;
        @Child private LLVMAddressNode addressNode;

        public LLVMLoadValueProfiledI8Node(LLVMAddressNode addressNode, byte profiledValue) {
            this.addressNode = addressNode;
            this.profiledValue = profiledValue;
        }

        @Override
        public byte executeI8(VirtualFrame frame) {
            byte value = LLVMMemory.getI8(addressNode.executePointee(frame));
            if (value == profiledValue) {
                return profiledValue;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                replace(LLVMLoadDirectI8NodeGen.create(addressNode));
                return value;
            }
        }

    }

}
