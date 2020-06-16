/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.debugexpr.parser;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

public final class DebugExprException extends RuntimeException {

    private static final long serialVersionUID = -5083864640686842678L;

    private final Node location;
    private final String message;

    private DebugExprException(LLVMExpressionNode operation, String message) {
        this.location = operation;
        this.message = message;
    }

    @TruffleBoundary
    public static DebugExprException typeError(LLVMExpressionNode operation, Object... members) {
        CompilerDirectives.transferToInterpreter();
        StringBuilder sb = new StringBuilder();
        sb.append("unexpected type ");
        if (members != null && members.length > 0 && members[0] != null) {
            sb.append("of \"");
            sb.append(members[0].toString());
            sb.append("\"");
            for (int i = 1; i < members.length; i++) {
                sb.append(", \"");
                sb.append(members[i].toString());
                sb.append("\"");
            }
        }
        if (operation != null) {
            sb.append("at ");
            sb.append(operation.getClass().getSimpleName());
        }
        return new DebugExprException(operation, sb.toString());
    }

    @TruffleBoundary
    public static DebugExprException symbolNotFound(LLVMExpressionNode operation, String name, Object receiver) {
        CompilerDirectives.transferToInterpreter();
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        sb.append(" not found");
        if (receiver != null) {
            sb.append(" as member of ");
            sb.append(receiver.toString());
        }
        return new DebugExprException(operation, sb.toString());
    }

    @TruffleBoundary
    public static DebugExprException nullObject(String description) {
        return new DebugExprException(null, "member at " + description + " is not available");
    }

    @TruffleBoundary
    public static DebugExprException create(LLVMExpressionNode operation, String message, Object... args) {
        return new DebugExprException(operation, String.format(message, args));
    }

    @Override
    public String getMessage() {
        return message;
    }

    public Node getLocation() {
        return location;
    }
}
