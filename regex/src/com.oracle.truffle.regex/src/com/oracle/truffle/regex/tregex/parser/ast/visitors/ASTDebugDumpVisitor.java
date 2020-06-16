/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;

public final class ASTDebugDumpVisitor extends DepthFirstTraversalRegexASTVisitor {

    private ASTDebugDumpVisitor() {
    }

    @CompilerDirectives.TruffleBoundary
    public static String getDump(Group root) {
        ASTDebugDumpVisitor visitor = new ASTDebugDumpVisitor();
        visitor.run(root);
        if (visitor.dead) {
            visitor.dump.append("\u001b[0m");
        }
        return visitor.dump.toString();
    }

    private final StringBuilder dump = new StringBuilder();
    private boolean dead;

    private void append(RegexASTNode node) {
        checkDead(node);
        dump.append(node.toString());
    }

    private void checkDead(RegexASTNode node) {
        if (!dead && node.isDead()) {
            dump.append("\u001b[41m");
            dead = true;
        }
        if (dead && !node.isDead()) {
            dump.append("\u001b[0m");
            dead = false;
        }
    }

    @Override
    protected void visit(BackReference backReference) {
        append(backReference);
    }

    @Override
    protected void visit(Group group) {
        checkDead(group);
        dump.append("(");
        if (group.getParent() instanceof RegexASTSubtreeRootNode) {
            dump.append(((RegexASTSubtreeRootNode) group.getParent()).getPrefix());
        } else if (!group.isCapturing()) {
            dump.append("?:");
        }
    }

    @Override
    protected void leave(Group group) {
        dump.append(")").append(group.loopToString());
    }

    @Override
    protected void visit(Sequence sequence) {
        if (sequence != sequence.getParent().getFirstAlternative()) {
            dump.append("|");
        }
    }

    @Override
    protected void visit(PositionAssertion assertion) {
        append(assertion);
    }

    @Override
    protected void visit(LookBehindAssertion assertion) {
    }

    @Override
    protected void visit(LookAheadAssertion assertion) {
    }

    @Override
    protected void visit(CharacterClass characterClass) {
        append(characterClass);
    }
}
