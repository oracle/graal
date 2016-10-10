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
package com.oracle.truffle.llvm.parser.base.model.visitors;

import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.BinaryOperationInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.CallInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.CastInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.CompareInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ExtractValueInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.InsertValueInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.LoadInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.PhiInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.SelectInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ShuffleVectorInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.VoidInstruction;

public abstract class ReducedInstructionVisitor implements InstructionVisitor {

    public abstract void visitValueInstruction(ValueInstruction valueInstruction);

    public abstract void visitVoidInstruction(VoidInstruction voidInstruction);

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
        visitVoidInstruction(branch);
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
        visitVoidInstruction(branch);
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
        visitVoidInstruction(branch);
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
        visitVoidInstruction(ret);
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
        visitVoidInstruction(store);
    }

    @Override
    public void visit(SwitchInstruction select) {
        visitVoidInstruction(select);
    }

    @Override
    public void visit(SwitchOldInstruction select) {
        visitVoidInstruction(select);
    }

    @Override
    public void visit(UnreachableInstruction unreachable) {
        visitVoidInstruction(unreachable);
    }

    @Override
    public void visit(VoidCallInstruction call) {
        visitVoidInstruction(call);

    }
}
