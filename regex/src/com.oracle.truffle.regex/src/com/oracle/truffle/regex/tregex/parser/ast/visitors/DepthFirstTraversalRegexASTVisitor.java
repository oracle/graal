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

import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;

/**
 * AST visitor base class that will visit a given subtree in depth-first order. Whenever all
 * children of a {@link RegexASTVisitorIterable} node ({@link Group}, {@link Sequence},
 * {@link com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode}) have been visited,
 * the corresponding {@code leave}-method will be called. Running the visitor is not thread-safe,
 * since it will store its current state directly in the AST, using the methods of
 * {@link RegexASTVisitorIterable} implemented by {@link Group}, {@link Sequence} and
 * {@link com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode}!
 *
 * <pre>
 * {@code
 * execution example on tree (a(bc(d|e)f)):
 * visit          Group (a(bc(d|e)f))
 * visit       Sequence a(bc(d|e)f)
 * visit CharacterClass a
 * visit          Group (bc(d|e)f)
 * visit       Sequence bc(d|e)f
 * visit CharacterClass b
 * visit CharacterClass c
 * visit          Group (d|e)
 * visit       Sequence d
 * visit CharacterClass d
 * leave       Sequence d
 * visit       Sequence e
 * visit CharacterClass e
 * leave       Sequence e
 * leave          Group (d|e)
 * visit CharacterClass f
 * leave       Sequence bc(d|e)f
 * leave          Group (bc(d|e)f)
 * leave       Sequence a(bc(d|e)f)
 * leave          Group (a(bc(d|e)f))
 * }
 * </pre>
 */
public abstract class DepthFirstTraversalRegexASTVisitor extends RegexASTVisitor {

    private RegexASTNode root;
    private RegexASTNode cur;
    private boolean done = false;
    private boolean reverse = false;

    protected void run(RegexASTNode runRoot) {
        run(runRoot, false);
    }

    protected void runReverse(RegexASTNode runRoot) {
        run(runRoot, true);
    }

    protected boolean isForward() {
        return !reverse;
    }

    protected boolean isReverse() {
        return reverse;
    }

    private void run(RegexASTNode runRoot, boolean runReverse) {
        reverse = runReverse;
        root = runRoot;
        cur = root;
        done = false;
        init(runRoot);
        while (!done) {
            doVisit(cur);
            while (doAdvance()) {
                // advance until we reach the next node to visit
            }
        }
    }

    @SuppressWarnings("unused")
    protected void init(RegexASTNode runRoot) {
    }

    private boolean doAdvance() {
        if (cur == null || cur == root.getParent()) {
            done = true;
            return false;
        }
        if (cur instanceof RegexASTVisitorIterable) {
            return advance((RegexASTVisitorIterable) cur);
        } else {
            return advanceLeafNode(cur);
        }
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
    }

    @Override
    protected void leave(Group group) {
    }

    @Override
    protected void leave(Sequence sequence) {
    }

    @Override
    protected void leave(LookBehindAssertion assertion) {
    }

    @Override
    protected void leave(LookAheadAssertion assertion) {
    }

    private boolean advance(RegexASTVisitorIterable iterable) {
        if (iterable.visitorHasNext()) {
            cur = iterable.visitorGetNext(reverse);
            return false;
        }
        iterable.resetVisitorIterator();
        doLeave(cur);
        cur = ((RegexASTNode) iterable).getParent();
        return true;
    }

    private boolean advanceLeafNode(RegexASTNode node) {
        cur = node.getParent();
        return true;
    }
}
