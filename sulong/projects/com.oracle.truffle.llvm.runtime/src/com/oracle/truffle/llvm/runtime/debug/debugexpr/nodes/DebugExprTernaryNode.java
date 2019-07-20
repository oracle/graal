/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

@NodeChild(value = "condition", type = LLVMExpressionNode.class)
public abstract class DebugExprTernaryNode extends LLVMExpressionNode {
    public abstract Object executeWithTarget(VirtualFrame frame, Object condition);

    @Child private LLVMExpressionNode thenNode;
    @Child private LLVMExpressionNode elseNode;

    private final ConditionProfile conditionProfile;

    public DebugExprTernaryNode(LLVMExpressionNode thenNode, LLVMExpressionNode elseNode) {
        this.thenNode = thenNode;
        this.elseNode = elseNode;
        this.conditionProfile = ConditionProfile.createCountingProfile();
    }

    @Specialization
    Object doTernary(VirtualFrame frame, boolean condition) {
        if (conditionProfile.profile(condition)) {
            return thenNode.executeGeneric(frame);
        } else {
            return elseNode.executeGeneric(frame);
        }
    }

    @Specialization
    Object doTernary(VirtualFrame frame, int condition) {
        if (conditionProfile.profile(condition != 0)) {
            return thenNode.executeGeneric(frame);
        } else {
            return elseNode.executeGeneric(frame);
        }
    }

    @Specialization
    Object doTernary(VirtualFrame frame, long condition) {
        if (conditionProfile.profile(condition != 0)) {
            return thenNode.executeGeneric(frame);
        } else {
            return elseNode.executeGeneric(frame);
        }
    }

    @Fallback
    Object doError(Object condition) {
        throw DebugExprException.typeError(this, condition);
    }

}
