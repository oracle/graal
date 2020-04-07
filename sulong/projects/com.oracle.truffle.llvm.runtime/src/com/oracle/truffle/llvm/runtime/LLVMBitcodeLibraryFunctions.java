/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public final class LLVMBitcodeLibraryFunctions {

    protected abstract static class LibraryFunctionNode extends LLVMNode {

        @Child protected DirectCallNode callNode;

        protected LibraryFunctionNode(LLVMContext context, String name) {
            LLVMFunction function = context.getGlobalScope().getFunction(name);
            if (function == null) {
                throw new LLVMLinkerException("Function not found: " + name);
            }
            LLVMFunctionDescriptor descriptor = context.createFunctionDescriptor(function);
            callNode = DirectCallNode.create(descriptor.getFunctionCode().getLLVMIRFunctionSlowPath());
        }

        protected Object execute(Object... args) {
            return callNode.call(args);
        }
    }

    public static final class SulongCanCatchNode extends LibraryFunctionNode {

        public SulongCanCatchNode(LLVMContext context) {
            super(context, "sulong_eh_canCatch");
        }

        public int canCatch(LLVMStack.StackPointer stack, Object unwindHeader, LLVMPointer catchType) {
            return (int) execute(stack, unwindHeader, catchType.copy());
        }
    }
}
