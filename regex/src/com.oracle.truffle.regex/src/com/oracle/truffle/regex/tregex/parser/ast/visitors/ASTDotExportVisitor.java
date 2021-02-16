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
import java.nio.file.Files;
import java.nio.file.Paths;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;

public final class ASTDotExportVisitor extends DepthFirstTraversalRegexASTVisitor {

    private final BufferedWriter writer;
    private final boolean showParentPointers;

    private ASTDotExportVisitor(BufferedWriter writer, boolean showParentPointers) {
        this.writer = writer;
        this.showParentPointers = showParentPointers;
    }

    @TruffleBoundary
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

    @TruffleBoundary
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
}
