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
package com.oracle.truffle.llvm.nodes.memory;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMIVarBitNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMLoadNodeFactory.LLVMLoadI32NodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMLoadNodeFactory.LLVMLoadI64NodeGen;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.LLVMIVarBit;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.types.memory.LLVMHeap;
import com.oracle.truffle.llvm.types.memory.LLVMMemory;

public abstract class LLVMLoadNode {

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMLoadI1Node extends LLVMI1Node {

        @Specialization
        public boolean executeI1(LLVMAddress addr) {
            return LLVMMemory.getI1(addr);
        }

    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMLoadI8Node extends LLVMI8Node {

        @Specialization
        public byte executeI8(LLVMAddress addr) {
            return LLVMMemory.getI8(addr);
        }

    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMLoadI16Node extends LLVMI16Node {

        @Specialization
        public short executeI16(LLVMAddress addr) {
            return LLVMMemory.getI16(addr);
        }

    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMLoadI32Node extends LLVMI32Node {

        @Specialization
        public int executeI32(LLVMAddress addr) {
            return LLVMMemory.getI32(addr);
        }

    }

    public static class LLVMUninitializedLoadI32Node extends LLVMI32Node {

        @Child private LLVMAddressNode addressNode;

        public LLVMUninitializedLoadI32Node(LLVMAddressNode addressNode) {
            this.addressNode = addressNode;
        }

        @Override
        public int executeI32(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreter();
            int val = LLVMMemory.getI32(addressNode.executePointee(frame));
            replace(new LLVMLoadValueProfiledI32Node(addressNode, val));
            return val;
        }

    }

    public static class LLVMLoadValueProfiledI32Node extends LLVMI32Node {

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
                CompilerDirectives.transferToInterpreter();
                replace(LLVMLoadI32NodeGen.create(addressNode));
                return value;
            }
        }

    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMLoadI64Node extends LLVMI64Node {

        @Specialization
        public long executeI64(LLVMAddress addr) {
            return LLVMMemory.getI64(addr);
        }
    }

    public static class LLVMUninitializedLoadI64Node extends LLVMI64Node {

        @Child private LLVMAddressNode addressNode;

        public LLVMUninitializedLoadI64Node(LLVMAddressNode addressNode) {
            this.addressNode = addressNode;
        }

        @Override
        public long executeI64(VirtualFrame frame) {
            CompilerDirectives.transferToInterpreter();
            long val = LLVMMemory.getI64(addressNode.executePointee(frame));
            replace(new LLVMLoadValueProfiledI64Node(addressNode, val));
            return val;
        }

    }

    public static class LLVMLoadValueProfiledI64Node extends LLVMI64Node {

        private final long profiledValue;
        @Child private LLVMAddressNode addressNode;

        public LLVMLoadValueProfiledI64Node(LLVMAddressNode addressNode, long profiledValue) {
            this.addressNode = addressNode;
            this.profiledValue = profiledValue;
        }

        @Override
        public long executeI64(VirtualFrame frame) {
            long value = LLVMMemory.getI64(addressNode.executePointee(frame));
            if (value == profiledValue) {
                return profiledValue;
            } else {
                CompilerDirectives.transferToInterpreter();
                replace(LLVMLoadI64NodeGen.create(addressNode));
                return value;
            }
        }

    }

    @NodeChild(type = LLVMAddressNode.class)
    @NodeField(name = "bitWidth", type = int.class)
    public abstract static class LLVMLoadIVarBitNode extends LLVMIVarBitNode {

        public abstract int getBitWidth();

        @Specialization
        public LLVMIVarBit executeI64(LLVMAddress addr) {
            return LLVMMemory.getIVarBit(addr, getBitWidth());
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMLoadFloatNode extends LLVMFloatNode {

        @Specialization
        public float executeFloat(LLVMAddress addr) {
            return LLVMMemory.getFloat(addr);
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMLoad80BitFloatNode extends LLVM80BitFloatNode {

        @Specialization
        public LLVM80BitFloat executeDouble(LLVMAddress addr) {
            return LLVMMemory.get80BitFloat(addr);
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMLoadDoubleNode extends LLVMDoubleNode {

        @Specialization
        public double executeDouble(LLVMAddress addr) {
            return LLVMMemory.getDouble(addr);
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMLoadFunctionNode extends LLVMFunctionNode {

        @Specialization
        public LLVMFunction executeAddress(LLVMAddress addr) {
            return LLVMHeap.getFunction(addr);
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMLoadAddressNode extends LLVMAddressNode {

        @Specialization
        public LLVMAddress executeAddress(LLVMAddress addr) {
            return LLVMMemory.getAddress(addr);
        }
    }

    @NodeChild(type = LLVMAddressNode.class)
    public abstract static class LLVMLoadStructNode extends LLVMAddressNode {

        @Specialization
        public LLVMAddress executeAddress(LLVMAddress addr) {
            return addr; // we do not actually load the struct into a virtual register
        }
    }

}
