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
package com.oracle.truffle.regex.tregex.parser.ast;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * {@link MatchFound} nodes are {@link RegexASTNode}s that represent the initial/final states of the
 * non-deterministic finite state automaton generated from the regular expression.
 * <p>
 * Regular expressions are translated into non-deterministic finite state automata, with each
 * {@link RegexASTNode} in the {@link RegexAST} contributing some of the states or transitions. The
 * {@link MatchFound} nodes are those that contribute the final (accepting) states. The root group
 * of every regular expression is linked (using the 'next' pointer) to a single {@link MatchFound}
 * node. Other {@link MatchFound} nodes appear in look-behind and look-ahead assertions, where they
 * contribute the final states of their subautomata (look-around assertions generate subautomata
 * which are then joined with the root automaton using a product construction).
 * <p>
 * {@link MatchFound} nodes are also used as initial states (the initial states of the forward
 * search automaton are the final states of the reverse search automaton). Therefore, there is a set
 * of {@link MatchFound} nodes used as final states in forward search (reachable by 'next' pointers)
 * and as initial states in reverse search and a set of {@link MatchFound} nodes used as final
 * states in reverse search (reachable by 'prev' pointers) and as initial states in forward search.
 * {@link MatchFound} being used as NFA initial states is also why they can have a next-pointer (
 * {@link #getNext()}) themselves (see {@link RegexAST#getNFAUnAnchoredInitialState(int)}).
 */
public class MatchFound extends Term {

    private RegexASTNode next;

    @Override
    public MatchFound copy(RegexAST ast, boolean recursive) {
        throw new UnsupportedOperationException();
    }

    public RegexASTNode getNext() {
        return next;
    }

    public void setNext(RegexASTNode next) {
        this.next = next;
    }

    @Override
    public SourceSection getSourceSection() {
        if (super.getSourceSection() == null) {
            RegexASTSubtreeRootNode parent = getSubTreeParent();
            if (parent == null || parent.getSourceSection() == null) {
                // initial state, not part of actual AST
                return null;
            }
            // set source section to empty space after parent tree
            SourceSection parentSourceSection = parent.getSourceSection();
            super.setSourceSection(parentSourceSection.getSource().createSection(parentSourceSection.getCharEndIndex(), 0));
        }
        return super.getSourceSection();
    }

    @Override
    public String toString() {
        return "::";
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return toJson("MatchFound");
    }
}
