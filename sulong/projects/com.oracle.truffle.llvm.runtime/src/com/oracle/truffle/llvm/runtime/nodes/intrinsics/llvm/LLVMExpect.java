/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class LLVMExpect {

    private LLVMExpect() {
        // private constructor
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "val")
    public abstract static class LLVMExpectI1 extends LLVMBuiltin {

        private final ConditionProfile expectProfile = getExpectConditionProfile();

        private final boolean expected;

        public LLVMExpectI1(boolean expected) {
            this.expected = expected;
        }

        @Specialization
        protected boolean doI1(boolean val) {
            if (expectProfile.profile(val == expected)) {
                return expected;
            } else {
                return val;
            }
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "val")
    public abstract static class LLVMExpectI32 extends LLVMBuiltin {

        private final ConditionProfile expectProfile = getExpectConditionProfile();

        private final int expected;

        public LLVMExpectI32(int expected) {
            this.expected = expected;
        }

        @Specialization
        protected int doI32(int val) {
            if (expectProfile.profile(val == expected)) {
                return expected;
            } else {
                return val;
            }
        }
    }

    @NodeChild(type = LLVMExpressionNode.class, value = "val")
    public abstract static class LLVMExpectI64 extends LLVMBuiltin {

        private final ConditionProfile expectProfile = getExpectConditionProfile();

        private final long expected;

        public LLVMExpectI64(long expected) {
            this.expected = expected;
        }

        @Specialization
        protected long doI64(long val) {
            if (expectProfile.profile(val == expected)) {
                return expected;
            } else {
                return val;
            }
        }
    }

    static ConditionProfile getExpectConditionProfile() {
        return ConditionProfile.create();
    }
}
