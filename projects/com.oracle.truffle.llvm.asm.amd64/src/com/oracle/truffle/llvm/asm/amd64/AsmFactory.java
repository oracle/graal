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
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64MulNodeFactory.LLVMAMD64MulbNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64MulNodeFactory.LLVMAMD64MullNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64MulNodeFactory.LLVMAMD64MulqNodeGen;
import com.oracle.truffle.llvm.nodes.asm.LLVMAMD64MulNodeFactory.LLVMAMD64MulwNodeGen;
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
import com.oracle.truffle.llvm.nodes.cast.LLVMToI32NodeFactory.LLVMI64ToI32NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI64NodeFactory.LLVMAnyToI64NodeGen;
import com.oracle.truffle.llvm.nodes.cast.LLVMToI8NodeFactory.LLVMI64ToI8NodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMArgNodeGen;
import com.oracle.truffle.llvm.nodes.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.nodes.memory.LLVMAllocInstruction.LLVMAllocaInstruction;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMI16StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMI32StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.LLVMStoreNodeFactory.LLVMI8StoreNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI16LoadNodeFactory.LLVMI16DirectLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI32LoadNodeFactory.LLVMI32ProfilingLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI64LoadNodeFactory.LLVMI64ProfilingLoadNodeGen;
import com.oracle.truffle.llvm.nodes.memory.load.LLVMI8LoadNodeFactory.LLVMI8ProfilingLoadNodeGen;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.others.LLVMUnsupportedInlineAssemblerNode.LLVMI32UnsupportedInlineAssemblerNode;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMAddressReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMDoubleReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMFloatReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMI16ReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMI32ReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMI64ReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMReadNodeFactory.LLVMI8ReadNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNode.LLVMWriteAddressNode;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteAddressNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteDoubleNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteFloatNodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI16NodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI32NodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI64NodeGen;
import com.oracle.truffle.llvm.nodes.vars.LLVMWriteNodeFactory.LLVMWriteI8NodeGen;
import com.oracle.truffle.llvm.nodes.vars.StructLiteralNode;
import com.oracle.truffle.llvm.nodes.vars.StructLiteralNode.LLVMI8StructWriteNode;
import com.oracle.truffle.llvm.runtime.types.LLVMBaseType;
import com.oracle.truffle.llvm.nodes.vars.StructLiteralNode.LLVMI16StructWriteNode;
import com.oracle.truffle.llvm.nodes.vars.StructLiteralNode.LLVMI32StructWriteNode;
import com.oracle.truffle.llvm.nodes.vars.StructLiteralNode.LLVMI64StructWriteNode;

public class AsmFactory {
    public static final int REG_START_INDEX = 1;

    private FrameDescriptor frameDescriptor;
    private List<LLVMExpressionNode> statements;
    private List<LLVMExpressionNode> arguments;
    private List<String> registers;
    private LLVMExpressionNode[] args;
    private LLVMExpressionNode result;
    private List<Argument> argInfo;
    private String asmFlags;
    private LLVMBaseType[] argTypes;
    private LLVMBaseType retType;

    public AsmFactory(LLVMExpressionNode[] args, LLVMBaseType[] argTypes, String asmFlags, LLVMBaseType retType) {
        this.args = args;
        this.argTypes = argTypes;
        this.asmFlags = asmFlags;
        this.frameDescriptor = new FrameDescriptor();
        this.statements = new ArrayList<>();
        this.arguments = new ArrayList<>();
        this.registers = new ArrayList<>();
        this.retType = retType;
        parseArguments();
    }

    private class Argument {
        private final boolean input;
        private final boolean output;
        private final boolean memory;
        private final LLVMBaseType type;
        private final int index;
        private final int inIndex;
        private final int outIndex;
        private final String source;
        private final String register;

        Argument(boolean input, boolean output, boolean memory, LLVMBaseType type, int index, int inIndex, int outIndex, String source, String register) {
            this.input = input;
            this.output = output;
            this.memory = memory;
            this.type = type;
            this.index = index;
            this.inIndex = inIndex;
            this.outIndex = outIndex;
            this.source = source;
            this.register = register;
        }

        public boolean isInput() {
            return input;
        }

        public boolean isOutput() {
            return output;
        }

        public boolean isMemory() {
            return memory;
        }

        public LLVMBaseType getType() {
            return type;
        }

        public int getIndex() {
            return index;
        }

        public int getInIndex() {
            assert isInput();
            return inIndex;
        }

        public int getOutIndex() {
            assert isOutput();
            return outIndex;
        }

        public String getRegister() {
            assert isRegister();
            return register;
        }

        public boolean isRegister() {
            return register != null;
        }

        public LLVMExpressionNode getAddress() {
            assert isMemory();
            if (output) {
                return LLVMArgNodeGen.create(outIndex);
            } else {
                throw new IllegalStateException();
            }
        }

        @Override
        public String toString() {
            return String.format("Argument[IDX=%d,I=%s,O=%s,M=%s,T=%s,S=%s]", index, input, output, memory, type, source);
        }
    }

    private void parseArguments() {
        argInfo = new ArrayList<>();
        String[] tokens = asmFlags.substring(1, asmFlags.length() - 1).split(",");

        int index = REG_START_INDEX;
        LLVMAllocaInstruction alloca = null;
        if (retType == LLVMBaseType.STRUCT) { // multiple out values
            assert args[1] instanceof LLVMAllocaInstruction;
            alloca = (LLVMAllocaInstruction) args[1];
            index++;
        }

        int outIndex = 0;

        for (String token : tokens) {
            boolean isTilde = false;
            boolean isInput = true;
            boolean isOutput = false;
            boolean isMemory = false;
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
                    default:
                        source = token.substring(i);
                        break;
                }
            }

            if (isTilde) {
                continue;
            }

            int start = source.indexOf('{');
            int end = source.lastIndexOf('}');
            if (start != -1 && end != -1) {
                registerName = source.substring(start + 1, end);
            }

            assert registerName == null || AsmRegisterOperand.isRegister(registerName);

            int idIn = index;
            int idOut = outIndex;
            LLVMBaseType type;
            if (isInput) {
                type = argTypes[index++];
            } else if (retType == LLVMBaseType.STRUCT) {
                if (isMemory) {
                    type = LLVMBaseType.ADDRESS;
                    idOut = index++;
                } else {
                    type = alloca.getType(outIndex++);
                }
            } else if (isOutput) {
                type = retType;
                if (isMemory) {
                    idOut = index++;
                }
            } else {
                throw new AssertionError("neither input nor output");
            }
            argInfo.add(new Argument(isInput, isOutput, isMemory, type, argInfo.size(), idIn, idOut, source, registerName));
        }
        assert index == argTypes.length;
        assert retType == LLVMBaseType.STRUCT ? outIndex == alloca.getOffsets().length : outIndex == 0;
    }

    public LLVMInlineAssemblyRootNode finishInline() {
        getArguments();
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
            case "rdtsc": {
                LLVMAMD64WriteI64RegisterNode high = getRegisterStore("rdx");
                LLVMAMD64WriteI64RegisterNode low = getRegisterStore("rax");
                statements.add(LLVMAMD64RdtscNodeGen.create(low, high));
                break;
            }
            default:
                statements.add(new LLVMI32UnsupportedInlineAssemblerNode());
                return;
        }
    }

    public void createUnaryOperation(String operation, AsmOperand operand) {
        LLVMExpressionNode src;
        LLVMExpressionNode out;
        AsmOperand dst = operand;
        LLVMBaseType dstType;
        assert operation.length() > 0;
        switch (operation.charAt(operation.length() - 1)) {
            case 'b':
                src = getOperandLoad(LLVMBaseType.I8, operand);
                dstType = LLVMBaseType.I8;
                break;
            case 'w':
                src = getOperandLoad(LLVMBaseType.I16, operand);
                dstType = LLVMBaseType.I16;
                break;
            case 'l':
                src = getOperandLoad(LLVMBaseType.I32, operand);
                dstType = LLVMBaseType.I32;
                break;
            case 'q':
                src = getOperandLoad(LLVMBaseType.I64, operand);
                dstType = LLVMBaseType.I64;
                break;
            default:
                src = null;
                dstType = LLVMBaseType.I64;
        }
        switch (operation) {
            case "incb":
                out = LLVMAMD64IncbNodeGen.create(src);
                break;
            case "incw":
                out = LLVMAMD64IncwNodeGen.create(src);
                break;
            case "incl":
                out = LLVMAMD64InclNodeGen.create(src);
                break;
            case "incq":
                out = LLVMAMD64IncqNodeGen.create(src);
                break;
            case "decb":
                out = LLVMAMD64DecbNodeGen.create(src);
                break;
            case "decw":
                out = LLVMAMD64DecwNodeGen.create(src);
                break;
            case "decl":
                out = LLVMAMD64DeclNodeGen.create(src);
                break;
            case "decq":
                out = LLVMAMD64DecqNodeGen.create(src);
                break;
            case "negb":
                out = LLVMAMD64NegbNodeGen.create(src);
                break;
            case "negw":
                out = LLVMAMD64NegwNodeGen.create(src);
                break;
            case "negl":
                out = LLVMAMD64NeglNodeGen.create(src);
                break;
            case "negq":
                out = LLVMAMD64NegqNodeGen.create(src);
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
                out = LLVMAMD64IdivbNodeGen.create(getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            case "idivw": {
                LLVMAMD64WriteI16RegisterNode rem = getRegisterStore("dx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("dx"));
                out = LLVMAMD64IdivwNodeGen.create(rem, high, getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            }
            case "idivl": {
                LLVMAMD64WriteI32RegisterNode rem = getRegisterStore("edx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("edx"));
                out = LLVMAMD64IdivlNodeGen.create(rem, high, getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("eax")), src);
                dst = new AsmRegisterOperand("eax");
                dstType = LLVMBaseType.I32;
                break;
            }
            case "idivq": {
                LLVMAMD64WriteI64RegisterNode rem = getRegisterStore("rdx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("rdx"));
                out = LLVMAMD64IdivqNodeGen.create(rem, high, getOperandLoad(LLVMBaseType.I64, new AsmRegisterOperand("rax")), src);
                dst = new AsmRegisterOperand("rax");
                dstType = LLVMBaseType.I64;
                break;
            }
            case "imulb":
                out = LLVMAMD64ImulbNodeGen.create(getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            case "imulw": {
                LLVMAMD64WriteI16RegisterNode high = getRegisterStore("dx");
                out = LLVMAMD64ImulwNodeGen.create(high, getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            }
            case "imull": {
                LLVMAMD64WriteI32RegisterNode high = getRegisterStore("edx");
                out = LLVMAMD64ImullNodeGen.create(high, getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("eax")), src);
                dst = new AsmRegisterOperand("eax");
                dstType = LLVMBaseType.I32;
                break;
            }
            case "imulq": {
                LLVMAMD64WriteI64RegisterNode high = getRegisterStore("rdx");
                out = LLVMAMD64ImulqNodeGen.create(high, getOperandLoad(LLVMBaseType.I64, new AsmRegisterOperand("rax")), src);
                dst = new AsmRegisterOperand("rax");
                dstType = LLVMBaseType.I64;
                break;
            }
            // TODO: implement properly
            case "divb":
                out = LLVMAMD64IdivbNodeGen.create(getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            case "divw": {
                LLVMAMD64WriteI16RegisterNode rem = getRegisterStore("dx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("dx"));
                out = LLVMAMD64IdivwNodeGen.create(rem, high, getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            }
            case "divl": {
                LLVMAMD64WriteI32RegisterNode rem = getRegisterStore("edx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("edx"));
                out = LLVMAMD64IdivlNodeGen.create(rem, high, getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("eax")), src);
                dst = new AsmRegisterOperand("eax");
                dstType = LLVMBaseType.I32;
                break;
            }
            case "divq": {
                LLVMAMD64WriteI64RegisterNode rem = getRegisterStore("rdx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("rdx"));
                out = LLVMAMD64IdivqNodeGen.create(rem, high, getOperandLoad(LLVMBaseType.I64, new AsmRegisterOperand("rax")), src);
                dst = new AsmRegisterOperand("rax");
                dstType = LLVMBaseType.I64;
                break;
            }
            case "mulb":
                src = getOperandLoad(LLVMBaseType.I16, operand);
                out = LLVMAMD64MulbNodeGen.create(getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            case "mulw": {
                LLVMAMD64WriteI16RegisterNode high = getRegisterStore("dx");
                out = LLVMAMD64MulwNodeGen.create(high, getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("ax")), src);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            }
            case "mull": {
                LLVMAMD64WriteI32RegisterNode high = getRegisterStore("edx");
                out = LLVMAMD64MullNodeGen.create(high, getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("eax")), src);
                dst = new AsmRegisterOperand("eax");
                dstType = LLVMBaseType.I32;
                break;
            }
            case "mulq": {
                LLVMAMD64WriteI64RegisterNode high = getRegisterStore("rdx");
                out = LLVMAMD64MulqNodeGen.create(high, getOperandLoad(LLVMBaseType.I64, new AsmRegisterOperand("rax")), src);
                dst = new AsmRegisterOperand("rax");
                dstType = LLVMBaseType.I64;
                break;
            }
            default:
                statements.add(new LLVMI32UnsupportedInlineAssemblerNode());
                return;
        }
        LLVMExpressionNode write = getOperandStore(dstType, dst, out);
        statements.add(write);
    }

    public void createBinaryOperation(String operation, AsmOperand a, AsmOperand b) {
        LLVMExpressionNode srcA;
        LLVMExpressionNode srcB;
        LLVMExpressionNode out;
        AsmOperand dst = b;
        LLVMBaseType dstType;
        switch (operation.charAt(operation.length() - 1)) {
            case 'b':
                srcA = getOperandLoad(LLVMBaseType.I8, a);
                srcB = getOperandLoad(LLVMBaseType.I8, b);
                dstType = LLVMBaseType.I8;
                break;
            case 'w':
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                srcB = getOperandLoad(LLVMBaseType.I16, b);
                dstType = LLVMBaseType.I16;
                break;
            case 'l':
                srcA = getOperandLoad(LLVMBaseType.I32, a);
                srcB = getOperandLoad(LLVMBaseType.I32, b);
                dstType = LLVMBaseType.I32;
                break;
            case 'q':
                srcA = getOperandLoad(LLVMBaseType.I64, a);
                srcB = getOperandLoad(LLVMBaseType.I64, b);
                dstType = LLVMBaseType.I64;
                break;
            default:
                srcA = null;
                srcB = null;
                dstType = LLVMBaseType.I8;
        }
        switch (operation) {
            case "addb":
                out = LLVMAMD64AddbNodeGen.create(srcA, srcB);
                break;
            case "addw":
                out = LLVMAMD64AddwNodeGen.create(srcA, srcB);
                break;
            case "addl":
                out = LLVMAMD64AddlNodeGen.create(srcA, srcB);
                break;
            case "addq":
                out = LLVMAMD64AddqNodeGen.create(srcA, srcB);
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
                srcA = getOperandLoad(LLVMBaseType.I8, a);
                srcB = getOperandLoad(LLVMBaseType.I16, b);
                out = LLVMAMD64IdivbNodeGen.create(srcB, srcA);
                dst = new AsmRegisterOperand("ax");
                dstType = LLVMBaseType.I16;
                break;
            case "idivw": {
                LLVMAMD64WriteI16RegisterNode rem = getRegisterStore("dx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I16, new AsmRegisterOperand("dx"));
                out = LLVMAMD64IdivwNodeGen.create(rem, high, srcB, srcA);
                dst = new AsmRegisterOperand("ax");
                break;
            }
            case "idivl": {
                LLVMAMD64WriteI32RegisterNode rem = getRegisterStore("edx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("edx"));
                out = LLVMAMD64IdivlNodeGen.create(rem, high, srcB, srcA);
                dst = new AsmRegisterOperand("eax");
                break;
            }
            case "idivq": {
                LLVMAMD64WriteI64RegisterNode rem = getRegisterStore("rdx");
                LLVMExpressionNode high = getOperandLoad(LLVMBaseType.I32, new AsmRegisterOperand("rdx"));
                out = LLVMAMD64IdivqNodeGen.create(rem, high, srcB, srcA);
                dst = new AsmRegisterOperand("rax");
                break;
            }
            case "imulb":
                srcA = getOperandLoad(LLVMBaseType.I16, a);
                srcB = getOperandLoad(LLVMBaseType.I8, b);
                out = LLVMAMD64ImulbNodeGen.create(srcA, srcB);
                dstType = LLVMBaseType.I16;
                break;
            case "imulw": {
                LLVMAMD64WriteI16RegisterNode high = getRegisterStore("dx");
                out = LLVMAMD64ImulwNodeGen.create(high, srcA, srcB);
                break;
            }
            case "imull": {
                LLVMAMD64WriteI32RegisterNode high = getRegisterStore("edx");
                out = LLVMAMD64ImullNodeGen.create(high, srcA, srcB);
                break;
            }
            case "imulq": {
                LLVMAMD64WriteI64RegisterNode high = getRegisterStore("rdx");
                out = LLVMAMD64ImulqNodeGen.create(high, srcA, srcB);
                break;
            }
            case "movb":
            case "movw":
            case "movl":
            case "movq":
                out = srcA;
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
            case "andb":
                out = LLVMAMD64AndbNodeGen.create(srcA, srcB);
                break;
            case "andw":
                out = LLVMAMD64AndwNodeGen.create(srcA, srcB);
                break;
            case "andl":
                out = LLVMAMD64AndlNodeGen.create(srcA, srcB);
                break;
            case "andq":
                out = LLVMAMD64AndqNodeGen.create(srcA, srcB);
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
            default:
                statements.add(new LLVMI32UnsupportedInlineAssemblerNode());
                return;
        }
        LLVMExpressionNode write = getOperandStore(dstType, dst, out);
        statements.add(write);
    }

    @SuppressWarnings("unused")
    public void createTernaryOperation(String op, AsmOperand a, AsmOperand b, AsmOperand c) {
    }

    protected void addFrameSlot(String reg, LLVMBaseType type) {
        if (!registers.contains(reg)) {
            registers.add(reg);
            FrameSlotKind kind;
            switch (type) {
                case I8:
                    kind = FrameSlotKind.Byte;
                    break;
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
        LLVMAllocaInstruction alloca = null;
        LLVMStructWriteNode[] writeNodes = null;
        if (retType == LLVMBaseType.STRUCT) {
            assert args[1] instanceof LLVMAllocaInstruction;
            alloca = (LLVMAllocaInstruction) args[1];
            writeNodes = new LLVMStructWriteNode[alloca.getLength()];
        }

        Set<String> todoRegisters = new HashSet<>(registers);
        for (Argument arg : argInfo) {
            // output register
            if (arg.isOutput()) {
                FrameSlot slot = null;
                if (arg.isRegister()) {
                    slot = getRegisterSlot(arg.getRegister());
                    LLVMExpressionNode register = LLVMI64ReadNodeGen.create(slot);
                    if (retType == LLVMBaseType.STRUCT) {
                        assert alloca.getType(arg.getOutIndex()) == arg.getType();
                        switch (arg.getType()) {
                            case I8:
                                writeNodes[arg.getOutIndex()] = new LLVMI8StructWriteNode(LLVMI64ToI8NodeGen.create(register));
                                break;
                            case I16:
                                writeNodes[arg.getOutIndex()] = new LLVMI16StructWriteNode(LLVMI64ToI16NodeGen.create(register));
                                break;
                            case I32:
                                writeNodes[arg.getOutIndex()] = new LLVMI32StructWriteNode(LLVMI64ToI32NodeGen.create(register));
                                break;
                            case I64:
                                writeNodes[arg.getOutIndex()] = new LLVMI64StructWriteNode(register);
                                break;
                            default:
                                throw new AsmParseException("invalid operand size");
                        }
                    } else {
                        result = castResult(register);
                    }
                } else {
                    slot = getArgumentSlot(arg.getIndex(), retType);
                    switch (retType) {
                        case I8:
                            result = LLVMI8ReadNodeGen.create(slot);
                            break;
                        case I16:
                            result = LLVMI16ReadNodeGen.create(slot);
                            break;
                        case I32:
                            result = LLVMI32ReadNodeGen.create(slot);
                            break;
                        case I64:
                            result = LLVMI64ReadNodeGen.create(slot);
                            break;
                        case STRUCT:
                            switch (arg.getType()) {
                                case I8:
                                    writeNodes[arg.getOutIndex()] = new LLVMI8StructWriteNode(LLVMI8ReadNodeGen.create(slot));
                                    break;
                                case I16:
                                    writeNodes[arg.getOutIndex()] = new LLVMI16StructWriteNode(LLVMI16ReadNodeGen.create(slot));
                                    break;
                                case I32:
                                    writeNodes[arg.getOutIndex()] = new LLVMI32StructWriteNode(LLVMI32ReadNodeGen.create(slot));
                                    break;
                                case I64:
                                    writeNodes[arg.getOutIndex()] = new LLVMI64StructWriteNode(LLVMI64ReadNodeGen.create(slot));
                                    break;
                                default:
                                    throw new AsmParseException("invalid operand size");
                            }
                            break;
                        case VOID:
                            result = null;
                            break;
                        // FIXME: implement properly
                        case FLOAT:
                            result = LLVMFloatReadNodeGen.create(slot);
                            break;
                        case DOUBLE:
                            result = LLVMDoubleReadNodeGen.create(slot);
                            break;
                        case ADDRESS:
                            result = LLVMAddressReadNodeGen.create(slot);
                            break;
                        default:
                            throw new AsmParseException("invalid operand size: " + retType);
                    }
                }
            }

            // input register
            if (arg.isInput()) {
                FrameSlot slot = null;
                if (arg.isRegister()) {
                    String reg = AsmRegisterOperand.getBaseRegister(arg.getRegister());
                    slot = getRegisterSlot(reg);
                    todoRegisters.remove(reg);
                    LLVMExpressionNode argnode = LLVMArgNodeGen.create(arg.getInIndex());
                    LLVMExpressionNode node = LLVMAnyToI64NodeGen.create(argnode);
                    arguments.add(LLVMWriteI64NodeGen.create(node, slot));
                }
                slot = getArgumentSlot(arg.getIndex(), argTypes[arg.getInIndex()]);
                LLVMExpressionNode argnode = LLVMArgNodeGen.create(arg.getInIndex());
                switch (arg.getType()) {
                    case I8:
                        arguments.add(LLVMWriteI8NodeGen.create(argnode, slot));
                        break;
                    case I16:
                        arguments.add(LLVMWriteI16NodeGen.create(argnode, slot));
                        break;
                    case I32:
                        arguments.add(LLVMWriteI32NodeGen.create(argnode, slot));
                        break;
                    case I64:
                        arguments.add(LLVMWriteI64NodeGen.create(argnode, slot));
                        break;
                    case FLOAT:
                        arguments.add(LLVMWriteFloatNodeGen.create(argnode, slot));
                        break;
                    case DOUBLE:
                        arguments.add(LLVMWriteDoubleNodeGen.create(argnode, slot));
                        break;
                    case ADDRESS:
                        arguments.add(LLVMWriteAddressNodeGen.create(argnode, slot));
                        break;
                    default:
                        throw new AsmParseException("invalid operand size: " + arg.getType());
                }
            }
        }

        if (retType == LLVMBaseType.STRUCT) {
            LLVMExpressionNode addrArg = LLVMArgNodeGen.create(1);
            FrameSlot slot = frameDescriptor.addFrameSlot("returnValue", FrameSlotKind.Object);
            LLVMWriteAddressNode writeAddr = LLVMWriteAddressNodeGen.create(addrArg, slot);
            statements.add(writeAddr);
            LLVMExpressionNode addr = LLVMAddressReadNodeGen.create(slot);
            this.result = new StructLiteralNode(alloca.getOffsets(), writeNodes, addr);
        }

        // initialize registers
        for (String register : todoRegisters) {
            if (register.startsWith("$")) {
                continue;
            }
            LLVMExpressionNode node = LLVMAMD64I64NodeGen.create(0);
            FrameSlot slot = getRegisterSlot(register);
            arguments.add(LLVMWriteI64NodeGen.create(node, slot));
        }

        assert retType == LLVMBaseType.VOID || retType != null;
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
            FrameSlot frame = getRegisterSlot(op.getBaseRegister());
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
        } else if (operand instanceof AsmArgumentOperand) {
            AsmArgumentOperand op = (AsmArgumentOperand) operand;
            Argument info = argInfo.get(op.getIndex());
            FrameSlot frame = getArgumentSlot(op.getIndex(), type);
            if (info.isMemory()) {
                switch (type) {
                    case I8:
                        return LLVMI8ProfilingLoadNodeGen.create(LLVMAddressReadNodeGen.create(frame));
                    case I16:
                        return LLVMI16DirectLoadNodeGen.create(LLVMAddressReadNodeGen.create(frame));
                    case I32:
                        return LLVMI32ProfilingLoadNodeGen.create(LLVMAddressReadNodeGen.create(frame));
                    case I64:
                        return LLVMI64ProfilingLoadNodeGen.create(LLVMAddressReadNodeGen.create(frame));
                    default:
                        throw new AsmParseException("invalid operand size");
                }
            } else {
                assert type == info.getType();
                switch (type) {
                    case I8:
                        return LLVMI8ReadNodeGen.create(frame);
                    case I16:
                        return LLVMI16ReadNodeGen.create(frame);
                    case I32:
                        return LLVMI32ReadNodeGen.create(frame);
                    case I64:
                        return LLVMI64ReadNodeGen.create(frame);
                    default:
                        throw new AsmParseException("invalid operand size");
                }
            }
        }
        throw new AsmParseException("unsupported operand type: " + operand);
    }

    private LLVMExpressionNode getOperandStore(LLVMBaseType type, AsmOperand operand, LLVMExpressionNode from) {
        if (operand instanceof AsmRegisterOperand) {
            AsmRegisterOperand op = (AsmRegisterOperand) operand;
            FrameSlot frame = getRegisterSlot(op.getBaseRegister());
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
        } else if (operand instanceof AsmArgumentOperand) {
            AsmArgumentOperand op = (AsmArgumentOperand) operand;
            Argument info = argInfo.get(op.getIndex());
            if (info.isMemory()) {
                LLVMExpressionNode address = info.getAddress();
                switch (type) {
                    case I8:
                        return LLVMI8StoreNodeGen.create(address, from);
                    case I16:
                        return LLVMI16StoreNodeGen.create(address, from);
                    case I32:
                        return LLVMI32StoreNodeGen.create(address, from);
                    case I64:
                        return LLVMI64StoreNodeGen.create(address, from);
                    default:
                        throw new AsmParseException("invalid operand size");
                }
            } else {
                assert type == info.getType();
                FrameSlot frame = getArgumentSlot(op.getIndex(), type);
                switch (type) {
                    case I8:
                        return LLVMWriteI8NodeGen.create(from, frame);
                    case I16:
                        return LLVMWriteI16NodeGen.create(from, frame);
                    case I32:
                        return LLVMWriteI32NodeGen.create(from, frame);
                    case I64:
                        return LLVMWriteI64NodeGen.create(from, frame);
                    default:
                        throw new AsmParseException("invalid operand size");
                }
            }
        }
        throw new AsmParseException("unsupported operand type: " + operand);
    }

    @SuppressWarnings("unchecked")
    private <T extends LLVMAMD64WriteRegisterNode> T getRegisterStore(String name) {
        AsmRegisterOperand op = new AsmRegisterOperand(name);
        FrameSlot frame = getRegisterSlot(name);
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

    private FrameSlot getRegisterSlot(String name) {
        AsmRegisterOperand op = new AsmRegisterOperand(name);
        String baseRegister = op.getBaseRegister();
        addFrameSlot(baseRegister, LLVMBaseType.I64);
        FrameSlot frame = frameDescriptor.findFrameSlot(baseRegister);
        return frame;
    }

    private static String getArgumentName(int index) {
        return "$" + index;
    }

    private FrameSlot getArgumentSlot(int index, LLVMBaseType type) {
        Argument info = argInfo.get(index);
        assert info.isMemory() || type == info.getType();

        String name = getArgumentName(index);
        addFrameSlot(name, type);
        return frameDescriptor.findFrameSlot(name);
    }
}
