/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.analysis;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.oracle.truffle.espresso.analysis.graph.LinkedBlock;

public final class Util {
    private Util() {
    }

    public static int[] toIntArray(ArrayList<Integer> targets) {
        int[] result = new int[targets.size()];
        int pos = 0;
        for (int i : targets) {
            result[pos++] = i;
        }
        return result;
    }

    public static BitSet mergeBitSets(BitSet[] lives, int size) {
        BitSet merges = new BitSet(size);
        for (BitSet live : lives) {
            merges.or(live);
        }
        return merges;
    }

    public static BitSet mergeBitSets(Iterable<BitSet> lives, int size) {
        BitSet merges = new BitSet(size);
        for (BitSet live : lives) {
            merges.or(live);
        }
        return merges;
    }

    public static boolean successorsAreDoneOrLoops(AnalysisProcessor processor, LinkedBlock b) {
        for (int succ : b.successorsID()) {
            if (!(processor.isDone(succ) || processor.isInProcess(succ))) {
                return false;
            }
        }
        return true;
    }

    public static Iterable<Integer> bitSetIterator(BitSet bs) {
        return new BitSetIterator(bs);
    }

    public static boolean assertNoDupe(int[] array) {
        Set<Integer> set = new HashSet<>();
        for (int i : array) {
            if (set.contains(i)) {
                return false;
            }
            set.add(i);
        }
        return true;
    }

    private static final class BitSetIterator implements Iterable<Integer>, Iterator<Integer> {
        private final BitSet bs;
        private int pos = -1;
        private int nextPos = -1;
        private boolean nextAvailable = false;

        private BitSetIterator(BitSet bs) {
            this.bs = bs;
        }

        @Override
        public Iterator<Integer> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            if (!nextAvailable) {
                nextAvailable = true;
                return (nextPos = bs.nextSetBit(pos + 1)) >= 0;
            }
            return nextPos >= 0;
        }

        @Override
        public Integer next() {
            if (!nextAvailable) {
                nextPos = bs.nextSetBit(pos + 1);
                assert nextPos >= 0 : "No hasNext() called.";
            }
            pos = nextPos;
            nextAvailable = false;
            return pos;
        }
    }
}
