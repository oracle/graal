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
package com.oracle.truffle.regex.tregex.dfa;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.automaton.StateSet;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.Arrays;
import java.util.PrimitiveIterator;

public final class NFAStateSet extends StateSet<NFAState> implements JsonConvertible {

    private int[] stateIndexMap;

    public NFAStateSet(NFA nfa) {
        super(nfa);
    }

    public NFAStateSet(NFA nfa, NFAState state) {
        super(nfa);
        add(state);
    }

    private NFAStateSet(NFAStateSet copy) {
        super(copy);
        this.stateIndexMap = copy.isStateIndexMapCreated() ? Arrays.copyOf(copy.stateIndexMap, copy.stateIndexMap.length) : null;
    }

    @Override
    public NFAStateSet copy() {
        return new NFAStateSet(this);
    }

    private boolean isStateIndexMapCreated() {
        return stateIndexMap != null;
    }

    @Override
    public boolean add(NFAState state) {
        if (isStateIndexMapCreated()) {
            throw new IllegalStateException("state set must not be altered after state index map creation!");
        }
        return super.add(state);
    }

    private void createStateIndexMap() {
        if (!isStateIndexMapCreated()) {
            stateIndexMap = new int[size()];
            int i = 0;
            PrimitiveIterator.OfInt iterator = intIterator();
            while (iterator.hasNext()) {
                stateIndexMap[i++] = iterator.nextInt();
            }
            assert isSorted(stateIndexMap);
        }
    }

    private static boolean isSorted(int[] array) {
        int prev = Integer.MIN_VALUE;
        for (int i : array) {
            if (prev > i) {
                return false;
            }
            prev = i;
        }
        return true;
    }

    public int getStateIndex(NFAState state) {
        createStateIndexMap();
        assert contains(state);
        return Arrays.binarySearch(stateIndexMap, 0, size(), state.getId());
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.array(this);
    }
}
