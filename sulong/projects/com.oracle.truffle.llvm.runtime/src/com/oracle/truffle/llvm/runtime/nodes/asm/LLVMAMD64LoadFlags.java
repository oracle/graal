/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.asm;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.CountingConditionProfile;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64Flags;

public abstract class LLVMAMD64LoadFlags extends LLVMExpressionNode {
    @NodeChild(value = "cf", type = LLVMExpressionNode.class)
    @NodeChild(value = "pf", type = LLVMExpressionNode.class)
    @NodeChild(value = "af", type = LLVMExpressionNode.class)
    @NodeChild(value = "zf", type = LLVMExpressionNode.class)
    @NodeChild(value = "sf", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64LahfNode extends LLVMAMD64LoadFlags {
        @Specialization
        protected byte doI8(boolean cf, boolean pf, boolean af, boolean zf, boolean sf,
                        @Cached CountingConditionProfile profileCF,
                        @Cached CountingConditionProfile profilePF,
                        @Cached CountingConditionProfile profileAF,
                        @Cached CountingConditionProfile profileZF,
                        @Cached CountingConditionProfile profileSF) {
            byte flags = 0;
            if (profileCF.profile(cf)) {
                flags |= (byte) (1 << LLVMAMD64Flags.CF);
            }
            if (profilePF.profile(pf)) {
                flags |= (byte) (1 << LLVMAMD64Flags.PF);
            }
            if (profileAF.profile(af)) {
                flags |= (byte) (1 << LLVMAMD64Flags.AF);
            }
            if (profileZF.profile(zf)) {
                flags |= (byte) (1 << LLVMAMD64Flags.ZF);
            }
            if (profileSF.profile(sf)) {
                flags |= (byte) (1 << LLVMAMD64Flags.SF);
            }
            return flags;
        }
    }

    @NodeChild(value = "cf", type = LLVMExpressionNode.class)
    @NodeChild(value = "pf", type = LLVMExpressionNode.class)
    @NodeChild(value = "af", type = LLVMExpressionNode.class)
    @NodeChild(value = "zf", type = LLVMExpressionNode.class)
    @NodeChild(value = "sf", type = LLVMExpressionNode.class)
    @NodeChild(value = "of", type = LLVMExpressionNode.class)
    public abstract static class LLVMAMD64ReadFlagswNode extends LLVMAMD64LoadFlags {
        private final CountingConditionProfile profileOF = CountingConditionProfile.create();

        @Specialization
        protected short doI16(boolean cf, boolean pf, boolean af, boolean zf, boolean sf, boolean of,
                        @Cached CountingConditionProfile profileCF,
                        @Cached CountingConditionProfile profilePF,
                        @Cached CountingConditionProfile profileAF,
                        @Cached CountingConditionProfile profileZF,
                        @Cached CountingConditionProfile profileSF) {
            short flags = 0;
            if (profileCF.profile(cf)) {
                flags |= (short) (1 << LLVMAMD64Flags.CF);
            }
            if (profilePF.profile(pf)) {
                flags |= (short) (1 << LLVMAMD64Flags.PF);
            }
            if (profileAF.profile(af)) {
                flags |= (short) (1 << LLVMAMD64Flags.AF);
            }
            if (profileZF.profile(zf)) {
                flags |= (short) (1 << LLVMAMD64Flags.ZF);
            }
            if (profileSF.profile(sf)) {
                flags |= (short) (1 << LLVMAMD64Flags.SF);
            }
            if (profileOF.profile(of)) {
                flags |= (short) (1 << LLVMAMD64Flags.OF);
            }
            return flags;
        }
    }
}
