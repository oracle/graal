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
package com.oracle.truffle.llvm.parser.impl;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;

import com.intel.llvm.ireditor.lLVM_IR.Argument;
import com.intel.llvm.ireditor.lLVM_IR.BinaryInstruction;
import com.intel.llvm.ireditor.lLVM_IR.BitwiseBinaryInstruction;
import com.intel.llvm.ireditor.lLVM_IR.ConversionInstruction;
import com.intel.llvm.ireditor.lLVM_IR.GlobalValueRef;
import com.intel.llvm.ireditor.lLVM_IR.Instruction;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_alloca;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_br;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_call_nonVoid;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_extractelement;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_extractvalue;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_fcmp;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_getelementptr;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_icmp;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_indirectbr;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_insertelement;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_insertvalue;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_load;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_ret;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_select;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_shufflevector;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_store;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_switch;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_unreachable;
import com.intel.llvm.ireditor.lLVM_IR.LocalValue;
import com.intel.llvm.ireditor.lLVM_IR.LocalValueRef;
import com.intel.llvm.ireditor.lLVM_IR.MiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.NamedMiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.StartingInstruction;
import com.intel.llvm.ireditor.lLVM_IR.TerminatorInstruction;
import com.intel.llvm.ireditor.lLVM_IR.TypedValue;
import com.intel.llvm.ireditor.lLVM_IR.ValueRef;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;

public final class LLVMReadVisitor {

    private final List<FrameSlot> reads = new ArrayList<>();
    private FrameDescriptor frameDescriptor;
    private boolean countPhiValues;

    public List<FrameSlot> getReads(Instruction instr, FrameDescriptor descriptor, boolean alsoCountPhiUsages) {
        frameDescriptor = descriptor;
        this.countPhiValues = alsoCountPhiUsages;
        if (instr instanceof TerminatorInstruction) {
            getTerminatorInstructionReads((TerminatorInstruction) instr);
        } else if (instr instanceof MiddleInstruction) {
            getMiddleInstructionReads((MiddleInstruction) instr);
        } else if (instr instanceof StartingInstruction) {
            getStartingInstructionReads((StartingInstruction) instr);
        } else {
            throw new AssertionError(instr);
        }
        return reads;
    }

    private void getStartingInstructionReads(StartingInstruction instr) {
        if (countPhiValues) {
            for (ValueRef value : instr.getInstruction().getValues()) {
                visitValueRef(value);
            }
        }
    }

    private void getMiddleInstructionReads(MiddleInstruction middleInstr) {
        EObject instr = middleInstr.getInstruction();
        if (instr instanceof NamedMiddleInstruction) {
            visitNamedMiddleInstructionRead((NamedMiddleInstruction) instr);
        } else if (instr instanceof Instruction_store) {
            visitStoreInstructionRead((Instruction_store) instr);
        } else if (instr instanceof Instruction_call_nonVoid) {
            visitFunctionCallRead((Instruction_call_nonVoid) instr);
        } else {
            throw new AssertionError(instr);
        }
    }

    private void visitFunctionCallRead(Instruction_call_nonVoid instr) {
        for (Argument arg : instr.getArgs().getArguments()) {
            visitValueRef(arg.getRef());
        }
        if (instr.getCallee() instanceof ValueRef) {
            visitValueRef((ValueRef) instr.getCallee());
        }
    }

    private void visitStoreInstructionRead(Instruction_store instr) {
        visitValueRef(instr.getPointer().getRef());
        visitValueRef(instr.getValue().getRef());
    }

    private void visitNamedMiddleInstructionRead(NamedMiddleInstruction namedMiddleInstr) {
        EObject instr = namedMiddleInstr.getInstruction();
        if (instr instanceof BinaryInstruction) {
            BinaryInstruction binaryInstruction = (BinaryInstruction) instr;
            visitBinaryArithmeticInstructionRead(binaryInstruction);
        } else if (instr instanceof BitwiseBinaryInstruction) {
            BitwiseBinaryInstruction bitwiseInstruction = (BitwiseBinaryInstruction) instr;
            visitBinaryLogicalInstructionRead(bitwiseInstruction);
        } else if (instr instanceof ConversionInstruction) {
            visitConversionConstructionRead((ConversionInstruction) instr);
        } else if (instr instanceof Instruction_call_nonVoid) {
            visitFunctionCallRead((Instruction_call_nonVoid) instr);
        } else if (instr instanceof Instruction_alloca) {
            visitAllocaInstructionRead((Instruction_alloca) instr);
        } else if (instr instanceof Instruction_load) {
            visitLoadInstructionRead((Instruction_load) instr);
        } else if (instr instanceof Instruction_icmp) {
            visitIcmpInstructionRead((Instruction_icmp) instr);
        } else if (instr instanceof Instruction_fcmp) {
            visitFcmpInstructionRead((Instruction_fcmp) instr);
        } else if (instr instanceof Instruction_getelementptr) {
            visitGetElementPtrRead((Instruction_getelementptr) instr);
        } else if (instr instanceof Instruction_select) {
            visitSelectInstrRead((Instruction_select) instr);
        } else if (instr instanceof Instruction_insertvalue) {
            visitInsertValueInstrRead((Instruction_insertvalue) instr);
        } else if (instr instanceof Instruction_extractelement) {
            visitExtractElementRead((Instruction_extractelement) instr);
        } else if (instr instanceof Instruction_insertelement) {
            visitInsertElementRead((Instruction_insertelement) instr);
        } else if (instr instanceof Instruction_extractvalue) {
            visitExtractValueRead((Instruction_extractvalue) instr);
        } else if (instr instanceof Instruction_shufflevector) {
            visitShuffleVectorRead((Instruction_shufflevector) instr);
        } else {
            throw new AssertionError(instr);
        }
    }

    private void visitShuffleVectorRead(Instruction_shufflevector instr) {
        visitValueRef(instr.getMask().getRef());
        visitValueRef(instr.getVector1().getRef());
        visitValueRef(instr.getVector2().getRef());
    }

    private void visitExtractValueRead(Instruction_extractvalue instr) {
        visitValueRef(instr.getAggregate().getRef());
    }

    private void visitInsertElementRead(Instruction_insertelement instr) {
        visitValueRef(instr.getElement().getRef());
        visitValueRef(instr.getIndex().getRef());
        visitValueRef(instr.getElement().getRef());
    }

    private void visitExtractElementRead(Instruction_extractelement instr) {
        visitValueRef(instr.getIndex().getRef());
        visitValueRef(instr.getVector().getRef());
    }

    private void visitInsertValueInstrRead(Instruction_insertvalue instr) {
        visitValueRef(instr.getAggregate().getRef());
        visitValueRef(instr.getElement().getRef());
    }

    private void visitSelectInstrRead(Instruction_select instr) {
        visitValueRef(instr.getCondition().getRef());
        visitValueRef(instr.getValue1().getRef());
        visitValueRef(instr.getValue2().getRef());
    }

    private void visitGetElementPtrRead(Instruction_getelementptr instr) {
        visitValueRef(instr.getBase().getRef());
        for (TypedValue index : instr.getIndices()) {
            visitValueRef(index.getRef());
        }
    }

    private void visitFcmpInstructionRead(Instruction_fcmp instr) {
        visitValueRef(instr.getOp1());
        visitValueRef(instr.getOp2());
    }

    private void visitIcmpInstructionRead(Instruction_icmp instr) {
        visitValueRef(instr.getOp1());
        visitValueRef(instr.getOp2());
    }

    private void visitLoadInstructionRead(Instruction_load instr) {
        visitValueRef(instr.getPointer().getRef());
    }

    private void visitAllocaInstructionRead(Instruction_alloca instr) {
        TypedValue numElementsVal = instr.getNumElements();
        if (numElementsVal != null) {
            visitValueRef(numElementsVal.getRef());
        }
    }

    private void visitConversionConstructionRead(ConversionInstruction instr) {
        visitValueRef(instr.getValue());
    }

    private void visitBinaryLogicalInstructionRead(BitwiseBinaryInstruction instr) {
        ValueRef op1 = instr.getOp1();
        ValueRef op2 = instr.getOp2();
        visitValueRef(op1);
        visitValueRef(op2);
    }

    private void visitBinaryArithmeticInstructionRead(BinaryInstruction instr) {
        ValueRef op1 = instr.getOp1();
        ValueRef op2 = instr.getOp2();
        visitValueRef(op1);
        visitValueRef(op2);
    }

    private void visitValueRef(ValueRef valueRef) {
        if (valueRef instanceof GlobalValueRef) {
            // skip: no read
        } else if (valueRef instanceof LocalValueRef) {
            LocalValueRef localValueRef = (LocalValueRef) valueRef;
            LocalValue localValue = localValueRef.getRef();
            String name = localValue.getName();
            reads.add(frameDescriptor.findFrameSlot(name));
        } else {
            throw new AssertionError(valueRef);
        }
    }

    private void getTerminatorInstructionReads(TerminatorInstruction instr) {
        EObject termInstruction = instr.getInstruction();
        if (termInstruction instanceof Instruction_ret) {
            visitRetRead((Instruction_ret) termInstruction);
        } else if (termInstruction instanceof Instruction_unreachable) {
            // skip: no read
        } else if (termInstruction instanceof Instruction_br) {
            visitBrRead((Instruction_br) termInstruction);
        } else if (termInstruction instanceof Instruction_switch) {
            visitSwitchRead((Instruction_switch) termInstruction);
        } else if (termInstruction instanceof Instruction_indirectbr) {
            visitIndirectBranchRead((Instruction_indirectbr) termInstruction);
        } else {
            throw new AssertionError(termInstruction);
        }
    }

    private void visitIndirectBranchRead(Instruction_indirectbr termInstruction) {
        visitValueRef(termInstruction.getAddress().getRef());
    }

    private void visitSwitchRead(Instruction_switch termInstruction) {
        visitValueRef(termInstruction.getComparisonValue().getRef());
    }

    private void visitBrRead(Instruction_br termInstruction) {
        if (termInstruction.getCondition() != null) {
            visitValueRef(termInstruction.getCondition().getRef());
        }
    }

    private void visitRetRead(Instruction_ret termInstruction) {
        if (termInstruction.getVal() != null) {
            visitValueRef(termInstruction.getVal().getRef());
        }
    }

}
