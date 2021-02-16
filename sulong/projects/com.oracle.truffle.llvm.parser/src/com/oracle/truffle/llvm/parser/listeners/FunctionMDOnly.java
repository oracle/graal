/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.listeners;

import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.scanner.Block;
import com.oracle.truffle.llvm.parser.scanner.RecordBuffer;

public final class FunctionMDOnly implements ParserListener {

    private final FunctionDefinition function;

    private final Types types;

    private final IRScope scope;

    public FunctionMDOnly(IRScope scope, Types types, FunctionDefinition function) {
        this.scope = scope;
        this.types = types;
        this.function = function;
    }

    public void setupScope() {
        scope.startLocalScope(function);

    }

    /**
     * Only to look for an MDSubprogram that is attached to a function. MetadataSubprogramOnly
     * throws an MDSubprogramParsedException when it is parsed.
     */
    @Override
    public ParserListener enter(Block block) {
        switch (block) {
            case METADATA:
            case METADATA_ATTACHMENT:
            case METADATA_KIND:
                return new MetadataSubprogramOnly(types, scope);

            default:
                return ParserListener.DEFAULT;
        }
    }

    @Override
    public void exit() {
        /*
         * No linkageName found. This method is not called if a linkage name has been found (i.e.,
         * MDSubprogramOnly has thrown MDSubprogramParsedException), since the information which had
         * been looked for has already been found, and the state of this parser is not relevant any
         * more. In case of another parsing step of this function (due to lazy parsing), a different
         * parser is used.
         */
        scope.exitLocalScope();
    }

    @Override
    public void record(RecordBuffer buffer) {

    }

}
