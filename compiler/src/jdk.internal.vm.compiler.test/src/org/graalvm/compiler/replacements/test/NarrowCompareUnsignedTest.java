/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.replacements.test;

import org.junit.Assume;
import org.junit.Test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AndNode;
import org.graalvm.compiler.nodes.calc.IntegerNormalizeCompareNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests for {@link IntegerNormalizeCompareNode} applied to subword integers.
 */
public class NarrowCompareUnsignedTest extends GraalCompilerTest {

    public static int narrowCompareUnsignedByte(int x, int y) {
        /*
         * This expression doesn't quite produce the graph shape we want: The narrows feed into sign
         * extensions that feed into the actual compare. We want to test the compare when applied to
         * the actual narrows. It's possible but fairly difficult to produce that graph shape in
         * practice. Therefore we intrinsify this method to get what we want.
         */
        return Integer.compareUnsigned((byte) x, (byte) y);
    }

    public static int narrowCompareUnsignedShort(int x, int y) {
        return Integer.compareUnsigned((short) x, (short) y);
    }

    public static int narrowCompareUnsignedChar(int x, int y) {
        return Integer.compareUnsigned((char) x, (char) y);
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(invocationPlugins, NarrowCompareUnsignedTest.class);
        r.register(new InvocationPlugin.InlineOnlyInvocationPlugin("narrowCompareUnsignedByte", int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                ValueNode narrowX = NarrowNode.create(x, Byte.SIZE, NodeView.DEFAULT);
                ValueNode narrowY = NarrowNode.create(y, Byte.SIZE, NodeView.DEFAULT);
                b.addPush(JavaKind.Int, IntegerNormalizeCompareNode.create(narrowX, narrowY, true, JavaKind.Int, b.getConstantReflection()));
                return true;
            }
        });
        r.register(new InvocationPlugin.InlineOnlyInvocationPlugin("narrowCompareUnsignedShort", int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                ValueNode narrowX = NarrowNode.create(x, Short.SIZE, NodeView.DEFAULT);
                ValueNode narrowY = NarrowNode.create(y, Short.SIZE, NodeView.DEFAULT);
                b.addPush(JavaKind.Int, IntegerNormalizeCompareNode.create(narrowX, narrowY, true, JavaKind.Int, b.getConstantReflection()));
                return true;
            }
        });
        r.register(new InvocationPlugin.InlineOnlyInvocationPlugin("narrowCompareUnsignedChar", int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode x, ValueNode y) {
                int unsignedMask = 0xffff;
                ValueNode maskedX = AndNode.create(x, ConstantNode.forInt(unsignedMask), NodeView.DEFAULT);
                ValueNode maskedY = AndNode.create(y, ConstantNode.forInt(unsignedMask), NodeView.DEFAULT);
                ValueNode narrowX = NarrowNode.create(maskedX, Character.SIZE, NodeView.DEFAULT);
                ValueNode narrowY = NarrowNode.create(maskedY, Character.SIZE, NodeView.DEFAULT);
                b.addPush(JavaKind.Int, IntegerNormalizeCompareNode.create(narrowX, narrowY, true, JavaKind.Int, b.getConstantReflection()));
                return true;
            }
        });
        super.registerInvocationPlugins(invocationPlugins);
    }

    private static final int[] TEST_VALUES = new int[]{0, 40, Byte.MAX_VALUE, Byte.MAX_VALUE + 40, Short.MAX_VALUE, Short.MAX_VALUE + 40, Character.MAX_VALUE, Character.MAX_VALUE + 40};

    public static int byteVariableSnippet(int x, int y) {
        return narrowCompareUnsignedByte(x, y);
    }

    @Test
    public void byteVariable() {
        Assume.assumeTrue("only test byte compare when supported on target", getLowerer().smallestCompareWidth() <= Byte.SIZE);
        for (int x : TEST_VALUES) {
            for (int y : TEST_VALUES) {
                test("byteVariableSnippet", x, y);
            }
        }
    }

    public static int byteConstantSnippet(int x) {
        return narrowCompareUnsignedByte(x, Byte.MAX_VALUE);
    }

    @Test
    public void byteConstant() {
        Assume.assumeTrue("only test byte compare when supported on target", getLowerer().smallestCompareWidth() <= Byte.SIZE);
        for (int x : TEST_VALUES) {
            test("byteConstantSnippet", x);
        }
    }

    public static int shortVariableSnippet(int x, int y) {
        return narrowCompareUnsignedShort(x, y);
    }

    @Test
    public void shortVariable() {
        Assume.assumeTrue("only test short compare when supported on target", getLowerer().smallestCompareWidth() <= Short.SIZE);
        for (int x : TEST_VALUES) {
            for (int y : TEST_VALUES) {
                test("shortVariableSnippet", x, y);
            }
        }
    }

    public static int shortConstantSnippet(int x) {
        return narrowCompareUnsignedShort(x, Short.MAX_VALUE);
    }

    @Test
    public void shortConstant() {
        Assume.assumeTrue("only test short compare when supported on target", getLowerer().smallestCompareWidth() <= Short.SIZE);
        for (int x : TEST_VALUES) {
            test("shortConstantSnippet", x);
        }
    }

    public static int charVariableSnippet(int x, int y) {
        return narrowCompareUnsignedChar(x, y);
    }

    @Test
    public void charVariable() {
        Assume.assumeTrue("only test char compare when supported on target", getLowerer().smallestCompareWidth() <= Character.SIZE);
        for (int x : TEST_VALUES) {
            for (int y : TEST_VALUES) {
                test("charVariableSnippet", x, y);
            }
        }
    }

    public static int charConstantSnippet(int x) {
        return narrowCompareUnsignedChar(x, Character.MAX_VALUE);
    }

    @Test
    public void charConstant() {
        Assume.assumeTrue("only test char compare when supported on target", getLowerer().smallestCompareWidth() <= Character.SIZE);
        for (int x : TEST_VALUES) {
            test("charConstantSnippet", x);
        }
    }
}
