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
package com.oracle.truffle.llvm.nodes.intrinsics.c;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeChildren;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

/**
 * Implements the C functions from math.h.
 */
public abstract class LLVMCMathsIntrinsics {

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMSqrt extends LLVMIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.sqrt(value);
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLog extends LLVMIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.log(value);
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLog10 extends LLVMIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.log10(value);
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMRint extends LLVMIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.rint(value);
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMCeil extends LLVMIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.ceil(value);
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMFloor extends LLVMIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.floor(value);
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMAbs extends LLVMIntrinsic {

        @Specialization
        public int executeIntrinsic(int value) {
            return Math.abs(value);
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMFAbs extends LLVMIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.abs(value);
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMExp extends LLVMIntrinsic {

        @Specialization
        public double executeIntrinsic(double value) {
            return Math.exp(value);
        }

    }

    @NodeChildren({@NodeChild(type = LLVMExpressionNode.class), @NodeChild(type = LLVMExpressionNode.class)})
    public abstract static class LLVMPow extends LLVMIntrinsic {

        @Specialization
        public double executeIntrinsic(double a, double b) {
            return Math.pow(a, b);
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMLAbs extends LLVMIntrinsic {

        @Specialization
        public long executeIntrinsic(long value) {
            return Math.abs(value);
        }

    }

    @NodeChild(type = LLVMExpressionNode.class)
    public abstract static class LLVMToUpper extends LLVMIntrinsic {

        @Specialization
        @TruffleBoundary
        public int executeIntrinsic(int value) {
            return Character.toUpperCase(value);
        }

    }

}
