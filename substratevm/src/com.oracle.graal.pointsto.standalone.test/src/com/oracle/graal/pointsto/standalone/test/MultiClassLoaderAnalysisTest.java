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

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.standalone.PointsToAnalyzer;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * This class tests analyzing same qualified name classes. Given multiple same qualified name
 * classes which are loaded by different classloaders at runtime as analysis targets, the standalone
 * pointsto should be able to correctly find out the expected method reachability.
 */
public class MultiClassLoaderAnalysisTest {

    private static final String TEST_CLASS = "com.oracle.graal.pointsto.standalone.test.MultiClassLoaderAnalysisCase";

    @Test
    public void test() throws IOException {

        PointstoAnalyzerTester tester = new PointstoAnalyzerTester();
        Path testTmpDir = tester.createTestTmpDir();
        try {
            // Save the test files from resource to temporary directories and build them with javac
            // API.
            Path p1 = tester.saveFileFromResource("/resources/source1/MultiClassLoaderAnalysisCase",
                            testTmpDir.resolve("source1/MultiClassLoaderAnalysisCase.java").normalize());
            Path p2 = tester.saveFileFromResource("/resources/source2/MultiClassLoaderAnalysisCase",
                            testTmpDir.resolve("source2/MultiClassLoaderAnalysisCase.java").normalize());
            assertNotNull(p1);
            assertNotNull(p2);

            Path tempBin1 = testTmpDir.resolve("bin1");
            Path tempBin2 = testTmpDir.resolve("bin2");

            ToolProvider javac = ToolProvider.findFirst("javac").get();
            javac.run(System.out, System.err, "-d", tempBin1.toString(), p1.toAbsolutePath().toString());
            javac.run(System.out, System.err, "-d", tempBin2.toString(), p2.toAbsolutePath().toString());

            // We need to append classpath url which is generated at testing time, so can't prepare
            // a file beforehand.
            StringBuilder entryContents = new StringBuilder();
            entryContents.append(TEST_CLASS + ".foo1:").append(tempBin1).append("\n");
            // Add a non-existed method deliberately.
            entryContents.append(TEST_CLASS + ".foo3:").append(tempBin1).append("\n");
            entryContents.append(TEST_CLASS + ".foo2:").append(tempBin2);
            // Write the entry file to the temporary directory
            Path entryPointFilePath = testTmpDir.resolve("MultiClassLoaderAnalysisTest");
            try (FileWriter fileWriter = new FileWriter(entryPointFilePath.toFile())) {
                fileWriter.write(entryContents.toString());
                fileWriter.flush();
            } catch (IOException e) {
                throw new RuntimeException("Fail to write data to entrypoints file", e);
            }
            tester.setAnalysisArguments(
                            "-H:AnalysisTargetAppCP=" + tempBin1.normalize().toString() + File.pathSeparator + tempBin2.normalize().toString(),
                            "-H:AnalysisEntryPointsFile=" + entryPointFilePath.normalize().toAbsolutePath());
            PointsToAnalyzer pointsToAnalyzer = tester.runAnalysis(true);
            AnalysisUniverse universe = pointsToAnalyzer.getResultUniverse();
            // Get two MultiClassLoaderAnalysisCase classes from analysis result
            List<AnalysisType> results = universe.getTypes().stream().filter(type -> type.toJavaName(true).equals(TEST_CLASS)).collect(Collectors.toList());
            assertEquals(2, results.size());

            // Check if the methods are reached as expected
            for (AnalysisType result : results) {
                AnalysisMethod m = null;
                for (AnalysisMethod declaredMethod : result.getDeclaredMethods(false)) {
                    String methodName = declaredMethod.getName();
                    if (methodName.equals("bar1") || methodName.equals("bar2")) {
                        m = declaredMethod;
                        break;
                    }
                }
                assertNotNull(m);
                assertTrue(m.isReachable());
            }
        } finally {
            tester.deleteTestTmpDir();
        }
    }
}
