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

import com.oracle.graal.pointsto.phases.PointsToMethodHandlePlugin;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.junit.Test;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.List;

/**
 * This test verifies whether the invokeDynamic in handled by {@link PointsToMethodHandlePlugin}.
 * This test class must be compiled with VM specific 9+, so that the String concat operation will be
 * compiled into invokeDynamic.
 */
public class StandaloneInvokeDynamicTest {

    @Test
    public void test() throws ReflectiveOperationException {
        PointstoAnalyzerTester tester = new PointstoAnalyzerTester(StandaloneInvokeDynamicCase.class);
        tester.setAnalysisArguments(tester.getTestClassName(),
                        "-H:AnalysisTargetAppCP=" + tester.getTestClassJar());

        Class<?> stringConcatHelperClass = Class.forName("java.lang.StringConcatHelper");
        List<Executable> expectedReachableMethodList = new ArrayList<>();
        if (JavaVersionUtil.JAVA_SPEC < 17) {
            expectedReachableMethodList.add(stringConcatHelperClass.getDeclaredMethod("mixCoder", byte.class, String.class));
            expectedReachableMethodList.add(stringConcatHelperClass.getDeclaredMethod("mixLen", int.class, String.class));
            expectedReachableMethodList.add(stringConcatHelperClass.getDeclaredMethod("checkOverflow", int.class));
        }
        expectedReachableMethodList.add(stringConcatHelperClass.getDeclaredMethod("checkOverflow", long.class));
        tester.setExpectedReachableMethods(expectedReachableMethodList.toArray(new Executable[0]));
        tester.runAnalysisAndAssert();
    }
}
