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
package com.oracle.truffle.llvm.parser.base.model.symbols.constants;

import com.oracle.truffle.llvm.parser.base.model.enums.AsmDialect;
import com.oracle.truffle.llvm.parser.base.model.types.Type;

public final class InlineAsmConstant extends AbstractConstant {

    private static final char DELIMITER = '\"';

    private final String asmExpression;

    private final String asmFlags;

    private final AsmDialect dialect;

    private final boolean hasSideEffects;

    private final boolean stackAlign;

    private InlineAsmConstant(Type type, String asmExpression, String asmFlags, boolean hasSideEffects, boolean stackAlign, AsmDialect dialect) {
        super(type);
        this.asmExpression = asmExpression;
        this.asmFlags = asmFlags;
        this.hasSideEffects = hasSideEffects;
        this.stackAlign = stackAlign;
        this.dialect = dialect;
    }

    public String getAsmExpression() {
        return asmExpression;
    }

    public String getAsmFlags() {
        return asmFlags;
    }

    public AsmDialect getDialect() {
        return dialect;
    }

    public boolean hasSideEffects() {
        return hasSideEffects;
    }

    public boolean needsAlignedStack() {
        return stackAlign;
    }

    @Override
    public String toString() {
        return "asm";
    }

    public static InlineAsmConstant generate(Type type, long[] args) {
        int argIndex = 0;

        final int flags = (int) args[argIndex++];
        final boolean hasSideEffects = (flags & 0x1) == 0x1;
        final boolean stackAlign = (flags & 0x2) == 0x2;
        final long asmDialect = flags >> 2;

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

        return new InlineAsmConstant(type, asmExpression, asmFlags, hasSideEffects, stackAlign, AsmDialect.decode(asmDialect));
    }
}
