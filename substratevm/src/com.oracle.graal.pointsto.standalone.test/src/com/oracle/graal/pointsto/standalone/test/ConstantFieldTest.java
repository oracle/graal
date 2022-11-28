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

/**
 * This test can test 2 facts for standalone pointsto analysis:
 * <ol>
 * <li>The clinit method is taken as analysis target instead of being executed to initialize the
 * class.</li>
 * <li>The static final field is not taken as final in the analysis, i.e. it is not considered as
 * constant at analysis time.</li>
 * </ol>
 */
public class ConstantFieldTest {

    @Test
    public void testConstantField() throws NoSuchMethodException, NoSuchFieldException {
        PointstoAnalyzerTester tester = new PointstoAnalyzerTester(ConstantFieldCase.class);
        tester.setAnalysisArguments(tester.getTestClassName(),
                        "-H:AnalysisTargetAppCP=" + tester.getTestClassJar());
        tester.setExpectedReachableMethods(ConstantFieldCase.ConstantType.class.getDeclaredMethod("foo"));
        tester.setExpectedReachableClinits(ConstantFieldCase.class);
        tester.setExpectedReachableFields(ConstantFieldCase.class.getDeclaredField("constantField"));
        tester.runAnalysisAndAssert();
    }
}
