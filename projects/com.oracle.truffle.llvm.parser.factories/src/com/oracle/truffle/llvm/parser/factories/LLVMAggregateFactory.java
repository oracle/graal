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

import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMAddressNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMFunctionNode;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVM80BitFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMDoubleNode;
import com.oracle.truffle.llvm.nodes.impl.base.floating.LLVMFloatNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI16Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI1Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI64Node;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI8Node;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMInsertValueNode.LLVMInsertDoubleValueNode;
import com.oracle.truffle.llvm.nodes.impl.memory.LLVMInsertValueNode.LLVMInsertFloatValueNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVM80BitFloatStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMAddressStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMCompoundStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMDoubleStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMEmptyStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMFloatStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMFunctionStructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMI16StructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMI1StructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMI32StructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMI64StructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vars.StructLiteralNode.LLVMI8StructWriteNode;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMExtractValueNodeFactory.LLVMExtract80BitFloatValueNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMExtractValueNodeFactory.LLVMExtractAddressValueNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMExtractValueNodeFactory.LLVMExtractDoubleValueNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMExtractValueNodeFactory.LLVMExtractFloatValueNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMExtractValueNodeFactory.LLVMExtractI16ValueNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMExtractValueNodeFactory.LLVMExtractI1ValueNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMExtractValueNodeFactory.LLVMExtractI32ValueNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMExtractValueNodeFactory.LLVMExtractI64ValueNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vector.LLVMExtractValueNodeFactory.LLVMExtractI8ValueNodeGen;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.base.model.LLVMToBitcodeAdapter;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.util.LLVMTypeHelperImpl;

public final class LLVMAggregateFactory {

    private LLVMAggregateFactory() {
    }

    public static LLVMExpressionNode createExtractValue(LLVMBaseType type, LLVMAddressNode targetAddress) {
        switch (type) {
            case I1:
                return LLVMExtractI1ValueNodeGen.create(targetAddress);
            case I8:
                return LLVMExtractI8ValueNodeGen.create(targetAddress);
            case I16:
                return LLVMExtractI16ValueNodeGen.create(targetAddress);
            case I32:
                return LLVMExtractI32ValueNodeGen.create(targetAddress);
            case I64:
                return LLVMExtractI64ValueNodeGen.create(targetAddress);
            case FLOAT:
                return LLVMExtractFloatValueNodeGen.create(targetAddress);
            case DOUBLE:
                return LLVMExtractDoubleValueNodeGen.create(targetAddress);
            case X86_FP80:
                return LLVMExtract80BitFloatValueNodeGen.create(targetAddress);
            case ADDRESS:
            case STRUCT:
                return LLVMExtractAddressValueNodeGen.create(targetAddress);
            default:
                throw new AssertionError(type);
        }
    }

    public static LLVMExpressionNode createInsertValue(LLVMAddressNode resultAggregate, LLVMAddressNode sourceAggregate, int size, int offset, LLVMExpressionNode valueToInsert,
                    LLVMBaseType llvmType) {
        switch (llvmType) {
            case FLOAT:
                return new LLVMInsertFloatValueNode(sourceAggregate, resultAggregate, size, offset, (LLVMFloatNode) valueToInsert);
            case DOUBLE:
                return new LLVMInsertDoubleValueNode(sourceAggregate, resultAggregate, size, offset, (LLVMDoubleNode) valueToInsert);
            default:
                throw new AssertionError(llvmType);
        }
    }

    public static LLVMExpressionNode createStructConstantNode(LLVMParserRuntime runtime, Type structType, boolean packed, Type[] types, LLVMExpressionNode[] constants) {
        int[] offsets = new int[types.length];
        LLVMStructWriteNode[] nodes = new LLVMStructWriteNode[types.length];
        int currentOffset = 0;
        int structSize = ((LLVMTypeHelperImpl) runtime.getTypeHelper()).getByteSize(structType);
        int structAlignment = ((LLVMTypeHelperImpl) runtime.getTypeHelper()).getAlignmentByte(structType);
        LLVMExpressionNode alloc = runtime.allocateFunctionLifetime(LLVMToBitcodeAdapter.unresolveType(structType), structSize, structAlignment);
        for (int i = 0; i < types.length; i++) {
            Type resolvedType = types[i];
            if (!packed) {
                currentOffset += ((LLVMTypeHelperImpl) runtime.getTypeHelper()).computePaddingByte(currentOffset, resolvedType);
            }
            offsets[i] = currentOffset;
            int byteSize = ((LLVMTypeHelperImpl) runtime.getTypeHelper()).getByteSize(resolvedType);
            nodes[i] = createStructWriteNode(runtime, constants[i], resolvedType);
            currentOffset += byteSize;
        }
        return new StructLiteralNode(offsets, nodes, (LLVMAddressNode) alloc);
    }

    private static LLVMStructWriteNode createStructWriteNode(LLVMParserRuntime runtime, LLVMExpressionNode parsedConstant, Type resolvedType) {
        int byteSize = ((LLVMTypeHelperImpl) runtime.getTypeHelper()).getByteSize(resolvedType);
        LLVMBaseType llvmType = LLVMTypeHelperImpl.getLLVMType(resolvedType).getType();
        switch (llvmType) {
            case I1:
                return new LLVMI1StructWriteNode((LLVMI1Node) parsedConstant);
            case I8:
                return new LLVMI8StructWriteNode((LLVMI8Node) parsedConstant);
            case I16:
                return new LLVMI16StructWriteNode((LLVMI16Node) parsedConstant);
            case I32:
                return new LLVMI32StructWriteNode((LLVMI32Node) parsedConstant);
            case I64:
                return new LLVMI64StructWriteNode((LLVMI64Node) parsedConstant);
            case FLOAT:
                return new LLVMFloatStructWriteNode((LLVMFloatNode) parsedConstant);
            case DOUBLE:
                return new LLVMDoubleStructWriteNode((LLVMDoubleNode) parsedConstant);
            case X86_FP80:
                return new LLVM80BitFloatStructWriteNode((LLVM80BitFloatNode) parsedConstant);
            case ARRAY:
            case STRUCT:
                if (byteSize == 0) {
                    return new LLVMEmptyStructWriteNode();
                } else {
                    return new LLVMCompoundStructWriteNode((LLVMAddressNode) parsedConstant, byteSize);
                }
            case ADDRESS:
                return new LLVMAddressStructWriteNode((LLVMAddressNode) parsedConstant);
            case FUNCTION_ADDRESS:
                return new LLVMFunctionStructWriteNode((LLVMFunctionNode) parsedConstant);
            default:
                throw new AssertionError(llvmType);
        }
    }

}
