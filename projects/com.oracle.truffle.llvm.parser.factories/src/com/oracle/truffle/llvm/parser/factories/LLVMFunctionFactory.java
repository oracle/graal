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

import com.intel.llvm.ireditor.types.ResolvedStructType;
import com.intel.llvm.ireditor.types.ResolvedType;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMMainFunctionReturnValueRootNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMTerminatorNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMIVarBitNode;
import com.oracle.truffle.llvm.nodes.impl.base.vector.LLVMVectorNode;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVM80BitFloatRetNodeGen;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVMAddressRetNodeGen;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVMDoubleRetNodeGen;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVMFloatRetNodeGen;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVMFunctionRetNodeGen;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVMI16RetNodeGen;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVMI1RetNodeGen;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVMI32RetNodeGen;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVMI64RetNodeGen;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVMI8RetNodeGen;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVMIVarBitRetNodeGen;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVMStructRetNodeGen;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVMVectorRetNodeGen;
import com.oracle.truffle.llvm.nodes.impl.control.LLVMRetNodeFactory.LLVMVoidReturnNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVM80BitFloatArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMAddressArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMDoubleArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMDoubleVectorArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMFloatArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMFloatVectorArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMFunctionArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMI16ArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMI16VectorArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMI1ArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMI1VectorArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMI32ArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMI32VectorArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMI64ArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMI64VectorArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMI8ArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMI8VectorArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMIVarBitArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNode.LLVMUnresolvedCallNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNode.LLVMVoidCallUnboxNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNodeFactory.LLVM80BitFloatCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNodeFactory.LLVMAddressCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNodeFactory.LLVMDoubleCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNodeFactory.LLVMFloatCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNodeFactory.LLVMFunctionCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNodeFactory.LLVMI16CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNodeFactory.LLVMI1CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNodeFactory.LLVMI32CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNodeFactory.LLVMI64CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNodeFactory.LLVMI8CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNodeFactory.LLVMStructCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNodeFactory.LLVMVarBitCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallUnboxNodeFactory.LLVMVectorCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsic.LLVMVoidIntrinsic;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsicRootNode.LLVMIntrinsicVoidNode;
import com.oracle.truffle.llvm.nodes.impl.intrinsics.llvm.LLVMIntrinsicRootNodeFactory.LLVMIntrinsicExpressionNodeGen;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.util.LLVMTypeHelper;
import com.oracle.truffle.llvm.runtime.LLVMOptions;
import com.oracle.truffle.llvm.types.LLVMFunctionDescriptor.LLVMRuntimeType;

public final class LLVMFunctionFactory {

    private LLVMFunctionFactory() {
    }

    public static LLVMTerminatorNode createRetVoid(LLVMParserRuntime runtime) {
        return LLVMVoidReturnNodeGen.create(runtime.getReturnSlot());
    }

    public static LLVMTerminatorNode createNonVoidRet(LLVMParserRuntime runtime, LLVMExpressionNode retValue, ResolvedType resolvedType) {
        FrameSlot retSlot = runtime.getReturnSlot();
        if (retValue == null || retSlot == null) {
            throw new AssertionError();
        }
        LLVMBaseType type = LLVMTypeHelper.getLLVMType(resolvedType);
        if (LLVMTypeHelper.isVectorType(type)) {
            return LLVMVectorRetNodeGen.create((LLVMVectorNode) retValue, retSlot);
        } else {
            switch (type) {
                case I1:
                    return LLVMI1RetNodeGen.create((LLVMI1Node) retValue, retSlot);
                case I8:
                    return LLVMI8RetNodeGen.create((LLVMI8Node) retValue, retSlot);
                case I16:
                    return LLVMI16RetNodeGen.create((LLVMI16Node) retValue, retSlot);
                case I32:
                    return LLVMI32RetNodeGen.create((LLVMI32Node) retValue, retSlot);
                case I64:
                    return LLVMI64RetNodeGen.create((LLVMI64Node) retValue, retSlot);
                case I_VAR_BITWIDTH:
                    return LLVMIVarBitRetNodeGen.create((LLVMIVarBitNode) retValue, retSlot);
                case FLOAT:
                    return LLVMFloatRetNodeGen.create((LLVMFloatNode) retValue, retSlot);
                case DOUBLE:
                    return LLVMDoubleRetNodeGen.create((LLVMDoubleNode) retValue, retSlot);
                case X86_FP80:
                    return LLVM80BitFloatRetNodeGen.create((LLVM80BitFloatNode) retValue, retSlot);
                case ADDRESS:
                    return LLVMAddressRetNodeGen.create((LLVMAddressNode) retValue, retSlot);
                case FUNCTION_ADDRESS:
                    return LLVMFunctionRetNodeGen.create((LLVMFunctionNode) retValue, retSlot);
                case STRUCT:
                    ResolvedStructType structType = (ResolvedStructType) resolvedType;
                    int size = LLVMTypeHelper.getByteSize(structType);
                    return LLVMStructRetNodeGen.create((LLVMAddressNode) retValue, retSlot, size);
                default:
                    throw new AssertionError(type);
            }
        }
    }

    public static LLVMExpressionNode createFunctionArgNode(int argIndex, LLVMBaseType paramType) {
        if (argIndex < 0) {
            throw new AssertionError();
        }
        LLVMExpressionNode argNode = createArgNode(argIndex, paramType);
        if (LLVMOptions.valueProfileFunctionArgs()) {
            return LLVMValueProfileFactory.createValueProfiledNode(argNode, paramType);
        } else {
            return argNode;
        }
    }

    private static LLVMExpressionNode createArgNode(int argIndex, LLVMBaseType paramType) throws AssertionError {
        switch (paramType) {
            case I1:
                return LLVMI1ArgNodeGen.create(argIndex);
            case I8:
                return LLVMI8ArgNodeGen.create(argIndex);
            case I16:
                return LLVMI16ArgNodeGen.create(argIndex);
            case I32:
                return LLVMI32ArgNodeGen.create(argIndex);
            case I64:
                return LLVMI64ArgNodeGen.create(argIndex);
            case I_VAR_BITWIDTH:
                return LLVMIVarBitArgNodeGen.create(argIndex);
            case FLOAT:
                return LLVMFloatArgNodeGen.create(argIndex);
            case DOUBLE:
                return LLVMDoubleArgNodeGen.create(argIndex);
            case X86_FP80:
                return LLVM80BitFloatArgNodeGen.create(argIndex);
            case ADDRESS:
            case STRUCT:
                return LLVMAddressArgNodeGen.create(argIndex);
            case FUNCTION_ADDRESS:
                return LLVMFunctionArgNodeGen.create(argIndex);
            case I1_VECTOR:
                return LLVMI1VectorArgNodeGen.create(argIndex);
            case I8_VECTOR:
                return LLVMI8VectorArgNodeGen.create(argIndex);
            case I16_VECTOR:
                return LLVMI16VectorArgNodeGen.create(argIndex);
            case I32_VECTOR:
                return LLVMI32VectorArgNodeGen.create(argIndex);
            case I64_VECTOR:
                return LLVMI64VectorArgNodeGen.create(argIndex);
            case FLOAT_VECTOR:
                return LLVMFloatVectorArgNodeGen.create(argIndex);
            case DOUBLE_VECTOR:
                return LLVMDoubleVectorArgNodeGen.create(argIndex);
            default:
                throw new AssertionError(paramType);

        }
    }

    public static LLVMNode createFunctionCall(LLVMFunctionNode functionNode, LLVMExpressionNode[] argNodes, LLVMBaseType llvmType) {
        LLVMUnresolvedCallNode unresolvedCallNode = new LLVMUnresolvedCallNode(functionNode, argNodes, LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0()));
        if (LLVMTypeHelper.isVectorType(llvmType)) {
            return LLVMVectorCallUnboxNodeGen.create(unresolvedCallNode);
        } else {
            switch (llvmType) {
                case VOID:
                    return new LLVMVoidCallUnboxNode(unresolvedCallNode);
                case I1:
                    return LLVMI1CallUnboxNodeGen.create(unresolvedCallNode);
                case I8:
                    return LLVMI8CallUnboxNodeGen.create(unresolvedCallNode);
                case I16:
                    return LLVMI16CallUnboxNodeGen.create(unresolvedCallNode);
                case I32:
                    return LLVMI32CallUnboxNodeGen.create(unresolvedCallNode);
                case I64:
                    return LLVMI64CallUnboxNodeGen.create(unresolvedCallNode);
                case I_VAR_BITWIDTH:
                    return LLVMVarBitCallUnboxNodeGen.create(unresolvedCallNode);
                case FLOAT:
                    return LLVMFloatCallUnboxNodeGen.create(unresolvedCallNode);
                case DOUBLE:
                    return LLVMDoubleCallUnboxNodeGen.create(unresolvedCallNode);
                case X86_FP80:
                    return LLVM80BitFloatCallUnboxNodeGen.create(unresolvedCallNode);
                case ADDRESS:
                    return LLVMAddressCallUnboxNodeGen.create(unresolvedCallNode);
                case FUNCTION_ADDRESS:
                    return LLVMFunctionCallUnboxNodeGen.create(unresolvedCallNode);
                case STRUCT:
                    return LLVMStructCallUnboxNodeGen.create(unresolvedCallNode);
                default:
                    throw new AssertionError(llvmType);
            }
        }

    }

    public static RootNode createGlobalRootNodeWrapping(RootCallTarget mainCallTarget, LLVMRuntimeType returnType) {
        switch (returnType) {
            case I1:
                return new LLVMMainFunctionReturnValueRootNode.LLVMMainFunctionReturnI1RootNode(mainCallTarget);
            case I8:
            case I16:
            case I32:
            case I64:
            case FLOAT:
            case DOUBLE:
                return new LLVMMainFunctionReturnValueRootNode.LLVMMainFunctionReturnNumberRootNode(mainCallTarget);
            case I_VAR_BITWIDTH:
                return new LLVMMainFunctionReturnValueRootNode.LLVMMainFunctionReturnIVarBitRootNode(mainCallTarget);
            default:
                throw new AssertionError(returnType);
        }
    }

    public static LLVMNode createFunctionArgNode(int i, Class<? extends Node> clazz) {
        int realIndex = LLVMCallNode.ARG_START_INDEX + i;
        LLVMNode argNode;
        if (clazz.equals(LLVMI32Node.class)) {
            argNode = LLVMArgNodeFactory.LLVMI32ArgNodeGen.create(realIndex);
        } else if (clazz.equals(LLVMI64Node.class)) {
            argNode = LLVMArgNodeFactory.LLVMI64ArgNodeGen.create(realIndex);
        } else if (clazz.equals(LLVMFloatNode.class)) {
            argNode = LLVMArgNodeFactory.LLVMFloatArgNodeGen.create(realIndex);
        } else if (clazz.equals(LLVMDoubleNode.class)) {
            argNode = LLVMArgNodeFactory.LLVMDoubleArgNodeGen.create(realIndex);
        } else if (clazz.equals(LLVMAddressNode.class)) {
            argNode = LLVMArgNodeFactory.LLVMAddressArgNodeGen.create(realIndex);
        } else if (clazz.equals(LLVMFunctionNode.class)) {
            argNode = LLVMArgNodeFactory.LLVMFunctionArgNodeGen.create(realIndex);
        } else {
            throw new AssertionError(clazz);
        }
        return argNode;
    }

    public static RootNode createFunctionSubstitutionRootNode(LLVMNode intrinsicNode) {
        RootNode functionRoot;
        if (intrinsicNode instanceof LLVMExpressionNode) {
            functionRoot = LLVMIntrinsicExpressionNodeGen.create((LLVMExpressionNode) intrinsicNode);
        } else if (intrinsicNode instanceof LLVMVoidIntrinsic) {
            functionRoot = new LLVMIntrinsicVoidNode(intrinsicNode);
        } else {
            throw new AssertionError(intrinsicNode.getClass());
        }
        return functionRoot;
    }

}
