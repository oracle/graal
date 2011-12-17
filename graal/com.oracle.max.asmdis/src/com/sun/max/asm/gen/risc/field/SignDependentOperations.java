/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.gen.risc.field;

import java.util.*;

import com.sun.max.asm.gen.risc.bitRange.*;

/**
 */
public enum SignDependentOperations {

    UNSIGNED {
        @Override
        public int minArgumentValue(BitRange bitRange) {
            return 0;
        }

        @Override
        public int maxArgumentValue(BitRange bitRange) {
            return bitRange.valueMask();
        }

        @Override
        public int assemble(int unsignedInt, BitRange bitRange) throws IndexOutOfBoundsException {
            return bitRange.assembleUnsignedInt(unsignedInt);
        }

        @Override
        public int extract(int instruction, BitRange bitRange) {
            return bitRange.extractUnsignedInt(instruction);
        }

        @Override
        public List<Integer> legalTestArgumentValues(int min, int max, int grain) {
            assert min == 0;
            assert grain > 0;
            assert max >= grain;
            final List<Integer> result = smallContiguousRange(min, max, grain);
            return result == null ? Arrays.asList(0, grain, max - grain, max) : result;
        }
    },

    SIGNED {

        @Override
        public int minArgumentValue(BitRange bitRange) {
            return -1 << (bitRange.width() - 1);
        }

        @Override
        public int maxArgumentValue(BitRange bitRange) {
            return bitRange.valueMask() >>> 1;
        }

        @Override
        public int assemble(int signedInt, BitRange bitRange) throws IndexOutOfBoundsException {
            return bitRange.assembleSignedInt(signedInt);
        }

        @Override
        public int extract(int instruction, BitRange bitRange) {
            return bitRange.extractSignedInt(instruction);
        }

        @Override
        public List<Integer> legalTestArgumentValues(int min, int max, int grain) {
            assert min < 0;
            assert grain > 0;
            assert max >= grain;
            final List<Integer> result = smallContiguousRange(min, max, grain);
            return result == null ? Arrays.asList(min, min + grain, -grain, 0, grain, max - grain, max) : null;
        }
    },

    SIGNED_OR_UNSIGNED {

        @Override
        public int minArgumentValue(BitRange bitRange) {
            return SIGNED.minArgumentValue(bitRange);
        }

        @Override
        public int maxArgumentValue(BitRange bitRange) {
            return UNSIGNED.maxArgumentValue(bitRange);
        }

        @Override
        public int assemble(int value, BitRange bitRange) throws IndexOutOfBoundsException {
            return (value >= 0) ? UNSIGNED.assemble(value, bitRange) : SIGNED.assemble(value, bitRange);
        }

        @Override
        public int extract(int instruction, BitRange bitRange) {
            return UNSIGNED.extract(instruction, bitRange);
        }

        @Override
        public List<Integer> legalTestArgumentValues(int min, int max, int grain) {
            assert min < 0;
            assert grain > 0;
            assert max >= grain;
            final List<Integer> result = smallContiguousRange(min, max, grain);
            return result == null ? Arrays.asList(
                // We only test positive arguments, since negative ones would be returned as positive by extract()
                // and that is correct, because there is no way to tell just by the instruction which sign was meant
                0, grain, max / 2, max - grain, max
            ) : null;
        }
    };

    /**
     * Creates a sequence of all the integers inclusive between a given min and max if the
     * sequence contains 32 or less items. Otherwise, this method returns null.
     */
    public static List<Integer> smallContiguousRange(int min, int max, int grain) {
        final long range = (((long) max - (long) min) + 1) / grain;
        if (range > 0 && range <= 32) {
            final List<Integer> result = new ArrayList<Integer>((int) range);
            for (int i = min; i <= max; i += grain * 2) {
                result.add(i);
            }
            return result;
        }
        return null;
    }

    public abstract int minArgumentValue(BitRange bitRange);

    public abstract int maxArgumentValue(BitRange bitRange);

    /**
     * @return instruction
     * @throws IndexOutOfBoundsException
     */
    public abstract int assemble(int value, BitRange bitRange) throws IndexOutOfBoundsException;

    public abstract int extract(int instruction, BitRange bitRange);

    public abstract List<Integer> legalTestArgumentValues(int min, int max, int grain);
}
