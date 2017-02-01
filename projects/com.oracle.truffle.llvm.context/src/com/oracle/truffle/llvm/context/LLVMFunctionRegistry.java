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
package com.oracle.truffle.llvm.context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.parser.api.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LLVMRuntimeType;

/**
 * Manages Sulong functions and intrinsified native functions.
 */
public class LLVMFunctionRegistry {

    public static final String ZERO_FUNCTION = "<zero function>";

    // do not start with 0, otherwise the first function
    // pointer would be == NULL
    private static final int REAL_FUNCTION_START_INDEX = 1;

    private final Map<String, NodeFactory<? extends LLVMExpressionNode>> intrinsics;
    private final NodeFactoryFacade facade;

    /**
     * The function index assigned to the next function descriptor.
     */
    private int currentFunctionIndex = REAL_FUNCTION_START_INDEX;

    /**
     * Maps a function index (see {@link LLVMFunction#getFunctionIndex()} to a function descriptor.
     */
    @CompilationFinal(dimensions = 1) private LLVMFunctionDescriptor[] functionDescriptors = new LLVMFunctionDescriptor[REAL_FUNCTION_START_INDEX];

    private final HashMap<String, LLVMFunctionDescriptor> functionIndex;

    public LLVMFunctionRegistry(NodeFactoryFacade facade, NativeLookup nativeLookup) {
        this.facade = facade;
        this.intrinsics = facade.getFunctionSubstitutionFactories();
        functionDescriptors[0] = facade.createFunctionDescriptor(ZERO_FUNCTION, LLVMRuntimeType.ILLEGAL, false, new LLVMRuntimeType[0], 0);
        this.functionIndex = new HashMap<>();
        this.functionIndex.put(ZERO_FUNCTION, functionDescriptors[0]);
        registerIntrinsics(nativeLookup);
    }

    private void registerIntrinsics(NativeLookup nativeLookup) {
        for (Map.Entry<String, NodeFactory<? extends LLVMExpressionNode>> entry : intrinsics.entrySet()) {
            String intrinsicFunction = entry.getKey();
            NodeFactory<? extends LLVMExpressionNode> nodeFactory = entry.getValue();

            LLVMFunctionDescriptor function = lookupFunctionDescriptor(intrinsicFunction, LLVMRuntimeType.ILLEGAL, new LLVMRuntimeType[0], false);
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
            function.setCallTarget(callTarget);
        }
    }

    /**
     * Looks up a unique function descriptor identified by the given <code>name</code>, creating it
     * if it doesn't exist yet.
     *
     * @param name the function's name
     * @param returnType the function's return type
     * @param paramTypes the function's
     * @param varArgs
     * @return the function descriptor
     */
    public LLVMFunctionDescriptor lookupFunctionDescriptor(String name, LLVMRuntimeType returnType, LLVMRuntimeType[] paramTypes, boolean varArgs) {
        CompilerAsserts.neverPartOfCompilation();
        LLVMFunctionDescriptor function = functionIndex.get(name);
        if (function == null) {
            function = facade.createFunctionDescriptor(name, returnType, varArgs, paramTypes, currentFunctionIndex++);
            LLVMFunctionDescriptor[] newFunctions = new LLVMFunctionDescriptor[functionDescriptors.length + 1];
            System.arraycopy(functionDescriptors, 0, newFunctions, 0, functionDescriptors.length);
            newFunctions[function.getFunctionIndex()] = function;
            functionDescriptors = newFunctions;

            functionIndex.put(name, function);
        }
        return function;
    }

    /**
     * Creates a function descriptor from the given <code>index</code> that has previously been
     * obtained by {@link LLVMFunction#getFunctionIndex()} .
     *
     * @param handle the function handle
     * @return the function descriptor
     */
    public LLVMFunctionDescriptor lookup(LLVMFunction handle) {
        return functionDescriptors[handle.getFunctionIndex()];
    }

    public LLVMFunctionDescriptor[] getFunctionDescriptors() {
        return functionDescriptors;
    }

    public boolean isZeroFunctionDescriptor(LLVMFunctionDescriptor function) {
        return function.getName().equals(ZERO_FUNCTION);
    }

    public LLVMFunctionDescriptor getZeroFunctionDescriptor() {
        return functionDescriptors[0];
    }
}
