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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/**
 * This test verifies the functionality of invoking compatible SVM feature from standalone pointsto
 * analyzer. This test takes the com.oracle.svm.hosted.dashboard.DashboardDumpFeature as test case,
 * so requires svm.jar on the classpath.
 */
public class DelegateSVMFeatureTest {

    public static void main(String[] args) {

    }

    @Test
    public void test() throws IOException {
        PointstoAnalyzerTester tester = new PointstoAnalyzerTester(this.getClass());
        Path testTmpDir = tester.createTestTmpDir();
        try {
            Path outputPath = testTmpDir.resolve("reports");
            String dumpFileName = "dumpRet";
            tester.setAnalysisArguments(tester.getTestClassName(),
                            "-H:AnalysisTargetAppCP=" + tester.getTestClassJar(),
                            "-H:DumpDashboardPointsto=" + outputPath.resolve(dumpFileName).normalize().toString());
            tester.runAnalysisAndAssert();
            assertTrue(Files.exists(outputPath.resolve(dumpFileName + ".dump")));
        } finally {
            tester.deleteTestTmpDir();
        }
    }
}
