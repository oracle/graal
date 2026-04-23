/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.test;

import org.junit.Test;

import com.oracle.graal.pointsto.standalone.test.classes.AnalysisEntryPointsFileCase;
import com.oracle.graal.pointsto.standalone.test.classes.AnalysisEntryPointsFileCase.C;

/**
 * This test verifies reading analysis entry points from file via -H:AnalysisEntryPointsFile.
 */
public class AnalysisEntryPointsFileTest extends StandaloneAnalysisTest {
    /**
     * Verifies that an entry-points file can select specific roots from
     * {@link AnalysisEntryPointsFileCase} and that standalone analysis honors exactly those
     * entries.
     *
     * This checks both setup and intent: the bundled entry-points resource must be resolved
     * directly from the test resources, consumed successfully, and then drive reachability so that
     * {@link C#doC()}, {@link AnalysisEntryPointsFileCase#doFoo()},
     * {@link AnalysisEntryPointsFileCase#doFoo1()}, and
     * {@link AnalysisEntryPointsFileCase#doBar1()} are reachable while
     * {@link AnalysisEntryPointsFileCase#doBar2()} stays unreachable.
     */
    @Test
    public void test() {
        runAnalysisWithEntryPointsFile(AnalysisEntryPointsFileCase.class, "/resources/entrypoints");
        Class<?> classC = AnalysisEntryPointsFileCase.C.class;
        assertReachable(findMethod(classC, "doC"));
        assertReachable(findMethod(AnalysisEntryPointsFileCase.class, "doFoo"));
        assertReachable(findMethod(AnalysisEntryPointsFileCase.class, "doFoo1"));
        assertReachable(findMethod(AnalysisEntryPointsFileCase.class, "doBar1"));
        assertNotReachable(findMethod(AnalysisEntryPointsFileCase.class, "doBar2"));
    }

    /**
     * Verifies that the direct method-entry API can select the single-argument
     * {@link AnalysisEntryPointsFileCase#bar(String)} overload without going through an
     * entry-points file.
     *
     * This checks the new in-process analyzer path specifically:
     * {@link AnalysisEntryPointsFileCase#doBar1()} should become reachable, while
     * {@link AnalysisEntryPointsFileCase#doBar2()} should stay unreachable.
     */
    @Test
    public void testDirectMethodEntry() {
        runAnalysisMethod(AnalysisEntryPointsFileCase.class, "bar", String.class);
        assertReachable(findMethod(AnalysisEntryPointsFileCase.class, "doBar1"));
        assertNotReachable(findMethod(AnalysisEntryPointsFileCase.class, "doBar2"));
    }
}
