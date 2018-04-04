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
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class ASTDotExportVisitor extends DepthFirstTraversalRegexASTVisitor {

    private final BufferedWriter writer;
    private final boolean showParentPointers;

    private ASTDotExportVisitor(BufferedWriter writer, boolean showParentPointers) {
        this.writer = writer;
        this.showParentPointers = showParentPointers;
    }

    @CompilerDirectives.TruffleBoundary
    public static void exportDot(RegexASTNode root, String path, boolean showParentPointers) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(path))) {
            ASTDotExportVisitor visitor = new ASTDotExportVisitor(writer, showParentPointers);
            writer.write("digraph ast {");
            writer.newLine();
            visitor.run(root);
            writer.write("}");
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String deadStyle(RegexASTNode node) {
        return node.isDead() ? ", style=filled, color=grey" : "";
    }

    private void writeln(String s) {
        try {
            writer.write(s);
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String nodeName(RegexASTNode node) {
        return String.format("node%d", node.getId());
    }

    private void printParentNextPrev(RegexASTNode node) {
        if (showParentPointers && node.getParent() != null) {
            writeln(String.format("%s -> %s [label=parent];", nodeName(node), nodeName(node.getParent())));
        }
    }

    @Override
    protected void visit(BackReference backReference) {
        writeln(String.format("%s [label=\"%s\", shape=box%s];", nodeName(backReference), backReference.toString().replace("\\", "\\\\"), deadStyle(backReference)));
        printParentNextPrev(backReference);
    }

    @Override
    protected void visit(Group group) {
        writeln(String.format("%s [label=group, shape=%s%s];", nodeName(group), group.isCapturing() ? "doublecircle" : "circle", deadStyle(group)));
        printParentNextPrev(group);
        int i = 0;
        for (Sequence s : group.getAlternatives()) {
            writeln(String.format("%s -> %s [label=alt%d];", nodeName(group), nodeName(s), i++));
        }
    }

    @Override
    protected void visit(Sequence sequence) {
        writeln(String.format("%s [label=seq, shape=house%s];", nodeName(sequence), deadStyle(sequence)));
        printParentNextPrev(sequence);
        int i = 0;
        for (Term t : sequence.getTerms()) {
            writeln(String.format("%s -> %s [label=seq%d];", nodeName(sequence), nodeName(t), i++));
        }
    }

    @Override
    protected void visit(PositionAssertion assertion) {
        writeln(String.format("%s [label=\"%s\", shape=box%s];", nodeName(assertion), assertion, deadStyle(assertion)));
        printParentNextPrev(assertion);
    }

    @Override
    protected void visit(LookBehindAssertion assertion) {
        visitLookAround(assertion);
    }

    @Override
    protected void visit(LookAheadAssertion assertion) {
        visitLookAround(assertion);
    }

    private void visitLookAround(RegexASTSubtreeRootNode assertion) {
        writeln(String.format("%s [label=la, shape=box%s];", nodeName(assertion), deadStyle(assertion)));
        printParentNextPrev(assertion);
        writeln(String.format("%s -> %s [label=ass];", nodeName(assertion), nodeName(assertion.getGroup())));
    }

    @Override
    protected void visit(CharacterClass characterClass) {
        writeln(String.format("%s [label=\"%s\", shape=box%s];", nodeName(characterClass), characterClass.toString().replace("\\", "\\\\"), deadStyle(characterClass)));
        printParentNextPrev(characterClass);
    }

    @Override
    protected void visit(MatchFound matchFound) {
        writeln(String.format("%s [label=\"%s\", shape=box%s];", nodeName(matchFound), matchFound, deadStyle(matchFound)));
        printParentNextPrev(matchFound);
    }
}
