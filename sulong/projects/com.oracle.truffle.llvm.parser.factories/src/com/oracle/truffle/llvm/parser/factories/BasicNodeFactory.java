/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameDescriptor.Builder;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute;
import com.oracle.truffle.llvm.parser.model.attributes.Attribute.KnownAttribute;
import com.oracle.truffle.llvm.parser.model.attributes.AttributesGroup;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CastConstant;
import com.oracle.truffle.llvm.runtime.ArithmeticOperation;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.CompareOperator;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMGetStackFromFrameNodeGen;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.UnaryOperation;
import com.oracle.truffle.llvm.runtime.config.CommonLanguageOptions;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.except.LLVMParserException;
import com.oracle.truffle.llvm.runtime.floating.LLVMLongDoubleNode;
import com.oracle.truffle.llvm.runtime.floating.LLVMLongDoubleNode.LongDoubleKinds;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemMoveNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemSetNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemorySizedOpNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.LLVMStackAccess;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.UniquesRegion;
import com.oracle.truffle.llvm.runtime.memory.LLVMStackFactory.LLVMAllocaConstInstructionNodeGen;
import com.oracle.truffle.llvm.runtime.memory.LLVMStackFactory.LLVMAllocaInstructionNodeGen;
import com.oracle.truffle.llvm.runtime.memory.LLVMStackFactory.LLVMGetUniqueStackSpaceInstructionNodeGen;
import com.oracle.truffle.llvm.runtime.memory.LLVMUniquesRegionAllocNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMUniquesRegionAllocNodeGen;
import com.oracle.truffle.llvm.runtime.memory.VarargsAreaStackAllocationNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMControlFlowNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLoadNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMTypesGen;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMBrUnconditionalNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMCatchReturnNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMCleanupReturnNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMConditionalBranchNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMDispatchBasicBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMDispatchBasicBlockNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMFunctionRootNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMFunctionRootNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMIndirectBranchNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVM128BitFloatRetNodeGen;
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
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMTailReturnNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMVectorRetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMRetNodeFactory.LLVMVoidReturnNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMSwitchNode;
import com.oracle.truffle.llvm.runtime.nodes.control.LLVMWritePhisNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMArgNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMCatchSwitchNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMFunctionStartNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMInvokeNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMLandingpadNode;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMLandingpadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMResumeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMTypeIdForExceptionNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsics.LLVMSmaxOperator;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsics.LLVMSminOperator;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsics.LLVMUmaxOperator;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsics.LLVMUminOperator;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsics;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.c.LLVMCMathsIntrinsicsFactory.LLVMPowNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMTruffleGetArgCountNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.interop.LLVMTruffleGetArgNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMAssumeNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMByteSwap;
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
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMMemCopy;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMMemMove;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMMemSetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMNoOpNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMPrefetchNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMReturnAddressNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMStackRestoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMStackSaveNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMTrapNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin.ScalarBuiltinFactory;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin.TypedBuiltinFactory;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.LLVMBuiltin.VectorBuiltinFactory;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.aarch64.LLVMAArch64_NeonNodesFactory;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMArithmetic;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMArithmeticFactory.GCCArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMArithmeticFactory.LLVMArithmeticWithOverflowAndCarryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMArithmeticFactory.LLVMArithmeticWithOverflowNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMArithmeticFactory.LLVMSimpleArithmeticPrimitiveNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMVectorReduceFactory.LLVMVectorReduceAddNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMVectorReduceFactory.LLVMVectorReduceAndNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMVectorReduceFactory.LLVMVectorReduceMulNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMVectorReduceFactory.LLVMVectorReduceOrNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMVectorReduceFactory.LLVMVectorReduceSignedMaxNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMVectorReduceFactory.LLVMVectorReduceSignedMinNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMVectorReduceFactory.LLVMVectorReduceUnsignedMaxNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMVectorReduceFactory.LLVMVectorReduceUnsignedMinNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.arith.LLVMVectorReduceFactory.LLVMVectorReduceXorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountLeadingZeroesNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountSetBitsNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.bit.CountTrailingZeroesNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVACopyNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAEndNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAListNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va.LLVMVAStartNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_ComparisonNodeFactory.LLVMX86_CmpssNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_ConversionNodeFactory.LLVMX86_ConversionDoubleToIntNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_ConversionNodeFactory.LLVMX86_ConversionFloatToIntNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_ConversionNodeFactory.LLVMX86_MovmskpdNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_ConversionNodeFactory.LLVMX86_Pmovmskb128NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_MissingBuiltin;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_SSE_VectorMaxNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_SSE_VectorMaxsdNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_SSE_VectorMinNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_SSE_VectorMinsdNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_VectorCmpNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_VectorMaxNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_VectorMaxsdNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_VectorMinNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_VectorMinsdNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_VectorPackNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_VectorMathNodeFactory.LLVMX86_VectorSquareRootNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMMetaLiteralNode;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMDoubleLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMFloatLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI16LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI1LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI32LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI64LiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.literals.LLVMSimpleLiteralNodeFactory.LLVMI8LiteralNodeGen;
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
import com.oracle.truffle.llvm.runtime.nodes.memory.FreeGlobalsBlockNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMCompareExchangeNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMFenceExpressionNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMFenceNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMGetElementPtrNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMInsertValueNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMNativeVarargsAreaStackAllocationNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMStructByValueNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMVarArgCompoundAddressNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMVectorizedGetElementPtrNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.NativeMemSetNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.NativeProfiledMemMoveNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.ProtectReadOnlyGlobalsBlockNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.literal.LLVMArrayLiteralNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.literal.LLVMArrayLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.literal.LLVMI8ArrayLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.literal.LLVMStructArrayLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVM128BitFloatLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVM80BitFloatLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDoubleLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMFloatLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI1LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMIVarBitLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadDoubleVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadFloatVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI16VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI1VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI32VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI64VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadI8VectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMLoadVectorNodeFactory.LLVMLoadPointerVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMPointerLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMStructLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.rmw.LLVMI16RMWNodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.rmw.LLVMI1RMWNodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.rmw.LLVMI32RMWNodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.rmw.LLVMI64RMWNodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.rmw.LLVMI8RMWNodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM128BitFloatStoreNode.LLVM128BitFloatOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM128BitFloatStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNode.LLVM80BitFloatOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVM80BitFloatStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMDoubleStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMDoubleStoreNode.LLVMDoubleOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMDoubleStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMFloatStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMFloatStoreNode.LLVMFloatOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMFloatStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNode.LLVMI16OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI1StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI1StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNode.LLVMI32OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode.LLVMI64OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNode.LLVMI8OffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMIVarBitStoreNode.LLVMIVarBitOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMIVarBitStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMOffsetStoreNodeFactory.LLVMGenericOffsetStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNode.LLVMPointerOffsetStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMStoreVectorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMStructStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMArithmeticNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMFunnelShiftNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMIsFPClassNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMPointerMaskNodeGen;
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
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMUnaryNode;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMUnaryNodeFactory.LLVMDoubleUnaryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMUnaryNodeFactory.LLVMFP128UnaryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMUnaryNodeFactory.LLVMFP80UnaryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMUnaryNodeFactory.LLVMFloatUnaryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMVectorArithmeticNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.op.LLVMVectorUnaryNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMSelectNodeFactory.LLVM128BitFloatSelectNodeGen;
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
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMVectorSelectNodeGen.LLVMDoubleVectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMVectorSelectNodeGen.LLVMFloatVectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMVectorSelectNodeGen.LLVMI16VectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMVectorSelectNodeGen.LLVMI1VectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMVectorSelectNodeGen.LLVMI32VectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMVectorSelectNodeGen.LLVMI64VectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMVectorSelectNodeGen.LLVMI8VectorSelectNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNode;
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
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.ArrayType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;
import com.oracle.truffle.llvm.runtime.types.VariableBitWidthType;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.symbols.LocalVariableDebugInfo;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode.I64_SIZE_IN_BYTES;
import static com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode.I8_SIZE_IN_BYTES;

public class BasicNodeFactory implements NodeFactory {

    protected final LLVMLanguage language;
    protected DataLayout dataLayout;

    protected final CommonLanguageOptions engineOptions;

    public BasicNodeFactory(LLVMLanguage language, DataLayout dataLayout, CommonLanguageOptions engineOptions) {
        this.language = language;
        this.dataLayout = dataLayout;
        this.engineOptions = engineOptions;
    }

    @Override
    public DataLayout getDataLayout() {
        return dataLayout;
    }

    @Override
    public LLVMLanguage getLanguage() {
        return language;
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
    public LLVMExpressionNode createLoad(Type resolvedResultType, LLVMExpressionNode loadTarget) {
        return CommonNodeFactory.createLoad(resolvedResultType, loadTarget);
    }

    @Override
    public LLVMStatementNode createStore(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) {
        try {
            return createMemoryStore(pointerNode, valueNode, type);
        } catch (TypeOverflowException e) {
            return Type.handleOverflowStatement(e);
        }
    }

    private LLVMStoreNode createMemoryStore(LLVMExpressionNode pointerNode, LLVMExpressionNode valueNode, Type type) throws TypeOverflowException {
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
                case F128:
                    return LLVM128BitFloatStoreNodeGen.create(pointerNode, valueNode);
                default:
                    throw new AssertionError(type);
            }
        } else if (type instanceof VariableBitWidthType) {
            return LLVMIVarBitStoreNodeGen.create(pointerNode, valueNode);
        } else if (type instanceof StructureType || type instanceof ArrayType) {
            return LLVMStructStoreNodeGen.create(createMemMove(), pointerNode, valueNode, getByteSize(type));
        } else if (type instanceof PointerType || type instanceof FunctionType) {
            return LLVMPointerStoreNodeGen.create(pointerNode, valueNode);
        } else if (type instanceof VectorType) {
            VectorType vectorType = (VectorType) type;
            return LLVMStoreVectorNodeGen.create(pointerNode, valueNode, vectorType.getNumberOfElementsInt());
        } else {
            throw new AssertionError(type);
        }
    }

    @Override
    public LLVMOffsetStoreNode createOffsetMemoryStore(Type resolvedType, LLVMExpressionNode value) throws TypeOverflowException {
        if (resolvedType instanceof PrimitiveType) {
            switch (((PrimitiveType) resolvedType).getPrimitiveKind()) {
                case I8:
                    return LLVMI8OffsetStoreNode.create(value);
                case I16:
                    return LLVMI16OffsetStoreNode.create(value);
                case I32:
                    return LLVMI32OffsetStoreNode.create(value);
                case I64:
                    return LLVMI64OffsetStoreNode.create(value);
                case FLOAT:
                    return LLVMFloatOffsetStoreNode.create(value);
                case DOUBLE:
                    return LLVMDoubleOffsetStoreNode.create(value);
                case X86_FP80:
                    return LLVM80BitFloatOffsetStoreNode.create(value);
                case F128:
                    return LLVM128BitFloatOffsetStoreNode.create(value);
                default:
                    throw new AssertionError(resolvedType);
            }
        } else if (resolvedType instanceof PointerType || resolvedType instanceof FunctionType) {
            return LLVMPointerOffsetStoreNode.create(value);
        } else if (resolvedType instanceof VariableBitWidthType) {
            return LLVMIVarBitOffsetStoreNode.create(value);
        }
        return LLVMGenericOffsetStoreNodeGen.create(createMemoryStore(null, null, resolvedType), null, null, value);
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
    public LLVMExpressionNode createFenceExpression() {
        return LLVMFenceExpressionNodeGen.create();
    }

    @Override
    public LLVMExpressionNode createVectorLiteralNode(LLVMExpressionNode[] values, Type type) {
        Type llvmType = ((VectorType) type).getElementType();
        if (llvmType instanceof PrimitiveType) {
            switch (((PrimitiveType) llvmType).getPrimitiveKind()) {
                case I1:
                    return LLVMI1VectorLiteralNodeGen.create(values);
                case I8:
                    return LLVMI8VectorLiteralNodeGen.create(values);
                case I16:
                    return LLVMI16VectorLiteralNodeGen.create(values);
                case I32:
                    return LLVMI32VectorLiteralNodeGen.create(values);
                case I64:
                    return LLVMI64VectorLiteralNodeGen.create(values);
                case FLOAT:
                    return LLVMFloatVectorLiteralNodeGen.create(values);
                case DOUBLE:
                    return LLVMDoubleVectorLiteralNodeGen.create(values);
                default:
                    throw new AssertionError();
            }
        } else if (llvmType instanceof PointerType || llvmType instanceof FunctionType) {
            return LLVMPointerVectorLiteralNodeGen.create(values);
        } else {
            throw new AssertionError(llvmType + " not yet supported");
        }
    }

    @Override
    public LLVMControlFlowNode createRetVoid() {
        return LLVMVoidReturnNodeGen.create();
    }

    @Override
    public LLVMControlFlowNode createRetTail(LLVMExpressionNode tailCall) {
        return LLVMTailReturnNodeGen.create(tailCall);
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
        } else if (type instanceof StructureType || type instanceof ArrayType) {
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
                case F128:
                    return LLVM128BitFloatRetNodeGen.create(retValue);
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
        return LLVMArgNodeGen.create(argIndex);
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
                    return LLVM80BitFloatLoadNodeGen.create(targetAddress);
                case F128:
                    return LLVM128BitFloatLoadNodeGen.create(targetAddress);
                default:
                    throw new AssertionError(type);
            }
        } else if (type instanceof VariableBitWidthType) {
            return LLVMIVarBitLoadNodeGen.create(targetAddress, ((VariableBitWidthType) type).getBitSizeInt());
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
        } else if (type instanceof PointerType) {
            return LLVMPointerLoadNodeGen.create(targetAddress);
        } else if (type instanceof StructureType || type instanceof ArrayType) {
            // basically no-op
            return LLVMStructLoadNodeGen.create(targetAddress);
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
                case F128:
                    return LLVM128BitFloatSelectNodeGen.create(condition, trueValue, falseValue);
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
                return LLVMArrayLiteralNodeGen.create(arrayValues, elementSize, createMemoryStore(null, null, elementType), arrayGetStackSpace);
            } else if (elementType instanceof ArrayType || elementType instanceof StructureType) {
                return LLVMStructArrayLiteralNodeGen.create(arrayValues, createMemMove(), elementSize, arrayGetStackSpace);
            }
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
        throw new AssertionError(elementType);
    }

    protected LLVMExpressionNode createAlloca(long byteSize, int alignment) {
        return LLVMAllocaConstInstructionNodeGen.create(byteSize, alignment);
    }

    @Override
    public LLVMExpressionNode createPrimitiveArrayLiteral(Object arrayValues, ArrayType arrayType, GetStackSpaceFactory arrayGetStackSpaceFactory) {
        assert arrayType.getNumberOfElements() == Array.getLength(arrayValues);
        LLVMExpressionNode arrayGetStackSpace = arrayGetStackSpaceFactory.createGetStackSpace(this, arrayType);
        Type elementType = arrayType.getElementType();
        try {
            long elementSize = getByteSize(elementType);
            if (elementSize == 0) {
                throw new TypeOverflowException(elementType + " has size of 0!");
            }
            if (elementType instanceof PrimitiveType) {
                PrimitiveType primitiveType = (PrimitiveType) elementType;
                if (primitiveType.getPrimitiveKind() == PrimitiveKind.I8 && arrayValues instanceof byte[] && elementSize == 1) {
                    return LLVMI8ArrayLiteralNodeGen.create((byte[]) arrayValues, arrayGetStackSpace);
                }
            }
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
        throw new AssertionError(elementType);
    }

    @Override
    public LLVMExpressionNode createAlloca(Type type) {
        try {
            return createAlloca(getByteSize(type), getByteAlignment(type));
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
    }

    protected boolean isManagedVAListType(Type type) {
        return language.getActiveConfiguration().getCapability(PlatformCapability.class).isManagedVAListType(type);
    }

    @Override
    public LLVMExpressionNode createAlloca(Type type, int alignment) {
        if (isManagedVAListType(type)) {
            // Create a factory node for a managed va_list instead of the stack allocation node.
            return LLVMVAListNodeGen.create(type);
        } else {
            try {
                return LLVMAllocaConstInstructionNodeGen.create(getByteSize(type), alignment);
            } catch (TypeOverflowException e) {
                return Type.handleOverflowExpression(e);
            }
        }
    }

    @Override
    public LLVMExpressionNode createGetUniqueStackSpace(Type type, UniquesRegion uniquesRegion) {
        try {
            long slotOffset = uniquesRegion.addSlot(getByteSize(type), getByteAlignment(type));
            return LLVMGetUniqueStackSpaceInstructionNodeGen.create(slotOffset);
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
    }

    @Override
    public LLVMExpressionNode createAllocaArray(Type elementType, LLVMExpressionNode numElements, int alignment) {
        try {
            long byteSize = getByteSize(elementType);
            return LLVMAllocaInstructionNodeGen.create(byteSize, alignment, numElements);
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
                    store = LLVMI1StoreNode.create();
                    break;
                case I8:
                    store = LLVMI8StoreNode.create();
                    break;
                case I16:
                    store = LLVMI16StoreNode.create();
                    break;
                case I32:
                    store = LLVMI32StoreNode.create();
                    break;
                case I64:
                    store = LLVMI64StoreNode.create();
                    break;
                case FLOAT:
                    store = LLVMFloatStoreNode.create();
                    break;
                case DOUBLE:
                    store = LLVMDoubleStoreNode.create();
                    break;
                case X86_FP80:
                    store = LLVM80BitFloatStoreNode.create();
                    break;
                case F128:
                    store = LLVM128BitFloatStoreNodeGen.create();
                    break;
                default:
                    throw new AssertionError(llvmType + " is not supported for insertvalue");
            }
        } else if (llvmType instanceof VariableBitWidthType) {
            store = LLVMIVarBitStoreNodeGen.create();
        } else if (llvmType instanceof VectorType) {
            store = LLVMStoreVectorNodeGen.create(null, null, ((VectorType) llvmType).getNumberOfElementsInt());
        } else if (llvmType instanceof PointerType) {
            store = LLVMPointerStoreNode.create();
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
                    currentOffset = Type.addUnsignedExact(currentOffset, dataLayout.getBytePadding(currentOffset, resolvedType));
                }
                offsets[i] = currentOffset;
                long byteSize = getByteSize(resolvedType);
                nodes[i] = createMemoryStore(null, null, resolvedType);
                currentOffset = Type.addUnsignedExact(currentOffset, byteSize);
            }
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
        return StructLiteralNodeGen.create(offsets, nodes, constants, getStackSpace);
    }

    @Override
    public RootNode createFunction(int exceptionValueSlot, LLVMBasicBlockNode[] allFunctionNodes, UniquesRegion uniquesRegion, LLVMStatementNode[] copyArgumentsToFrame,
                    FrameDescriptor frameDescriptor, LocalVariableDebugInfo debugInfo, String name, String originalName, int argumentCount, Source bcSource,
                    LLVMSourceLocation location, LLVMFunction rootFunction) {
        LLVMUniquesRegionAllocNode uniquesRegionAllocNode = uniquesRegion.isEmpty() ? null
                        : LLVMUniquesRegionAllocNodeGen.create(createAlloca(uniquesRegion.getSize(), uniquesRegion.getAlignment()));
        LLVMDispatchBasicBlockNode body = LLVMDispatchBasicBlockNodeGen.create(exceptionValueSlot, allFunctionNodes, debugInfo, engineOptions.osrMode);
        body.setSourceLocation(LLVMSourceLocation.orDefault(location));
        LLVMStackAccess stackAccess = createStackAccess();
        LLVMFunctionRootNode functionRoot = LLVMFunctionRootNodeGen.create(uniquesRegionAllocNode, stackAccess, copyArgumentsToFrame, body, frameDescriptor);
        functionRoot.setSourceLocation(LLVMSourceLocation.orDefault(location));
        return new LLVMFunctionStartNode(language, stackAccess, functionRoot, frameDescriptor, name, argumentCount, originalName, bcSource, location, dataLayout, rootFunction);
    }

    @Override
    public LLVMExpressionNode createInlineAssemblerExpression(String asmExpression, String asmFlags, LLVMExpressionNode[] args, Type.TypeArrayBuilder argTypes,
                    Type retType) {
        return language.getCapability(PlatformCapability.class).getInlineAssemblyParser().getInlineAssemblerExpression(this, asmExpression, asmFlags, args, argTypes, retType);
    }

    @Override
    public LLVMExpressionNode createGetStackFromFrame() {
        return LLVMGetStackFromFrameNodeGen.create();
    }

    @Override
    public LLVMControlFlowNode createFunctionInvoke(LLVMWriteNode writeResult, LLVMExpressionNode functionNode, LLVMExpressionNode[] argNodes, FunctionType type,
                    int normalIndex, int unwindIndex, LLVMStatementNode normalPhiWriteNodes, LLVMStatementNode unwindPhiWriteNodes) {
        return LLVMInvokeNode.create(type, writeResult, functionNode, argNodes, normalIndex, unwindIndex, normalPhiWriteNodes, unwindPhiWriteNodes);
    }

    @Override
    public LLVMExpressionNode createLandingPad(LLVMExpressionNode allocateLandingPadValue, int exceptionValueSlot, boolean cleanup, long[] clauseKinds, LLVMExpressionNode[] entries,
                    LLVMExpressionNode getStack) {

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

    @Override
    public LLVMControlFlowNode createCatchSwitch(int exceptionSlot, int[] targetBlocks, LLVMExpressionNode getStack, LLVMStatementNode[] phiWrites) {
        return LLVMCatchSwitchNode.create(exceptionSlot, targetBlocks, getStack, phiWrites);
    }

    @Override
    public LLVMControlFlowNode createCatchSwitch(int exceptionSlot, int[] targetBlocks, int unwindBlock, LLVMExpressionNode getStack, LLVMStatementNode[] phiWrites) {
        return LLVMCatchSwitchNode.create(exceptionSlot, targetBlocks, unwindBlock, getStack, phiWrites);
    }

    @Override
    public LLVMControlFlowNode createCatchReturn(int unconditionalIndex, LLVMExpressionNode getStack, LLVMStatementNode phiWrites) {
        return LLVMCatchReturnNode.create(unconditionalIndex, getStack, phiWrites);
    }

    @Override
    public LLVMControlFlowNode createCleanupReturn(int unconditionalIndex, LLVMExpressionNode getStack, LLVMStatementNode phiWrites) {
        return LLVMCleanupReturnNode.create(unconditionalIndex, getStack, phiWrites);
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
    public LLVMControlFlowNode createResumeInstruction(int exceptionValueSlot) {
        return LLVMResumeNodeGen.create(exceptionValueSlot);
    }

    @Override
    public LLVMExpressionNode createCompareExchangeInstruction(AggregateType returnType, Type elementType, LLVMExpressionNode ptrNode, LLVMExpressionNode cmpNode,
                    LLVMExpressionNode newNode) {
        try {
            return LLVMCompareExchangeNode.create(createAlloca(returnType), returnType, dataLayout, ptrNode, cmpNode, newNode);
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }
    }

    @Override
    public LLVMExpressionNode createLLVMBuiltin(Symbol target, LLVMExpressionNode[] args, Type.TypeArrayBuilder argsTypes, int callerArgumentCount) {
        Symbol actualTarget = target;
        if (actualTarget instanceof CastConstant && ((CastConstant) actualTarget).getValue() instanceof FunctionDeclaration) {
            /*
             * This branch solves the following scenario, in which a builtin is bitcast before its
             * invocation:
             *
             * %1 = call i32 bitcast (i32 (...)* @polyglot_get_arg_count to i32 ()*)() #2, !dbg !18
             *
             * Otherwise the builtin would be handled as a normal function.
             */
            actualTarget = ((CastConstant) actualTarget).getValue();
        }
        if (actualTarget instanceof FunctionDeclaration) {
            FunctionDeclaration declaration = (FunctionDeclaration) actualTarget;
            String name = declaration.getName();
            /*
             * These "llvm." builtins are *not* function intrinsics. Builtins replace statements
             * that look like function calls but are actually LLVM intrinsics. An example is
             * llvm.stackpointer. Also, it is not possible to retrieve the functionpointer of such
             * pseudo-call-targets.
             *
             * These builtins shall not be used for regular function intrinsification!
             */
            if (name.startsWith("llvm.experimental.constrained.")) {
                return getConstrainedFPBuiltin(declaration, args);
            } else if (name.startsWith("llvm.")) {
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
                LLVMIntrinsicProvider intrinsicProvider = language.getCapability(LLVMIntrinsicProvider.class);
                if (intrinsicProvider.isIntrinsified(name)) {
                    /*
                     * Note that getRawTypeArray has a side effect, so we can't rely on the implicit
                     * null return of generateIntrinsicNode if the function is not intrinsified.
                     */
                    return intrinsicProvider.generateIntrinsicNode(name, args, Type.getRawTypeArray(argsTypes), this);
                }
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
        return LLVMMemCopy.createIntrinsic(args, createMemMove(), this);
    }

    private LLVMExpressionNode createMemmoveIntrinsic(LLVMExpressionNode[] args) {
        return LLVMMemMove.LLVMMemMoveI64.createIntrinsic(args, createMemMove(), this);
    }

    static final class TypeSuffix {
        final String suffix;
        final String length;

        TypeSuffix(String suffix, String length) {
            this.suffix = suffix;
            this.length = length;
        }
    }

    // matches the type suffix of an LLVM intrinsic function, including the dot
    private static final Pattern INTRINSIC_TYPE_SUFFIX_PATTERN = Pattern.compile("\\S+(?<suffix>\\.(?:[vp](?<length>\\d+))?([if]\\d+)?)$");

    private static TypeSuffix getTypeSuffix(String intrinsicName) {
        assert intrinsicName != null;
        final Matcher typeSuffixMatcher = INTRINSIC_TYPE_SUFFIX_PATTERN.matcher(intrinsicName);
        if (typeSuffixMatcher.matches()) {
            return new TypeSuffix(typeSuffixMatcher.group("suffix"), typeSuffixMatcher.group("length"));
        }
        return null;
    }

    // matches the type suffix of an LLVM intrinsic function, including the dot
    private static final Pattern VECTOR_INTRINSIC_PATTERN = Pattern.compile("^llvm(\\.experimental)?\\.vector\\.(?<op>[a-z.]+)\\.v(?<len>\\d+)[if]\\d+$");

    private static LLVMExpressionNode matchVectorOp(String intrinsicName, LLVMExpressionNode[] args) {
        Matcher matcher = VECTOR_INTRINSIC_PATTERN.matcher(intrinsicName);
        if (!matcher.matches()) {
            return null;
        }

        int len = Integer.parseInt(matcher.group("len"));
        switch (matcher.group("op")) {
            case "reduce.add":
                return LLVMVectorReduceAddNodeGen.create(args[1], len);
            case "reduce.mul":
                return LLVMVectorReduceMulNodeGen.create(args[1], len);
            case "reduce.and":
                return LLVMVectorReduceAndNodeGen.create(args[1], len);
            case "reduce.or":
                return LLVMVectorReduceOrNodeGen.create(args[1], len);
            case "reduce.xor":
                return LLVMVectorReduceXorNodeGen.create(args[1], len);
            case "reduce.umax":
                return LLVMVectorReduceUnsignedMaxNodeGen.create(args[1], len);
            case "reduce.smax":
                return LLVMVectorReduceSignedMaxNodeGen.create(args[1], len);
            case "reduce.umin":
                return LLVMVectorReduceUnsignedMinNodeGen.create(args[1], len);
            case "reduce.smin":
                return LLVMVectorReduceSignedMinNodeGen.create(args[1], len);
            default:
                return null;
        }
    }

    private static PrimitiveKind parsePrimitiveKind(String type) {
        switch (type) {
            case "i1":
                return PrimitiveKind.I1;
            case "i8":
                return PrimitiveKind.I8;
            case "i16":
                return PrimitiveKind.I16;
            case "i32":
                return PrimitiveKind.I32;
            case "i64":
                return PrimitiveKind.I64;
            case "f32":
                return PrimitiveKind.FLOAT;
            case "f64":
                return PrimitiveKind.DOUBLE;
            case "f80":
                return PrimitiveKind.X86_FP80;
            case "f128":
                return PrimitiveKind.F128;
            default:
                return null;
        }
    }

    private static final Pattern TYPED_INTRINSIC_PATTERN = Pattern.compile("^llvm\\.(?<op>[a-z0-9.]+)\\.(?<type>(v(?<vlen>[0-9]+))?(?<ptype>[if][0-9]+))");

    private static TypedBuiltinFactory getBuiltinFactory(String op, PrimitiveKind kind) {
        switch (op) {
            case "bswap":
                return LLVMByteSwap.getFactory(kind);
            case "ctpop":
                return CountSetBitsNode.getFactory(kind);
            case "ctlz":
                return CountLeadingZeroesNode.getFactory(kind);
            case "cttz":
                return CountTrailingZeroesNode.getFactory(kind);
            case "sqrt":
                return LLVMCMathsIntrinsics.getSqrtFactory(kind);
            case "log":
                return LLVMCMathsIntrinsics.getLogFactory(kind);
            case "log2":
                return LLVMCMathsIntrinsics.getLog2Factory(kind);
            case "log10":
                return LLVMCMathsIntrinsics.getLog10Factory(kind);
            case "rint":
                return LLVMCMathsIntrinsics.getRintFactory(kind);
            case "ceil":
                return LLVMCMathsIntrinsics.getCeilFactory(kind);
            case "floor":
                return LLVMCMathsIntrinsics.getFloorFactory(kind);
            case "exp":
                return LLVMCMathsIntrinsics.getExpFactory(kind);
            case "exp2":
                return LLVMCMathsIntrinsics.getExp2Factory(kind);
            case "sin":
                return LLVMCMathsIntrinsics.getSinFactory(kind);
            case "cos":
                return LLVMCMathsIntrinsics.getCosFactory(kind);
            case "round":
                return LLVMCMathsIntrinsics.getRoundFactory(kind);
            case "maxnum":
                return LLVMCMathsIntrinsics.getMaxnumFactory(kind);
            case "minnum":
                return LLVMCMathsIntrinsics.getMinnumFactory(kind);
            case "copysign":
                return LLVMCMathsIntrinsics.getCopySignFactory(kind);
            case "abs":
                return LLVMCMathsIntrinsics.getAbsFactory(kind);
            case "fabs":
                return LLVMCMathsIntrinsics.getFAbsFactory(kind);
            case "fshl":
                return LLVMFunnelShiftNode.getFshlFactory(kind);
            case "fshr":
                return LLVMFunnelShiftNode.getFshrFactory(kind);
            case "is.fpclass":
                return LLVMIsFPClassNode.getIsFPClassFactory(kind);
            default:
                return null;
        }
    }

    protected LLVMExpressionNode getTypedLLVMBuiltin(String intrinsicName, LLVMExpressionNode[] args) {
        Matcher matcher = TYPED_INTRINSIC_PATTERN.matcher(intrinsicName);
        if (!matcher.matches()) {
            return null;
        }

        PrimitiveKind type = parsePrimitiveKind(matcher.group("ptype"));
        TypedBuiltinFactory factory = getBuiltinFactory(matcher.group("op"), type);
        if (factory == null) {
            // builtin not found or not supported for this type
            return null;
        }

        String vlen = matcher.group("vlen");
        if (vlen == null) {
            ScalarBuiltinFactory scalar = factory.getScalar();
            if (scalar != null) {
                return scalar.create(args);
            }
        } else {
            VectorBuiltinFactory vector = factory.getVector();
            if (vector != null) {
                int vectorSize = vlen == null ? 1 : Integer.parseInt(vlen);
                return vector.create(vectorSize, args);
            }
        }

        // vector not supported
        return null;
    }

    protected LLVMExpressionNode getLLVMBuiltin(FunctionDeclaration declaration, LLVMExpressionNode[] args, int callerArgumentCount) {

        String intrinsicName = declaration.getName();
        try {
            switch (intrinsicName) {
                case "llvm.ptrmask.p0.i64":
                    return LLVMPointerMaskNodeGen.create(args[1], args[2]);
                case "llvm.memset.p0.i32":
                case "llvm.memset.p0i8.i32":
                case "llvm.memset.p0.i64":
                case "llvm.memset.p0i8.i64":
                    return createMemsetIntrinsic(args);
                case "llvm.assume":
                    return LLVMAssumeNodeGen.create(args[1]);
                case "llvm.clear_cache": // STUB
                case "llvm.donothing":
                    return LLVMNoOpNodeGen.create();
                case "llvm.prefetch":
                    return LLVMPrefetchNodeGen.create(args[1], args[2], args[3], args[4]);
                case "llvm.memcpy.p0.p0.i64":
                case "llvm.memcpy.p0i8.p0i8.i64":
                case "llvm.memcpy.p0.p0.i32":
                case "llvm.memcpy.p0i8.p0i8.i32":
                    return createMemcpyIntrinsic(args);
                case "llvm.trap":
                    return LLVMTrapNodeGen.create();
                case "llvm.memmove.p0.p0.i64":
                case "llvm.memmove.p0i8.p0i8.i64":
                    return createMemmoveIntrinsic(args);
                case "llvm.pow.f32":
                case "llvm.pow.f64":
                case "llvm.powi.f32":
                case "llvm.powi.f64":
                case "llvm.powi.f32.i32":
                case "llvm.powi.f64.i16":
                case "llvm.powi.f64.i32":
                    return LLVMPowNodeGen.create(args[1], args[2]);
                case "llvm.pow.f80":
                case "llvm.powi.f80":
                case "llvm.powi.f80.i32":
                    return LLVMLongDoubleNode.createPowNode(args[1], args[2], LongDoubleKinds.FP80);
                case "llvm.pow.f128":
                case "llvm.powi.f128":
                case "llvm.powi.f128.i32":
                    return LLVMLongDoubleNode.createPowNode(args[1], args[2], LongDoubleKinds.FP128);
                case "llvm.fmuladd.f32":
                    LLVMExpressionNode mulNodeFloat = createArithmeticOp(ArithmeticOperation.MUL, PrimitiveType.FLOAT, args[1], args[2]);
                    return createArithmeticOp(ArithmeticOperation.ADD, PrimitiveType.FLOAT, mulNodeFloat, args[3]);
                case "llvm.fmuladd.f64":
                    LLVMExpressionNode mulNodeDouble = createArithmeticOp(ArithmeticOperation.MUL, PrimitiveType.DOUBLE, args[1], args[2]);
                    return createArithmeticOp(ArithmeticOperation.ADD, PrimitiveType.DOUBLE, mulNodeDouble, args[3]);
                case "llvm.fmuladd.f80":
                    LLVMExpressionNode mulNodeF80 = createArithmeticOp(ArithmeticOperation.MUL, PrimitiveType.X86_FP80, args[1], args[2]);
                    return createArithmeticOp(ArithmeticOperation.ADD, PrimitiveType.X86_FP80, mulNodeF80, args[3]);
                case "llvm.fmuladd.f128":
                    LLVMExpressionNode mulNodeF128 = createArithmeticOp(ArithmeticOperation.MUL, PrimitiveType.F128, args[1], args[2]);
                    return createArithmeticOp(ArithmeticOperation.ADD, PrimitiveType.F128, mulNodeF128, args[3]);
                case "llvm.fmuladd.v2f64":
                    VectorType typeVec2f64 = new VectorType(PrimitiveType.DOUBLE, 2);
                    LLVMExpressionNode vecMulNodeDouble = createArithmeticOp(ArithmeticOperation.MUL, typeVec2f64, args[1], args[2]);
                    return createArithmeticOp(ArithmeticOperation.ADD, typeVec2f64, vecMulNodeDouble, args[3]);
                case "llvm.returnaddress":
                    return LLVMReturnAddressNodeGen.create(args[1]);
                case "llvm.lifetime.start.p0":
                case "llvm.lifetime.start.p0i8":
                case "llvm.lifetime.start":
                    return LLVMLifetimeStartNodeGen.create(args[1], args[2]);
                case "llvm.lifetime.end.p0":
                case "llvm.lifetime.end.p0i8":
                case "llvm.lifetime.end":
                    return LLVMLifetimeEndNodeGen.create(args[1], args[2]);
                case "llvm.invariant.start":
                case "llvm.invariant.start.p0":
                case "llvm.invariant.start.p0i8":
                    return LLVMInvariantStartNodeGen.create(args[1], args[2]);
                case "llvm.invariant.end":
                case "llvm.invariant.end.p0":
                case "llvm.invariant.end.p0i8":
                    return LLVMInvariantEndNodeGen.create(args[1], args[2]);
                case "llvm.stacksave":
                case "llvm.stacksave.p0":
                    return LLVMStackSaveNodeGen.create();
                case "llvm.stackrestore":
                case "llvm.stackrestore.p0":
                    return LLVMStackRestoreNodeGen.create(args[1]);
                case "llvm.frameaddress":
                case "llvm.frameaddress.p0":
                case "llvm.frameaddress.p0i8":
                    return LLVMFrameAddressNodeGen.create(args[1]);
                case "llvm.threadlocal.address.p0":
                    /*
                     * We already resolve TLS variables directly, when doing the symbol table
                     * lookup. So for us, this is a noop.
                     */
                    return args[1];
                case "llvm.va_start":
                    return LLVMVAStartNodeGen.create(callerArgumentCount, args[1]);
                case "llvm.va_end":
                    return LLVMVAEndNodeGen.create(args[1]);
                case "llvm.va_copy":
                    return LLVMVACopyNodeGen.create(args[1], args[2], callerArgumentCount);
                case "llvm.eh.sjlj.longjmp":
                case "llvm.eh.sjlj.setjmp":
                    return LLVMUnsupportedInstructionNode.createExpression(UnsupportedReason.SET_JMP_LONG_JMP);
                case "llvm.dbg.assign":
                    // https://llvm.org/docs/AssignmentTracking.html
                    // not used by Sulong
                    return LLVMNoOpNodeGen.create();
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
                case "llvm.objectsize.i64.p0":
                case "llvm.objectsize.i64.p0i8":
                case "llvm.objectsize.i64":
                    return LLVMI64ObjectSizeNodeGen.create(args[1], args[2]);
                case "llvm.uadd.sat.i8":
                case "llvm.uadd.sat.i16":
                case "llvm.uadd.sat.i32":
                case "llvm.uadd.sat.i64":
                    return LLVMSimpleArithmeticPrimitiveNodeGen.create(LLVMArithmetic.UNSIGNED_ADD_SAT, args[1], args[2]);
                case "llvm.usub.sat.i8":
                case "llvm.usub.sat.i16":
                case "llvm.usub.sat.i32":
                case "llvm.usub.sat.i64":
                    return LLVMSimpleArithmeticPrimitiveNodeGen.create(LLVMArithmetic.UNSIGNED_SUB_SAT, args[1], args[2]);
                case "llvm.sadd.sat.i8":
                case "llvm.sadd.sat.i16":
                case "llvm.sadd.sat.i32":
                case "llvm.sadd.sat.i64":
                    return LLVMSimpleArithmeticPrimitiveNodeGen.create(LLVMArithmetic.SIGNED_ADD_SAT, args[1], args[2]);
                case "llvm.ssub.sat.i8":
                case "llvm.ssub.sat.i16":
                case "llvm.ssub.sat.i32":
                case "llvm.ssub.sat.i64":
                    return LLVMSimpleArithmeticPrimitiveNodeGen.create(LLVMArithmetic.SIGNED_SUB_SAT, args[1], args[2]);
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
                case "llvm.x86.sse2.min.sd":
                    return LLVMX86_VectorMinsdNodeGen.create(args[1], args[2]);
                case "llvm.x86.sse.max.ps":
                    return LLVMX86_SSE_VectorMaxNodeGen.create(args[1], args[2]);
                case "llvm.x86.sse.max.ss":
                    return LLVMX86_SSE_VectorMaxsdNodeGen.create(args[1], args[2]);
                case "llvm.x86.sse.min.ps":
                    return LLVMX86_SSE_VectorMinNodeGen.create(args[1], args[2]);
                case "llvm.x86.sse.min.ss":
                    return LLVMX86_SSE_VectorMinsdNodeGen.create(args[1], args[2]);
                case "llvm.x86.sse2.cmp.sd":
                    return LLVMX86_VectorCmpNodeGen.create(args[1], args[2], args[3]);
                case "llvm.x86.sse2.packssdw.128":
                case "llvm.x86.sse2.packsswb.128":
                    return LLVMX86_VectorPackNodeGen.create(args[1], args[2]);
                case "llvm.x86.sse2.pmovmskb.128":
                    return LLVMX86_Pmovmskb128NodeGen.create(args[1]);
                case "llvm.x86.sse2.movmsk.pd":
                    return LLVMX86_MovmskpdNodeGen.create(args[1]);
                default:
                    break;
            }
        } catch (TypeOverflowException e) {
            return Type.handleOverflowExpression(e);
        }

        LLVMExpressionNode typedIntrinsic = getTypedLLVMBuiltin(intrinsicName, args);
        if (typedIntrinsic != null) {
            return typedIntrinsic;
        }

        LLVMExpressionNode vectorIntrinsic = matchVectorOp(intrinsicName, args);
        if (vectorIntrinsic != null) {
            return vectorIntrinsic;
        }

        if (intrinsicName.startsWith("llvm.aarch64.neon")) {
            String op = intrinsicName.substring("llvm.aarch64.neon.".length());
            switch (op) {
                case "ld1x2.v16i8.p0":
                case "ld1x2.v16i8.p0i8":
                    return LLVMAArch64_NeonNodesFactory.LLVMAArch64_Ld1x2NodeGen.create(2 * I64_SIZE_IN_BYTES, args[1], args[2]);
                case "ld2.v16i8.p0":
                case "ld2.v16i8.p0v16i8":
                    return LLVMAArch64_NeonNodesFactory.LLVMAArch64_Ld2NodeGen.create(2 * I64_SIZE_IN_BYTES, I8_SIZE_IN_BYTES, args[1], args[2]);
                case "tbl1.v16i8":
                    return LLVMAArch64_NeonNodesFactory.LLVMAArch64_Tbl1NodeGen.create(2 * I64_SIZE_IN_BYTES, args[1], args[2]);
                case "tbl2.v16i8":
                    return LLVMAArch64_NeonNodesFactory.LLVMAArch64_Tbl2NodeGen.create(2 * I64_SIZE_IN_BYTES, args[1], args[2], args[3]);
                case "umaxv.i32.v16i8":
                    return LLVMAArch64_NeonNodesFactory.LLVMAArch64_UmaxvNodeGen.create(2 * I64_SIZE_IN_BYTES, args[1]);
                case "uqsub.v16i8":
                    return LLVMAArch64_NeonNodesFactory.LLVMAArch64_UqsubNodeGen.create(2 * I64_SIZE_IN_BYTES, args[1], args[2]);
            }
        }

        // strip the type suffix for intrinsics that are supported for more than one data type. If
        // we do not implement the corresponding data type the node will just report a missing
        // specialization at run-time
        TypeSuffix typeSuffix = getTypeSuffix(intrinsicName);
        if (typeSuffix != null) {
            intrinsicName = intrinsicName.substring(0, intrinsicName.length() - typeSuffix.suffix.length());
        }

        if ("llvm.prefetch".equals(intrinsicName)) {
            return LLVMPrefetchNodeGen.create(args[1], args[2], args[3], args[4]);
        }

        if ("llvm.is.constant".equals(intrinsicName)) {
            return LLVMIsConstantNodeGen.create(args[1]);
        }

        if ("llvm.umax".equals(intrinsicName) && typeSuffix != null) {
            try {
                if (typeSuffix.length != null) {
                    int vectorLength = Integer.parseInt(typeSuffix.length);
                    return LLVMCMathsIntrinsicsFactory.LLVMVectorMinMaxNodeGen.create(args[1], args[2], vectorLength, LLVMUmaxOperator.INSTANCE);
                } else {
                    return LLVMCMathsIntrinsicsFactory.LLVMScalarMinMaxNodeGen.create(args[1], args[2], LLVMUmaxOperator.INSTANCE);
                }
            } catch (NumberFormatException e) {
                // fall through
            }
        }

        if ("llvm.umin".equals(intrinsicName) && typeSuffix != null) {
            try {
                if (typeSuffix.length != null) {
                    int vectorLength = Integer.parseInt(typeSuffix.length);
                    return LLVMCMathsIntrinsicsFactory.LLVMVectorMinMaxNodeGen.create(args[1], args[2], vectorLength, LLVMUminOperator.INSTANCE);
                } else {
                    return LLVMCMathsIntrinsicsFactory.LLVMScalarMinMaxNodeGen.create(args[1], args[2], LLVMUminOperator.INSTANCE);
                }
            } catch (NumberFormatException e) {
                // fall through
            }
        }

        if ("llvm.smax".equals(intrinsicName) && typeSuffix != null) {
            try {
                if (typeSuffix.length != null) {
                    int vectorLength = Integer.parseInt(typeSuffix.length);
                    return LLVMCMathsIntrinsicsFactory.LLVMVectorMinMaxNodeGen.create(args[1], args[2], vectorLength, LLVMSmaxOperator.INSTANCE);
                } else {
                    return LLVMCMathsIntrinsicsFactory.LLVMScalarMinMaxNodeGen.create(args[1], args[2], LLVMSmaxOperator.INSTANCE);
                }
            } catch (NumberFormatException e) {
                // fall through
            }
        }

        if ("llvm.smin".equals(intrinsicName) && typeSuffix != null) {
            try {
                if (typeSuffix.length != null) {
                    int vectorLength = Integer.parseInt(typeSuffix.length);
                    return LLVMCMathsIntrinsicsFactory.LLVMVectorMinMaxNodeGen.create(args[1], args[2], vectorLength, LLVMSminOperator.INSTANCE);
                } else {
                    return LLVMCMathsIntrinsicsFactory.LLVMScalarMinMaxNodeGen.create(args[1], args[2], LLVMSminOperator.INSTANCE);
                }
            } catch (NumberFormatException e) {
                // fall through
            }
        }

        return LLVMX86_MissingBuiltin.create(declaration.getName());
    }

    private static CompareOperator getCompareOp(LLVMExpressionNode expr) {
        LLVMMetaLiteralNode meta = (LLVMMetaLiteralNode) expr;
        String op = (String) meta.getMetadata();
        switch (op) {
            case "oeq":
                return CompareOperator.FP_ORDERED_EQUAL;
            case "ogt":
                return CompareOperator.FP_ORDERED_GREATER_THAN;
            case "oge":
                return CompareOperator.FP_ORDERED_GREATER_OR_EQUAL;
            case "olt":
                return CompareOperator.FP_ORDERED_LESS_THAN;
            case "ole":
                return CompareOperator.FP_ORDERED_LESS_OR_EQUAL;
            case "one":
                return CompareOperator.FP_ORDERED_NOT_EQUAL;
            case "ord":
                return CompareOperator.FP_ORDERED;
            case "ueq":
                return CompareOperator.FP_UNORDERED_EQUAL;
            case "ugt":
                return CompareOperator.FP_UNORDERED_GREATER_THAN;
            case "uge":
                return CompareOperator.FP_UNORDERED_GREATER_OR_EQUAL;
            case "ult":
                return CompareOperator.FP_UNORDERED_LESS_THAN;
            case "ule":
                return CompareOperator.FP_UNORDERED_LESS_OR_EQUAL;
            case "une":
                return CompareOperator.FP_UNORDERED_NOT_EQUAL;
            case "uno":
                return CompareOperator.FP_UNORDERED;
            default:
                throw new LLVMParserException("unsupported fp compare op: " + op);
        }
    }

    private static final int CONSTRAINED_PREFIX_LEN = "llvm.experimental.constrained.".length();

    private LLVMExpressionNode getConstrainedFPBuiltin(FunctionDeclaration declaration, LLVMExpressionNode[] args) {
        /*
         * We ignore the two meta-arguments of the binary arithmetic llvm.experimental.constrained
         * builtins as they are just hints by compiler to optimization passes. They inform the
         * passes on possible assumptions about the current rounding mode and floating point
         * exceptions behavior. Other than that, they have exactly the same semantics as normal
         * floating point operations.
         */
        assert declaration.getName().startsWith("llvm.experimental.constrained.");
        int typeIndex = declaration.getName().indexOf('.', CONSTRAINED_PREFIX_LEN);
        String name = declaration.getName().substring(CONSTRAINED_PREFIX_LEN, typeIndex);
        Type retType = declaration.getType().getReturnType();

        if (retType == PrimitiveType.F128 || retType == PrimitiveType.X86_FP80) {
            LongDoubleKinds kind = LongDoubleKinds.FP80;
            if (retType.equals(PrimitiveType.F128)) {
                kind = LongDoubleKinds.FP128;
            }
            switch (name) {
                case "pow", "powi" -> {
                    return LLVMLongDoubleNode.createPowNode(args[1], args[2], kind);
                }
                case "sqrt", "log", "log2", "log10", "rint", "ceil", "floor", "exp", "exp2", "sin", "cos" -> {
                    return LLVMLongDoubleNode.createUnary(name, args[1], kind);
                }
                case "fmuladd" -> {
                    LLVMExpressionNode mulNodeF128 = createArithmeticOp(ArithmeticOperation.MUL, retType, args[1], args[2]);
                    return createArithmeticOp(ArithmeticOperation.ADD, retType, mulNodeF128, args[3]);
                }
            }
        }

        switch (name) {
            case "fadd":
                return createArithmeticOp(ArithmeticOperation.ADD, retType, args[1], args[2]);
            case "fsub":
                return createArithmeticOp(ArithmeticOperation.SUB, retType, args[1], args[2]);
            case "fmul":
                return createArithmeticOp(ArithmeticOperation.MUL, retType, args[1], args[2]);
            case "fdiv":
                return createArithmeticOp(ArithmeticOperation.DIV, retType, args[1], args[2]);
            case "frem":
                return createArithmeticOp(ArithmeticOperation.REM, retType, args[1], args[2]);
            case "fptoui":
            case "uitofp":
                return CommonNodeFactory.createUnsignedCast(args[1], retType);
            case "fpext":
            case "fptosi":
            case "sitofp":
            case "fptrunc":
                return CommonNodeFactory.createSignedCast(args[1], retType);
            case "fcmp":
            case "fcmps":
                return CommonNodeFactory.createComparison(getCompareOp(args[3]), retType, args[1], args[2]);
            case "fmuladd":
                LLVMExpressionNode mulNodeF80 = createArithmeticOp(ArithmeticOperation.MUL, retType, args[1], args[2]);
                return createArithmeticOp(ArithmeticOperation.ADD, retType, mulNodeF80, args[3]);

            case "sqrt":
                if (declaration.getName().endsWith("v2f64")) {
                    return LLVMCMathsIntrinsicsFactory.LLVMSqrtVectorNodeGen.create(2, args[1]);
                } else if (declaration.getName().endsWith("v4f32")) {
                    return LLVMCMathsIntrinsicsFactory.LLVMSqrtVectorNodeGen.create(4, args[1]);
                } else {
                    return LLVMCMathsIntrinsicsFactory.LLVMSqrtNodeGen.create(args[1]);
                }
            case "pow":
            case "powi":
                return LLVMPowNodeGen.create(args[1], args[2]);
            case "sin":
                return LLVMCMathsIntrinsicsFactory.LLVMSinNodeGen.create(args[1]);
            case "cos":
                return LLVMCMathsIntrinsicsFactory.LLVMCosNodeGen.create(args[1]);
            case "exp":
                return LLVMCMathsIntrinsicsFactory.LLVMExpNodeGen.create(args[1]);
            case "exp2":
                return LLVMCMathsIntrinsicsFactory.LLVMExp2NodeGen.create(args[1]);
            case "log":
                return LLVMCMathsIntrinsicsFactory.LLVMLogNodeGen.create(args[1]);
            case "log10":
                return LLVMCMathsIntrinsicsFactory.LLVMLog10NodeGen.create(args[1]);
            case "log2":
                return LLVMCMathsIntrinsicsFactory.LLVMLog2NodeGen.create(args[1]);
            case "rint":
                return LLVMCMathsIntrinsicsFactory.LLVMRintNodeGen.create(args[1]);

            case "minnum":
                return LLVMCMathsIntrinsicsFactory.LLVMMinnumNodeGen.create(args[1], args[2]);
            case "maxnum":
                return LLVMCMathsIntrinsicsFactory.LLVMMaxnumNodeGen.create(args[1], args[2]);
            case "ceil":
                return LLVMCMathsIntrinsicsFactory.LLVMCeilNodeGen.create(args[1]);
            case "floor":
                return LLVMCMathsIntrinsicsFactory.LLVMFloorNodeGen.create(args[1]);
            case "round":
                return LLVMCMathsIntrinsicsFactory.LLVMRoundNodeGen.create(args[1]);

        }

        return LLVMX86_MissingBuiltin.create(declaration.getName());
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
                case F128:
                    return LLVMFP128ArithmeticNodeGen.create(op, left, right);
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
                case F128:
                    return LLVMFP128UnaryNodeGen.create(op, operand);
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
    public LLVMExpressionNode createVarArgCompoundValue(long length, int alignment, Type type, LLVMExpressionNode parameterNode) {
        return LLVMVarArgCompoundAddressNodeGen.create(parameterNode, length, alignment, type);
    }

    @Override
    public LLVMMemMoveNode createMemMove() {
        return NativeProfiledMemMoveNodeGen.create();
    }

    @Override
    public LLVMAllocateNode createAllocateGlobalsBlock(long totalSize) {
        return AllocateGlobalsBlockNode.create(totalSize);
    }

    @Override
    public LLVMMemorySizedOpNode createProtectGlobalsBlock() {
        return ProtectReadOnlyGlobalsBlockNodeGen.create();
    }

    @Override
    public LLVMMemorySizedOpNode createFreeGlobalsBlock() {
        return FreeGlobalsBlockNodeGen.create();
    }

    @Override
    public LLVMMemorySizedOpNode getFreeGlobalsBlockUncached() {
        return FreeGlobalsBlockNodeGen.getUncached();
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

    public int getByteAlignment(Type type) {
        return type.getAlignment(dataLayout);
    }

    public long getByteSize(Type type) throws TypeOverflowException {
        return type.getSize(dataLayout);
    }

    public long getIndexOffset(long index, AggregateType type) throws TypeOverflowException {
        return type.getOffsetOf(index, dataLayout);
    }

    @Override
    public boolean boxGlobals() {
        return true;
    }

    @Override
    public LLVMStackAccess createStackAccess() {
        return new LLVMStack.LLVMNativeStackAccess(language.getLLVMMemory());
    }

    @Override
    public void addStackSlots(Builder builder) {
        int stackId = builder.addSlot(FrameSlotKind.Object, null, null);
        assert stackId == LLVMStack.STACK_ID;
        int uniquesRegionId = builder.addSlot(FrameSlotKind.Object, null, null);
        assert uniquesRegionId == LLVMStack.UNIQUES_REGION_ID;
        int basePointerId = builder.addSlot(FrameSlotKind.Long, null, null);
        assert basePointerId == LLVMStack.BASE_POINTER_ID;
    }
}
