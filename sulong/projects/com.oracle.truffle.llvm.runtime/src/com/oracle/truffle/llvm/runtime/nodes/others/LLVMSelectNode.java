/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.others;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
@NodeChild(type = LLVMExpressionNode.class)
public abstract class LLVMSelectNode extends LLVMExpressionNode {
    protected final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

    public abstract static class LLVMI1SelectNode extends LLVMSelectNode {

        @Specialization
        protected boolean doOp(boolean cond, boolean trueBranch, boolean elseBranch) {
            return conditionProfile.profile(cond) ? trueBranch : elseBranch;
        }
    }

    public abstract static class LLVMI8SelectNode extends LLVMSelectNode {

        @Specialization
        protected byte doOp(boolean cond, byte trueBranch, byte elseBranch) {
            return conditionProfile.profile(cond) ? trueBranch : elseBranch;
        }
    }

    public abstract static class LLVMI16SelectNode extends LLVMSelectNode {

        @Specialization
        protected short doOp(boolean cond, short trueBranch, short elseBranch) {
            return conditionProfile.profile(cond) ? trueBranch : elseBranch;
        }
    }

    public abstract static class LLVMI32SelectNode extends LLVMSelectNode {

        @Specialization
        protected int doOp(boolean cond, int trueBranch, int elseBranch) {
            return conditionProfile.profile(cond) ? trueBranch : elseBranch;
        }
    }

    public abstract static class LLVMI64SelectNode extends LLVMSelectNode {

        @Specialization
        protected Object doOp(boolean cond, Object trueBranch, Object elseBranch) {
            assert trueBranch instanceof Long || LLVMPointer.isInstance(trueBranch);
            assert elseBranch instanceof Long || LLVMPointer.isInstance(elseBranch);
            return conditionProfile.profile(cond) ? trueBranch : elseBranch;
        }
    }

    public abstract static class LLVMFloatSelectNode extends LLVMSelectNode {

        @Specialization
        protected float doOp(boolean cond, float trueBranch, float elseBranch) {
            return conditionProfile.profile(cond) ? trueBranch : elseBranch;
        }
    }

    public abstract static class LLVMDoubleSelectNode extends LLVMSelectNode {

        @Specialization
        protected double doOp(boolean cond, double trueBranch, double elseBranch) {
            return conditionProfile.profile(cond) ? trueBranch : elseBranch;
        }
    }

    public abstract static class LLVM80BitFloatSelectNode extends LLVMSelectNode {

        @Specialization
        protected LLVM80BitFloat doOp(boolean cond, LLVM80BitFloat trueBranch, LLVM80BitFloat elseBranch) {
            return conditionProfile.profile(cond) ? trueBranch : elseBranch;
        }
    }

    public abstract static class LLVMGenericSelectNode extends LLVMSelectNode {

        @Specialization
        protected Object doOp(boolean cond, Object trueBranch, Object elseBranch) {
            return conditionProfile.profile(cond) ? trueBranch : elseBranch;
        }
    }
}
