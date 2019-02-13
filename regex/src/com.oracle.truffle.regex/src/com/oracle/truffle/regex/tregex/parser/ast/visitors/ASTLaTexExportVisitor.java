/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.ast.visitors;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.util.LaTexExport;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class ASTLaTexExportVisitor extends DepthFirstTraversalRegexASTVisitor {

    public enum DrawPointers {
        LOOKBEHIND_ENTRIES
    }

    private final RegexAST ast;
    private final BufferedWriter writer;
    private int indent = 0;
    private final List<CharacterClass> lbEntries = new ArrayList<>();

    private ASTLaTexExportVisitor(RegexAST ast, BufferedWriter writer) {
        this.ast = ast;
        this.writer = writer;
    }

    @CompilerDirectives.TruffleBoundary
    public static void exportLatex(RegexAST ast, String path, DrawPointers pointers) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(path))) {
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
            switch (pointers) {
                case LOOKBEHIND_ENTRIES:
                    visitor.drawLookBehindEntries();
                    break;
            }
            visitor.writeln("\\end{forest}");
            visitor.writeln("\\end{document}");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void drawLookBehindEntries() {
        for (CharacterClass cc : lbEntries) {
            for (Group lbe : cc.getLookBehindEntries()) {
                writeln(String.format("\\draw[->,dotted] (n%d) to[in=north] (n%d);", cc.getId(), lbe.getId()));
            }
        }
    }

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

    @Override
    protected void visit(MatchFound matchFound) {
        writeln(node(matchFound));
    }
}
