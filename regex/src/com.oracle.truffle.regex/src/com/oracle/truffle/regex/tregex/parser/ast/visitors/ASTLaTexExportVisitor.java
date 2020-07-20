/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.ast.visitors;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.util.LaTexExport;

public final class ASTLaTexExportVisitor extends DepthFirstTraversalRegexASTVisitor {

    private final RegexAST ast;
    private final BufferedWriter writer;
    private int indent = 0;
    private final List<CharacterClass> lbEntries = new ArrayList<>();

    private ASTLaTexExportVisitor(RegexAST ast, BufferedWriter writer) {
        this.ast = ast;
        this.writer = writer;
    }

    @TruffleBoundary
    public static void exportLatex(RegexAST ast, TruffleFile path) {
        try (BufferedWriter writer = path.newBufferedWriter(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ASTLaTexExportVisitor visitor = new ASTLaTexExportVisitor(ast, writer);
            visitor.writeln("\\documentclass{standalone}");
            visitor.writeln("\\usepackage[utf8]{inputenc}");
            visitor.writeln("\\usepackage[T1]{fontenc}");
            visitor.writeln("\\usepackage[edges]{forest}");
            visitor.writeln("\\begin{document}");
            visitor.writeln("\\begin{forest}");
            visitor.writeln("for tree={draw,");
            visitor.writeln("before typesetting nodes={content=\\texttt{#1}}");
            visitor.writeln("},");
            visitor.writeln("forked edges,");
            visitor.run(ast.getWrappedRoot());
            visitor.drawLookBehindEntries();
            visitor.writeln("\\end{forest}");
            visitor.writeln("\\end{document}");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void drawLookBehindEntries() {
        for (CharacterClass cc : lbEntries) {
            for (LookBehindAssertion lbe : cc.getLookBehindEntries()) {
                writeln(String.format("\\draw[->,dotted] (n%d) to[in=north] (n%d);", cc.getId(), lbe.getGroup().getId()));
            }
        }
    }

    @TruffleBoundary
    private void writeln(String s) {
        try {
            for (int i = 0; i < indent; i++) {
                writer.write(" ");
            }
            writer.write(s);
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String style(RegexASTNode node) {
        if (node.isDead()) {
            return ",fill=gray";
        }
        if (node instanceof PositionAssertion && (ast.getReachableCarets().contains(node) || ast.getReachableDollars().contains(node))) {
            return ",fill=cyan";
        }
        if (node == ast.getWrappedRoot()) {
            return ",fill=green";
        }
        return "";
    }

    private String node(RegexASTNode node) {
        return String.format("[%s%s]", label(node.toString(), node), style(node));
    }

    private static String label(String str, RegexASTNode node) {
        return String.format("{%s\\textsubscript{%d}},name=n%d", LaTexExport.escape(str), node.getId(), node.getId());
    }

    private void openNode(String str, RegexASTNode node) {
        writeln("[" + label(str, node) + style(node));
        indent += 2;
    }

    private void closeNode() {
        indent -= 2;
        writeln("]");
    }

    @Override
    protected void visit(BackReference backReference) {
        writeln(node(backReference));
    }

    @Override
    protected void visit(Group group) {
        openNode((group.isCapturing() ? String.format("(%d)", group.getGroupNumber()) : "(:?)") + group.loopToString(), group);
    }

    @Override
    protected void leave(Group group) {
        closeNode();
    }

    @Override
    protected void visit(Sequence sequence) {
        openNode("|", sequence);
    }

    @Override
    protected void leave(Sequence sequence) {
        closeNode();
    }

    @Override
    protected void visit(PositionAssertion assertion) {
        writeln(node(assertion));
    }

    @Override
    protected void visit(LookBehindAssertion assertion) {
        openNode(String.format("(%s)", assertion.getPrefix()), assertion);
    }

    @Override
    protected void leave(LookBehindAssertion assertion) {
        closeNode();
    }

    @Override
    protected void visit(LookAheadAssertion assertion) {
        openNode(String.format("(%s)", assertion.getPrefix()), assertion);
    }

    @Override
    protected void leave(LookAheadAssertion assertion) {
        closeNode();
    }

    @Override
    protected void visit(CharacterClass characterClass) {
        writeln(node(characterClass));
        if (characterClass.hasLookBehindEntries()) {
            lbEntries.add(characterClass);
        }
    }
}
