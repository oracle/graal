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
import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.base.LLVMNode;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64DeclNodeGen;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64AddlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64AndlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64ImulNodeGen;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64OrlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64SallNodeGen;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64SarlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64ShllNodeGen;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64ShrlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64SublNodeGen;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64I32BinaryNodeFactory.LLVMAMD64XorlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64IdivlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64ImmNode;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64InclNodeGen;
import com.oracle.truffle.llvm.nodes.impl.asm.LLVMAMD64NotlNodeGen;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMI32ArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMCallNode;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.nodes.impl.others.LLVMUnsupportedInlineAssemblerNode.LLVMI32UnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMReadNodeFactory.LLVMI32ReadNodeGen;
import com.oracle.truffle.llvm.nodes.impl.vars.LLVMWriteNodeFactory.LLVMWriteI32NodeGen;
import com.oracle.truffle.llvm.parser.LLVMBaseType;

public class AsmFactory {

    public static final int REG_START_INDEX = 1;
    private static final int HEX = 16;

    private FrameDescriptor frameDescriptor;
    private List<LLVMNode> statements;
    private List<LLVMNode> arguments;
    private List<String> registers;
    private LLVMExpressionNode result;
    private String asmFlags;

    public AsmFactory(String asmFlags) {
        this.asmFlags = asmFlags;
        this.frameDescriptor = new FrameDescriptor();
        this.statements = new ArrayList<>();
        this.arguments = new ArrayList<>();
        this.registers = new ArrayList<>();
        this.registers.add("-");
    }

    public LLVMInlineAssemblyRootNode finishInline() {
        getArguments();
        return new LLVMInlineAssemblyRootNode(null, frameDescriptor, statements.toArray(new LLVMNode[statements.size()]), arguments, result);
    }

    public void createBinaryOperation(String operation, String left, String right) {
        FrameSlot leftSlot = frameDescriptor.findFrameSlot(left);
        FrameSlot rightSlot = frameDescriptor.findFrameSlot(right);
        LLVMI32Node leftNode = (leftSlot != null) ? LLVMI32ReadNodeGen.create(leftSlot) : getImmediateNode(left);
        LLVMI32Node rightNode = LLVMI32ReadNodeGen.create(rightSlot);
        this.result = LLVMI32ReadNodeGen.create(rightSlot);
        LLVMI32Node opNode = null;
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
        LLVMI32Node childNode = LLVMI32ReadNodeGen.create(slot);
        this.result = LLVMI32ReadNodeGen.create(slot);
        LLVMI32Node opNode = null;
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

    public void createDivisionOperatoin(String op, String divisor) {
        FrameSlot slot = frameDescriptor.findFrameSlot(divisor);
        LLVMI32Node divisorNode = (slot != null) ? LLVMI32ReadNodeGen.create(slot) : getImmediateNode(divisor);
        LLVMNode statement = new LLVMI32UnsupportedInlineAssemblerNode();
        LLVMExpressionNode returnNode = new LLVMI32UnsupportedInlineAssemblerNode();

        if (op.equals("idivl")) {
            FrameSlot eaxSlot = frameDescriptor.findFrameSlot("%eax");
            LLVMI32Node eaxNode = LLVMI32ReadNodeGen.create(eaxSlot);
            FrameSlot edxSlot = frameDescriptor.findFrameSlot("%edx");
            LLVMI32Node edxNode = LLVMI32ReadNodeGen.create(edxSlot);
            LLVMI32Node divisionNode = LLVMAMD64IdivlNodeGen.create(divisorNode, eaxNode, edxNode);
            statement = LLVMWriteI32NodeGen.create(divisionNode, eaxSlot);
            returnNode = LLVMI32ReadNodeGen.create(eaxSlot);
        }
        // TODO: Other div instruction shall go here
        this.statements.add(statement);
        this.result = returnNode;
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
                    LLVMI32Node node = LLVMI32ArgNodeGen.create(index);
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
