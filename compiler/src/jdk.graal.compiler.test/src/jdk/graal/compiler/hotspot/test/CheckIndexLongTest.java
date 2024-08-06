/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import jdk.graal.compiler.replacements.test.MethodSubstitutionTest;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CheckIndexLongTest extends MethodSubstitutionTest {

    static ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder()).putInt(0, 42).putInt(1, 43);
    static byte[] array = buffer.array();
    static long arrayLength = 16;
    static long negativeLength = -1;
    static long someIndex = 4;

    private boolean withExceptions;

    public static int objectsCheckIndex() {
        return array[(int) Objects.checkIndex(someIndex, array.length)];
    }

    public static int objectsCheckIndex0() {
        return array[(int) Objects.checkIndex(0L, array.length)];
    }

    public static int objectsCheckIndex1() {
        return array[(int) Objects.checkIndex(1L, array.length)];
    }

    public static int objectsCheckIndexLoop() {
        int sum = 0;
        for (long i = 0; i < arrayLength; i++) {
            sum += array[(int) Objects.checkIndex(i, array.length)];
        }
        return sum;
    }

    public static int objectsCheckIndexLoopOverLength() {
        int sum = 0;
        for (long i = 0; i < array.length; i++) {
            sum += array[(int) Objects.checkIndex(i, array.length)];
        }
        return sum;
    }

    public static long objectsCheckIndexNonArrayLength() {
        long index = Objects.checkIndex(someIndex, arrayLength);
        if (index >= 0 && index < arrayLength) {
            return index;
        } else {
            return -1;
        }
    }

    public static long objectsCheckIndexNonArrayLength1() {
        long index = Objects.checkIndex(1L, arrayLength);
        if (index >= 0 && index < arrayLength) {
            return index;
        } else {
            return -1;
        }
    }

    public static long objectsCheckIndexConstant() {
        return Objects.checkIndex(1L, 2L);
    }

    @Test
    public void testObjectsCheckIndex() {
        test("objectsCheckIndex");
        testGraph("objectsCheckIndex");
        test("objectsCheckIndex0");
        testGraph("objectsCheckIndex0");
        test("objectsCheckIndex1");
        testGraph("objectsCheckIndex1");

        test("objectsCheckIndexLoop");
        testGraph("objectsCheckIndexLoop");
        test("objectsCheckIndexLoopOverLength");
        testGraph("objectsCheckIndexLoopOverLength");

        test("objectsCheckIndexNonArrayLength");
        testGraph("objectsCheckIndexNonArrayLength");
        test("objectsCheckIndexNonArrayLength1");
        testGraph("objectsCheckIndexNonArrayLength1");

        test("objectsCheckIndexConstant");
        testGraph("objectsCheckIndexConstant");
    }

    public static int checkIndexOOB() {
        return array[(int) Objects.checkIndex(16L, array.length)];
    }

    public static int checkIndexNegativeLength() {
        return array[(int) Objects.checkIndex(1L, negativeLength)];
    }

    @Test
    public void testCheckIndexOOB() {
        test("checkIndexOOB");
        testGraph("checkIndexOOB", false);

        withExceptions = true;
        test("checkIndexOOB");
        testGraph("checkIndexOOB", true);
    }

    @Test
    public void testCheckIndexNegativeLength() {
        test("checkIndexNegativeLength");
        testGraph("checkIndexNegativeLength", false);

        withExceptions = true;
        test("checkIndexNegativeLength");
        testGraph("checkIndexNegativeLength", true);
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        return super.editGraphBuilderConfiguration(conf).withBytecodeExceptionMode(withExceptions ? BytecodeExceptionMode.CheckAll : BytecodeExceptionMode.OmitAll);
    }

    @Override
    protected OptimisticOptimizations getOptimisticOptimizations() {
        return OptimisticOptimizations.NONE;
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        super.registerInvocationPlugins(invocationPlugins);

        // Cut off the Objects.requireNonNull exception path: might generate an unwanted invoke.
        final InvocationPlugin requireNonNullPlugin = new InvocationPlugin("requireNonNull", Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode obj) {
                b.addPush(JavaKind.Object, b.addNonNullCast(obj));
                return true;
            }
        };
        final Registration objects = new Registration(invocationPlugins, Objects.class);
        objects.register(requireNonNullPlugin);
    }

    @Override
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        // Ensure non-intrinsified Unsafe methods are inlined.
        if (method.getDeclaringClass().getUnqualifiedName().equals("Unsafe")) {
            return InlineInvokePlugin.InlineInfo.createStandardInlineInfo(method);
        }
        return super.bytecodeParserShouldInlineInvoke(b, method, args);
    }
}
