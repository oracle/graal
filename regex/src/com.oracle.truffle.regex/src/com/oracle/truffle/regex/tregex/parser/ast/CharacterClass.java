/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.JSRegexParser;
import com.oracle.truffle.regex.tregex.string.AbstractStringBuffer;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * A {@link Term} that matches characters belonging to a specified set of characters.
 * <p>
 * Corresponds to the right-hand sides <em>PatternCharacter</em>, <strong>.</strong> and
 * <em>CharacterClass</em> of the goal symbol <em>Atom</em> and the right-hand sides
 * <em>CharacterClassEscape</em> and <em>CharacterEscape</em> of the goal symbol <em>AtomEscape</em>
 * in the ECMAScript RegExp syntax.
 * <p>
 * Note that {@link CharacterClass} nodes and the {@link CodePointSet}s that they rely on can only
 * match characters from the Basic Multilingual Plane (and whose code point fits into 16-bit
 * integers). Any term which matches characters outside of the Basic Multilingual Plane is expanded
 * by {@link JSRegexParser} into a more complex expression which matches the individual code units
 * that would make up the UTF-16 encoding of those characters.
 */
public class CharacterClass extends QuantifiableTerm {

    private CodePointSet charSet;
    // look-behind groups which might match the same character as this CharacterClass node
    private StateSet<GlobalSubTreeIndex, LookBehindAssertion> lookBehindEntries;

    /**
     * Creates a new {@link CharacterClass} node which matches the set of characters specified by
     * the {@code matcherBuilder}.
     */
    CharacterClass(CodePointSet charSet) {
        this.charSet = charSet;
    }

    private CharacterClass(CharacterClass copy, CodePointSet charSet) {
        super(copy);
        this.charSet = charSet;
    }

    @Override
    public CharacterClass copy(RegexAST ast) {
        return ast.register(new CharacterClass(this, charSet));
    }

    @Override
    public CharacterClass copyRecursive(RegexAST ast, CompilationBuffer compilationBuffer) {
        return ast.register(new CharacterClass(this, ast.getEncoding().getFullSet().createIntersection(charSet, compilationBuffer)));
    }

    @Override
    public Sequence getParent() {
        return (Sequence) super.getParent();
    }

    /**
     * Returns the {@link CodePointSet} representing the set of characters that can be matched by
     * this {@link CharacterClass}.
     */
    public CodePointSet getCharSet() {
        return charSet;
    }

    public void setCharSet(CodePointSet charSet) {
        this.charSet = charSet;
    }

    public boolean wasSingleChar() {
        return isFlagSet(FLAG_CHARACTER_CLASS_WAS_SINGLE_CHAR);
    }

    public void setWasSingleChar() {
        setWasSingleChar(true);
    }

    public void setWasSingleChar(boolean value) {
        setFlag(FLAG_CHARACTER_CLASS_WAS_SINGLE_CHAR, value);
    }

    @Override
    public boolean isUnrollingCandidate() {
        return hasQuantifier() && getQuantifier().isWithinThreshold(TRegexOptions.TRegexQuantifierUnrollThresholdSingleCC);
    }

    public void addLookBehindEntry(RegexAST ast, LookBehindAssertion lookBehindEntry) {
        if (lookBehindEntries == null) {
            lookBehindEntries = StateSet.create(ast.getSubtrees());
        }
        lookBehindEntries.add(lookBehindEntry);
    }

    public boolean hasLookBehindEntries() {
        return lookBehindEntries != null;
    }

    /**
     * Returns the (fixed-length) look-behind assertions whose first characters can match the same
     * character as this node. Note that the set contains the {@link Group} bodies of the
     * {@link LookBehindAssertion} nodes, not the {@link LookBehindAssertion} nodes themselves.
     */
    public Set<LookBehindAssertion> getLookBehindEntries() {
        if (lookBehindEntries == null) {
            return Collections.emptySet();
        }
        return lookBehindEntries;
    }

    public void extractSingleChar(AbstractStringBuffer literal, AbstractStringBuffer mask) {
        if (charSet.matchesSingleChar()) {
            literal.append(charSet.getMin());
            assert mask.getEncoding().getEncodedSize(0) == 1;
            for (int i = 0; i < mask.getEncoding().getEncodedSize(charSet.getMin()); i++) {
                mask.append(0);
            }
        } else {
            assert charSet.matches2CharsWith1BitDifference();
            int c1 = charSet.getMin();
            int c2 = charSet.getMax();
            literal.appendOR(c1, c2);
            mask.appendXOR(c1, c2);
        }
    }

    @Override
    public boolean equalsSemantic(RegexASTNode obj, boolean ignoreQuantifier) {
        return obj instanceof CharacterClass && ((CharacterClass) obj).getCharSet().equals(charSet) && (ignoreQuantifier || quantifierEquals((CharacterClass) obj));
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return charSet.toString() + quantifierToString();
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        final JsonObject json = toJson("CharacterClass").append(Json.prop("charSet", charSet));
        if (lookBehindEntries != null) {
            json.append(Json.prop("lookBehindEntries", lookBehindEntries.stream().map(RegexASTNode::astNodeId).collect(Collectors.toList())));
        }
        return json;
    }
}
