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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.ArrayRegionEqualsNode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class TStringOpsRegionEqualsConstantStrideTest extends TStringOpsRegionEqualsTest {

    Object[] constantArgs = new Object[10];

    public TStringOpsRegionEqualsConstantStrideTest(
                    byte[] arrayA, int offsetA, int lengthA, int strideA, int fromIndexA,
                    byte[] arrayB, int offsetB, int lengthB, int strideB, int fromIndexB, int lengthCMP) {
        super(arrayA, offsetA, lengthA, strideA, fromIndexA, arrayB, offsetB, lengthB, strideB, fromIndexB, lengthCMP);
    }

    @Parameters(name = "{index}: offset: {1}, {6}, stride: {3}, {8}, length: {10}")
    public static List<Object[]> data() {
        return TStringOpsRegionEqualsTest.data();
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        addConstantParameterBinding(conf, constantArgs);
        return super.editGraphBuilderConfiguration(conf);
    }

    private static final ThreadLocal<InstalledCode[]> cache = ThreadLocal.withInitial(() -> new InstalledCode[9]);

    @Override
    protected InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean ignoreForceCompile, boolean ignoreInstallAsDefault, OptionValues options) {
        return cacheInstalledCodeConstantStride(installedCodeOwner, graph, options, getRegionEqualsWithOrMaskWithStrideIntl(), cache.get(), strideA, strideB);
    }

    @Override
    @Test
    public void testRegionEquals() {
        constantArgs[4] = strideA;
        constantArgs[9] = strideB;
        testWithNative(getRegionEqualsWithOrMaskWithStrideIntl(), null, DUMMY_LOCATION,
                        arrayA, offsetA, lengthA, strideA, fromIndexA,
                        arrayB, offsetB, lengthB, strideB, fromIndexB, null, lengthCMP);
    }

    @Override
    protected void checkIntrinsicNode(ArrayRegionEqualsNode node) {
        Assert.assertTrue(node.getDirectStubCallIndex() >= 0);
    }
}
