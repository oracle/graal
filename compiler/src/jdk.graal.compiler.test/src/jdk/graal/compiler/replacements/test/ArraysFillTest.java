/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import java.util.Arrays;

import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.replacements.ConstantBindingParameterPlugin;
import jdk.graal.compiler.replacements.nodes.ArrayFillNode;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class ArraysFillTest extends ArraysSubstitutionsTestBase {
    private static final int[] LENGTHS = {0, 1, 2, 3, 4, 5, 7, 8, 9, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 31, 65, 127, 255, 1023, 1024, 1025};

    private void testFills(Class<?> type, Object constant) {
        Assume.assumeTrue((getTarget().arch instanceof AArch64));
        for (int length : LENGTHS) {
            testFillsSubstitution(new ArraysFillTestConfig(type, length, constant));
        }
    }

    @Test
    public void testFillBooleanFalse() {
        testFills(boolean.class, false);
    }

    @Test
    public void testFillBooleanTrue() {
        testFills(boolean.class, true);
    }

    @Test
    public void testFillCharA() {
        testFills(char.class, 'A');
    }

    @Test
    public void testFillChar0() {
        testFills(char.class, '0');
    }

    @Test
    public void testFillByteMin() {
        testFills(byte.class, Byte.MIN_VALUE);
    }

    @Test
    public void testFillByteMax() {
        testFills(byte.class, Byte.MAX_VALUE);
    }

    @Test
    public void testFillShortMin() {
        testFills(short.class, Short.MIN_VALUE);
    }

    @Test
    public void testFillShortMax() {
        testFills(short.class, Short.MAX_VALUE);
    }

    @Test
    public void testFillIntMin() {
        testFills(int.class, Integer.MIN_VALUE);
    }

    @Test
    public void testFillIntMax() {
        testFills(int.class, Integer.MAX_VALUE);
    }

    @Test
    public void testFillLongMin() {
        testFills(long.class, Long.MIN_VALUE);
    }

    @Test
    public void testFillLongMax() {
        testFills(long.class, Long.MAX_VALUE);
    }

    @Test
    public void testFillFloatMin() {
        testFills(float.class, Float.MIN_VALUE);
    }

    @Test
    public void testFillFloatMax() {
        testFills(float.class, Float.MAX_VALUE);
    }

    @Test
    public void testFillDoubleMin() {
        testFills(double.class, Double.MIN_VALUE);
    }

    @Test
    public void testFillDoubleMax() {
        testFills(double.class, Double.MAX_VALUE);
    }

    private Object[] constantArgs;

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        if (constantArgs != null) {
            ConstantBindingParameterPlugin constantBinding = new ConstantBindingParameterPlugin(constantArgs, this.getMetaAccess(), this.getSnippetReflection());
            conf.getPlugins().appendParameterPlugin(constantBinding);
        }
        return super.editGraphBuilderConfiguration(conf);
    }

    protected void testFillsSubstitution(ArraysFillTestConfig config) {
        ResolvedJavaMethod realMethod = getResolvedJavaMethod(Arrays.class, "fill", config.parameterType());
        ResolvedJavaMethod testMethod = getResolvedJavaMethod(config.testMethodName());
        StructuredGraph graph = testGraph(config.testMethodName());

        // Check to see if the resulting graph contains the expected node
        StructuredGraph replacement = getReplacements().getInlineSubstitution(realMethod, 0, false, Invoke.InlineControl.Normal, false, null, graph.allowAssumptions(), graph.getOptions());
        if (replacement == null) {
            assertInGraph(graph, ArrayFillNode.class);
        }

        // Force compilation
        InstalledCode code = getCode(testMethod, null, true);
        assert code != null;

        Object array1 = config.newArray();
        Object array2 = config.newArray();
        Object array3 = config.newArray();

        invokeSafe(realMethod, null, array1, config.getConstant());
        invokeSafe(testMethod, null, array2, config.getConstant());
        executeVarargsSafe(code, array3, config.getConstant());

        // Verify that the original method and the substitution produce the same value
        assertDeepEquals(array1, array2);

        // Verify that the generated code and the original produce the same value
        assertDeepEquals(array2, array3);
    }
}
