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
package com.oracle.truffle.llvm.intrinsics.c;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.LLVMIntrinsic.LLVMDoubleIntrinsic;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMDoubleNode;

/**
 * Implements the C functions from math.h.
 */
public abstract class LLVMCMathsIntrinsics {

    @NodeChild(type = LLVMDoubleNode.class)
    public abstract static class LLVMACos extends LLVMDoubleIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.acos(value);
        }
    }

    @NodeChild(type = LLVMDoubleNode.class)
    public abstract static class LLVMASin extends LLVMDoubleIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.asin(value);
        }
    }

    @NodeChild(type = LLVMDoubleNode.class)
    public abstract static class LLVMATan extends LLVMDoubleIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.atan(value);
        }
    }

    @NodeChild(type = LLVMDoubleNode.class)
    public abstract static class LLVMCos extends LLVMDoubleIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.cos(value);
        }

    }

    @NodeChild(type = LLVMDoubleNode.class)
    public abstract static class LLVMExp extends LLVMDoubleIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.exp(value);
        }
    }

    @NodeChild(type = LLVMDoubleNode.class)
    public abstract static class LLVMLog extends LLVMDoubleIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.log(value);
        }
    }

    @NodeChild(type = LLVMDoubleNode.class)
    public abstract static class LLVMSqrt extends LLVMDoubleIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.sqrt(value);
        }

    }

    @NodeChild(type = LLVMDoubleNode.class)
    public abstract static class LLVMSin extends LLVMDoubleIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.sin(value);
        }

    }

    @NodeChild(type = LLVMDoubleNode.class)
    public abstract static class LLVMTan extends LLVMDoubleIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.tan(value);
        }

    }

    @NodeChild(type = LLVMDoubleNode.class)
    public abstract static class LLVMTanh extends LLVMDoubleIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.tanh(value);
        }

    }

}
