/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.asm.amd64.AsmParseException;
import com.oracle.truffle.llvm.asm.amd64.InlineAssemblyParser;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute.KnownAttribute;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesGroup;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.runtime.ArithmeticOperation;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode.LLVMIRFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.UnaryOperation;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.except.LLVMAllocationFailureException;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.except.LLVMStackOverflowError;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobal;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemSetNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemoryOpNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.UniquesRegion;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.UniquesRegion.UniqueSlot;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.UniquesRegion.UniquesRegionAllocator;
import com.oracle.truffle.llvm.runtime.memory.LLVMUniquesRegionAllocNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMUniquesRegionAllocNodeGen;
import com.oracle.truffle.llvm.runtime.memory.VarargsAreaStackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLazyException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMTo80BitFloatingNodeGen.LLVMSignedCastToLLVM80BitFloatNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToDoubleNodeGen.LLVMSignedCastToDoubleNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMBrUnconditionalNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMConditionalBranchNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMDispatchBasicBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMDispatchBasicBlockNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMFunctionRootNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMFunctionRootNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMIndirectBranchNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMLoopDispatchNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMLoopNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVM80BitFloatRetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMAddressRetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMDoubleRetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMFloatRetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMI16RetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMI1RetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMI32RetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMI64RetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMI8RetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMIVarBitRetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMStructRetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMVectorRetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMVoidReturnNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMSwitchNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMWritePhisNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMArgNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMInvokeNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMLandingpadNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMLandingpadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMResumeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMTypeIdForExceptionNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFAbsNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMFAbsVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMPowNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMTruffleGetArgCountNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMTruffleGetArgNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMAssumeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI16VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI32VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMByteSwapFactory.LLVMByteSwapI64VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMExpectFactory.LLVMExpectI1NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMExpectFactory.LLVMExpectI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMExpectFactory.LLVMExpectI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMFrameAddressNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMI64ObjectSizeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMInvariantEndNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMInvariantStartNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMIsConstantNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMLifetimeEndNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMLifetimeStartNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMMemCopyNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMMemMoveFactory.LLVMMemMoveI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMMemSetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMMemoryIntrinsicFactory.LLVMFreeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMNoOpNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMPrefetchNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMReturnAddressNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMStackRestoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMStackSaveNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMTrapNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMArithmetic;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMArithmeticFactory.GCCArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMArithmeticFactory.LLVMArithmeticWithOverflowAndCarryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMArithmeticFactory.LLVMArithmeticWithOverflowNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountLeadingZeroesNodeFactory.CountLeadingZeroesI8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountSetBitsNodeFactory.CountSetBitsI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountSetBitsNodeFactory.CountSetBitsI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountTrailingZeroesNodeFactory.CountTrailingZeroesI8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64BitVACopyNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64BitVAEndNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_64VAStartNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_ComparisonNodeFactory.LLVMX86_CmpssNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_ConversionNodeFactory.LLVMX86_ConversionDoubleToIntNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_ConversionNodeFactory.LLVMX86_ConversionFloatToIntNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_ConversionNodeFactory.LLVMX86_MovmskpdNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_ConversionNodeFactory.LLVMX86_Pmovmskb128NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_MissingBuiltin;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_VectorCmpNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_VectorMaxNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_VectorMaxsdNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_VectorMinNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_VectorPackNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_VectorSquareRootNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNode.LLVMManagedPointerLiteralNode;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMDoubleLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMFloatLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI16LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI1LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI32LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI64LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI8LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMManagedPointerLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMNativePointerLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMDoubleVectorLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMFloatVectorLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMI16VectorLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMI1VectorLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMI32VectorLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMI64VectorLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMI8VectorLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMVectorLiteralNodeFactory.LLVMPointerVectorLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.AllocateGlobalsBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.AllocateReadOnlyGlobalsBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.FreeReadOnlyGlobalsBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMCompareExchangeNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMFenceNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMGetElementPtrNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMGetStackSpaceInstruction.LLVMGetStackForConstInstruction;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMGetStackSpaceInstructionFactory.LLVMAllocaConstInstructionNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMGetStackSpaceInstructionFactory.LLVMAllocaInstructionNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMGetStackSpaceInstructionFactory.LLVMGetUniqueStackSpaceInstructionNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMInsertValueNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMNativeVarargsAreaStackAllocationNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMStructByValueNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMVarArgCompoundAddressNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMVectorizedGetElementPtrNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.NativeMemSetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.NativeProfiledMemMoveNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.ProtectReadOnlyGlobalsBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.literal.LLVMArrayLiteralNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.literal.LLVMArrayLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.literal.LLVMStructArrayLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVM80BitFloatDirectLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMPointerDirectLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMFloatLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI1LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadDoubleVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadFloatVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI16VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI1VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI32VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI64VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI8VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadPointerVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.rmw.LLVMI16RMWNodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.rmw.LLVMI1RMWNodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.rmw.LLVMI32RMWNodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.rmw.LLVMI64RMWNodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.rmw.LLVMI8RMWNodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMDoubleStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMFloatStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI1StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMIVarBitStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMStoreVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMStructStoreNodeGen;
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
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMUnaryNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMUnaryNodeFactory.LLVMDoubleUnaryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMUnaryNodeFactory.LLVMFP80UnaryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMUnaryNodeFactory.LLVMFloatUnaryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMVectorArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMVectorUnaryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMAccessSymbolNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMSelectNodeFactory.LLVM80BitFloatSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMSelectNodeFactory.LLVMDoubleSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMSelectNodeFactory.LLVMFloatSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMSelectNodeFactory.LLVMGenericSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMSelectNodeFactory.LLVMI16SelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMSelectNodeFactory.LLVMI1SelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMSelectNodeFactory.LLVMI32SelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMSelectNodeFactory.LLVMI64SelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMSelectNodeFactory.LLVMI8SelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMUnreachableNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMUnsupportedInstructionNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMValueProfilingNode;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMVectorSelectNodeFactory.LLVMDoubleVectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMVectorSelectNodeFactory.LLVMFloatVectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMVectorSelectNodeFactory.LLVMI16VectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMVectorSelectNodeFactory.LLVMI1VectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMVectorSelectNodeFactory.LLVMI32VectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMVectorSelectNodeFactory.LLVMI64VectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMVectorSelectNodeFactory.LLVMI8VectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNode;
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
import com.oracle.truffle.llvm.runtime.nodes.vars.StructLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMExtractElementNodeFactory.LLVMDoubleExtractElementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMExtractElementNodeFactory.LLVMFloatExtractElementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMExtractElementNodeFactory.LLVMI16ExtractElementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMExtractElementNodeFactory.LLVMI1ExtractElementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMExtractElementNodeFactory.LLVMI32ExtractElementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMExtractElementNodeFactory.LLVMI64ExtractElementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMExtractElementNodeFactory.LLVMI8ExtractElementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMInsertElementNodeFactory.LLVMDoubleInsertElementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMInsertElementNodeFactory.LLVMFloatInsertElementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMInsertElementNodeFactory.LLVMI16InsertElementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMInsertElementNodeFactory.LLVMI1InsertElementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMInsertElementNodeFactory.LLVMI32InsertElementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMInsertElementNodeFactory.LLVMI64InsertElementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMInsertElementNodeFactory.LLVMI8InsertElementNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMShuffleVectorNodeFactory.LLVMShuffleDoubleVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMShuffleVectorNodeFactory.LLVMShuffleFloatVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMShuffleVectorNodeFactory.LLVMShuffleI16VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMShuffleVectorNodeFactory.LLVMShuffleI1VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMShuffleVectorNodeFactory.LLVMShuffleI32VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMShuffleVectorNodeFactory.LLVMShuffleI64VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vector.LLVMShuffleVectorNodeFactory.LLVMShuffleI8VectorNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.symbols.LocalVariableDebugInfo;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

public class BasicNodeFactory implements NodeFactory {
    protected final LLVMContext context;
    protected DataLayout dataLayout;

    public BasicNodeFactory(LLVMContext context, DataLayout dataLayout) {
        this.context = context;
        this.dataLayout = dataLayout;
    }

    @Override
    public LLVMExpressionNode createInsertElement(Type resultType, LLVMExpressionNode vector, LLVMExpressionNode element, LLVMExpressionNode index) {
        VectorType vectorType = (VectorType) resultType;
        int vectorLength = vectorType.getNumberOfElementsInt();
        if (vectorType.getElementType() instanceof PrimitiveType) {
            switch (((PrimitiveType) vectorType.getElementType()).getPrimitiveKind()) {
                case I1:
                    return LLVMI1InsertElementNodeGen.create(vector, element, index, vectorLength);
                case I8:
                    return LLVMI8InsertElementNodeGen.create(vector, element, index, vectorLength);
                case I16:
                    return LLVMI16InsertElementNodeGen.create(vector, element, index, vectorLength);
                case I32:
                    return LLVMI32InsertElementNodeGen.create(vector, element, index, vectorLength);
                case I64:
                    return LLVMI64InsertElementNodeGen.create(vector, element, index, vectorLength);
                case FLOAT:
                    return LLVMFloatInsertElementNodeGen.create(vector, element, index, vectorLength);
                case DOUBLE:
                    return LLVMDoubleInsertElementNodeGen.create(vector, element, index, vectorLength);
                default:
                    throw new AssertionError("vector type " + vectorType + "  is not supported for insertelement");
            }
        } else if (vectorType.getElementType() instanceof PointerType || vectorType.getElementType() instanceof FunctionType) {
            return LLVMI64InsertElementNodeGen.create(vector, element, index, vectorLength);
        }
        throw new AssertionError("vector type " + vectorType + "  is not supported for insertelement");
    }

    @Override
    public LLVMExpressionNode createExtractElement(Type resultType, LLVMExpressionNode vector, LLVMExpressionNode index) {
        if (resultType instanceof PrimitiveType) {
            PrimitiveType resultType1 = (PrimitiveType) resultType;
            switch (resultType1.getPrimitiveKind()) {
                case I1:
                    return LLVMI1ExtractElementNodeGen.create(vector, index);
                case I8:
                    return LLVMI8ExtractElementNodeGen.create(vector, index);
                case I16:
                    return LLVMI16ExtractElementNodeGen.create(vector, index);
                case I32:
                    return LLVMI32ExtractElementNodeGen.create(vector, index);
                case I64:
                    return LLVMI64ExtractElementNodeGen.create(vector, index);
                case FLOAT:
                    return LLVMFloatExtractElementNodeGen.create(vector, index);
                case DOUBLE:
                    return LLVMDoubleExtractElementNodeGen.create(vector, index);
                default:
                    throw new AssertionError(resultType1 + " is not supported for extractelement");
            }
        } else if (resultType instanceof PointerType || resultType instanceof FunctionType) {
            return LLVMI64ExtractElementNodeGen.create(vector, index);
        } else {
            throw new AssertionError(resultType + " is not supported for extractelement");
        }
    }

    @Override
    public LLVMExpressionNode createShuffleVector(Type llvmType, LLVMExpressionNode vector1, LLVMExpressionNode vector2,
                    LLVMExpressionNode mask) {
        VectorType resultType = (VectorType) llvmType;
        int resultLength = resultType.getNumberOfElementsInt();
        if (resultType.getElementType() instanceof PrimitiveType) {
            switch (((PrimitiveType) resultType.getElementType()).getPrimitiveKind()) {
                case I1:
                    return LLVMShuffleI1VectorNodeGen.create(vector1, vector2, mask, resultLength);
                case I8:
                    return LLVMShuffleI8VectorNodeGen.create(vector1, vector2, mask, resultLength);
                case I16:
                    return LLVMShuffleI16VectorNodeGen.create(vector1, vector2, mask, resultLength);
                case I32:
                    return LLVMShuffleI32VectorNodeGen.create(vector1, vector2, mask, resultLength);
                case I64:
                    return LLVMShuffleI64VectorNodeGen.create(vector1, vector2, mask, resultLength);
                case FLOAT:
                    return LLVMShuffleFloatVectorNodeGen.create(vector1, vector2, mask, resultLength);
                case DOUBLE:
                    return LLVMShuffleDoubleVectorNodeGen.create(vector1, vector2, mask, resultLength);
                default:
                    throw new AssertionError(resultType + " is not supported for shufflevector");
            }
        } else if (resultType.getElementType() instanceof PointerType || resultType.getElementType() instanceof FunctionType) {
            return LLVMShuffleI64VectorNodeGen.create(vector1, vector2, mask, resultLength);
        }
        throw new AssertionError(resultType + " is not supported for shufflevector");
    }

    @Override
    public LLVMStatementNode createStore(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
        try {
            return createStore(pointerNode, valueNode, type, getByteSize(type));
        } catch (TypeOverflowException e) {
            return Type.handleOverflowStatement(e);
        }
    }

    @Override
    public LLVMExpressionNode createRMWXchg(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
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
                throw new AssertionError("unsupported for atomicrmw xchg: " + type);
        }
    }

    @Override
    public LLVMExpressionNode createRMWAdd(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
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
                throw new AssertionError("unsupported add atomicrmw xchg: " + type);
        }
    }

    @Override
    public LLVMExpressionNode createRMWSub(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
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
                throw new AssertionError("unsupported sub atomicrmw xchg: " + type);
        }
    }

    @Override
    public LLVMExpressionNode createRMWAnd(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
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
                throw new AssertionError("unsupported for atomicrmw and: " + type);
        }
    }

    @Override
    public LLVMExpressionNode createRMWNand(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
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
                throw new AssertionError("unsupported for atomicrmw nand: " + type);
        }
    }

    @Override
    public LLVMExpressionNode createRMWOr(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
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
                throw new AssertionError("unsupported for atomicrmw or: " + type);
        }
    }

    @Override
    public LLVMExpressionNode createRMWXor(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
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
                throw new AssertionError("unsupported for atomicrmw xor: " + type);
        }
    }

    @Override
    public LLVMStatementNode createFence() {
        return LLVMFenceNodeGen.create();
    }

    @Override
    public LLVMExpressionNode createVectorLiteralNode(List<LLVMExpressionNode> listValues, Type type) {
        LLVMExpressionNode[] vals = listValues.toArray(LLVMExpressionNode.NO_EXPRESSIONS);
        Type llvmType = ((VectorType) type).getElementType();
        if (llvmType instanceof PrimitiveType) {
            switch (((PrimitiveType) llvmType).getPrimitiveKind()) {
                case I1:
                    return LLVMI1VectorLiteralNodeGen.create(vals);
                case I8:
                    return LLVMI8VectorLiteralNodeGen.create(vals);
                case I16:
                    return LLVMI16VectorLiteralNodeGen.create(vals);
                case I32:
                    return LLVMI32VectorLiteralNodeGen.create(vals);
                case I64:
                    return LLVMI64VectorLiteralNodeGen.create(vals);
                case FLOAT:
                    return LLVMFloatVectorLiteralNodeGen.create(vals);
                case DOUBLE:
                    return LLVMDoubleVectorLiteralNodeGen.create(vals);
                default:
                    throw new AssertionError();
            }
        } else if (llvmType instanceof PointerType || llvmType instanceof FunctionType) {
            return LLVMPointerVectorLiteralNodeGen.create(vals);
        } else {
            throw new AssertionError(llvmType + " not yet supported");
        }
    }

    @Override
    public LLVMControlFlowNode createRetVoid() {
        return LLVMVoidReturnNodeGen.create();
    }

    @Override
    public LLVMControlFlowNode createNonVoidRet(LLVMExpressionNode retValue, Type type) {
        if (retValue == null) {
            throw new AssertionError();
        }
        if (type instanceof VectorType) {
            return LLVMVectorRetNodeGen.create(retValue);
        } else if (type instanceof VariableBitWidthType) {
            return LLVMIVarBitRetNodeGen.create(retValue);
        } else if (type instanceof PointerType || type instanceof FunctionType) {
            return LLVMAddressRetNodeGen.create(retValue);
        } else if (type instanceof StructureType) {
            try {
                long size = getByteSize(type);
                return LLVMStructRetNodeGen.create(createMemMove(), retValue, size);
            } catch (TypeOverflowException e) {
                return LLVMStructRetNodeGen.create(createMemMove(), Type.handleOverflowExpression(e), 0);
            }
        } else if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return LLVMI1RetNodeGen.create(retValue);
                case I8:
                    return LLVMI8RetNodeGen.create(retValue);
                case I16:
                    return LLVMI16RetNodeGen.create(retValue);
                case I32:
                    return LLVMI32RetNodeGen.create(retValue);
                case I64:
                    return LLVMI64RetNodeGen.create(retValue);
                case FLOAT:
                    return LLVMFloatRetNodeGen.create(retValue);
                case DOUBLE:
                    return LLVMDoubleRetNodeGen.create(retValue);
                case X86_FP80:
                    return LLVM80BitFloatRetNodeGen.create(retValue);
                default:
                    throw new AssertionError(type);
            }
        }
        throw new AssertionError(type);
    }

    @Override
    public LLVMExpressionNode createFunctionArgNode(int argIndex, Type paramType) {
        if (argIndex < 0) {
            throw new AssertionError();
        }
        LLVMExpressionNode argNode = LLVMArgNodeGen.create(argIndex);
        if (argIndex < LLVMCallNode.USER_ARGUMENT_OFFSET) {
            // Do not profile the stackpointer
            return argNode;
        }
        return LLVMValueProfilingNode.create(argNode, paramType);
    }

    @Override
    public LLVMWriteNode createFrameWrite(Type llvmType, LLVMExpressionNode result, FrameSlot slot) {
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

    @Override
    public LLVMLoadNode createExtractValue(Type type, LLVMExpressionNode targetAddress) {
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return LLVMI1LoadNodeGen.create(targetAddress);
                case I8:
                    return LLVMI8LoadNodeGen.create(targetAddress);
                case I16:
                    return LLVMI16LoadNodeGen.create(targetAddress);
                case I32:
                    return LLVMI32LoadNodeGen.create(targetAddress);
                case I64:
                    return LLVMI64LoadNodeGen.create(targetAddress);
                case FLOAT:
                    return LLVMFloatLoadNodeGen.create(targetAddress);
                case DOUBLE:
                    return LLVMDoubleLoadNodeGen.create(targetAddress);
                case X86_FP80:
                    return LLVM80BitFloatDirectLoadNodeGen.create(targetAddress);
                default:
                    throw new AssertionError(type);
            }
        } else if (type instanceof VectorType) {
            VectorType vectorType = (VectorType) type;
            int vectorLength = vectorType.getNumberOfElementsInt();
            Type elementType = vectorType.getElementType();
            if (elementType instanceof PrimitiveType) {
                switch (((PrimitiveType) elementType).getPrimitiveKind()) {
                    case I1:
                        return LLVMLoadI1VectorNodeGen.create(targetAddress, vectorLength);
                    case I8:
                        return LLVMLoadI8VectorNodeGen.create(targetAddress, vectorLength);
                    case I16:
                        return LLVMLoadI16VectorNodeGen.create(targetAddress, vectorLength);
                    case I32:
                        return LLVMLoadI32VectorNodeGen.create(targetAddress, vectorLength);
                    case I64:
                        return LLVMLoadI64VectorNodeGen.create(targetAddress, vectorLength);
                    case FLOAT:
                        return LLVMLoadFloatVectorNodeGen.create(targetAddress, vectorLength);
                    case DOUBLE:
                        return LLVMLoadDoubleVectorNodeGen.create(targetAddress, vectorLength);
                    default:
                        throw new AssertionError(type);
                }
            } else if (elementType instanceof PointerType || elementType instanceof FunctionType) {
                return LLVMLoadPointerVectorNodeGen.create(targetAddress, vectorLength);
            } else {
                throw new AssertionError(type);
            }
        } else if (type instanceof PointerType || type instanceof StructureType || type instanceof ArrayType) {
            return LLVMPointerDirectLoadNodeGen.create(targetAddress);
        } else {
            throw new AssertionError(type + " is not supported for extractvalue");
        }
    }

    @Override
    public LLVMExpressionNode createTypedElementPointer(long indexedTypeLength, Type targetType, LLVMExpressionNode aggregateAddress, LLVMExpressionNode index) {
        return LLVMGetElementPtrNodeGen.create(indexedTypeLength, targetType, aggregateAddress, index);
    }

    @Override
    public LLVMExpressionNode createVectorizedTypedElementPointer(long indexedTypeLength, Type targetType, LLVMExpressionNode aggregateAddress, LLVMExpressionNode index) {
        return LLVMVectorizedGetElementPtrNodeGen.create(indexedTypeLength, targetType, aggregateAddress, index);
    }

    @Override
    public LLVMExpressionNode createSelect(Type type, LLVMExpressionNode condition, LLVMExpressionNode trueValue, LLVMExpressionNode falseValue) {
        if (type instanceof VectorType) {
            VectorType vectorType = (VectorType) type;
            final Type elementType = vectorType.getElementType();
            int vectorLength = vectorType.getNumberOfElementsInt();
            if (elementType == PrimitiveType.I1) {
                return LLVMI1VectorSelectNodeGen.create(condition, trueValue, falseValue, vectorLength);
            } else if (elementType == PrimitiveType.I8) {
                return LLVMI8VectorSelectNodeGen.create(condition, trueValue, falseValue, vectorLength);
            } else if (elementType == PrimitiveType.I16) {
                return LLVMI16VectorSelectNodeGen.create(condition, trueValue, falseValue, vectorLength);
            } else if (elementType == PrimitiveType.I32) {
                return LLVMI32VectorSelectNodeGen.create(condition, trueValue, falseValue, vectorLength);
            } else if (elementType == PrimitiveType.I64 || elementType instanceof PointerType || elementType instanceof FunctionType) {
                return LLVMI64VectorSelectNodeGen.create(condition, trueValue, falseValue, vectorLength);
            } else if (elementType == PrimitiveType.FLOAT) {
                return LLVMFloatVectorSelectNodeGen.create(condition, trueValue, falseValue, vectorLength);
            } else if (elementType == PrimitiveType.DOUBLE) {
                return LLVMDoubleVectorSelectNodeGen.create(condition, trueValue, falseValue, vectorLength);
            } else {
                throw new AssertionError("Cannot create vector select for type: " + type);
            }
        } else if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return LLVMI1SelectNodeGen.create(condition, trueValue, falseValue);
                case I8:
                    return LLVMI8SelectNodeGen.create(condition, trueValue, falseValue);
                case I16:
                    return LLVMI16SelectNodeGen.create(condition, trueValue, falseValue);
                case I32:
                    return LLVMI32SelectNodeGen.create(condition, trueValue, falseValue);
                case I64:
                    return LLVMI64SelectNodeGen.create(condition, trueValue, falseValue);
                case FLOAT:
                    return LLVMFloatSelectNodeGen.create(condition, trueValue, falseValue);
                case DOUBLE:
                    return LLVMDoubleSelectNodeGen.create(condition, trueValue, falseValue);
                case X86_FP80:
                    return LLVM80BitFloatSelectNodeGen.create(condition, trueValue, falseValue);
            }
        }
        return LLVMGenericSelectNodeGen.create(condition, trueValue, falseValue);
    }

    @Override
    public LLVMExpressionNode createZeroVectorInitializer(int nrElements, VectorType llvmType) {
        Type llvmType1 = llvmType.getElementType();
        if (llvmType1 instanceof PrimitiveType) {
            switch (((PrimitiveType) llvmType1).getPrimitiveKind()) {
                case I1:
                    LLVMExpressionNode[] i1Vals = createI1LiteralNodes(nrElements, false);
                    return LLVMI1VectorLiteralNodeGen.create(i1Vals);
                case I8:
                    LLVMExpressionNode[] i8Vals = createI8LiteralNodes(nrElements, (byte) 0);
                    return LLVMI8VectorLiteralNodeGen.create(i8Vals);
                case I16:
                    LLVMExpressionNode[] i16Vals = createI16LiteralNodes(nrElements, (short) 0);
                    return LLVMI16VectorLiteralNodeGen.create(i16Vals);
                case I32:
                    LLVMExpressionNode[] i32Vals = createI32LiteralNodes(nrElements, 0);
                    return LLVMI32VectorLiteralNodeGen.create(i32Vals);
                case I64:
                    LLVMExpressionNode[] i64Vals = createI64LiteralNodes(nrElements, 0);
                    return LLVMI64VectorLiteralNodeGen.create(i64Vals);
                case FLOAT:
                    LLVMExpressionNode[] floatVals = createFloatLiteralNodes(nrElements, 0.0f);
                    return LLVMFloatVectorLiteralNodeGen.create(floatVals);
                case DOUBLE:
                    LLVMExpressionNode[] doubleVals = createDoubleLiteralNodes(nrElements, 0.0f);
                    return LLVMDoubleVectorLiteralNodeGen.create(doubleVals);
                default:
                    throw new AssertionError(llvmType1);
            }
        } else if (llvmType1 instanceof PointerType) {
            LLVMExpressionNode[] addressVals = createNullAddressLiteralNodes(nrElements);
            return LLVMPointerVectorLiteralNodeGen.create(addressVals);
        } else {
            throw new AssertionError(llvmType1 + " not yet supported");
        }
    }

    @Override
    public LLVMExpressionNode createLiteral(Object value, Type type) {
        if (type instanceof PointerType || type instanceof FunctionType) {
            if (LLVMNativePointer.isInstance(value)) {
                return LLVMNativePointerLiteralNodeGen.create(LLVMNativePointer.cast(value));
            } else if (LLVMManagedPointer.isInstance(value)) {
                return LLVMManagedPointerLiteralNodeGen.create(LLVMManagedPointer.cast(value));
            } else if (value instanceof LLVMGlobal || value instanceof LLVMFunction) {
                return LLVMAccessSymbolNodeGen.create((LLVMSymbol) value);
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
        }
        throw new AssertionError(value + " " + type);
    }

    @Override
    public LLVMControlFlowNode createUnreachableNode() {
        return LLVMUnreachableNodeGen.create();
    }

    @Override
    public LLVMControlFlowNode createIndirectBranch(LLVMExpressionNode value, int[] labelTargets, LLVMStatementNode[] phiWrites) {
        return LLVMIndirectBranchNode.create(value, labelTargets, phiWrites);
    }

    @Override
    public LLVMControlFlowNode createSwitch(LLVMExpressionNode cond, int[] successors, LLVMExpressionNode[] cases, Type llvmType, LLVMStatementNode[] phiWriteNodes) {
        LLVMExpressionNode[] caseNodes = Arrays.copyOf(cases, cases.length, LLVMExpressionNode[].class);
        return LLVMSwitchNode.create(successors, phiWriteNodes, cond, caseNodes);
    }

    @Override
    public LLVMControlFlowNode createConditionalBranch(int trueIndex, int falseIndex, LLVMExpressionNode conditionNode, LLVMStatementNode truePhiWriteNodes,
                    LLVMStatementNode falsePhiWriteNodes) {
        return LLVMConditionalBranchNode.create(trueIndex, falseIndex, truePhiWriteNodes, falsePhiWriteNodes, conditionNode);
    }

    @Override
    public LLVMControlFlowNode createUnconditionalBranch(int unconditionalIndex, LLVMStatementNode phiWrites) {
        return LLVMBrUnconditionalNode.create(unconditionalIndex, phiWrites);
    }

    @Override
    public LLVMExpressionNode createArrayLiteral(LLVMExpressionNode[] arrayValues, ArrayType arrayType, GetStackSpaceFactory arrayGetStackSpaceFactory) {
        assert arrayType.getNumberOfElements() == arrayValues.length;
        LLVMExpressionNode arrayGetStackSpace = arrayGetStackSpaceFactory.createGetStackSpace(this, arrayType);
        Type elementType = arrayType.getElementType();
        try {
            long elementSize = getByteSize(elementType);
            if (elementSize == 0) {
                throw new TypeOverflowException(elementType + " has size of 0!");
            }
            if (elementType instanceof PrimitiveType || elementType instanceof PointerType || elementType instanceof FunctionType || elementType instanceof VariableBitWidthType) {
                return LLVMArrayLiteralNodeGen.create(arrayValues, elementSize, createMemoryStore(elementType), arrayGetStackSpace);
            } else if (elementType instanceof ArrayType || elementType instanceof StructureType) {
                return LLVMStructArrayLiteralNodeGen.create(arrayValues, createMemMove(), elementSize, arrayGetStackSpace);
            }
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
        throw new AssertionError(elementType);
    }

    @Override
    public LLVMExpressionNode createAlloca(Type type) {
        try {
            int alignment = getByteAlignment(type);
            long byteSize = getByteSize(type);
            LLVMGetStackForConstInstruction alloc = LLVMAllocaConstInstructionNodeGen.create(byteSize, alignment, type);
            return createGetStackSpace(type, alloc, byteSize);
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
    }

    @Override
    public LLVMExpressionNode createAlloca(Type type, int alignment) {
        try {
            long byteSize = getByteSize(type);
            LLVMGetStackForConstInstruction alloc = LLVMAllocaConstInstructionNodeGen.create(byteSize, alignment, type);
            return createGetStackSpace(type, alloc, byteSize);
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
    }

    @Override
    public LLVMExpressionNode createGetUniqueStackSpace(Type type, UniquesRegion uniquesRegion) {
        try {
            int alignment = getByteAlignment(type);
            long byteSize = getByteSize(type);
            UniqueSlot slot = uniquesRegion.addSlot(byteSize, alignment);
            LLVMGetStackForConstInstruction getStackSpace = LLVMGetUniqueStackSpaceInstructionNodeGen.create(byteSize, alignment, type, slot);
            return createGetStackSpace(type, getStackSpace, byteSize);
        } catch (LLVMStackOverflowError soe) {
            return LLVMLazyException.createExpressionNode(LLVMAllocationFailureException::new, soe);
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
    }

    protected LLVMExpressionNode createGetStackSpace(Type type, LLVMGetStackForConstInstruction getStackSpace, long byteSize) {
        if (type instanceof StructureType) {
            StructureType struct = (StructureType) type;
            final long[] offsets = new long[struct.getNumberOfElementsInt()];
            final Type[] types = new Type[struct.getNumberOfElementsInt()];
            long currentOffset = 0;
            try {
                for (int i = 0; i < struct.getNumberOfElements(); i++) {
                    final Type elemType = struct.getElementType(i);

                    if (!struct.isPacked()) {
                        currentOffset = Type.addUnsignedExact(currentOffset, getBytePadding(currentOffset, elemType));
                    }

                    offsets[i] = currentOffset;
                    types[i] = elemType;
                    currentOffset = Type.addUnsignedExact(currentOffset, getByteSize(elemType));
                }
            } catch (TypeOverflowException e) {
                return Type.handleOverflowExpression(e);
            }
            assert currentOffset <= byteSize : "currentOffset " + currentOffset + " vs. byteSize " + byteSize;
            getStackSpace.setTypes(types);
            getStackSpace.setOffsets(offsets);
        }
        return getStackSpace;
    }

    @Override
    public LLVMExpressionNode createAllocaArray(Type elementType, LLVMExpressionNode numElements, int alignment) {
        try {
            long byteSize = getByteSize(elementType);
            return LLVMAllocaInstructionNodeGen.create(byteSize, alignment, elementType, numElements);
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
    }

    @Override
    public VarargsAreaStackAllocationNode createVarargsAreaStackAllocation() {
        return LLVMNativeVarargsAreaStackAllocationNodeGen.create();
    }

    @Override
    public LLVMExpressionNode createInsertValue(LLVMExpressionNode resultAggregate, LLVMExpressionNode sourceAggregate, long size, long offset, LLVMExpressionNode valueToInsert, Type llvmType) {
        LLVMStoreNode store;
        if (llvmType instanceof PrimitiveType) {
            switch (((PrimitiveType) llvmType).getPrimitiveKind()) {
                case I1:
                    store = LLVMI1StoreNodeGen.create(null, null);
                    break;
                case I8:
                    store = LLVMI8StoreNodeGen.create(null, null);
                    break;
                case I16:
                    store = LLVMI16StoreNodeGen.create(null, null);
                    break;
                case I32:
                    store = LLVMI32StoreNodeGen.create(null, null);
                    break;
                case I64:
                    store = LLVMI64StoreNodeGen.create(null, null);
                    break;
                case FLOAT:
                    store = LLVMFloatStoreNodeGen.create(null, null);
                    break;
                case DOUBLE:
                    store = LLVMDoubleStoreNodeGen.create(null, null);
                    break;
                case X86_FP80:
                    store = LLVM80BitFloatStoreNodeGen.create(null, null);
                    break;
                default:
                    throw new AssertionError(llvmType + " is not supported for insertvalue");
            }
        } else if (llvmType instanceof VectorType) {
            store = LLVMStoreVectorNodeGen.create(null, null, ((VectorType) llvmType).getNumberOfElementsInt());
        } else if (llvmType instanceof PointerType) {
            store = LLVMPointerStoreNodeGen.create(null, null);
        } else {
            throw new AssertionError(llvmType + " is not supported for insertvalue");
        }
        return LLVMInsertValueNodeGen.create(store, createMemMove(), size, offset, sourceAggregate, resultAggregate, valueToInsert);
    }

    @Override
    public LLVMExpressionNode createZeroNode(LLVMExpressionNode addressNode, long size) {
        return LLVMMemSetNodeGen.create(createMemSet(), addressNode, LLVMI8LiteralNodeGen.create((byte) 0), LLVMI64LiteralNodeGen.create(size), LLVMI1LiteralNodeGen.create(false));
    }

    @Override
    public LLVMExpressionNode createStructureConstantNode(Type structType, GetStackSpaceFactory getStackSpaceFactory, boolean packed, Type[] types,
                    LLVMExpressionNode[] constants) {
        long[] offsets = new long[types.length];
        LLVMStoreNode[] nodes = new LLVMStoreNode[types.length];
        long currentOffset = 0;
        LLVMExpressionNode getStackSpace = getStackSpaceFactory.createGetStackSpace(this, structType);
        try {
            for (int i = 0; i < types.length; i++) {
                Type resolvedType = types[i];
                if (!packed) {
                    currentOffset = Type.addUnsignedExact(currentOffset, getBytePadding(currentOffset, resolvedType));
                }
                offsets[i] = currentOffset;
                long byteSize = getByteSize(resolvedType);
                nodes[i] = createMemoryStore(resolvedType);
                currentOffset = Type.addUnsignedExact(currentOffset, byteSize);
            }
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
        return StructLiteralNodeGen.create(offsets, nodes, constants, getStackSpace);
    }

    private LLVMStoreNode createMemoryStore(Type resolvedType) throws TypeOverflowException {
        if (resolvedType instanceof ArrayType || resolvedType instanceof StructureType) {
            long byteSize = getByteSize(resolvedType);
            return LLVMStructStoreNodeGen.create(createMemMove(), null, null, byteSize);
        } else if (resolvedType instanceof PrimitiveType) {
            switch (((PrimitiveType) resolvedType).getPrimitiveKind()) {
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
                case X86_FP80:
                    return LLVM80BitFloatStoreNodeGen.create(null, null);
                default:
                    throw new AssertionError(resolvedType);
            }
        } else if (resolvedType instanceof PointerType || resolvedType instanceof FunctionType) {
            return LLVMPointerStoreNodeGen.create(null, null);
        } else if (resolvedType instanceof VariableBitWidthType) {
            return LLVMIVarBitStoreNodeGen.create(null, null);
        }
        throw new AssertionError(resolvedType);
    }

    @Override
    public LLVMExpressionNode createFunctionBlockNode(FrameSlot exceptionValueSlot, LLVMBasicBlockNode[] allFunctionNodes, UniquesRegionAllocator uniquesRegionAllocator,
                    LLVMStatementNode[] copyArgumentsToFrame, LLVMSourceLocation location, FrameDescriptor frameDescriptor, FrameSlot loopSuccessorSlot, LocalVariableDebugInfo debugInfo) {
        LLVMUniquesRegionAllocNode uniquesRegionAllocNode = LLVMUniquesRegionAllocNodeGen.create(uniquesRegionAllocator);
        LLVMDispatchBasicBlockNode body = LLVMDispatchBasicBlockNodeGen.create(exceptionValueSlot, allFunctionNodes, loopSuccessorSlot, debugInfo);
        body.setSourceLocation(LLVMSourceLocation.orDefault(location));
        final LLVMFunctionRootNode functionRoot = LLVMFunctionRootNodeGen.create(uniquesRegionAllocNode, copyArgumentsToFrame, body, frameDescriptor);
        functionRoot.setSourceLocation(LLVMSourceLocation.orDefault(location));
        return functionRoot;
    }

    @Override
    public RootNode createFunctionStartNode(LLVMExpressionNode functionBodyNode, FrameDescriptor frame, String name, String originalName,
                    int argumentCount, Source bcSource, LLVMSourceLocation location) {
        return new LLVMFunctionStartNode(context.getLanguage(), functionBodyNode, frame, name, argumentCount, originalName, bcSource, location, dataLayout);
    }

    @Override
    public LLVMExpressionNode createInlineAssemblerExpression(ExternalLibrary library, String asmExpression, String asmFlags, LLVMExpressionNode[] args, Type.TypeArrayBuilder argTypes,
                    Type retType) {
        Type[] retTypes = null;
        long[] retOffsets = null;
        if (retType instanceof StructureType) { // multiple out values
            assert args[1] instanceof LLVMGetStackForConstInstruction;
            LLVMGetStackForConstInstruction getStackSpace = (LLVMGetStackForConstInstruction) args[1];
            retTypes = getStackSpace.getTypes();
            retOffsets = getStackSpace.getOffsets();
        }

        LLVMInlineAssemblyRootNode assemblyRoot;
        try {
            assemblyRoot = InlineAssemblyParser.parseInlineAssembly(context.getLanguage(), asmExpression, asmFlags, argTypes, retType, retTypes, retOffsets);
        } catch (AsmParseException e) {
            assemblyRoot = getLazyUnsupportedInlineRootNode(asmExpression, e);
        }
        LLVMIRFunction function = new LLVMIRFunction(Truffle.getRuntime().createCallTarget(assemblyRoot), null);
        LLVMFunction functionDetail = LLVMFunction.create("<asm>", library, function, new FunctionType(MetaType.UNKNOWN, 0, false), LLVMSymbol.INVALID_INDEX, LLVMSymbol.INVALID_INDEX,
                        false);
        LLVMFunctionDescriptor asm = context.createFunctionDescriptor(functionDetail);
        LLVMManagedPointerLiteralNode asmFunction = LLVMManagedPointerLiteralNodeGen.create(LLVMManagedPointer.create(asm));

        return LLVMCallNode.create(new FunctionType(MetaType.UNKNOWN, argTypes, false), asmFunction, args, false);
    }

    private LLVMInlineAssemblyRootNode getLazyUnsupportedInlineRootNode(String asmExpression, AsmParseException e) {
        LLVMInlineAssemblyRootNode assemblyRoot;
        String message = asmExpression + ": " + e.getMessage();
        assemblyRoot = new LLVMInlineAssemblyRootNode(context.getLanguage(), new FrameDescriptor(),
                        Collections.singletonList(LLVMUnsupportedInstructionNode.create(UnsupportedReason.INLINE_ASSEMBLER, message)), Collections.emptyList(), null);
        return assemblyRoot;
    }

    @Override
    public LLVMControlFlowNode createFunctionInvoke(LLVMWriteNode writeResult, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type,
                    int normalIndex, int unwindIndex, LLVMStatementNode normalPhiWriteNodes, LLVMStatementNode unwindPhiWriteNodes) {
        return LLVMInvokeNode.create(type, writeResult, functionNode, argNodes, normalIndex, unwindIndex, normalPhiWriteNodes, unwindPhiWriteNodes);
    }

    @Override
    public LLVMExpressionNode createLandingPad(LLVMExpressionNode allocateLandingPadValue, FrameSlot exceptionValueSlot, boolean cleanup, long[] clauseKinds,
                    LLVMExpressionNode[] entries, LLVMExpressionNode getStack) {

        LLVMLandingpadNode.LandingpadEntryNode[] landingpadEntries = new LLVMLandingpadNode.LandingpadEntryNode[entries.length];
        for (int i = 0; i < entries.length; i++) {
            if (clauseKinds[i] == 0) {
                // catch
                landingpadEntries[i] = getLandingpadCatchEntry(entries[i]);
            } else if (clauseKinds[i] == 1) {
                // filter
                landingpadEntries[i] = getLandingpadFilterEntry(entries[i]);
            } else {
                throw new IllegalStateException();
            }
        }
        return LLVMLandingpadNodeGen.create(getStack, allocateLandingPadValue, exceptionValueSlot, cleanup, landingpadEntries);
    }

    private static LLVMLandingpadNode.LandingpadEntryNode getLandingpadCatchEntry(LLVMExpressionNode exp) {
        return LLVMLandingpadNode.createCatchEntry(exp);
    }

    private static LLVMLandingpadNode.LandingpadEntryNode getLandingpadFilterEntry(LLVMExpressionNode exp) {
        LLVMExpressionNode arrayNode = exp;
        LLVMArrayLiteralNode array = (LLVMArrayLiteralNode) arrayNode;
        LLVMExpressionNode[] types = array == null ? LLVMExpressionNode.NO_EXPRESSIONS : array.getValues();
        return LLVMLandingpadNode.createFilterEntry(types);
    }

    @Override
    public LLVMControlFlowNode createResumeInstruction(FrameSlot exceptionValueSlot) {
        return LLVMResumeNodeGen.create(exceptionValueSlot);
    }

    @Override
    public LLVMExpressionNode createCompareExchangeInstruction(AggregateType returnType, Type elementType, LLVMExpressionNode ptrNode, LLVMExpressionNode cmpNode,
                    LLVMExpressionNode newNode) {
        try {
            return LLVMCompareExchangeNode.create(returnType, dataLayout, ptrNode, cmpNode, newNode);
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
    }

    @Override
    public LLVMExpressionNode createLLVMBuiltin(Symbol target, LLVMExpressionNode[] args, Type.TypeArrayBuilder argsTypes, int callerArgumentCount) {
        if (target instanceof FunctionDeclaration) {
            FunctionDeclaration declaration = (FunctionDeclaration) target;
            String name = declaration.getName();
            /*
             * These "llvm." builtins are *not* function intrinsics. Builtins replace statements
             * that look like function calls but are actually LLVM intrinsics. An example is
             * llvm.stackpointer. Also, it is not possible to retrieve the functionpointer of such
             * pseudo-call-targets.
             *
             * These builtins shall not be used for regular function intrinsification!
             */
            if (name.startsWith("llvm.")) {
                return getLLVMBuiltin(declaration, args, callerArgumentCount);
            } else if (name.startsWith("__builtin_")) {
                return getGccBuiltin(declaration, args);
            } else if (name.equals("polyglot_get_arg")) {
                // this function accesses the frame directly
                // it must therefore not be hidden behind a call target
                return LLVMTruffleGetArgNodeGen.create(args[1]);
            } else if (name.equals("polyglot_get_arg_count")) {
                // this function accesses the frame directly
                // it must therefore not be hidden behind a call target
                return LLVMTruffleGetArgCountNodeGen.create();
            } else {
                // Inline Sulong intrinsics directly at their call site, to avoid the overhead of a
                // call node and extra argument nodes.
                LLVMIntrinsicProvider intrinsicProvider = context.getLanguage().getCapability(LLVMIntrinsicProvider.class);
                return intrinsicProvider.generateIntrinsicNode(name, args, argsTypes, this);
            }
        }
        return null;
    }

    private LLVMExpressionNode createMemsetIntrinsic(LLVMExpressionNode[] args) {
        if (args.length == 6) {
            return LLVMMemSetNodeGen.create(createMemSet(), args[1], args[2], args[3], args[5]);
        } else if (args.length == 5) {
            // LLVM 7 drops the alignment argument
            return LLVMMemSetNodeGen.create(createMemSet(), args[1], args[2], args[3], args[4]);
        } else {
            throw new LLVMParserException("Illegal number of arguments to @llvm.memset.*: " + args.length);
        }
    }

    private LLVMExpressionNode createMemcpyIntrinsic(LLVMExpressionNode[] args) {
        if (args.length == 6) {
            return LLVMMemCopyNodeGen.create(createMemMove(), args[1], args[2], args[3], args[5]);
        } else if (args.length == 5) {
            // LLVM 7 drops the alignment argument
            return LLVMMemCopyNodeGen.create(createMemMove(), args[1], args[2], args[3], args[4]);
        } else {
            throw new LLVMParserException("Illegal number of arguments to @llvm.memcpy.*: " + args.length);
        }
    }

    private LLVMExpressionNode createMemmoveIntrinsic(LLVMExpressionNode[] args) {
        if (args.length == 6) {
            return LLVMMemMoveI64NodeGen.create(createMemMove(), args[1], args[2], args[3], args[5]);
        } else if (args.length == 5) {
            // LLVM 7 drops the alignment argument
            return LLVMMemMoveI64NodeGen.create(createMemMove(), args[1], args[2], args[3], args[4]);
        } else {
            throw new LLVMParserException("Illegal number of arguments to @llvm.memmove.*: " + args.length);
        }
    }

    // matches the type suffix of an LLVM intrinsic function, including the dot
    private static final Pattern INTRINSIC_TYPE_SUFFIX_PATTERN = Pattern.compile("\\S+(?<suffix>\\.(?:[vp]\\d+)?[if]\\d+)$");

    private static String getTypeSuffix(String intrinsicName) {
        assert intrinsicName != null;
        final Matcher typeSuffixMatcher = INTRINSIC_TYPE_SUFFIX_PATTERN.matcher(intrinsicName);
        if (typeSuffixMatcher.matches()) {
            return typeSuffixMatcher.group("suffix");
        }
        return null;
    }

    protected LLVMExpressionNode getLLVMBuiltin(FunctionDeclaration declaration, LLVMExpressionNode[] args, int callerArgumentCount) {

        String intrinsicName = declaration.getName();
        try {
            switch (intrinsicName) {
                case "llvm.memset.p0i8.i32":
                case "llvm.memset.p0i8.i64":
                    return createMemsetIntrinsic(args);
                case "llvm.assume":
                    return LLVMAssumeNodeGen.create(args[1]);
                case "llvm.clear_cache": // STUB
                case "llvm.donothing":
                    return LLVMNoOpNodeGen.create();
                case "llvm.prefetch":
                    return LLVMPrefetchNodeGen.create(args[1], args[2], args[3], args[4]);
                case "llvm.ctlz.i8":
                    return CountLeadingZeroesI8NodeGen.create(args[1], args[2]);
                case "llvm.ctlz.i16":
                    return CountLeadingZeroesI16NodeGen.create(args[1], args[2]);
                case "llvm.ctlz.i32":
                    return CountLeadingZeroesI32NodeGen.create(args[1], args[2]);
                case "llvm.ctlz.i64":
                    return CountLeadingZeroesI64NodeGen.create(args[1], args[2]);
                case "llvm.memcpy.p0i8.p0i8.i64":
                case "llvm.memcpy.p0i8.p0i8.i32":
                    return createMemcpyIntrinsic(args);
                case "llvm.ctpop.i32":
                    return CountSetBitsI32NodeGen.create(args[1]);
                case "llvm.ctpop.i64":
                    return CountSetBitsI64NodeGen.create(args[1]);
                case "llvm.cttz.i8":
                    return CountTrailingZeroesI8NodeGen.create(args[1], args[2]);
                case "llvm.cttz.i16":
                    return CountTrailingZeroesI16NodeGen.create(args[1], args[2]);
                case "llvm.cttz.i32":
                    return CountTrailingZeroesI32NodeGen.create(args[1], args[2]);
                case "llvm.cttz.i64":
                    return CountTrailingZeroesI64NodeGen.create(args[1], args[2]);
                case "llvm.trap":
                    return LLVMTrapNodeGen.create();
                case "llvm.bswap.i16":
                    return LLVMByteSwapI16NodeGen.create(args[1]);
                case "llvm.bswap.i32":
                    return LLVMByteSwapI32NodeGen.create(args[1]);
                case "llvm.bswap.i64":
                    return LLVMByteSwapI64NodeGen.create(args[1]);
                case "llvm.bswap.v8i16":
                    return LLVMByteSwapI16VectorNodeGen.create(8, args[1]);
                case "llvm.bswap.v16i16":
                    return LLVMByteSwapI16VectorNodeGen.create(16, args[1]);
                case "llvm.bswap.v4i32":
                    return LLVMByteSwapI32VectorNodeGen.create(4, args[1]);
                case "llvm.bswap.v8i32":
                    return LLVMByteSwapI32VectorNodeGen.create(8, args[1]);
                case "llvm.bswap.v2i64":
                    return LLVMByteSwapI64VectorNodeGen.create(2, args[1]);
                case "llvm.bswap.v4i64":
                    return LLVMByteSwapI64VectorNodeGen.create(4, args[1]);
                case "llvm.memmove.p0i8.p0i8.i64":
                    return createMemmoveIntrinsic(args);
                case "llvm.pow.f32":
                    return LLVMPowNodeGen.create(args[1], args[2]);
                case "llvm.pow.f64":
                    return LLVMPowNodeGen.create(args[1], args[2]);
                case "llvm.pow.f80":
                    return LLVMPowNodeGen.create(args[1], args[2]);
                case "llvm.powi.f32":
                    return LLVMPowNodeGen.create(args[1], args[2]);
                case "llvm.powi.f64":
                    return LLVMPowNodeGen.create(args[1], args[2]);
                case "llvm.powi.f80":
                    return LLVMPowNodeGen.create(args[1], args[2]);
                case "llvm.round.f32":
                case "llvm.round.f64":
                case "llvm.round.f80":
                    return LLVMCMathsIntrinsicsFactory.LLVMRoundNodeGen.create(args[1]);
                case "llvm.fabs.f32":
                case "llvm.fabs.f64":
                case "llvm.fabs.f80":
                    return LLVMFAbsNodeGen.create(args[1]);
                case "llvm.fabs.v2f64":
                    return LLVMFAbsVectorNodeGen.create(args[1], 2);
                case "llvm.minnum.f32":
                case "llvm.minnum.f64":
                    return LLVMCMathsIntrinsicsFactory.LLVMMinnumNodeGen.create(args[1], args[2]);
                case "llvm.maxnum.f32":
                case "llvm.maxnum.f64":
                    return LLVMCMathsIntrinsicsFactory.LLVMMaxnumNodeGen.create(args[1], args[2]);
                case "llvm.returnaddress":
                    return LLVMReturnAddressNodeGen.create(args[1]);
                case "llvm.lifetime.start.p0i8":
                case "llvm.lifetime.start":
                    return LLVMLifetimeStartNodeGen.create(args[1], args[2]);
                case "llvm.lifetime.end.p0i8":
                case "llvm.lifetime.end":
                    return LLVMLifetimeEndNodeGen.create(args[1], args[2]);
                case "llvm.invariant.start":
                case "llvm.invariant.start.p0i8":
                    return LLVMInvariantStartNodeGen.create(args[1], args[2]);
                case "llvm.invariant.end":
                case "llvm.invariant.end.p0i8":
                    return LLVMInvariantEndNodeGen.create(args[1], args[2]);
                case "llvm.stacksave":
                    return createStackSave();
                case "llvm.stackrestore":
                    return createStackRestore(args[1]);
                case "llvm.frameaddress":
                    return LLVMFrameAddressNodeGen.create(args[1]);
                case "llvm.va_start":
                    return LLVMX86_64VAStartNodeGen.create(callerArgumentCount, createVarargsAreaStackAllocation(), createMemMove(), args[1]);
                case "llvm.va_end":
                    return LLVMX86_64BitVAEndNodeGen.create(args[1]);
                case "llvm.va_copy":
                    return LLVMX86_64BitVACopyNodeGen.create(args[1], args[2], callerArgumentCount);
                case "llvm.eh.sjlj.longjmp":
                case "llvm.eh.sjlj.setjmp":
                    return LLVMUnsupportedInstructionNode.createExpression(UnsupportedReason.SET_JMP_LONG_JMP);
                case "llvm.dbg.declare":
                case "llvm.dbg.addr":
                case "llvm.dbg.value":
                    throw new IllegalStateException("Unhandled call to intrinsic function " + declaration.getName());
                case "llvm.dbg.label":
                    // a call to dbg.label describes that execution has arrived at a label in the
                    // original source code. the source location of the call will be applied, rather
                    // than the explicit descriptor of the label which is passed to dbg.label. both
                    // reference the same line number, this just avoids special-casing dbg.label
                    // like
                    // the other dbg.* intrinsics.
                    return LLVMNoOpNodeGen.create();
                case "llvm.eh.typeid.for":
                    return LLVMTypeIdForExceptionNodeGen.create(args[1]);
                case "llvm.expect.i1": {
                    boolean expectedValue = LLVMTypesGen.asBoolean(args[2].executeGeneric(null));
                    LLVMExpressionNode actualValueNode = args[1];
                    return LLVMExpectI1NodeGen.create(expectedValue, actualValueNode);
                }
                case "llvm.expect.i32": {
                    int expectedValue = LLVMTypesGen.asInteger(args[2].executeGeneric(null));
                    LLVMExpressionNode actualValueNode = args[1];
                    return LLVMExpectI32NodeGen.create(expectedValue, actualValueNode);
                }
                case "llvm.expect.i64": {
                    long expectedValue = LLVMTypesGen.asLong(args[2].executeGeneric(null));
                    LLVMExpressionNode actualValueNode = args[1];
                    return LLVMExpectI64NodeGen.create(expectedValue, actualValueNode);
                }
                case "llvm.objectsize.i64.p0i8":
                case "llvm.objectsize.i64":
                    return LLVMI64ObjectSizeNodeGen.create(args[1], args[2]);
                case "llvm.copysign.f32":
                case "llvm.copysign.f64":
                case "llvm.copysign.f80":
                    return LLVMCMathsIntrinsicsFactory.LLVMCopySignNodeGen.create(args[1], args[2]);

                case "llvm.uadd.with.overflow.i8":
                case "llvm.uadd.with.overflow.i16":
                case "llvm.uadd.with.overflow.i32":
                case "llvm.uadd.with.overflow.i64":
                    return LLVMArithmeticWithOverflowNodeGen.create(LLVMArithmetic.UNSIGNED_ADD, getOverflowFieldOffset(declaration), args[2], args[3], args[1]);
                case "llvm.usub.with.overflow.i8":
                case "llvm.usub.with.overflow.i16":
                case "llvm.usub.with.overflow.i32":
                case "llvm.usub.with.overflow.i64":
                    return LLVMArithmeticWithOverflowNodeGen.create(LLVMArithmetic.UNSIGNED_SUB, getOverflowFieldOffset(declaration), args[2], args[3], args[1]);
                case "llvm.umul.with.overflow.i8":
                case "llvm.umul.with.overflow.i16":
                case "llvm.umul.with.overflow.i32":
                case "llvm.umul.with.overflow.i64":
                    return LLVMArithmeticWithOverflowNodeGen.create(LLVMArithmetic.UNSIGNED_MUL, getOverflowFieldOffset(declaration), args[2], args[3], args[1]);
                case "llvm.sadd.with.overflow.i8":
                case "llvm.sadd.with.overflow.i16":
                case "llvm.sadd.with.overflow.i32":
                case "llvm.sadd.with.overflow.i64":
                    return LLVMArithmeticWithOverflowNodeGen.create(LLVMArithmetic.SIGNED_ADD, getOverflowFieldOffset(declaration), args[2], args[3], args[1]);
                case "llvm.ssub.with.overflow.i8":
                case "llvm.ssub.with.overflow.i16":
                case "llvm.ssub.with.overflow.i32":
                case "llvm.ssub.with.overflow.i64":
                    return LLVMArithmeticWithOverflowNodeGen.create(LLVMArithmetic.SIGNED_SUB, getOverflowFieldOffset(declaration), args[2], args[3], args[1]);
                case "llvm.smul.with.overflow.i8":
                case "llvm.smul.with.overflow.i16":
                case "llvm.smul.with.overflow.i32":
                case "llvm.smul.with.overflow.i64":
                    return LLVMArithmeticWithOverflowNodeGen.create(LLVMArithmetic.SIGNED_MUL, getOverflowFieldOffset(declaration), args[2], args[3], args[1]);
                case "llvm.exp2.f32":
                case "llvm.exp2.f64":
                    return LLVMCMathsIntrinsicsFactory.LLVMExp2NodeGen.create(args[1]);
                case "llvm.sqrt.f32":
                case "llvm.sqrt.f64":
                    return LLVMCMathsIntrinsicsFactory.LLVMSqrtNodeGen.create(args[1]);
                case "llvm.sqrt.v2f64":
                    return LLVMCMathsIntrinsicsFactory.LLVMSqrtVectorNodeGen.create(args[1], 2);
                case "llvm.sin.f32":
                case "llvm.sin.f64":
                    return LLVMCMathsIntrinsicsFactory.LLVMSinNodeGen.create(args[1]);
                case "llvm.cos.f32":
                case "llvm.cos.f64":
                    return LLVMCMathsIntrinsicsFactory.LLVMCosNodeGen.create(args[1]);
                case "llvm.exp.f32":
                case "llvm.exp.f64":
                    return LLVMCMathsIntrinsicsFactory.LLVMExpNodeGen.create(args[1]);
                case "llvm.log.f32":
                case "llvm.log.f64":
                    return LLVMCMathsIntrinsicsFactory.LLVMLogNodeGen.create(args[1]);
                case "llvm.log2.f32":
                case "llvm.log2.f64":
                    return LLVMCMathsIntrinsicsFactory.LLVMLog2NodeGen.create(args[1]);
                case "llvm.log10.f32":
                case "llvm.log10.f64":
                    return LLVMCMathsIntrinsicsFactory.LLVMLog10NodeGen.create(args[1]);
                case "llvm.floor.f32":
                case "llvm.floor.f64":
                    return LLVMCMathsIntrinsicsFactory.LLVMFloorNodeGen.create(args[1]);
                case "llvm.ceil.f32":
                case "llvm.ceil.f64":
                    return LLVMCMathsIntrinsicsFactory.LLVMCeilNodeGen.create(args[1]);
                case "llvm.rint.f32":
                case "llvm.rint.f64":
                    return LLVMCMathsIntrinsicsFactory.LLVMRintNodeGen.create(args[1]);
                case "llvm.x86.sse.cvtss2si":
                    return LLVMX86_ConversionFloatToIntNodeGen.create(args[1]);
                case "llvm.x86.sse.cmp.ss":
                    return LLVMX86_CmpssNodeGen.create(args[1], args[2], args[3]);
                case "llvm.x86.sse2.cvtsd2si":
                    return LLVMX86_ConversionDoubleToIntNodeGen.create(args[1]);
                case "llvm.x86.sse2.sqrt.pd":
                    return LLVMX86_VectorSquareRootNodeGen.create(args[1]);
                case "llvm.x86.sse2.max.pd":
                    return LLVMX86_VectorMaxNodeGen.create(args[1], args[2]);
                case "llvm.x86.sse2.max.sd":
                    return LLVMX86_VectorMaxsdNodeGen.create(args[1], args[2]);
                case "llvm.x86.sse2.min.pd":
                    return LLVMX86_VectorMinNodeGen.create(args[1], args[2]);
                case "llvm.x86.sse2.cmp.sd":
                    return LLVMX86_VectorCmpNodeGen.create(args[1], args[2], args[3]);
                case "llvm.x86.sse2.packssdw.128":
                case "llvm.x86.sse2.packsswb.128":
                    return LLVMX86_VectorPackNodeGen.create(args[1], args[2]);
                case "llvm.x86.sse2.pmovmskb.128":
                    return LLVMX86_Pmovmskb128NodeGen.create(args[1]);
                case "llvm.x86.sse2.movmsk.pd":
                    return LLVMX86_MovmskpdNodeGen.create(args[1]);
                case "llvm.experimental.constrained.fpext.f64":
                case "llvm.experimental.constrained.fpext.f64.f32":
                    return LLVMSignedCastToDoubleNodeGen.create(args[1]);
                case "llvm.experimental.constrained.fpext.f80":
                case "llvm.experimental.constrained.fpext.f80.f64":
                    return LLVMSignedCastToLLVM80BitFloatNodeGen.create(args[1]);

/*
 * We ignore the two meta-arguments of the binary arithmetic llvm.experimental.constrained builtins
 * as they are just hints by compiler to optimization passes. They inform the passes on possible
 * assumptions about the current rounding mode and floating point exceptions (FPE) behavior.
 *
 * Nonetheless, as the values of rounding mode or the FPE behavior other than "round.tonearest" and
 * "fpexcept.ignore", which are the default and currently the only supported ones, indicate that the
 * code may want to ensure a specific rounding of numbers or respond to FPE, we should issue some
 * "unsupported FP mode" warning when parsing bitcode.
 */

                case "llvm.experimental.constrained.fadd.f32":
                    return LLVMFloatArithmeticNodeGen.create(ArithmeticOperation.ADD, args[1], args[2]);
                case "llvm.experimental.constrained.fadd.f64":
                    return LLVMDoubleArithmeticNodeGen.create(ArithmeticOperation.ADD, args[1], args[2]);
                case "llvm.experimental.constrained.fadd.f80":
                    return LLVMFP80ArithmeticNodeGen.create(ArithmeticOperation.ADD, args[1], args[2]);
                case "llvm.experimental.constrained.fsub.f32":
                    return LLVMFloatArithmeticNodeGen.create(ArithmeticOperation.SUB, args[1], args[2]);
                case "llvm.experimental.constrained.fsub.f64":
                    return LLVMDoubleArithmeticNodeGen.create(ArithmeticOperation.SUB, args[1], args[2]);
                case "llvm.experimental.constrained.fsub.f80":
                    return LLVMFP80ArithmeticNodeGen.create(ArithmeticOperation.SUB, args[1], args[2]);
                case "llvm.experimental.constrained.fmul.f32":
                    return LLVMFloatArithmeticNodeGen.create(ArithmeticOperation.MUL, args[1], args[2]);
                case "llvm.experimental.constrained.fmul.f64":
                    return LLVMDoubleArithmeticNodeGen.create(ArithmeticOperation.MUL, args[1], args[2]);
                case "llvm.experimental.constrained.fmul.f80":
                    return LLVMFP80ArithmeticNodeGen.create(ArithmeticOperation.MUL, args[1], args[2]);
                case "llvm.experimental.constrained.fdiv.f32":
                    return LLVMFloatArithmeticNodeGen.create(ArithmeticOperation.DIV, args[1], args[2]);
                case "llvm.experimental.constrained.fdiv.f64":
                    return LLVMDoubleArithmeticNodeGen.create(ArithmeticOperation.DIV, args[1], args[2]);
                case "llvm.experimental.constrained.fdiv.f80":
                    return LLVMFP80ArithmeticNodeGen.create(ArithmeticOperation.DIV, args[1], args[2]);
                case "llvm.experimental.constrained.frem.f32":
                    return LLVMFloatArithmeticNodeGen.create(ArithmeticOperation.REM, args[1], args[2]);
                case "llvm.experimental.constrained.frem.f64":
                    return LLVMDoubleArithmeticNodeGen.create(ArithmeticOperation.REM, args[1], args[2]);
                case "llvm.experimental.constrained.frem.f80":
                    return LLVMFP80ArithmeticNodeGen.create(ArithmeticOperation.REM, args[1], args[2]);
                default:
                    break;
            }
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }

        // strip the type suffix for intrinsics that are supported for more than one data type. If
        // we do not implement the corresponding data type the node will just report a missing
        // specialization at run-time
        String typeSuffix = getTypeSuffix(intrinsicName);
        if (typeSuffix != null) {
            intrinsicName = intrinsicName.substring(0, intrinsicName.length() - typeSuffix.length());
        }

        if ("llvm.prefetch".equals(intrinsicName)) {
            return LLVMPrefetchNodeGen.create(args[1], args[2], args[3], args[4]);
        }

        if ("llvm.is.constant".equals(intrinsicName)) {
            return LLVMIsConstantNodeGen.create(args[1]);
        }

        return LLVMX86_MissingBuiltin.create(declaration.getName());
    }

    @Override
    public LLVMExpressionNode createStackSave() {
        return LLVMStackSaveNodeGen.create();
    }

    @Override
    public LLVMExpressionNode createArithmeticOp(ArithmeticOperation op, Type type, LLVMExpressionNode left, LLVMExpressionNode right) {
        if (type instanceof VectorType) {
            VectorType vectorType = (VectorType) type;
            LLVMArithmeticNode arithmeticNode = createScalarArithmeticOp(op, vectorType.getElementType(), null, null);
            return LLVMVectorArithmeticNodeGen.create(vectorType.getNumberOfElementsInt(), arithmeticNode, left, right);
        } else {
            return createScalarArithmeticOp(op, type, left, right);
        }
    }

    protected LLVMArithmeticNode createScalarArithmeticOp(ArithmeticOperation op, Type type, LLVMExpressionNode left, LLVMExpressionNode right) {
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
                    throw new AssertionError("Unknown primitive type: " + type);
            }
        } else if (type instanceof VariableBitWidthType) {
            return LLVMIVarBitArithmeticNodeGen.create(op, left, right);
        } else {
            throw new AssertionError("Unknown type: " + type);
        }
    }

    @Override
    public LLVMExpressionNode createUnaryOp(UnaryOperation op, Type type, LLVMExpressionNode operand) {
        if (type instanceof VectorType) {
            VectorType vectorType = (VectorType) type;
            LLVMUnaryNode unaryNode = createScalarUnaryOp(op, vectorType.getElementType(), null);
            return LLVMVectorUnaryNodeGen.create(vectorType.getNumberOfElementsInt(), unaryNode, operand);
        } else {
            return createScalarUnaryOp(op, type, operand);
        }
    }

    protected LLVMUnaryNode createScalarUnaryOp(UnaryOperation op, Type type, LLVMExpressionNode operand) {
        assert !(type instanceof VectorType);
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case FLOAT:
                    return LLVMFloatUnaryNodeGen.create(op, operand);
                case DOUBLE:
                    return LLVMDoubleUnaryNodeGen.create(op, operand);
                case X86_FP80:
                    return LLVMFP80UnaryNodeGen.create(op, operand);
                default:
                    throw new UnsupportedOperationException("Type is unsupported for scalar unary operation: " + type);
            }
        } else {
            throw new AssertionError("Unknown type: " + type);
        }
    }

    @Override
    public LLVMExpressionNode createBitcast(LLVMExpressionNode fromNode, Type targetType, Type fromType) {
        return CommonNodeFactory.createBitcast(fromNode, targetType, fromType);
    }

    @Override
    public LLVMExpressionNode createStackRestore(LLVMExpressionNode stackPointer) {
        return LLVMStackRestoreNodeGen.create(stackPointer);
    }

    private long getOverflowFieldOffset(FunctionDeclaration declaration) throws TypeOverflowException {
        return getIndexOffset(1, (AggregateType) declaration.getType().getReturnType());
    }

    protected LLVMExpressionNode getGccBuiltin(FunctionDeclaration declaration, LLVMExpressionNode[] args) {
        switch (declaration.getName()) {
            case "__builtin_addcb":
            case "__builtin_addcs":
            case "__builtin_addc":
            case "__builtin_addcl":
                return LLVMArithmeticWithOverflowAndCarryNodeGen.create(LLVMArithmetic.CARRY_ADD, args[1], args[2], args[3], args[4]);
            case "__builtin_subcb":
            case "__builtin_subcs":
            case "__builtin_subc":
            case "__builtin_subcl":
                return LLVMArithmeticWithOverflowAndCarryNodeGen.create(LLVMArithmetic.CARRY_SUB, args[1], args[2], args[3], args[4]);
            case "__builtin_add_overflow":
                if (isZeroExtendArithmeticBuiltin(declaration)) {
                    throw new IllegalStateException("Missing GCC builtin: " + declaration.getName());
                } else {
                    return GCCArithmeticNodeGen.create(LLVMArithmetic.SIGNED_ADD, args[1], args[2], args[3]);
                }
            case "__builtin_sub_overflow":
                if (isZeroExtendArithmeticBuiltin(declaration)) {
                    throw new IllegalStateException("Missing GCC builtin: " + declaration.getName());
                } else {
                    return GCCArithmeticNodeGen.create(LLVMArithmetic.SIGNED_SUB, args[1], args[2], args[3]);
                }
            case "__builtin_mul_overflow":
                if (isZeroExtendArithmeticBuiltin(declaration)) {
                    return GCCArithmeticNodeGen.create(LLVMArithmetic.UNSIGNED_MUL, args[1], args[2], args[3]);
                } else {
                    return GCCArithmeticNodeGen.create(LLVMArithmetic.SIGNED_MUL, args[1], args[2], args[3]);
                }

            default:
                throw new IllegalStateException("Missing GCC builtin: " + declaration.getName());
        }
    }

    private static boolean isZeroExtendArithmeticBuiltin(FunctionDeclaration declaration) {
        final AttributesGroup group = declaration.getParameterAttributesGroup(0);
        if (group == null) {
            return false;
        }
        for (Attribute a : group.getAttributes()) {
            if (a instanceof KnownAttribute && ((KnownAttribute) a).getAttr() == Attribute.Kind.ZEROEXT) {
                return true;
            }
        }
        return false;
    }

    @Override
    public LLVMStatementNode createPhi(LLVMExpressionNode[] cycleFrom, LLVMWriteNode[] cycleWrites, LLVMWriteNode[] ordinaryWrites) {
        assert ordinaryWrites.length > 0 && cycleFrom.length == cycleWrites.length;
        return LLVMWritePhisNodeGen.create(cycleFrom, cycleWrites, ordinaryWrites);
    }

    @Override
    public LLVMExpressionNode createCopyStructByValue(Type type, GetStackSpaceFactory getStackSpaceFactory, LLVMExpressionNode parameterNode) {
        try {
            LLVMExpressionNode getStackSpaceNode = getStackSpaceFactory.createGetStackSpace(this, type);
            return LLVMStructByValueNodeGen.create(createMemMove(), getStackSpaceNode, parameterNode, getByteSize(type));
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
    }

    @Override
    public LLVMExpressionNode createVarArgCompoundValue(long length, int alignment, LLVMExpressionNode parameterNode) {
        return LLVMVarArgCompoundAddressNodeGen.create(parameterNode, length, alignment);
    }

    @Override
    public LLVMMemMoveNode createMemMove() {
        return NativeProfiledMemMoveNodeGen.create();
    }

    @Override
    public LLVMAllocateNode createAllocateGlobalsBlock(StructureType structType, boolean readOnly) {
        try {
            if (readOnly) {
                return new AllocateReadOnlyGlobalsBlockNode(context, structType, dataLayout);
            } else {
                return AllocateGlobalsBlockNode.create(structType, dataLayout);
            }
        } catch (TypeOverflowException e) {
            return Type.handleOverflowAllocate(e);
        }
    }

    @Override
    public LLVMMemoryOpNode createProtectGlobalsBlock() {
        return new ProtectReadOnlyGlobalsBlockNode(context);
    }

    @Override
    public LLVMMemoryOpNode createFreeGlobalsBlock(boolean readOnly) {
        if (readOnly) {
            return new FreeReadOnlyGlobalsBlockNode(context);
        } else {
            return LLVMFreeNodeGen.create(null);
        }
    }

    @Override
    public LLVMMemSetNode createMemSet() {
        return NativeMemSetNodeGen.create();
    }

    private static LLVMExpressionNode[] createDoubleLiteralNodes(int nrElements, double value) {
        LLVMExpressionNode[] doubleZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            doubleZeroInits[i] = LLVMDoubleLiteralNodeGen.create(value);
        }
        return doubleZeroInits;
    }

    private static LLVMExpressionNode[] createFloatLiteralNodes(int nrElements, float value) {
        LLVMExpressionNode[] floatZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            floatZeroInits[i] = LLVMFloatLiteralNodeGen.create(value);
        }
        return floatZeroInits;
    }

    private static LLVMExpressionNode[] createI64LiteralNodes(int nrElements, long value) {
        LLVMExpressionNode[] i64ZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i64ZeroInits[i] = LLVMI64LiteralNodeGen.create(value);
        }
        return i64ZeroInits;
    }

    private static LLVMExpressionNode[] createI32LiteralNodes(int nrElements, int value) {
        LLVMExpressionNode[] i32ZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i32ZeroInits[i] = LLVMI32LiteralNodeGen.create(value);
        }
        return i32ZeroInits;
    }

    private static LLVMExpressionNode[] createI16LiteralNodes(int nrElements, short value) {
        LLVMExpressionNode[] i16ZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i16ZeroInits[i] = LLVMI16LiteralNodeGen.create(value);
        }
        return i16ZeroInits;
    }

    private static LLVMExpressionNode[] createI8LiteralNodes(int nrElements, byte value) {
        LLVMExpressionNode[] i8ZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i8ZeroInits[i] = LLVMI8LiteralNodeGen.create(value);
        }
        return i8ZeroInits;
    }

    private static LLVMExpressionNode[] createI1LiteralNodes(int nrElements, boolean value) {
        LLVMExpressionNode[] i1ZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            i1ZeroInits[i] = LLVMI1LiteralNodeGen.create(value);
        }
        return i1ZeroInits;
    }

    private static LLVMExpressionNode[] createNullAddressLiteralNodes(int nrElements) {
        LLVMExpressionNode[] addressZeroInits = new LLVMExpressionNode[nrElements];
        for (int i = 0; i < nrElements; i++) {
            addressZeroInits[i] = LLVMNativePointerLiteralNodeGen.create(LLVMNativePointer.createNull());
        }
        return addressZeroInits;
    }

    private LLVMStatementNode createStore(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type, long size) {
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I1:
                    return LLVMI1StoreNodeGen.create(pointerNode, valueNode);
                case I8:
                    return LLVMI8StoreNodeGen.create(pointerNode, valueNode);
                case I16:
                    return LLVMI16StoreNodeGen.create(pointerNode, valueNode);
                case I32:
                    return LLVMI32StoreNodeGen.create(pointerNode, valueNode);
                case I64:
                    return LLVMI64StoreNodeGen.create(pointerNode, valueNode);
                case FLOAT:
                    return LLVMFloatStoreNodeGen.create(pointerNode, valueNode);
                case DOUBLE:
                    return LLVMDoubleStoreNodeGen.create(pointerNode, valueNode);
                case X86_FP80:
                    return LLVM80BitFloatStoreNodeGen.create(pointerNode, valueNode);
                default:
                    throw new AssertionError(type);
            }
        } else if (type instanceof VariableBitWidthType) {
            return LLVMIVarBitStoreNodeGen.create(pointerNode, valueNode);
        } else if (type instanceof StructureType || type instanceof ArrayType) {
            return LLVMStructStoreNodeGen.create(createMemMove(), pointerNode, valueNode, size);
        } else if (type instanceof PointerType || type instanceof FunctionType) {
            return LLVMPointerStoreNodeGen.create(pointerNode, valueNode);
        } else if (type instanceof VectorType) {
            VectorType vectorType = (VectorType) type;
            return LLVMStoreVectorNodeGen.create(pointerNode, valueNode, vectorType.getNumberOfElementsInt());
        } else {
            throw new AssertionError(type);
        }
    }

    public int getByteAlignment(Type type) {
        return type.getAlignment(dataLayout);
    }

    public long getByteSize(Type type) throws TypeOverflowException {
        return type.getSize(dataLayout);
    }

    public int getBytePadding(long offset, Type type) {
        return Type.getPadding(offset, type, dataLayout);
    }

    public long getIndexOffset(long index, AggregateType type) throws TypeOverflowException {
        return type.getOffsetOf(index, dataLayout);
    }

    @Override
    public LLVMControlFlowNode createLoop(RepeatingNode body, int[] successorIDs) {
        return LLVMLoopNode.create(body, successorIDs);
    }

    @Override
    public RepeatingNode createLoopDispatchNode(FrameSlot exceptionValueSlot, List<? extends LLVMStatementNode> bodyNodes, LLVMBasicBlockNode[] originalBodyNodes, int headerId,
                    int[] indexMapping, int[] successors, FrameSlot successorSlot) {
        return new LLVMLoopDispatchNode(exceptionValueSlot, bodyNodes.toArray(new LLVMBasicBlockNode[bodyNodes.size()]), originalBodyNodes, headerId, indexMapping, successors, successorSlot);
    }
}
