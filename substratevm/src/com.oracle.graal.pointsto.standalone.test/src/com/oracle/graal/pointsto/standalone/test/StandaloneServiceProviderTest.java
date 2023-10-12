/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Alibaba Group Holding Limited. All rights reserved.
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

import java.nio.file.Files;

import org.junit.Test;

public class StandaloneServiceProviderTest {
    public static void main(String[] args) {
        Files.exists(null);
    }

    @Test
    public void testDisableServiceLoader() throws ClassNotFoundException {
        PointstoAnalyzerTester tester = new PointstoAnalyzerTester(this.getClass());
        tester.setAnalysisArguments(tester.getTestClassName(),
                        "-H:-AnalysisRegisterServices",
                        "-H:AnalysisTargetAppCP=" + tester.getTestClassJar());
        tester.setExpectedReachableTypes(Class.forName("java.nio.file.Files"),
                        Class.forName("java.nio.file.spi.FileSystemProvider"));
        tester.setExpectedUnreachableTypes(Class.forName("jdk.internal.jrtfs.JrtFileSystemProvider"));
        tester.runAnalysisAndAssert();
    }

    @Test
    public void testEnableServiceLoader() throws ClassNotFoundException {
        PointstoAnalyzerTester tester = new PointstoAnalyzerTester(this.getClass());
        tester.setAnalysisArguments(tester.getTestClassName(),
                        "-H:AnalysisTargetAppCP=" + tester.getTestClassJar());
        tester.setExpectedReachableTypes(Class.forName("java.nio.file.Files"),
                        Class.forName("java.nio.file.spi.FileSystemProvider"),
                        Class.forName("jdk.internal.jrtfs.JrtFileSystemProvider"));
        tester.runAnalysisAndAssert();
    }
}
