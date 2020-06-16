/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLanguage.InlineParsingRequest;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.nodes.DebugExprNodeFactory;
import com.oracle.truffle.llvm.runtime.debug.debugexpr.parser.DebugExprException;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMDebuggerScopeFactory;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;

import java.util.Collection;

public class DebugExprParser {

    private static final class BailoutErrorListener extends BaseErrorListener {
        private final String snippet;

        BailoutErrorListener(String snippet) {
            this.snippet = snippet;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            String location = "-- line " + line + " col " + (charPositionInLine + 1) + ": ";
            throw DebugExprException.create(null, "Debug Expression error in %s:\n%s%s", snippet, location, msg);
        }
    }

    private final DebugExpressionParser parser;
    private final DebugExpressionLexer lexer;
    private final String asmSnippet;

    public DebugExprParser(InlineParsingRequest request, Collection<Scope> globalScopes, LLVMContext context) {
        asmSnippet = request.getSource().getCharacters().toString();
        lexer = new DebugExpressionLexer(CharStreams.fromString(asmSnippet));
        parser = new DebugExpressionParser(new CommonTokenStream(lexer));

        final Collection<Scope> scopes = LLVMDebuggerScopeFactory.createSourceLevelScope(request.getLocation(), request.getFrame(), context);
        DebugExprNodeFactory nodeFactory = DebugExprNodeFactory.create(scopes, globalScopes);
        parser.setNodeFactory(nodeFactory);
    }

    public LLVMExpressionNode parse() throws DebugExprException {
        lexer.removeErrorListeners();
        parser.removeErrorListeners();

        BailoutErrorListener listener = new BailoutErrorListener(asmSnippet);
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);

        parser.debugExpr();
        LLVMExpressionNode root = parser.GetASTRoot();

        if (parser.getNumberOfSyntaxErrors() == 0) {
            return root;
        } else {
            throw DebugExprException.create(root, listener.toString().replace("\n", "").replace("\r", ""));
        }
    }
}
