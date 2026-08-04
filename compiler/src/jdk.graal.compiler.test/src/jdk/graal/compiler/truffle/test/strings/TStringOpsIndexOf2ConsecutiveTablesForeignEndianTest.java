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
import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.ArrayIndexOfNode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class TStringOpsIndexOf2ConsecutiveTablesForeignEndianTest extends TStringOpsTest<ArrayIndexOfNode> {

    private Object[] constantArgs = new Object[7];
    private int strideA;

    public static List<Object[]> data() {
        return TStringOpsIndexOfConsecutiveTablesTestData.data(1, 2);
    }

    public TStringOpsIndexOf2ConsecutiveTablesForeignEndianTest() {
        super(ArrayIndexOfNode.class);
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
        testParameterized(data(), args -> {
            this.strideA = (int) args[2];
            testIndexOf2ConsecutiveTablesForeignEndianCase(args);
        });
    }

    private void testIndexOf2ConsecutiveTablesForeignEndianCase(Object[] args) {
        byte[] arrayA = TStringOpsIndexOfConsecutiveTablesTestData.createArray((int) args[2], (int) args[0], (int) args[1], (int) args[3],
                        (TStringOpsIndexOfConsecutiveTablesTestData.CaseSpec) args[5], ((TStringOpsIndexOfConsecutiveTablesTestData.TableCase) args[4]).sequence((int) args[6]));
        long offsetA = (int) args[0] + byteArrayBaseOffset();
        int lengthA = (int) args[1];
        int strideA = (int) args[2];
        int fromIndexA = (int) args[3];
        byte[] tables = ((TStringOpsIndexOfConsecutiveTablesTestData.TableCase) args[4]).tables();

        Assume.assumeTrue(ArrayIndexOfNode.isSupported(getArchitecture(), Stride.fromLog2(strideA), LIRGeneratorTool.ArrayIndexOfVariant.FindTwoConsecutiveTablesForeignEndian));
        constantArgs[4] = strideA;
        testWithNative(getIndexOf2ConsecutiveTablesForeignEndianIntl(), null, DUMMY_LOCATION, byteSwapArray(arrayA, strideA), offsetA, lengthA, strideA, fromIndexA, tables);
    }

    @Override
    protected void checkIntrinsicNode(ArrayIndexOfNode node) {
        Assert.assertEquals(LIRGeneratorTool.ArrayIndexOfVariant.FindTwoConsecutiveTablesForeignEndian, node.getVariant());
        Assert.assertEquals(JavaKind.Long, node.stamp(NodeView.DEFAULT).getStackKind());
    }
}
