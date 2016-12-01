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
package com.oracle.truffle.llvm.nodes.cast;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI8Node;

public abstract class LLVMToI1Node {

    @NodeChild(value = "fromNode", type = LLVMI8Node.class)
    public abstract static class LLVMI8ToI1Node extends LLVMI1Node {

        @Specialization
        public boolean executeI1(byte from) {
            return (from & 1) != 0;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI16Node.class)
    public abstract static class LLVMI16ToI1Node extends LLVMI1Node {

        @Specialization
        public boolean executeI1(short from) {
            return (from & 1) != 0;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI32Node.class)
    public abstract static class LLVMI32ToI1Node extends LLVMI1Node {

        @Specialization
        public boolean executeI1(int from) {
            return (from & 1) != 0;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMI64Node.class)
    public abstract static class LLVMI64ToI1Node extends LLVMI1Node {

        @Specialization
        public boolean executeI1(long from) {
            return (from & 1) != 0;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMFloatNode.class)
    public abstract static class LLVMFloatToI1Node extends LLVMI1Node {

        @Specialization
        public boolean executeI1(float from) {
            return from != 0;
        }
    }

    @NodeChild(value = "fromNode", type = LLVMDoubleNode.class)
    public abstract static class LLVMDoubleToI1Node extends LLVMI1Node {

        @Specialization
        public boolean executeI1(double from) {
            return from != 0;
        }
    }

}
