/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test.strings;

import java.util.List;
import java.util.stream.Collectors;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class TStringOpsIndexOfTwoConsecutiveConstantTest extends TStringOpsIndexOfTwoConsecutiveTest {

    final Object[] constantArgs;

    public TStringOpsIndexOfTwoConsecutiveConstantTest(byte[] arrayA, int offsetA, int lengthA, int strideA, int fromIndexA, int v0, int v1, int mask0, int mask1) {
        super(arrayA, offsetA, lengthA, strideA, fromIndexA, v0, v1, mask0, mask1);
        constantArgs = new Object[]{DUMMY_LOCATION, arrayA, offsetA, lengthA, strideA, fromIndexA, v0, v1, mask0, mask1};
    }

    @Parameters(name = "{index}: offset: {1}, length: {2}, stride: {3}, fromIndex: {4}, toIndex: {5}")
    public static List<Object[]> data() {
        return TStringOpsIndexOfTwoConsecutiveTest.data().stream().filter(args -> {
            int length = (int) args[2];
            int fromIndex = (int) args[4];
            // this test takes much longer than TStringOpsIndexOfTwoConsecutiveTest, reduce number
            // of test cases
            return length == 0 || length == 1 || length == 7 || length == 16 && fromIndex < 2;
        }).collect(Collectors.toList());
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        addConstantParameterBinding(conf, constantArgs);
        return super.editGraphBuilderConfiguration(conf);
    }

    @Override
    protected StructuredGraph parseForCompile(ResolvedJavaMethod method, CompilationIdentifier compilationId, OptionValues options) {
        return makeAllArraysStable(super.parseForCompile(method, compilationId, options));
    }

    @Override
    protected InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean ignoreForceCompile, boolean ignoreInstallAsDefault, OptionValues options) {
        return super.getCode(installedCodeOwner, graph, true, false, options);
    }

    @Override
    @Test
    public void testIndexOfTwoConsecutive() {
        constantArgs[6] = v0;
        constantArgs[7] = v1;
        test(getIndexOf2ConsecutiveWithStrideIntl(), null, DUMMY_LOCATION, arrayA, offsetA, lengthA, strideA, fromIndexA, v0, v1);
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        if (isSupportedArchitecture()) {
            if (arrayA.length < GraalOptions.StringIndexOfConstantLimit.getValue(graph.getOptions())) {
                assertConstantReturn(graph);
            }
        }
    }
}
