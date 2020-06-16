/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import java.math.BigInteger;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMDebugGlobalVariable;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugManagedValue;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObjectBuilder;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMSourceTypeFactory;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Value;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.interop.convert.ToAnyLLVMNodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToDoubleNodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToFloatNodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToI16NodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToI1NodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToI32NodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToI64NodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToI8NodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToPointer;
import com.oracle.truffle.llvm.runtime.interop.convert.ToVoidLLVMNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMTo80BitFloatingNodeGen.LLVMBitcastToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMTo80BitFloatingNodeGen.LLVMSignedCastToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMTo80BitFloatingNodeGen.LLVMUnsignedCastToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToAddressNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToDoubleNodeGen.LLVMBitcastToDoubleNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToDoubleNodeGen.LLVMSignedCastToDoubleNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToDoubleNodeGen.LLVMUnsignedCastToDoubleNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToFloatNodeGen.LLVMBitcastToFloatNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToFloatNodeGen.LLVMSignedCastToFloatNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToFloatNodeGen.LLVMUnsignedCastToFloatNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI16NodeGen.LLVMBitcastToI16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI16NodeGen.LLVMSignedCastToI16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI16NodeGen.LLVMUnsignedCastToI16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI1NodeGen.LLVMBitcastToI1NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI1NodeGen.LLVMSignedCastToI1NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI32NodeGen.LLVMBitcastToI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI32NodeGen.LLVMSignedCastToI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI32NodeGen.LLVMUnsignedCastToI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI64NodeGen.LLVMBitcastToI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI64NodeGen.LLVMSignedCastToI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI64NodeGen.LLVMUnsignedCastToI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI8NodeGen.LLVMBitcastToI8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI8NodeGen.LLVMSignedCastToI8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToI8NodeGen.LLVMUnsignedCastToI8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVarINodeGen.LLVMBitcastToIVarNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVarINodeGen.LLVMSignedCastToIVarNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVarINodeGen.LLVMUnsignedCastToIVarNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToDoubleVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToFloatVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToI16VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToI1VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToI32VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToI64VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToI8VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMBitcastToPointerVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMSignedCastToDoubleVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMSignedCastToFloatVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMSignedCastToI16VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMSignedCastToI1VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMSignedCastToI32VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMSignedCastToI64VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorNodeFactory.LLVMSignedCastToI8VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorZeroExtNodeFactory.LLVMUnsignedCastToDoubleVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorZeroExtNodeFactory.LLVMUnsignedCastToFloatVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorZeroExtNodeFactory.LLVMUnsignedCastToI16VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorZeroExtNodeFactory.LLVMUnsignedCastToI1VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorZeroExtNodeFactory.LLVMUnsignedCastToI32VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorZeroExtNodeFactory.LLVMUnsignedCastToI64VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToVectorZeroExtNodeFactory.LLVMUnsignedCastToI8VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug.LLVMDebugSimpleObjectBuilder;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug.LLVMDebugTrapNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug.LLVMToDebugDeclaration;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug.LLVMToDebugValueNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVM80BitFloatLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMDoubleLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMFloatLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI16LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI1LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI32LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI64LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI8LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMIVarBitLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMNativePointerLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMVectorizedGetElementPtrNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDirectLoadNode.LLVMPointerDirectLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVM80BitFloatDirectLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMIVarBitDirectLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMPointerDirectLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMStructDirectLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMFloatLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMFloatLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI1LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI1LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadDoubleVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadFloatVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI16VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI1VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI32VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI64VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI8VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadPointerVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMDoubleStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMFloatStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI1StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMAbstractCompareNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNode.LLVMAbstractI64ArithmeticNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.LLVMDoubleArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.LLVMFP80ArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.LLVMFloatArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.LLVMI16ArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.LLVMI1ArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.LLVMI32ArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.LLVMI8ArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.LLVMIVarBitArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMEqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMFalseCmpNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMNeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMOrderedEqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMOrderedGeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMOrderedGtNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMOrderedLeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMOrderedLtNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMOrderedNeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMOrderedNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMSignedLeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMSignedLtNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMTrueCmpNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMUnorderedEqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMUnorderedGeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMUnorderedGtNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMUnorderedLeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMUnorderedLtNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMUnorderedNeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMUnorderedNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMUnsignedLeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMCompareNodeFactory.LLVMUnsignedLtNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMPointerCompareNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMPointerCompareNode.LLVMNegateNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMVectorArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMVectorCompareNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMAccessSymbolNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMUnsupportedInstructionNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMValueProfilingNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.LLVM80BitFloatReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.LLVMAddressReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.LLVMDoubleReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.LLVMFloatReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.LLVMI16ReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.LLVMI1ReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.LLVMI32ReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.LLVMI64ReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.LLVMI8ReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.LLVMIReadVarBitNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadVectorNodeFactory.LLVMDoubleVectorReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadVectorNodeFactory.LLVMFloatVectorReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadVectorNodeFactory.LLVMI16VectorReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadVectorNodeFactory.LLVMI1VectorReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadVectorNodeFactory.LLVMI32VectorReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadVectorNodeFactory.LLVMI64VectorReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadVectorNodeFactory.LLVMI8VectorReadNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.llvm.runtime.vector.LLVMVector;

public class CommonNodeFactory {

    public CommonNodeFactory() {
    }

    public static LLVMLoadNode createLoadNode(LLVMInteropType.ValueKind kind) {
        switch (kind) {
            case I1:
                return LLVMI1LoadNode.create();
            case I8:
                return LLVMI8LoadNode.create();
            case I16:
                return LLVMI16LoadNode.create();
            case I32:
                return LLVMI32LoadNode.create();
            case I64:
                return LLVMI64LoadNode.create();
            case FLOAT:
                return LLVMFloatLoadNode.create();
            case DOUBLE:
                return LLVMDoubleLoadNode.create();
            case POINTER:
                return LLVMPointerDirectLoadNode.create();
            default:
                throw new IllegalStateException("unexpected interop kind " + kind);
        }
    }

    public static LLVMLoadNode getUncachedLoadNode(LLVMInteropType.ValueKind kind) {
        switch (kind) {
            case I1:
                return LLVMI1LoadNodeGen.getUncached();
            case I8:
                return LLVMI8LoadNodeGen.getUncached();
            case I16:
                return LLVMI16LoadNodeGen.getUncached();
            case I32:
                return LLVMI32LoadNodeGen.getUncached();
            case I64:
                return LLVMI64LoadNodeGen.getUncached();
            case FLOAT:
                return LLVMFloatLoadNodeGen.getUncached();
            case DOUBLE:
                return LLVMDoubleLoadNodeGen.getUncached();
            case POINTER:
                return LLVMPointerDirectLoadNodeGen.getUncached();
            default:
                throw new IllegalStateException("unexpected interop kind " + kind);
        }
    }

    public static LLVMStoreNode createStoreNode(LLVMInteropType.ValueKind kind) {
        switch (kind) {
            case I1:
                return LLVMI1StoreNodeGen.create(null, null);
            case I8:
                return LLVMI8StoreNodeGen.create(null, null);
            case I16:
                return LLVMI16StoreNodeGen.create(null, null);
            case I32:
                return LLVMI32StoreNodeGen.create(null, null);
            case I64:
                return LLVMI64StoreNodeGen.create(null, null);
            case FLOAT:
                return LLVMFloatStoreNodeGen.create(null, null);
            case DOUBLE:
                return LLVMDoubleStoreNodeGen.create(null, null);
            case POINTER:
                return LLVMPointerStoreNodeGen.create(null, null);
            default:
                throw new IllegalStateException("unexpected interop kind " + kind);
        }
    }

    public static LLVMStoreNode getUncachedStoreNode(LLVMInteropType.ValueKind kind) {
        switch (kind) {
            case I1:
                return LLVMI1StoreNodeGen.getUncached();
            case I8:
                return LLVMI8StoreNodeGen.getUncached();
            case I16:
                return LLVMI16StoreNodeGen.getUncached();
            case I32:
                return LLVMI32StoreNodeGen.getUncached();
            case I64:
                return LLVMI64StoreNodeGen.getUncached();
            case FLOAT:
                return LLVMFloatStoreNodeGen.getUncached();
            case DOUBLE:
                return LLVMDoubleStoreNodeGen.getUncached();
            case POINTER:
                return LLVMPointerStoreNodeGen.getUncached();
            default:
                throw new IllegalStateException("unexpected interop kind " + kind);
        }
    }

    public static TruffleObject toGenericDebuggerValue(Object llvmType, Object value, DataLayout dataLayout) {
        final TruffleObject complexObject = asDebuggerIRValue(llvmType, value, dataLayout);
        if (complexObject != null) {
            return complexObject;
        }

        return LLVMDebugManagedValue.create(llvmType, value);
    }

    private static TruffleObject asDebuggerIRValue(Object llvmType, Object value, DataLayout dataLayout) {
        final Type type;
        if (llvmType instanceof Type) {
            type = (Type) llvmType;
        } else {
            return null;
        }

        // e.g. debugger symbols
        if (type instanceof MetaType) {
            return null;
        }

        final LLVMSourceType sourceType = LLVMSourceTypeFactory.resolveType(type, dataLayout);
        if (sourceType == null) {
            return null;
        }

        // after frame-nulling the actual vector length does not correspond to the type anymore
        if (value instanceof LLVMVector && ((LLVMVector) value).getLength() == 0) {
            return null;
        }

        // after frame-nulling the actual bitsize does not correspond to the type anymore
        if (value instanceof LLVMIVarBit && ((LLVMIVarBit) value).getBitSize() == 0) {
            return null;
        }

        final LLVMDebugValue debugValue = createDebugValueBuilder().build(value);
        if (debugValue == LLVMDebugValue.UNAVAILABLE) {
            return null;
        }

        return LLVMDebugObject.create(sourceType, 0L, debugValue, null);
    }

    public static LLVMDebugObjectBuilder createDebugStaticValue(LLVMContext context, LLVMExpressionNode valueNode, boolean isGlobal) {
        LLVMDebugValue.Builder toDebugNode = createDebugValueBuilder();

        Object value = null;
        if (isGlobal) {
            assert valueNode instanceof LLVMAccessSymbolNode;
            LLVMAccessSymbolNode node = (LLVMAccessSymbolNode) valueNode;
            LLVMSymbol symbol = node.getDescriptor();
            if (symbol.isGlobalVariable()) {
                value = new LLVMDebugGlobalVariable(symbol.asGlobalVariable(), context);
            } else {
                throw new IllegalStateException(symbol.getKind() + " symbol: " + symbol.getName());
            }
        } else {
            try {
                value = valueNode.executeGeneric(null);
            } catch (Throwable ignored) {
                // constant values should not need frame access
            }
        }

        if (value != null) {
            return LLVMDebugSimpleObjectBuilder.create(toDebugNode, value);
        } else {
            return LLVMDebugObjectBuilder.UNAVAILABLE;
        }
    }

    /**
     * The rules for whether to build a scalar-getelementptr or vector-getelementptr node:
     *
     * <pre>
     * S = scalar node
     * V = vector node
     * BC = broadcast node
     * GEP = scalar getelementptr node
     * VGEP = vector getelementptr node
     *
     * The BasePointer (BP) could either be a scalar, vector or GEP/VGEP node coming from the
     * previous indexing dimension. The Index can only either be a scalar or a vector.
     *
     * Pointers, arrays and structures are considered scalars.
     *
     * (BP, Idx) --> Next OP(PTR, IDX)
     *
     * -------------------------------
     *
     * 0: (S, S) --> GEP(S, S)
     * 1: (S, V) --> VGEP(BC(S), V)
     * 2: (V, S) --> VGEP(V, BC(S))
     * 3: (V, V) --> VGEP(V, V)
     * 4: (GEP, S) --> GEP(GEP, S)
     * 5: (GEP, V) --> VGEP(BC(GEP), V)
     * 6: (VGEP, S) --> VGEP(VGEP, BC(S))
     * 7: (VGEP, V) --> VGEP(VGEP, V)
     * </pre>
     */
    public static LLVMExpressionNode createElementPointer(NodeFactory nodeFactory, long indexedTypeLength, Type currentType, LLVMExpressionNode currentAddress, LLVMExpressionNode indexNode,
                    Type indexType,
                    final boolean wasVectorized) {
        if (wasVectorized) {
            // Cases 2, 3, 6, 7
            if (indexType instanceof VectorType) {
                // Cases 3, 7
                return nodeFactory.createVectorizedTypedElementPointer(indexedTypeLength, currentType, currentAddress, indexNode);
            } else {
                // Cases 2, 6
                int length = ((VectorType) currentType).getNumberOfElementsInt();
                return nodeFactory.createVectorizedTypedElementPointer(indexedTypeLength, currentType, currentAddress,
                                LLVMVectorizedGetElementPtrNodeGen.IndexVectorBroadcastNodeGen.create(length, indexNode));
            }
        } else {
            // Cases 0, 1, 4, 5
            if (indexType instanceof VectorType) {
                // Cases 1, 5
                int length = ((VectorType) indexType).getNumberOfElementsInt();
                return nodeFactory.createVectorizedTypedElementPointer(indexedTypeLength, currentType, LLVMVectorizedGetElementPtrNodeGen.ResultVectorBroadcastNodeGen.create(length, currentAddress),
                                indexNode);
            } else {
                // Cases 0, 4
                return nodeFactory.createTypedElementPointer(indexedTypeLength, currentType, currentAddress, indexNode);
            }
        }
    }

    public static LLVMExpressionNode createNestedElementPointerNode(
                    NodeFactory nodeFactory,
                    DataLayout dataLayout,
                    LLVMExpressionNode[] indexNodes,
                    Long[] indexConstants,
                    Type[] indexTypes,
                    LLVMExpressionNode curAddress,
                    Type curType) {
        try {
            LLVMExpressionNode currentAddress = curAddress;
            Type currentType = curType;

            boolean wasVectorized = currentType instanceof VectorType;
            if (wasVectorized) {
                VectorType vectorType = (VectorType) currentType;
                currentType = vectorType.getElementType();
            }

            for (int i = 0; i < indexNodes.length; i++) {
                Type indexType = indexTypes[i];
                Long indexInteger = indexConstants[i];
                if (indexInteger == null) {
                    // the index is determined at runtime
                    if (currentType instanceof StructureType) {
                        // according to http://llvm.org/docs/LangRef.html#getelementptr-instruction
                        throw new LLVMParserException("Indices on structs must be constant integers!");
                    }
                    AggregateType aggregate = (AggregateType) currentType;
                    long indexedTypeLength = aggregate.getOffsetOf(1, dataLayout);
                    currentType = aggregate.getElementType(1);
                    currentAddress = createElementPointer(nodeFactory, indexedTypeLength, currentType, currentAddress, indexNodes[i], indexType, wasVectorized);
                    wasVectorized = currentAddress instanceof LLVMVectorizedGetElementPtrNodeGen;
                } else {
                    // the index is a constant integer
                    AggregateType aggregate = (AggregateType) currentType;
                    long addressOffset = aggregate.getOffsetOf(indexInteger, dataLayout);
                    currentType = aggregate.getElementType(indexInteger);

                    // creating a pointer inserts type information, this needs to happen for the
                    // address computed by getelementptr even if it is the same as the basepointer
                    if (addressOffset != 0 || i == indexNodes.length - 1) {
                        LLVMExpressionNode indexNode;
                        if (indexType == PrimitiveType.I32) {
                            indexNode = nodeFactory.createLiteral(1, PrimitiveType.I32);
                        } else if (indexType == PrimitiveType.I64) {
                            indexNode = nodeFactory.createLiteral(1L, PrimitiveType.I64);
                        } else {
                            throw new AssertionError(indexType);
                        }
                        currentAddress = createElementPointer(nodeFactory, addressOffset, currentType, currentAddress, indexNode, indexType, wasVectorized);
                        wasVectorized = currentAddress instanceof LLVMVectorizedGetElementPtrNodeGen;
                    }
                }
            }

            return currentAddress;
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
    }

    public static LLVMExpressionNode createFrameRead(Type llvmType, FrameSlot frameSlot) {
        if (llvmType instanceof PrimitiveType) {
            switch (((PrimitiveType) llvmType).getPrimitiveKind()) {
                case I1:
                    return LLVMI1ReadNodeGen.create(frameSlot);
                case I8:
                    return LLVMI8ReadNodeGen.create(frameSlot);
                case I16:
                    return LLVMI16ReadNodeGen.create(frameSlot);
                case I32:
                    return LLVMI32ReadNodeGen.create(frameSlot);
                case I64:
                    return LLVMI64ReadNodeGen.create(frameSlot);
                case FLOAT:
                    return LLVMFloatReadNodeGen.create(frameSlot);
                case DOUBLE:
                    return LLVMDoubleReadNodeGen.create(frameSlot);
                case X86_FP80:
                    return LLVM80BitFloatReadNodeGen.create(frameSlot);
            }
        } else if (llvmType instanceof VectorType) {
            Type elemType = ((VectorType) llvmType).getElementType();
            if (elemType instanceof PrimitiveType) {
                switch (((PrimitiveType) elemType).getPrimitiveKind()) {
                    case I1:
                        return LLVMI1VectorReadNodeGen.create(frameSlot);
                    case I8:
                        return LLVMI8VectorReadNodeGen.create(frameSlot);
                    case I16:
                        return LLVMI16VectorReadNodeGen.create(frameSlot);
                    case I32:
                        return LLVMI32VectorReadNodeGen.create(frameSlot);
                    case I64:
                        return LLVMI64VectorReadNodeGen.create(frameSlot);
                    case FLOAT:
                        return LLVMFloatVectorReadNodeGen.create(frameSlot);
                    case DOUBLE:
                        return LLVMDoubleVectorReadNodeGen.create(frameSlot);
                }
            } else if (elemType instanceof PointerType || elemType instanceof FunctionType) {
                return LLVMI64VectorReadNodeGen.create(frameSlot);
            }
        } else if (llvmType instanceof VariableBitWidthType) {
            return LLVMIReadVarBitNodeGen.create(frameSlot);
        } else if (llvmType instanceof PointerType || llvmType instanceof FunctionType) {
            return LLVMAddressReadNodeGen.create(frameSlot);
        } else if (llvmType instanceof StructureType || llvmType instanceof ArrayType) {
            return LLVMAddressReadNodeGen.create(frameSlot);
        } else if (llvmType instanceof VoidType) {
            return LLVMUnsupportedInstructionNode.createExpression(UnsupportedReason.PARSER_ERROR_VOID_SLOT);
        } else if (llvmType == MetaType.DEBUG) {
            return LLVMReadNodeFactory.LLVMDebugReadNodeGen.create(frameSlot);
        }
        throw new AssertionError(llvmType + " for " + frameSlot.getIdentifier());
    }

    public static LLVMLoadNode createLoad(Type resolvedResultType, LLVMExpressionNode loadTarget) {
        if (resolvedResultType instanceof VectorType) {
            return createLoadVector((VectorType) resolvedResultType, loadTarget, ((VectorType) resolvedResultType).getNumberOfElementsInt());
        } else {
            int bits = resolvedResultType instanceof VariableBitWidthType ? ((VariableBitWidthType) resolvedResultType).getBitSizeInt() : 0;
            return createLoad(resolvedResultType, loadTarget, bits);
        }
    }

    private static LLVMLoadNode createLoadVector(VectorType resultType, LLVMExpressionNode loadTarget, int size) {
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
        } else if (elemType instanceof PointerType || elemType instanceof FunctionType) {
            return LLVMLoadPointerVectorNodeGen.create(loadTarget, size);
        } else {
            throw new AssertionError(elemType + " vectors not supported");
        }
    }

    private static LLVMLoadNode createLoad(Type resultType, LLVMExpressionNode loadTarget, int bits) {
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
        } else if (resultType instanceof StructureType || resultType instanceof ArrayType) {
            return LLVMStructDirectLoadNodeGen.create(loadTarget);
        } else if (resultType instanceof PointerType || resultType instanceof FunctionType) {
            return LLVMPointerDirectLoadNodeGen.create(loadTarget);
        } else {
            throw new AssertionError(resultType);
        }
    }

    public static ForeignToLLVM createForeignToLLVM(ForeignToLLVMType type) {
        switch (type) {
            case VOID:
                return ToVoidLLVMNodeGen.create();
            case ANY:
                return ToAnyLLVMNodeGen.create();
            case I1:
                return ToI1NodeGen.create();
            case I8:
                return ToI8NodeGen.create();
            case I16:
                return ToI16NodeGen.create();
            case I32:
                return ToI32NodeGen.create();
            case I64:
                return ToI64NodeGen.create();
            case FLOAT:
                return ToFloatNodeGen.create();
            case DOUBLE:
                return ToDoubleNodeGen.create();
            case POINTER:
                return ToPointer.create();
            default:
                throw new IllegalStateException(type.toString());
        }
    }

    public static ForeignToLLVM createForeignToLLVM(Value type) {
        switch (type.getKind()) {
            case I1:
                return ToI1NodeGen.create();
            case I8:
                return ToI8NodeGen.create();
            case I16:
                return ToI16NodeGen.create();
            case I32:
                return ToI32NodeGen.create();
            case I64:
                return ToI64NodeGen.create();
            case FLOAT:
                return ToFloatNodeGen.create();
            case DOUBLE:
                return ToDoubleNodeGen.create();
            case POINTER:
                return ToPointer.create(type.getBaseType());
            default:
                throw new IllegalStateException("unexpected interop kind " + type.getKind());
        }
    }

    public static LLVMExpressionNode createFunctionCall(LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type) {
        LLVMExpressionNode callNode = LLVMCallNode.create(type, functionNode, argNodes, true);
        return LLVMValueProfilingNode.create(callNode, type.getReturnType());
    }

    public static LLVMStatementNode createDebugTrap() {
        return LLVMDebugTrapNodeGen.create();
    }

    public static LLVMDebugValue.Builder createDebugDeclarationBuilder() {
        return LLVMToDebugDeclaration.getInstance();
    }

    public static LLVMDebugValue.Builder createDebugValueBuilder() {
        return LLVMToDebugValueNodeGen.getUncached();
    }

    public static LLVMExpressionNode createArithmeticOp(ArithmeticOperation op, Type type, LLVMExpressionNode left, LLVMExpressionNode right) {
        if (type instanceof VectorType) {
            VectorType vectorType = (VectorType) type;
            LLVMArithmeticNode arithmeticNode = createScalarArithmeticOp(op, vectorType.getElementType(), null, null);
            return LLVMVectorArithmeticNodeGen.create(vectorType.getNumberOfElementsInt(), arithmeticNode, left, right);
        } else {
            return createScalarArithmeticOp(op, type, left, right);
        }
    }

    public static LLVMArithmeticNode createScalarArithmeticOp(ArithmeticOperation op, Type type, LLVMExpressionNode left, LLVMExpressionNode right) {
        assert !(type instanceof VectorType);
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return LLVMI1ArithmeticNodeGen.create(op, left, right);
                case I8:
                    return LLVMI8ArithmeticNodeGen.create(op, left, right);
                case I16:
                    return LLVMI16ArithmeticNodeGen.create(op, left, right);
                case I32:
                    return LLVMI32ArithmeticNodeGen.create(op, left, right);
                case I64:
                    return LLVMAbstractI64ArithmeticNode.create(op, left, right);
                case FLOAT:
                    return LLVMFloatArithmeticNodeGen.create(op, left, right);
                case DOUBLE:
                    return LLVMDoubleArithmeticNodeGen.create(op, left, right);
                case X86_FP80:
                    return LLVMFP80ArithmeticNodeGen.create(op, left, right);
                default:
                    throw new AssertionError(type);
            }
        } else if (type instanceof VariableBitWidthType) {
            return LLVMIVarBitArithmeticNodeGen.create(op, left, right);
        } else {
            throw new AssertionError(type);
        }
    }

    public static LLVMExpressionNode createComparison(CompareOperator operator, Type type, LLVMExpressionNode lhs, LLVMExpressionNode rhs) {
        if (type instanceof VectorType) {
            VectorType vectorType = ((VectorType) type);
            LLVMAbstractCompareNode comparison = createScalarComparison(operator, vectorType.getElementType(), null, null);
            return LLVMVectorCompareNodeGen.create(vectorType.getNumberOfElementsInt(), comparison, lhs, rhs);
        } else {
            return createScalarComparison(operator, type, lhs, rhs);
        }
    }

    private static LLVMAbstractCompareNode createScalarComparison(CompareOperator operator, Type type, LLVMExpressionNode lhs, LLVMExpressionNode rhs) {
        assert !(type instanceof VectorType);
        if (usePointerComparison(type)) {
            return createPointerComparison(operator, lhs, rhs);
        } else {
            return createPrimitiveComparison(operator, lhs, rhs);
        }
    }

    private static LLVMAbstractCompareNode createPointerComparison(CompareOperator operator, LLVMExpressionNode lhs, LLVMExpressionNode rhs) {
        switch (operator) {
            case INT_EQUAL:
                return LLVMPointerCompareNode.create(LLVMPointerCompareNode.Kind.EQ, lhs, rhs);
            case INT_NOT_EQUAL:
                return LLVMPointerCompareNode.create(LLVMPointerCompareNode.Kind.NEQ, lhs, rhs);
            case INT_UNSIGNED_GREATER_THAN:
                return LLVMNegateNode.create(LLVMPointerCompareNode.create(LLVMPointerCompareNode.Kind.ULE, lhs, rhs));
            case INT_UNSIGNED_GREATER_OR_EQUAL:
                return LLVMNegateNode.create(LLVMPointerCompareNode.create(LLVMPointerCompareNode.Kind.ULT, lhs, rhs));
            case INT_UNSIGNED_LESS_THAN:
                return LLVMPointerCompareNode.create(LLVMPointerCompareNode.Kind.ULT, lhs, rhs);
            case INT_UNSIGNED_LESS_OR_EQUAL:
                return LLVMPointerCompareNode.create(LLVMPointerCompareNode.Kind.ULE, lhs, rhs);
            case INT_SIGNED_GREATER_THAN:
                return LLVMNegateNode.create(LLVMPointerCompareNode.create(LLVMPointerCompareNode.Kind.SLE, lhs, rhs));
            case INT_SIGNED_GREATER_OR_EQUAL:
                return LLVMNegateNode.create(LLVMPointerCompareNode.create(LLVMPointerCompareNode.Kind.SLT, lhs, rhs));
            case INT_SIGNED_LESS_THAN:
                return LLVMPointerCompareNode.create(LLVMPointerCompareNode.Kind.SLT, lhs, rhs);
            case INT_SIGNED_LESS_OR_EQUAL:
                return LLVMPointerCompareNode.create(LLVMPointerCompareNode.Kind.SLE, lhs, rhs);
            default:
                throw new AssertionError(operator);
        }
    }

    private static LLVMAbstractCompareNode createPrimitiveComparison(CompareOperator operator, LLVMExpressionNode lhs, LLVMExpressionNode rhs) {
        switch (operator) {
            case FP_FALSE:
                return LLVMFalseCmpNodeGen.create(lhs, rhs);
            case FP_ORDERED_EQUAL:
                return LLVMOrderedEqNodeGen.create(lhs, rhs);
            case FP_ORDERED_GREATER_THAN:
                return LLVMOrderedGtNodeGen.create(lhs, rhs);
            case FP_ORDERED_GREATER_OR_EQUAL:
                return LLVMOrderedGeNodeGen.create(lhs, rhs);
            case FP_ORDERED_LESS_THAN:
                return LLVMOrderedLtNodeGen.create(lhs, rhs);
            case FP_ORDERED_LESS_OR_EQUAL:
                return LLVMOrderedLeNodeGen.create(lhs, rhs);
            case FP_ORDERED_NOT_EQUAL:
                return LLVMOrderedNeNodeGen.create(lhs, rhs);
            case FP_ORDERED:
                return LLVMOrderedNodeGen.create(lhs, rhs);
            case FP_UNORDERED:
                return LLVMUnorderedNodeGen.create(lhs, rhs);
            case FP_UNORDERED_EQUAL:
                return LLVMUnorderedEqNodeGen.create(lhs, rhs);
            case FP_UNORDERED_GREATER_THAN:
                return LLVMUnorderedGtNodeGen.create(lhs, rhs);
            case FP_UNORDERED_GREATER_OR_EQUAL:
                return LLVMUnorderedGeNodeGen.create(lhs, rhs);
            case FP_UNORDERED_LESS_THAN:
                return LLVMUnorderedLtNodeGen.create(lhs, rhs);
            case FP_UNORDERED_LESS_OR_EQUAL:
                return LLVMUnorderedLeNodeGen.create(lhs, rhs);
            case FP_UNORDERED_NOT_EQUAL:
                return LLVMUnorderedNeNodeGen.create(lhs, rhs);
            case FP_TRUE:
                return LLVMTrueCmpNodeGen.create(lhs, rhs);
            case INT_EQUAL:
                return LLVMEqNodeGen.create(lhs, rhs);
            case INT_NOT_EQUAL:
                return LLVMNeNodeGen.create(lhs, rhs);
            case INT_UNSIGNED_GREATER_THAN:
                return LLVMNegateNode.create(LLVMUnsignedLeNodeGen.create(lhs, rhs));
            case INT_UNSIGNED_GREATER_OR_EQUAL:
                return LLVMNegateNode.create(LLVMUnsignedLtNodeGen.create(lhs, rhs));
            case INT_UNSIGNED_LESS_THAN:
                return LLVMUnsignedLtNodeGen.create(lhs, rhs);
            case INT_UNSIGNED_LESS_OR_EQUAL:
                return LLVMUnsignedLeNodeGen.create(lhs, rhs);
            case INT_SIGNED_GREATER_THAN:
                return LLVMNegateNode.create(LLVMSignedLeNodeGen.create(lhs, rhs));
            case INT_SIGNED_GREATER_OR_EQUAL:
                return LLVMNegateNode.create(LLVMSignedLtNodeGen.create(lhs, rhs));
            case INT_SIGNED_LESS_THAN:
                return LLVMSignedLtNodeGen.create(lhs, rhs);
            case INT_SIGNED_LESS_OR_EQUAL:
                return LLVMSignedLeNodeGen.create(lhs, rhs);
            default:
                throw new RuntimeException("Missed a compare operator");
        }
    }

    private static boolean usePointerComparison(Type type) {
        return type instanceof PointerType || type instanceof FunctionType || type instanceof PrimitiveType && ((PrimitiveType) type).getPrimitiveKind() == PrimitiveKind.I64;
    }

    public static LLVMExpressionNode createSimpleConstantNoArray(Object constant, Type type) {
        if (type instanceof VariableBitWidthType) {
            Number c = (Number) constant;
            try {
                if (Long.compareUnsigned(type.getBitSize(), Long.SIZE) <= 0) {
                    return LLVMIVarBitLiteralNodeGen.create(LLVMIVarBit.fromLong(((VariableBitWidthType) type).getBitSizeInt(), c.longValue()));
                } else {
                    return LLVMIVarBitLiteralNodeGen.create(LLVMIVarBit.fromBigInteger(((VariableBitWidthType) type).getBitSizeInt(), (BigInteger) c));
                }
            } catch (TypeOverflowException e) {
                return Type.handleOverflowExpression(e);
            }
        } else if (type instanceof PointerType || type instanceof FunctionType) {
            if (constant == null) {
                return LLVMNativePointerLiteralNodeGen.create(LLVMNativePointer.create(0));
            } else {
                throw new AssertionError("Not a Simple Constant: " + constant);
            }
        } else if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return LLVMI1LiteralNodeGen.create((boolean) constant);
                case I8:
                    return LLVMI8LiteralNodeGen.create((byte) constant);
                case I16:
                    return LLVMI16LiteralNodeGen.create((short) constant);
                case I32:
                    return LLVMI32LiteralNodeGen.create((int) constant);
                case FLOAT:
                    return LLVMFloatLiteralNodeGen.create((float) constant);
                case DOUBLE:
                    return LLVMDoubleLiteralNodeGen.create((double) constant);
                case X86_FP80:
                    if (constant == null) {
                        return LLVM80BitFloatLiteralNodeGen.create(LLVM80BitFloat.fromLong(0));
                    } else {
                        return LLVM80BitFloatLiteralNodeGen.create(LLVM80BitFloat.fromBytesBigEndian((byte[]) constant));
                    }
                case I64:
                    return LLVMI64LiteralNodeGen.create((long) constant);
                default:
                    throw new AssertionError(type);
            }
        } else if (type == MetaType.DEBUG) {
            return LLVMNativePointerLiteralNodeGen.create(LLVMNativePointer.createNull());
        } else {
            throw new AssertionError(type);
        }
    }

    @SuppressWarnings("unused")
    public static LLVMExpressionNode createBitcast(LLVMExpressionNode fromNode, Type targetType, Type fromType) {
        // does a reinterpreting cast between pretty much anything as long as source and target have
        // the same bit width.
        assert targetType != null;

        if (targetType instanceof PrimitiveType) {
            return createBitcast(fromNode, ((PrimitiveType) targetType).getPrimitiveKind());
        } else if (targetType instanceof PointerType || targetType instanceof FunctionType) {
            return LLVMToAddressNodeGen.create(fromNode);
        } else if (targetType instanceof VariableBitWidthType) {
            return LLVMBitcastToIVarNodeGen.create(fromNode, ((VariableBitWidthType) targetType).getBitSizeInt());
        } else if (targetType instanceof VectorType) {
            VectorType vectorType = (VectorType) targetType;
            Type elemType = vectorType.getElementType();
            int vectorLength = vectorType.getNumberOfElementsInt();
            if (elemType instanceof PrimitiveType) {
                switch (((PrimitiveType) elemType).getPrimitiveKind()) {
                    case I1:
                        return LLVMBitcastToI1VectorNodeGen.create(fromNode, vectorLength);
                    case I8:
                        return LLVMBitcastToI8VectorNodeGen.create(fromNode, vectorLength);
                    case I16:
                        return LLVMBitcastToI16VectorNodeGen.create(fromNode, vectorLength);
                    case I32:
                        return LLVMBitcastToI32VectorNodeGen.create(fromNode, vectorLength);
                    case I64:
                        return LLVMBitcastToI64VectorNodeGen.create(fromNode, vectorLength);
                    case FLOAT:
                        return LLVMBitcastToFloatVectorNodeGen.create(fromNode, vectorLength);
                    case DOUBLE:
                        return LLVMBitcastToDoubleVectorNodeGen.create(fromNode, vectorLength);
                }
            }
        }

        throw unsupportedCast(targetType);
    }

    private static LLVMExpressionNode createBitcast(LLVMExpressionNode fromNode, PrimitiveKind kind) {
        switch (kind) {
            case I1:
                return LLVMBitcastToI1NodeGen.create(fromNode);
            case I8:
                return LLVMBitcastToI8NodeGen.create(fromNode);
            case I16:
                return LLVMBitcastToI16NodeGen.create(fromNode);
            case I32:
                return LLVMBitcastToI32NodeGen.create(fromNode);
            case I64:
                return LLVMBitcastToI64NodeGen.create(fromNode);
            case FLOAT:
                return LLVMBitcastToFloatNodeGen.create(fromNode);
            case DOUBLE:
                return LLVMBitcastToDoubleNodeGen.create(fromNode);
            case X86_FP80:
                return LLVMBitcastToLLVM80BitFloatNodeGen.create(fromNode);
            default:
                throw unsupportedCast(kind);
        }
    }

    public static LLVMExpressionNode createUnsignedCast(LLVMExpressionNode fromNode, Type targetType) {
        // does an unsigned cast (zero extension or FP to uint). for vectors, the number of elements
        // in source and target must match.
        //
        // @formatter:off
        // source: ([vector] int, | ([vector] uint, | ([vector] FP,  | (int,
        // target:  [vector] int) |  [vector] FP)   |  [vector] uint |  ptr)
        // @formatter:on
        if (targetType instanceof PrimitiveType) {
            return createUnsignedCast(fromNode, ((PrimitiveType) targetType).getPrimitiveKind());
        } else if (targetType instanceof PointerType || targetType instanceof FunctionType) {
            return LLVMToAddressNodeGen.create(fromNode);
        } else if (targetType instanceof VariableBitWidthType) {
            return LLVMUnsignedCastToIVarNodeGen.create(fromNode, ((VariableBitWidthType) targetType).getBitSizeInt());
        } else if (targetType instanceof VectorType) {
            VectorType vectorType = (VectorType) targetType;
            Type elemType = vectorType.getElementType();
            int vectorLength = vectorType.getNumberOfElementsInt();
            if (elemType instanceof PrimitiveType) {
                switch (((PrimitiveType) elemType).getPrimitiveKind()) {
                    case I1:
                        return LLVMUnsignedCastToI1VectorNodeGen.create(fromNode, vectorLength);
                    case I8:
                        return LLVMUnsignedCastToI8VectorNodeGen.create(fromNode, vectorLength);
                    case I16:
                        return LLVMUnsignedCastToI16VectorNodeGen.create(fromNode, vectorLength);
                    case I32:
                        return LLVMUnsignedCastToI32VectorNodeGen.create(fromNode, vectorLength);
                    case I64:
                        return LLVMUnsignedCastToI64VectorNodeGen.create(fromNode, vectorLength);
                    case FLOAT:
                        return LLVMUnsignedCastToFloatVectorNodeGen.create(fromNode, vectorLength);
                    case DOUBLE:
                        return LLVMUnsignedCastToDoubleVectorNodeGen.create(fromNode, vectorLength);
                }
            } else if (elemType instanceof PointerType) {
                return LLVMBitcastToPointerVectorNodeGen.create(fromNode, vectorLength);
            }
        }

        throw unsupportedCast(targetType);
    }

    public static LLVMExpressionNode createUnsignedCast(LLVMExpressionNode fromNode, PrimitiveKind kind) {
        switch (kind) {
            case I1:
                // Since signed (fptosi) and unsigned (fptoui) casts to i1 behave the same, we
                // return a SignedCastToI1 node here.
                return LLVMSignedCastToI1NodeGen.create(fromNode);
            case I8:
                return LLVMUnsignedCastToI8NodeGen.create(fromNode);
            case I16:
                return LLVMUnsignedCastToI16NodeGen.create(fromNode);
            case I32:
                return LLVMUnsignedCastToI32NodeGen.create(fromNode);
            case I64:
                return LLVMUnsignedCastToI64NodeGen.create(fromNode);
            case FLOAT:
                return LLVMUnsignedCastToFloatNodeGen.create(fromNode);
            case DOUBLE:
                return LLVMUnsignedCastToDoubleNodeGen.create(fromNode);
            case X86_FP80:
                return LLVMUnsignedCastToLLVM80BitFloatNodeGen.create(fromNode);
            default:
                throw unsupportedCast(kind);
        }
    }

    public static LLVMExpressionNode createSignedCast(LLVMExpressionNode fromNode, Type targetType) {
        // does a signed cast (either sign extend or truncate) from (int or FP) to (int or FP). for
        // vectors, the number of elements in source and target must match.
        //
        // @formatter:off
        // source: ([vector] int, | ([vector] FP,   | ([vector] sint, | ([vector] FP, | (ptr,
        // target:  [vector] int) |  [vector] sint) |  [vector] FP)   |  [vector] FP) |  int)
        // @formatter:on
        if (targetType instanceof PrimitiveType) {
            return createSignedCast(fromNode, ((PrimitiveType) targetType).getPrimitiveKind());
        } else if (targetType instanceof VariableBitWidthType) {
            return LLVMSignedCastToIVarNodeGen.create(fromNode, ((VariableBitWidthType) targetType).getBitSizeInt());
        } else if (targetType instanceof VectorType) {
            VectorType vectorType = (VectorType) targetType;
            Type elemType = vectorType.getElementType();
            int vectorLength = vectorType.getNumberOfElementsInt();
            if (elemType instanceof PrimitiveType) {
                switch (((PrimitiveType) ((VectorType) targetType).getElementType()).getPrimitiveKind()) {
                    case I1:
                        return LLVMSignedCastToI1VectorNodeGen.create(fromNode, vectorLength);
                    case I8:
                        return LLVMSignedCastToI8VectorNodeGen.create(fromNode, vectorLength);
                    case I16:
                        return LLVMSignedCastToI16VectorNodeGen.create(fromNode, vectorLength);
                    case I32:
                        return LLVMSignedCastToI32VectorNodeGen.create(fromNode, vectorLength);
                    case I64:
                        return LLVMSignedCastToI64VectorNodeGen.create(fromNode, vectorLength);
                    case FLOAT:
                        return LLVMSignedCastToFloatVectorNodeGen.create(fromNode, vectorLength);
                    case DOUBLE:
                        return LLVMSignedCastToDoubleVectorNodeGen.create(fromNode, vectorLength);
                }
            }
        }

        throw unsupportedCast(targetType);
    }

    public static LLVMExpressionNode createSignedCast(LLVMExpressionNode fromNode, PrimitiveKind kind) {
        switch (kind) {
            case I1:
                return LLVMSignedCastToI1NodeGen.create(fromNode);
            case I8:
                return LLVMSignedCastToI8NodeGen.create(fromNode);
            case I16:
                return LLVMSignedCastToI16NodeGen.create(fromNode);
            case I32:
                return LLVMSignedCastToI32NodeGen.create(fromNode);
            case I64:
                return LLVMSignedCastToI64NodeGen.create(fromNode);
            case FLOAT:
                return LLVMSignedCastToFloatNodeGen.create(fromNode);
            case DOUBLE:
                return LLVMSignedCastToDoubleNodeGen.create(fromNode);
            case X86_FP80:
                return LLVMSignedCastToLLVM80BitFloatNodeGen.create(fromNode);
            default:
                throw unsupportedCast(kind);
        }
    }

    private static AssertionError unsupportedCast(Type targetType) {
        throw new LLVMParserException("Cannot cast to " + targetType);
    }

    private static AssertionError unsupportedCast(PrimitiveKind kind) {
        throw new LLVMParserException("Cannot cast to " + kind);
    }
}
