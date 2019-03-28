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

import com.oracle.truffle.regex.tregex.nfa.ASTNodeSet;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTRootNode;

import java.util.ArrayList;

/**
 * For all lookbehind assertions, mark all states where the assertion may begin. If an assertion may
 * begin before the root of the AST, the AST is wrapped into a non-capturing group and prepended
 * with a sequence of optional and non-optional any-char matchers, as shown in the following
 * example: /(?<=ab)/ is transformed to /(?:[_any_][_any_](?:|[_any_](?:|[_any_])))(?<=ab)/. The
 * sequence of non-optional any-char matchers is called "prefix", and is necessary for cases where
 * we want to start searching for a regex match on a random position in a string. When starting on
 * any other index than 0, we decrease the index by the length of the prefix (but stop at position
 * 0) and thereby guarantee that lookbehind matches are found, but no regex match prior to the
 * actual starting index is found. When starting at index 0, the prefix is ignored. When starting at
 * index 1, just the last element of the prefix is used, and so on.
 * <p>
 * This entire mechanism assumes that all lookbehind assertion have a fixed length!
 *
 * @see RegexAST#createPrefix()
 */
public class MarkLookBehindEntriesVisitor extends NFATraversalRegexASTVisitor {

    private ASTNodeSet<CharacterClass> curEntriesFound;
    private ASTNodeSet<CharacterClass> newEntriesFound;
    private ASTNodeSet<LookAheadAssertion> curLookAheadBoundariesHit;
    private ASTNodeSet<LookAheadAssertion> newLookAheadBoundariesHit;

    public MarkLookBehindEntriesVisitor(RegexAST ast) {
        super(ast);
        curEntriesFound = new ASTNodeSet<>(ast);
        newEntriesFound = new ASTNodeSet<>(ast);
        curLookAheadBoundariesHit = new ASTNodeSet<>(ast);
        newLookAheadBoundariesHit = new ASTNodeSet<>(ast);
        setCanTraverseCaret(false);
        setReverse(true);
    }

    public void run() {
        for (LookBehindAssertion lb : ast.getLookBehinds()) {
            if (lb.getLength() == 0) {
                continue;
            }
            run(lb);
            movePastLookAheadBoundaries();
            int curDepth = 1;
            while (!newEntriesFound.isEmpty() && curDepth < lb.getLength()) {
                // Here we go to all previous successors until we reach the required depth. This
                // might cause us to revisit nodes we have already visited but it will not cause
                // infinite loops. We will re-enter looping groups and try all of their
                // alternatives. This way, we simulate all the possible ways the look-behind
                // expression could match up with the regular expression it is embedded in.
                // Example:
                // Take the regexp /m.*(?<=meme)/, represented in our AST as (?:m(?:.|)*(?<=meme)).
                // By repeatedly entering (?:.|)* and forking into both the alternatives, we end up
                // visiting the 'm' CharacterClass node at the beginning of the regexp with depth
                // equal to 1, 2, 3 and 4. Therefore, the 'm' node will be marked as a possible
                // beginning of the look-behind.
                curDepth++;
                ASTNodeSet<CharacterClass> tmp = curEntriesFound;
                curEntriesFound = newEntriesFound;
                newEntriesFound = tmp;
                newEntriesFound.clear();
                for (CharacterClass cc : curEntriesFound) {
                    run(cc);
                }
                // If our current lookbehind is inside a lookahead assertion, we have to break out
                // of the lookahead subtree.
                movePastLookAheadBoundaries();
            }
            for (CharacterClass t : newEntriesFound) {
                t.addLookBehindEntry(ast, lb.getGroup());
            }
            curEntriesFound.clear();
            newEntriesFound.clear();
        }
    }

    private void movePastLookAheadBoundaries() {
        while (!newLookAheadBoundariesHit.isEmpty()) {
            ASTNodeSet<LookAheadAssertion> tmp = curLookAheadBoundariesHit;
            curLookAheadBoundariesHit = newLookAheadBoundariesHit;
            newLookAheadBoundariesHit = tmp;
            newLookAheadBoundariesHit.clear();
            for (LookAheadAssertion la : curLookAheadBoundariesHit) {
                run(la);
            }
        }
    }

    @Override
    protected void visit(ArrayList<PathElement> path) {
        final RegexASTNode lastNode = path.get(path.size() - 1).getNode();
        if (lastNode instanceof CharacterClass) {
            CharacterClass cc = (CharacterClass) lastNode;
            newEntriesFound.add(cc);
        } else {
            assert lastNode instanceof MatchFound;
            MatchFound mf = (MatchFound) lastNode;
            if (!(mf.getSubTreeParent() instanceof RegexASTRootNode)) {
                assert mf.getSubTreeParent() instanceof LookAheadAssertion : "this visitor does not support nested look-behind assertions!";
                newLookAheadBoundariesHit.add((LookAheadAssertion) mf.getSubTreeParent());
            }
        }
    }

    @Override
    protected void enterLookAhead(LookAheadAssertion assertion) {
    }

    @Override
    protected void leaveLookAhead(LookAheadAssertion assertion) {
    }
}
