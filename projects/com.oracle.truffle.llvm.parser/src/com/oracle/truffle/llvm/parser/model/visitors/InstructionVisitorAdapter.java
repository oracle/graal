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
package com.oracle.truffle.llvm.parser.model.visitors;

import com.oracle.truffle.llvm.parser.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BinaryOperationInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CastInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CompareExchangeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CompareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InvokeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.LandingpadInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.LoadInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReadModifyWriteInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ResumeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SelectInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ShuffleVectorInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidInvokeInstruction;

public interface InstructionVisitorAdapter extends InstructionVisitor {

    default void defaultAction(@SuppressWarnings("unused") Instruction inst) {
    }

    @Override
    default void visit(AllocateInstruction allocate) {
        defaultAction(allocate);
    }

    @Override
    default void visit(BinaryOperationInstruction operation) {
        defaultAction(operation);
    }

    @Override
    default void visit(BranchInstruction branch) {
        defaultAction(branch);
    }

    @Override
    default void visit(CallInstruction call) {
        defaultAction(call);
    }

    @Override
    default void visit(LandingpadInstruction landingpadInstruction) {
        defaultAction(landingpadInstruction);
    }

    @Override
    default void visit(CastInstruction cast) {
        defaultAction(cast);
    }

    @Override
    default void visit(CompareInstruction operation) {
        defaultAction(operation);
    }

    @Override
    default void visit(ConditionalBranchInstruction branch) {
        defaultAction(branch);
    }

    @Override
    default void visit(ExtractElementInstruction extract) {
        defaultAction(extract);
    }

    @Override
    default void visit(ExtractValueInstruction extract) {
        defaultAction(extract);
    }

    @Override
    default void visit(GetElementPointerInstruction gep) {
        defaultAction(gep);
    }

    @Override
    default void visit(IndirectBranchInstruction branch) {
        defaultAction(branch);
    }

    @Override
    default void visit(InsertElementInstruction insert) {
        defaultAction(insert);
    }

    @Override
    default void visit(InsertValueInstruction insert) {
        defaultAction(insert);
    }

    @Override
    default void visit(LoadInstruction load) {
        defaultAction(load);
    }

    @Override
    default void visit(PhiInstruction phi) {
        defaultAction(phi);
    }

    @Override
    default void visit(ReturnInstruction ret) {
        defaultAction(ret);
    }

    @Override
    default void visit(SelectInstruction select) {
        defaultAction(select);
    }

    @Override
    default void visit(ShuffleVectorInstruction shuffle) {
        defaultAction(shuffle);
    }

    @Override
    default void visit(StoreInstruction store) {
        defaultAction(store);
    }

    @Override
    default void visit(SwitchInstruction select) {
        defaultAction(select);
    }

    @Override
    default void visit(SwitchOldInstruction select) {
        defaultAction(select);
    }

    @Override
    default void visit(UnreachableInstruction unreachable) {
        defaultAction(unreachable);
    }

    @Override
    default void visit(VoidCallInstruction call) {
        defaultAction(call);
    }

    @Override
    default void visit(InvokeInstruction invoke) {
        defaultAction(invoke);
    }

    @Override
    default void visit(VoidInvokeInstruction invoke) {
        defaultAction(invoke);
    }

    @Override
    default void visit(ResumeInstruction resume) {
        defaultAction(resume);
    }

    @Override
    default void visit(CompareExchangeInstruction cmpxchg) {
        defaultAction(cmpxchg);
    }

    @Override
    default void visit(ReadModifyWriteInstruction rmw) {
        defaultAction(rmw);
    }

}
