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
package com.oracle.truffle.regex.tregex.parser.ast;

import java.util.ArrayList;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.RegexASTVisitorIterable;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * A Sequence is a concatenation of {@link Term}s.
 * <p>
 * Sequences are used as the alternatives in a {@link Group}. They are the only subtype of
 * {@link RegexASTNode} which does not extend {@link Term}. In order to coerce a Sequence into a
 * {@link Term}, wrap it in a {@link Group} (as its only alternative).
 * <p>
 * Corresponds to the goal symbol <em>Alternative</em> in the ECMAScript RegExp syntax.
 */
public final class Sequence extends RegexASTNode implements RegexASTVisitorIterable {

    private final ArrayList<Term> terms = new ArrayList<>();
    private short visitorIterationIndex = 0;

    Sequence() {
    }

    private Sequence(Sequence copy) {
        super(copy);
    }

    @Override
    public Sequence copy(RegexAST ast) {
        return ast.register(new Sequence(this));
    }

    @Override
    public Sequence copyRecursive(RegexAST ast, CompilationBuffer compilationBuffer) {
        Sequence copy = copy(ast);
        for (Term t : terms) {
            copy.add(t.copyRecursive(ast, compilationBuffer));
        }
        return copy;
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
     * {@link #add(Term)} and {@link #removeLastTerm()} instead.
     */
    public ArrayList<Term> getTerms() {
        return terms;
    }

    public boolean isEmpty() {
        return terms.isEmpty();
    }

    public int size() {
        return terms.size();
    }

    public Term getFirstTerm() {
        return terms.get(0);
    }

    public Term get(int i) {
        return terms.get(i);
    }

    public Term getLastTerm() {
        return terms.get(terms.size() - 1);
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
     * Replaces the term at position {@code index} with the given {@link Term}.
     *
     * @param term
     */
    public void replace(int index, Term term) {
        term.setParent(this);
        term.setSeqIndex(index);
        terms.set(index, term);
    }

    /**
     * Removes the last {@link Term} from this {@link Sequence}.
     */
    public void removeLastTerm() {
        terms.remove(terms.size() - 1);
    }

    public boolean isFirstInGroup() {
        return getParent().getFirstAlternative() == this;
    }

    public boolean isLastInGroup() {
        return getParent().getAlternatives().get(getParent().getAlternatives().size() - 1) == this;
    }

    public boolean isPenultimateInGroup() {
        ArrayList<Sequence> alt = getParent().getAlternatives();
        return alt.size() > 1 && alt.get(alt.size() - 2) == this;
    }

    public boolean isLiteral() {
        if (isEmpty()) {
            return false;
        }
        for (Term t : terms) {
            if (!(t.isCharacterClass()) || t.asCharacterClass().hasNotUnrolledQuantifier()) {
                return false;
            }
        }
        return true;
    }

    public boolean isSingleCharClass() {
        return size() == 1 && isLiteral();
    }

    public int getEnclosedCaptureGroupsLow() {
        int lo = Integer.MAX_VALUE;
        for (Term t : terms) {
            if (t instanceof Group) {
                Group g = (Group) t;
                if (g.getEnclosedCaptureGroupsLow() != g.getEnclosedCaptureGroupsHigh()) {
                    lo = Math.min(lo, g.getEnclosedCaptureGroupsLow());
                }
                if (g.isCapturing()) {
                    lo = Math.min(lo, g.getGroupNumber());
                }
            }
        }
        return lo == Integer.MAX_VALUE ? -1 : lo;
    }

    public int getEnclosedCaptureGroupsHigh() {
        int hi = Integer.MIN_VALUE;
        for (Term t : terms) {
            if (t instanceof Group) {
                Group g = (Group) t;
                if (g.getEnclosedCaptureGroupsLow() != g.getEnclosedCaptureGroupsHigh()) {
                    hi = Math.max(hi, g.getEnclosedCaptureGroupsHigh());
                }
                if (g.isCapturing()) {
                    hi = Math.max(hi, g.getGroupNumber() + 1);
                }
            }
        }
        return hi == Integer.MIN_VALUE ? -1 : hi;
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
    public boolean equalsSemantic(RegexASTNode obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Sequence)) {
            return false;
        }
        Sequence o = (Sequence) obj;
        if (size() != o.size()) {
            return false;
        }
        for (int i = 0; i < size(); i++) {
            if (!terms.get(i).equalsSemantic(o.terms.get(i))) {
                return false;
            }
        }
        return true;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return terms.stream().map(Term::toString).collect(Collectors.joining(""));
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return toJson("Sequence").append(Json.prop("terms", terms));
    }
}
