/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.util.Exceptions;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * Contains a full mapping of every {@link RegexASTSubtreeRootNode} in a {@link RegexAST} to a
 * {@link PureNFA}.
 */
public final class PureNFAMap {

    private final RegexAST ast;
    private final PureNFA root;
    private final PureNFAIndex lookArounds;
    private int prefixLength = 0;
    private StateSet<PureNFAIndex, PureNFA>[] prefixLookbehindEntries;

    public PureNFAMap(RegexAST ast, PureNFA root, PureNFAIndex lookArounds) {
        this.ast = ast;
        this.root = root;
        this.lookArounds = lookArounds;
    }

    public RegexAST getAst() {
        return ast;
    }

    public PureNFA getRoot() {
        return root;
    }

    public PureNFAIndex getLookArounds() {
        return lookArounds;
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    public RegexASTSubtreeRootNode getASTSubtree(PureNFA nfa) {
        return nfa == root ? ast.getRoot().getSubTreeParent() : ast.getLookArounds().get(nfa.getSubTreeId());
    }

    /**
     * Creates a {@link CodePointSet} that matches the union of all code point sets of
     * {@link PureNFAState#isCharacterClass() character class successor states} of the root NFA's
     * {@link PureNFA#getUnAnchoredInitialState() unanchored initial state}. If this can not be
     * calculated, e.g. because one of the successors is an {@link PureNFAState#isEmptyMatch() empty
     * match state}, {@code null} is returned.
     */
    public CodePointSet getMergedInitialStateCharSet(CompilationBuffer compilationBuffer) {
        CodePointSetAccumulator acc = compilationBuffer.getCodePointSetAccumulator1();
        if (mergeInitialStateMatcher(root, acc)) {
            return acc.toCodePointSet();
        }
        return null;
    }

    private boolean mergeInitialStateMatcher(PureNFA nfa, CodePointSetAccumulator acc) {
        for (PureNFATransition t : nfa.getUnAnchoredInitialState().getSuccessors()) {
            PureNFAState target = t.getTarget();
            switch (target.getKind()) {
                case PureNFAState.KIND_INITIAL_OR_FINAL_STATE:
                    break;
                case PureNFAState.KIND_BACK_REFERENCE:
                case PureNFAState.KIND_EMPTY_MATCH:
                    return false;
                case PureNFAState.KIND_LOOK_AROUND:
                    if (target.isLookAroundNegated() || target.isLookBehind(ast) || !mergeInitialStateMatcher(lookArounds.get(target.getLookAroundId()), acc)) {
                        return false;
                    }
                    break;
                case PureNFAState.KIND_CHARACTER_CLASS:
                    acc.addSet(target.getCharSet());
                    break;
                default:
                    throw Exceptions.shouldNotReachHere();
            }
        }
        return true;
    }

    /**
     * Mark a potential look-behind entry starting {@code offset} characters before the root
     * expression.
     */
    @SuppressWarnings("unchecked")
    public void addPrefixLookBehindEntry(PureNFA lookBehind, int offset) {
        if (prefixLookbehindEntries == null || prefixLookbehindEntries.length < offset) {
            int length = Integer.highestOneBit(offset);
            if (length < offset) {
                length *= 2;
            }
            assert length >= offset;
            prefixLookbehindEntries = prefixLookbehindEntries == null ? new StateSet[length] : Arrays.copyOf(prefixLookbehindEntries, length);
        }
        int i = offset - 1;
        if (prefixLookbehindEntries[i] == null) {
            prefixLookbehindEntries[i] = StateSet.create(lookArounds);
        }
        prefixLookbehindEntries[i].add(lookBehind);
        prefixLength = Math.max(prefixLength, offset);
    }

    public JsonValue toJson() {
        return Json.obj(Json.prop("root", root.toJson(ast)),
                        Json.prop("lookArounds", lookArounds.stream().map(x -> x.toJson(ast))));
    }
}
