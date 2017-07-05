/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.metadata;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.visitors.FunctionVisitor;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitorAdapter;

import java.util.HashMap;
import java.util.Map;

public final class DebugInformation {

    public static DebugInformation generate() {
        return new DebugInformation();
    }

    private final Map<FunctionDefinition, SourceSection> functionSections = new HashMap<>();

    private final Map<Instruction, SourceSection> instructionSections = new HashMap<>();

    private final DIVisitor visitor = new DIVisitor();

    private DebugInformation() {
    }

    public SourceSection parseAndGetDebugInfo(FunctionDefinition function, Source bcSource) {
        visitor.visit(function, bcSource);
        return functionSections.get(function);
    }

    public SourceSection getDebugInfo(Instruction inst) {
        return instructionSections.get(inst);
    }

    private final class DIVisitor implements FunctionVisitor, InstructionVisitorAdapter {

        private final SourceSectionGenerator generator;

        private SourceSection farthestSection;

        private DIVisitor() {
            this.generator = new SourceSectionGenerator();
        }

        public void visit(FunctionDefinition function, Source bcSource) {
            SourceSection section = generator.getOrDefault(function);
            farthestSection = section;

            if (section == null) {
                // debug information is not available or the current function is not included in it
                String sourceText = String.format("%s:%s", bcSource.getName(), function.getName());
                Source irSource = Source.newBuilder(sourceText).mimeType(SourceSectionGenerator.MIMETYPE_PLAINTEXT).name(sourceText).build();
                section = irSource.createSection(1);

            } else {
                function.accept(this);
                try {
                    // Truffle breakpoints are only hit if the text referenced by the SourceSection
                    // of the corresponding node is fully contained in its rootnode's
                    // sourcesection's text
                    int newLength = farthestSection.getCharEndIndex() - section.getCharIndex();
                    section = section.getSource().createSection(section.getStartLine(), section.getStartColumn(), newLength);
                } catch (Throwable ignored) {
                    // this might fail in case the source file was modified after compilation
                }
            }

            functionSections.put(function, section);
            farthestSection = null;
        }

        @Override
        public void visit(InstructionBlock block) {
            block.accept(this);
        }

        @Override
        public void defaultAction(Instruction inst) {
            SourceSection instLoc = generator.getOrDefault(inst);
            if (instLoc != null && farthestSection.getCharEndIndex() < instLoc.getCharEndIndex()) {
                farthestSection = instLoc;
            }
            instructionSections.put(inst, instLoc);
        }
    }
}
