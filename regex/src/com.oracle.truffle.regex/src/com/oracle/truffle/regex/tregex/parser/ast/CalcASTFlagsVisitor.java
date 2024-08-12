/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.ast;

import static com.oracle.truffle.regex.tregex.parser.ast.CalcASTPropsVisitor.OR_FLAGS;
import static com.oracle.truffle.regex.tregex.parser.ast.CalcASTPropsVisitor.setFlagsLookAroundAssertion;

import com.oracle.truffle.regex.tregex.parser.ast.visitors.DepthFirstTraversalRegexASTVisitor;

/**
 * Reduced version of {@link CalcASTPropsVisitor}. This version calculates
 * {@link CalcASTPropsVisitor#OR_FLAGS} and {@link RegexASTNode#mayMatchEmptyString()} only.
 */
public class CalcASTFlagsVisitor extends DepthFirstTraversalRegexASTVisitor {

    private static final int OR_FLAGS_GROUP = OR_FLAGS | RegexASTNode.FLAG_MAY_MATCH_EMPTY_STRING;
    private final RegexAST ast;

    public CalcASTFlagsVisitor(RegexAST ast) {
        this.ast = ast;
    }

    public static void run(RegexAST ast) {
        CalcASTFlagsVisitor visitor = new CalcASTFlagsVisitor(ast);
        visitor.run(ast.getRoot());
    }

    @Override
    protected void visit(BackReference backReference) {
        backReference.setHasBackReferences();
        backReference.getParent().setHasBackReferences();
        backReference.setMayMatchEmptyString(true);
    }

    @Override
    protected void visit(Sequence sequence) {
        sequence.setMayMatchEmptyString(true);
    }

    @Override
    protected void leave(Group group) {
        int flags = 0;
        for (Sequence s : group.getAlternatives()) {
            flags |= s.getFlags(OR_FLAGS_GROUP);
        }
        if (group.isLoop()) {
            flags |= RegexASTNode.FLAG_HAS_LOOPS;
        }
        if (group.isCapturing()) {
            flags |= RegexASTNode.FLAG_HAS_CAPTURE_GROUPS;
        }
        group.setFlags(flags, OR_FLAGS_GROUP);
        if (group.getParent() != null) {
            if (!group.mayMatchEmptyString() && !group.hasMin0Quantifier()) {
                group.getParent().setMayMatchEmptyString(false);
            }
            group.getParent().setFlags(group.getParent().getFlags(OR_FLAGS) | (flags & ~RegexASTNode.FLAG_MAY_MATCH_EMPTY_STRING), OR_FLAGS);
        }
    }

    @Override
    protected void visit(CharacterClass characterClass) {
        if (!characterClass.hasMin0Quantifier()) {
            characterClass.getParent().setMayMatchEmptyString(false);
        }
    }

    @Override
    protected void visit(PositionAssertion assertion) {
        switch (assertion.type) {
            case CARET -> assertion.getParent().setHasCaret();
            case DOLLAR -> assertion.getParent().setHasDollar();
            case MATCH_BEGIN, MATCH_END -> ast.getProperties().setMatchBoundaryAssertions();
        }
    }

    @Override
    protected void visit(LookBehindAssertion assertion) {
        assertion.setHasLookBehinds();
        assertion.getParent().setHasLookBehinds();
    }

    @Override
    protected void leave(LookBehindAssertion assertion) {
        setFlagsLookAroundAssertion(assertion);
    }

    @Override
    protected void visit(LookAheadAssertion assertion) {
        assertion.setHasLookAheads();
        assertion.getParent().setHasLookAheads();
    }

    @Override
    protected void leave(LookAheadAssertion assertion) {
        setFlagsLookAroundAssertion(assertion);
    }

    @Override
    protected void leave(AtomicGroup atomicGroup) {
        atomicGroup.setHasAtomicGroups();
        atomicGroup.getParent().setHasAtomicGroups();
        CalcASTPropsVisitor.setFlagsSubtreeRootNode(atomicGroup, OR_FLAGS);
        if (!atomicGroup.mayMatchEmptyString()) {
            atomicGroup.getParent().setMayMatchEmptyString(false);
        }
    }
}
