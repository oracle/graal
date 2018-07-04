/*
 * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.graal.code.amd64;

import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.amd64.AMD64BaseAssembler.OperandDataAnnotation;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.CompilationResult.CodeAnnotation;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

public class AMD64InstructionPatcher {

    private final Map<Integer, OperandDataAnnotation> operandAnnotations;

    public AMD64InstructionPatcher(CompilationResult compilationResult) {
        /*
         * The AMD64Assembler emits additional information for instructions that describes the
         * location of the displacement in addresses and the location of the immediate operand for
         * calls that we need to patch.
         */
        operandAnnotations = new HashMap<>();
        for (CodeAnnotation codeAnnotation : compilationResult.getAnnotations()) {
            if (codeAnnotation instanceof CompilationResultBuilder.AssemblerAnnotation) {
                Assembler.CodeAnnotation assemblerAnotation = ((CompilationResultBuilder.AssemblerAnnotation) codeAnnotation).assemblerCodeAnnotation;
                if (assemblerAnotation instanceof OperandDataAnnotation) {
                    OperandDataAnnotation operandAnnotation = (OperandDataAnnotation) assemblerAnotation;
                    operandAnnotations.put(operandAnnotation.instructionPosition, operandAnnotation);
                }
            }

        }

    }

    public static final class PatchData {
        public final int operandPosition;
        public final int operandSize;
        public final int nextInstructionPosition;
        public final int value;

        private PatchData(int position, int size, int nextInstructionPosition, int value) {
            this.operandPosition = position;
            this.operandSize = size;
            this.nextInstructionPosition = nextInstructionPosition;
            this.value = value;
        }

        public void apply(byte[] code) {
            int curValue = value;
            for (int i = 0; i < operandSize; i++) {
                assert code[operandPosition + i] == 0;
                code[operandPosition + i] = (byte) (curValue & 0xFF);
                curValue = curValue >>> 8;
            }
            assert curValue == 0;
        }
    }

    public PatchData findPatchData(int codePos, int relative) {
        OperandDataAnnotation operandData = operandAnnotations.get(codePos);
        assert operandData.instructionPosition == codePos;

        int offset = relative - (operandData.nextInstructionPosition - operandData.instructionPosition);
        return new PatchData(operandData.operandPosition, operandData.operandSize, operandData.nextInstructionPosition, offset);
    }
}
