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

import com.oracle.truffle.regex.tregex.matchers.MatcherBuilder;
import com.oracle.truffle.regex.tregex.nfa.ASTNodeSet;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * A {@link Term} that matches characters belonging to a specified set of characters.
 * <p>
 * Corresponds to the right-hand sides <em>PatternCharacter</em>, <strong>.</strong> and
 * <em>CharacterClass</em> of the goal symbol <em>Atom</em> and the right-hand sides
 * <em>CharacterClassEscape</em> and <em>CharacterEscape</em> of the goal symbol <em>AtomEscape</em>
 * in the ECMAScript RegExp syntax.
 * <p>
 * Note that {@link CharacterClass} nodes and the {@link MatcherBuilder}s that they rely on can only
 * match characters from the Basic Multilingual Plane (and whose code point fits into 16-bit
 * integers). Any term which matches characters outside of the Basic Multilingual Plane is expanded
 * by {@link RegexParser} into a more complex expression which matches the individual code units
 * that would make up the UTF-16 encoding of those characters.
 */
public class CharacterClass extends Term {

    private MatcherBuilder matcherBuilder;
    // look-behind groups which might match the same character as this CharacterClass node
    private ASTNodeSet<Group> lookBehindEntries;

    /**
     * Creates a new {@link CharacterClass} node which matches the set of characters specified by
     * the {@code matcherBuilder}.
     */
    CharacterClass(MatcherBuilder matcherBuilder) {
        this.matcherBuilder = matcherBuilder;
    }

    private CharacterClass(CharacterClass copy) {
        super(copy);
        matcherBuilder = copy.matcherBuilder;
    }

    @Override
    public CharacterClass copy(RegexAST ast, boolean recursive) {
        return ast.register(new CharacterClass(this));
    }

    /**
     * Returns the {@link MatcherBuilder} representing the set of characters that can be matched by
     * this {@link CharacterClass}.
     */
    public MatcherBuilder getMatcherBuilder() {
        return matcherBuilder;
    }

    public void setMatcherBuilder(MatcherBuilder matcherBuilder) {
        this.matcherBuilder = matcherBuilder;
    }

    public void addLookBehindEntry(RegexAST ast, Group lookBehindEntry) {
        if (lookBehindEntries == null) {
            lookBehindEntries = new ASTNodeSet<>(ast);
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
    public Set<Group> getLookBehindEntries() {
        if (lookBehindEntries == null) {
            return Collections.emptySet();
        }
        return lookBehindEntries;
    }

    @Override
    public String toString() {
        return matcherBuilder.toString();
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        final JsonObject json = toJson("CharacterClass").append(Json.prop("matcherBuilder", matcherBuilder));
        if (lookBehindEntries != null) {
            json.append(Json.prop("lookBehindEntries", lookBehindEntries.stream().map(RegexASTNode::astNodeId).collect(Collectors.toList())));
        }
        return json;
    }
}
