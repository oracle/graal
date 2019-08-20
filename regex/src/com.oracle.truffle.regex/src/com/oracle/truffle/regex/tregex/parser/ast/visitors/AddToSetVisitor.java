/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.tregex.nfa.ASTNodeSet;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;

public final class AddToSetVisitor extends DepthFirstTraversalRegexASTVisitor {

    private final ASTNodeSet<RegexASTNode> set;

    private AddToSetVisitor(ASTNodeSet<RegexASTNode> set) {
        this.set = set;
    }

    public static void addCharacterClasses(ASTNodeSet<RegexASTNode> set, RegexASTNode runRoot) {
        new AddToSetVisitor(set).run(runRoot);
    }

    @Override
    protected void visit(BackReference backReference) {
    }

    @Override
    protected void visit(Group group) {
    }

    @Override
    protected void visit(Sequence sequence) {
    }

    @Override
    protected void visit(PositionAssertion assertion) {
    }

    @Override
    protected void visit(LookBehindAssertion assertion) {
    }

    @Override
    protected void visit(LookAheadAssertion assertion) {
    }

    @Override
    protected void visit(CharacterClass characterClass) {
        set.add(characterClass);
    }

    @Override
    protected void visit(MatchFound matchFound) {
    }

}
