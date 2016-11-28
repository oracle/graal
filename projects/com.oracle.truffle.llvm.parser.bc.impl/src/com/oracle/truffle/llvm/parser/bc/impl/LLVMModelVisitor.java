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
package com.oracle.truffle.llvm.parser.bc.impl;

import java.util.List;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.context.LLVMContext;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalConstant;
import com.oracle.truffle.llvm.parser.base.model.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.model.visitors.ModelVisitor;
import com.oracle.truffle.llvm.parser.base.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;
import com.oracle.truffle.llvm.types.LLVMFunction;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;

public final class LLVMModelVisitor implements ModelVisitor {

    private final LLVMBitcodeVisitor visitor;
    private final LLVMContext context;

    public LLVMModelVisitor(LLVMBitcodeVisitor visitor, LLVMContext context) {
        this.visitor = visitor;
        this.context = context;
    }

    @Override
    public void visit(GlobalAlias alias) {
        visitor.getAliases().put(alias, alias.getValue());
    }

    @Override
    public void visit(GlobalConstant constant) {
        visitor.getGlobals().put(constant, null);
    }

    @Override
    public void visit(GlobalVariable variable) {
        visitor.getGlobals().put(variable, null);
    }

    @Override
    public void visit(FunctionDeclaration method) {
    }

    @Override
    public void visit(FunctionDefinition method) {
        FrameDescriptor frame = visitor.getStack().getFrame(method.getName());

        List<LLVMNode> parameters = visitor.createParameters(frame, method);

        final LLVMLifetimeAnalysis lifetimes = LLVMLifetimeAnalysis.getResult(method, frame, visitor.getPhis().getPhiMap(method.getName()));

        LLVMExpressionNode body = visitor.createFunction(method, lifetimes);

        LLVMNode[] beforeFunction = parameters.toArray(new LLVMNode[parameters.size()]);
        LLVMNode[] afterFunction = new LLVMNode[0];

        final SourceSection sourceSection = visitor.getSource().createSection(1);
        RootNode rootNode = visitor.getNodeFactoryFacade().createFunctionStartNode(visitor, body, beforeFunction, afterFunction, sourceSection, frame, method);
        if (LLVMOptions.DEBUG.printFunctionASTs()) {
            NodeUtil.printTree(System.out, rootNode);
            System.out.flush();
        }

        LLVMRuntimeType llvmReturnType = method.getReturnType().getRuntimeType();
        LLVMRuntimeType[] llvmParamTypes = LLVMBitcodeTypeHelper.toRuntimeTypes(method.getArgumentTypes());
        LLVMFunction function = context.getFunctionRegistry().createFunctionDescriptor(method.getName(), llvmReturnType, llvmParamTypes, method.isVarArg());
        RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(rootNode);
        visitor.getFunctions().put(function, callTarget);
        visitor.exitFunction();
    }

    @Override
    public void visit(Type type) {
    }

}
