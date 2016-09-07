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
package uk.ac.man.cs.llvm.ir.model.constants;

import uk.ac.man.cs.llvm.ir.types.Type;

public final class InlineAsmConstant extends AbstractConstant {

    private static final char DELIMITER = '\"';

    private final String asmExpression;

    private final String asmFlags;

    private InlineAsmConstant(Type type, String asmExpression, String asmFlags) {
        super(type);
        this.asmExpression = asmExpression;
        this.asmFlags = asmFlags;
    }

    public String getAsmExpression() {
        return asmExpression;
    }

    public String getAsmFlags() {
        return asmFlags;
    }

    @Override
    public String toString() {
        return "asm";
    }

    public static InlineAsmConstant generate(Type type, long[] args) {
        int argIndex = 0;

        int flagCount = (int) args[argIndex++];
        if (flagCount != 0) {
            // LLVM inline assembler expressions support flags indicating that the assembler
            // instructions contain possible side-effects, that the stack must be aligned in a
            // certain way and to use the intel assembly dialect instead of the default ATT. We
            // currently do not support them in either parser.
            // TODO implement inline assembler constraint flags
            throw new UnsupportedOperationException("Keywords \'sideffect\', \'alignstack\' and \'inteldialect\' are not supported yet!");
        }

        int expressionStringLength = (int) args[argIndex++];
        final StringBuilder asmExpressionBuilder = new StringBuilder(expressionStringLength + 2);
        asmExpressionBuilder.append(DELIMITER);
        while (expressionStringLength-- > 0) {
            asmExpressionBuilder.append((char) args[argIndex++]);
        }
        asmExpressionBuilder.append(DELIMITER);

        int flagsStringLength = (int) args[argIndex++];
        final StringBuilder asmFlagsBuilder = new StringBuilder(flagsStringLength + 2);
        asmFlagsBuilder.append(DELIMITER);
        while (flagsStringLength-- > 0) {
            asmFlagsBuilder.append((char) args[argIndex++]);
        }
        asmFlagsBuilder.append(DELIMITER);

        final String asmExpression = asmExpressionBuilder.toString();
        final String asmFlags = asmFlagsBuilder.toString();

        return new InlineAsmConstant(type, asmExpression, asmFlags);
    }
}
