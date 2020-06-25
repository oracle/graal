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
package com.oracle.truffle.llvm.parser.model.visitors;

import com.oracle.truffle.llvm.parser.ValueList;
import com.oracle.truffle.llvm.parser.metadata.MetadataSymbol;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.SourceVariable;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDeclaration;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.constants.BinaryOperationConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.BlockAddressConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CastConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.CompareConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.GetElementPointerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.InlineAsmConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.NullConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.SelectConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.StringConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.UndefinedConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.ArrayConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.StructureConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate.VectorConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.DoubleConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.FloatConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.floatingpoint.X86FP80Constant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.BigIntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.constants.integer.IntegerConstant;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalAlias;
import com.oracle.truffle.llvm.parser.model.symbols.globals.GlobalVariable;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.AllocateInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BinaryOperationInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CastInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CompareExchangeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.CompareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgDeclareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DebugTrapInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ExtractValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.FenceInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.GetElementPointerInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertElementInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.InsertValueInstruction;
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
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnaryOperationInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidInvokeInstruction;

public interface SymbolVisitor extends ValueList.ValueVisitor<SymbolImpl> {

    default void visit(ArrayConstant constant) {
        defaultAction(constant);
    }

    default void visit(StructureConstant constant) {
        defaultAction(constant);
    }

    default void visit(VectorConstant constant) {
        defaultAction(constant);
    }

    default void visit(BigIntegerConstant constant) {
        defaultAction(constant);
    }

    default void visit(BinaryOperationConstant constant) {
        defaultAction(constant);
    }

    default void visit(BlockAddressConstant constant) {
        defaultAction(constant);
    }

    default void visit(CastConstant constant) {
        defaultAction(constant);
    }

    default void visit(CompareConstant constant) {
        defaultAction(constant);
    }

    default void visit(DoubleConstant constant) {
        defaultAction(constant);
    }

    default void visit(FloatConstant constant) {
        defaultAction(constant);
    }

    default void visit(X86FP80Constant constant) {
        defaultAction(constant);
    }

    default void visit(FunctionDeclaration function) {
        defaultAction(function);
    }

    default void visit(FunctionDefinition function) {
        defaultAction(function);
    }

    default void visit(GetElementPointerConstant constant) {
        defaultAction(constant);
    }

    default void visit(InlineAsmConstant constant) {
        defaultAction(constant);
    }

    default void visit(IntegerConstant constant) {
        defaultAction(constant);
    }

    default void visit(NullConstant constant) {
        defaultAction(constant);
    }

    default void visit(StringConstant constant) {
        defaultAction(constant);
    }

    default void visit(UndefinedConstant constant) {
        defaultAction(constant);
    }

    default void visit(MetadataSymbol constant) {
        defaultAction(constant);
    }

    default void visit(AllocateInstruction inst) {
        defaultAction(inst);
    }

    default void visit(BinaryOperationInstruction inst) {
        defaultAction(inst);
    }

    default void visit(UnaryOperationInstruction inst) {
        defaultAction(inst);
    }

    default void visit(BranchInstruction inst) {
        defaultAction(inst);
    }

    default void visit(CallInstruction inst) {
        defaultAction(inst);
    }

    default void visit(LandingpadInstruction inst) {
        defaultAction(inst);
    }

    default void visit(CastInstruction inst) {
        defaultAction(inst);
    }

    default void visit(CompareInstruction inst) {
        defaultAction(inst);
    }

    default void visit(ConditionalBranchInstruction inst) {
        defaultAction(inst);
    }

    default void visit(ExtractElementInstruction inst) {
        defaultAction(inst);
    }

    default void visit(ExtractValueInstruction inst) {
        defaultAction(inst);
    }

    default void visit(GetElementPointerInstruction inst) {
        defaultAction(inst);
    }

    default void visit(IndirectBranchInstruction inst) {
        defaultAction(inst);
    }

    default void visit(InsertElementInstruction inst) {
        defaultAction(inst);
    }

    default void visit(InsertValueInstruction inst) {
        defaultAction(inst);
    }

    default void visit(LoadInstruction inst) {
        defaultAction(inst);
    }

    default void visit(PhiInstruction inst) {
        defaultAction(inst);
    }

    default void visit(ReturnInstruction inst) {
        defaultAction(inst);
    }

    default void visit(SelectInstruction inst) {
        defaultAction(inst);
    }

    default void visit(ShuffleVectorInstruction inst) {
        defaultAction(inst);
    }

    default void visit(StoreInstruction inst) {
        defaultAction(inst);
    }

    default void visit(SwitchInstruction inst) {
        defaultAction(inst);
    }

    default void visit(SwitchOldInstruction inst) {
        defaultAction(inst);
    }

    default void visit(UnreachableInstruction inst) {
        defaultAction(inst);
    }

    default void visit(VoidCallInstruction inst) {
        defaultAction(inst);
    }

    default void visit(InvokeInstruction inst) {
        defaultAction(inst);
    }

    default void visit(VoidInvokeInstruction inst) {
        defaultAction(inst);
    }

    default void visit(ResumeInstruction inst) {
        defaultAction(inst);
    }

    default void visit(CompareExchangeInstruction inst) {
        defaultAction(inst);
    }

    default void visit(ReadModifyWriteInstruction inst) {
        defaultAction(inst);
    }

    default void visit(FenceInstruction inst) {
        defaultAction(inst);
    }

    default void visit(DbgDeclareInstruction inst) {
        defaultAction(inst);
    }

    default void visit(DbgValueInstruction inst) {
        defaultAction(inst);
    }

    default void visit(DebugTrapInstruction inst) {
        defaultAction(inst);
    }

    default void visit(FunctionParameter param) {
        defaultAction(param);
    }

    default void visit(GlobalVariable global) {
        defaultAction(global);
    }

    default void visit(GlobalAlias global) {
        defaultAction(global);
    }

    default void visit(SourceVariable variable) {
        defaultAction(variable);
    }

    default void visit(SelectConstant constant) {
        defaultAction(constant);
    }
}
