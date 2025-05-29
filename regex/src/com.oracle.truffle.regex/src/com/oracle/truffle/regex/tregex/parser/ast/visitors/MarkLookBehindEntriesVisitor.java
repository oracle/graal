/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;

/**
 * For all lookbehind assertions, mark all states where the assertion may begin. If an assertion may
 * begin before the root of the AST, the AST is wrapped into a non-capturing group and prepended
 * with a sequence of optional and non-optional any-char matchers, as shown in the following
 * example: /(?&lt;=ab)/ is transformed to /(?:[_any_][_any_](?:|[_any_](?:|[_any_])))(?&lt;=ab)/.
 * The sequence of non-optional any-char matchers is called "prefix", and is necessary for cases
 * where we want to start searching for a regex match on a random position in a string. When
 * starting on any other index than 0, we decrease the index by the length of the prefix (but stop
 * at position 0) and thereby guarantee that lookbehind matches are found, but no regex match prior
 * to the actual starting index is found. When starting at index 0, the prefix is ignored. When
 * starting at index 1, just the last element of the prefix is used, and so on.
 * <p>
 * This entire mechanism assumes that all lookbehind assertion have a fixed length!
 *
 * @see RegexAST#createPrefix()
 */
public class MarkLookBehindEntriesVisitor extends NFATraversalRegexASTVisitor {

    private StateSet<RegexAST, CharacterClass> curEntriesFound;
    private StateSet<RegexAST, CharacterClass> newEntriesFound;
    private StateSet<RegexAST, LookAheadAssertion> curLookAheadBoundariesHit;
    private StateSet<RegexAST, LookAheadAssertion> newLookAheadBoundariesHit;

    public MarkLookBehindEntriesVisitor(RegexAST ast) {
        super(ast);
        curEntriesFound = StateSet.create(ast);
        newEntriesFound = StateSet.create(ast);
        curLookAheadBoundariesHit = StateSet.create(ast);
        newLookAheadBoundariesHit = StateSet.create(ast);
        setCanTraverseCaret(false);
        setIgnoreMatchBoundaryAssertions(true);
        setReverse(true);
    }

    public void run() {
        for (RegexASTSubtreeRootNode subtreeRootNode : ast.getSubtrees()) {
            if (!subtreeRootNode.isLookBehindAssertion() || subtreeRootNode.isDead()) {
                continue;
            }
            LookBehindAssertion lb = subtreeRootNode.asLookBehindAssertion();
            run(lb);
            movePastLookAheadBoundaries();
            int curDepth = 1;
            while (!newEntriesFound.isEmpty() && curDepth < lb.getLiteralLength()) {
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
                StateSet<RegexAST, CharacterClass> tmp = curEntriesFound;
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
                t.addLookBehindEntry(ast, lb);
            }
            curEntriesFound.clear();
            newEntriesFound.clear();
        }
    }

    private void movePastLookAheadBoundaries() {
        while (!newLookAheadBoundariesHit.isEmpty()) {
            StateSet<RegexAST, LookAheadAssertion> tmp = curLookAheadBoundariesHit;
            curLookAheadBoundariesHit = newLookAheadBoundariesHit;
            newLookAheadBoundariesHit = tmp;
            newLookAheadBoundariesHit.clear();
            for (LookAheadAssertion la : curLookAheadBoundariesHit) {
                run(la);
            }
        }
    }

    @Override
    protected void visit(RegexASTNode target) {
        if (target instanceof CharacterClass) {
            CharacterClass cc = (CharacterClass) target;
            newEntriesFound.add(cc);
        } else {
            assert target instanceof MatchFound;
            MatchFound mf = (MatchFound) target;
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

    @Override
    protected boolean isBuildingDFA() {
        return true;
    }

    @Override
    protected boolean canPruneAfterUnconditionalFinalState() {
        return false;
    }
}
