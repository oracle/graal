/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
