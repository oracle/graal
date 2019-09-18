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

import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;

public final class PreCalcResultVisitor extends DepthFirstTraversalRegexASTVisitor {

    private final boolean extractLiteral;

    private int index = 0;
    private final char[] literal;
    private final char[] mask;
    private final PreCalculatedResultFactory result;

    private PreCalcResultVisitor(RegexAST ast, boolean extractLiteral) {
        result = new PreCalculatedResultFactory(ast.getNumberOfCaptureGroups());
        this.extractLiteral = extractLiteral;
        if (extractLiteral) {
            literal = new char[ast.getRoot().getMinPath()];
            mask = ast.getProperties().hasCharClasses() ? new char[ast.getRoot().getMinPath()] : null;
        } else {
            literal = null;
            mask = null;
        }
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

    public String getLiteral() {
        return new String(literal);
    }

    public boolean hasMask() {
        return mask != null;
    }

    public String getMask() {
        return mask == null ? null : new String(mask);
    }

    public PreCalculatedResultFactory getResultFactory() {
        return result;
    }

    @Override
    protected void visit(BackReference backReference) {
        throw new IllegalArgumentException();
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
    }

    @Override
    protected void visit(Sequence sequence) {
    }

    @Override
    protected void visit(PositionAssertion assertion) {
    }

    @Override
    protected void visit(LookBehindAssertion assertion) {
        throw new IllegalArgumentException();
    }

    @Override
    protected void visit(LookAheadAssertion assertion) {
        throw new IllegalArgumentException();
    }

    @Override
    protected void visit(CharacterClass characterClass) {
        if (extractLiteral) {
            if (mask == null) {
                literal[index] = (char) characterClass.getCharSet().getLo(0);
            } else {
                characterClass.extractSingleChar(literal, mask, index);
            }
        }
        index++;
    }

    @Override
    protected void visit(MatchFound matchFound) {
    }
}
