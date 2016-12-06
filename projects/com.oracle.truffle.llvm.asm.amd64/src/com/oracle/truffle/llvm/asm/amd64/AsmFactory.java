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
import java.util.List;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.llvm.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64DeclNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64AddlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64AndlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64ImulNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64OrlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64SallNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64SarlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64ShllNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64ShrlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64SublNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64XorlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64I64BinaryNodeFactory.LLVMAMD64I64ShrlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64IdivlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ImmNode;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64InclNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64ModNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64NotlNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64RdtscNodeGen;
import com.oracle.truffle.llvm.nodes.base.LLVMStructWriteNode;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI32NodeFactory;
import com.oracle.truffle.llvm.nodes.func.LLVMArgNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMI32UnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMAddressReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMI32ReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMI64ReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNode.LLVMWriteAddressNode;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNode.LLVMWriteI64Node;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteAddressNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI32NodeGen;
import com.oracle.truffle.llvm.nodes.vars.StructLiteralNode;
import com.oracle.truffle.llvm.nodes.vars.StructLiteralNode.LLVMI32StructWriteNode;
import com.oracle.truffle.llvm.parser.api.LLVMBaseType;

public class AsmFactory {

    public static final int REG_START_INDEX = 1;
    private static final int HEX = 16;

    private FrameDescriptor frameDescriptor;
    private List<LLVMExpressionNode> statements;
    private List<LLVMExpressionNode> arguments;
    private List<String> registers;
    private LLVMExpressionNode result;
    private String asmFlags;
    private LLVMBaseType retType;
    private int[] returnStructOffsets;
    private LLVMStructWriteNode[] returnStructWriteNodes;

    public AsmFactory(String asmFlags, LLVMBaseType retType) {
        this.asmFlags = asmFlags;
        this.frameDescriptor = new FrameDescriptor();
        this.statements = new ArrayList<>();
        this.arguments = new ArrayList<>();
        this.registers = new ArrayList<>();
        this.registers.add("-");
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

    public void createBinaryOperation(String operation, String left, String right) {
        FrameSlot leftSlot = frameDescriptor.findFrameSlot(left);
        String rightOperand = (right == null) ? "%eax" : right;
        FrameSlot rightSlot = frameDescriptor.findFrameSlot(rightOperand);
        LLVMExpressionNode leftNode = (leftSlot != null) ? LLVMI32ReadNodeGen.create(leftSlot) : getImmediateNode(left);
        LLVMExpressionNode rightNode = LLVMI32ReadNodeGen.create(rightSlot);
        this.result = LLVMI32ReadNodeGen.create(rightSlot);
        LLVMExpressionNode opNode = null;
        switch (operation) {
            case "addl":
                opNode = LLVMAMD64AddlNodeGen.create(leftNode, rightNode);
                break;
            case "subl":
                opNode = LLVMAMD64SublNodeGen.create(leftNode, rightNode);
                break;
            case "andl":
                opNode = LLVMAMD64AndlNodeGen.create(leftNode, rightNode);
                break;
            case "orl":
                opNode = LLVMAMD64OrlNodeGen.create(leftNode, rightNode);
                break;
            case "xorl":
                opNode = LLVMAMD64XorlNodeGen.create(leftNode, rightNode);
                break;
            case "sall":
                opNode = LLVMAMD64SallNodeGen.create(leftNode, rightNode);
                break;
            case "sarl":
                opNode = LLVMAMD64SarlNodeGen.create(leftNode, rightNode);
                break;
            case "shll":
                opNode = LLVMAMD64ShllNodeGen.create(leftNode, rightNode);
                break;
            case "shrl":
                opNode = LLVMAMD64ShrlNodeGen.create(leftNode, rightNode);
                break;
            case "movl":
                opNode = leftNode;
                break;
            case "imull":
                opNode = LLVMAMD64ImulNodeGen.create(leftNode, rightNode);
                break;
            default:
                opNode = new LLVMI32UnsupportedInlineAssemblerNode();
                return;
        }
        this.statements.add(LLVMWriteI32NodeGen.create(opNode, rightSlot));
    }

    public void createUnaryOperation(String operation, String node) {
        FrameSlot slot = frameDescriptor.findFrameSlot(node);
        LLVMExpressionNode childNode = LLVMI32ReadNodeGen.create(slot);
        this.result = LLVMI32ReadNodeGen.create(slot);
        LLVMExpressionNode opNode = null;
        switch (operation) {
            case "notl":
                opNode = LLVMAMD64NotlNodeGen.create(childNode);
                break;
            case "decl":
                opNode = LLVMAMD64DeclNodeGen.create(childNode);
                break;
            case "incl":
                opNode = LLVMAMD64InclNodeGen.create(childNode);
                break;
            default:
                opNode = new LLVMI32UnsupportedInlineAssemblerNode();
                return;
        }
        this.statements.add(LLVMWriteI32NodeGen.create(opNode, slot));
    }

    public void createMiscOperation(String operation) {

        switch (operation) {
            case "rdtsc":
                createRdtscOperation();
                break;
            default:
                statements.add(new LLVMI32UnsupportedInlineAssemblerNode());
        }
    }

    public void createRdtscOperation() {
        LLVMExpressionNode rdtscCalcNode = LLVMAMD64RdtscNodeGen.create();
        FrameSlot rdtscFrameSlot = frameDescriptor.addFrameSlot("rdtscSlot");
        rdtscFrameSlot.setKind(FrameSlotKind.Long);
        LLVMWriteI64Node writeRdtscNode = LLVMWriteNodeFactory.LLVMWriteI64NodeGen.create(rdtscCalcNode, rdtscFrameSlot);
        statements.add(writeRdtscNode);
        // 64 bit TSC value is stored by evenly splitting in eax and edx 32 bit registers
        LLVMExpressionNode rdtscReadNode = LLVMI64ReadNodeGen.create(rdtscFrameSlot);
        LLVMExpressionNode constant32Node = new LLVMAMD64ImmNode(LLVMExpressionNode.I32_SIZE_IN_BYTES * Byte.SIZE);
        LLVMExpressionNode edxNode = LLVMToI32NodeFactory.LLVMI64ToI32NodeGen.create(LLVMAMD64I64ShrlNodeGen.create(constant32Node, rdtscReadNode));
        LLVMExpressionNode eaxNode = LLVMToI32NodeFactory.LLVMI64ToI32NodeGen.create(rdtscReadNode);

        LLVMI32StructWriteNode eax = new LLVMI32StructWriteNode(eaxNode);
        LLVMI32StructWriteNode edx = new LLVMI32StructWriteNode(edxNode);

        returnStructOffsets = new int[]{0, LLVMExpressionNode.I32_SIZE_IN_BYTES};
        returnStructWriteNodes = new LLVMStructWriteNode[]{eax, edx};
    }

    public void createDivisionOperation(String op, String divisor) {
        FrameSlot slot = frameDescriptor.findFrameSlot(divisor);
        LLVMExpressionNode divisorNode = (slot != null) ? LLVMI32ReadNodeGen.create(slot) : getImmediateNode(divisor);

        if (op.equals("idivl")) {
            FrameSlot eaxSlot = frameDescriptor.findFrameSlot("%eax");
            LLVMExpressionNode eaxNode = LLVMI32ReadNodeGen.create(eaxSlot);
            FrameSlot edxSlot = frameDescriptor.findFrameSlot("%edx");
            LLVMExpressionNode edxNode = LLVMI32ReadNodeGen.create(edxSlot);
            LLVMExpressionNode divisionNode = LLVMAMD64IdivlNodeGen.create(divisorNode, eaxNode, edxNode);

            if (retType == LLVMBaseType.STRUCT) {
                LLVMExpressionNode remainderNode = LLVMAMD64ModNodeGen.create(divisorNode, eaxNode, edxNode);
                LLVMI32StructWriteNode quotient = new LLVMI32StructWriteNode(divisionNode);
                LLVMI32StructWriteNode remainder = new LLVMI32StructWriteNode(remainderNode);
                returnStructOffsets = new int[]{0, LLVMExpressionNode.I32_SIZE_IN_BYTES};
                returnStructWriteNodes = new LLVMStructWriteNode[]{remainder, quotient};
            } else if (retType == LLVMBaseType.I32) {
                result = divisionNode;
            } else {
                result = new LLVMUnsupportedInlineAssemblerNode();
            }
        } else {
            // TODO: Other div instruction shall go here
            result = new LLVMI32UnsupportedInlineAssemblerNode();
        }
    }

    protected void addFrameSlot(String reg, LLVMBaseType type) {
        if (!registers.contains(reg)) {
            registers.add(reg);
            FrameSlotKind kind;
            switch (type) {
                case I32:
                    kind = FrameSlotKind.Int;
                    break;
                default:
                    kind = FrameSlotKind.Illegal;
                    break;
            }
            this.frameDescriptor.addFrameSlot(reg, kind);
        }
    }

    private void getArguments() {
        for (int i = LLVMCallNode.ARG_START_INDEX; i < registers.size(); i++) {
            String reg = registers.get(i);
            if (reg != null) {
                int index = getRegisterIndex(reg);
                if (index != 0) {
                    LLVMExpressionNode node = LLVMArgNodeGen.create(index);
                    FrameSlot slot = frameDescriptor.findFrameSlot(reg);
                    arguments.add(LLVMWriteI32NodeGen.create(node, slot));
                }
            }
        }
    }

    private static LLVMAMD64ImmNode getImmediateNode(String number) {
        if (number.contains("0x")) {
            // hexadecimal number
            return new LLVMAMD64ImmNode(Integer.parseInt(number.substring(2), HEX));
        } else {
            // decimal number
            return new LLVMAMD64ImmNode(Integer.parseInt(number));
        }
    }

    private int getRegisterIndex(String register) {
        String[] flags = asmFlags.split(",");
        for (int i = REG_START_INDEX; i < flags.length; i++) {
            if (flags[i].startsWith("{") && register.contains(flags[i].substring(1, flags[i].length() - 1))) {
                return i;
            }
        }
        return 0;
    }

}
