/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.lir.amd64.AMD64ArrayEqualsOp;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.ArrayRegionEqualsNode;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class TStringOpsRegionEqualsConstantLengthTest extends TStringOpsRegionEqualsTest {

    static final int[] lengthFilter = {1, 7, 16, 31};

    final Object[] constantArgs = new Object[13];

    public TStringOpsRegionEqualsConstantLengthTest(
                    byte[] arrayA, int offsetA, int lengthA, int strideA, int fromIndexA,
                    byte[] arrayB, int offsetB, int lengthB, int strideB, int fromIndexB, int lengthCMP) {
        super(arrayA, offsetA, lengthA, strideA, fromIndexA, arrayB, offsetB, lengthB, strideB, fromIndexB, lengthCMP);
    }

    @Parameters(name = "{index}: offset: {1}, {6}, stride: {3}, {8}, length: {10}")
    public static List<Object[]> data() {
        return TStringOpsRegionEqualsTest.data().stream().filter(args -> {
            int length = (int) args[10];
            // this test takes much longer than TStringOpsRegionEqualsTest, reduce number of test
            // cases
            return contains(lengthFilter, length);
        }).collect(Collectors.toList());
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        addConstantParameterBinding(conf, constantArgs);
        return super.editGraphBuilderConfiguration(conf);
    }

    private static final ThreadLocal<InstalledCode[]> cache = ThreadLocal.withInitial(() -> new InstalledCode[9 * lengthFilter.length]);

    @Override
    protected InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean ignoreForceCompile, boolean ignoreInstallAsDefault, OptionValues options) {
        return cacheInstalledCodeConstantStrideLength(installedCodeOwner, graph, options, getRegionEqualsWithOrMaskWithStrideIntl(), cache.get(), strideA, strideB, indexOf(lengthFilter, lengthCMP));
    }

    @Override
    @Test
    public void testRegionEquals() {
        Assume.assumeTrue(getTarget().arch instanceof AMD64);
        Assume.assumeTrue(AMD64ArrayEqualsOp.canGenerateConstantLengthCompare(getTarget(), null, JavaKind.Byte, Stride.fromLog2(strideA), Stride.fromLog2(strideB), lengthCMP, getMaxVectorSize()));
        constantArgs[4] = strideA;
        constantArgs[9] = strideB;
        constantArgs[12] = lengthCMP;
        testWithNative(getRegionEqualsWithOrMaskWithStrideIntl(), null, DUMMY_LOCATION,
                        arrayA, offsetA, lengthA, strideA, fromIndexA,
                        arrayB, offsetB, lengthB, strideB, fromIndexB, null, lengthCMP);
    }

    @Override
    protected void checkIntrinsicNode(ArrayRegionEqualsNode node) {
        Assert.assertTrue(node.getDirectStubCallIndex() >= 0);
        ValueNode stride = node.getDynamicStrides();
        Assert.assertTrue(stride == null || stride.isJavaConstant());
        Assert.assertTrue(node.getLength().isJavaConstant());
    }

    @Override
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (method.getName().equals("stubStride")) {
            return InlineInvokePlugin.InlineInfo.createStandardInlineInfo(method);
        }
        return super.bytecodeParserShouldInlineInvoke(b, method, args);
    }
}
