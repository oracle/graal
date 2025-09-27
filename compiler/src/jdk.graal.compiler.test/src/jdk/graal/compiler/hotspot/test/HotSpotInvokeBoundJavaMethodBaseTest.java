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

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Base class for tests involving calls to bound methods.
 */
public abstract class HotSpotInvokeBoundJavaMethodBaseTest extends HotSpotGraalCompilerTest {

    public static final JavaKind[] KINDS = {JavaKind.Boolean, JavaKind.Byte, JavaKind.Short, JavaKind.Char, JavaKind.Int, JavaKind.Long, JavaKind.Object};

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        for (JavaKind kind : KINDS) {
            String name = kind.isObject() ? "passingObject" : "passing" + capitalize(kind.getJavaName());
            Class<?> argType = kind.isObject() ? Object.class : kind.toJavaClass();
            invocationPlugins.register(HotSpotInvokeBoundJavaMethodBaseTest.class, new InvocationPlugin(name, argType) {
                @Override
                public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg) {
                    invocationPluginApply(b, targetMethod, receiver, arg, kind);
                    return true;
                }
            });
        }
        super.registerInvocationPlugins(invocationPlugins);
    }

    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    protected abstract boolean invocationPluginApply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg, JavaKind kind);

    static boolean[] booleanValues = new boolean[]{Boolean.TRUE, Boolean.FALSE};

    static boolean passingBoolean(boolean arg) {
        return arg;
    }

    public static boolean passingBooleanSnippet(boolean arg) {
        return passingBoolean(arg);
    }

    public void testPassingBoolean() {
        for (boolean value : booleanValues) {
            test("passingBooleanSnippet", value);
        }
    }

    static byte[] byteValues = new byte[]{Byte.MAX_VALUE, -1, 0, 1, Byte.MIN_VALUE};

    static byte passingByte(byte arg) {
        return arg;
    }

    public static byte passingByteSnippet(byte arg) {
        return passingByte(arg);
    }

    public void testPassingByte() {
        for (byte value : byteValues) {
            test("passingByteSnippet", value);
        }
    }

    static short[] shortValues = new short[]{Short.MAX_VALUE, -1, 0, 1, Short.MIN_VALUE};

    static short passingShort(short arg) {
        return arg;
    }

    public static short passingShortSnippet(short arg) {
        return passingShort(arg);
    }

    public void testPassingShort() {
        for (short value : shortValues) {
            test("passingShortSnippet", value);
        }
    }

    static char[] charValues = new char[]{Character.MAX_VALUE, 1, Character.MIN_VALUE};

    static char passingChar(char arg) {
        return arg;
    }

    public static char passingCharSnippet(char arg) {
        return passingChar(arg);
    }

    public void testPassingChar() {
        for (char value : charValues) {
            test("passingCharSnippet", value);
        }
    }

    static int[] intValues = new int[]{Integer.MAX_VALUE, -1, 0, 1, Integer.MIN_VALUE};

    static int passingInt(int arg) {
        return arg;
    }

    public static int passingIntSnippet(int arg) {
        return passingInt(arg);
    }

    public void testPassingInt() {
        for (int value : intValues) {
            test("passingIntSnippet", value);
        }
    }

    static long[] longValues = new long[]{Long.MAX_VALUE, -1, 0, 1, Long.MIN_VALUE};

    static long passingLong(long arg) {
        return arg;
    }

    public static long passingLongSnippet(long arg) {
        return passingLong(arg);
    }

    public void testPassingLong() {
        for (long value : longValues) {
            test("passingLongSnippet", value);
        }
    }

    static Object[] objectValues = new Object[]{null, "String", Integer.valueOf(-1)};

    static Object passingObject(Object arg) {
        return arg;
    }

    public static Object passingObjectSnippet(Object arg) {
        return passingObject(arg);
    }

    public void testPassingObject() {
        for (Object value : objectValues) {
            test("passingObjectSnippet", value);
        }
    }

    public void testMany() {
        testPassingObject();
        testPassingInt();
        testPassingByte();
        testPassingChar();
        testPassingLong();
        testPassingBoolean();
        testPassingShort();
    }
}
