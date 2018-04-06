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
        if (sequence != sequence.getParent().getAlternatives().get(0)) {
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

    @Override
    protected void visit(MatchFound matchFound) {
        append(matchFound);
    }
}
