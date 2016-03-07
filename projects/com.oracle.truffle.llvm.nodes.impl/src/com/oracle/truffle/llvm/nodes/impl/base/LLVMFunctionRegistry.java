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
package com.oracle.truffle.llvm.nodes.impl.base;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMAbortFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMACosFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMASinFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMATanFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMCosFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMExpFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMLogFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSinFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMSqrtFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMTanFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMTanhFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMCallocFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMExitFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMFreeFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.c.LLVMMallocFactory;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMVoidIntrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsicRootNode.LLVMIntrinsicVoidNode;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsicRootNodeFactory.LLVMIntrinsicAddressNodeGen;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsicRootNodeFactory.LLVMIntrinsicDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsicRootNodeFactory.LLVMIntrinsicFloatNodeGen;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsicRootNodeFactory.LLVMIntrinsicI16NodeGen;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsicRootNodeFactory.LLVMIntrinsicI32NodeGen;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsicRootNodeFactory.LLVMIntrinsicI64NodeGen;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsicRootNodeFactory.LLVMIntrinsicI8NodeGen;
import com.oracle.truffle.llvm.runtime.LLVMOptimizationConfiguration;
import com.oracle.truffle.llvm.types.LLVMFunction;

/**
 * Manages Sulong functions and intrinsified native functions.
 */
public class LLVMFunctionRegistry {

    private final Map<String, NodeFactory<? extends LLVMIntrinsic>> intrinsics = new HashMap<>();

    public LLVMFunctionRegistry(LLVMOptimizationConfiguration optimizationConfig) {
        initializeIntrinsics(optimizationConfig);
    }

    private void initializeIntrinsics(LLVMOptimizationConfiguration optimizationConfig) {
        // Fortran
        intrinsics.put("@_gfortran_abort", LLVMAbortFactory.getInstance());

        // C
        intrinsics.put("@abort", LLVMAbortFactory.getInstance());
        intrinsics.put("@exit", LLVMExitFactory.getInstance());

        if (optimizationConfig.intrinsifyCLibraryFunctions()) {
            // math.h
            intrinsics.put("@acos", LLVMACosFactory.getInstance());
            intrinsics.put("@asin", LLVMASinFactory.getInstance());
            intrinsics.put("@atan", LLVMATanFactory.getInstance());
            intrinsics.put("@cos", LLVMCosFactory.getInstance());
            intrinsics.put("@exp", LLVMExpFactory.getInstance());
            intrinsics.put("@log", LLVMLogFactory.getInstance());
            intrinsics.put("@sqrt", LLVMSqrtFactory.getInstance());
            intrinsics.put("@sin", LLVMSinFactory.getInstance());
            intrinsics.put("@tan", LLVMTanFactory.getInstance());
            intrinsics.put("@tanh", LLVMTanhFactory.getInstance());

            // other libraries
            intrinsics.put("@malloc", LLVMMallocFactory.getInstance());
            intrinsics.put("@free", LLVMFreeFactory.getInstance());
            intrinsics.put("@calloc", LLVMCallocFactory.getInstance());
        }
    }

    /**
     * Maps a function index (see {@link LLVMFunction#getFunctionIndex()} to a call target.
     */
    @CompilationFinal private RootCallTarget[] functionPtrCallTargetMap;

    /**
     * Looks up the call target for a specific function. The lookup may return <code>null</code> if
     * the function is a native function or if the function cannot be found.
     *
     * @param function the function
     * @return the call target, <code>null</code> if not found.
     */
    public RootCallTarget lookup(LLVMFunction function) {
        int functionIndex = function.getFunctionIndex();
        if (functionIndex >= 0 && functionIndex < functionPtrCallTargetMap.length) {
            RootCallTarget result = functionPtrCallTargetMap[functionIndex];
            return result;
        } else {
            return null;
        }
    }

    public void register(Map<LLVMFunction, RootCallTarget> functionCallTargets) {
        functionPtrCallTargetMap = new RootCallTarget[LLVMFunction.getNumberRegisteredFunctions() + intrinsics.size()];
        registerIntrinsics();
        for (LLVMFunction func : functionCallTargets.keySet()) {
            functionPtrCallTargetMap[func.getFunctionIndex()] = functionCallTargets.get(func);
        }
    }

    private void registerIntrinsics() {
        for (String intrinsicFunction : intrinsics.keySet()) {
            LLVMFunction function = LLVMFunction.createFromName(intrinsicFunction);
            NodeFactory<? extends LLVMIntrinsic> nodeFactory = intrinsics.get(intrinsicFunction);
            List<Class<? extends Node>> executionSignature = nodeFactory.getExecutionSignature();
            int nrArguments = executionSignature.size();
            LLVMNode[] args = new LLVMNode[nrArguments];
            for (int i = 0; i < nrArguments; i++) {
                args[i] = getArgReadNode(executionSignature, i);
            }
            LLVMIntrinsic intrinsicNode = nodeFactory.createNode((Object[]) args);
            RootNode functionRoot = getRootNode(intrinsicNode);
            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(functionRoot);
            addToFunctionMap(function, callTarget);
        }
    }

    private static LLVMNode getArgReadNode(List<Class<? extends Node>> executionSignature, int i) {
        Class<? extends Node> clazz = executionSignature.get(i);
        LLVMNode argNode;
        if (clazz.equals(LLVMI32Node.class)) {
            argNode = LLVMArgNodeFactory.LLVMI32ArgNodeGen.create(i);
        } else if (clazz.equals(LLVMI64Node.class)) {
            argNode = LLVMArgNodeFactory.LLVMI64ArgNodeGen.create(i);
        } else if (clazz.equals(LLVMFloatNode.class)) {
            argNode = LLVMArgNodeFactory.LLVMFloatArgNodeGen.create(i);
        } else if (clazz.equals(LLVMDoubleNode.class)) {
            argNode = LLVMArgNodeFactory.LLVMDoubleArgNodeGen.create(i);
        } else if (clazz.equals(LLVMAddressNode.class)) {
            argNode = LLVMArgNodeFactory.LLVMAddressArgNodeGen.create(i);
        } else if (clazz.equals(LLVMFunctionNode.class)) {
            argNode = LLVMArgNodeFactory.LLVMFunctionArgNodeGen.create(i);
        } else {
            throw new AssertionError(clazz);
        }
        return argNode;
    }

    private static RootNode getRootNode(LLVMIntrinsic intrinsicNode) throws AssertionError {
        RootNode functionRoot;
        if (intrinsicNode instanceof LLVMI8Node) {
            functionRoot = LLVMIntrinsicI8NodeGen.create((LLVMI8Node) intrinsicNode);
        } else if (intrinsicNode instanceof LLVMI16Node) {
            functionRoot = LLVMIntrinsicI16NodeGen.create((LLVMI16Node) intrinsicNode);
        } else if (intrinsicNode instanceof LLVMI32Node) {
            functionRoot = LLVMIntrinsicI32NodeGen.create((LLVMI32Node) intrinsicNode);
        } else if (intrinsicNode instanceof LLVMI64Node) {
            functionRoot = LLVMIntrinsicI64NodeGen.create((LLVMI64Node) intrinsicNode);
        } else if (intrinsicNode instanceof LLVMFloatNode) {
            functionRoot = LLVMIntrinsicFloatNodeGen.create((LLVMFloatNode) intrinsicNode);
        } else if (intrinsicNode instanceof LLVMDoubleNode) {
            functionRoot = LLVMIntrinsicDoubleNodeGen.create((LLVMDoubleNode) intrinsicNode);
        } else if (intrinsicNode instanceof LLVMAddressNode) {
            functionRoot = LLVMIntrinsicAddressNodeGen.create((LLVMAddressNode) intrinsicNode);
        } else if (intrinsicNode instanceof LLVMVoidIntrinsic) {
            functionRoot = new LLVMIntrinsicVoidNode(((LLVMNode) intrinsicNode));
        } else {
            throw new AssertionError(intrinsicNode.getClass());
        }
        return functionRoot;
    }

    private void addToFunctionMap(LLVMFunction function, RootCallTarget callTarget) {
        assert functionPtrCallTargetMap[function.getFunctionIndex()] == null;
        functionPtrCallTargetMap[function.getFunctionIndex()] = callTarget;
    }

}
