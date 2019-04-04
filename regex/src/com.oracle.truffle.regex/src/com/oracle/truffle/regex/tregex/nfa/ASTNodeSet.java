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
package com.oracle.truffle.regex.tregex.nfa;

import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;

import java.util.Collection;

public class ASTNodeSet<S extends RegexASTNode> extends StateSet<S> {

    public ASTNodeSet(RegexAST ast) {
        super(ast);
    }

    public ASTNodeSet(RegexAST ast, S node) {
        super(ast);
        add(node);
    }

    public ASTNodeSet(RegexAST ast, Collection<S> initialNodes) {
        super(ast);
        addAll(initialNodes);
    }

    private ASTNodeSet(ASTNodeSet<S> copy) {
        super(copy);
    }

    public RegexAST getAst() {
        return (RegexAST) getStateIndex();
    }

    @Override
    public ASTNodeSet<S> copy() {
        return new ASTNodeSet<>(this);
    }
}
