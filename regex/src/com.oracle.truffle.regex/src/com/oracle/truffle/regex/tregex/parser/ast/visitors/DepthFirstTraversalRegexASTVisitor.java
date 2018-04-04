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

import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
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

    public void run(RegexASTNode runRoot) {
        run(runRoot, false);
    }

    public void runReverse(RegexASTNode runRoot) {
        run(runRoot, true);
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
        } else if (cur instanceof MatchFound) {
            return advance((MatchFound) cur);
        } else {
            return advanceLeafNode(cur);
        }
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

    private boolean advance(MatchFound matchFound) {
        if (cur.getParent() != null) {
            cur = matchFound.getParent();
            return true;
        }
        // all MatchFound nodes must have a parent
        throw new IllegalStateException();
    }

    private boolean advanceLeafNode(RegexASTNode node) {
        cur = node.getParent();
        return true;
    }
}
