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
package com.oracle.truffle.llvm.parser;

import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.parser.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.NativeLookup;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.Type;

/**
 * Manages Sulong functions and intrinsified native functions.
 */
public final class LLVMFunctionRegistry {

    private static final String ZERO_FUNCTION = "<zero function>";

    private final Map<String, NodeFactory<? extends LLVMExpressionNode>> intrinsics;
    private final NodeFactoryFacade facade;

    private final LLVMContext context;

    LLVMFunctionRegistry(LLVMContext context, NodeFactoryFacade facade) {
        this.facade = facade;
        this.context = context;
        this.intrinsics = facade.getFunctionSubstitutionFactories();
        lookupFunctionDescriptor(ZERO_FUNCTION, new FunctionType(MetaType.UNKNOWN, new Type[0], false));
        registerIntrinsics(context.getNativeLookup());
    }

    private void registerIntrinsics(NativeLookup nativeLookup) {
        for (Map.Entry<String, NodeFactory<? extends LLVMExpressionNode>> entry : intrinsics.entrySet()) {
            String intrinsicFunction = entry.getKey();
            NodeFactory<? extends LLVMExpressionNode> nodeFactory = entry.getValue();

            LLVMFunctionDescriptor function = lookupFunctionDescriptor(intrinsicFunction, new FunctionType(MetaType.UNKNOWN, new Type[0], false));
            RootNode functionRoot;
            List<Class<? extends Node>> executionSignature = nodeFactory.getExecutionSignature();

            int nrArguments = executionSignature.size();
            Object[] args = new Object[nrArguments];
            int functionDescriptor = 0;
            for (int i = 0; i < nrArguments; i++) {
                args[i] = facade.createFunctionArgNode(i - functionDescriptor, executionSignature.get(i));
            }
            LLVMExpressionNode intrinsicNode;
            List<Class<?>> firstNodeFactory = nodeFactory.getNodeSignatures().get(0);
            if (firstNodeFactory.size() > 0 && firstNodeFactory.get(0) == TruffleObject.class) {
                // node constructor expects a NativeSymbol as first argument
                Object[] newArgs = new Object[args.length + 1];
                newArgs[0] = nativeLookup.getNativeFunction(intrinsicFunction);
                System.arraycopy(args, 0, newArgs, 1, args.length);
                intrinsicNode = nodeFactory.createNode(newArgs);
            } else {
                intrinsicNode = nodeFactory.createNode(args);
            }
            functionRoot = facade.createFunctionSubstitutionRootNode(intrinsicNode);
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(functionRoot);
            function.setIntrinsicCallTarget(callTarget);
        }
    }

    public LLVMFunctionDescriptor lookupFunctionDescriptor(String name, FunctionType type) {
        return context.addFunction(name, i -> facade.createFunctionDescriptor(name, type, i));
    }

}
