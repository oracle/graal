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
package com.oracle.truffle.llvm.asm.amd64;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException;
import com.oracle.truffle.llvm.runtime.LLVMUnsupportedException.UnsupportedReason;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStatementNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMStoreNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64AdcNodeFactory.LLVMAMD64AdcbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64AdcNodeFactory.LLVMAMD64AdclNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64AdcNodeFactory.LLVMAMD64AdcqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64AdcNodeFactory.LLVMAMD64AdcwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64AddNodeFactory.LLVMAMD64AddbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64AddNodeFactory.LLVMAMD64AddlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64AddNodeFactory.LLVMAMD64AddqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64AddNodeFactory.LLVMAMD64AddwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64AndNodeFactory.LLVMAMD64AndbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64AndNodeFactory.LLVMAMD64AndlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64AndNodeFactory.LLVMAMD64AndqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64AndNodeFactory.LLVMAMD64AndwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64BsfNodeFactory.LLVMAMD64BsflNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64BsfNodeFactory.LLVMAMD64BsfqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64BsfNodeFactory.LLVMAMD64BsfwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64BsrNodeFactory.LLVMAMD64BsrlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64BsrNodeFactory.LLVMAMD64BsrqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64BsrNodeFactory.LLVMAMD64BsrwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64BswapNodeFactory.LLVMAMD64BswaplNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64BswapNodeFactory.LLVMAMD64BswapqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64CmpNodeFactory.LLVMAMD64CmpbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64CmpNodeFactory.LLVMAMD64CmplNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64CmpNodeFactory.LLVMAMD64CmpqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64CmpNodeFactory.LLVMAMD64CmpwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64CmpXchgNodeFactory.LLVMAMD64CmpXchgbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64CmpXchgNodeFactory.LLVMAMD64CmpXchglNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64CmpXchgNodeFactory.LLVMAMD64CmpXchgqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64CmpXchgNodeFactory.LLVMAMD64CmpXchgwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64CpuidNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64DecNodeFactory.LLVMAMD64DecbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64DecNodeFactory.LLVMAMD64DeclNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64DecNodeFactory.LLVMAMD64DecqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64DecNodeFactory.LLVMAMD64DecwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64DivNodeFactory.LLVMAMD64DivbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64DivNodeFactory.LLVMAMD64DivlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64DivNodeFactory.LLVMAMD64DivqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64DivNodeFactory.LLVMAMD64DivwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64GetFlagNodesFactory.LLVMAMD64GetFlagEqualNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64GetFlagNodesFactory.LLVMAMD64GetFlagGNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64GetFlagNodesFactory.LLVMAMD64GetFlagLENodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64GetFlagNodesFactory.LLVMAMD64GetFlagNegNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64GetFlagNodesFactory.LLVMAMD64GetFlagNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64GetFlagNodesFactory.LLVMAMD64GetFlagNorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64GetFlagNodesFactory.LLVMAMD64GetFlagOrNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64GetFlagNodesFactory.LLVMAMD64GetFlagXorNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64IdivNodeFactory.LLVMAMD64IdivbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64IdivNodeFactory.LLVMAMD64IdivlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64IdivNodeFactory.LLVMAMD64IdivqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64IdivNodeFactory.LLVMAMD64IdivwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ImmNodeFactory.LLVMAMD64I16NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ImmNodeFactory.LLVMAMD64I1NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ImmNodeFactory.LLVMAMD64I32NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ImmNodeFactory.LLVMAMD64I64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ImmNodeFactory.LLVMAMD64I8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ImulNodeFactory.LLVMAMD64ImulbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ImulNodeFactory.LLVMAMD64Imull3NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ImulNodeFactory.LLVMAMD64ImullNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ImulNodeFactory.LLVMAMD64Imulq3NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ImulNodeFactory.LLVMAMD64ImulqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ImulNodeFactory.LLVMAMD64Imulw3NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ImulNodeFactory.LLVMAMD64ImulwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64IncNodeFactory.LLVMAMD64IncbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64IncNodeFactory.LLVMAMD64InclNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64IncNodeFactory.LLVMAMD64IncqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64IncNodeFactory.LLVMAMD64IncwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64LoadFlagsFactory.LLVMAMD64LahfNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64LoadFlagsFactory.LLVMAMD64ReadFlagswNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64MulNodeFactory.LLVMAMD64MulbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64MulNodeFactory.LLVMAMD64MullNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64MulNodeFactory.LLVMAMD64MulqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64MulNodeFactory.LLVMAMD64MulwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64NegNodeFactory.LLVMAMD64NegbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64NegNodeFactory.LLVMAMD64NeglNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64NegNodeFactory.LLVMAMD64NegqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64NegNodeFactory.LLVMAMD64NegwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64NotNodeFactory.LLVMAMD64NotbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64NotNodeFactory.LLVMAMD64NotlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64NotNodeFactory.LLVMAMD64NotqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64NotNodeFactory.LLVMAMD64NotwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64OrNodeFactory.LLVMAMD64OrbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64OrNodeFactory.LLVMAMD64OrlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64OrNodeFactory.LLVMAMD64OrqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64OrNodeFactory.LLVMAMD64OrwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64PopNodeFactory.LLVMAMD64PoplNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64PopNodeFactory.LLVMAMD64PopqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64PopNodeFactory.LLVMAMD64PopwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64PushNodeFactory.LLVMAMD64PushlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64PushNodeFactory.LLVMAMD64PushqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64PushNodeFactory.LLVMAMD64PushwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RdRandNodeFactory.LLVMAMD64RdRandlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RdRandNodeFactory.LLVMAMD64RdRandqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RdRandNodeFactory.LLVMAMD64RdRandwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RdSeedNodeFactory.LLVMAMD64RdSeedlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RdSeedNodeFactory.LLVMAMD64RdSeedqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RdSeedNodeFactory.LLVMAMD64RdSeedwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RdtscNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RepNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RolNodeFactory.LLVMAMD64RolbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RolNodeFactory.LLVMAMD64RollNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RolNodeFactory.LLVMAMD64RolqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RolNodeFactory.LLVMAMD64RolwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RorNodeFactory.LLVMAMD64RorbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RorNodeFactory.LLVMAMD64RorlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RorNodeFactory.LLVMAMD64RorqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64RorNodeFactory.LLVMAMD64RorwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64SalNodeFactory.LLVMAMD64SalbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64SalNodeFactory.LLVMAMD64SallNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64SalNodeFactory.LLVMAMD64SalqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64SalNodeFactory.LLVMAMD64SalwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64SarNodeFactory.LLVMAMD64SarbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64SarNodeFactory.LLVMAMD64SarlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64SarNodeFactory.LLVMAMD64SarqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64SarNodeFactory.LLVMAMD64SarwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64SetFlagNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ShlNodeFactory.LLVMAMD64ShlbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ShlNodeFactory.LLVMAMD64ShllNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ShlNodeFactory.LLVMAMD64ShlqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ShlNodeFactory.LLVMAMD64ShlwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ShrNodeFactory.LLVMAMD64ShrbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ShrNodeFactory.LLVMAMD64ShrlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ShrNodeFactory.LLVMAMD64ShrqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64ShrNodeFactory.LLVMAMD64ShrwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64StoreFlagsFactory.LLVMAMD64SahfNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64StoreFlagsFactory.LLVMAMD64WriteFlagswNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64StosNodeFactory.LLVMAMD64StosbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64StosNodeFactory.LLVMAMD64StosdNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64StosNodeFactory.LLVMAMD64StosqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64StosNodeFactory.LLVMAMD64StoswNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64SubNodeFactory.LLVMAMD64SubbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64SubNodeFactory.LLVMAMD64SublNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64SubNodeFactory.LLVMAMD64SubqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64SubNodeFactory.LLVMAMD64SubwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64Ud2NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64XaddNodeFactory.LLVMAMD64XaddbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64XaddNodeFactory.LLVMAMD64XaddlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64XaddNodeFactory.LLVMAMD64XaddqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64XaddNodeFactory.LLVMAMD64XaddwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64XchgNodeFactory.LLVMAMD64XchgbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64XchgNodeFactory.LLVMAMD64XchglNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64XchgNodeFactory.LLVMAMD64XchgqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64XchgNodeFactory.LLVMAMD64XchgwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64XorNodeFactory.LLVMAMD64XorbNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64XorNodeFactory.LLVMAMD64XorlNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64XorNodeFactory.LLVMAMD64XorqNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.LLVMAMD64XorNodeFactory.LLVMAMD64XorwNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64AddressComputationNodeFactory.LLVMAMD64AddressDisplacementComputationNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64AddressComputationNodeFactory.LLVMAMD64AddressNoBaseOffsetComputationNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64AddressComputationNodeFactory.LLVMAMD64AddressOffsetComputationNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64AddressComputationNodeFactory.LLVMAMD64AddressSegmentComputationNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64Flags;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64GetTlsNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64ReadAddressNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64ReadRegisterNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64Target;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64ToI8NodeFactory.LLVMAMD64I64ToI8NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64ToRegisterNodeFactory.LLVMI16ToR64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64ToRegisterNodeFactory.LLVMI32ToR64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64ToRegisterNodeFactory.LLVMI8ToR64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64UpdateFlagsNode.LLVMAMD64UpdateCPAZSOFlagsNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64UpdateFlagsNode.LLVMAMD64UpdateCPZSOFlagsNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64UpdateFlagsNode.LLVMAMD64UpdatePZSFlagsNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64UpdateFlagsNode.LLVMAMD64UpdatePZSOFlagsNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteAddressRegisterNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteBooleanNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteTupelNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteTupelNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteValueNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.support.LLVMAMD64WriteValueNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMSyscallNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.cast.LLVMToAddressNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMArgNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug.LLVMDebugTrapNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_ConversionNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.x86.LLVMX86_ConversionNodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.memory.LLVMFenceNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMDirectLoadNodeFactory.LLVMPointerDirectLoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI16LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI32LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI64LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.load.LLVMI8LoadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI16StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMPointerStoreNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.others.LLVMUnsupportedInstructionNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.LLVMAddressReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMReadNodeFactory.LLVMI1ReadNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNode.LLVMWritePointerNode;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI1NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI64NodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.LLVMWriteNodeFactory.LLVMWritePointerNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.vars.StructLiteralNodeGen;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;
import com.oracle.truffle.llvm.runtime.types.VoidType;

import static com.oracle.truffle.llvm.runtime.types.Type.TypeArrayBuilder;

class AsmFactory {
    private static final int REG_START_INDEX = 1;
    private static final String TEMP_REGISTER_PREFIX = "__$$tmp_r_";

    private static final String CONSTRAINT_REG = "r";
    private static final String CONSTRAINT_REG_L = "q";
    private static final String CONSTRAINT_REG_XMM = "x";

    private final FrameDescriptor frameDescriptor;
    private final List<LLVMStatementNode> statements;
    private final List<LLVMStatementNode> arguments;
    private final List<String> registers;
    private LLVMExpressionNode result;
    private List<Argument> argInfo;
    private final String asmFlags;
    private final TypeArrayBuilder argTypes;
    private final Type retType;
    private final Type[] retTypes;
    private final long[] retOffsets;

    private String currentPrefix;

    private final LLVMLanguage language;

    AsmFactory(LLVMLanguage language, TypeArrayBuilder argTypes, String asmFlags, Type retType, Type[] retTypes, long[] retOffsets) {
        this.language = language;
        this.argTypes = argTypes;
        this.asmFlags = asmFlags;
        this.frameDescriptor = new FrameDescriptor();
        this.statements = new ArrayList<>();
        this.arguments = new ArrayList<>();
        this.registers = new ArrayList<>();
        this.retType = retType;
        this.retTypes = retTypes;
        this.retOffsets = retOffsets;
        parseArguments();
    }

    private static AsmParseException invalidOperandType(Type type) {
        return new AsmParseException("invalid operand type: " + type);
    }

    private static AsmParseException unsupportedOperandType(Type type) {
        return new AsmParseException("unsupported operand type: " + type);
    }

    private void parseArguments() {
        argInfo = new ArrayList<>();
        String[] tokens = asmFlags.substring(1, asmFlags.length() - 1).split(",");

        int index = REG_START_INDEX + (retType instanceof StructureType ? 1 : 0);
        int outIndex = 0;

        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            boolean isTilde = false;
            boolean isInput = true;
            boolean isOutput = false;
            boolean isMemory = false;
            boolean isAnonymous = false;
            String source = null;
            String registerName = null;
            int i;
            for (i = 0; i < token.length() && source == null; i++) {
                switch (token.charAt(i)) {
                    case '~':
                        isTilde = true;
                        isInput = false;
                        break;
                    case '+':
                        isInput = true;
                        isOutput = true;
                        break;
                    case '=':
                        isInput = false;
                        isOutput = true;
                        break;
                    case '*':
                        isMemory = true;
                        break;
                    case '&':
                        break;
                    default:
                        source = token.substring(i);
                        break;
                }
            }

            if (isTilde) {
                continue;
            }
            if (source == null) {
                throw new AsmParseException("invalid token: " + token);
            }

            int start = source.indexOf('{');
            int end = source.lastIndexOf('}');
            if (start != -1 && end != -1) {
                registerName = source.substring(start + 1, end);
            } else if (CONSTRAINT_REG.equals(source) || CONSTRAINT_REG_L.equals(source) || CONSTRAINT_REG_XMM.equals(source)) {
                registerName = TEMP_REGISTER_PREFIX + argInfo.size();
                isAnonymous = true;
            } else if (source.length() == 1 && Character.isDigit(source.charAt(0))) {
                int id = Character.digit(source.charAt(0), 10);
                Argument arg = argInfo.get(id);
                assert isInput && !isOutput;
                isInput = true;
                isOutput = false;
                if (arg.isRegister()) {
                    registerName = arg.getRegister();
                }
                isAnonymous = arg.isAnonymous();
            }

            assert registerName == null || AsmRegisterOperand.isRegister(registerName) || registerName.startsWith(TEMP_REGISTER_PREFIX);

            int idIn = index;
            int idOut = outIndex;
            Type type;
            if (isInput) {
                type = argTypes.get(index++);
            } else if (retType instanceof StructureType) {
                if (isMemory) {
                    type = argTypes.get(index);
                    idOut = index++;
                } else {
                    type = retTypes[outIndex++];
                }
            } else if (isOutput) {
                type = retType;
                if (isMemory) {
                    if (type instanceof VoidType) {
                        type = argTypes.get(index);
                    }
                    idOut = index++;
                }
            } else {
                throw new AssertionError("neither input nor output");
            }
            if (isAnonymous && type instanceof PointerType) {
                assert registerName != null;
                addFrameSlot(registerName, type);
            }
            argInfo.add(new Argument(isInput, isOutput, isMemory, isAnonymous, type, argInfo.size(), idIn, idOut, source, registerName));
        }
        assert index == argTypes.size();
        assert retType instanceof StructureType ? outIndex == retOffsets.length : outIndex == 0;
    }

    LLVMInlineAssemblyRootNode finishInline() {
        getArguments();
        return new LLVMInlineAssemblyRootNode(language, frameDescriptor, statements, arguments, result);
    }

    void setPrefix(String prefix) {
        this.currentPrefix = prefix;
    }

    void createInt(AsmImmediateOperand nr) {
        long id = nr.getValue();
        if (id == 3) {
            statements.add(LLVMDebugTrapNodeGen.create());
        } else {
            statements.add(LLVMUnsupportedInstructionNode.create(LLVMUnsupportedException.UnsupportedReason.INLINE_ASSEMBLER, "interrupt " + nr));
        }
    }

    private void createRep(LLVMStatementNode body) {
        if ("rep".equals(currentPrefix)) {
            LLVMExpressionNode rcx = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rcx"));
            LLVMAMD64WriteValueNode writeRCX = getStore(PrimitiveType.I64, new AsmRegisterOperand("rcx"));
            statements.add(LLVMAMD64RepNodeGen.create(writeRCX, rcx, body));
        } else {
            statements.add(body);
        }
    }

    void createOperation(String operation) {
        switch (operation) {
            case "cld":
                statements.add(LLVMAMD64SetFlagNodeGen.create(getFlagWrite(LLVMAMD64Flags.DF), false));
                break;
            case "clc":
            case "cli":
            case "cmc":
                statements.add(LLVMUnsupportedInstructionNode.create(UnsupportedReason.INLINE_ASSEMBLER, operation));
                break;
            case "lahf": {
                LLVMExpressionNode lahf = LLVMAMD64LahfNodeGen.create(getFlag(LLVMAMD64Flags.CF), getFlag(LLVMAMD64Flags.PF), getFlag(LLVMAMD64Flags.AF), getFlag(LLVMAMD64Flags.ZF),
                                getFlag(LLVMAMD64Flags.SF));
                statements.add(getOperandStore(PrimitiveType.I8, new AsmRegisterOperand("ah"), lahf));
                break;
            }
            case "sahf": {
                LLVMExpressionNode ah = getOperandLoad(PrimitiveType.I8, new AsmRegisterOperand("ah"));
                statements.add(LLVMAMD64SahfNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF),
                                getFlagWrite(LLVMAMD64Flags.ZF), getFlagWrite(LLVMAMD64Flags.SF), ah));
                break;
            }
            case "popf":
            case "popfw": {
                LLVMExpressionNode read = LLVMAMD64PopwNodeGen.create();
                statements.add(LLVMAMD64WriteFlagswNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF),
                                getFlagWrite(LLVMAMD64Flags.ZF), getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), read));
                break;
            }
            case "pushf":
            case "pushfw": {
                LLVMExpressionNode flags = LLVMAMD64ReadFlagswNodeGen.create(getFlag(LLVMAMD64Flags.CF), getFlag(LLVMAMD64Flags.PF), getFlag(LLVMAMD64Flags.AF), getFlag(LLVMAMD64Flags.ZF),
                                getFlag(LLVMAMD64Flags.SF), getFlag(LLVMAMD64Flags.OF));
                statements.add(LLVMAMD64PushwNodeGen.create(flags));
                break;
            }
            case "std":
                statements.add(LLVMAMD64SetFlagNodeGen.create(getFlagWrite(LLVMAMD64Flags.DF), true));
                break;
            case "stc":
            case "sti":
                statements.add(LLVMUnsupportedInstructionNode.create(UnsupportedReason.INLINE_ASSEMBLER, operation));
                break;
            case "nop":
            case "pause":
                break;
            case "hlt":
                // TODO: implement properly
                break;
            case "mfence":
            case "lfence":
            case "sfence":
                statements.add(LLVMFenceNodeGen.create());
                break;
            case "rdtsc": {
                LLVMAMD64WriteValueNode high = getRegisterStore("rdx");
                LLVMAMD64WriteValueNode low = getRegisterStore("rax");
                LLVMAMD64WriteTupelNode out = LLVMAMD64WriteTupelNodeGen.create(low, high);
                statements.add(LLVMAMD64RdtscNodeGen.create(out));
                break;
            }
            case "cpuid": {
                LLVMExpressionNode level = getOperandLoad(PrimitiveType.I32, new AsmRegisterOperand("eax"));
                LLVMAMD64WriteValueNode eax = getRegisterStore("eax");
                LLVMAMD64WriteValueNode ebx = getRegisterStore("ebx");
                LLVMAMD64WriteValueNode ecx = getRegisterStore("ecx");
                LLVMAMD64WriteValueNode edx = getRegisterStore("edx");
                statements.add(LLVMAMD64CpuidNodeGen.create(eax, ebx, ecx, edx, level));
                break;
            }
            case "ud2":
                statements.add(LLVMAMD64Ud2NodeGen.create());
                break;
            case "syscall": {
                LLVMExpressionNode rax = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rax"));
                LLVMExpressionNode rdi = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rdi"));
                LLVMExpressionNode rsi = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rsi"));
                LLVMExpressionNode rdx = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rdx"));
                LLVMExpressionNode r10 = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("r10"));
                LLVMExpressionNode r8 = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("r8"));
                LLVMExpressionNode r9 = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("r9"));
                LLVMExpressionNode syscall = LLVMSyscallNodeGen.create(rax, rdi, rsi, rdx, r10, r8, r9);
                statements.add(getOperandStore(PrimitiveType.I64, new AsmRegisterOperand("rax"), syscall));
                break;
            }
            case "stosb": {
                LLVMExpressionNode al = getOperandLoad(PrimitiveType.I8, new AsmRegisterOperand("al"));
                LLVMExpressionNode rdi = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rdi"));
                LLVMExpressionNode df = getFlag(LLVMAMD64Flags.DF);
                LLVMAMD64WriteValueNode writeRDI = getStore(PrimitiveType.I64, new AsmRegisterOperand("rdi"));
                createRep(LLVMAMD64StosbNodeGen.create(writeRDI, al, rdi, df));
                break;
            }
            case "stosw": {
                LLVMExpressionNode ax = getOperandLoad(PrimitiveType.I16, new AsmRegisterOperand("ax"));
                LLVMExpressionNode rdi = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rdi"));
                LLVMExpressionNode df = getFlag(LLVMAMD64Flags.DF);
                LLVMAMD64WriteValueNode writeRDI = getStore(PrimitiveType.I64, new AsmRegisterOperand("rdi"));
                createRep(LLVMAMD64StoswNodeGen.create(writeRDI, ax, rdi, df));
                break;
            }
            case "stosd": {
                LLVMExpressionNode eax = getOperandLoad(PrimitiveType.I32, new AsmRegisterOperand("eax"));
                LLVMExpressionNode rdi = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rdi"));
                LLVMExpressionNode df = getFlag(LLVMAMD64Flags.DF);
                LLVMAMD64WriteValueNode writeRDI = getStore(PrimitiveType.I64, new AsmRegisterOperand("rdi"));
                createRep(LLVMAMD64StosdNodeGen.create(writeRDI, eax, rdi, df));
                break;
            }
            case "stosq": {
                LLVMExpressionNode rax = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rax"));
                LLVMExpressionNode rdi = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rdi"));
                LLVMExpressionNode df = getFlag(LLVMAMD64Flags.DF);
                LLVMAMD64WriteValueNode writeRDI = getStore(PrimitiveType.I64, new AsmRegisterOperand("rdi"));
                createRep(LLVMAMD64StosqNodeGen.create(writeRDI, rax, rdi, df));
                break;
            }
            default:
                statements.add(LLVMUnsupportedInstructionNode.create(UnsupportedReason.INLINE_ASSEMBLER, operation));
                return;
        }
    }

    void createUnaryOperationImplicitSize(String operation, AsmOperand operand) {
        LLVMExpressionNode out;
        AsmOperand dst = operand;
        assert operand != null;
        assert operation.length() > 0;
        Type dstType = getType(operand);
        switch (operation) {
            case "seta":
            case "setnbe":
                out = LLVMAMD64GetFlagNorNodeGen.create(getFlag(LLVMAMD64Flags.CF), getFlag(LLVMAMD64Flags.ZF));
                dstType = PrimitiveType.I8;
                break;
            case "setae":
            case "setnb":
            case "setnc":
                out = LLVMAMD64GetFlagNegNodeGen.create(getFlag(LLVMAMD64Flags.CF));
                dstType = PrimitiveType.I8;
                break;
            case "setb":
            case "setc":
            case "setnae":
                out = LLVMAMD64GetFlagNodeGen.create(getFlag(LLVMAMD64Flags.CF));
                dstType = PrimitiveType.I8;
                break;
            case "sete":
            case "setz":
                out = LLVMAMD64GetFlagNodeGen.create(getFlag(LLVMAMD64Flags.ZF));
                dstType = PrimitiveType.I8;
                break;
            case "setg":
            case "setnle":
                out = LLVMAMD64GetFlagGNodeGen.create(getFlag(LLVMAMD64Flags.ZF), getFlag(LLVMAMD64Flags.SF), getFlag(LLVMAMD64Flags.OF));
                dstType = PrimitiveType.I8;
                break;
            case "setge":
            case "setnl":
                out = LLVMAMD64GetFlagEqualNodeGen.create(getFlag(LLVMAMD64Flags.SF), getFlag(LLVMAMD64Flags.OF));
                dstType = PrimitiveType.I8;
                break;
            case "setl":
            case "setnge":
                out = LLVMAMD64GetFlagXorNodeGen.create(getFlag(LLVMAMD64Flags.SF), getFlag(LLVMAMD64Flags.OF));
                dstType = PrimitiveType.I8;
                break;
            case "setle":
            case "setng":
                out = LLVMAMD64GetFlagLENodeGen.create(getFlag(LLVMAMD64Flags.ZF), getFlag(LLVMAMD64Flags.SF), getFlag(LLVMAMD64Flags.OF));
                dstType = PrimitiveType.I8;
                break;
            case "setbe":
            case "setna":
                out = LLVMAMD64GetFlagOrNodeGen.create(getFlag(LLVMAMD64Flags.CF), getFlag(LLVMAMD64Flags.ZF));
                dstType = PrimitiveType.I8;
                break;
            case "setne":
            case "setnz":
                out = LLVMAMD64GetFlagNegNodeGen.create(getFlag(LLVMAMD64Flags.ZF));
                dstType = PrimitiveType.I8;
                break;
            case "setno":
                out = LLVMAMD64GetFlagNegNodeGen.create(getFlag(LLVMAMD64Flags.OF));
                dstType = PrimitiveType.I8;
                break;
            case "setnp":
            case "setpo":
                out = LLVMAMD64GetFlagNegNodeGen.create(getFlag(LLVMAMD64Flags.PF));
                dstType = PrimitiveType.I8;
                break;
            case "setns":
                out = LLVMAMD64GetFlagNegNodeGen.create(getFlag(LLVMAMD64Flags.SF));
                dstType = PrimitiveType.I8;
                break;
            case "seto":
                out = LLVMAMD64GetFlagNodeGen.create(getFlag(LLVMAMD64Flags.OF));
                dstType = PrimitiveType.I8;
                break;
            case "setp":
            case "setpe":
                out = LLVMAMD64GetFlagNodeGen.create(getFlag(LLVMAMD64Flags.PF));
                dstType = PrimitiveType.I8;
                break;
            case "sets":
                out = LLVMAMD64GetFlagNodeGen.create(getFlag(LLVMAMD64Flags.SF));
                dstType = PrimitiveType.I8;
                break;
            case "rdrand":
                switch (getPrimitiveType(dstType)) {
                    case I16:
                        out = LLVMAMD64RdRandwNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF));
                        break;
                    case I32:
                        out = LLVMAMD64RdRandlNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF));
                        break;
                    case I64:
                        out = LLVMAMD64RdRandqNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF));
                        break;
                    default:
                        throw invalidOperandType(dstType);
                }
                break;
            case "rdseed":
                switch (getPrimitiveType(dstType)) {
                    case I16:
                        out = LLVMAMD64RdSeedwNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF));
                        break;
                    case I32:
                        out = LLVMAMD64RdSeedlNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF));
                        break;
                    case I64:
                        out = LLVMAMD64RdSeedqNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF));
                        break;
                    default:
                        throw invalidOperandType(dstType);
                }
                break;
            case "pop":
                // default size: I64
                if (dstType == null) {
                    dstType = PrimitiveType.I64;
                }
                if (dstType instanceof PointerType) {
                    dstType = ((PointerType) dstType).getPointeeType();
                    switch (getPrimitiveType(dstType)) {
                        case I16:
                            out = LLVMAMD64PopwNodeGen.create();
                            break;
                        case I32:
                            out = LLVMAMD64PoplNodeGen.create();
                            break;
                        case I64:
                            out = LLVMAMD64PopqNodeGen.create();
                            break;
                        default:
                            throw invalidOperandType(dstType);
                    }
                } else {
                    switch (getPrimitiveType(dstType)) {
                        case I16:
                            out = LLVMAMD64PopwNodeGen.create();
                            break;
                        case I32:
                            out = LLVMAMD64PoplNodeGen.create();
                            break;
                        case I64:
                            out = LLVMAMD64PopqNodeGen.create();
                            break;
                        default:
                            throw invalidOperandType(dstType);
                    }
                }
                break;
            case "push":
                // default size: I64
                if (dstType == null) {
                    dstType = PrimitiveType.I64;
                }
                if (dstType instanceof PointerType) {
                    dstType = ((PointerType) dstType).getPointeeType();
                    LLVMExpressionNode src = getOperandLoad(dstType, operand);
                    switch (getPrimitiveType(dstType)) {
                        case I16:
                            statements.add(LLVMAMD64PushwNodeGen.create(src));
                            return;
                        case I32:
                            statements.add(LLVMAMD64PushlNodeGen.create(src));
                            return;
                        case I64:
                            statements.add(LLVMAMD64PushqNodeGen.create(src));
                            return;
                        default:
                            throw invalidOperandType(dstType);
                    }
                } else {
                    LLVMExpressionNode src = getOperandLoad(dstType, operand);
                    switch (getPrimitiveType(dstType)) {
                        case I16:
                            statements.add(LLVMAMD64PushwNodeGen.create(src));
                            return;
                        case I32:
                            statements.add(LLVMAMD64PushlNodeGen.create(src));
                            return;
                        case I64:
                            statements.add(LLVMAMD64PushqNodeGen.create(src));
                            return;
                        default:
                            throw invalidOperandType(dstType);
                    }
                }
            case "bswap":
                LLVMExpressionNode src = getOperandLoad(dstType, operand);
                switch (getPrimitiveType(dstType)) {
                    case I32:
                        out = LLVMAMD64BswaplNodeGen.create(src);
                        break;
                    case I64:
                        out = LLVMAMD64BswapqNodeGen.create(src);
                        break;
                    default:
                        throw invalidOperandType(dstType);
                }
                break;
            default:
                statements.add(LLVMUnsupportedInstructionNode.create(UnsupportedReason.INLINE_ASSEMBLER, operation));
                return;
        }
        if (dstType == null) {
            throw new IllegalArgumentException("unknown operand width");
        }
        statements.add(getOperandStore(dstType, dst, out));
    }

    void createUnaryOperation(String operation, AsmOperand operand) {
        LLVMExpressionNode src;
        LLVMExpressionNode out;
        AsmOperand dst = operand;
        Type dstType;
        assert operation.length() > 0;
        char suffix = operation.charAt(operation.length() - 1);
        dstType = getPrimitiveTypeFromSuffix(suffix);
        src = getOperandLoad(dstType, operand);
        switch (operation) {
            case "incb":
                out = LLVMAMD64IncbNodeGen.create(getUpdatePZSOFlagsNode(), src);
                break;
            case "incw":
                out = LLVMAMD64IncwNodeGen.create(getUpdatePZSOFlagsNode(), src);
                break;
            case "incl":
                out = LLVMAMD64InclNodeGen.create(getUpdatePZSOFlagsNode(), src);
                break;
            case "incq":
                out = LLVMAMD64IncqNodeGen.create(getUpdatePZSOFlagsNode(), src);
                break;
            case "decb":
                out = LLVMAMD64DecbNodeGen.create(getUpdatePZSOFlagsNode(), src);
                break;
            case "decw":
                out = LLVMAMD64DecwNodeGen.create(getUpdatePZSOFlagsNode(), src);
                break;
            case "decl":
                out = LLVMAMD64DeclNodeGen.create(getUpdatePZSOFlagsNode(), src);
                break;
            case "decq":
                out = LLVMAMD64DecqNodeGen.create(getUpdatePZSOFlagsNode(), src);
                break;
            case "negb":
                out = LLVMAMD64NegbNodeGen.create(getUpdateCPZSOFlagsNode(), src);
                break;
            case "negw":
                out = LLVMAMD64NegwNodeGen.create(getUpdateCPZSOFlagsNode(), src);
                break;
            case "negl":
                out = LLVMAMD64NeglNodeGen.create(getUpdateCPZSOFlagsNode(), src);
                break;
            case "negq":
                out = LLVMAMD64NegqNodeGen.create(getUpdateCPZSOFlagsNode(), src);
                break;
            case "notb":
                out = LLVMAMD64NotbNodeGen.create(src);
                break;
            case "notw":
                out = LLVMAMD64NotwNodeGen.create(src);
                break;
            case "notl":
                out = LLVMAMD64NotlNodeGen.create(src);
                break;
            case "notq":
                out = LLVMAMD64NotqNodeGen.create(src);
                break;
            case "idivb":
                out = LLVMAMD64IdivbNodeGen.create(getOperandLoad(PrimitiveType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = PrimitiveType.I16;
                break;
            case "idivw": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("ax"), getRegisterStore("dx"));
                LLVMExpressionNode high = getOperandLoad(PrimitiveType.I16, new AsmRegisterOperand("dx"));
                statements.add(LLVMAMD64IdivwNodeGen.create(res, high, getOperandLoad(PrimitiveType.I16, new AsmRegisterOperand("ax")), src));
                return;
            }
            case "idivl": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("eax"), getRegisterStore("edx"));
                LLVMExpressionNode high = getOperandLoad(PrimitiveType.I32, new AsmRegisterOperand("edx"));
                statements.add(LLVMAMD64IdivlNodeGen.create(res, high, getOperandLoad(PrimitiveType.I32, new AsmRegisterOperand("eax")), src));
                return;
            }
            case "idivq": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("rax"), getRegisterStore("rdx"));
                LLVMExpressionNode high = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rdx"));
                statements.add(LLVMAMD64IdivqNodeGen.create(res, high, getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rax")), src));
                return;
            }
            case "imulb": {
                LLVMAMD64WriteValueNode res = getRegisterStore("ax");
                statements.add(LLVMAMD64ImulbNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF), getFlagWrite(LLVMAMD64Flags.ZF),
                                getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), res, getOperandLoad(PrimitiveType.I8, new AsmRegisterOperand("al")), src));
                return;
            }
            case "imulw": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("ax"), getRegisterStore("dx"));
                statements.add(LLVMAMD64ImulwNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF), getFlagWrite(LLVMAMD64Flags.ZF),
                                getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), res, getOperandLoad(PrimitiveType.I16, new AsmRegisterOperand("ax")), src));
                return;
            }
            case "imull": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("eax"), getRegisterStore("edx"));
                statements.add(LLVMAMD64ImullNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF), getFlagWrite(LLVMAMD64Flags.ZF),
                                getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), res, getOperandLoad(PrimitiveType.I32, new AsmRegisterOperand("eax")), src));
                return;
            }
            case "imulq": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("rax"), getRegisterStore("rdx"));
                statements.add(LLVMAMD64ImulqNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF), getFlagWrite(LLVMAMD64Flags.ZF),
                                getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), res, getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rax")), src));
                return;
            }
            case "divb": {
                LLVMAMD64WriteValueNode res = getRegisterStore("ax");
                statements.add(LLVMAMD64DivbNodeGen.create(res, getOperandLoad(PrimitiveType.I16, new AsmRegisterOperand("ax")), src));
                return;
            }
            case "divw": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("ax"), getRegisterStore("dx"));
                LLVMExpressionNode high = getOperandLoad(PrimitiveType.I16, new AsmRegisterOperand("dx"));
                statements.add(LLVMAMD64DivwNodeGen.create(res, high, getOperandLoad(PrimitiveType.I16, new AsmRegisterOperand("ax")), src));
                return;
            }
            case "divl": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("eax"), getRegisterStore("edx"));
                LLVMExpressionNode high = getOperandLoad(PrimitiveType.I32, new AsmRegisterOperand("edx"));
                statements.add(LLVMAMD64DivlNodeGen.create(res, high, getOperandLoad(PrimitiveType.I32, new AsmRegisterOperand("eax")), src));
                return;
            }
            case "divq": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("rax"), getRegisterStore("rdx"));
                LLVMExpressionNode high = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rdx"));
                statements.add(LLVMAMD64DivqNodeGen.create(res, high, getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rax")), src));
                return;
            }
            case "mulb": {
                LLVMAMD64WriteValueNode res = getRegisterStore("ax");
                statements.add(LLVMAMD64MulbNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF), getFlagWrite(LLVMAMD64Flags.ZF),
                                getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), res, getOperandLoad(PrimitiveType.I8, new AsmRegisterOperand("al")), src));
                return;
            }
            case "mulw": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("ax"), getRegisterStore("dx"));
                statements.add(LLVMAMD64MulwNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF), getFlagWrite(LLVMAMD64Flags.ZF),
                                getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), res, getOperandLoad(PrimitiveType.I16, new AsmRegisterOperand("ax")), src));
                return;
            }
            case "mull": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("eax"), getRegisterStore("edx"));
                statements.add(LLVMAMD64MullNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF), getFlagWrite(LLVMAMD64Flags.ZF),
                                getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), res, getOperandLoad(PrimitiveType.I32, new AsmRegisterOperand("eax")), src));
                return;
            }
            case "mulq": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("rax"), getRegisterStore("rdx"));
                statements.add(LLVMAMD64MulqNodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF), getFlagWrite(LLVMAMD64Flags.ZF),
                                getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), res, getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rax")), src));
                return;
            }
            case "bswapl":
                out = LLVMAMD64BswaplNodeGen.create(src);
                break;
            case "bswapq":
                out = LLVMAMD64BswapqNodeGen.create(src);
                break;
            case "popw":
                out = LLVMAMD64PopwNodeGen.create();
                break;
            case "popl":
                out = LLVMAMD64PoplNodeGen.create();
                break;
            case "popq":
                out = LLVMAMD64PopqNodeGen.create();
                break;
            case "pushw":
                statements.add(LLVMAMD64PushwNodeGen.create(src));
                return;
            case "pushl":
                statements.add(LLVMAMD64PushlNodeGen.create(src));
                return;
            case "pushq":
                statements.add(LLVMAMD64PushqNodeGen.create(src));
                return;
            default:
                statements.add(LLVMUnsupportedInstructionNode.create(UnsupportedReason.INLINE_ASSEMBLER, operation));
                return;
        }
        statements.add(getOperandStore(dstType, dst, out));
    }

    private static boolean isShiftOperation(String operation) {
        return operation.startsWith("shl") || operation.startsWith("shr") || operation.startsWith("rol") || operation.startsWith("ror") || operation.startsWith("sal") || operation.startsWith("sar");
    }

    private static PrimitiveType getPrimitiveTypeFromSuffix(char suffix) {
        switch (suffix) {
            case 'b':
                return PrimitiveType.I8;
            case 'w':
                return PrimitiveType.I16;
            case 'l':
                return PrimitiveType.I32;
            case 'q':
                return PrimitiveType.I64;
            default:
                throw new AsmParseException("invalid size");
        }
    }

    private class XchgOperands {
        public final LLVMExpressionNode srcA;
        public final LLVMExpressionNode srcB;
        public final LLVMAMD64WriteValueNode dst1;
        public final LLVMAMD64WriteValueNode dst2;
        public final LLVMAMD64WriteTupelNode dst;

        XchgOperands(AsmOperand a, AsmOperand b, Type type) {
            if (b instanceof AsmRegisterOperand) {
                AsmRegisterOperand rb = (AsmRegisterOperand) b;
                srcA = getOperandLoad(type, a);
                srcB = getOperandLoad(type, b);
                dst1 = getStore(type, a);
                dst2 = getRegisterStore(rb.getRegister());
            } else if (a instanceof AsmRegisterOperand) {
                AsmRegisterOperand ra = (AsmRegisterOperand) a;
                srcA = getOperandLoad(type, b);
                srcB = getOperandLoad(type, a);
                dst1 = getStore(type, b);
                dst2 = getRegisterStore(ra.getRegister());
            } else if (a instanceof AsmArgumentOperand) {
                AsmArgumentOperand arg = (AsmArgumentOperand) a;
                Argument info = argInfo.get(arg.getIndex());
                if (info.isRegister()) {
                    srcA = getOperandLoad(type, b);
                    srcB = getOperandLoad(type, a);
                    dst1 = getStore(type, b);
                    dst2 = getRegisterStore(type, info.getRegister());
                } else {
                    throw new AsmParseException("not implemented");
                }
            } else {
                throw new AsmParseException("not implemented");
            }
            dst = LLVMAMD64WriteTupelNodeGen.create(dst1, dst2);
        }
    }

    private Type getType(AsmOperand operand) {
        Type type = operand.getType();
        if (type != null) {
            return type;
        } else if (operand instanceof AsmArgumentOperand) {
            AsmArgumentOperand op = (AsmArgumentOperand) operand;
            Argument info = argInfo.get(op.getIndex());
            return info.getType();
        } else {
            return null;
        }
    }

    private Type getType(AsmOperand dst, AsmOperand src) {
        Type type = getType(dst);
        if (type == null || type instanceof VoidType) {
            type = getType(src);
        }
        if (type == null) {
            throw new AsmParseException("cannot infer type");
        }
        return type;
    }

    void createBinaryOperationImplicitSize(String operation, AsmOperand a, AsmOperand b) {
        AsmOperand dst = b;
        AsmOperand src = a;
        assert a != null && b != null;
        Type dstType = getType(b, a);
        LLVMExpressionNode srcA;
        LLVMExpressionNode srcB;
        LLVMExpressionNode out;
        switch (operation) {
            case "lea":
                out = getOperandAddress(dstType, src);
                if (isLeaPointer(src)) {
                    dstType = new PointerType(dstType);
                }
                break;
            case "xor":
                srcA = getOperandLoad(dstType, a);
                srcB = getOperandLoad(dstType, b);
                switch (getPrimitiveType(dstType)) {
                    case I8:
                        out = LLVMAMD64XorbNodeGen.create(srcA, srcB);
                        break;
                    case I16:
                        out = LLVMAMD64XorwNodeGen.create(srcA, srcB);
                        break;
                    case I32:
                        out = LLVMAMD64XorlNodeGen.create(srcA, srcB);
                        break;
                    case I64:
                        out = LLVMAMD64XorqNodeGen.create(srcA, srcB);
                        break;
                    default:
                        throw invalidOperandType(dstType);
                }
                break;
            case "mov":
                if (dstType instanceof PrimitiveType || dstType instanceof PointerType) {
                    out = getOperandLoad(dstType, a);
                } else {
                    throw invalidOperandType(dstType);
                }
                break;
            case "bsr":
                srcA = getOperandLoad(dstType, a);
                srcB = getOperandLoad(dstType, b);
                switch (getPrimitiveType(dstType)) {
                    case I16:
                        out = LLVMAMD64BsrwNodeGen.create(getFlagWrite(LLVMAMD64Flags.ZF), srcA, srcB);
                        break;
                    case I32:
                        out = LLVMAMD64BsrlNodeGen.create(getFlagWrite(LLVMAMD64Flags.ZF), srcA, srcB);
                        break;
                    case I64:
                        out = LLVMAMD64BsrqNodeGen.create(getFlagWrite(LLVMAMD64Flags.ZF), srcA, srcB);
                        break;
                    default:
                        throw invalidOperandType(dstType);
                }
                break;
            case "bsf":
                srcA = getOperandLoad(dstType, a);
                srcB = getOperandLoad(dstType, b);
                switch (getPrimitiveType(dstType)) {
                    case I16:
                        out = LLVMAMD64BsfwNodeGen.create(getFlagWrite(LLVMAMD64Flags.ZF), srcA, srcB);
                        break;
                    case I32:
                        out = LLVMAMD64BsflNodeGen.create(getFlagWrite(LLVMAMD64Flags.ZF), srcA, srcB);
                        break;
                    case I64:
                        out = LLVMAMD64BsfqNodeGen.create(getFlagWrite(LLVMAMD64Flags.ZF), srcA, srcB);
                        break;
                    default:
                        throw invalidOperandType(dstType);
                }
                break;
            case "xchg": {
                LLVMStatementNode res;
                XchgOperands operands = new XchgOperands(a, b, dstType);
                switch (getPrimitiveType(dstType)) {
                    case I8:
                        res = LLVMAMD64XchgbNodeGen.create(operands.dst, operands.srcA, operands.srcB);
                        break;
                    case I16:
                        res = LLVMAMD64XchgwNodeGen.create(operands.dst, operands.srcA, operands.srcB);
                        break;
                    case I32:
                        res = LLVMAMD64XchglNodeGen.create(operands.dst, operands.srcA, operands.srcB);
                        break;
                    case I64:
                        res = LLVMAMD64XchgqNodeGen.create(operands.dst, operands.srcA, operands.srcB);
                        break;
                    default:
                        throw invalidOperandType(dstType);
                }
                statements.add(res);
                return;
            }
            case "cmpxchg": {
                srcA = getOperandLoad(dstType, a);
                srcB = getOperandLoad(dstType, b);
                LLVMAMD64WriteValueNode dst1 = getStore(dstType, b);
                LLVMAMD64WriteValueNode dst2;
                LLVMExpressionNode accumulator;
                LLVMStatementNode res;
                if (dstType instanceof PointerType) {
                    dst2 = getRegisterStore("rax");
                    accumulator = getOperandLoad(new PointerType(PrimitiveType.I8), new AsmRegisterOperand("rax"));
                    res = LLVMAMD64CmpXchgqNodeGen.create(getUpdateCPAZSOFlagsNode(), dst1, dst2, accumulator, srcA, srcB);
                } else {
                    switch (getPrimitiveType(dstType)) {
                        case I8:
                            dst2 = getRegisterStore("al");
                            accumulator = getOperandLoad(PrimitiveType.I8, new AsmRegisterOperand("al"));
                            res = LLVMAMD64CmpXchgbNodeGen.create(getUpdateCPAZSOFlagsNode(), dst1, dst2, accumulator, srcA, srcB);
                            break;
                        case I16:
                            dst2 = getRegisterStore("ax");
                            accumulator = getOperandLoad(PrimitiveType.I16, new AsmRegisterOperand("ax"));
                            res = LLVMAMD64CmpXchgwNodeGen.create(getUpdateCPAZSOFlagsNode(), dst1, dst2, accumulator, srcA, srcB);
                            break;
                        case I32:
                            dst2 = getRegisterStore("eax");
                            accumulator = getOperandLoad(PrimitiveType.I32, new AsmRegisterOperand("eax"));
                            res = LLVMAMD64CmpXchglNodeGen.create(getUpdateCPAZSOFlagsNode(), dst1, dst2, accumulator, srcA, srcB);
                            break;
                        case I64:
                            dst2 = getRegisterStore("rax");
                            accumulator = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rax"));
                            res = LLVMAMD64CmpXchgqNodeGen.create(getUpdateCPAZSOFlagsNode(), dst1, dst2, accumulator, srcA, srcB);
                            break;
                        default:
                            throw invalidOperandType(dstType);
                    }
                }
                statements.add(res);
                return;
            }
            case "and":
                srcA = getOperandLoad(dstType, a);
                srcB = getOperandLoad(dstType, b);
                switch (getPrimitiveType(dstType)) {
                    case I8:
                        out = LLVMAMD64AndbNodeGen.create(getUpdatePZSFlagsNode(), srcA, srcB);
                        break;
                    case I16:
                        out = LLVMAMD64AndwNodeGen.create(getUpdatePZSFlagsNode(), srcA, srcB);
                        break;
                    case I32:
                        out = LLVMAMD64AndlNodeGen.create(getUpdatePZSFlagsNode(), srcA, srcB);
                        break;
                    case I64:
                        out = LLVMAMD64AndqNodeGen.create(getUpdatePZSFlagsNode(), srcA, srcB);
                        break;
                    default:
                        throw invalidOperandType(dstType);
                }
                break;
            case "or":
                srcA = getOperandLoad(dstType, a);
                srcB = getOperandLoad(dstType, b);
                switch (getPrimitiveType(dstType)) {
                    case I8:
                        out = LLVMAMD64OrbNodeGen.create(srcA, srcB);
                        break;
                    case I16:
                        out = LLVMAMD64OrwNodeGen.create(srcA, srcB);
                        break;
                    case I32:
                        out = LLVMAMD64OrlNodeGen.create(srcA, srcB);
                        break;
                    case I64:
                        out = LLVMAMD64OrqNodeGen.create(srcA, srcB);
                        break;
                    default:
                        throw invalidOperandType(dstType);
                }
                break;
            case "pmovmskb":
                srcA = getOperandLoad(getType(a), a);
                LLVMX86_ConversionNode.LLVMX86_Pmovmskb128 pmovmskb128 = LLVMX86_ConversionNodeFactory.LLVMX86_Pmovmskb128NodeGen.create(srcA);
                out = pmovmskb128;
                break;
            default:
                statements.add(LLVMUnsupportedInstructionNode.create(UnsupportedReason.INLINE_ASSEMBLER, operation));
                return;
        }
        statements.add(getOperandStore(dstType, dst, out));
    }

    private static PrimitiveKind getPrimitiveType(Type dstType) {
        if (dstType instanceof PrimitiveType) {
            return ((PrimitiveType) dstType).getPrimitiveKind();
        } else {
            throw invalidOperandType(dstType);
        }
    }

    void createBinaryOperation(String operation, AsmOperand a, AsmOperand b) {
        LLVMExpressionNode srcA;
        LLVMExpressionNode srcB;
        LLVMExpressionNode out;
        assert a != null && b != null;
        AsmOperand dst = b;
        Type dstType;
        char suffix = operation.charAt(operation.length() - 1);
        dstType = getPrimitiveTypeFromSuffix(suffix);
        srcB = getOperandLoad(dstType, b);
        if (isShiftOperation(operation)) {
            srcA = getOperandLoad(PrimitiveType.I8, a);
        } else {
            srcA = getOperandLoad(dstType, a);
        }
        switch (operation) {
            case "addb":
                out = LLVMAMD64AddbNodeGen.create(getUpdateCPZSOFlagsNode(), srcA, srcB);
                break;
            case "addw":
                out = LLVMAMD64AddwNodeGen.create(getUpdateCPZSOFlagsNode(), srcA, srcB);
                break;
            case "addl":
                out = LLVMAMD64AddlNodeGen.create(getUpdateCPZSOFlagsNode(), srcA, srcB);
                break;
            case "addq":
                out = LLVMAMD64AddqNodeGen.create(getUpdateCPZSOFlagsNode(), srcA, srcB);
                break;
            case "adcb":
                out = LLVMAMD64AdcbNodeGen.create(getUpdateCPZSOFlagsNode(), srcA, srcB, getFlag(LLVMAMD64Flags.CF));
                break;
            case "adcw":
                out = LLVMAMD64AdcwNodeGen.create(getUpdateCPZSOFlagsNode(), srcA, srcB, getFlag(LLVMAMD64Flags.CF));
                break;
            case "adcl":
                out = LLVMAMD64AdclNodeGen.create(getUpdateCPZSOFlagsNode(), srcA, srcB, getFlag(LLVMAMD64Flags.CF));
                break;
            case "adcq":
                out = LLVMAMD64AdcqNodeGen.create(getUpdateCPZSOFlagsNode(), srcA, srcB, getFlag(LLVMAMD64Flags.CF));
                break;
            case "subb":
                out = LLVMAMD64SubbNodeGen.create(srcB, srcA);
                break;
            case "subw":
                out = LLVMAMD64SubwNodeGen.create(srcB, srcA);
                break;
            case "subl":
                out = LLVMAMD64SublNodeGen.create(srcB, srcA);
                break;
            case "subq":
                out = LLVMAMD64SubqNodeGen.create(srcB, srcA);
                break;
            case "idivb":
                srcA = getOperandLoad(PrimitiveType.I8, a);
                srcB = getOperandLoad(PrimitiveType.I16, b);
                out = LLVMAMD64IdivbNodeGen.create(srcB, srcA);
                dst = new AsmRegisterOperand("ax");
                dstType = PrimitiveType.I16;
                break;
            case "idivw": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("ax"), getRegisterStore("dx"));
                LLVMExpressionNode high = getOperandLoad(PrimitiveType.I16, new AsmRegisterOperand("dx"));
                statements.add(LLVMAMD64IdivwNodeGen.create(res, high, srcB, srcA));
                return;
            }
            case "idivl": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("eax"), getRegisterStore("edx"));
                LLVMExpressionNode high = getOperandLoad(PrimitiveType.I32, new AsmRegisterOperand("edx"));
                statements.add(LLVMAMD64IdivlNodeGen.create(res, high, srcB, srcA));
                return;
            }
            case "idivq": {
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(getRegisterStore("rax"), getRegisterStore("rdx"));
                LLVMExpressionNode high = getOperandLoad(PrimitiveType.I32, new AsmRegisterOperand("rdx"));
                statements.add(LLVMAMD64IdivqNodeGen.create(res, high, srcB, srcA));
                return;
            }
            case "imulw": {
                LLVMAMD64WriteValueNode res = getRegisterStore(dstType, dst);
                statements.add(LLVMAMD64Imulw3NodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF), getFlagWrite(LLVMAMD64Flags.ZF),
                                getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), res, srcA, srcB));
                return;
            }
            case "imull": {
                LLVMAMD64WriteValueNode res = getRegisterStore(dstType, dst);
                statements.add(LLVMAMD64Imull3NodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF), getFlagWrite(LLVMAMD64Flags.ZF),
                                getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), res, srcA, srcB));
                return;
            }
            case "imulq": {
                LLVMAMD64WriteValueNode res = getRegisterStore(dstType, dst);
                statements.add(LLVMAMD64Imulq3NodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF), getFlagWrite(LLVMAMD64Flags.ZF),
                                getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), res, srcA, srcB));
                return;
            }
            case "movb":
            case "movw":
            case "movl":
            case "movq":
                out = srcA;
                break;
            case "movsbw":
                srcA = getOperandLoad(PrimitiveType.I8, a);
                out = CommonNodeFactory.createSignedCast(srcA, PrimitiveType.I16);
                break;
            case "movsbl":
                srcA = getOperandLoad(PrimitiveType.I8, a);
                out = CommonNodeFactory.createSignedCast(srcA, PrimitiveType.I32);
                break;
            case "movsbq":
                srcA = getOperandLoad(PrimitiveType.I8, a);
                out = CommonNodeFactory.createSignedCast(srcA, PrimitiveType.I64);
                break;
            case "movswl":
                srcA = getOperandLoad(PrimitiveType.I16, a);
                out = CommonNodeFactory.createSignedCast(srcA, PrimitiveType.I32);
                break;
            case "movswq":
                srcA = getOperandLoad(PrimitiveType.I16, a);
                out = CommonNodeFactory.createSignedCast(srcA, PrimitiveType.I64);
                break;
            case "movslq":
                srcA = getOperandLoad(PrimitiveType.I32, a);
                out = CommonNodeFactory.createSignedCast(srcA, PrimitiveType.I64);
                break;
            case "movzbw":
                srcA = getOperandLoad(PrimitiveType.I8, a);
                out = CommonNodeFactory.createUnsignedCast(srcA, PrimitiveType.I16);
                break;
            case "movzbl":
                srcA = getOperandLoad(PrimitiveType.I8, a);
                out = CommonNodeFactory.createUnsignedCast(srcA, PrimitiveType.I32);
                break;
            case "movzbq":
                srcA = getOperandLoad(PrimitiveType.I8, a);
                out = CommonNodeFactory.createUnsignedCast(srcA, PrimitiveType.I64);
                break;
            case "movzwl":
                srcA = getOperandLoad(PrimitiveType.I16, a);
                out = CommonNodeFactory.createUnsignedCast(srcA, PrimitiveType.I32);
                break;
            case "movzwq":
                srcA = getOperandLoad(PrimitiveType.I16, a);
                out = CommonNodeFactory.createUnsignedCast(srcA, PrimitiveType.I64);
                break;
            case "salb":
                out = LLVMAMD64SalbNodeGen.create(srcB, srcA);
                break;
            case "salw":
                out = LLVMAMD64SalwNodeGen.create(srcB, srcA);
                break;
            case "sall":
                out = LLVMAMD64SallNodeGen.create(srcB, srcA);
                break;
            case "salq":
                out = LLVMAMD64SalqNodeGen.create(srcB, srcA);
                break;
            case "sarb":
                out = LLVMAMD64SarbNodeGen.create(srcB, srcA);
                break;
            case "sarw":
                out = LLVMAMD64SarwNodeGen.create(srcB, srcA);
                break;
            case "sarl":
                out = LLVMAMD64SarlNodeGen.create(srcB, srcA);
                break;
            case "sarq":
                out = LLVMAMD64SarqNodeGen.create(srcB, srcA);
                break;
            case "shlb":
                out = LLVMAMD64ShlbNodeGen.create(srcB, srcA);
                break;
            case "shlw":
                out = LLVMAMD64ShlwNodeGen.create(srcB, srcA);
                break;
            case "shll":
                out = LLVMAMD64ShllNodeGen.create(srcB, srcA);
                break;
            case "shlq":
                out = LLVMAMD64ShlqNodeGen.create(srcB, srcA);
                break;
            case "shrb":
                out = LLVMAMD64ShrbNodeGen.create(srcB, srcA);
                break;
            case "shrw":
                out = LLVMAMD64ShrwNodeGen.create(srcB, srcA);
                break;
            case "shrl":
                out = LLVMAMD64ShrlNodeGen.create(srcB, srcA);
                break;
            case "shrq":
                out = LLVMAMD64ShrqNodeGen.create(srcB, srcA);
                break;
            case "rolb":
                out = LLVMAMD64RolbNodeGen.create(srcB, srcA);
                break;
            case "rolw":
                out = LLVMAMD64RolwNodeGen.create(srcB, srcA);
                break;
            case "roll":
                out = LLVMAMD64RollNodeGen.create(srcB, srcA);
                break;
            case "rolq":
                out = LLVMAMD64RolqNodeGen.create(srcB, srcA);
                break;
            case "rorb":
                out = LLVMAMD64RorbNodeGen.create(srcB, srcA);
                break;
            case "rorw":
                out = LLVMAMD64RorwNodeGen.create(srcB, srcA);
                break;
            case "rorl":
                out = LLVMAMD64RorlNodeGen.create(srcB, srcA);
                break;
            case "rorq":
                out = LLVMAMD64RorqNodeGen.create(srcB, srcA);
                break;
            case "andb":
                out = LLVMAMD64AndbNodeGen.create(getUpdatePZSFlagsNode(), srcA, srcB);
                break;
            case "andw":
                out = LLVMAMD64AndwNodeGen.create(getUpdatePZSFlagsNode(), srcA, srcB);
                break;
            case "andl":
                out = LLVMAMD64AndlNodeGen.create(getUpdatePZSFlagsNode(), srcA, srcB);
                break;
            case "andq":
                out = LLVMAMD64AndqNodeGen.create(getUpdatePZSFlagsNode(), srcA, srcB);
                break;
            case "orb":
                out = LLVMAMD64OrbNodeGen.create(srcA, srcB);
                break;
            case "orw":
                out = LLVMAMD64OrwNodeGen.create(srcA, srcB);
                break;
            case "orl":
                out = LLVMAMD64OrlNodeGen.create(srcA, srcB);
                break;
            case "orq":
                out = LLVMAMD64OrqNodeGen.create(srcA, srcB);
                break;
            case "xchgb": {
                XchgOperands operands = new XchgOperands(a, b, dstType);
                statements.add(LLVMAMD64XchgbNodeGen.create(operands.dst, operands.srcA, operands.srcB));
                return;
            }
            case "xchgw": {
                XchgOperands operands = new XchgOperands(a, b, dstType);
                statements.add(LLVMAMD64XchgwNodeGen.create(operands.dst, operands.srcA, operands.srcB));
                return;
            }
            case "xchgl": {
                XchgOperands operands = new XchgOperands(a, b, dstType);
                statements.add(LLVMAMD64XchglNodeGen.create(operands.dst, operands.srcA, operands.srcB));
                return;
            }
            case "xchgq": {
                XchgOperands operands = new XchgOperands(a, b, dstType);
                statements.add(LLVMAMD64XchgqNodeGen.create(operands.dst, operands.srcA, operands.srcB));
                return;
            }
            case "cmpb":
                statements.add(LLVMAMD64CmpbNodeGen.create(getUpdateCPAZSOFlagsNode(), srcB, srcA));
                return;
            case "cmpw":
                statements.add(LLVMAMD64CmpwNodeGen.create(getUpdateCPAZSOFlagsNode(), srcB, srcA));
                return;
            case "cmpl":
                statements.add(LLVMAMD64CmplNodeGen.create(getUpdateCPAZSOFlagsNode(), srcB, srcA));
                return;
            case "cmpq":
                statements.add(LLVMAMD64CmpqNodeGen.create(getUpdateCPAZSOFlagsNode(), srcB, srcA));
                return;
            case "cmpxchgb": {
                LLVMAMD64WriteValueNode dst1 = getStore(dstType, b);
                LLVMAMD64WriteValueNode dst2 = getRegisterStore("al");
                LLVMExpressionNode accumulator = getOperandLoad(PrimitiveType.I8, new AsmRegisterOperand("al"));
                statements.add(LLVMAMD64CmpXchgbNodeGen.create(getUpdateCPAZSOFlagsNode(), dst1, dst2, accumulator, srcA, srcB));
                return;
            }
            case "cmpxchgw": {
                LLVMAMD64WriteValueNode dst1 = getStore(dstType, b);
                LLVMAMD64WriteValueNode dst2 = getRegisterStore("ax");
                LLVMExpressionNode accumulator = getOperandLoad(PrimitiveType.I16, new AsmRegisterOperand("ax"));
                statements.add(LLVMAMD64CmpXchgwNodeGen.create(getUpdateCPAZSOFlagsNode(), dst1, dst2, accumulator, srcA, srcB));
                return;
            }
            case "cmpxchgl": {
                LLVMAMD64WriteValueNode dst1 = getStore(dstType, b);
                LLVMAMD64WriteValueNode dst2 = getRegisterStore("eax");
                LLVMExpressionNode accumulator = getOperandLoad(PrimitiveType.I32, new AsmRegisterOperand("eax"));
                statements.add(LLVMAMD64CmpXchglNodeGen.create(getUpdateCPAZSOFlagsNode(), dst1, dst2, accumulator, srcA, srcB));
                return;
            }
            case "cmpxchgq": {
                LLVMAMD64WriteValueNode dst1 = getStore(dstType, b);
                LLVMAMD64WriteValueNode dst2 = getRegisterStore("rax");
                LLVMExpressionNode accumulator = getOperandLoad(PrimitiveType.I64, new AsmRegisterOperand("rax"));
                statements.add(LLVMAMD64CmpXchgqNodeGen.create(getUpdateCPAZSOFlagsNode(), dst1, dst2, accumulator, srcA, srcB));
                return;
            }
            case "xaddb": {
                LLVMAMD64WriteValueNode dst1 = getRegisterStore(PrimitiveType.I8, a);
                LLVMAMD64WriteValueNode dst2 = getStore(dstType, dst);
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(dst1, dst2);
                statements.add(LLVMAMD64XaddbNodeGen.create(getUpdateCPZSOFlagsNode(), res, srcA, srcB));
                return;
            }
            case "xaddw": {
                LLVMAMD64WriteValueNode dst1 = getRegisterStore(PrimitiveType.I16, a);
                LLVMAMD64WriteValueNode dst2 = getStore(dstType, dst);
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(dst1, dst2);
                statements.add(LLVMAMD64XaddwNodeGen.create(getUpdateCPZSOFlagsNode(), res, srcA, srcB));
                return;
            }
            case "xaddl": {
                LLVMAMD64WriteValueNode dst1 = getRegisterStore(PrimitiveType.I32, a);
                LLVMAMD64WriteValueNode dst2 = getStore(dstType, dst);
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(dst1, dst2);
                statements.add(LLVMAMD64XaddlNodeGen.create(getUpdateCPZSOFlagsNode(), res, srcA, srcB));
                return;
            }
            case "xaddq": {
                LLVMAMD64WriteValueNode dst1 = getRegisterStore(PrimitiveType.I64, a);
                LLVMAMD64WriteValueNode dst2 = getStore(dstType, dst);
                LLVMAMD64WriteTupelNode res = LLVMAMD64WriteTupelNodeGen.create(dst1, dst2);
                statements.add(LLVMAMD64XaddqNodeGen.create(getUpdateCPZSOFlagsNode(), res, srcA, srcB));
                return;
            }
            case "xorb":
                out = LLVMAMD64XorbNodeGen.create(srcA, srcB);
                break;
            case "xorw":
                out = LLVMAMD64XorwNodeGen.create(srcA, srcB);
                break;
            case "xorl":
                out = LLVMAMD64XorlNodeGen.create(srcA, srcB);
                break;
            case "xorq":
                out = LLVMAMD64XorqNodeGen.create(srcA, srcB);
                break;
            case "bsrw":
                out = LLVMAMD64BsrwNodeGen.create(getFlagWrite(LLVMAMD64Flags.ZF), srcA, srcB);
                break;
            case "bsrl":
                out = LLVMAMD64BsrlNodeGen.create(getFlagWrite(LLVMAMD64Flags.ZF), srcA, srcB);
                break;
            case "bsrq":
                out = LLVMAMD64BsrqNodeGen.create(getFlagWrite(LLVMAMD64Flags.ZF), srcA, srcB);
                break;
            case "bsfw":
                out = LLVMAMD64BsfwNodeGen.create(getFlagWrite(LLVMAMD64Flags.ZF), srcA, srcB);
                break;
            case "bsfl":
                out = LLVMAMD64BsflNodeGen.create(getFlagWrite(LLVMAMD64Flags.ZF), srcA, srcB);
                break;
            case "bsfq":
                out = LLVMAMD64BsfqNodeGen.create(getFlagWrite(LLVMAMD64Flags.ZF), srcA, srcB);
                break;
            default:
                statements.add(LLVMUnsupportedInstructionNode.create(UnsupportedReason.INLINE_ASSEMBLER, operation));
                return;
        }
        statements.add(getOperandStore(dstType, dst, out));
    }

    void createTernaryOperation(String operation, AsmOperand a, AsmOperand b, AsmOperand c) {
        AsmOperand dst = c;
        LLVMExpressionNode srcA;
        LLVMExpressionNode srcB;
        Type dstType;
        assert a != null && b != null && c != null;
        char suffix = operation.charAt(operation.length() - 1);
        dstType = getPrimitiveTypeFromSuffix(suffix);
        srcB = getOperandLoad(dstType, b);
        srcA = getOperandLoad(dstType, a);
        LLVMAMD64WriteValueNode res = getRegisterStore(dstType, dst);
        switch (operation) {
            case "imulw":
                statements.add(LLVMAMD64Imulw3NodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF), getFlagWrite(LLVMAMD64Flags.ZF),
                                getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), res, srcA, srcB));
                return;
            case "imull":
                statements.add(LLVMAMD64Imull3NodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF), getFlagWrite(LLVMAMD64Flags.ZF),
                                getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), res, srcA, srcB));
                return;
            case "imulq":
                statements.add(LLVMAMD64Imulq3NodeGen.create(getFlagWrite(LLVMAMD64Flags.CF), getFlagWrite(LLVMAMD64Flags.PF), getFlagWrite(LLVMAMD64Flags.AF), getFlagWrite(LLVMAMD64Flags.ZF),
                                getFlagWrite(LLVMAMD64Flags.SF), getFlagWrite(LLVMAMD64Flags.OF), res, srcA, srcB));
                return;
            default:
                statements.add(LLVMUnsupportedInstructionNode.create(UnsupportedReason.INLINE_ASSEMBLER, operation));
                return;
        }
    }

    void addFrameSlot(String reg, Type type) {
        if (!registers.contains(reg)) {
            registers.add(reg);
            FrameSlotKind kind = computeFrameSlotKind(type);
            this.frameDescriptor.addFrameSlot(reg, type, kind);
        }
    }

    private static FrameSlotKind computeFrameSlotKind(Type type) {
        if (type instanceof PrimitiveType) {
            PrimitiveKind primitiveKind = ((PrimitiveType) type).getPrimitiveKind();
            switch (primitiveKind) {
                case I1:
                case I8:
                    return FrameSlotKind.Byte;
                case I32:
                    return FrameSlotKind.Int;
                case I64:
                    return FrameSlotKind.Long;
            }
        } else if (type instanceof PointerType) {
            return FrameSlotKind.Object;
        }

        throw new AsmParseException("unexpected type: " + type);
    }

    private static PrimitiveKind getPrimitiveKind(Argument arg) {
        PrimitiveKind primitiveKind;
        if (arg.getType() instanceof PrimitiveType) {
            primitiveKind = ((PrimitiveType) arg.getType()).getPrimitiveKind();
        } else {
            throw new AsmParseException("cannot handle return type " + arg.getType());
        }
        return primitiveKind;
    }

    private void getArguments() {
        LLVMStoreNode[] writeNodes = LLVMStoreNode.NO_STORES;
        LLVMExpressionNode[] valueNodes = LLVMExpressionNode.NO_EXPRESSIONS;
        if (retType instanceof StructureType) {
            writeNodes = new LLVMStoreNode[retTypes.length];
            valueNodes = new LLVMExpressionNode[retTypes.length];
        }

        Set<String> todoRegisters = new HashSet<>(registers);
        for (Argument arg : argInfo) {
            // output register
            if (arg.isOutput()) {
                FrameSlot slot = null;
                if (arg.isRegister()) {
                    slot = getRegisterSlot(arg.getRegister());
                    LLVMExpressionNode register = LLVMAMD64ReadRegisterNodeGen.create(slot);
                    if (retType instanceof StructureType) {
                        assert retTypes[arg.getOutIndex()] == arg.getType();
                        if (arg.getType() instanceof PointerType) {
                            valueNodes[arg.getOutIndex()] = LLVMToAddressNodeGen.create(register);
                            writeNodes[arg.getOutIndex()] = LLVMPointerStoreNodeGen.create(null, null);
                        } else {
                            PrimitiveKind primitiveKind = getPrimitiveKind(arg);
                            switch (primitiveKind) {
                                case I8:
                                    valueNodes[arg.getOutIndex()] = CommonNodeFactory.createSignedCast(register, PrimitiveType.I8);
                                    writeNodes[arg.getOutIndex()] = LLVMI8StoreNodeGen.create(null, null);
                                    break;
                                case I16:
                                    valueNodes[arg.getOutIndex()] = CommonNodeFactory.createSignedCast(register, PrimitiveType.I16);
                                    writeNodes[arg.getOutIndex()] = LLVMI16StoreNodeGen.create(null, null);
                                    break;
                                case I32:
                                    valueNodes[arg.getOutIndex()] = CommonNodeFactory.createSignedCast(register, PrimitiveType.I32);
                                    writeNodes[arg.getOutIndex()] = LLVMI32StoreNodeGen.create(null, null);
                                    break;
                                case I64:
                                    valueNodes[arg.getOutIndex()] = register;
                                    writeNodes[arg.getOutIndex()] = LLVMI64StoreNodeGen.create(null, null);
                                    break;
                                default:
                                    throw invalidOperandType(arg.getType());
                            }
                        }
                    } else {
                        result = castResult(register);
                    }
                } else {
                    assert arg.isMemory();
                    slot = getArgumentSlot(arg.getIndex(), argTypes.get(arg.getOutIndex()));
                    LLVMExpressionNode argnode = LLVMArgNodeGen.create(arg.getOutIndex());
                    arguments.add(LLVMWritePointerNodeGen.create(slot, argnode));
                }
            }

            // input register
            if (arg.isInput()) {
                FrameSlot slot = null;
                if (arg.isRegister()) {
                    String reg = arg.isAnonymous() ? arg.getRegister() : AsmRegisterOperand.getBaseRegister(arg.getRegister());
                    slot = getRegisterSlot(reg);
                    todoRegisters.remove(reg);
                    LLVMExpressionNode argnode = LLVMArgNodeGen.create(arg.getInIndex());
                    if (argTypes.get(arg.getInIndex()) instanceof PointerType) {
                        arguments.add(LLVMWritePointerNodeGen.create(slot, argnode));
                    } else if (argTypes.get(arg.getInIndex()) instanceof VectorType) {
                        arguments.add(LLVMWriteNodeFactory.LLVMWriteVectorNodeGen.create(slot, argnode));
                    } else {
                        LLVMExpressionNode node = CommonNodeFactory.createSignedCast(argnode, PrimitiveType.I64);
                        arguments.add(LLVMWriteI64NodeGen.create(slot, node));
                    }
                }
                slot = getArgumentSlot(arg.getIndex(), argTypes.get(arg.getInIndex()));
                LLVMExpressionNode argnode = LLVMArgNodeGen.create(arg.getInIndex());
                if (arg.getType() instanceof PrimitiveType) {
                    LLVMExpressionNode node = CommonNodeFactory.createSignedCast(argnode, PrimitiveType.I64);
                    arguments.add(LLVMWriteI64NodeGen.create(slot, node));
                } else if (arg.getType() instanceof VectorType) {
                    arguments.add(LLVMWriteNodeFactory.LLVMWriteVectorNodeGen.create(slot, argnode));
                } else if (arg.getType() instanceof PointerType) {
                    arguments.add(LLVMWritePointerNodeGen.create(slot, argnode));
                } else {
                    throw invalidOperandType(arg.getType());
                }
            }
        }

        if (retType instanceof StructureType) {
            LLVMExpressionNode addrArg = LLVMArgNodeGen.create(1);
            FrameSlot slot = frameDescriptor.addFrameSlot("returnValue", null, FrameSlotKind.Object);
            LLVMWritePointerNode writeAddr = LLVMWritePointerNodeGen.create(slot, addrArg);
            statements.add(writeAddr);
            LLVMExpressionNode addr = LLVMAddressReadNodeGen.create(slot);
            this.result = StructLiteralNodeGen.create(retOffsets, writeNodes, valueNodes, addr);
        }

        todoRegisters.remove("rsp"); // rsp is initialized to stack pointer; ignore it here
        // initialize registers
        for (String register : todoRegisters) {
            if (register.startsWith("$")) {
                continue;
            }
            LLVMExpressionNode node = LLVMAMD64I64NodeGen.create(0);
            FrameSlot slot = getRegisterSlot(register);
            arguments.add(LLVMWriteI64NodeGen.create(slot, node));
        }

        // initialize flags
        LLVMExpressionNode zero = LLVMAMD64I1NodeGen.create(false);
        arguments.add(LLVMWriteI1NodeGen.create(getFlagSlot(LLVMAMD64Flags.CF), zero));
        arguments.add(LLVMWriteI1NodeGen.create(getFlagSlot(LLVMAMD64Flags.PF), zero));
        arguments.add(LLVMWriteI1NodeGen.create(getFlagSlot(LLVMAMD64Flags.AF), zero));
        arguments.add(LLVMWriteI1NodeGen.create(getFlagSlot(LLVMAMD64Flags.ZF), zero));
        arguments.add(LLVMWriteI1NodeGen.create(getFlagSlot(LLVMAMD64Flags.SF), zero));
        arguments.add(LLVMWriteI1NodeGen.create(getFlagSlot(LLVMAMD64Flags.OF), zero));

        // copy stack pointer
        LLVMExpressionNode stackPointer = LLVMArgNodeGen.create(0);
        FrameSlot stackSlot = frameDescriptor.addFrameSlot(LLVMStack.FRAME_ID);
        frameDescriptor.setFrameSlotKind(stackSlot, FrameSlotKind.Object);
        arguments.add(LLVMWritePointerNodeGen.create(frameDescriptor.findFrameSlot(LLVMStack.FRAME_ID), stackPointer));

        arguments.add(LLVMWritePointerNodeGen.create(getRegisterSlot("rsp"), stackPointer));

        assert retType instanceof VoidType || retType != null;
    }

    private LLVMExpressionNode castResult(LLVMExpressionNode register) {
        if (retType instanceof PointerType) {
            return LLVMToAddressNodeGen.create(register);
        }
        return CommonNodeFactory.createSignedCast(register, retType);
    }

    private boolean isLeaPointer(AsmOperand operand) {
        if (operand instanceof AsmArgumentOperand) {
            AsmArgumentOperand op = (AsmArgumentOperand) operand;
            Argument info = argInfo.get(op.getIndex());
            if (info.isMemory()) {
                return true;
            } else {
                throw new AsmParseException("not a pointer");
            }
        } else if (operand instanceof AsmMemoryOperand) {
            AsmMemoryOperand op = (AsmMemoryOperand) operand;
            AsmOperand base = op.getBase();
            AsmOperand offset = op.getOffset();
            return base != null || offset != null;
        } else {
            throw new AsmParseException("unsupported operand: " + operand);
        }
    }

    private LLVMExpressionNode getOperandAddress(AsmOperand operand) {
        return getOperandAddress(operand.getType(), operand);
    }

    private LLVMExpressionNode getOperandAddress(Type type, AsmOperand operand) {
        if (operand instanceof AsmRegisterOperand) {
            AsmRegisterOperand op = (AsmRegisterOperand) operand;
            FrameSlot frame = getRegisterSlot(op.getBaseRegister());
            if (type instanceof PointerType) {
                return LLVMAddressReadNodeGen.create(frame);
            } else {
                throw new AsmParseException("not a pointer");
            }
        } else if (operand instanceof AsmArgumentOperand) {
            AsmArgumentOperand op = (AsmArgumentOperand) operand;
            Argument info = argInfo.get(op.getIndex());
            FrameSlot frame = getArgumentSlot(op.getIndex(), type);
            if (info.isMemory()) {
                return LLVMAddressReadNodeGen.create(frame);
            } else {
                throw new AsmParseException("not a pointer");
            }
        } else if (operand instanceof AsmMemoryOperand) {
            AsmMemoryOperand op = (AsmMemoryOperand) operand;
            int displacement = op.getDisplacement();
            AsmOperand base = op.getBase();
            AsmOperand offset = op.getOffset();
            int shift = op.getShift();
            LLVMExpressionNode baseAddress;
            assert op.getSegment() == null || "%fs".equals(op.getSegment());
            LLVMExpressionNode segment = null;
            if (op.getSegment() != null) {
                segment = LLVMAMD64GetTlsNodeGen.create();
            }
            if (base != null) {
                baseAddress = getOperandLoad(new PointerType(type), base);
            } else if (offset != null) {
                LLVMExpressionNode offsetNode = getOperandLoad(null, offset);
                if (op.getSegment() != null) {
                    return LLVMAMD64AddressOffsetComputationNodeGen.create(displacement, shift, segment, offsetNode);
                } else {
                    return LLVMAMD64AddressNoBaseOffsetComputationNodeGen.create(displacement, shift, offsetNode);
                }
            } else if (op.getSegment() != null) {
                return LLVMAMD64AddressDisplacementComputationNodeGen.create(displacement, segment);
            } else {
                if (type instanceof PrimitiveType) {
                    switch (((PrimitiveType) type).getPrimitiveKind()) {
                        case I16:
                            return LLVMAMD64I16NodeGen.create((short) displacement);
                        case I32:
                            return LLVMAMD64I32NodeGen.create(displacement);
                        case I64:
                            return LLVMAMD64I64NodeGen.create(displacement);
                        default:
                            throw new AsmParseException("unknown type: " + type);
                    }
                } else if (type instanceof PointerType) {
                    return LLVMAMD64I64NodeGen.create(displacement);
                } else {
                    throw new AsmParseException("invalid type: " + type);
                }
            }
            LLVMExpressionNode address;
            if (offset == null) {
                address = LLVMAMD64AddressDisplacementComputationNodeGen.create(displacement, baseAddress);
            } else {
                LLVMExpressionNode offsetNode = getOperandLoad(null, offset);
                address = LLVMAMD64AddressOffsetComputationNodeGen.create(displacement, shift, baseAddress, offsetNode);
            }
            if (op.getSegment() != null) {
                address = LLVMAMD64AddressSegmentComputationNodeGen.create(address, segment);
            }
            return address;
        } else {
            throw new AsmParseException("unsupported operand: " + operand);
        }
    }

    private LLVMExpressionNode getOperandLoad(Type typeHint, AsmOperand operand) {
        Type type = typeHint == null ? operand.getType() : typeHint;
        if (operand instanceof AsmRegisterOperand) {
            AsmRegisterOperand op = (AsmRegisterOperand) operand;
            FrameSlot frame = getRegisterSlot(op.getBaseRegister());
            LLVMExpressionNode register = LLVMAMD64ReadRegisterNodeGen.create(frame);
            int shift = op.getShift();
            assert type instanceof PointerType || type == op.getType();
            if (type instanceof PointerType) {
                switch (((PrimitiveType) op.getType()).getPrimitiveKind()) {
                    case I8:
                        return CommonNodeFactory.createSignedCast(register, PrimitiveType.I8);
                    case I16:
                        return CommonNodeFactory.createSignedCast(register, PrimitiveType.I16);
                    case I32:
                        return CommonNodeFactory.createSignedCast(register, PrimitiveType.I32);
                    case I64:
                        return LLVMAMD64ReadAddressNodeGen.create(frame);
                    default:
                        throw unsupportedOperandType(type);
                }
            }
            switch (((PrimitiveType) op.getType()).getPrimitiveKind()) {
                case I8:
                    return LLVMAMD64I64ToI8NodeGen.create(shift, register);
                case I16:
                    return CommonNodeFactory.createSignedCast(register, PrimitiveType.I16);
                case I32:
                    return CommonNodeFactory.createSignedCast(register, PrimitiveType.I32);
                case I64:
                    return register;
                default:
                    throw unsupportedOperandType(type);
            }
        } else if (operand instanceof AsmImmediateOperand) {
            AsmImmediateOperand op = (AsmImmediateOperand) operand;
            if (op.isLabel()) {
                throw new AsmParseException("labels not supported");
            } else {
                switch (((PrimitiveType) type).getPrimitiveKind()) {
                    case I8:
                        return LLVMAMD64I8NodeGen.create((byte) op.getValue());
                    case I16:
                        return LLVMAMD64I16NodeGen.create((short) op.getValue());
                    case I32:
                        return LLVMAMD64I32NodeGen.create((int) op.getValue());
                    case I64:
                        return LLVMAMD64I64NodeGen.create(op.getValue());
                    default:
                        throw unsupportedOperandType(type);
                }
            }
        } else if (operand instanceof AsmArgumentOperand) {
            AsmArgumentOperand op = (AsmArgumentOperand) operand;
            Argument info = argInfo.get(op.getIndex());
            FrameSlot frame = getArgumentSlot(op.getIndex(), type);
            if (info.isMemory()) {
                if (type instanceof PointerType) {
                    return LLVMPointerDirectLoadNodeGen.create(LLVMAddressReadNodeGen.create(frame));
                }
                switch (((PrimitiveType) type).getPrimitiveKind()) {
                    case I8:
                        return LLVMI8LoadNodeGen.create(LLVMAddressReadNodeGen.create(frame));
                    case I16:
                        return LLVMI16LoadNodeGen.create(LLVMAddressReadNodeGen.create(frame));
                    case I32:
                        return LLVMI32LoadNodeGen.create(LLVMAddressReadNodeGen.create(frame));
                    case I64:
                        return LLVMI64LoadNodeGen.create(LLVMAddressReadNodeGen.create(frame));
                    default:
                        throw unsupportedOperandType(type);
                }
            } else if (info.isRegister()) {
                frame = getRegisterSlot(info.getRegister());
                if (type instanceof PointerType) {
                    return LLVMAMD64ReadAddressNodeGen.create(frame);
                }
                LLVMExpressionNode register = LLVMAMD64ReadRegisterNodeGen.create(frame);
                return CommonNodeFactory.createSignedCast(register, type);
            } else { // constraint "0"-"9"
                if (type instanceof PointerType) {
                    return LLVMAMD64ReadAddressNodeGen.create(frame);
                }
                LLVMExpressionNode register = LLVMAMD64ReadRegisterNodeGen.create(frame);
                PrimitiveType primitiveType = (PrimitiveType) type;
                return CommonNodeFactory.createSignedCast(register, primitiveType);
            }
        } else if (operand instanceof AsmMemoryOperand) {
            LLVMExpressionNode address = getOperandAddress(operand);
            LLVMExpressionNode addr = LLVMToAddressNodeGen.create(address);
            if (type instanceof PrimitiveType) {
                switch (((PrimitiveType) type).getPrimitiveKind()) {
                    case I8:
                        return LLVMI8LoadNodeGen.create(addr);
                    case I16:
                        return LLVMI16LoadNodeGen.create(addr);
                    case I32:
                        return LLVMI32LoadNodeGen.create(addr);
                    case I64:
                        return LLVMI64LoadNodeGen.create(addr);
                    default:
                        throw unsupportedOperandType(type);
                }
            } else if (type instanceof PointerType) {
                return LLVMPointerDirectLoadNodeGen.create(addr);
            } else {
                throw unsupportedOperandType(type);
            }
        }
        throw new AsmParseException("unsupported operand: " + operand);
    }

    private LLVMStatementNode getOperandStore(Type type, AsmOperand operand, LLVMExpressionNode from) {
        if (operand instanceof AsmRegisterOperand) {
            AsmRegisterOperand op = (AsmRegisterOperand) operand;
            FrameSlot frame = getRegisterSlot(op.getBaseRegister());
            LLVMExpressionNode register = LLVMAMD64ReadRegisterNodeGen.create(frame);
            int shift = op.getShift();
            LLVMExpressionNode out = null;
            assert (type instanceof PointerType && op.getType() == PrimitiveType.I64) || (op.getType() instanceof PointerType && type == PrimitiveType.I64) ||
                            (type == op.getType());
            switch (((PrimitiveType) op.getType()).getPrimitiveKind()) {
                case I8:
                    out = LLVMI8ToR64NodeGen.create(shift, register, from);
                    break;
                case I16:
                    out = LLVMI16ToR64NodeGen.create(register, from);
                    break;
                case I32:
                    out = LLVMI32ToR64NodeGen.create(from);
                    break;
                case I64:
                    out = from;
                    break;
                default:
                    throw new AsmParseException("unsupported operand type: " + op.getType());
            }
            return LLVMWriteI64NodeGen.create(frame, out);
        } else if (operand instanceof AsmArgumentOperand) {
            AsmArgumentOperand op = (AsmArgumentOperand) operand;
            Argument info = argInfo.get(op.getIndex());
            if (info.isMemory()) {
                LLVMExpressionNode address = info.getAddress();
                if (type instanceof PrimitiveType) {
                    switch (((PrimitiveType) type).getPrimitiveKind()) {
                        case I8:
                            return LLVMI8StoreNodeGen.create(address, from);
                        case I16:
                            return LLVMI16StoreNodeGen.create(address, from);
                        case I32:
                            return LLVMI32StoreNodeGen.create(address, from);
                        case I64:
                            return LLVMI64StoreNodeGen.create(address, from);
                        default:
                            throw unsupportedOperandType(type);
                    }
                } else {
                    throw unsupportedOperandType(type);
                }
            } else if (info.isRegister()) {
                FrameSlot frame = getRegisterSlot(info.getRegister());
                LLVMExpressionNode register = LLVMAMD64ReadRegisterNodeGen.create(frame);
                LLVMExpressionNode out = null;
                if (type instanceof PointerType || info.getType() instanceof PointerType) {
                    return LLVMAMD64WriteAddressRegisterNodeGen.create(from, frame);
                }
                switch (((PrimitiveType) type).getPrimitiveKind()) {
                    case I8:
                        out = LLVMI8ToR64NodeGen.create(0, register, from);
                        break;
                    case I16:
                        out = LLVMI16ToR64NodeGen.create(register, from);
                        break;
                    case I32:
                        out = LLVMI32ToR64NodeGen.create(from);
                        break;
                    case I64:
                        out = from;
                        break;
                    default:
                        throw unsupportedOperandType(type);
                }
                return LLVMWriteI64NodeGen.create(frame, out);
            } else {
                throw new AssertionError("this should not happen; " + info);
            }
        } else if (operand instanceof AsmMemoryOperand) {
            LLVMExpressionNode address = getOperandAddress(operand);
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case I8:
                    return LLVMI8StoreNodeGen.create(address, from);
                case I16:
                    return LLVMI16StoreNodeGen.create(address, from);
                case I32:
                    return LLVMI32StoreNodeGen.create(address, from);
                case I64:
                    return LLVMI64StoreNodeGen.create(address, from);
                default:
                    throw unsupportedOperandType(type);
            }
        }
        throw unsupportedOperandType(operand.getType());
    }

    private LLVMAMD64Target getTarget(Type type, AsmOperand operand) {
        if (operand instanceof AsmRegisterOperand) {
            AsmRegisterOperand op = (AsmRegisterOperand) operand;
            FrameSlot frame = getRegisterSlot(op.getBaseRegister());
            int shift = op.getShift();
            assert type == op.getType();
            switch (((PrimitiveType) op.getType()).getPrimitiveKind()) {
                case I8:
                    return new LLVMAMD64Target(frame, shift);
                case I16:
                case I32:
                case I64:
                    return new LLVMAMD64Target(frame);
                default:
                    throw unsupportedOperandType(op.getType());
            }
        } else if (operand instanceof AsmArgumentOperand) {
            AsmArgumentOperand op = (AsmArgumentOperand) operand;
            Argument info = argInfo.get(op.getIndex());
            if (info.isMemory()) {
                LLVMExpressionNode address = info.getAddress();
                if (type instanceof PointerType) {
                    return new LLVMAMD64Target(address);
                }
                switch (((PrimitiveType) type).getPrimitiveKind()) {
                    case I8:
                    case I16:
                    case I32:
                    case I64:
                        return new LLVMAMD64Target(address);
                    default:
                        throw unsupportedOperandType(type);
                }
            } else if (info.isRegister()) {
                FrameSlot frame = getRegisterSlot(info.getRegister());
                if (type instanceof PointerType || info.getType() instanceof PointerType) {
                    return new LLVMAMD64Target(frame);
                }
                switch (((PrimitiveType) type).getPrimitiveKind()) {
                    case I8:
                    case I16:
                    case I32:
                    case I64:
                        return new LLVMAMD64Target(frame);
                    default:
                        throw unsupportedOperandType(type);
                }
            } else {
                throw new AssertionError("this should not happen; " + info);
            }
        }
        throw unsupportedOperandType(operand.getType());
    }

    private LLVMAMD64Target getRegisterTarget(Type type, AsmOperand operand) {
        if (operand instanceof AsmRegisterOperand) {
            AsmRegisterOperand op = (AsmRegisterOperand) operand;
            return getRegisterTarget(type, op.getRegister());
        } else if (operand instanceof AsmArgumentOperand) {
            AsmArgumentOperand op = (AsmArgumentOperand) operand;
            Argument info = argInfo.get(op.getIndex());
            if (info.isRegister()) {
                return getRegisterTarget(type, info.getRegister());
            } else {
                throw new AsmParseException("unsupported operand: " + info);
            }
        }
        throw unsupportedOperandType(operand.getType());
    }

    private LLVMAMD64Target getRegisterTarget(String name) {
        AsmRegisterOperand op = new AsmRegisterOperand(name);
        Type type = name.startsWith(TEMP_REGISTER_PREFIX) ? PrimitiveType.I64 : op.getType();
        return getRegisterTarget(type, name);
    }

    private LLVMAMD64Target getRegisterTarget(Type type, String name) {
        AsmRegisterOperand op = new AsmRegisterOperand(name);
        FrameSlot frame = getRegisterSlot(name);
        switch (((PrimitiveType) type).getPrimitiveKind()) {
            case I8:
                return new LLVMAMD64Target(frame, op.getShift());
            case I16:
            case I32:
            case I64:
                return new LLVMAMD64Target(frame);
            default:
                throw unsupportedOperandType(type);
        }
    }

    private LLVMAMD64WriteValueNode getStore(Type type, AsmOperand operand) {
        return LLVMAMD64WriteValueNodeGen.create(getTarget(type, operand));
    }

    private LLVMAMD64WriteValueNode getRegisterStore(Type type, AsmOperand operand) {
        return LLVMAMD64WriteValueNodeGen.create(getRegisterTarget(type, operand));
    }

    private LLVMAMD64WriteValueNode getRegisterStore(Type type, String name) {
        return LLVMAMD64WriteValueNodeGen.create(getRegisterTarget(type, name));
    }

    private LLVMAMD64WriteValueNode getRegisterStore(String name) {
        return LLVMAMD64WriteValueNodeGen.create(getRegisterTarget(name));
    }

    private FrameSlot getRegisterSlot(String name) {
        if (name.startsWith(TEMP_REGISTER_PREFIX)) {
            addFrameSlot(name, PrimitiveType.I64);
            return frameDescriptor.findFrameSlot(name);
        }
        AsmRegisterOperand op = new AsmRegisterOperand(name);
        String baseRegister = op.getBaseRegister();
        addFrameSlot(baseRegister, PrimitiveType.I64);
        FrameSlot frame = frameDescriptor.findFrameSlot(baseRegister);
        return frame;
    }

    private static String getArgumentName(int index) {
        return "$" + index;
    }

    private FrameSlot getArgumentSlot(int index, Type type) {
        Argument info = argInfo.get(index);
        String name = getArgumentName(index);
        if (type instanceof StructureType || type instanceof PointerType) {
            addFrameSlot(name, info.getType());
        } else {
            addFrameSlot(name, PrimitiveType.I64);
        }
        return frameDescriptor.findFrameSlot(name);
    }

    private static String getFlagName(long flag) {
        return "$flag_" + flag;
    }

    private FrameSlot getFlagSlot(long flag) {
        String name = getFlagName(flag);
        addFrameSlot(name, PrimitiveType.I1);
        return frameDescriptor.findFrameSlot(name);
    }

    private LLVMExpressionNode getFlag(long flag) {
        return LLVMI1ReadNodeGen.create(getFlagSlot(flag));
    }

    private LLVMAMD64WriteBooleanNode getFlagWrite(long flag) {
        return new LLVMAMD64WriteBooleanNode(getFlagSlot(flag));
    }

    private LLVMAMD64UpdatePZSFlagsNode getUpdatePZSFlagsNode() {
        return new LLVMAMD64UpdatePZSFlagsNode(getFlagSlot(LLVMAMD64Flags.PF), getFlagSlot(LLVMAMD64Flags.ZF), getFlagSlot(LLVMAMD64Flags.SF));
    }

    private LLVMAMD64UpdatePZSOFlagsNode getUpdatePZSOFlagsNode() {
        return new LLVMAMD64UpdatePZSOFlagsNode(getFlagSlot(LLVMAMD64Flags.PF), getFlagSlot(LLVMAMD64Flags.ZF), getFlagSlot(LLVMAMD64Flags.SF), getFlagSlot(LLVMAMD64Flags.OF));
    }

    private LLVMAMD64UpdateCPZSOFlagsNode getUpdateCPZSOFlagsNode() {
        return new LLVMAMD64UpdateCPZSOFlagsNode(getFlagSlot(LLVMAMD64Flags.CF), getFlagSlot(LLVMAMD64Flags.PF), getFlagSlot(LLVMAMD64Flags.ZF), getFlagSlot(LLVMAMD64Flags.SF),
                        getFlagSlot(LLVMAMD64Flags.OF));
    }

    private LLVMAMD64UpdateCPAZSOFlagsNode getUpdateCPAZSOFlagsNode() {
        return new LLVMAMD64UpdateCPAZSOFlagsNode(getFlagSlot(LLVMAMD64Flags.CF), getFlagSlot(LLVMAMD64Flags.PF), getFlagSlot(LLVMAMD64Flags.AF), getFlagSlot(LLVMAMD64Flags.ZF),
                        getFlagSlot(LLVMAMD64Flags.SF), getFlagSlot(LLVMAMD64Flags.OF));
    }
}
