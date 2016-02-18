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
package com.oracle.truffle.llvm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.types.LLVMAddress;

/**
 * This class is the entry point for every intrinsified (substituted) function.
 */
public abstract class LLVMIntrinsicRootNode extends RootNode {

    LLVMIntrinsicRootNode() {
        super(LLVMLanguage.class, null, new FrameDescriptor());
    }

    public abstract LLVMNode getNode();

    @Override
    public String toString() {
        return getNode().getClass().getSimpleName();
    }

    public static class LLVMIntrinsicVoidNode extends LLVMIntrinsicRootNode {

        @Child private LLVMNode node;

        public LLVMIntrinsicVoidNode(LLVMNode node) {
            this.node = node;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            node.executeVoid(frame);
            return null;
        }

        @Override
        public LLVMNode getNode() {
            return node;
        }
    }

    @NodeChild(type = LLVMI8Node.class, value = "node")
    public abstract static class LLVMIntrinsicI8Node extends LLVMIntrinsicRootNode {

        @Specialization
        public Object execute(byte val) {
            return val;
        }
    }

    @NodeChild(type = LLVMI16Node.class, value = "node")
    public abstract static class LLVMIntrinsicI16Node extends LLVMIntrinsicRootNode {

        @Specialization
        public Object execute(short val) {
            return val;
        }
    }

    @NodeChild(type = LLVMI32Node.class, value = "node")
    public abstract static class LLVMIntrinsicI32Node extends LLVMIntrinsicRootNode {

        @Specialization
        public Object execute(int val) {
            return val;
        }
    }

    @NodeChild(type = LLVMI64Node.class, value = "node")
    public abstract static class LLVMIntrinsicI64Node extends LLVMIntrinsicRootNode {

        @Specialization
        public Object execute(long value) {
            return value;
        }
    }

    @NodeChild(type = LLVMFloatNode.class, value = "node")
    public abstract static class LLVMIntrinsicFloatNode extends LLVMIntrinsicRootNode {

        @Specialization
        public Object execute(float val) {
            return val;
        }
    }

    @NodeChild(type = LLVMDoubleNode.class, value = "node")
    public abstract static class LLVMIntrinsicDoubleNode extends LLVMIntrinsicRootNode {

        @Specialization
        public Object execute(double val) {
            return val;
        }
    }

    @NodeChild(type = LLVMAddressNode.class, value = "node")
    public abstract static class LLVMIntrinsicAddressNode extends LLVMIntrinsicRootNode {

        @Specialization
        public Object execute(LLVMAddress value) {
            return value;
        }

    }

}
