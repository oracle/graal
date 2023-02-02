/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench.micro;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.source.Source;
import org.graalvm.polybench.micro.expr.EvalExpression;
import org.graalvm.polybench.micro.expr.EvalExpressionNodeGen;
import org.graalvm.polybench.micro.expr.ExecuteExpression;
import org.graalvm.polybench.micro.expr.ExecuteExpressionNodeGen;
import org.graalvm.polybench.micro.expr.Expression;
import org.graalvm.polybench.micro.expr.InvokeMemberExpression;
import org.graalvm.polybench.micro.expr.InvokeMemberExpressionNodeGen;
import org.graalvm.polybench.micro.expr.PreparedValueExpression;
import org.graalvm.polybench.micro.expr.ReadMemberExpression;
import org.graalvm.polybench.micro.expr.ReadMemberExpressionNodeGen;
import org.graalvm.polybench.micro.nodes.SetupRootNode;
import org.graalvm.polybench.micro.nodes.SetupRootNodeGen;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.graalvm.polybench.micro.Declaration.ExpressionFactory;

public final class Parser {

    static CallTarget doParse(MicrobenchLanguage language, Source source) {
        try {
            return new Parser(source).parseEverything(language);
        } catch (IOException e) {
            throw new ParseError(e.getMessage());
        }
    }

    private static final Pattern NEWLINE = Pattern.compile("\n");
    private static final Pattern WHITESPACE = Pattern.compile("^\\s*(#.*)?");

    private final HashMap<String, Declaration> symbolTable = new HashMap<>();

    final String[] lines;
    int currentLine;
    StreamTokenizer tokenizer;

    private Parser(Source source) {
        lines = NEWLINE.split(source.getCharacters());
        currentLine = 0;
    }

    CallTarget parseEverything(MicrobenchLanguage language) throws IOException {
        // declarations for SETUP
        parseDeclarations();

        Expression[] preparedState = parseSetupStatement();

        // declarations for MICROBENCH
        parseDeclarations();

        Microbench spec = parseMicrobenchStatement();

        SetupRootNode setupNode = SetupRootNodeGen.create(language, spec, preparedState);
        return setupNode.getCallTarget();
    }

    void parseDeclarations() throws IOException {
        for (;;) {
            Declaration decl = parseDeclaration();
            if (decl == null) {
                return;
            }

            symbolTable.put(decl.name, decl);
        }
    }

    Expression[] parseSetupStatement() throws IOException {
        expect(StreamTokenizer.TT_WORD, "SETUP");
        if (!"SETUP".equals(tokenizer.sval)) {
            throw error("expected SETUP");
        }

        ArrayList<Declaration> setupDeclarations = new ArrayList<>();

        int token;
        while ((token = tokenizer.nextToken()) != StreamTokenizer.TT_EOF) {
            if (token != StreamTokenizer.TT_WORD) {
                throw error("expected identifier");
            }

            final int idx = setupDeclarations.size();
            Declaration decl = new Declaration(tokenizer.sval, () -> new PreparedValueExpression(idx));
            setupDeclarations.add(decl);
        }

        Expression[] setupExpressions = new Expression[setupDeclarations.size()];
        int i = 0;
        for (Declaration decl : setupDeclarations) {
            setupExpressions[i++] = use(decl.name);
        }

        // start over with clean scope, only carry over declarations from SETUP statement
        symbolTable.clear();
        for (Declaration decl : setupDeclarations) {
            symbolTable.put(decl.name, decl);
        }

        return setupExpressions;
    }

    Microbench parseMicrobenchStatement() throws IOException {
        expect(StreamTokenizer.TT_WORD, "MICROBENCH");
        if (!"MICROBENCH".equals(tokenizer.sval)) {
            throw error("expected MICROBENCH");
        }

        expect(StreamTokenizer.TT_WORD, "identifier");
        Expression microbench = use(tokenizer.sval);

        expect(StreamTokenizer.TT_EOF, "end of line");

        int batchSize = 0;
        int repeat = 1;
        Integer warmupIterations = null;
        Integer iterations = null;
        String unit = null;

        while (setupNextLine()) {
            expect(StreamTokenizer.TT_WORD, "option name");
            switch (tokenizer.sval) {
                case "repeat":
                    expect(':', ":");
                    repeat = parseInteger();
                    break;

                case "batchSize":
                    expect(':', ":");
                    batchSize = parseInteger();
                    break;

                case "iterations":
                    expect(':', ":");
                    iterations = parseInteger();
                    break;

                case "unit":
                    expect(':', ":");
                    expect(StreamTokenizer.TT_WORD, "time unit");
                    switch (tokenizer.sval) {
                        case "s":
                        case "ms":
                        case "us":
                        case "ns":
                            unit = tokenizer.sval;
                            break;
                        default:
                            throw error("time unit");
                    }
                    break;

                case "warmupIterations":
                    expect(':', ":");
                    warmupIterations = parseInteger();
                    break;

                default:
                    throw error("unknown option");
            }
            expect(StreamTokenizer.TT_EOF, "end of line");
        }

        if (batchSize == 0) {
            batchSize = repeat;
        }

        return new Microbench(repeat, batchSize, unit, microbench, warmupIterations, iterations);
    }

    int parseInteger() throws IOException {
        expect(StreamTokenizer.TT_NUMBER, "number");
        int ret = (int) tokenizer.nval;
        if (ret <= 0 || ret != tokenizer.nval) {
            throw error("positive integer");
        }
        return ret;
    }

    private Expression use(String name) {
        Declaration decl = symbolTable.get(name);
        if (decl == null) {
            throw new ParseError("line %d: couldn't find declaration '%s'", currentLine, name);
        }
        return decl.expression.create();
    }

    private String nextLine() {
        if (currentLine < lines.length) {
            return lines[currentLine++];
        } else {
            currentLine++; // still do this, because of backup()
            return null;
        }
    }

    private void backup() {
        currentLine--;
    }

    private boolean hasNext() {
        return currentLine < lines.length;
    }

    private ParseError error(String msg) {
        String actual;
        switch (tokenizer.ttype) {
            case StreamTokenizer.TT_WORD:
                actual = tokenizer.sval;
                break;
            case StreamTokenizer.TT_NUMBER:
                actual = Double.toString(tokenizer.nval);
                break;
            case StreamTokenizer.TT_EOF:
            case StreamTokenizer.TT_EOL:
                actual = "end of line";
                break;
            default:
                char t = (char) tokenizer.ttype;
                actual = Character.toString(t);
        }
        throw new ParseError("line %d: %s, got %s", currentLine, msg, actual);
    }

    private void expect(int token, String expected) throws IOException {
        if (tokenizer.nextToken() != token) {
            throw error(String.format("expected %s", expected));
        }
    }

    private boolean setupNextLine() {
        String line;
        Matcher m;
        do {
            if (!hasNext()) {
                return false;
            }

            line = nextLine();
            m = WHITESPACE.matcher(line);
        } while (m.find() && m.start() == 0 && m.end() == line.length());

        tokenizer = new StreamTokenizer(new StringReader(line));
        tokenizer.wordChars('_', '_');
        tokenizer.commentChar('#');

        return true;
    }

    Declaration parseDeclaration() throws IOException {
        if (!setupNextLine()) {
            return null;
        }

        expect(StreamTokenizer.TT_WORD, "identifier");
        String ident = tokenizer.sval;

        switch (ident) {
            // stop parsing declarations on keywords
            case "SETUP":
            case "MICROBENCH":
                tokenizer.pushBack();
                return null;
        }

        expect('=', "assignment");

        Expression expr = parseOperation();
        tokenizer = null;

        return new Declaration(ident, new ExpressionFactory() {

            private boolean used = false;

            @Override
            public Expression create() {
                if (used) {
                    throw new ParseError("line %d: can't use %s twice", currentLine, ident);
                }
                used = true;
                return expr;
            }
        });
    }

    Expression parseOperation() throws IOException {
        expect(StreamTokenizer.TT_WORD, "operation");
        switch (tokenizer.sval) {
            case "EXECUTE":
                return parseExecute();
            case "READ_MEMBER":
                return parseReadMember();
            case "INVOKE_MEMBER":
                return parseInvokeMember();
            case "EVAL":
                return parseEval();
            default:
                throw error("unknown operation");
        }
    }

    ExecuteExpression parseExecute() throws IOException {
        expect(StreamTokenizer.TT_WORD, "identifier");
        Expression receiver = use(tokenizer.sval);

        Expression[] args = parseArguments();

        return ExecuteExpressionNodeGen.create(args, receiver);
    }

    InvokeMemberExpression parseInvokeMember() throws IOException {
        expect(StreamTokenizer.TT_WORD, "identifier");
        Expression receiver = use(tokenizer.sval);

        expect('"', "string");
        String member = tokenizer.sval;

        Expression[] args = parseArguments();

        return InvokeMemberExpressionNodeGen.create(member, args, receiver);
    }

    Expression[] parseArguments() throws IOException {
        ArrayList<Expression> result = new ArrayList<>();
        int token;
        while ((token = tokenizer.nextToken()) != StreamTokenizer.TT_EOF) {
            if (token == StreamTokenizer.TT_WORD) {
                Expression param = use(tokenizer.sval);
                result.add(param);
            } else {
                throw error("expected argument identifier");
            }
        }
        return result.toArray(Expression.EMPTY);
    }

    ReadMemberExpression parseReadMember() throws IOException {
        expect(StreamTokenizer.TT_WORD, "identifier");
        Expression receiver = use(tokenizer.sval);

        expect('"', "string");
        String member = tokenizer.sval;

        expect(StreamTokenizer.TT_EOF, "end of line");

        return ReadMemberExpressionNodeGen.create(member, receiver);
    }

    EvalExpression parseEval() throws IOException {
        expect('"', "language ID");
        String langId = tokenizer.sval;
        expect(StreamTokenizer.TT_EOF, "end of line");

        String code = parseIndentedBlock();
        Source source = Source.newBuilder(langId, code, "bench-expr").build();
        return EvalExpressionNodeGen.create(source);
    }

    String parseIndentedBlock() {
        String line = nextLine();
        Matcher m = WHITESPACE.matcher(line);
        if (!m.find() || m.start() > 0 || m.end() == 0) {
            backup();
            return "";
        }
        String ws = m.group();
        int wsLen = ws.length();

        StringBuilder builder = new StringBuilder();

        while (line != null && line.startsWith(ws)) {
            builder.append(line.substring(wsLen)).append('\n');
            line = nextLine();
        }

        backup();
        return builder.toString();
    }
}
