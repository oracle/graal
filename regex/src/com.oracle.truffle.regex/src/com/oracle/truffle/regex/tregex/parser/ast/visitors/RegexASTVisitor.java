/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;

public abstract class RegexASTVisitor {

    protected abstract void visit(BackReference backReference);

    protected abstract void visit(Group group);

    protected abstract void leave(Group group);

    protected abstract void visit(Sequence sequence);

    protected abstract void leave(Sequence sequence);

    protected abstract void visit(PositionAssertion assertion);

    protected abstract void visit(LookBehindAssertion assertion);

    protected abstract void leave(LookBehindAssertion assertion);

    protected abstract void visit(LookAheadAssertion assertion);

    protected abstract void leave(LookAheadAssertion assertion);

    protected abstract void visit(CharacterClass characterClass);

    protected abstract void visit(MatchFound matchFound);

    protected void doVisit(RegexASTNode cur) {
        if (cur == null) {
            throw new IllegalStateException();
        }
        if (cur instanceof Group) {
            visit((Group) cur);
        } else if (cur instanceof Sequence) {
            visit((Sequence) cur);
        } else if (cur instanceof PositionAssertion) {
            visit((PositionAssertion) cur);
        } else if (cur instanceof LookBehindAssertion) {
            visit((LookBehindAssertion) cur);
        } else if (cur instanceof LookAheadAssertion) {
            visit((LookAheadAssertion) cur);
        } else if (cur instanceof CharacterClass) {
            visit((CharacterClass) cur);
        } else if (cur instanceof BackReference) {
            visit((BackReference) cur);
        } else if (cur instanceof MatchFound) {
            visit((MatchFound) cur);
        } else {
            throw new IllegalStateException();
        }
    }

    protected void doLeave(RegexASTNode cur) {
        if (cur instanceof Group) {
            leave((Group) cur);
        } else if (cur instanceof Sequence) {
            leave((Sequence) cur);
        } else if (cur instanceof LookBehindAssertion) {
            leave((LookBehindAssertion) cur);
        } else if (cur instanceof LookAheadAssertion) {
            leave((LookAheadAssertion) cur);
        } else {
            throw new IllegalStateException();
        }
    }
}
