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

import static org.junit.runners.Parameterized.Parameters;

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

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class TStringOpsIndexOfAnyConstantTest extends TStringOpsIndexOfAnyTest {

    Object[] constantArgs;

    public TStringOpsIndexOfAnyConstantTest(byte[] arrayA, int offsetA, int lengthA, int strideA, int fromIndexA, int[] values) {
        super(arrayA, offsetA, lengthA, strideA, fromIndexA, values);
    }

    @Parameters(name = "{index}: offset: {1}, length: {2}, stride: {3}, fromIndex: {4}, toIndex: {5}")
    public static List<Object[]> data() {
        return TStringOpsIndexOfAnyTest.data().stream().filter(args -> {
            int length = (int) args[2];
            int fromIndex = (int) args[4];
            // this test takes much longer than TStringOpsIndexOfAnyTest, reduce number of test
            // cases
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
        // Force recompile if constant binding should be done
        return super.getCode(installedCodeOwner, graph, true, false, options);
    }

    @Override
    @Test
    public void testIndexOfAny() {
        constantArgs = new Object[7];
        constantArgs[0] = DUMMY_LOCATION;
        constantArgs[1] = arrayA;
        constantArgs[2] = offsetA;
        constantArgs[3] = lengthA;
        if (strideA == 0) {
            byte[] valuesB = new byte[values.length];
            for (int i = 0; i < values.length; i++) {
                valuesB[i] = (byte) values[i];
            }
            constantArgs[4] = fromIndexA;
            constantArgs[5] = valuesB;
            test(getIndexOfAnyByteIntl(), null, DUMMY_LOCATION, arrayA, offsetA, lengthA, fromIndexA, valuesB);
        }
        if (strideA < 2) {
            char[] valuesC = new char[values.length];
            for (int i = 0; i < values.length; i++) {
                valuesC[i] = (char) (strideA == 0 ? values[i] & 0xff : values[i]);
            }
            constantArgs[4] = strideA;
            constantArgs[5] = fromIndexA;
            constantArgs[6] = valuesC;
            test(getIndexOfAnyCharIntl(), null, DUMMY_LOCATION, arrayA, offsetA, lengthA, strideA, fromIndexA, valuesC);
        }
        int[] valuesI = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            valuesI[i] = strideA == 0 ? values[i] & 0xff : strideA == 1 ? values[i] & 0xffff : values[i];
        }
        constantArgs[4] = strideA;
        constantArgs[5] = fromIndexA;
        constantArgs[6] = valuesI;
        test(getIndexOfAnyIntIntl(), null, DUMMY_LOCATION, arrayA, offsetA, lengthA, strideA, fromIndexA, valuesI);
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
