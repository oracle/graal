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
import com.oracle.truffle.llvm.parser.base.model.symbols.instructions.VoidCallInstruction;

public interface InstructionVisitorAdapter extends InstructionVisitor {

    @Override
    default public void visit(AllocateInstruction allocate) {
    }

    @Override
    default public void visit(BinaryOperationInstruction operation) {
    }

    @Override
    default public void visit(BranchInstruction branch) {
    }

    @Override
    default public void visit(CallInstruction call) {
    }

    @Override
    default public void visit(CastInstruction cast) {
    }

    @Override
    default public void visit(CompareInstruction operation) {
    }

    @Override
    default public void visit(ConditionalBranchInstruction branch) {
    }

    @Override
    default public void visit(ExtractElementInstruction extract) {
    }

    @Override
    default public void visit(ExtractValueInstruction extract) {
    }

    @Override
    default public void visit(GetElementPointerInstruction gep) {
    }

    @Override
    default public void visit(IndirectBranchInstruction branch) {
    }

    @Override
    default public void visit(InsertElementInstruction insert) {
    }

    @Override
    default public void visit(InsertValueInstruction insert) {
    }

    @Override
    default public void visit(LoadInstruction load) {
    }

    @Override
    default public void visit(PhiInstruction phi) {
    }

    @Override
    default public void visit(ReturnInstruction ret) {
    }

    @Override
    default public void visit(SelectInstruction select) {
    }

    @Override
    default public void visit(ShuffleVectorInstruction shuffle) {
    }

    @Override
    default public void visit(StoreInstruction store) {
    }

    @Override
    default public void visit(SwitchInstruction select) {
    }

    @Override
    default public void visit(SwitchOldInstruction select) {
    }

    @Override
    default public void visit(UnreachableInstruction unreachable) {
    }

    @Override
    default public void visit(VoidCallInstruction call) {
    }

}
