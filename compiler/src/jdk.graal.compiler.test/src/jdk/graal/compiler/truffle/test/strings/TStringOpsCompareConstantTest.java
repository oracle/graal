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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class TStringOpsCompareConstantTest extends TStringOpsCompareTest {

    @Parameters(name = "{index}: offset: {1}, {6}, stride: {3}, {8}, length: {12}")
    public static List<Object[]> data() {
        return TStringOpsConstantTest.reduceTestData(TStringOpsCompareTest.data(), 6, 7);
    }

    final Object[] constantArgs;

    public TStringOpsCompareConstantTest(
                    byte[] arrayA, int offsetA, int strideA,
                    byte[] arrayB, int offsetB, int strideB, int lengthCMP) {
        super(arrayA, offsetA, strideA, arrayB, offsetB, strideB, lengthCMP);
        constantArgs = new Object[]{DUMMY_LOCATION,
                        arrayA, this.offsetA, strideA,
                        arrayB, this.offsetB, strideB, lengthCMP};
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
    public void testMemCmp() {
        test(getMemcmpWithStrideIntl(), null, DUMMY_LOCATION,
                        arrayA, offsetA, strideA,
                        arrayB, offsetB, strideB, lengthCMP);
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        if (isSupportedArchitecture()) {
            if ((lengthCMP << Math.max(strideA, strideB)) < GraalOptions.ArrayRegionEqualsConstantLimit.getValue(graph.getOptions())) {
                assertConstantReturn(graph);
            }
        }
    }
}
