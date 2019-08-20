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
package com.oracle.truffle.llvm.nodes.others;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;

public final class LLVMUnsupportedInstructionNode extends LLVMStatementNode {

    public static LLVMUnsupportedInstructionNode create(UnsupportedReason reason) {
        return new LLVMUnsupportedInstructionNode(null, reason, null);
    }

    public static LLVMUnsupportedInstructionNode create(LLVMSourceLocation location, UnsupportedReason reason) {
        return new LLVMUnsupportedInstructionNode(location, reason, null);
    }

    public static LLVMUnsupportedInstructionNode create(LLVMSourceLocation location, UnsupportedReason reason, String message) {
        return new LLVMUnsupportedInstructionNode(location, reason, message);
    }

    public static LLVMUnsupportedExpressionNode createExpression(UnsupportedReason reason) {
        return new LLVMUnsupportedExpressionNode(create(reason));
    }

    public static LLVMUnsupportedExpressionNode createExpression(LLVMSourceLocation location, UnsupportedReason reason) {
        return new LLVMUnsupportedExpressionNode(create(location, reason));
    }

    public static LLVMUnsupportedExpressionNode createExpression(LLVMSourceLocation location, UnsupportedReason reason, String message) {
        return new LLVMUnsupportedExpressionNode(create(location, reason, message));
    }

    private final LLVMSourceLocation source;
    private final String message;
    private final UnsupportedReason reason;

    private LLVMUnsupportedInstructionNode(LLVMSourceLocation location, UnsupportedReason reason, String message) {
        this.source = location;
        this.message = message;
        this.reason = reason;
    }

    @Override
    public LLVMSourceLocation getSourceLocation() {
        return source;
    }

    @Override
    public void execute(VirtualFrame frame) {
        CompilerDirectives.transferToInterpreter();
        if (message == null) {
            throw new LLVMUnsupportedException(this, reason);
        }
        throw new LLVMUnsupportedException(this, reason, "Unsupported operation: " + message);
    }

    public static final class LLVMUnsupportedExpressionNode extends LLVMExpressionNode {

        @Child private LLVMUnsupportedInstructionNode instruction;

        private LLVMUnsupportedExpressionNode(LLVMUnsupportedInstructionNode instruction) {
            this.instruction = instruction;
        }

        @Override
        public Object executeGeneric(VirtualFrame frame) {
            instruction.execute(frame);
            return null;
        }
    }
}
