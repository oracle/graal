/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.factories.inlineasm;

import java.util.Collections;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.llvm.asm.amd64.AsmFactory;
import com.oracle.truffle.llvm.asm.amd64.InlineAssemblyParser;
import com.oracle.truffle.llvm.runtime.IDGenerater;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode.LLVMIRFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout.StructureTypeOffsets;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.inlineasm.InlineAssemblyParserBase;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNode.LLVMManagedPointerLiteralNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMUnsupportedInstructionNode;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;

public class AMD64InlineAssemblyParser extends InlineAssemblyParserBase {

    private LLVMInlineAssemblyRootNode getLazyUnsupportedInlineRootNode(NodeFactory factory, String asmExpression, LLVMParserException e) {
        LLVMInlineAssemblyRootNode assemblyRoot;
        String message = asmExpression + ": " + e.getMessage();
        FrameDescriptor.Builder builder = FrameDescriptor.newBuilder();
        factory.addStackSlots(builder);
        assemblyRoot = new LLVMInlineAssemblyRootNode(factory.getLanguage(), builder.build(), factory.createStackAccess(),
                        Collections.singletonList(LLVMUnsupportedInstructionNode.create(UnsupportedReason.INLINE_ASSEMBLER, message)), Collections.emptyList(), null);
        return assemblyRoot;
    }

    @Override
    public LLVMExpressionNode getInlineAssemblerExpression(NodeFactory nodeFactory, String asmExpression, String asmFlags, LLVMExpressionNode[] args,
                    Type.TypeArrayBuilder argTypes, Type retType) {
        StructureTypeOffsets offsets;

        try {
            offsets = nodeFactory.getDataLayout().getStructureTypeOffsets(retType);
        } catch (TypeOverflowException ex) {
            return Type.handleOverflowExpression(ex);
        }

        LLVMInlineAssemblyRootNode assemblyRoot;
        try {
            assemblyRoot = InlineAssemblyParser.parseInlineAssembly(asmExpression,
                            new AsmFactory(nodeFactory.getLanguage(), argTypes, asmFlags, retType, offsets.getTypes(), offsets.getOffsets(), nodeFactory));
        } catch (LLVMParserException e) {
            assemblyRoot = getLazyUnsupportedInlineRootNode(nodeFactory, asmExpression, e);
        }
        LLVMIRFunction function = new LLVMIRFunction(assemblyRoot.getCallTarget(), null);
        LLVMFunction functionDetail = LLVMFunction.create("<asm>", function, new FunctionType(MetaType.UNKNOWN, 0, FunctionType.NOT_VARARGS), IDGenerater.INVALID_ID, LLVMSymbol.INVALID_INDEX,
                        false, assemblyRoot.getName(), false);
        // The function descriptor for the inline assembly does not require a language.
        LLVMFunctionDescriptor asm = new LLVMFunctionDescriptor(functionDetail, new LLVMFunctionCode(functionDetail));
        LLVMManagedPointerLiteralNode asmFunction = LLVMManagedPointerLiteralNode.create(LLVMManagedPointer.create(asm));

        return LLVMCallNode.create(new FunctionType(MetaType.UNKNOWN, argTypes, FunctionType.NOT_VARARGS), asmFunction, args, false);
    }
}
