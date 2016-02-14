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

import com.intel.llvm.ireditor.types.ResolvedType;
import com.oracle.truffle.llvm.nodes.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.base.integers.LLVMIVarBitNode;
import com.oracle.truffle.llvm.nodes.base.vector.LLVMVectorNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMLoadVectorNodeFactory.LLVMLoadDoubleVectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMLoadVectorNodeFactory.LLVMLoadFloatVectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMLoadVectorNodeFactory.LLVMLoadI16VectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMLoadVectorNodeFactory.LLVMLoadI1VectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMLoadVectorNodeFactory.LLVMLoadI32VectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMLoadVectorNodeFactory.LLVMLoadI64VectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMLoadVectorNodeFactory.LLVMLoadI8VectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVM80BitFloatStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMAdressStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMDoubleStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMFloatStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMFunctionStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMI16StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMI1StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMIVarBitStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMStructStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreVectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVM80BitFloatDirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMAddressDirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMFunctionDirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMIVarBitDirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMStructDirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDoubleLoadNodeFactory.LLVMDoubleDirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDoubleLoadNodeFactory.LLVMDoubleProfilingLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMFloatLoadNodeFactory.LLVMFloatDirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMFloatLoadNodeFactory.LLVMFloatProfilingLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI16LoadNode.LLVMI16UninitializedLoadNode;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI16LoadNodeFactory.LLVMI16DirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI1LoadNode.LLVMI1UninitializedLoadNode;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI1LoadNodeFactory.LLVMI1DirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI32LoadNodeFactory.LLVMI32DirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI32LoadNodeFactory.LLVMI32ProfilingLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI64LoadNodeFactory.LLVMI64DirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI64LoadNodeFactory.LLVMI64ProfilingLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI8LoadNodeFactory.LLVMI8DirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI8LoadNodeFactory.LLVMI8ProfilingLoadNodeGen;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.util.LLVMTypeHelper;

public final class LLVMMemoryReadWriteFactory {

    private LLVMMemoryReadWriteFactory() {
    }

    public static LLVMExpressionNode createLoad(ResolvedType resolvedResultType, LLVMAddressNode loadTarget, LLVMParserRuntime runtime) {
        LLVMBaseType resultType = LLVMTypeHelper.getLLVMType(resolvedResultType);
        if (runtime.getOptimizationConfiguration().valueProfileMemoryReads()) {
            switch (resultType) {
                case I1:
                    return new LLVMI1UninitializedLoadNode(loadTarget);
                case I8:
                    return LLVMI8ProfilingLoadNodeGen.create(loadTarget);
                case I16:
                    return new LLVMI16UninitializedLoadNode(loadTarget);
                case I32:
                    return LLVMI32ProfilingLoadNodeGen.create(loadTarget);
                case I64:
                    return LLVMI64ProfilingLoadNodeGen.create(loadTarget);
                case FLOAT:
                    return LLVMFloatProfilingLoadNodeGen.create(loadTarget);
                case DOUBLE:
                    return LLVMDoubleProfilingLoadNodeGen.create(loadTarget);
                default:
                    // fall through and instantiate a direct load node
            }
        }
        switch (resultType) {
            case I1:
                return LLVMI1DirectLoadNodeGen.create(loadTarget);
            case I8:
                return LLVMI8DirectLoadNodeGen.create(loadTarget);
            case I16:
                return LLVMI16DirectLoadNodeGen.create(loadTarget);
            case I32:
                return LLVMI32DirectLoadNodeGen.create(loadTarget);
            case I64:
                return LLVMI64DirectLoadNodeGen.create(loadTarget);
            case I_VAR_BITWIDTH:
                int bitWidth = resolvedResultType.getBits().intValue();
                return LLVMIVarBitDirectLoadNodeGen.create(loadTarget, bitWidth);
            case FLOAT:
                return LLVMFloatDirectLoadNodeGen.create(loadTarget);
            case DOUBLE:
                return LLVMDoubleDirectLoadNodeGen.create(loadTarget);
            case X86_FP80:
                return LLVM80BitFloatDirectLoadNodeGen.create(loadTarget);
            case ADDRESS:
                return LLVMAddressDirectLoadNodeGen.create(loadTarget);
            case FUNCTION_ADDRESS:
                return LLVMFunctionDirectLoadNodeGen.create(loadTarget);
            case STRUCT:
            case ARRAY:
                return LLVMStructDirectLoadNodeGen.create(loadTarget);
            default:
                break;
        }
        if (resolvedResultType.isVector()) {
            int size = resolvedResultType.asVector().getSize();
            switch (resultType) {
                case I1_VECTOR:
                    return LLVMLoadI1VectorNodeGen.create(loadTarget, size);
                case I8_VECTOR:
                    return LLVMLoadI8VectorNodeGen.create(loadTarget, size);
                case I16_VECTOR:
                    return LLVMLoadI16VectorNodeGen.create(loadTarget, size);
                case I32_VECTOR:
                    return LLVMLoadI32VectorNodeGen.create(loadTarget, size);
                case I64_VECTOR:
                    return LLVMLoadI64VectorNodeGen.create(loadTarget, size);
                case FLOAT_VECTOR:
                    return LLVMLoadFloatVectorNodeGen.create(loadTarget, size);
                case DOUBLE_VECTOR:
                    return LLVMLoadDoubleVectorNodeGen.create(loadTarget, size);
                default:
                    throw new AssertionError(resultType);
            }
        } else {
            throw new AssertionError(resultType);
        }
    }

    public static LLVMNode createStore(LLVMAddressNode pointerNode, LLVMExpressionNode valueNode, ResolvedType type) {
        LLVMBaseType llvmType = LLVMTypeHelper.getLLVMType(type);
        switch (llvmType) {
            case I1:
                return LLVMI1StoreNodeGen.create(pointerNode, (LLVMI1Node) valueNode);
            case I8:
                return LLVMI8StoreNodeGen.create(pointerNode, (LLVMI8Node) valueNode);
            case I16:
                return LLVMI16StoreNodeGen.create(pointerNode, (LLVMI16Node) valueNode);
            case I32:
                return LLVMI32StoreNodeGen.create(pointerNode, (LLVMI32Node) valueNode);
            case I64:
                return LLVMI64StoreNodeGen.create(pointerNode, (LLVMI64Node) valueNode);
            case I_VAR_BITWIDTH:
                return LLVMIVarBitStoreNodeGen.create(pointerNode, (LLVMIVarBitNode) valueNode);
            case FLOAT:
                return LLVMFloatStoreNodeGen.create(pointerNode, (LLVMFloatNode) valueNode);
            case DOUBLE:
                return LLVMDoubleStoreNodeGen.create(pointerNode, (LLVMDoubleNode) valueNode);
            case X86_FP80:
                return LLVM80BitFloatStoreNodeGen.create(pointerNode, (LLVM80BitFloatNode) valueNode);
            case ADDRESS:
                return LLVMAdressStoreNodeGen.create(pointerNode, (LLVMAddressNode) valueNode);
            case FUNCTION_ADDRESS:
                return LLVMFunctionStoreNodeGen.create(pointerNode, (LLVMFunctionNode) valueNode);
            case STRUCT:
            case ARRAY:
                return LLVMStructStoreNodeGen.create(pointerNode, (LLVMAddressNode) valueNode, LLVMTypeHelper.getByteSize(type));
            default:
                break;
        }
        if (type.isVector()) {
            return LLVMStoreVectorNodeGen.create(pointerNode, (LLVMVectorNode) valueNode);
        } else {
            throw new AssertionError(llvmType);
        }
    }

}
