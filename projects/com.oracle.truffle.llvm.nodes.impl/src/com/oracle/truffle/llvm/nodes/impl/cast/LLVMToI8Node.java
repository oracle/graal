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
package com.oracle.truffle.llvm.nodes.impl.cast;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMIVarBitNode;
import com.oracle.truffle.llvm.types.LLVMAddress;
import com.oracle.truffle.llvm.types.LLVMIVarBit;
import com.oracle.truffle.llvm.types.floating.LLVM80BitFloat;

public abstract class LLVMToI8Node extends LLVMI8Node {

    @NodeChild(value = "fromNode", type = LLVMI1Node.class)
    public abstract static class LLVMI1ToI8Node extends LLVMToI8Node {

        @Specialization
        public byte executeI8(boolean from) {
            return from ? (byte) -1 : 0;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI1Node.class)
    public abstract static class LLVMI1ToI8ZeroExtNode extends LLVMToI8Node {

        @Specialization
        public byte executeI8(boolean from) {
            return (byte) (from ? 1 : 0);
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI16Node.class)
    public abstract static class LLVMI16ToI8Node extends LLVMToI8Node {

        @Specialization
        public byte executeI8(short from) {
            return (byte) from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI32Node.class)
    public abstract static class LLVMI32ToI8Node extends LLVMToI8Node {

        @Specialization
        public byte executeI8(int from) {
            return (byte) from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI64Node.class)
    public abstract static class LLVMI64ToI8Node extends LLVMToI8Node {

        @Specialization
        public byte executeI8(long from) {
            return (byte) from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMIVarBitNode.class)
    public abstract static class LLVMIVarToI8Node extends LLVMToI8Node {

        @Specialization
        public byte executeI8(LLVMIVarBit from) {
            return from.getByteValue();
        }
    }

    @NodeChild(value = "fromNode", type = LLVMFloatNode.class)
    public abstract static class LLVMFloatToI8Node extends LLVMToI8Node {

        @Specialization
        public byte executeI8(float from) {
            return (byte) from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMDoubleNode.class)
    public abstract static class LLVMDoubleToI8Node extends LLVMToI8Node {

        @Specialization
        public byte executeI8(double from) {
            return (byte) from;
        }
    }

    @NodeChild(value = "fromNode", type = LLVM80BitFloatNode.class)
    public abstract static class LLVM80BitFloatToI8Node extends LLVMToI8Node {

        @Specialization
        public byte executeI8(LLVM80BitFloat from) {
            return from.getByteValue();
        }
    }

    @NodeChild(value = "fromNode", type = LLVMAddressNode.class)
    public abstract static class LLVMAddressToI8Node extends LLVMToI8Node {

        @Specialization
        public byte executeI8(LLVMAddress from) {
            return (byte) from.getVal();
        }
    }

}
