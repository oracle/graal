/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMDebugGlobalVariable;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMDebugThreadLocalGlobalVariable;
import com.oracle.truffle.llvm.runtime.debug.type.LLVMSourceType;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugManagedValue;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObject;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObjectBuilder;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMSourceTypeFactory;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.floating.LLVM128BitFloat;
import com.oracle.truffle.llvm.runtime.floating.LLVM80BitFloat;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType;
import com.oracle.truffle.llvm.runtime.interop.access.LLVMInteropType.Value;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM;
import com.oracle.truffle.llvm.runtime.interop.convert.ForeignToLLVM.ForeignToLLVMType;
import com.oracle.truffle.llvm.runtime.interop.convert.ToAnyLLVMNodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToDoubleNodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToFP128NodeGen;
import com.oracle.truffle.llvm.runtime.interop.convert.ToFP80NodeGen;
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
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMTo128BitFloatingNodeGen.LLVMSignedCastToLLVM128BitFloatNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMTo128BitFloatingNodeGen.LLVMUnsignedCastToLLVM128BitFloatNodeGen;
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
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAArgNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMMetaLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVM128BitFloatLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVM80BitFloatLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMDoubleLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMFloatLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI16LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI1LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI32LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI64LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI8LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMIVarBitLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMManagedPointerLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMNativePointerLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMVectorizedGetElementPtrNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVM128BitFloatLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVM80BitFloatLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNode.LLVMDoubleOffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNodeGen.LLVMDoubleOffsetLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMFloatLoadNode.LLVMFloatOffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMFloatLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMFloatLoadNodeGen.LLVMFloatOffsetLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNode.LLVMI16OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNodeGen.LLVMI16OffsetLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI1LoadNode.LLVMI1OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI1LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI1LoadNodeGen.LLVMI1OffsetLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNode.LLVMI32OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNodeGen.LLVMI32OffsetLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNode.LLVMI64OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNodeGen.LLVMI64OffsetLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNode.LLVMI8OffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNodeGen.LLVMI8OffsetLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMIVarBitLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadDoubleVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadFloatVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI16VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI1VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI32VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI64VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI8VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadPointerVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMOffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMPointerLoadNode.LLVMPointerOffsetLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMPointerLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMPointerLoadNodeGen.LLVMPointerOffsetLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMStructLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMDoubleStoreNode.LLVMDoubleOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMDoubleStoreNodeGen.LLVMDoubleOffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMFloatStoreNode.LLVMFloatOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMFloatStoreNodeGen.LLVMFloatOffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNode.LLVMI16OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNodeGen.LLVMI16OffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI1StoreNode.LLVMI1OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI1StoreNodeGen.LLVMI1OffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode.LLVMI32OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen.LLVMI32OffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode.LLVMI64OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNodeGen.LLVMI64OffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNode.LLVMI8OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNodeGen.LLVMI8OffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMOffsetStoreNode.LLVMPrimitiveOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode.LLVMPointerOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNodeGen.LLVMPointerOffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMAbstractCompareNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNode.LLVMAbstractI64ArithmeticNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.LLVMDoubleArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNodeFactory.LLVMFP128ArithmeticNodeGen;
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
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMAccessElemPtrSymbolNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMAccessGlobalSymbolNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMAccessSymbolNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMAccessThreadLocalSymbolNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMUnsupportedInstructionNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNode.LLVM128BitFloatReadNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNode.LLVM80BitFloatReadNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNode.LLVMDebugReadNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNode.LLVMDoubleReadNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNode.LLVMFloatReadNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNode.LLVMI16ReadNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNode.LLVMI1ReadNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNode.LLVMI32ReadNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNode.LLVMI8ReadNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNode.LLVMIReadVarBitNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNode.LLVMObjectReadNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.LLVMI64ReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWrite128BitFloatingNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWrite80BitFloatingNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWriteDoubleNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWriteFloatNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI1NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWriteIVarBitNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWritePointerNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWriteVectorNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;

public class CommonNodeFactory {

    public CommonNodeFactory() {
    }

    public static LLVMExpressionNode createLiteral(Object value, Type type) {
        if (type instanceof PointerType || type instanceof FunctionType) {
            if (LLVMNativePointer.isInstance(value)) {
                return LLVMNativePointerLiteralNodeGen.create(LLVMNativePointer.cast(value));
            } else if (LLVMManagedPointer.isInstance(value)) {
                return LLVMManagedPointerLiteralNodeGen.create(LLVMManagedPointer.cast(value));
            } else if (value instanceof LLVMGlobal || value instanceof LLVMFunction) {
                return LLVMAccessGlobalSymbolNodeGen.create((LLVMSymbol) value);
            } else if (value instanceof LLVMElemPtrSymbol) {
                return LLVMAccessElemPtrSymbolNodeGen.create((LLVMElemPtrSymbol) value);
            } else if (value instanceof LLVMThreadLocalSymbol) {
                return LLVMAccessThreadLocalSymbolNodeGen.create((LLVMThreadLocalSymbol) value);
            } else {
                throw new AssertionError(value.getClass());
            }
        } else if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return LLVMI1LiteralNodeGen.create((boolean) value);
                case I8:
                    return LLVMI8LiteralNodeGen.create((byte) value);
                case I16:
                    return LLVMI16LiteralNodeGen.create((short) value);
                case I32:
                    return LLVMI32LiteralNodeGen.create((int) value);
                case I64:
                    return LLVMI64LiteralNodeGen.create((long) value);
                case FLOAT:
                    return LLVMFloatLiteralNodeGen.create((float) value);
                case DOUBLE:
                    return LLVMDoubleLiteralNodeGen.create((double) value);
                default:
                    throw new AssertionError(value + " " + type);
            }
        } else if (type instanceof MetaType) {
            return LLVMMetaLiteralNodeGen.create(value);
        }
        throw new AssertionError(value + " " + type);
    }

    public static LLVMOffsetLoadNode createOffsetLoadNode(LLVMInteropType.ValueKind kind) {
        switch (kind) {
            case I1:
                return LLVMI1OffsetLoadNode.create();
            case I8:
                return LLVMI8OffsetLoadNode.create();
            case I16:
                return LLVMI16OffsetLoadNode.create();
            case I32:
                return LLVMI32OffsetLoadNode.create();
            case I64:
                return LLVMI64OffsetLoadNode.create();
            case FLOAT:
                return LLVMFloatOffsetLoadNode.create();
            case DOUBLE:
                return LLVMDoubleOffsetLoadNode.create();
            case POINTER:
                return LLVMPointerOffsetLoadNode.create();
            default:
                throw new IllegalStateException("unexpected interop kind " + kind);
        }
    }

    public static LLVMOffsetLoadNode getUncachedOffsetLoadNode(LLVMInteropType.ValueKind kind) {
        switch (kind) {
            case I1:
                return LLVMI1OffsetLoadNodeGen.getUncached();
            case I8:
                return LLVMI8OffsetLoadNodeGen.getUncached();
            case I16:
                return LLVMI16OffsetLoadNodeGen.getUncached();
            case I32:
                return LLVMI32OffsetLoadNodeGen.getUncached();
            case I64:
                return LLVMI64OffsetLoadNodeGen.getUncached();
            case FLOAT:
                return LLVMFloatOffsetLoadNodeGen.getUncached();
            case DOUBLE:
                return LLVMDoubleOffsetLoadNodeGen.getUncached();
            case POINTER:
                return LLVMPointerOffsetLoadNodeGen.getUncached();
            default:
                throw new IllegalStateException("unexpected interop kind " + kind);
        }
    }

    public static LLVMPrimitiveOffsetStoreNode createOffsetStoreNode(LLVMInteropType.ValueKind kind) {
        switch (kind) {
            case I1:
                return LLVMI1OffsetStoreNode.create();
            case I8:
                return LLVMI8OffsetStoreNode.create();
            case I16:
                return LLVMI16OffsetStoreNode.create();
            case I32:
                return LLVMI32OffsetStoreNode.create();
            case I64:
                return LLVMI64OffsetStoreNode.create();
            case FLOAT:
                return LLVMFloatOffsetStoreNode.create();
            case DOUBLE:
                return LLVMDoubleOffsetStoreNode.create();
            case POINTER:
                return LLVMPointerOffsetStoreNode.create();
            default:
                throw new IllegalStateException("unexpected interop kind " + kind);
        }
    }

    public static LLVMPrimitiveOffsetStoreNode getUncachedOffsetStoreNode(LLVMInteropType.ValueKind kind) {
        switch (kind) {
            case I1:
                return LLVMI1OffsetStoreNodeGen.getUncached();
            case I8:
                return LLVMI8OffsetStoreNodeGen.getUncached();
            case I16:
                return LLVMI16OffsetStoreNodeGen.getUncached();
            case I32:
                return LLVMI32OffsetStoreNodeGen.getUncached();
            case I64:
                return LLVMI64OffsetStoreNodeGen.getUncached();
            case FLOAT:
                return LLVMFloatOffsetStoreNodeGen.getUncached();
            case DOUBLE:
                return LLVMDoubleOffsetStoreNodeGen.getUncached();
            case POINTER:
                return LLVMPointerOffsetStoreNodeGen.getUncached();
            default:
                throw new IllegalStateException("unexpected interop kind " + kind);
        }
    }

    public static Object toGenericDebuggerValue(Object llvmType, Object value, DataLayout dataLayout) {
        final Object complexObject = asDebuggerIRValue(llvmType, value, dataLayout);
        if (complexObject != null) {
            return complexObject;
        }

        return LLVMDebugManagedValue.create(llvmType, value);
    }

    private static Object asDebuggerIRValue(Object llvmType, Object value, DataLayout dataLayout) {
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

    public static LLVMDebugObjectBuilder createDebugStaticValue(LLVMExpressionNode valueNode, boolean isGlobal) {
        LLVMDebugValue.Builder toDebugNode = createDebugValueBuilder();

        Object value = null;
        if (isGlobal) {
            assert valueNode instanceof LLVMAccessSymbolNode;
            LLVMAccessSymbolNode node = (LLVMAccessSymbolNode) valueNode;
            LLVMSymbol symbol = node.getSymbol();
            if (symbol.isGlobalVariable()) {
                value = new LLVMDebugGlobalVariable(symbol.asGlobalVariable());
            } else if (symbol.isThreadLocalSymbol()) {
                value = new LLVMDebugThreadLocalGlobalVariable(symbol.asThreadLocalSymbol());
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

    private static final class ElementPointerFactory {

        private final NodeFactory nodeFactory;

        private boolean wasVectorized;
        private int vectorLength;

        private LLVMExpressionNode currentAddress;
        private Type currentType;

        ElementPointerFactory(NodeFactory nodeFactory, LLVMExpressionNode currentAddress, Type currentType) {
            this.nodeFactory = nodeFactory;
            this.currentAddress = currentAddress;
            this.currentType = currentType;

            this.wasVectorized = currentType instanceof VectorType;
            if (wasVectorized) {
                VectorType vectorType = (VectorType) currentType;
                this.currentType = vectorType.getElementType();
                this.vectorLength = vectorType.getNumberOfElementsInt();
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
        public void addIndex(long indexedTypeLength, LLVMExpressionNode indexNode, Type indexType) {
            if (wasVectorized) {
                // Cases 2, 3, 6, 7
                if (indexType instanceof VectorType) {
                    // Cases 3, 7
                    assert vectorLength == ((VectorType) indexType).getNumberOfElementsInt();
                    currentAddress = nodeFactory.createVectorizedTypedElementPointer(indexedTypeLength, currentType, currentAddress, indexNode);
                } else {
                    // Cases 2, 6
                    currentAddress = nodeFactory.createVectorizedTypedElementPointer(indexedTypeLength, currentType, currentAddress,
                                    LLVMVectorizedGetElementPtrNodeGen.IndexVectorBroadcastNodeGen.create(vectorLength, indexNode));
                }
            } else {
                // Cases 0, 1, 4, 5
                if (indexType instanceof VectorType) {
                    // Cases 1, 5
                    vectorLength = ((VectorType) indexType).getNumberOfElementsInt();
                    wasVectorized = true;
                    currentAddress = nodeFactory.createVectorizedTypedElementPointer(indexedTypeLength, currentType,
                                    LLVMVectorizedGetElementPtrNodeGen.ResultVectorBroadcastNodeGen.create(vectorLength, currentAddress), indexNode);
                } else {
                    // Cases 0, 4
                    currentAddress = nodeFactory.createTypedElementPointer(indexedTypeLength, currentType, currentAddress, indexNode);
                }
            }
        }
    }

    /**
     * Create an expression node that, when executed, will provide an address where the respective
     * argument should either be copied from or copied into. This is important when passing struct
     * arguments by value, this node uses getelementptr to infer the location from the source types
     * into the destination frame slot.
     *
     * @param baseAddress Base address from which to calculate the offsets.
     * @param sourceType Type to index into.
     * @param indices List of indices to reach a member or element from the base address.
     */
    public static LLVMExpressionNode getTargetAddress(LLVMExpressionNode baseAddress, Type sourceType, Collection<Long> indices, NodeFactory nf, DataLayout dataLayout) {
        int indicesSize = indices.size();
        Long[] indicesArr = new Long[indicesSize];
        LLVMExpressionNode[] indexNodes = new LLVMExpressionNode[indicesSize];

        int i = indicesSize - 1;
        for (Long idx : indices) {
            indicesArr[i] = idx;
            indexNodes[i] = CommonNodeFactory.createLiteral(idx, PrimitiveType.I64);
            i--;
        }
        assert i == -1;

        PrimitiveType[] indexTypes = new PrimitiveType[indicesSize];
        Arrays.fill(indexTypes, PrimitiveType.I64);

        LLVMExpressionNode nestedGEPs = CommonNodeFactory.createNestedElementPointerNode(
                        nf,
                        dataLayout,
                        indexNodes,
                        indicesArr,
                        indexTypes,
                        baseAddress,
                        sourceType);

        return nestedGEPs;
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
            ElementPointerFactory factory = new ElementPointerFactory(nodeFactory, curAddress, curType);

            for (int i = 0; i < indexNodes.length; i++) {
                Type indexType = indexTypes[i];
                Long indexInteger = indexConstants[i];
                if (indexInteger == null) {
                    // the index is determined at runtime
                    if (factory.currentType instanceof StructureType) {
                        // according to http://llvm.org/docs/LangRef.html#getelementptr-instruction
                        throw new LLVMParserException("Indices on structs must be constant integers!");
                    }
                    AggregateType aggregate = (AggregateType) factory.currentType;
                    long indexedTypeLength = aggregate.getOffsetOf(1, dataLayout);
                    factory.currentType = aggregate.getElementType(1);
                    factory.addIndex(indexedTypeLength, indexNodes[i], indexType);
                } else {
                    // the index is a constant integer
                    AggregateType aggregate = (AggregateType) factory.currentType;
                    long addressOffset = aggregate.getOffsetOf(indexInteger, dataLayout);
                    factory.currentType = aggregate.getElementType(indexInteger);

                    // creating a pointer inserts type information, this needs to happen for the
                    // address computed by getelementptr even if it is the same as the basepointer
                    if (addressOffset != 0 || i == indexNodes.length - 1) {
                        LLVMExpressionNode indexNode;
                        if (indexType == PrimitiveType.I32) {
                            indexNode = CommonNodeFactory.createLiteral(1, PrimitiveType.I32);
                        } else if (indexType == PrimitiveType.I64) {
                            indexNode = CommonNodeFactory.createLiteral(1L, PrimitiveType.I64);
                        } else {
                            throw new AssertionError(indexType);
                        }
                        factory.addIndex(addressOffset, indexNode, indexType);
                    }
                }
            }

            return factory.currentAddress;
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
    }

    public static LLVMExpressionNode createFrameRead(Type llvmType, int frameSlot) {
        if (llvmType instanceof PrimitiveType) {
            switch (((PrimitiveType) llvmType).getPrimitiveKind()) {
                case I1:
                    return LLVMI1ReadNode.create(frameSlot);
                case I8:
                    return LLVMI8ReadNode.create(frameSlot);
                case I16:
                    return LLVMI16ReadNode.create(frameSlot);
                case I32:
                    return LLVMI32ReadNode.create(frameSlot);
                case I64:
                    return LLVMI64ReadNodeGen.create(frameSlot);
                case FLOAT:
                    return LLVMFloatReadNode.create(frameSlot);
                case DOUBLE:
                    return LLVMDoubleReadNode.create(frameSlot);
                case X86_FP80:
                    return LLVM80BitFloatReadNode.create(frameSlot);
                case F128:
                    return LLVM128BitFloatReadNode.create(frameSlot);
            }
        } else if (llvmType instanceof VectorType) {
            Type elemType = ((VectorType) llvmType).getElementType();
            if (elemType instanceof PrimitiveType) {
                switch (((PrimitiveType) elemType).getPrimitiveKind()) {
                    case I1:
                    case I8:
                    case I16:
                    case I32:
                    case I64:
                    case FLOAT:
                    case DOUBLE:
                        return LLVMObjectReadNode.create(frameSlot);
                }
            } else if (elemType instanceof PointerType || elemType instanceof FunctionType) {
                return LLVMObjectReadNode.create(frameSlot);
            }
        } else if (llvmType instanceof VariableBitWidthType) {
            return LLVMIReadVarBitNode.create(frameSlot);
        } else if (llvmType instanceof PointerType || llvmType instanceof FunctionType) {
            return LLVMObjectReadNode.create(frameSlot);
        } else if (llvmType instanceof StructureType || llvmType instanceof ArrayType) {
            return LLVMObjectReadNode.create(frameSlot);
        } else if (llvmType instanceof VoidType) {
            return LLVMUnsupportedInstructionNode.createExpression(UnsupportedReason.PARSER_ERROR_VOID_SLOT);
        } else if (llvmType == MetaType.DEBUG) {
            return LLVMDebugReadNode.create(frameSlot);
        }
        throw new AssertionError(llvmType + " for " + frameSlot);
    }

    public static LLVMLoadNode createLoad(Type resolvedResultType, LLVMExpressionNode loadTarget) {
        if (resolvedResultType instanceof VectorType) {
            return createLoadVector((VectorType) resolvedResultType, loadTarget, ((VectorType) resolvedResultType).getNumberOfElementsInt());
        } else {
            int bits = resolvedResultType instanceof VariableBitWidthType ? ((VariableBitWidthType) resolvedResultType).getBitSizeInt() : 0;
            return createLoad(resolvedResultType, loadTarget, bits);
        }
    }

    public static LLVMWriteNode createFrameWrite(Type llvmType, LLVMExpressionNode result, int slot) {
        if (llvmType instanceof VectorType) {
            return LLVMWriteVectorNodeGen.create(slot, result);
        } else if (llvmType instanceof PrimitiveType) {
            switch (((PrimitiveType) llvmType).getPrimitiveKind()) {
                case I1:
                    return LLVMWriteI1NodeGen.create(slot, result);
                case I8:
                    return LLVMWriteI8NodeGen.create(slot, result);
                case I16:
                    return LLVMWriteI16NodeGen.create(slot, result);
                case I32:
                    return LLVMWriteI32NodeGen.create(slot, result);
                case I64:
                    return LLVMWriteI64NodeGen.create(slot, result);
                case FLOAT:
                    return LLVMWriteFloatNodeGen.create(slot, result);
                case DOUBLE:
                    return LLVMWriteDoubleNodeGen.create(slot, result);
                case X86_FP80:
                    return LLVMWrite80BitFloatingNodeGen.create(slot, result);
                case F128:
                    return LLVMWrite128BitFloatingNodeGen.create(slot, result);
                default:
                    throw new AssertionError(llvmType);
            }
        } else if (llvmType instanceof VariableBitWidthType) {
            return LLVMWriteIVarBitNodeGen.create(slot, result);
        } else if (llvmType instanceof PointerType || llvmType instanceof FunctionType) {
            return LLVMWritePointerNodeGen.create(slot, result);
        } else if (llvmType instanceof StructureType || llvmType instanceof ArrayType) {
            return LLVMWritePointerNodeGen.create(slot, result);
        }
        throw new AssertionError(llvmType);
    }

    public static LLVMExpressionNode createVaArg(Type type, LLVMExpressionNode source) {
        return LLVMVAArgNodeGen.create(type, source);
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
                    return LLVM80BitFloatLoadNodeGen.create(loadTarget);
                case F128:
                    return LLVM128BitFloatLoadNodeGen.create(loadTarget);
                default:
                    throw new AssertionError(resultType);
            }
        } else if (resultType instanceof VariableBitWidthType) {
            return LLVMIVarBitLoadNodeGen.create(loadTarget, bits);
        } else if (resultType instanceof StructureType || resultType instanceof ArrayType) {
            return LLVMStructLoadNodeGen.create(loadTarget);
        } else if (resultType instanceof PointerType || resultType instanceof FunctionType) {
            return LLVMPointerLoadNodeGen.create(loadTarget);
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
            case FP80:
                return ToFP80NodeGen.create();
            case FP128:
                return ToFP128NodeGen.create();
            case POINTER:
                return ToPointer.create();
            default:
                throw new IllegalStateException(type.toString());
        }
    }

    public static ForeignToLLVM createForeignToLLVM(Value type) {
        switch (type.kind) {
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
            case FP80:
                return ToFP80NodeGen.create();
            case FP128:
                return ToFP128NodeGen.create();
            case POINTER:
                return ToPointer.create(type.baseType);
            default:
                throw new IllegalStateException("unexpected interop kind " + type.kind);
        }
    }

    public static LLVMExpressionNode createFunctionCall(LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type) {
        return createFunctionCall(functionNode, argNodes, type, false);
    }

    public static LLVMExpressionNode createFunctionCall(LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type, boolean mustTail) {
        return LLVMCallNode.create(type, functionNode, argNodes, true, mustTail);
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
                case F128:
                    return LLVMFP128ArithmeticNodeGen.create(op, left, right);
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
                case F128:
                    if (constant == null) {
                        return LLVM128BitFloatLiteralNodeGen.create(LLVM128BitFloat.fromLong(0));
                    } else {
                        return LLVM128BitFloatLiteralNodeGen.create(LLVM128BitFloat.fromBytesBigEndian((byte[]) constant));
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
            } else if (elemType instanceof PointerType) {
                if (fromType instanceof VectorType) {
                    VectorType fromVector = (VectorType) fromType;
                    if (fromVector.getNumberOfElements() == vectorType.getNumberOfElements() && fromVector.getElementType() instanceof PointerType) {
                        // cast from vector-of-pointers to vector-of-pointers
                        // nothing to do, only the pointee type is different
                        return fromNode;
                    }
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
            case F128:
                return LLVMUnsignedCastToLLVM128BitFloatNodeGen.create(fromNode);
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
            case F128:
                return LLVMSignedCastToLLVM128BitFloatNodeGen.create(fromNode);
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
