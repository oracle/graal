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

import com.oracle.truffle.llvm.nodes.base.LLVMStructWriteNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStructWriteNodeFactory.LLVMCompoundStructWriteNodeGen;
import com.oracle.truffle.llvm.nodes.base.LLVMStructWriteNodeFactory.LLVMEmptyStructWriteNodeGen;
import com.oracle.truffle.llvm.nodes.base.LLVMStructWriteNodeFactory.LLVMPrimitiveStructWriteNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMInsertValueNode.LLVMInsertAddressValueNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMInsertValueNode.LLVMInsertDoubleValueNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMInsertValueNode.LLVMInsertFloatValueNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMInsertValueNode.LLVMInsertI8ValueNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMInsertValueNode.LLVMInsertI16ValueNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMInsertValueNode.LLVMInsertI32ValueNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMInsertValueNode.LLVMInsertI64ValueNode;
import com.oracle.truffle.llvm.nodes.vars.StructLiteralNode;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtract80BitFloatValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractAddressValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractDoubleValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractFloatValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractI16ValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractI1ValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractI32ValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractI64ValueNodeGen;
import com.oracle.truffle.llvm.nodes.vector.LLVMExtractValueNodeFactory.LLVMExtractI8ValueNodeGen;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;

final class LLVMAggregateFactory {

    private LLVMAggregateFactory() {
    }

    static LLVMExpressionNode createExtractValue(Type type, LLVMExpressionNode targetAddress) {
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
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
                default:
                    throw new AssertionError(type);
            }
        } else if (type instanceof PointerType || type instanceof StructureType || type instanceof ArrayType) {
            return LLVMExtractAddressValueNodeGen.create(targetAddress);
        } else {
            throw new AssertionError(type);
        }
    }

    static LLVMExpressionNode createInsertValue(LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, int size, int offset,
                    LLVMExpressionNode valueToInsert, Type llvmType) {
        if (llvmType instanceof PrimitiveType) {
            switch (((PrimitiveType) llvmType).getPrimitiveKind()) {
                case I8:
                    return new LLVMInsertI8ValueNode(sourceAggregate, resultAggregate, size, offset, valueToInsert);
                case I16:
                    return new LLVMInsertI16ValueNode(sourceAggregate, resultAggregate, size, offset, valueToInsert);
                case I32:
                    return new LLVMInsertI32ValueNode(sourceAggregate, resultAggregate, size, offset, valueToInsert);
                case I64:
                    return new LLVMInsertI64ValueNode(sourceAggregate, resultAggregate, size, offset, valueToInsert);
                case FLOAT:
                    return new LLVMInsertFloatValueNode(sourceAggregate, resultAggregate, size, offset, valueToInsert);
                case DOUBLE:
                    return new LLVMInsertDoubleValueNode(sourceAggregate, resultAggregate, size, offset, valueToInsert);
                default:
                    throw new AssertionError(llvmType);
            }
        } else if (llvmType instanceof PointerType) {
            return new LLVMInsertAddressValueNode(sourceAggregate, resultAggregate, size, offset, valueToInsert);
        } else {
            throw new AssertionError(llvmType);
        }
    }

    static LLVMExpressionNode createStructConstantNode(LLVMParserRuntime runtime, Type structType, boolean packed, Type[] types, LLVMExpressionNode[] constants) {
        int[] offsets = new int[types.length];
        LLVMStructWriteNode[] nodes = new LLVMStructWriteNode[types.length];
        int currentOffset = 0;
        int structSize = runtime.getByteSize(structType);
        int structAlignment = runtime.getByteAlignment(structType);
        LLVMExpressionNode alloc = runtime.allocateFunctionLifetime(structType, structSize, structAlignment);
        for (int i = 0; i < types.length; i++) {
            Type resolvedType = types[i];
            if (!packed) {
                currentOffset += runtime.getBytePadding(currentOffset, resolvedType);
            }
            offsets[i] = currentOffset;
            int byteSize = runtime.getByteSize(resolvedType);
            nodes[i] = createStructWriteNode(runtime, resolvedType);
            currentOffset += byteSize;
        }
        return new StructLiteralNode(offsets, nodes, constants, alloc);
    }

    private static LLVMStructWriteNode createStructWriteNode(LLVMParserRuntime runtime, Type resolvedType) {
        if (resolvedType instanceof ArrayType || resolvedType instanceof StructureType) {
            int byteSize = runtime.getByteSize(resolvedType);
            if (byteSize == 0) {
                return LLVMEmptyStructWriteNodeGen.create();
            } else {
                return LLVMCompoundStructWriteNodeGen.create(byteSize);
            }
        } else {
            return LLVMPrimitiveStructWriteNodeGen.create();
        }
    }

}
