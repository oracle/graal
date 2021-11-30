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

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertNotNull;

/**
 * This test verifies the UnsupportedFeatureException is thrown when the analysis target class has
 * compatible problem. There are two analysis target classes,
 * {@code ClassVersionIncompatibleMain.class} and {@code IncompatibleClass.class}. The former
 * depends on the latter. The former is compiled with JDK11 and the latter is compiled with JDK17.
 * The runtime JDK is below 17. In this case, there should be warning message reported.
 */
public class ClassVersionIncompatibleTest {
    @Test
    public void test() throws IOException {
        // This test is only available with JDK version less than 17
        if (JavaVersionUtil.JAVA_SPEC < 17) {
            PointstoAnalyzerTester tester = new PointstoAnalyzerTester(ClassVersionIncompatibleMain.class);
            Path testTmpDir = tester.createTestTmpDir();
            try {
                String testDir = "com/oracle/graal/pointsto/standalone/test";
                // Copy the analysis target classes to test temporary directory
                Path p1 = tester.saveFileFromResource("/resources/bin11/" + testDir + "/ClassVersionIncompatibleMain.class",
                                testTmpDir.resolve("bin/" + testDir + "/ClassVersionIncompatibleMain.class").normalize());
                Path p2 = tester.saveFileFromResource("/resources/bin17/" + testDir + "/IncompatibleClass.class",
                                testTmpDir.resolve("bin/" + testDir + "/IncompatibleClass.class").normalize());
                assertNotNull(p1);
                assertNotNull(p2);
                tester.setAnalysisArguments(tester.getTestClassName(),
                                "-H:AnalysisTargetAppCP=" + testTmpDir.resolve("bin").normalize().toString());
                tester.runAnalysisAndAssert(false);
            } finally {
                tester.deleteTestTmpDir();
            }
        }
    }
}
