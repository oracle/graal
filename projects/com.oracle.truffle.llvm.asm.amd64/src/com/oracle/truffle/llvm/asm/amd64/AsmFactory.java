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
package com.oracle.truffle.llvm.asm.amd64;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64AddNodeFactory.LLVMAMD64AddbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64AddNodeFactory.LLVMAMD64AddlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64AddNodeFactory.LLVMAMD64AddqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64AddNodeFactory.LLVMAMD64AddwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64AndNodeFactory.LLVMAMD64AndbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64AndNodeFactory.LLVMAMD64AndlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64AndNodeFactory.LLVMAMD64AndqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64AndNodeFactory.LLVMAMD64AndwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64DecNodeFactory.LLVMAMD64DecbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64DecNodeFactory.LLVMAMD64DeclNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64DecNodeFactory.LLVMAMD64DecqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64DecNodeFactory.LLVMAMD64DecwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64IdivNodeFactory.LLVMAMD64IdivbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64IdivNodeFactory.LLVMAMD64IdivlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64IdivNodeFactory.LLVMAMD64IdivqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64IdivNodeFactory.LLVMAMD64IdivwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ImmNodeFactory.LLVMAMD64I16NodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ImmNodeFactory.LLVMAMD64I32NodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ImmNodeFactory.LLVMAMD64I64NodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ImmNodeFactory.LLVMAMD64I8NodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ImulNodeFactory.LLVMAMD64ImulbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ImulNodeFactory.LLVMAMD64ImullNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ImulNodeFactory.LLVMAMD64ImulqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ImulNodeFactory.LLVMAMD64ImulwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64IncNodeFactory.LLVMAMD64IncbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64IncNodeFactory.LLVMAMD64InclNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64IncNodeFactory.LLVMAMD64IncqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64IncNodeFactory.LLVMAMD64IncwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64NegNodeFactory.LLVMAMD64NegbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64NegNodeFactory.LLVMAMD64NeglNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64NegNodeFactory.LLVMAMD64NegqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64NegNodeFactory.LLVMAMD64NegwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64NotNodeFactory.LLVMAMD64NotbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64NotNodeFactory.LLVMAMD64NotlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64NotNodeFactory.LLVMAMD64NotqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64NotNodeFactory.LLVMAMD64NotwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64OrNodeFactory.LLVMAMD64OrbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64OrNodeFactory.LLVMAMD64OrlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64OrNodeFactory.LLVMAMD64OrqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64OrNodeFactory.LLVMAMD64OrwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64RdtscNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64SalNodeFactory.LLVMAMD64SalbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64SalNodeFactory.LLVMAMD64SallNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64SalNodeFactory.LLVMAMD64SalqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64SalNodeFactory.LLVMAMD64SalwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64SarNodeFactory.LLVMAMD64SarbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64SarNodeFactory.LLVMAMD64SarlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64SarNodeFactory.LLVMAMD64SarqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64SarNodeFactory.LLVMAMD64SarwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ShlNodeFactory.LLVMAMD64ShlbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ShlNodeFactory.LLVMAMD64ShllNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ShlNodeFactory.LLVMAMD64ShlqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ShlNodeFactory.LLVMAMD64ShlwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ShrNodeFactory.LLVMAMD64ShrbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ShrNodeFactory.LLVMAMD64ShrlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ShrNodeFactory.LLVMAMD64ShrqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ShrNodeFactory.LLVMAMD64ShrwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64SubNodeFactory.LLVMAMD64SubbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64SubNodeFactory.LLVMAMD64SublNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64SubNodeFactory.LLVMAMD64SubqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64SubNodeFactory.LLVMAMD64SubwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64XorNodeFactory.LLVMAMD64XorbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64XorNodeFactory.LLVMAMD64XorlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64XorNodeFactory.LLVMAMD64XorqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64XorNodeFactory.LLVMAMD64XorwNodeGen;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI16RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI32RegisterNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64WriteRegisterNode.LLVMAMD64WriteI64RegisterNode;
import com.oracle.truffle.llvm.nodes.base.LLVMStructWriteNode;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64ToRegisterNodeFactory.LLVMI16ToR64NodeGen;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64ToRegisterNodeFactory.LLVMI32ToR64NodeGen;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMAMD64ToRegisterNodeFactory.LLVMI8ToR64NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI16NodeFactory.LLVMI64ToI16NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI32NodeFactory;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI32NodeFactory.LLVMI64ToI32NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI64NodeFactory.LLVMAnyToI64NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI8NodeFactory.LLVMI64ToI8NodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMArgNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMI32UnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMAddressReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMI64ReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNode;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNode.LLVMWriteAddressNode;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNode.LLVMWriteI64Node;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteAddressNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI64NodeGen;
import com.oracle.truffle.llvm.nodes.vars.StructLiteralNode;
import com.oracle.truffle.llvm.nodes.vars.StructLiteralNode.LLVMI32StructWriteNode;
import com.oracle.truffle.llvm.parser.api.LLVMBaseType;

public class AsmFactory {

    public static final int REG_START_INDEX = 1;

    private FrameDescriptor frameDescriptor;
    private List<LLVMExpressionNode> statements;
    private List<LLVMExpressionNode> arguments;
    private List<String> registers;
    @SuppressWarnings("unused") private LLVMExpressionNode[] args;
    private LLVMExpressionNode result;
    private String asmFlags;
    private LLVMBaseType retType;
    private int[] returnStructOffsets;
    private LLVMStructWriteNode[] returnStructWriteNodes;

    public AsmFactory(LLVMExpressionNode[] args, String asmFlags, LLVMBaseType retType) {
        this.args = args;
        this.asmFlags = asmFlags;
        this.frameDescriptor = new FrameDescriptor();
        this.statements = new ArrayList<>();
        this.arguments = new ArrayList<>();
        this.registers = new ArrayList<>();
        this.retType = retType;
    }

    public LLVMInlineAssemblyRootNode finishInline() {
        getArguments();
        if (retType == LLVMBaseType.STRUCT) {
            LLVMExpressionNode structAllocInstr = LLVMArgNodeGen.create(1);
            FrameSlot returnValueSlot = frameDescriptor.addFrameSlot("returnValue");
            returnValueSlot.setKind(FrameSlotKind.Object);
            LLVMWriteAddressNode writeStructAddress = LLVMWriteAddressNodeGen.create(structAllocInstr, returnValueSlot);
            statements.add(writeStructAddress);
            LLVMExpressionNode readStructAddress = LLVMAddressReadNodeGen.create(returnValueSlot);
            result = new StructLiteralNode(returnStructOffsets, returnStructWriteNodes, readStructAddress);
        }
        return new LLVMInlineAssemblyRootNode(null, frameDescriptor, statements.toArray(new LLVMExpressionNode[statements.size()]), arguments, result);
    }

    public void createOperation(String operation) {
        switch (operation) {
            case "clc":
            case "cld":
            case "cli":
            case "cmc":
            case "lahf":
            case "popfw":
            case "pushfw":
            case "sahf":
            case "stc":
            case "std":
            case "sti":
                statements.add(new LLVMI32UnsupportedInlineAssemblerNode());
                return;
            case "nop":
                break;
            default:
                statements.add(new LLVMI32UnsupportedInlineAssemblerNode());
                return;
        }
    }

    public void createUnaryOperation(String operation, AsmOperand operand) {
        LLVMExpressionNode src;
        LLVMExpressionNode out;
        AsmOperand dst = operand;
        LLVMBaseType dstType = LLVMBaseType.I64;
        switch (operation) {
            case "incb":
                src = getOperandLoad(LLVMBaseType.I8, operand);
                out = LLVMAMD64IncbNodeGen.create(src);
                dstType = LLVMBaseType.I8;
                break;
            case "incw":
                src = getOperandLoad(LLVMBaseType.I16, operand);
                out = LLVMAMD64IncwNodeGen.create(src);
                dstType = LLVMBaseType.I16;
                break;
            case "incl":
                src = getOperandLoad(LLVMBaseType.I32, operand);
                out = LLVMAMD64InclNodeGen.create(src);
                dstType = LLVMBaseType.I32;
                break;
            case "incq":
                src = getOperandLoad(LLVMBaseType.I64, operand);
                out = LLVMAMD64IncqNodeGen.create(src);
                dstType = LLVMBaseType.I64;
                break;
            case "decb":
                src = getOperandLoad(LLVMBaseType.I8, operand);
                out = LLVMAMD64DecbNodeGen.create(src);
                dstType = LLVMBaseType.I8;
                break;
            case "decw":
                src = getOperandLoad(LLVMBaseType.I16, operand);
                out = LLVMAMD64DecwNodeGen.create(src);
                dstType = LLVMBaseType.I16;
                break;
            case "decl":
                src = getOperandLoad(LLVMBaseType.I32, operand);
                out = LLVMAMD64DeclNodeGen.create(src);
                dstType = LLVMBaseType.I32;
                break;
            case "decq":
                src = getOperandLoad(LLVMBaseType.I64, operand);
                out = LLVMAMD64DecqNodeGen.create(src);
                dstType = LLVMBaseType.I64;
                break;
            case "negb":
                src = getOperandLoad(LLVMBaseType.I8, operand);
                out = LLVMAMD64NegbNodeGen.create(src);
                dstType = LLVMBaseType.I8;
                break;
            case "negw":
                src = getOperandLoad(LLVMBaseType.I16, operand);
                out = LLVMAMD64NegwNodeGen.create(src);
                dstType = LLVMBaseType.I16;
                break;
            case "negl":
                src = getOperandLoad(LLVMBaseType.I32, operand);
                out = LLVMAMD64NeglNodeGen.create(src);
                dstType = LLVMBaseType.I32;
                break;
            case "negq":
                src = getOperandLoad(LLVMBaseType.I64, operand);
                out = LLVMAMD64NegqNodeGen.create(src);
                dstType = LLVMBaseType.I64;
                break;
            case "notb":
                src = getOperandLoad(LLVMBaseType.I8, operand);
                out = LLVMAMD64NotbNodeGen.create(src);
                dstType = LLVMBaseType.I8;
                break;
            case "notw":
                src = getOperandLoad(LLVMBaseType.I16, operand);
                out = LLVMAMD64NotwNodeGen.create(src);
                dstType = LLVMBaseType.I16;
                break;
            case "notl":
                src = getOperandLoad(LLVMBaseType.I32, operand);
                out = LLVMAMD64NotlNodeGen.create(src);
                dstType = LLVMBaseType.I32;
                break;
            case "notq":
                src = getOperandLoad(LLVMBaseType.I64, operand);
                out = LLVMAMD64NotqNodeGen.create(src);
                dstType = LLVMBaseType.I64;
                break;
            case "idivb":
                src = getOperandLoad(LLVMBaseType.I8, operand);
                out = LLVMAMD64IdivbNodeGen.create(getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            case "idivw": {
                LLVMAMD64WriteI16RegisterNode rem = getRegisterStore("dx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("dx"));
                src = getOperandLoad(LLVMBaseType.I16, operand);
                out = LLVMAMD64IdivwNodeGen.create(rem, high, getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            }
            case "idivl": {
                LLVMAMD64WriteI32RegisterNode rem = getRegisterStore("edx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("edx"));
                src = getOperandLoad(LLVMBaseType.I32, operand);
                out = LLVMAMD64IdivlNodeGen.create(rem, high, getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("eax")), src);
                dst = new AsmRegisterOperand("eax");
                dstType = LLVMBaseType.I32;
                break;
            }
            case "idivq": {
                LLVMAMD64WriteI64RegisterNode rem = getRegisterStore("rdx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("rdx"));
                src = getOperandLoad(LLVMBaseType.I64, operand);
                out = LLVMAMD64IdivqNodeGen.create(rem, high, getOperandLoad(LLVMBaseType.I64, new AsmRegisterOperand("rax")), src);
                dst = new AsmRegisterOperand("rax");
                dstType = LLVMBaseType.I64;
                break;
            }
            case "imulb":
                src = getOperandLoad(LLVMBaseType.I8, operand);
                out = LLVMAMD64ImulbNodeGen.create(getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            case "imulw": {
                LLVMAMD64WriteI16RegisterNode high = getRegisterStore("dx");
                src = getOperandLoad(LLVMBaseType.I16, operand);
                out = LLVMAMD64ImulwNodeGen.create(high, getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            }
            case "imull": {
                LLVMAMD64WriteI32RegisterNode high = getRegisterStore("edx");
                src = getOperandLoad(LLVMBaseType.I32, operand);
                out = LLVMAMD64ImullNodeGen.create(high, getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("eax")), src);
                dst = new AsmRegisterOperand("eax");
                dstType = LLVMBaseType.I32;
                break;
            }
            case "imulq": {
                LLVMAMD64WriteI64RegisterNode high = getRegisterStore("rdx");
                src = getOperandLoad(LLVMBaseType.I64, operand);
                out = LLVMAMD64ImulqNodeGen.create(high, getOperandLoad(LLVMBaseType.I64, new AsmRegisterOperand("rax")), src);
                dst = new AsmRegisterOperand("rax");
                dstType = LLVMBaseType.I64;
                break;
            }
            // TODO: implement properly
            case "divb":
                src = getOperandLoad(LLVMBaseType.I8, operand);
                out = LLVMAMD64IdivbNodeGen.create(getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            case "divw": {
                LLVMAMD64WriteI16RegisterNode rem = getRegisterStore("dx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("dx"));
                src = getOperandLoad(LLVMBaseType.I16, operand);
                out = LLVMAMD64IdivwNodeGen.create(rem, high, getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            }
            case "divl": {
                LLVMAMD64WriteI32RegisterNode rem = getRegisterStore("edx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("edx"));
                src = getOperandLoad(LLVMBaseType.I32, operand);
                out = LLVMAMD64IdivlNodeGen.create(rem, high, getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("eax")), src);
                dst = new AsmRegisterOperand("eax");
                dstType = LLVMBaseType.I32;
                break;
            }
            case "divq": {
                LLVMAMD64WriteI64RegisterNode rem = getRegisterStore("rdx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("rdx"));
                src = getOperandLoad(LLVMBaseType.I64, operand);
                out = LLVMAMD64IdivqNodeGen.create(rem, high, getOperandLoad(LLVMBaseType.I64, new AsmRegisterOperand("rax")), src);
                dst = new AsmRegisterOperand("rax");
                dstType = LLVMBaseType.I64;
                break;
            }
            case "mulb":
                src = getOperandLoad(LLVMBaseType.I16, operand);
                out = LLVMAMD64ImulbNodeGen.create(getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            case "mulw": {
                LLVMAMD64WriteI16RegisterNode high = getRegisterStore("dx");
                src = getOperandLoad(LLVMBaseType.I16, operand);
                out = LLVMAMD64ImulwNodeGen.create(high, getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            }
            case "mull": {
                LLVMAMD64WriteI32RegisterNode high = getRegisterStore("edx");
                src = getOperandLoad(LLVMBaseType.I32, operand);
                out = LLVMAMD64ImullNodeGen.create(high, getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("eax")), src);
                dst = new AsmRegisterOperand("eax");
                dstType = LLVMBaseType.I32;
                break;
            }
            case "mulq": {
                LLVMAMD64WriteI64RegisterNode high = getRegisterStore("rdx");
                src = getOperandLoad(LLVMBaseType.I64, operand);
                out = LLVMAMD64ImulqNodeGen.create(high, getOperandLoad(LLVMBaseType.I64, new AsmRegisterOperand("rax")), src);
                dst = new AsmRegisterOperand("rax");
                dstType = LLVMBaseType.I64;
                break;
            }
            default:
                statements.add(new LLVMI32UnsupportedInlineAssemblerNode());
                return;
        }
        LLVMWriteNode write = getOperandStore(dstType, dst, out);
        statements.add(write);
    }

    public void createBinaryOperation(String operation, AsmOperand a, AsmOperand b) {
        LLVMExpressionNode srcA;
        LLVMExpressionNode srcB;
        LLVMExpressionNode out;
        AsmOperand dst = b;
        LLVMBaseType dstType = LLVMBaseType.I8;
        switch (operation) {
            case "addb":
                srcA = getOperandLoad(LLVMBaseType.I8, a);
                srcB = getOperandLoad(LLVMBaseType.I8, b);
                out = LLVMAMD64AddbNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I8;
                break;
            case "addw":
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                srcB = getOperandLoad(LLVMBaseType.I16, b);
                out = LLVMAMD64AddwNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I16;
                break;
            case "addl":
                srcA = getOperandLoad(LLVMBaseType.I32, a);
                srcB = getOperandLoad(LLVMBaseType.I32, b);
                out = LLVMAMD64AddlNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I32;
                break;
            case "addq":
                srcA = getOperandLoad(LLVMBaseType.I64, a);
                srcB = getOperandLoad(LLVMBaseType.I64, b);
                out = LLVMAMD64AddqNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I64;
                break;
            case "subb":
                srcA = getOperandLoad(LLVMBaseType.I8, a);
                srcB = getOperandLoad(LLVMBaseType.I8, b);
                out = LLVMAMD64SubbNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I8;
                break;
            case "subw":
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                srcB = getOperandLoad(LLVMBaseType.I16, b);
                out = LLVMAMD64SubwNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I16;
                break;
            case "subl":
                srcA = getOperandLoad(LLVMBaseType.I32, a);
                srcB = getOperandLoad(LLVMBaseType.I32, b);
                out = LLVMAMD64SublNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I32;
                break;
            case "subq":
                srcA = getOperandLoad(LLVMBaseType.I64, a);
                srcB = getOperandLoad(LLVMBaseType.I64, b);
                out = LLVMAMD64SubqNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I64;
                break;
            case "idivb":
                srcA = getOperandLoad(LLVMBaseType.I8, a);
                srcB = getOperandLoad(LLVMBaseType.I16, b);
                out = LLVMAMD64IdivbNodeGen.create(srcB, srcA);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            case "idivw": {
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                srcB = getOperandLoad(LLVMBaseType.I16, b);
                LLVMAMD64WriteI16RegisterNode rem = getRegisterStore("dx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("dx"));
                out = LLVMAMD64IdivwNodeGen.create(rem, high, srcB, srcA);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            }
            case "idivl": {
                srcA = getOperandLoad(LLVMBaseType.I32, a);
                srcB = getOperandLoad(LLVMBaseType.I32, b);
                LLVMAMD64WriteI32RegisterNode rem = getRegisterStore("edx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("edx"));
                out = LLVMAMD64IdivlNodeGen.create(rem, high, srcB, srcA);
                dst = new AsmRegisterOperand("eax");
                dstType = LLVMBaseType.I32;
                break;
            }
            case "idivq": {
                srcA = getOperandLoad(LLVMBaseType.I64, a);
                srcB = getOperandLoad(LLVMBaseType.I64, b);
                LLVMAMD64WriteI64RegisterNode rem = getRegisterStore("rdx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("rdx"));
                out = LLVMAMD64IdivqNodeGen.create(rem, high, srcB, srcA);
                dst = new AsmRegisterOperand("rax");
                dstType = LLVMBaseType.I64;
                break;
            }
            case "imulb":
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                srcB = getOperandLoad(LLVMBaseType.I8, b);
                out = LLVMAMD64ImulbNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I16;
                break;
            case "imulw": {
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                srcB = getOperandLoad(LLVMBaseType.I16, b);
                LLVMAMD64WriteI16RegisterNode high = getRegisterStore("dx");
                out = LLVMAMD64ImulwNodeGen.create(high, srcA, srcB);
                dstType = LLVMBaseType.I16;
                break;
            }
            case "imull": {
                srcA = getOperandLoad(LLVMBaseType.I32, a);
                srcB = getOperandLoad(LLVMBaseType.I32, b);
                LLVMAMD64WriteI32RegisterNode high = getRegisterStore("edx");
                out = LLVMAMD64ImullNodeGen.create(high, srcA, srcB);
                dstType = LLVMBaseType.I32;
                break;
            }
            case "imulq": {
                srcA = getOperandLoad(LLVMBaseType.I64, a);
                srcB = getOperandLoad(LLVMBaseType.I64, b);
                LLVMAMD64WriteI64RegisterNode high = getRegisterStore("rdx");
                out = LLVMAMD64ImulqNodeGen.create(high, srcA, srcB);
                dstType = LLVMBaseType.I64;
                break;
            }
            case "movb":
                srcA = getOperandLoad(LLVMBaseType.I8, a);
                dstType = LLVMBaseType.I8;
                out = srcA;
                break;
            case "movw":
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                dstType = LLVMBaseType.I16;
                out = srcA;
                break;
            case "movl":
                srcA = getOperandLoad(LLVMBaseType.I32, a);
                dstType = LLVMBaseType.I32;
                out = srcA;
                break;
            case "movq":
                srcA = getOperandLoad(LLVMBaseType.I64, a);
                dstType = LLVMBaseType.I64;
                out = srcA;
                break;
            case "salb":
                srcA = getOperandLoad(LLVMBaseType.I8, a);
                srcB = getOperandLoad(LLVMBaseType.I8, b);
                out = LLVMAMD64SalbNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I8;
                break;
            case "salw":
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                srcB = getOperandLoad(LLVMBaseType.I16, b);
                out = LLVMAMD64SalwNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I16;
                break;
            case "sall":
                srcA = getOperandLoad(LLVMBaseType.I32, a);
                srcB = getOperandLoad(LLVMBaseType.I32, b);
                out = LLVMAMD64SallNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I32;
                break;
            case "salq":
                srcA = getOperandLoad(LLVMBaseType.I64, a);
                srcB = getOperandLoad(LLVMBaseType.I64, b);
                out = LLVMAMD64SalqNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I64;
                break;
            case "sarb":
                srcA = getOperandLoad(LLVMBaseType.I8, a);
                srcB = getOperandLoad(LLVMBaseType.I8, b);
                out = LLVMAMD64SarbNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I8;
                break;
            case "sarw":
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                srcB = getOperandLoad(LLVMBaseType.I16, b);
                out = LLVMAMD64SarwNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I16;
                break;
            case "sarl":
                srcA = getOperandLoad(LLVMBaseType.I32, a);
                srcB = getOperandLoad(LLVMBaseType.I32, b);
                out = LLVMAMD64SarlNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I32;
                break;
            case "sarq":
                srcA = getOperandLoad(LLVMBaseType.I64, a);
                srcB = getOperandLoad(LLVMBaseType.I64, b);
                out = LLVMAMD64SarqNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I64;
                break;
            case "shlb":
                srcA = getOperandLoad(LLVMBaseType.I8, a);
                srcB = getOperandLoad(LLVMBaseType.I8, b);
                out = LLVMAMD64ShlbNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I8;
                break;
            case "shlw":
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                srcB = getOperandLoad(LLVMBaseType.I16, b);
                out = LLVMAMD64ShlwNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I16;
                break;
            case "shll":
                srcA = getOperandLoad(LLVMBaseType.I32, a);
                srcB = getOperandLoad(LLVMBaseType.I32, b);
                out = LLVMAMD64ShllNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I32;
                break;
            case "shlq":
                srcA = getOperandLoad(LLVMBaseType.I64, a);
                srcB = getOperandLoad(LLVMBaseType.I64, b);
                out = LLVMAMD64ShlqNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I64;
                break;
            case "shrb":
                srcA = getOperandLoad(LLVMBaseType.I8, a);
                srcB = getOperandLoad(LLVMBaseType.I8, b);
                out = LLVMAMD64ShrbNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I8;
                break;
            case "shrw":
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                srcB = getOperandLoad(LLVMBaseType.I16, b);
                out = LLVMAMD64ShrwNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I16;
                break;
            case "shrl":
                srcA = getOperandLoad(LLVMBaseType.I32, a);
                srcB = getOperandLoad(LLVMBaseType.I32, b);
                out = LLVMAMD64ShrlNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I32;
                break;
            case "shrq":
                srcA = getOperandLoad(LLVMBaseType.I64, a);
                srcB = getOperandLoad(LLVMBaseType.I64, b);
                out = LLVMAMD64ShrqNodeGen.create(srcB, srcA);
                dstType = LLVMBaseType.I64;
                break;
            case "andb":
                srcA = getOperandLoad(LLVMBaseType.I8, a);
                srcB = getOperandLoad(LLVMBaseType.I8, b);
                out = LLVMAMD64AndbNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I8;
                break;
            case "andw":
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                srcB = getOperandLoad(LLVMBaseType.I16, b);
                out = LLVMAMD64AndwNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I16;
                break;
            case "andl":
                srcA = getOperandLoad(LLVMBaseType.I32, a);
                srcB = getOperandLoad(LLVMBaseType.I32, b);
                out = LLVMAMD64AndlNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I32;
                break;
            case "andq":
                srcA = getOperandLoad(LLVMBaseType.I64, a);
                srcB = getOperandLoad(LLVMBaseType.I64, b);
                out = LLVMAMD64AndqNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I64;
                break;
            case "orb":
                srcA = getOperandLoad(LLVMBaseType.I8, a);
                srcB = getOperandLoad(LLVMBaseType.I8, b);
                out = LLVMAMD64OrbNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I8;
                break;
            case "orw":
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                srcB = getOperandLoad(LLVMBaseType.I16, b);
                out = LLVMAMD64OrwNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I16;
                break;
            case "orl":
                srcA = getOperandLoad(LLVMBaseType.I32, a);
                srcB = getOperandLoad(LLVMBaseType.I32, b);
                out = LLVMAMD64OrlNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I32;
                break;
            case "orq":
                srcA = getOperandLoad(LLVMBaseType.I64, a);
                srcB = getOperandLoad(LLVMBaseType.I64, b);
                out = LLVMAMD64OrqNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I64;
                break;
            case "xorb":
                srcA = getOperandLoad(LLVMBaseType.I8, a);
                srcB = getOperandLoad(LLVMBaseType.I8, b);
                out = LLVMAMD64XorbNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I8;
                break;
            case "xorw":
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                srcB = getOperandLoad(LLVMBaseType.I16, b);
                out = LLVMAMD64XorwNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I16;
                break;
            case "xorl":
                srcA = getOperandLoad(LLVMBaseType.I32, a);
                srcB = getOperandLoad(LLVMBaseType.I32, b);
                out = LLVMAMD64XorlNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I32;
                break;
            case "xorq":
                srcA = getOperandLoad(LLVMBaseType.I64, a);
                srcB = getOperandLoad(LLVMBaseType.I64, b);
                out = LLVMAMD64XorqNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I64;
                break;
            default:
                statements.add(new LLVMI32UnsupportedInlineAssemblerNode());
                return;
        }
        LLVMWriteNode write = getOperandStore(dstType, dst, out);
        statements.add(write);
    }

    public void createRdtscOperation() {
        LLVMExpressionNode rdtscCalcNode = LLVMAMD64RdtscNodeGen.create();
        FrameSlot rdtscFrameSlot = frameDescriptor.addFrameSlot("rdtscSlot");
        rdtscFrameSlot.setKind(FrameSlotKind.Long);
        LLVMWriteI64Node writeRdtscNode = LLVMWriteNodeFactory.LLVMWriteI64NodeGen.create(rdtscCalcNode, rdtscFrameSlot);
        statements.add(writeRdtscNode);
        // 64 bit TSC value is stored by evenly splitting in eax and edx 32 bit registers
        LLVMExpressionNode rdtscReadNode = LLVMI64ReadNodeGen.create(rdtscFrameSlot);
        LLVMExpressionNode constant32Node = LLVMAMD64I32NodeGen.create(LLVMExpressionNode.I32_SIZE_IN_BYTES * Byte.SIZE);
        LLVMExpressionNode edxNode = LLVMToI32NodeFactory.LLVMI64ToI32NodeGen.create(LLVMAMD64ShrlNodeGen.create(constant32Node, rdtscReadNode));
        LLVMExpressionNode eaxNode = LLVMToI32NodeFactory.LLVMI64ToI32NodeGen.create(rdtscReadNode);

        LLVMI32StructWriteNode eax = new LLVMI32StructWriteNode(eaxNode);
        LLVMI32StructWriteNode edx = new LLVMI32StructWriteNode(edxNode);

        returnStructOffsets = new int[]{0, LLVMExpressionNode.I32_SIZE_IN_BYTES};
        returnStructWriteNodes = new LLVMStructWriteNode[]{eax, edx};
    }

    @SuppressWarnings("unused")
    public void createTernaryOperation(String op, AsmOperand a, AsmOperand b, AsmOperand c) {
    }

    protected void addFrameSlot(String reg, LLVMBaseType type) {
        if (!registers.contains(reg)) {
            registers.add(reg);
            FrameSlotKind kind;
            switch (type) {
                case I32:
                    kind = FrameSlotKind.Int;
                    break;
                case I64:
                    kind = FrameSlotKind.Long;
                    break;
                default:
                    kind = FrameSlotKind.Illegal;
                    break;
            }
            this.frameDescriptor.addFrameSlot(reg, kind);
        }
    }

    private void getArguments() {
        String[] tokens = asmFlags.substring(1, asmFlags.length() - 1).split(",");
        int index = 1;
        Set<String> todoRegisters = new HashSet<>(registers);
        for (String token : tokens) {
            if (token.charAt(0) == '~') {
                continue;
            }
            int start = token.indexOf('{');
            int end = token.lastIndexOf('}');

            // FIXME: start or end could be -1
            assert start != -1;
            assert end != -1;

            String registerName = token.substring(start + 1, end);
            // output register
            if (token.charAt(0) == '=') {
                if (AsmRegisterOperand.isRegister(registerName)) {
                    String reg = AsmRegisterOperand.getBaseRegister(registerName);
                    addFrameSlot(reg, LLVMBaseType.I64);
                    FrameSlot slot = frameDescriptor.findFrameSlot(reg);
                    LLVMExpressionNode register = LLVMI64ReadNodeGen.create(slot);
                    this.result = castResult(register);
                }
                continue;
            }
            // input register
            if (AsmRegisterOperand.isRegister(registerName)) {
                String reg = AsmRegisterOperand.getBaseRegister(registerName);
                if (registers.contains(reg)) {
                    LLVMExpressionNode arg = LLVMArgNodeGen.create(index);
                    LLVMExpressionNode node = LLVMAnyToI64NodeGen.create(arg);
                    addFrameSlot(reg, LLVMBaseType.I64);
                    FrameSlot slot = frameDescriptor.findFrameSlot(reg);
                    arguments.add(LLVMWriteI64NodeGen.create(node, slot));
                    index++;
                    todoRegisters.remove(reg);
                } else {
                    index++;
                }
            }
        }

        // initialize registers
        for (String register : todoRegisters) {
            LLVMExpressionNode node = LLVMAMD64I64NodeGen.create(0);
            FrameSlot slot = frameDescriptor.findFrameSlot(register);
            arguments.add(LLVMWriteI64NodeGen.create(node, slot));
        }
    }

    private LLVMExpressionNode castResult(LLVMExpressionNode register) {
        switch (retType) {
            case I8:
                return LLVMI64ToI8NodeGen.create(register);
            case I16:
                return LLVMI64ToI16NodeGen.create(register);
            case I32:
                return LLVMI64ToI32NodeGen.create(register);
            case I64:
                return register;
            default:
                return new LLVMUnsupportedInlineAssemblerNode();
        }
    }

    private LLVMExpressionNode getOperandLoad(LLVMBaseType type, AsmOperand operand) {
        if (operand instanceof AsmRegisterOperand) {
            AsmRegisterOperand op = (AsmRegisterOperand) operand;
            String baseRegister = op.getBaseRegister();
            addFrameSlot(baseRegister, LLVMBaseType.I64);
            FrameSlot frame = frameDescriptor.findFrameSlot(baseRegister);
            LLVMExpressionNode register = LLVMI64ReadNodeGen.create(frame);
            assert type == op.getWidth();
            switch (op.getWidth()) {
                case I8:
                    return LLVMI64ToI8NodeGen.create(register);
                case I16:
                    return LLVMI64ToI16NodeGen.create(register);
                case I32:
                    return LLVMI64ToI32NodeGen.create(register);
                case I64:
                    return register;
                default:
                    throw new AsmParseException("invalid operand size");
            }
        } else if (operand instanceof AsmImmediateOperand) {
            AsmImmediateOperand op = (AsmImmediateOperand) operand;
            if (op.isLabel()) {
                throw new AsmParseException("labels not supported");
            } else {
                switch (type) {
                    case I8:
                        return LLVMAMD64I8NodeGen.create((byte) op.getValue());
                    case I16:
                        return LLVMAMD64I16NodeGen.create((short) op.getValue());
                    case I32:
                        return LLVMAMD64I32NodeGen.create((int) op.getValue());
                    case I64:
                        return LLVMAMD64I64NodeGen.create(op.getValue());
                    default:
                        throw new AsmParseException("invalid dst type");
                }
            }
        }
        throw new AsmParseException("unsupported operand type");
    }

    private LLVMWriteNode getOperandStore(LLVMBaseType type, AsmOperand operand, LLVMExpressionNode from) {
        if (operand instanceof AsmRegisterOperand) {
            AsmRegisterOperand op = (AsmRegisterOperand) operand;
            String baseRegister = op.getBaseRegister();
            addFrameSlot(baseRegister, LLVMBaseType.I64);
            FrameSlot frame = frameDescriptor.findFrameSlot(baseRegister);
            LLVMExpressionNode register = LLVMI64ReadNodeGen.create(frame);
            int shift = op.getShift();
            LLVMExpressionNode out = null;
            assert type == op.getWidth();
            switch (op.getWidth()) {
                case I8:
                    out = LLVMI8ToR64NodeGen.create(shift, register, from);
                    break;
                case I16:
                    out = LLVMI16ToR64NodeGen.create(register, from);
                    break;
                case I32:
                    out = LLVMI32ToR64NodeGen.create(register, from);
                    break;
                case I64:
                    out = from;
                    break;
                default:
                    throw new AsmParseException("unsupported operand size");
            }
            return LLVMWriteI64NodeGen.create(out, frame);
        }
        throw new AsmParseException("unsupported operand type");
    }

    @SuppressWarnings("unchecked")
    private <T extends LLVMAMD64WriteRegisterNode> T getRegisterStore(String name) {
        AsmRegisterOperand op = new AsmRegisterOperand(name);
        String baseRegister = op.getBaseRegister();
        addFrameSlot(baseRegister, LLVMBaseType.I64);
        FrameSlot frame = frameDescriptor.findFrameSlot(baseRegister);
        LLVMExpressionNode register = LLVMI64ReadNodeGen.create(frame);
        LLVMAMD64WriteRegisterNode node = null;
        switch (op.getWidth()) {
            case I16:
                node = new LLVMAMD64WriteI16RegisterNode(frame, register);
                break;
            case I32:
                node = new LLVMAMD64WriteI32RegisterNode(frame, register);
                break;
            case I64:
                node = new LLVMAMD64WriteI64RegisterNode(frame);
                break;
            default:
                throw new AsmParseException("unsupported operand type");
        }
        return (T) node;
    }
}
