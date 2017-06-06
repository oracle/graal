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
import com.oracle.truffle.api.source.SourceSection;
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
import com.oracle.truffle.llvm.nodes.func.LLVMArgNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.func.LLVMInvokeNode;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMAddressProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMDoubleProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMFloatProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMI16ProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMI1ProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMI32ProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMI64ProfiledValueNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMValueProfilingNodeFactory.LLVMI8ProfiledValueNodeGen;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
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

    static LLVMControlFlowNode createNonVoidRet(LLVMParserRuntime runtime, LLVMExpressionNode retValue, Type type, SourceSection source) {
        if (retValue == null) {
            throw new AssertionError();
        }
        if (type instanceof VectorType) {
            return LLVMVectorRetNodeGen.create(source, retValue);
        } else if (type instanceof VariableBitWidthType) {
            return LLVMIVarBitRetNodeGen.create(source, retValue);
        } else if (Type.isFunctionOrFunctionPointer(type)) {
            return LLVMFunctionRetNodeGen.create(source, retValue);
        } else if (type instanceof PointerType) {
            return LLVMAddressRetNodeGen.create(source, retValue);
        } else if (type instanceof StructureType) {
            int size = runtime.getByteSize(type);
            return LLVMStructRetNodeGen.create(source, retValue, size);
        } else if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return LLVMI1RetNodeGen.create(source, retValue);
                case I8:
                    return LLVMI8RetNodeGen.create(source, retValue);
                case I16:
                    return LLVMI16RetNodeGen.create(source, retValue);
                case I32:
                    return LLVMI32RetNodeGen.create(source, retValue);
                case I64:
                    return LLVMI64RetNodeGen.create(source, retValue);
                case FLOAT:
                    return LLVMFloatRetNodeGen.create(source, retValue);
                case DOUBLE:
                    return LLVMDoubleRetNodeGen.create(source, retValue);
                case X86_FP80:
                    return LLVM80BitFloatRetNodeGen.create(source, retValue);
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
        LLVMExpressionNode argNode = LLVMArgNodeGen.create(argIndex);
        if (paramType instanceof PrimitiveType) {
            switch (((PrimitiveType) paramType).getPrimitiveKind()) {
                case I8:
                    return LLVMI8ProfiledValueNodeGen.create(argNode);
                case I32:
                    return LLVMI32ProfiledValueNodeGen.create(argNode);
                case I64:
                    return LLVMI64ProfiledValueNodeGen.create(argNode);
                case FLOAT:
                    return LLVMFloatProfiledValueNodeGen.create(argNode);
                case DOUBLE:
                    return LLVMDoubleProfiledValueNodeGen.create(argNode);
                case I1:
                    return LLVMI1ProfiledValueNodeGen.create(argNode);
                case I16:
                    return LLVMI16ProfiledValueNodeGen.create(argNode);
                default:
                    return argNode;
            }
        } else if (paramType instanceof PointerType) {
            return LLVMAddressProfiledValueNodeGen.create(argNode);
        } else {
            return argNode;
        }
    }

    static LLVMExpressionNode createFunctionCall(LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType functionType, SourceSection sourceSection) {
        LLVMExpressionNode callNode = new LLVMCallNode(functionType, functionNode, argNodes, sourceSection);
        if (functionType.getReturnType() instanceof PrimitiveType) {
            switch (((PrimitiveType) functionType.getReturnType()).getPrimitiveKind()) {
                case I8:
                    return LLVMI8ProfiledValueNodeGen.create(callNode);
                case I32:
                    return LLVMI32ProfiledValueNodeGen.create(callNode);
                case I64:
                    return LLVMI64ProfiledValueNodeGen.create(callNode);
                case FLOAT:
                    return LLVMFloatProfiledValueNodeGen.create(callNode);
                case DOUBLE:
                    return LLVMDoubleProfiledValueNodeGen.create(callNode);
                case I1:
                    return LLVMI1ProfiledValueNodeGen.create(callNode);
                case I16:
                    return LLVMI16ProfiledValueNodeGen.create(callNode);
                default:
                    return callNode;
            }
        } else if (functionType.getReturnType() instanceof PointerType) {
            return LLVMAddressProfiledValueNodeGen.create(callNode);
        } else {
            return callNode;
        }
    }

    static LLVMControlFlowNode createFunctionInvoke(FrameSlot resultLocation, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type,
                    int normalIndex, int unwindIndex, LLVMExpressionNode normalPhiWriteNodes, LLVMExpressionNode unwindPhiWriteNodes, SourceSection sourceSection) {
        return new LLVMInvokeNode.LLVMFunctionInvokeNode(type, resultLocation, functionNode, argNodes, normalIndex, unwindIndex, normalPhiWriteNodes, unwindPhiWriteNodes,
                        sourceSection);
    }

    static RootNode createGlobalRootNodeWrapping(LLVMLanguage language, RootCallTarget mainCallTarget, Type returnType) {
        if (returnType instanceof VoidType) {
            return new LLVMMainFunctionReturnValueRootNode.LLVMMainFunctionReturnVoidRootNode(language, mainCallTarget);
        } else if (returnType instanceof VariableBitWidthType) {
            return new LLVMMainFunctionReturnValueRootNode.LLVMMainFunctionReturnIVarBitRootNode(language, mainCallTarget);
        } else if (returnType instanceof PrimitiveType) {
            switch (((PrimitiveType) returnType).getPrimitiveKind()) {
                case I1:
                    return new LLVMMainFunctionReturnValueRootNode.LLVMMainFunctionReturnI1RootNode(language, mainCallTarget);
                case I8:
                case I16:
                case I32:
                case I64:
                case FLOAT:
                case DOUBLE:
                    return new LLVMMainFunctionReturnValueRootNode.LLVMMainFunctionReturnNumberRootNode(language, mainCallTarget);
                default:
                    throw new AssertionError(returnType);
            }
        }
        throw new AssertionError(returnType);
    }

}
