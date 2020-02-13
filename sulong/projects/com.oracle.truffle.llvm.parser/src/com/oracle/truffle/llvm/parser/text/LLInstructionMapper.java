/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.text;

import java.util.ArrayDeque;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.SourceFunction;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.functions.FunctionParameter;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgDeclareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DebugTrapInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.FenceInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReadModifyWriteInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ResumeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidInvokeInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.ValueInstructionVisitor;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.options.TargetStream;
import com.oracle.truffle.llvm.runtime.types.symbols.LLVMIdentifier;

final class LLInstructionMapper {

    private static final class Mapper extends ValueInstructionVisitor implements FunctionVisitor {

        private final Source llSource;
        private final LLVMSourceLocation parentLocation;
        private final ArrayDeque<LLSourceMap.Instruction> llInstructions;

        private Mapper(Source llSource, LLVMSourceLocation parentLocation, ArrayDeque<LLSourceMap.Instruction> llInstructions) {
            this.llSource = llSource;
            this.parentLocation = parentLocation;
            this.llInstructions = llInstructions;
        }

        LLVMSourceLocation toLocation(LLSourceMap.Instruction instruction) {
            return instruction.toSourceLocation(llSource, parentLocation);
        }

        private void assignInstructionLocation(Instruction inst, String... opCodes) {
            LLSourceMap.Instruction expected = llInstructions.peekFirst();
            while (expected != null) {
                for (String opCode : opCodes) {
                    if (opCode.equals(expected.getDescriptor())) {
                        inst.setSourceLocation(toLocation(expected));
                        llInstructions.pollFirst();
                        return;
                    }
                }
                if (expected.getDescriptor().startsWith("%")) {
                    // stop skipping remainders of multi-line instructions once we find the
                    // definition of the next SSA-value
                    return;
                } else {
                    llInstructions.pollFirst();
                    expected = llInstructions.peekFirst();
                }
            }
        }

        @Override
        public void visitValueInstruction(ValueInstruction value) {
            assignInstructionLocation(value, LLVMIdentifier.toLocalIdentifier(value.getName()));
        }

        @Override
        public void visit(BranchInstruction inst) {
            assignInstructionLocation(inst, "br");
        }

        @Override
        public void visit(ConditionalBranchInstruction inst) {
            assignInstructionLocation(inst, "br");
        }

        @Override
        public void visit(IndirectBranchInstruction inst) {
            assignInstructionLocation(inst, "indirectbr");
        }

        @Override
        public void visit(ReturnInstruction inst) {
            assignInstructionLocation(inst, "ret");
        }

        @Override
        public void visit(StoreInstruction inst) {
            assignInstructionLocation(inst, "store");
        }

        @Override
        public void visit(SwitchInstruction inst) {
            assignInstructionLocation(inst, "switch");
        }

        @Override
        public void visit(SwitchOldInstruction inst) {
            assignInstructionLocation(inst, "switch");
        }

        @Override
        public void visit(UnreachableInstruction inst) {
            assignInstructionLocation(inst, "unreachable");
        }

        @Override
        public void visit(VoidCallInstruction inst) {
            assignInstructionLocation(inst, "tail", "musttail", "notail", "call");
        }

        @Override
        public void visit(VoidInvokeInstruction inst) {
            assignInstructionLocation(inst, "invoke");
        }

        @Override
        public void visit(ResumeInstruction inst) {
            assignInstructionLocation(inst, "resume");
        }

        @Override
        public void visit(FenceInstruction inst) {
            assignInstructionLocation(inst, "fence");
        }

        @Override
        public void visit(DbgDeclareInstruction inst) {
            assignInstructionLocation(inst, "tail", "call");
        }

        @Override
        public void visit(DbgValueInstruction inst) {
            assignInstructionLocation(inst, "tail", "call");
        }

        @Override
        public void visit(ReadModifyWriteInstruction inst) {
            assignInstructionLocation(inst, "atomicrmw");
        }

        @Override
        public void visit(DebugTrapInstruction inst) {
            assignInstructionLocation(inst, "tail", "call");
        }

        @Override
        public void visit(InstructionBlock block) {
            block.accept(this);
        }
    }

    private static void fillInNames(FunctionDefinition function) {
        int symbolIndex = 0;

        // in K&R style function declarations the parameters are not assigned names
        for (final FunctionParameter parameter : function.getParameters()) {
            if (LLVMIdentifier.UNKNOWN.equals(parameter.getName())) {
                parameter.setName(String.valueOf(symbolIndex++));
            }
        }

        final Set<String> explicitBlockNames = function.getBlocks().stream().map(InstructionBlock::getName).filter(blockName -> !LLVMIdentifier.isUnknown(blockName)).collect(Collectors.toSet());
        for (final InstructionBlock block : function.getBlocks()) {
            if (LLVMIdentifier.isUnknown(block.getName())) {
                do {
                    block.setName(LLVMIdentifier.toImplicitBlockName(symbolIndex++));
                    // avoid name clashes
                } while (explicitBlockNames.contains(block.getName()));
            }
            for (int i = 0; i < block.getInstructionCount(); i++) {
                final Instruction instruction = block.getInstruction(i);
                if (instruction instanceof ValueInstruction) {
                    final ValueInstruction value = (ValueInstruction) instruction;
                    if (LLVMIdentifier.isUnknown(value.getName())) {
                        value.setName(String.valueOf(symbolIndex++));
                    }
                }
            }
        }
    }

    static void setSourceLocations(LLSourceMap sourceMap, FunctionDefinition functionDefinition, LLVMParserRuntime runtime) {
        final LLSourceMap.Function function = sourceMap.getFunction(functionDefinition.getName());
        if (function == null) {
            TargetStream stream = runtime.getContext().llDebugVerboseStream();
            if (stream != null) {
                stream.println("Cannot find .ll source for function " + functionDefinition.getName());
            }
            return;
        }

        final LLVMSourceLocation location = function.toSourceLocation(sourceMap, runtime);
        SourceFunction sourceFunction = functionDefinition.getSourceFunction();
        sourceFunction = new SourceFunction(location, sourceFunction.getSourceType());
        functionDefinition.setSourceFunction(sourceFunction);

        fillInNames(functionDefinition);
        functionDefinition.accept((FunctionVisitor) new Mapper(sourceMap.getLLSource(), location, function.getInstructionList()));

        sourceMap.clearFunction(function);
    }
}
