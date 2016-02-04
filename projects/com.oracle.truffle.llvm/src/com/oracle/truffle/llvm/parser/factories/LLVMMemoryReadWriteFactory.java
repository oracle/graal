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
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadDirectNodeFactory.LLVMLoadDirect80BitFloatNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadDirectNodeFactory.LLVMLoadDirectAddressNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadDirectNodeFactory.LLVMLoadDirectFunctionNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadDirectNodeFactory.LLVMLoadDirectIVarBitNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadDirectNodeFactory.LLVMLoadDirectStructNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadDoubleNode.LLVMUninitializedLoadDoubleNode;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadDoubleNodeFactory.LLVMLoadDirectDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadFloatNode.LLVMUninitializedLoadFloatNode;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadFloatNodeFactory.LLVMLoadDirectFloatNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadI16Node.LLVMUninitializedLoadI16Node;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadI16NodeFactory.LLVMLoadDirectI16NodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadI1Node.LLVMUninitializedLoadI1Node;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadI1NodeFactory.LLVMLoadDirectI1NodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadI32Node.LLVMUninitializedLoadI32Node;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadI32NodeFactory.LLVMLoadDirectI32NodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadI64Node.LLVMUninitializedLoadI64Node;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadI64NodeFactory.LLVMLoadDirectI64NodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadI8Node.LLVMUninitializedLoadI8Node;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadI8NodeFactory.LLVMLoadDirectI8NodeGen;
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
                    return new LLVMUninitializedLoadI1Node(loadTarget);
                case I8:
                    return new LLVMUninitializedLoadI8Node(loadTarget);
                case I16:
                    return new LLVMUninitializedLoadI16Node(loadTarget);
                case I32:
                    return new LLVMUninitializedLoadI32Node(loadTarget);
                case I64:
                    return new LLVMUninitializedLoadI64Node(loadTarget);
                case FLOAT:
                    return new LLVMUninitializedLoadFloatNode(loadTarget);
                case DOUBLE:
                    return new LLVMUninitializedLoadDoubleNode(loadTarget);
                default:
                    // fall through and instantiate a direct load node
            }
        }
        switch (resultType) {
            case I1:
                return LLVMLoadDirectI1NodeGen.create(loadTarget);
            case I8:
                return LLVMLoadDirectI8NodeGen.create(loadTarget);
            case I16:
                return LLVMLoadDirectI16NodeGen.create(loadTarget);
            case I32:
                return LLVMLoadDirectI32NodeGen.create(loadTarget);
            case I64:
                return LLVMLoadDirectI64NodeGen.create(loadTarget);
            case I_VAR_BITWIDTH:
                int bitWidth = resolvedResultType.getBits().intValue();
                return LLVMLoadDirectIVarBitNodeGen.create(loadTarget, bitWidth);
            case FLOAT:
                return LLVMLoadDirectFloatNodeGen.create(loadTarget);
            case DOUBLE:
                return LLVMLoadDirectDoubleNodeGen.create(loadTarget);
            case X86_FP80:
                return LLVMLoadDirect80BitFloatNodeGen.create(loadTarget);
            case ADDRESS:
                return LLVMLoadDirectAddressNodeGen.create(loadTarget);
            case FUNCTION_ADDRESS:
                return LLVMLoadDirectFunctionNodeGen.create(loadTarget);
            case STRUCT:
            case ARRAY:
                return LLVMLoadDirectStructNodeGen.create(loadTarget);
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
