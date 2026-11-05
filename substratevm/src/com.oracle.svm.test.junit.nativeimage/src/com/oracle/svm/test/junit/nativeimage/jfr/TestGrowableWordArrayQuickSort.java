/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, 2025, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.WordFactory;
import org.graalvm.word.impl.Word;
import org.junit.Test;

import com.oracle.svm.core.collections.GrowableWordArray;
import com.oracle.svm.core.collections.GrowableWordArrayAccess;
import com.oracle.svm.core.nmt.NmtCategory;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestGrowableWordArrayQuickSort {
    @Test
    public void testDeterministicEdgeCases() {
        assertSorted();
        assertSorted(42);
        assertSorted(2, 1);
        assertSorted(1, 2, 3, 4, 5);
        assertSorted(5, 4, 3, 2, 1);
        assertSorted(7, 7, 7, 7, 7);
        assertSorted(Long.MAX_VALUE, 0, -1, Long.MIN_VALUE, 1, Long.MAX_VALUE, Long.MIN_VALUE);
    }

    @Test
    public void testRandomizedInputWithDuplicates() {
        Random random = new Random(0x5eedL);
        long[] values = new long[1000];
        long nextLong = 0;
        for (int i = 0; i < values.length; i++) {
            if (i % 50 != 0) {
                nextLong = random.nextLong();
            }
            values[i] = nextLong;
        }

        assertSorted(values);
    }

    static int compare(Word a, Word b) {
        return Long.compare(a.rawValue(), b.rawValue());
    }

    private static void assertSorted(long... values) {
        long[] expected = Arrays.copyOf(values, values.length);
        Arrays.sort(expected);

        GrowableWordArray gwa = StackValue.get(GrowableWordArray.class);
        GrowableWordArrayAccess.initialize(gwa);
        try {
            for (long value : values) {
                assertTrue(GrowableWordArrayAccess.add(gwa, WordFactory.signed(value), NmtCategory.JFR));
            }

            GrowableWordArrayAccess.qsort(gwa, 0, gwa.getSize() - 1, TestGrowableWordArrayQuickSort::compare);
            assertEquals(expected.length, gwa.getSize());
            for (int i = 0; i < expected.length; i++) {
                assertEquals(expected[i], GrowableWordArrayAccess.get(gwa, i).rawValue());
            }
        } finally {
            GrowableWordArrayAccess.freeData(gwa);
        }
    }
}
