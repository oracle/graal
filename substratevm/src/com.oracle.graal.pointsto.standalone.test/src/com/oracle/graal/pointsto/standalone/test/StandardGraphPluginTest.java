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

import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.replacements.StandardGraphBuilderPlugins;
import org.junit.Test;

import java.lang.reflect.Array;

/**
 * Method {@link StandardGraphBuilderPlugins#registerInvocationPlugins} has registered many
 * {@link InvocationPlugin}s. This class tests a typical one of them to check if the
 * {@code registerInvocationPlugins} method has been invoked for standalone analysis.
 */
public class StandardGraphPluginTest {

    /**
     * Check the invocation of method
     * {@code StandardGraphBuilderPlugins#registerArrayPlugins(InvocationPlugins, Replacements)}
     * which is invoked by {@link StandardGraphBuilderPlugins#registerInvocationPlugins}.
     * 
     * @throws NoSuchMethodException
     */
    @Test
    public void testArrayNewInstanceReachability() throws NoSuchMethodException {
        PointstoAnalyzerTester tester = new PointstoAnalyzerTester(ArrayNewInstancePluginCase.class);
        tester.setAnalysisArguments(tester.getTestClassName(),
                        "-H:AnalysisTargetAppCP=" + tester.getTestClassJar());
        tester.setExpectedReachableMethods(ArrayNewInstancePluginCase.C.class.getDeclaredMethod("foo"));
        tester.setExpectedUnreachableMethods(Array.class.getDeclaredMethod("newArray", Class.class, int.class));
        tester.runAnalysisAndAssert();
    }
}
