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

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;

/**
 * This test verifies reading analysis entry points from file via -H:AnalysisEntryPointsFile.
 */
public class AnalysisEntryPointsFileTest {
    static class C {
        static {
            doC();
        }

        private static void doC() {
        }
    }

    public static void foo() {
        doFoo();
    }

    private static void doFoo() {
    }

    @SuppressWarnings("unused")
    public static void bar(String s) {
        doBar1();
    }

    private static void doBar1() {
    }

    @SuppressWarnings("unused")
    public static void bar(String s, int i) {
        doBar2();
    }

    private static void doBar2() {
    }

    public void foo1() {
        doFoo1();
    }

    private void doFoo1() {
    }

    @Test
    public void test() throws IOException, ReflectiveOperationException {
        PointstoAnalyzerTester tester = new PointstoAnalyzerTester(this.getClass());
        Path outPutDirectory = tester.createTestTmpDir();
        Path entryFilePath = tester.saveFileFromResource("/resources/entrypoints", outPutDirectory.resolve("entrypoints").normalize());
        assertNotNull("Fail to create entrypoints file.", entryFilePath);
        try {
            tester.setAnalysisArguments("-H:AnalysisEntryPointsFile=" + entryFilePath.toString(),
                            "-H:AnalysisTargetAppCP=" + tester.getTestClassJar());
            Class<?> classC = C.class;
            tester.setExpectedReachableMethods(classC.getDeclaredMethod("doC"),
                            tester.getTestClass().getDeclaredMethod("doFoo"),
                            tester.getTestClass().getDeclaredMethod("doFoo1"),
                            tester.getTestClass().getDeclaredMethod("doBar1"));
            tester.setExpectedUnreachableMethods(tester.getTestClass().getDeclaredMethod("doBar2"));
            tester.runAnalysisAndAssert();
        } finally {
            tester.deleteTestTmpDir();
        }
    }
}
