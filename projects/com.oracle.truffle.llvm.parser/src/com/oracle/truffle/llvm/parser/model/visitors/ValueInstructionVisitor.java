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
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CompareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.LoadInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SelectInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ShuffleVectorInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;

public abstract class ValueInstructionVisitor implements InstructionVisitor {

    public abstract void visitValueInstruction(ValueInstruction valueInstruction);

    @Override
    public void visit(AllocateInstruction allocate) {
        visitValueInstruction(allocate);
    }

    @Override
    public void visit(BinaryOperationInstruction operation) {
        visitValueInstruction(operation);
    }

    @Override
    public void visit(BranchInstruction branch) {
    }

    @Override
    public void visit(CallInstruction call) {
        visitValueInstruction(call);
    }

    @Override
    public void visit(CastInstruction cast) {
        visitValueInstruction(cast);
    }

    @Override
    public void visit(CompareInstruction operation) {
        visitValueInstruction(operation);
    }

    @Override
    public void visit(ConditionalBranchInstruction branch) {
    }

    @Override
    public void visit(ExtractElementInstruction extract) {
        visitValueInstruction(extract);
    }

    @Override
    public void visit(ExtractValueInstruction extract) {
        visitValueInstruction(extract);
    }

    @Override
    public void visit(GetElementPointerInstruction gep) {
        visitValueInstruction(gep);
    }

    @Override
    public void visit(IndirectBranchInstruction branch) {
    }

    @Override
    public void visit(InsertElementInstruction insert) {
        visitValueInstruction(insert);
    }

    @Override
    public void visit(InsertValueInstruction insert) {
        visitValueInstruction(insert);
    }

    @Override
    public void visit(LoadInstruction load) {
        visitValueInstruction(load);
    }

    @Override
    public void visit(PhiInstruction phi) {
        visitValueInstruction(phi);
    }

    @Override
    public void visit(ReturnInstruction ret) {
    }

    @Override
    public void visit(SelectInstruction select) {
        visitValueInstruction(select);
    }

    @Override
    public void visit(ShuffleVectorInstruction shuffle) {
        visitValueInstruction(shuffle);
    }

    @Override
    public void visit(StoreInstruction store) {
    }

    @Override
    public void visit(SwitchInstruction select) {
    }

    @Override
    public void visit(SwitchOldInstruction select) {
    }

    @Override
    public void visit(UnreachableInstruction unreachable) {
    }

    @Override
    public void visit(VoidCallInstruction call) {
    }
}
