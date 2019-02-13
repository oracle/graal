/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;

/**
 * This visitor is used for setting the {@link SourceSection} of AST subtrees that are copied into
 * the parser tree as substitutions for things like word boundaries and position assertions in
 * multi-line mode. It will set the source section of all nodes in the subtree to the
 * {@link SourceSection} object passed to {@link #run(Group, SourceSection)}.
 *
 * @see com.oracle.truffle.regex.tregex.parser.RegexParser
 */
public final class SetSourceSectionVisitor extends DepthFirstTraversalRegexASTVisitor {

    private SourceSection sourceSection;

    public void run(Group root, SourceSection srcSection) {
        this.sourceSection = srcSection;
        run(root);
    }

    @Override
    protected void visit(BackReference backReference) {
        backReference.setSourceSection(sourceSection);
    }

    @Override
    protected void visit(Group group) {
        group.setSourceSectionBegin(null);
        group.setSourceSectionEnd(null);
        group.setSourceSection(sourceSection);
    }

    @Override
    protected void leave(Group group) {
    }

    @Override
    protected void visit(Sequence sequence) {
        sequence.setSourceSection(sourceSection);
    }

    @Override
    protected void visit(PositionAssertion assertion) {
        assertion.setSourceSection(sourceSection);
    }

    @Override
    protected void visit(LookBehindAssertion assertion) {
        // look-around assertions always delegate their source section information to their inner
        // group
    }

    @Override
    protected void visit(LookAheadAssertion assertion) {
        // look-around assertions always delegate their source section information to their inner
        // group
    }

    @Override
    protected void visit(CharacterClass characterClass) {
        characterClass.setSourceSection(sourceSection);
    }

    @Override
    protected void visit(MatchFound matchFound) {
        // match-found nodes delegate their source section information to their parent subtree root
        // node
    }
}
