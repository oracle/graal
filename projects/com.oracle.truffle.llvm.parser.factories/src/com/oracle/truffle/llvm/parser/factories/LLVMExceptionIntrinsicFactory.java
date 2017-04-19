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
package com.oracle.truffle.llvm.parser.factories;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.llvm.nodes.func.LLVMAtExitNode;
import com.oracle.truffle.llvm.nodes.func.LLVMBeginCatchNode;
import com.oracle.truffle.llvm.nodes.func.LLVMEndCatchNode;
import com.oracle.truffle.llvm.nodes.func.LLVMFreeExceptionNode;
import com.oracle.truffle.llvm.nodes.func.LLVMRethrowNode;
import com.oracle.truffle.llvm.nodes.func.LLVMThrowExceptionNode;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.runtime.LLVMExitException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

final class LLVMExceptionIntrinsicFactory {

    private LLVMExceptionIntrinsicFactory() {
    }

    static LLVMExpressionNode create(String name, LLVMExpressionNode[] argNodes, int numberOfExplicitArguments, LLVMParserRuntime runtime,
                    FrameSlot exceptionValueSlot) {
        return create(name, argNodes, numberOfExplicitArguments, runtime.getStackPointerSlot(), exceptionValueSlot);
    }

    @SuppressWarnings("unused")
    private static LLVMExpressionNode create(String functionName, LLVMExpressionNode[] argNodes, int numberOfExplicitArguments, FrameSlot stack, FrameSlot exceptionValueSlot) {
        if (functionName.equals("@__cxa_throw")) {
            return new LLVMThrowExceptionNode(argNodes[1], argNodes[2], argNodes[3]);
        } else if (functionName.equals("@__cxa_rethrow")) {
            return new LLVMRethrowNode();
        } else if (functionName.equals("@__cxa_begin_catch")) {
            return new LLVMBeginCatchNode(argNodes[1]);
        } else if (functionName.equals("@__cxa_end_catch")) {
            return new LLVMEndCatchNode(argNodes[0]);
        } else if (functionName.equals("@__cxa_free_exception")) {
            return new LLVMFreeExceptionNode(argNodes[1]);
        } else if (functionName.equals("@__cxa_atexit")) {
            return new LLVMAtExitNode(argNodes[1], argNodes[2], argNodes[3]);
        } else if (functionName.equals("@__cxa_call_unexpected")) {
            return new LLVMExpressionNode() {
                @Override
                public Object executeGeneric(VirtualFrame frame) {
                    throw new LLVMExitException(134);
                }
            };
        } else {
            // if we do not intrinsify an cxa function, we want to do the native call
            return null;
        }
    }
}
