/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nfa;

import java.util.Collections;
import java.util.Set;

import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.tregex.automaton.AbstractState;
import com.oracle.truffle.regex.tregex.automaton.SimpleStateIndex;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;

/**
 * Represents a state of a {@link PureNFA}. All {@link PureNFAState}s correspond to a single
 * {@link RegexASTNode}, referenced by {@link #getAstNodeId()}. Initial and final states correspond
 * to the NFA helper nodes contained in {@link RegexASTSubtreeRootNode}. All other states correspond
 * to either {@link CharacterClass}es or {@link BackReference}s.
 */
public class PureNFAState extends AbstractState<PureNFAState, PureNFATransition> {

    private static final PureNFATransition[] EMPTY_TRANSITIONS = {};

    private final short astNodeId;
    private final CodePointSet charSet;
    private Set<PureNFA> lookBehindEntries;

    public PureNFAState(short id, short astNodeId, CodePointSet charSet) {
        super(id, EMPTY_TRANSITIONS);
        this.astNodeId = astNodeId;
        this.charSet = charSet;
    }

    public short getAstNodeId() {
        return astNodeId;
    }

    public CodePointSet getCharSet() {
        return charSet;
    }

    @Override
    protected PureNFATransition[] createTransitionsArray(int length) {
        return new PureNFATransition[length];
    }

    public Set<PureNFA> getLookBehindEntries() {
        if (lookBehindEntries == null) {
            return Collections.emptySet();
        }
        return lookBehindEntries;
    }

    public void addLookBehindEntry(SimpleStateIndex<PureNFA> lookBehinds, PureNFA lb) {
        if (lookBehindEntries == null) {
            lookBehindEntries = StateSet.create(lookBehinds);
        }
        lookBehindEntries.add(lb);
    }
}
