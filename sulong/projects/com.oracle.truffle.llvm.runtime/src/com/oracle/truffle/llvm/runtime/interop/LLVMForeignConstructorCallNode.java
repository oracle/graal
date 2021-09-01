/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.interop;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceFunctionType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI64LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;

public class LLVMForeignConstructorCallNode extends LLVMForeignCallNode {

    @Child LLVMExpressionNode malloc;

    public static LLVMForeignConstructorCallNode create(LLVMLanguage language, LLVMFunctionCode function, LLVMInteropType interopType, LLVMSourceFunctionType sourceType,
                    LLVMInteropType.Structured structuredType) {
        Type escapeType = function.getLLVMFunction().getType().getArgumentType(0);
        return new LLVMForeignConstructorCallNode(language, function, interopType, sourceType, structuredType, escapeType);

    }

    protected LLVMForeignConstructorCallNode(LLVMLanguage language, LLVMFunctionCode function, LLVMInteropType interopType, LLVMSourceFunctionType sourceType,
                    LLVMInteropType.Structured structuredType, Type escapeType) {
        super(language, function, interopType, sourceType, structuredType, escapeType);

        /*
         * TODO(rs): This should probably be a call to the C++ new operator instead of malloc. This
         * makes a difference if the new operator is overloaded.
         */
        NodeFactory factory = language.getActiveConfiguration().createNodeFactory(language, language.getDefaultDataLayout());
        LLVMExpressionNode stack = factory.createGetStackFromFrame();
        LLVMExpressionNode size = LLVMI64LiteralNodeGen.create(returnBaseType.getSize());
        malloc = language.getCapability(LLVMIntrinsicProvider.class).generateIntrinsicNode("malloc", new LLVMExpressionNode[]{stack, size}, new Type[]{PointerType.VOID, PrimitiveType.I64}, factory);
    }

    @Override
    protected Object doCall(VirtualFrame frame, LLVMStack stack) throws ArityException, TypeOverflowException {
        Object[] rawArguments = frame.getArguments();
        rawArguments[0] = malloc.executeGeneric(frame);
        if (packArguments.toLLVM.length != rawArguments.length) {
            // arguments also contain 'self' object, thus -1 for argCount
            int arity = packArguments.toLLVM.length - 1;
            throw ArityException.create(arity, arity, rawArguments.length - 1);
        }

        Object[] arguments = packArguments.execute(rawArguments, stack);
        callNode.call(arguments);
        /*
         * The packArgumentsNode adds the stack pointer to the argument list. Therefore,
         * rawArguments[0] is now at arguments[1].
         */
        return arguments[1];
    }
}
