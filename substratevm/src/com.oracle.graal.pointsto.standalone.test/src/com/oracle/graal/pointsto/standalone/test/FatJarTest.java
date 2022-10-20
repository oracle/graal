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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.spi.ToolProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class FatJarTest {
    private static final String TEST_CLASS = "com.oracle.graal.pointsto.standalone.test.FatJarCase";
    private static PointstoAnalyzerTester tester;
    private static Path fatJar;
    private static Path entryPointFilePath;

    @BeforeClass
    public static void prepareTest() throws IOException {
        tester = new PointstoAnalyzerTester();
        Path outPutDirectory = tester.createTestTmpDir();

        Path p = tester.saveFileFromResource("/resources/fatjar/FatJarCase",
                        outPutDirectory.resolve("source/FatJarCase.java").normalize());
        assertNotNull(p);

        Path tempBin = outPutDirectory.resolve("bin");

        // Make a fatjar in tempBIn
        ToolProvider javac = ToolProvider.findFirst("javac").get();
        javac.run(System.out, System.err, "-d", tempBin.toString(), p.toAbsolutePath().toString());

        Path tempLib = outPutDirectory.resolve("lib");
        if (Files.notExists(tempLib)) {
            Files.createDirectory(tempLib);
        }
        Path insideJar = tempLib.resolve("test.jar");
        ToolProvider jar = ToolProvider.findFirst("jar").get();
        int ret = jar.run(System.out, System.err, "-cf", insideJar.toString(), "-C", tempBin.toString(), ".");
        assertEquals("Fail to package the inside jar file of fatjar", 0, ret);
        fatJar = tempBin.resolve("fatjar.jar");
        ret = jar.run(System.out, System.err, "-cf", fatJar.toString(), "-C", outPutDirectory.toString(), "lib");
        assertEquals("Fail to package the fatjar file", 0, ret);

        // Prepare entrypoint file
        StringBuilder entryContents = new StringBuilder();
        entryContents.append(TEST_CLASS + ".foo");
        entryPointFilePath = outPutDirectory.resolve("FatJarCase");
        try (FileWriter fileWriter = new FileWriter(entryPointFilePath.toFile())) {
            fileWriter.write(entryContents.toString());
            fileWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException("Fail to write data to entrypoints file", e);
        }
    }

    @AfterClass
    public static void after() throws IOException {
        tester.deleteTestTmpDir();
    }

    public static void runTest() throws ClassNotFoundException, NoSuchMethodException {
        tester.runAnalysis(true);
        Class<?> fatjarTestClass = Class.forName(TEST_CLASS, false, tester.getAnalysisClassLoader());
        tester.setExpectedReachableMethods(fatjarTestClass.getDeclaredMethod("foo"),
                        fatjarTestClass.getDeclaredMethod("bar"));
        tester.runAsserts();
    }

    @Test
    public void testFatJar() throws ClassNotFoundException, NoSuchMethodException {
        tester.setAnalysisArguments(
                        "-H:AnalysisTargetAppCP=" + fatJar.toString(),
                        "-H:AnalysisEntryPointsFile=" + entryPointFilePath.normalize().toAbsolutePath());
        runTest();
    }

    @Test
    public void testJarInFatJar() throws ClassNotFoundException, NoSuchMethodException {
        tester.setAnalysisArguments(
                        "-H:AnalysisTargetAppCP=" + fatJar.toString() + "!/lib/test.jar",
                        "-H:AnalysisEntryPointsFile=" + entryPointFilePath.normalize().toAbsolutePath());
        runTest();
    }
}
