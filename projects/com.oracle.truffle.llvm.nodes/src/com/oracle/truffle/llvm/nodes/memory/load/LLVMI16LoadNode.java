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
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI16LoadNodeFactory.LLVMI16DirectLoadNodeGen;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

// Truffle has no branch profiles for short
public abstract class LLVMI16LoadNode extends LLVMI16Node {

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMI16DirectLoadNode extends LLVMI16LoadNode {

        @Specialization
        public short executeI16(LLVMAddress addr) {
            return LLVMMemory.getI16(addr);
        }

    }

    public static class LLVMI16UninitializedLoadNode extends LLVMI16LoadNode {

        @Child private LLVMAddressNode addressNode;

        public LLVMI16UninitializedLoadNode(LLVMAddressNode addressNode) {
            this.addressNode = addressNode;
        }

        @Override
        public short executeI16(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            short val = LLVMMemory.getI16(addressNode.executePointee(frame));
            replace(new LLVMI16ProfilingLoadNode(addressNode, val));
            return val;
        }

    }

    public static class LLVMI16ProfilingLoadNode extends LLVMI16LoadNode {

        private final short profiledValue;
        @Child private LLVMAddressNode addressNode;

        public LLVMI16ProfilingLoadNode(LLVMAddressNode addressNode, short profiledValue) {
            this.addressNode = addressNode;
            this.profiledValue = profiledValue;
        }

        @Override
        public short executeI16(VirtualFrame frame) {
            short value = LLVMMemory.getI16(addressNode.executePointee(frame));
            if (value == profiledValue) {
                return profiledValue;
            } else {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                replace(LLVMI16DirectLoadNodeGen.create(addressNode));
                return value;
            }
        }

    }

}
