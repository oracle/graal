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

public final class InitIDVisitor extends DepthFirstTraversalRegexASTVisitor {

    /**
     * ID of the parent node of AST nodes that are not part of a lookaround assertion.
     */
    public static final int REGEX_AST_ROOT_PARENT_ID = 0;

    private final RegexASTNode[] index;
    private int nextID;

    private InitIDVisitor(RegexASTNode[] index, int nextID) {
        this.index = index;
        this.nextID = nextID;
    }

    public static void init(RegexAST ast) {
        // additional reserved slots:
        // - 1 slot for REGEX_AST_ROOT_PARENT_ID
        // - prefix length + 1 anchored initial NFA states
        // - prefix length + 1 unanchored initial NFA states
        // - 1 slot at the end for NFA loopBack matcher
        int initialID = 3 + (ast.getWrappedPrefixLength() * 2);
        InitIDVisitor visitor = new InitIDVisitor(new RegexASTNode[initialID + ast.getNumberOfNodes() + 1], initialID);
        assert ast.getWrappedRoot().getSubTreeParent().getId() == REGEX_AST_ROOT_PARENT_ID;
        visitor.index[REGEX_AST_ROOT_PARENT_ID] = ast.getWrappedRoot().getSubTreeParent();
        visitor.run(ast.getWrappedRoot());
        ast.setIndex(visitor.index);
    }

    private void initID(RegexASTNode node) {
        node.setId(nextID++);
        index[node.getId()] = node;
    }

    @Override
    protected void visit(BackReference backReference) {
        initID(backReference);
    }

    @Override
    protected void visit(Group group) {
        initID(group);
    }

    @Override
    protected void leave(Group group) {
        if (group.getParent() instanceof RegexASTSubtreeRootNode) {
            final MatchFound matchFound = group.getSubTreeParent().getMatchFound();
            if (!matchFound.idInitialized()) {
                initID(matchFound);
            }
        }
    }

    @Override
    protected void visit(Sequence sequence) {
        initID(sequence);
    }

    @Override
    protected void visit(PositionAssertion assertion) {
        initID(assertion);
    }

    @Override
    protected void visit(LookBehindAssertion assertion) {
        initID(assertion);
    }

    @Override
    protected void visit(LookAheadAssertion assertion) {
        initID(assertion);
    }

    @Override
    protected void visit(CharacterClass characterClass) {
        initID(characterClass);
    }

    @Override
    protected void visit(MatchFound matchFound) {
        initID(matchFound);
    }
}
