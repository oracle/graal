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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Path;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.oracle.graal.pointsto.standalone.test.classes.ClassEqualityCase;

/**
 * Verifies standalone analysis report emission using {@link ClassEqualityCase} as a small input.
 */
public class StandaloneAnalysisReportTest extends StandaloneAnalysisTest {
    /*
     * Takes an arbitrary small fixture because the report tests care about emitted files rather
     * than about any fixture-specific reachability nuance.
     */
    private static final Class<?> TEST_CLASS = ClassEqualityCase.class;

    /**
     * Owns report-output directories for this test class so each report scenario gets an isolated
     * temporary root without relying on the shared harness temp-directory bookkeeping.
     */
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    /**
     * Verifies that analyzing {@link ClassEqualityCase} with report generation enabled writes at
     * least one call-tree report file.
     *
     * This checks the standalone report plumbing rather than a specific reachability fact: the
     * analysis must honor the configured reports path, create the reports directory, and emit
     * output into it.
     */
    @Test
    public void testPrintAnalysisCallTree() {
        Path testTmpDir = temporaryFolder.getRoot().toPath();
        runAnalysis(TEST_CLASS,
                        "-H:ReportsPath=" + testTmpDir,
                        "-H:+PrintAnalysisCallTree");
        File reportDir = testTmpDir.resolve("reports").toFile();
        assertTrue(reportDir.isDirectory());
        File[] reportFiles = reportDir.listFiles();
        assertTrue(reportFiles.length > 0);
    }

    /**
     * Verifies object-tree report generation for {@link ClassEqualityCase} when that report becomes
     * meaningful for the fixture.
     *
     * The test is currently ignored because the analyzed code does not produce a useful object tree
     * yet, but the expected setup and output checks remain documented here for future re-enabling.
     */
    @Test
    @Ignore("There is no class initialization yet, so object-tree reporting is not meaningful for this fixture.")
    public void testPrintAnalysisObjectTree() {
        Path testTmpDir = temporaryFolder.getRoot().toPath();
        runAnalysis(TEST_CLASS,
                        "-H:ReportsPath=" + testTmpDir,
                        "-H:+PrintImageObjectTree");
        File reportDir = testTmpDir.resolve("reports").toFile();
        assertTrue(reportDir.isDirectory());
        File[] reportFiles = reportDir.listFiles();
        assertTrue(reportFiles.length > 0);
    }
}
