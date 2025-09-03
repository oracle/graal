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
package jdk.graal.compiler.hotspot.test;

import static jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates.findMethod;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.TestForeignCalls;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HotSpotInvokeJavaMethodTest extends HotSpotGraalCompilerTest {

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        for (JavaKind kind : TestForeignCalls.KINDS) {
            HotSpotForeignCallDescriptor desc = TestForeignCalls.createStubCallDescriptor(kind);
            String name = desc.getName();
            Class<?> argType = desc.getSignature().getArgumentTypes()[1];
            invocationPlugins.register(HotSpotInvokeJavaMethodTest.class, new InvocationPlugin(name, argType) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg) {
                    ResolvedJavaMethod javaMethod = findMethod(b.getMetaAccess(), HotSpotInvokeJavaMethodTest.class, desc.getName());
                    ValueNode method = ConstantNode.forConstant(b.getStampProvider().createMethodStamp(), javaMethod.getEncoding(), b.getMetaAccess(), b.getGraph());
                    ForeignCallNode node = new ForeignCallNode(desc, method, arg);
                    b.add(node);
                    return true;
                }
            });
        }
        super.registerInvocationPlugins(invocationPlugins);
    }

    @Before
    public void before() {
        Assume.assumeTrue("Invoke stub helper is missing", runtime().getVMConfig().invokeJavaMethodAddress != 0);
    }

    static Object passedArg;

    static boolean[] booleanValues = new boolean[]{Boolean.TRUE, Boolean.FALSE};

    static void passingBoolean(boolean arg) {
        passedArg = arg;
    }

    public static boolean passingBooleanSnippet(boolean arg) {
        passedArg = null;
        passingBoolean(arg);
        return (boolean) passedArg;
    }

    @Test
    public void testPassingBoolean() {
        for (boolean value : booleanValues) {
            test("passingBooleanSnippet", value);
        }
    }

    static byte[] byteValues = new byte[]{Byte.MAX_VALUE, -1, 0, 1, Byte.MIN_VALUE};

    static void passingByte(byte arg) {
        passedArg = arg;
    }

    public static void passingByteSnippet(byte arg) {
        passedArg = null;
        passingByte(arg);
    }

    @Test
    public void testPassingByte() {
        for (byte value : byteValues) {
            test("passingByteSnippet", value);
        }
    }

    static short[] shortValues = new short[]{Short.MAX_VALUE, -1, 0, 1, Short.MIN_VALUE};

    static void passingShort(short arg) {
        passedArg = arg;
    }

    public static short passingShortSnippet(short arg) {
        passedArg = null;
        passingShort(arg);
        return (short) passedArg;
    }

    @Test
    public void testPassingShort() {
        for (short value : shortValues) {
            test("passingShortSnippet", value);
        }
    }

    static char[] charValues = new char[]{Character.MAX_VALUE, 1, Character.MIN_VALUE};

    static void passingChar(char arg) {
        passedArg = arg;
    }

    public static char passingCharSnippet(char arg) {
        passedArg = null;
        passingChar(arg);
        return (char) passedArg;
    }

    @Test
    public void testPassingChar() {
        for (char value : charValues) {
            test("passingCharSnippet", value);
        }
    }

    static int[] intValues = new int[]{Integer.MAX_VALUE, -1, 0, 1, Integer.MIN_VALUE};

    static void passingInt(int arg) {
        passedArg = arg;
    }

    public static int passingIntSnippet(int arg) {
        passedArg = null;
        passingInt(arg);
        return (int) passedArg;
    }

    @Test
    public void testPassingInt() {
        for (int value : intValues) {
            test("passingIntSnippet", value);
        }
    }

    static long[] longValues = new long[]{Long.MAX_VALUE, -1, 0, 1, Long.MIN_VALUE};

    static void passingLong(long arg) {
        passedArg = arg;
    }

    public static long passingLongSnippet(long arg) {
        passedArg = null;
        passingLong(arg);
        return (long) passedArg;
    }

    @Test
    public void testPassingLong() {
        for (long value : longValues) {
            test("passingLongSnippet", value);
        }
    }

    static Object[] objectValues = new Object[]{null, "String", Integer.valueOf(-1)};

    static void passingObject(Object arg) {
        passedArg = arg;
    }

    public static Object passingObjectSnippet(Object arg) {
        passedArg = null;
        passingObject(arg);
        return passedArg;
    }

    @Test
    public void testPassingObject() {
        for (Object value : objectValues) {
            test("passingObjectSnippet", value);
        }
    }
}
