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

import org.junit.Test;

import com.oracle.svm.core.nmt.NmtCategory;
import jdk.graal.compiler.word.Word;
import org.graalvm.word.WordFactory;
import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.collections.GrowableWordArray;
import com.oracle.svm.core.collections.GrowableWordArrayAccess;

import java.util.random.RandomGenerator;

import static org.junit.Assert.assertTrue;

public class TestGrowableWordArrayQuickSort {
    @Test
    public void test() throws Throwable {
        RandomGenerator randomGenerator = RandomGenerator.getDefault();
        GrowableWordArray gwa = StackValue.get(GrowableWordArray.class);
        GrowableWordArrayAccess.initialize(gwa);
        long nextLong = 0;
        for (int i = 0; i < 1000; i++) {
            // Occasionally insert duplicates
            if (i % 50 != 0) {
                nextLong = randomGenerator.nextLong();
            }
            GrowableWordArrayAccess.add(gwa, WordFactory.signed(nextLong), NmtCategory.JFR);
        }

        GrowableWordArrayAccess.qsort(gwa, 0, gwa.getSize() - 1, TestGrowableWordArrayQuickSort::compare);
        long last = GrowableWordArrayAccess.get(gwa, 0).rawValue();
        for (int i = 0; i < gwa.getSize(); i++) {
            long current = GrowableWordArrayAccess.get(gwa, i).rawValue();
            assertTrue(last <= current);
            last = current;
        }
    }

    static int compare(Word a, Word b) {
        return Long.compare(a.rawValue(), b.rawValue());
    }

}
