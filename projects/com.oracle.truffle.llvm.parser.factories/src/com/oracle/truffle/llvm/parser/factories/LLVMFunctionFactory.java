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
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.facade.NodeFactoryFacade;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.VoidType;

final class LLVMFunctionFactory {

    private LLVMFunctionFactory() {
    }

    static LLVMControlFlowNode createRetVoid(LLVMParserRuntime runtime) {
        return LLVMVoidReturnNodeGen.create(runtime.getReturnSlot());
    }

    static LLVMControlFlowNode createNonVoidRet(LLVMParserRuntime runtime, LLVMExpressionNode retValue, Type type) {
        FrameSlot retSlot = runtime.getReturnSlot();
        if (retValue == null || retSlot == null) {
            throw new AssertionError();
        }
        if (type instanceof VectorType) {
            return LLVMVectorRetNodeGen.create(retValue, retSlot);
        } else if (type instanceof VariableBitWidthType) {
            return LLVMIVarBitRetNodeGen.create(retValue, retSlot);
        } else if (Type.isFunctionOrFunctionPointer(type)) {
            return LLVMFunctionRetNodeGen.create(retValue, retSlot);
        } else if (type instanceof PointerType) {
            return LLVMAddressRetNodeGen.create(retValue, retSlot);
        } else if (type instanceof StructureType) {
            int size = runtime.getByteSize(type);
            return LLVMStructRetNodeGen.create(runtime.getHeapFunctions(), retValue, retSlot, size);
        } else if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
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
                case FLOAT:
                    return LLVMFloatRetNodeGen.create(retValue, retSlot);
                case DOUBLE:
                    return LLVMDoubleRetNodeGen.create(retValue, retSlot);
                case X86_FP80:
                    return LLVM80BitFloatRetNodeGen.create(retValue, retSlot);
                default:
                    throw new AssertionError(type);
            }
        }
        throw new AssertionError(type);
    }

    static LLVMExpressionNode createFunctionArgNode(int argIndex, Type paramType) {
        if (argIndex < 0) {
            throw new AssertionError();
        }
        LLVMExpressionNode argNode = createArgNode(argIndex);
        return LLVMValueProfileFactory.createValueProfiledNode(argNode, paramType);
    }

    private static LLVMExpressionNode createArgNode(int argIndex) throws AssertionError {
        return LLVMArgNodeGen.create(argIndex);
    }

    static LLVMExpressionNode createFunctionCall(LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType functionType) {
        LLVMCallNode unresolvedCallNode = new LLVMCallNode(LLVMLanguage.INSTANCE.findContext0(LLVMLanguage.INSTANCE.createFindContextNode0()),
                        functionType, functionNode, argNodes);
        return createUnresolvedNodeWrapping(functionType.getReturnType(), unresolvedCallNode);

    }

    private static LLVMExpressionNode createUnresolvedNodeWrapping(Type llvmType, LLVMExpressionNode unresolvedCallNode) {
        if (llvmType instanceof VectorType) {
            return LLVMVectorCallUnboxNodeGen.create(unresolvedCallNode);
        } else if (llvmType instanceof VariableBitWidthType) {
            return LLVMVarBitCallUnboxNodeGen.create(unresolvedCallNode);
        } else if (llvmType instanceof VoidType) {
            return new LLVMVoidCallUnboxNode(unresolvedCallNode);
        } else if (Type.isFunctionOrFunctionPointer(llvmType)) {
            return LLVMFunctionCallUnboxNodeGen.create(unresolvedCallNode);
        } else if (llvmType instanceof StructureType) {
            return LLVMStructCallUnboxNodeGen.create(unresolvedCallNode);
        } else if (llvmType instanceof PointerType) {
            return LLVMAddressCallUnboxNodeGen.create(unresolvedCallNode);
        } else if (llvmType instanceof PrimitiveType) {
            switch (((PrimitiveType) llvmType).getPrimitiveKind()) {
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
                case FLOAT:
                    return LLVMFloatCallUnboxNodeGen.create(unresolvedCallNode);
                case DOUBLE:
                    return LLVMDoubleCallUnboxNodeGen.create(unresolvedCallNode);
                case X86_FP80:
                    return LLVM80BitFloatCallUnboxNodeGen.create(unresolvedCallNode);
                default:
                    throw new AssertionError(llvmType);
            }
        }
        throw new AssertionError(llvmType);
    }

    static RootNode createGlobalRootNodeWrapping(RootCallTarget mainCallTarget, Type returnType) {
        if (returnType instanceof VoidType) {
            return new LLVMMainFunctionReturnValueRootNode.LLVMMainFunctionReturnVoidRootNode(mainCallTarget);
        } else if (returnType instanceof VariableBitWidthType) {
            return new LLVMMainFunctionReturnValueRootNode.LLVMMainFunctionReturnIVarBitRootNode(mainCallTarget);
        } else if (returnType instanceof PrimitiveType) {
            switch (((PrimitiveType) returnType).getPrimitiveKind()) {
                case I1:
                    return new LLVMMainFunctionReturnValueRootNode.LLVMMainFunctionReturnI1RootNode(mainCallTarget);
                case I8:
                case I16:
                case I32:
                case I64:
                case FLOAT:
                case DOUBLE:
                    return new LLVMMainFunctionReturnValueRootNode.LLVMMainFunctionReturnNumberRootNode(mainCallTarget);
                default:
                    throw new AssertionError(returnType);
            }
        }
        throw new AssertionError(returnType);
    }

    static LLVMExpressionNode createFunctionArgNode(NodeFactoryFacade facade, int i) {
        int realIndex = facade.getArgStartIndex().get() + i;
        return LLVMArgNodeGen.create(realIndex);
    }

    static RootNode createFunctionSubstitutionRootNode(LLVMExpressionNode intrinsicNode) {
        return LLVMIntrinsicExpressionNodeGen.create(intrinsicNode);
    }

}
