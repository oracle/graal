/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDirectLoadNode.LLVMGlobalVariableDirectLoadNode;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVM80BitFloatDirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMAddressDirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMFunctionDirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMIVarBitDirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMStructDirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMDoubleLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMFloatLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI16LoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI1LoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI32LoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI64LoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI8LoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadAddressVectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadDoubleVectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadFloatVectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI16VectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI1VectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI32VectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI64VectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI8VectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.rmw.LLVMI16RMWNodeFactory;
import com.oracle.truffle.llvm.nodes.memory.rmw.LLVMI1RMWNodeFactory;
import com.oracle.truffle.llvm.nodes.memory.rmw.LLVMI32RMWNodeFactory;
import com.oracle.truffle.llvm.nodes.memory.rmw.LLVMI64RMWNodeFactory;
import com.oracle.truffle.llvm.nodes.memory.rmw.LLVMI8RMWNodeFactory;
import com.oracle.truffle.llvm.nodes.memory.store.LLVM80BitFloatStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMAddressStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMDoubleStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMFloatStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMFunctionStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMGlobalVariableStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI16StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI1StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMIVarBitStoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMStoreVectorNodeGen;
import com.oracle.truffle.llvm.nodes.memory.store.LLVMStructStoreNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMAccessGlobalVariableStorageNode;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.model.enums.ReadModifyWriteOperator;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;

final class LLVMMemoryReadWriteFactory {

    private LLVMMemoryReadWriteFactory() {
    }

    static LLVMExpressionNode createLoad(Type resolvedResultType, LLVMExpressionNode loadTarget) {
        if (resolvedResultType instanceof VectorType) {
            return createLoadVector((VectorType) resolvedResultType, loadTarget, ((VectorType) resolvedResultType).getNumberOfElements());
        } else {
            int bits = resolvedResultType instanceof VariableBitWidthType
                            ? resolvedResultType.getBitSize()
                            : 0;
            return createLoad(resolvedResultType, loadTarget, bits);
        }
    }

    private static LLVMExpressionNode createLoad(Type resultType, LLVMExpressionNode loadTarget, int bits) {
        if (resultType instanceof PrimitiveType) {
            switch (((PrimitiveType) resultType).getPrimitiveKind()) {
                case I1:
                    return LLVMI1LoadNodeGen.create(loadTarget);
                case I8:
                    return LLVMI8LoadNodeGen.create(loadTarget);
                case I16:
                    return LLVMI16LoadNodeGen.create(loadTarget);
                case I32:
                    return LLVMI32LoadNodeGen.create(loadTarget);
                case I64:
                    return LLVMI64LoadNodeGen.create(loadTarget);
                case FLOAT:
                    return LLVMFloatLoadNodeGen.create(loadTarget);
                case DOUBLE:
                    return LLVMDoubleLoadNodeGen.create(loadTarget);
                case X86_FP80:
                    return LLVM80BitFloatDirectLoadNodeGen.create(loadTarget);
                default:
                    throw new AssertionError(resultType);
            }
        } else if (resultType instanceof VariableBitWidthType) {
            return LLVMIVarBitDirectLoadNodeGen.create(loadTarget, bits);
        } else if (Type.isFunctionOrFunctionPointer(resultType)) {
            return LLVMFunctionDirectLoadNodeGen.create(loadTarget);
        } else if (resultType instanceof StructureType || resultType instanceof ArrayType) {
            return LLVMStructDirectLoadNodeGen.create(loadTarget);
        } else if (resultType instanceof PointerType) {
            if (loadTarget instanceof LLVMAccessGlobalVariableStorageNode) {
                return new LLVMGlobalVariableDirectLoadNode(((LLVMAccessGlobalVariableStorageNode) loadTarget).getDescriptor());
            } else {
                return LLVMAddressDirectLoadNodeGen.create(loadTarget);
            }
        } else {
            throw new AssertionError(resultType);
        }
    }

    private static LLVMExpressionNode createLoadVector(VectorType resultType, LLVMExpressionNode loadTarget, int size) {
        Type elemType = resultType.getElementType();
        if (elemType instanceof PrimitiveType) {

            switch (((PrimitiveType) elemType).getPrimitiveKind()) {
                case I1:
                    return LLVMLoadI1VectorNodeGen.create(loadTarget, size);
                case I8:
                    return LLVMLoadI8VectorNodeGen.create(loadTarget, size);
                case I16:
                    return LLVMLoadI16VectorNodeGen.create(loadTarget, size);
                case I32:
                    return LLVMLoadI32VectorNodeGen.create(loadTarget, size);
                case I64:
                    return LLVMLoadI64VectorNodeGen.create(loadTarget, size);
                case FLOAT:
                    return LLVMLoadFloatVectorNodeGen.create(loadTarget, size);
                case DOUBLE:
                    return LLVMLoadDoubleVectorNodeGen.create(loadTarget, size);
                default:
                    throw new AssertionError(elemType + " vectors not supported");
            }
        } else if (elemType instanceof PointerType) {
            return LLVMLoadAddressVectorNodeGen.create(loadTarget, size);
        } else {
            throw new AssertionError(elemType + " vectors not supported");
        }
    }

    static LLVMExpressionNode createStore(LLVMParserRuntime runtime, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type, SourceSection source) {
        return createStore(pointerNode, valueNode, type, runtime.getContext().getByteSize(type), source);
    }

    private static LLVMExpressionNode createStore(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type, int size, SourceSection source) {
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return LLVMI1StoreNodeGen.create(source, pointerNode, valueNode);
                case I8:
                    return LLVMI8StoreNodeGen.create(source, pointerNode, valueNode);
                case I16:
                    return LLVMI16StoreNodeGen.create(source, pointerNode, valueNode);
                case I32:
                    return LLVMI32StoreNodeGen.create(source, pointerNode, valueNode);
                case I64:
                    return LLVMI64StoreNodeGen.create(source, pointerNode, valueNode);
                case FLOAT:
                    return LLVMFloatStoreNodeGen.create(source, pointerNode, valueNode);
                case DOUBLE:
                    return LLVMDoubleStoreNodeGen.create(source, pointerNode, valueNode);
                case X86_FP80:
                    return LLVM80BitFloatStoreNodeGen.create(source, pointerNode, valueNode);
                default:
                    throw new AssertionError(type);
            }
        } else if (type instanceof VariableBitWidthType) {
            return LLVMIVarBitStoreNodeGen.create((VariableBitWidthType) type, source, pointerNode, valueNode);
        } else if (Type.isFunctionOrFunctionPointer(type)) {
            return LLVMFunctionStoreNodeGen.create(type, source, pointerNode, valueNode);
        } else if (type instanceof StructureType || type instanceof ArrayType) {
            return LLVMStructStoreNodeGen.create(type, source, pointerNode, valueNode, size);
        } else if (type instanceof PointerType) {
            if (pointerNode instanceof LLVMAccessGlobalVariableStorageNode) {
                return LLVMGlobalVariableStoreNodeGen.create(((LLVMAccessGlobalVariableStorageNode) pointerNode).getDescriptor(), source, valueNode);
            } else {
                return LLVMAddressStoreNodeGen.create(type, source, pointerNode, valueNode);
            }
        } else if (type instanceof VectorType) {
            return LLVMStoreVectorNodeGen.create(type, source, pointerNode, valueNode);
        } else {
            throw new AssertionError(type);
        }
    }

    static LLVMExpressionNode createReadModifyWrite(ReadModifyWriteOperator operator, LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
        if (type instanceof PrimitiveType) {
            switch (operator) {
                case XCHG:
                    switch (((PrimitiveType) type).getPrimitiveKind()) {
                        case I1:
                            return LLVMI1RMWNodeFactory.LLVMI1RMWXchgNodeGen.create(pointerNode, valueNode);
                        case I8:
                            return LLVMI8RMWNodeFactory.LLVMI8RMWXchgNodeGen.create(pointerNode, valueNode);
                        case I16:
                            return LLVMI16RMWNodeFactory.LLVMI16RMWXchgNodeGen.create(pointerNode, valueNode);
                        case I32:
                            return LLVMI32RMWNodeFactory.LLVMI32RMWXchgNodeGen.create(pointerNode, valueNode);
                        case I64:
                            return LLVMI64RMWNodeFactory.LLVMI64RMWXchgNodeGen.create(pointerNode, valueNode);
                        default:
                            throw new AssertionError(type);
                    }
                case ADD:
                    switch (((PrimitiveType) type).getPrimitiveKind()) {
                        case I1:
                            return LLVMI1RMWNodeFactory.LLVMI1RMWAddNodeGen.create(pointerNode, valueNode);
                        case I8:
                            return LLVMI8RMWNodeFactory.LLVMI8RMWAddNodeGen.create(pointerNode, valueNode);
                        case I16:
                            return LLVMI16RMWNodeFactory.LLVMI16RMWAddNodeGen.create(pointerNode, valueNode);
                        case I32:
                            return LLVMI32RMWNodeFactory.LLVMI32RMWAddNodeGen.create(pointerNode, valueNode);
                        case I64:
                            return LLVMI64RMWNodeFactory.LLVMI64RMWAddNodeGen.create(pointerNode, valueNode);
                        default:
                            throw new AssertionError(type);
                    }
                case SUB:
                    switch (((PrimitiveType) type).getPrimitiveKind()) {
                        case I1:
                            return LLVMI1RMWNodeFactory.LLVMI1RMWSubNodeGen.create(pointerNode, valueNode);
                        case I8:
                            return LLVMI8RMWNodeFactory.LLVMI8RMWSubNodeGen.create(pointerNode, valueNode);
                        case I16:
                            return LLVMI16RMWNodeFactory.LLVMI16RMWSubNodeGen.create(pointerNode, valueNode);
                        case I32:
                            return LLVMI32RMWNodeFactory.LLVMI32RMWSubNodeGen.create(pointerNode, valueNode);
                        case I64:
                            return LLVMI64RMWNodeFactory.LLVMI64RMWSubNodeGen.create(pointerNode, valueNode);
                        default:
                            throw new AssertionError(type);
                    }
                case AND:
                    switch (((PrimitiveType) type).getPrimitiveKind()) {
                        case I1:
                            return LLVMI1RMWNodeFactory.LLVMI1RMWAndNodeGen.create(pointerNode, valueNode);
                        case I8:
                            return LLVMI8RMWNodeFactory.LLVMI8RMWAndNodeGen.create(pointerNode, valueNode);
                        case I16:
                            return LLVMI16RMWNodeFactory.LLVMI16RMWAndNodeGen.create(pointerNode, valueNode);
                        case I32:
                            return LLVMI32RMWNodeFactory.LLVMI32RMWAndNodeGen.create(pointerNode, valueNode);
                        case I64:
                            return LLVMI64RMWNodeFactory.LLVMI64RMWAndNodeGen.create(pointerNode, valueNode);
                        default:
                            throw new AssertionError(type);
                    }
                case NAND:
                    switch (((PrimitiveType) type).getPrimitiveKind()) {
                        case I1:
                            return LLVMI1RMWNodeFactory.LLVMI1RMWNandNodeGen.create(pointerNode, valueNode);
                        case I8:
                            return LLVMI8RMWNodeFactory.LLVMI8RMWNandNodeGen.create(pointerNode, valueNode);
                        case I16:
                            return LLVMI16RMWNodeFactory.LLVMI16RMWNandNodeGen.create(pointerNode, valueNode);
                        case I32:
                            return LLVMI32RMWNodeFactory.LLVMI32RMWNandNodeGen.create(pointerNode, valueNode);
                        case I64:
                            return LLVMI64RMWNodeFactory.LLVMI64RMWNandNodeGen.create(pointerNode, valueNode);
                        default:
                            throw new AssertionError(type);
                    }
                case OR:
                    switch (((PrimitiveType) type).getPrimitiveKind()) {
                        case I1:
                            return LLVMI1RMWNodeFactory.LLVMI1RMWOrNodeGen.create(pointerNode, valueNode);
                        case I8:
                            return LLVMI8RMWNodeFactory.LLVMI8RMWOrNodeGen.create(pointerNode, valueNode);
                        case I16:
                            return LLVMI16RMWNodeFactory.LLVMI16RMWOrNodeGen.create(pointerNode, valueNode);
                        case I32:
                            return LLVMI32RMWNodeFactory.LLVMI32RMWOrNodeGen.create(pointerNode, valueNode);
                        case I64:
                            return LLVMI64RMWNodeFactory.LLVMI64RMWOrNodeGen.create(pointerNode, valueNode);
                        default:
                            throw new AssertionError(type);
                    }
                case XOR:
                    switch (((PrimitiveType) type).getPrimitiveKind()) {
                        case I1:
                            return LLVMI1RMWNodeFactory.LLVMI1RMWXorNodeGen.create(pointerNode, valueNode);
                        case I8:
                            return LLVMI8RMWNodeFactory.LLVMI8RMWXorNodeGen.create(pointerNode, valueNode);
                        case I16:
                            return LLVMI16RMWNodeFactory.LLVMI16RMWXorNodeGen.create(pointerNode, valueNode);
                        case I32:
                            return LLVMI32RMWNodeFactory.LLVMI32RMWXorNodeGen.create(pointerNode, valueNode);
                        case I64:
                            return LLVMI64RMWNodeFactory.LLVMI64RMWXorNodeGen.create(pointerNode, valueNode);
                        default:
                            throw new AssertionError(type);
                    }
                case MAX:
                case MIN:
                case UMAX:
                case UMIN:
                default:
                    throw new AssertionError(operator);
            }
        } else {
            throw new AssertionError(type);
        }
    }

}
