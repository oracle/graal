/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.antlr;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage.InlineParsingRequest;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class DebugExprParser {


    private static final class BailoutErrorListener extends BaseErrorListener {
        private final String snippet;

        BailoutErrorListener(String snippet) {
            this.snippet = snippet;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            String location = "-- line " + line + " col " + (charPositionInLine + 1) + ": ";
            throw DebugExprException.create(null, String.format("ASM error in %s:\n%s%s", snippet, location, msg));
        }
    }

    private final DebugExpressionParser parser;
    private final DebugExpressionLexer lexer;
    private final String asmSnippet;

    public DebugExprParser(LLVMLanguage language, InlineParsingRequest request, Iterable<Scope> globalScopes) {
        asmSnippet = request.getSource().getCharacters().toString();
        lexer = new DebugExpressionLexer(CharStreams.fromString(asmSnippet));
        parser = new DebugExpressionParser(new CommonTokenStream(lexer));
    }

    public LLVMExpressionNode parse() throws DebugExprException {
//        final ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
//        parser.errors.errorStream = new PrintStream(errorStream);
//        parser.Parse();
//        LLVMExpressionNode root = parser.GetASTRoot();
//        if (parser.errors.count == 0) { // parsed correctly
//            return root;
//        } else {
//            throw DebugExprException.create(root, errorStream.toString().replace("\n", "").replace("\r", ""));
//        }

        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        BailoutErrorListener listener = new BailoutErrorListener(asmSnippet);
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);
        parser.r();
//        parser.snippet = asmSnippet;
//        parser.factory = new AsmFactory(language, argTypes, asmFlags, retType, retTypes, retOffsets);
//        parser.inline_assembly();
//        if (parser.root == null) {
//            throw new IllegalStateException("no roots produced by inline assembly snippet");
//        }
//        return parser.root;
        return null;
    }
}
