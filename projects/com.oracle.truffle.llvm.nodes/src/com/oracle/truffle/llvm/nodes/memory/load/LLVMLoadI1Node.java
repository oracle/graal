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
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadI1NodeFactory.LLVMLoadDirectI1NodeGen;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

public abstract class LLVMLoadI1Node extends LLVMI1Node {

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMLoadDirectI1Node extends LLVMLoadI1Node {

        @Specialization
        public boolean executeI1(LLVMAddress addr) {
            return LLVMMemory.getI1(addr);
        }

    }

    public static class LLVMUninitializedLoadI1Node extends LLVMLoadI1Node {

        @Child private LLVMAddressNode addressNode;

        public LLVMUninitializedLoadI1Node(LLVMAddressNode addressNode) {
            this.addressNode = addressNode;
        }

        @Override
        public boolean executeI1(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            boolean val = LLVMMemory.getI1(addressNode.executePointee(frame));
            replace(new LLVMLoadValueProfiledI1Node(addressNode, val));
            return val;
        }

    }

    public static class LLVMLoadValueProfiledI1Node extends LLVMLoadI1Node {

        private final boolean profiledValue;
        @Child private LLVMAddressNode addressNode;

        public LLVMLoadValueProfiledI1Node(LLVMAddressNode addressNode, boolean profiledValue) {
            this.addressNode = addressNode;
            this.profiledValue = profiledValue;
        }

        @Override
        public boolean executeI1(VirtualFrame frame) {
            boolean value = LLVMMemory.getI1(addressNode.executePointee(frame));
            if (value == profiledValue) {
                return profiledValue;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                replace(LLVMLoadDirectI1NodeGen.create(addressNode));
                return value;
            }
        }

    }

}
