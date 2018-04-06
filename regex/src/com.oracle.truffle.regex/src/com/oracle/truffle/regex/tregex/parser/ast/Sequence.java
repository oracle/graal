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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.RegexASTVisitorIterable;
import com.oracle.truffle.regex.tregex.util.DebugUtil;

import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * A Sequence is a concatenation of {@link Term}s.
 * <p>
 * Sequences are used as the alternatives in a {@link Group}. They are the only subtype of
 * {@link RegexASTNode} which does not extend {@link Term}. In order to coerce a Sequence into a
 * {@link Term}, wrap it in a {@link Group} (as its only alternative).
 * <p>
 * Corresponds to the goal symbol <em>Alternative</em> in the ECMAScript RegExp syntax.
 */
public class Sequence extends RegexASTNode implements RegexASTVisitorIterable {

    private final ArrayList<Term> terms = new ArrayList<>();
    private short visitorIterationIndex = 0;

    Sequence() {
    }

    private Sequence(Sequence copy, RegexAST ast) {
        super(copy);
        for (Term t : copy.terms) {
            add(t.copy(ast));
        }
    }

    @Override
    public Sequence copy(RegexAST ast) {
        return ast.register(new Sequence(this, ast));
    }

    @Override
    public Group getParent() {
        return (Group) super.getParent();
    }

    @Override
    public void setParent(RegexASTNode parent) {
        assert parent instanceof Group;
        super.setParent(parent);
    }

    /**
     * Returns the list of terms that constitute this {@link Sequence}.
     * <p>
     * Note that elements should not be added or removed from this list. Use the methods
     * {@link #add(Term)} and {@link #removeLast()} instead.
     */
    public ArrayList<Term> getTerms() {
        return terms;
    }

    public boolean isEmpty() {
        return terms.isEmpty();
    }

    public Term getFirstTerm() {
        return terms.get(0);
    }

    public Term getLastTerm() {
        return terms.get(terms.size() - 1);
    }

    /**
     * Marks the node as dead, i.e. unmatchable.
     * <p>
     * Note that using this setter also traverses the ancestors and children of this node and
     * updates their "dead" status as well.
     */
    @Override
    public void markAsDead() {
        super.markAsDead();
        if (getParent() == null) {
            return;
        }
        for (Term t : terms) {
            t.markAsDead();
        }
        for (Sequence s : getParent().getAlternatives()) {
            if (!s.isDead()) {
                return;
            }
        }
        getParent().markAsDead();
    }

    /**
     * Adds a {@link Term} to the end of the {@link Sequence}.
     * 
     * @param term
     */
    public void add(Term term) {
        term.setParent(this);
        term.setSeqIndex(terms.size());
        terms.add(term);
    }

    /**
     * Removes the last {@link Term} from this {@link Sequence}.
     */
    public void removeLast() {
        terms.remove(terms.size() - 1);
    }

    public boolean isLiteral() {
        if (isEmpty()) {
            return false;
        }
        for (Term t : terms) {
            if (!(t instanceof CharacterClass)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public RegexASTSubtreeRootNode getSubTreeParent() {
        return getParent().getSubTreeParent();
    }

    @Override
    public boolean visitorHasNext() {
        return visitorIterationIndex < terms.size();
    }

    @Override
    public void resetVisitorIterator() {
        visitorIterationIndex = 0;
    }

    @Override
    public RegexASTNode visitorGetNext(boolean reverse) {
        if (reverse) {
            return terms.get(terms.size() - (++visitorIterationIndex));
        }
        return terms.get(visitorIterationIndex++);
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public String toString() {
        return terms.stream().map(Term::toString).collect(Collectors.joining(""));
    }

    @Override
    public DebugUtil.Table toTable() {
        return toTable("Sequence").append(terms.stream().map(RegexASTNode::toTable));
    }
}
