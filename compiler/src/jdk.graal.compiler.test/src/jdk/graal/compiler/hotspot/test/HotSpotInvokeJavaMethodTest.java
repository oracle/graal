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

import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallDescriptor;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.TestForeignCalls;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HotSpotInvokeJavaMethodTest extends HotSpotGraalCompilerTest {

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        for (JavaKind kind : TestForeignCalls.KINDS) {
            HotSpotForeignCallDescriptor desc = TestForeignCalls.createStubCallDescriptor(kind);
            String name = desc.getName();
            Class<?> argType = desc.getSignature().getArgumentTypes()[0];
            invocationPlugins.register(HotSpotInvokeJavaMethodTest.class, new InvocationPlugin(name, argType) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg) {
                    ForeignCallNode node = new ForeignCallNode(desc, arg);
                    b.addPush(kind, node);
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

    static boolean[] booleanValues = new boolean[]{Boolean.TRUE, Boolean.FALSE};

    static boolean booleanReturnsBoolean(boolean arg) {
        return arg;
    }

    public static boolean booleanReturnsBooleanSnippet(boolean arg) {
        return booleanReturnsBoolean(arg);
    }

    @Test
    public void testBooleanReturnsBoolean() {
        for (boolean value : booleanValues) {
            test("booleanReturnsBooleanSnippet", value);
        }
    }

    static byte[] byteValues = new byte[]{Byte.MAX_VALUE, -1, 0, 1, Byte.MIN_VALUE};

    static byte byteReturnsByte(byte arg) {
        return arg;
    }

    public static byte byteReturnsByteSnippet(byte arg) {
        return byteReturnsByte(arg);
    }

    @Test
    public void testByteReturnsByte() {
        for (byte value : byteValues) {
            test("byteReturnsByteSnippet", value);
        }
    }

    static short[] shortValues = new short[]{Short.MAX_VALUE, -1, 0, 1, Short.MIN_VALUE};

    static short shortReturnsShort(short arg) {
        return arg;
    }

    public static short shortReturnsShortSnippet(short arg) {
        return shortReturnsShort(arg);
    }

    @Test
    public void testShortReturnsShort() {
        for (short value : shortValues) {
            test("shortReturnsShortSnippet", value);
        }
    }

    static char[] charValues = new char[]{Character.MAX_VALUE, 1, Character.MIN_VALUE};

    static char charReturnsChar(char arg) {
        return arg;
    }

    public static char charReturnsCharSnippet(char arg) {
        return charReturnsChar(arg);
    }

    @Test
    public void testCharReturnsChar() {
        for (char value : charValues) {
            test("charReturnsCharSnippet", value);
        }
    }

    static int[] intValues = new int[]{Integer.MAX_VALUE, -1, 0, 1, Integer.MIN_VALUE};

    static int intReturnsInt(int arg) {
        return arg;
    }

    public static int intReturnsIntSnippet(int arg) {
        return intReturnsInt(arg);
    }

    @Test
    public void testIntReturnsInt() {
        for (int value : intValues) {
            test("intReturnsIntSnippet", value);
        }
    }

    static long[] longValues = new long[]{Long.MAX_VALUE, -1, 0, 1, Long.MIN_VALUE};

    static long longReturnsLong(long arg) {
        return arg;
    }

    public static long longReturnsLongSnippet(long arg) {
        return longReturnsLong(arg);
    }

    @Test
    public void testLongReturnsLong() {
        for (long value : longValues) {
            test("longReturnsLongSnippet", value);
        }
    }

    static float[] floatValues = new float[]{Float.MAX_VALUE, -1, 0, 1, Float.MIN_VALUE};

    static float floatReturnsFloat(float arg) {
        return arg;
    }

    public static float floatReturnsFloatSnippet(float arg) {
        return floatReturnsFloat(arg);
    }

    static Object[] objectValues = new Object[]{null, "String", Integer.valueOf(-1)};

    static Object objectReturnsObject(Object arg) {
        return arg;
    }

    public static Object objectReturnsObjectSnippet(Object arg) {
        return objectReturnsObject(arg);
    }

    @Test
    public void testObjectReturnsObject() {
        for (Object value : objectValues) {
            test("objectReturnsObjectSnippet", value);
        }
    }
}
