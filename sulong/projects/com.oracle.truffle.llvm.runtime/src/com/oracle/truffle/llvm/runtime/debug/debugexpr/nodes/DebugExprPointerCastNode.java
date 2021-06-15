/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprType;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourcePointerType;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue.Builder;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public abstract class DebugExprPointerCastNode extends LLVMExpressionNode implements MemberAccessible {

    @Child private LLVMExpressionNode pointerNode;
    @Child private DebugExprTypeofNode typeNode;

    public DebugExprPointerCastNode(LLVMExpressionNode pointerNode, DebugExprTypeofNode typeNode) {
        this.pointerNode = pointerNode;
        this.typeNode = typeNode;
    }

    @Specialization
    Object doCast(VirtualFrame frame) {
        Object executedPointerNode = pointerNode.executeGeneric(frame);
        return getMember(executedPointerNode, frame);
    }

    @Override
    public DebugExprType getType(VirtualFrame frame) {
        return typeNode.getType(frame);
    }

    private Object getMember(Object executedPointerNode, VirtualFrame frame) {
        if (executedPointerNode == null) {
            throw DebugExprException.create(this, "debugObject to dereference is null");
        }
        if (!typeNode.getLLVMSourceType(frame).isPointer()) {
            throw DebugExprException.create(this, "%s is no pointer", executedPointerNode);
        }
        try {
            LLVMSourcePointerType llvmSourcePointerType = (LLVMSourcePointerType) typeNode.getLLVMSourceType(frame);

            LLVMDebugObject llvmPointerObject = (LLVMDebugObject) executedPointerNode;
            Object llvmPointerValue = llvmPointerObject.getValue();
            Builder builder = CommonNodeFactory.createDebugValueBuilder();
            LLVMDebugValue pointerValue = builder.build(llvmPointerValue);
            return LLVMDebugObject.create(llvmSourcePointerType, 0L, pointerValue, null);

        } catch (ClassCastException e) {
            throw DebugExprException.create(this, "%s cannot be casted to pointer ", executedPointerNode);
        }
    }

    @Override
    public Object getMember(VirtualFrame frame) {
        if (pointerNode instanceof MemberAccessible) {
            MemberAccessible ma = (MemberAccessible) pointerNode;
            Object member = ma.getMember(frame);
            return getMember(member, frame);
        }
        throw DebugExprException.create(this, "member %s is not accessible", pointerNode);
    }

}
