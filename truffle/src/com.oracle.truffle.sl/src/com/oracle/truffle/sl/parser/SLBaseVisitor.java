/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sl.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.BlockContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.FunctionContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.MemberAssignContext;
import com.oracle.truffle.sl.parser.SimpleLanguageOperationsParser.NameAccessContext;
import com.oracle.truffle.sl.runtime.SLStrings;

/**
 * Base AST visitor class, that handles common SL behaviour such as error reporting, scoping and
 * literal parsing.
 */
public abstract class SLBaseVisitor extends SimpleLanguageOperationsBaseVisitor<Void> {

    /**
     * Base implementation of parsing, which handles lexer and parser setup, and error reporting.
     */
    protected static void parseSLImpl(Source source, SLBaseVisitor visitor) {
        SimpleLanguageOperationsLexer lexer = new SimpleLanguageOperationsLexer(CharStreams.fromString(source.getCharacters().toString()));
        SimpleLanguageOperationsParser parser = new SimpleLanguageOperationsParser(new CommonTokenStream(lexer));
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        BailoutErrorListener listener = new BailoutErrorListener(source);
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);

        parser.simplelanguage().accept(visitor);
    }

    protected final SLLanguage language;
    protected final Source source;
    protected final TruffleString sourceString;

    protected SLBaseVisitor(SLLanguage language, Source source) {
        this.language = language;
        this.source = source;
        sourceString = SLStrings.fromJavaString(source.getCharacters().toString());
    }

    protected void semErr(Token token, String message) {
        assert token != null;
        throwParseError(source, token.getLine(), token.getCharPositionInLine(), token, message);
    }

    private static final class BailoutErrorListener extends BaseErrorListener {
        private final Source source;

        BailoutErrorListener(Source source) {
            this.source = source;
        }

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
            throwParseError(source, line, charPositionInLine, (Token) offendingSymbol, msg);
        }
    }

    private static void throwParseError(Source source, int line, int charPositionInLine, Token token, String message) {
        int col = charPositionInLine + 1;
        String location = "-- line " + line + " col " + col + ": ";
        int length = token == null ? 1 : Math.max(token.getStopIndex() - token.getStartIndex(), 0);
        throw new SLParseError(source, line, col, length, String.format("Error(s) parsing script:%n" + location + message));
    }

    protected TruffleString asTruffleString(Token literalToken, boolean removeQuotes) {
        int fromIndex = literalToken.getStartIndex();
        int length = literalToken.getStopIndex() - literalToken.getStartIndex() + 1;
        if (removeQuotes) {
            /* Remove the trailing and ending " */
            assert literalToken.getText().length() >= 2 && literalToken.getText().startsWith("\"") && literalToken.getText().endsWith("\"");
            fromIndex += 1;
            length -= 2;
        }
        return sourceString.substringByteIndexUncached(fromIndex * 2, length * 2, SLLanguage.STRING_ENCODING, true);
    }

    // ------------------------------- locals handling --------------------------

    private static class FindLocalsVisitor extends SimpleLanguageOperationsBaseVisitor<Void> {
        boolean entered = false;
        List<Token> results = new ArrayList<>();

        @Override
        public Void visitBlock(BlockContext ctx) {
            if (entered) {
                return null;
            }

            entered = true;
            return super.visitBlock(ctx);
        }

        @Override
        public Void visitNameAccess(NameAccessContext ctx) {
            if (ctx.member_expression().size() > 0 && ctx.member_expression(0) instanceof MemberAssignContext) {
                results.add(ctx.IDENTIFIER().getSymbol());
            }

            return super.visitNameAccess(ctx);
        }
    }

    private static class LocalScope {
        final LocalScope parent;
        final Map<TruffleString, Integer> locals;

        LocalScope(LocalScope parent) {
            this.parent = parent;
            locals = new HashMap<>(parent.locals);
        }

        LocalScope() {
            this.parent = null;
            locals = new HashMap<>();
        }
    }

    private int totalLocals = 0;

    private LocalScope curScope = null;

    protected final List<TruffleString> enterFunction(FunctionContext ctx) {
        List<TruffleString> result = new ArrayList<>();
        assert curScope == null;

        curScope = new LocalScope();
        totalLocals = 0;

        // skip over function name which is also an IDENTIFIER
        for (int i = 1; i < ctx.IDENTIFIER().size(); i++) {
            TruffleString paramName = asTruffleString(ctx.IDENTIFIER(i).getSymbol(), false);
            curScope.locals.put(paramName, totalLocals++);
            result.add(paramName);
        }

        return result;
    }

    protected final void exitFunction() {
        curScope = curScope.parent;
        assert curScope == null;
    }

    protected final List<TruffleString> enterBlock(BlockContext ctx) {
        List<TruffleString> result = new ArrayList<>();
        curScope = new LocalScope(curScope);

        FindLocalsVisitor findLocals = new FindLocalsVisitor();
        findLocals.visitBlock(ctx);

        for (Token tok : findLocals.results) {
            TruffleString name = asTruffleString(tok, false);
            if (curScope.locals.get(name) == null) {
                curScope.locals.put(name, totalLocals++);
                result.add(name);
            }
        }

        return result;
    }

    protected final void exitBlock() {
        curScope = curScope.parent;
    }

    protected final int getNameIndex(TruffleString name) {
        Integer i = curScope.locals.get(name);
        if (i == null) {
            return -1;
        } else {
            return i;
        }
    }

    protected final int getNameIndex(Token name) {
        return getNameIndex(asTruffleString(name, false));
    }

}
