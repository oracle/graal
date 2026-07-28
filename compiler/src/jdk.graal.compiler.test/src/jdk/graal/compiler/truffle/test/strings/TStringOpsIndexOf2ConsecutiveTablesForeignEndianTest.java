/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.ArrayIndexOfNode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class TStringOpsIndexOf2ConsecutiveTablesForeignEndianTest extends TStringOpsTest<ArrayIndexOfNode> {

    private final Object[] constantArgs = new Object[7];

    @Parameters(name = "{index}: offset: {0}, length: {1}, stride: {2}, fromIndex: {3}, tableCase: {4}, case: {5}, sequence: {7}")
    public static List<Object[]> data() {
        return TStringOpsIndexOfConsecutiveTablesTestData.data(1, 2);
    }

    final byte[] arrayA;
    final long offsetA;
    final int lengthA;
    final int strideA;
    final int fromIndexA;
    final byte[] tables;

    public TStringOpsIndexOf2ConsecutiveTablesForeignEndianTest(int offsetA, int lengthA, int strideA, int fromIndexA,
                    TStringOpsIndexOfConsecutiveTablesTestData.TableCase tableCase,
                    TStringOpsIndexOfConsecutiveTablesTestData.CaseSpec caseSpec,
                    int sequenceIndex,
                    @SuppressWarnings("unused") String sequenceLabel) {
        super(ArrayIndexOfNode.class);
        this.arrayA = TStringOpsIndexOfConsecutiveTablesTestData.createArray(strideA, offsetA, lengthA, fromIndexA, caseSpec, tableCase.sequence(sequenceIndex));
        this.offsetA = offsetA + byteArrayBaseOffset();
        this.lengthA = lengthA;
        this.strideA = strideA;
        this.fromIndexA = fromIndexA;
        this.tables = tableCase.tables();
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        addConstantParameterBinding(conf, constantArgs);
        return super.editGraphBuilderConfiguration(conf);
    }

    private static final ThreadLocal<InstalledCode[]> cache = ThreadLocal.withInitial(() -> new InstalledCode[9]);

    @Override
    protected InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean ignoreForceCompile, boolean ignoreInstallAsDefault, OptionValues options) {
        return cacheInstalledCodeConstantStride(installedCodeOwner, graph, options, getIndexOf2ConsecutiveTablesForeignEndianIntl(), cache.get(), strideA, 0);
    }

    @Test
    public void testIndexOf2ConsecutiveTablesForeignEndian() {
        constantArgs[4] = strideA;
        testWithNative(getIndexOf2ConsecutiveTablesForeignEndianIntl(), null, DUMMY_LOCATION, byteSwapArray(arrayA, strideA), offsetA, lengthA, strideA, fromIndexA, tables);
    }

    @Override
    protected void checkIntrinsicNode(ArrayIndexOfNode node) {
        Assert.assertEquals(LIRGeneratorTool.ArrayIndexOfVariant.FindTwoConsecutiveTablesForeignEndian, node.getVariant());
        Assert.assertEquals(JavaKind.Long, node.stamp(NodeView.DEFAULT).getStackKind());
    }
}
