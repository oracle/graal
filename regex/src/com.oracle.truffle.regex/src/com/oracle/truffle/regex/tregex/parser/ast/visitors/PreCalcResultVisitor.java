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

import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;
import com.oracle.truffle.regex.tregex.string.AbstractString;
import com.oracle.truffle.regex.tregex.string.AbstractStringBuffer;
import com.oracle.truffle.regex.tregex.util.Exceptions;

public final class PreCalcResultVisitor extends DepthFirstTraversalRegexASTVisitor {

    private final boolean extractLiteral;
    private final boolean unrollGroups;

    private final RegexAST ast;
    private int index = 0;
    private final AbstractStringBuffer literal;
    private final AbstractStringBuffer mask;
    private final PreCalculatedResultFactory result;
    private PreCalcResultVisitor groupUnroller;

    private PreCalcResultVisitor(RegexAST ast, boolean extractLiteral) {
        this.ast = ast;
        result = new PreCalculatedResultFactory(ast.getNumberOfCaptureGroups());
        this.extractLiteral = extractLiteral;
        if (extractLiteral) {
            literal = ast.getEncoding().createStringBuffer(ast.getRoot().getMinPath());
            mask = ast.getProperties().hasCharClasses() ? ast.getEncoding().createStringBuffer(ast.getRoot().getMinPath()) : null;
        } else {
            literal = null;
            mask = null;
        }
        unrollGroups = true;
    }

    private PreCalcResultVisitor(RegexAST ast, boolean extractLiteral, boolean unrollGroups, int index, AbstractStringBuffer literal, AbstractStringBuffer mask, PreCalculatedResultFactory result) {
        this.ast = ast;
        this.extractLiteral = extractLiteral;
        this.unrollGroups = unrollGroups;
        this.index = index;
        this.literal = literal;
        this.mask = mask;
        this.result = result;
    }

    public static PreCalcResultVisitor run(RegexAST ast, boolean extractLiteral) {
        PreCalcResultVisitor visitor = new PreCalcResultVisitor(ast, extractLiteral);
        visitor.run(ast.getRoot());
        visitor.result.setLength(visitor.index);
        return visitor;
    }

    public static PreCalculatedResultFactory createResultFactory(RegexAST ast) {
        PreCalcResultVisitor visitor = new PreCalcResultVisitor(ast, false);
        visitor.run(ast.getRoot());
        visitor.result.setLength(visitor.index);
        return visitor.result;
    }

    public AbstractString getLiteral() {
        return literal.materialize();
    }

    public boolean hasMask() {
        return mask != null;
    }

    public AbstractString getMask() {
        return mask == null ? null : mask.materialize();
    }

    public PreCalculatedResultFactory getResultFactory() {
        return result;
    }

    @Override
    protected void visit(BackReference backReference) {
        throw Exceptions.shouldNotReachHere();
    }

    @Override
    protected void visit(Group group) {
        if (group.isCapturing()) {
            result.setStart(group.getGroupNumber(), index);
        }
    }

    @Override
    protected void leave(Group group) {
        if (group.isCapturing()) {
            result.setEnd(group.getGroupNumber(), index);
        }
        if (unrollGroups && group.hasNotUnrolledQuantifier()) {
            assert group.getQuantifier().getMin() == group.getQuantifier().getMax();
            if (groupUnroller == null) {
                groupUnroller = new PreCalcResultVisitor(ast, extractLiteral, false, index, literal, mask, result);
            }
            groupUnroller.index = index;
            for (int i = 0; i < group.getQuantifier().getMin() - 1; i++) {
                groupUnroller.run(group);
            }
            index = groupUnroller.index;
        }
    }

    @Override
    protected void visit(Sequence sequence) {
    }

    @Override
    protected void visit(PositionAssertion assertion) {
    }

    @Override
    protected void visit(LookBehindAssertion assertion) {
        throw Exceptions.shouldNotReachHere();
    }

    @Override
    protected void visit(LookAheadAssertion assertion) {
        throw Exceptions.shouldNotReachHere();
    }

    @Override
    protected void visit(CharacterClass characterClass) {
        assert !characterClass.hasQuantifier() || characterClass.getQuantifier().getMin() == characterClass.getQuantifier().getMax();
        for (int i = 0; i < (characterClass.hasNotUnrolledQuantifier() ? characterClass.getQuantifier().getMin() : 1); i++) {
            int cp = characterClass.getCharSet().getMin();
            if (extractLiteral) {
                if (mask == null) {
                    literal.append(cp);
                } else {
                    characterClass.extractSingleChar(literal, mask);
                }
            }
            index += ast.getEncoding().getEncodedSize(cp);
        }
    }
}
