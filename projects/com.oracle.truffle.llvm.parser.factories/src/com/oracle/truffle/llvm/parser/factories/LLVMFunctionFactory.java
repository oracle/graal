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

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.llvm.context.LLVMLanguage;
import com.oracle.truffle.llvm.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMMainFunctionReturnValueRootNode;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVM80BitFloatRetNodeGen;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVMAddressRetNodeGen;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVMDoubleRetNodeGen;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVMFloatRetNodeGen;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVMFunctionRetNodeGen;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVMI16RetNodeGen;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVMI1RetNodeGen;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVMI32RetNodeGen;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVMI64RetNodeGen;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVMI8RetNodeGen;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVMIVarBitRetNodeGen;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVMStructRetNodeGen;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVMVectorRetNodeGen;
import com.oracle.truffle.llvm.nodes.control.LLVMRetNodeFactory.LLVMVoidReturnNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMArgNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNode.LLVMVoidCallUnboxNode;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVM80BitFloatCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMAddressCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMDoubleCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMFloatCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMFunctionCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMI16CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMI1CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMI32CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMI64CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMI8CallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMStructCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMVarBitCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallUnboxNodeFactory.LLVMVectorCallUnboxNodeGen;
import com.oracle.truffle.llvm.nodes.intrinsics.llvm.LLVMIntrinsicRootNodeFactory.LLVMIntrinsicExpressionNodeGen;
import com.oracle.truffle.llvm.parser.api.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.parser.api.util.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.api.util.LLVMTypeHelper;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.LLVMRuntimeType;
import com.oracle.truffle.llvm.runtime.types.LLVMBaseType;
import com.oracle.truffle.llvm.runtime.types.LLVMType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMFunctionFactory {

    private LLVMFunctionFactory() {
    }

    public static LLVMControlFlowNode createRetVoid(LLVMParserRuntime runtime) {
        return LLVMVoidReturnNodeGen.create(runtime.getReturnSlot());
    }

    public static LLVMControlFlowNode createNonVoidRet(LLVMParserRuntime runtime, LLVMExpressionNode retValue, Type resolvedType) {
        FrameSlot retSlot = runtime.getReturnSlot();
        if (retValue == null || retSlot == null) {
            throw new AssertionError();
        }
        LLVMBaseType type = resolvedType.getLLVMBaseType();
        if (LLVMTypeHelper.isVectorType(type)) {
            return LLVMVectorRetNodeGen.create(retValue, retSlot);
        } else {
            switch (type) {
                case I1:
                    return LLVMI1RetNodeGen.create(retValue, retSlot);
                case I8:
                    return LLVMI8RetNodeGen.create(retValue, retSlot);
                case I16:
                    return LLVMI16RetNodeGen.create(retValue, retSlot);
                case I32:
                    return LLVMI32RetNodeGen.create(retValue, retSlot);
                case I64:
                    return LLVMI64RetNodeGen.create(retValue, retSlot);
                case I_VAR_BITWIDTH:
                    return LLVMIVarBitRetNodeGen.create(retValue, retSlot);
                case FLOAT:
                    return LLVMFloatRetNodeGen.create(retValue, retSlot);
                case DOUBLE:
                    return LLVMDoubleRetNodeGen.create(retValue, retSlot);
                case X86_FP80:
                    return LLVM80BitFloatRetNodeGen.create(retValue, retSlot);
                case ADDRESS:
                    return LLVMAddressRetNodeGen.create(retValue, retSlot);
                case FUNCTION_ADDRESS:
                    return LLVMFunctionRetNodeGen.create(retValue, retSlot);
                case STRUCT:
                    int size = runtime.getByteSize(resolvedType);
                    return LLVMStructRetNodeGen.create(runtime.getHeapFunctions(), retValue, retSlot, size);
                default:
                    throw new AssertionError(type);
            }
        }
    }

    public static LLVMExpressionNode createFunctionArgNode(int argIndex, LLVMBaseType paramType) {
        if (argIndex < 0) {
            throw new AssertionError();
        }
        LLVMExpressionNode argNode = createArgNode(argIndex);
        return LLVMValueProfileFactory.createValueProfiledNode(argNode, paramType);
    }

    public static LLVMExpressionNode createArgNode(int argIndex) throws AssertionError {
        return LLVMArgNodeGen.create(argIndex);
    }

    public static LLVMExpressionNode createFunctionCall(LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, Type[] argTypes, LLVMBaseType llvmType) {
        LLVMCallNode unresolvedCallNode = new LLVMCallNode(LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0()),
                        LLVMTypeHelper.convertType(new LLVMType(llvmType)), functionNode, argNodes, argTypes);
        return createUnresolvedNodeWrapping(llvmType, unresolvedCallNode);

    }

    public static LLVMExpressionNode createUnresolvedNodeWrapping(LLVMBaseType llvmType, LLVMExpressionNode unresolvedCallNode) {
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
            case VOID:
                return new LLVMMainFunctionReturnValueRootNode.LLVMMainFunctionReturnVoidRootNode(mainCallTarget);
            default:
                throw new AssertionError(returnType);
        }
    }

    public static LLVMExpressionNode createFunctionArgNode(NodeFactoryFacade facade, int i) {
        int realIndex = facade.getArgStartIndex().get() + i;
        return LLVMArgNodeGen.create(realIndex);
    }

    public static RootNode createFunctionSubstitutionRootNode(LLVMExpressionNode intrinsicNode) {
        return LLVMIntrinsicExpressionNodeGen.create(intrinsicNode);
    }

}
