/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.parser.ast.Term;

/**
 * An AST visitor that produces a deep copy of a given {@link Term} and its subtree, and registers
 * all new nodes in the {@link RegexAST} provided at instantiation. This visitor should be preferred
 * over recursively copying with {@link RegexASTNode#copy(RegexAST)} whenever possible, since it is
 * non-recursive. Note that this visitor is not thread-safe!
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
        doCopy(backReference);
    }

    @Override
    protected void visit(Group group) {
        curParent = doCopy(group);
    }

    @Override
    protected void leave(Group group) {
        goToUpperParent();
    }

    @Override
    protected void visit(Sequence sequence) {
        Sequence copy = sequence.copy(ast);
        ((Group) curParent).add(copy);
        curParent = copy;
    }

    @Override
    protected void leave(Sequence sequence) {
        goToUpperParent();
    }

    @Override
    protected void visit(PositionAssertion assertion) {
        doCopy(assertion);
    }

    @Override
    protected void visit(LookBehindAssertion assertion) {
        curParent = doCopy(assertion);
    }

    @Override
    protected void leave(LookBehindAssertion assertion) {
        goToUpperParent();
    }

    @Override
    protected void visit(LookAheadAssertion assertion) {
        curParent = doCopy(assertion);
    }

    @Override
    protected void leave(LookAheadAssertion assertion) {
        goToUpperParent();
    }

    @Override
    protected void visit(CharacterClass characterClass) {
        doCopy(characterClass);
    }

    private void goToUpperParent() {
        assert curParent != null;
        curParent = curParent.getParent();
    }

    private Term doCopy(Term t) {
        Term copy = t.copy(ast);
        ast.addSourceSections(copy, ast.getSourceSections(t));
        if (curParent == null) {
            assert copyRoot == null;
            copyRoot = copy;
        } else if (curParent instanceof RegexASTSubtreeRootNode) {
            assert copy instanceof Group;
            ((RegexASTSubtreeRootNode) curParent).setGroup((Group) copy);
        } else {
            ((Sequence) curParent).add(copy);
        }
        return copy;
    }
}
