/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;

/**
 * An AST visitor that produces a deep copy of a given {@link Term} and its subtree, and registers
 * all new nodes in the {@link RegexAST} provided at instantiation. This visitor should be preferred
 * over recursively copying with {@link RegexASTNode#copy(RegexAST, boolean)} whenever possible,
 * since it is non-recursive. Note that this visitor is not thread-safe!
 *
 * @see DepthFirstTraversalRegexASTVisitor
 */
public class CopyVisitor extends DepthFirstTraversalRegexASTVisitor {

    private final RegexAST ast;
    private Term copyRoot;
    private RegexASTNode curParent;

    public CopyVisitor(RegexAST ast) {
        this.ast = ast;
    }

    public Term copy(Term term) {
        run(term);
        assert copyRoot != null;
        Term result = copyRoot;
        copyRoot = null;
        return result;
    }

    @Override
    protected void visit(BackReference backReference) {
        addToParent(backReference.copy(ast, false));
    }

    @Override
    protected void visit(Group group) {
        Group copy = group.copy(ast, false);
        addToParent(copy);
        curParent = copy;
    }

    @Override
    protected void leave(Group group) {
        goToUpperParent();
    }

    @Override
    protected void visit(Sequence sequence) {
        Sequence copy = sequence.copy(ast, false);
        ((Group) curParent).add(copy);
        curParent = copy;
    }

    @Override
    protected void leave(Sequence sequence) {
        goToUpperParent();
    }

    @Override
    protected void visit(PositionAssertion assertion) {
        addToParent(assertion.copy(ast, false));
    }

    @Override
    protected void visit(LookBehindAssertion assertion) {
        LookBehindAssertion copy = assertion.copy(ast, false);
        addToParent(copy);
        curParent = copy;
    }

    @Override
    protected void leave(LookBehindAssertion assertion) {
        goToUpperParent();
    }

    @Override
    protected void visit(LookAheadAssertion assertion) {
        LookAheadAssertion copy = assertion.copy(ast, false);
        addToParent(copy);
        curParent = copy;
    }

    @Override
    protected void leave(LookAheadAssertion assertion) {
        goToUpperParent();
    }

    @Override
    protected void visit(CharacterClass characterClass) {
        addToParent(characterClass.copy(ast, false));
    }

    @Override
    protected void visit(MatchFound matchFound) {
        throw new IllegalStateException();
    }

    private void goToUpperParent() {
        assert curParent != null;
        curParent = curParent.getParent();
    }

    private void addToParent(Term copy) {
        if (curParent == null) {
            assert copyRoot == null;
            copyRoot = copy;
        } else if (curParent instanceof RegexASTSubtreeRootNode) {
            assert copy instanceof Group;
            ((RegexASTSubtreeRootNode) curParent).setGroup((Group) copy);
        } else {
            ((Sequence) curParent).add(copy);
        }
    }
}
