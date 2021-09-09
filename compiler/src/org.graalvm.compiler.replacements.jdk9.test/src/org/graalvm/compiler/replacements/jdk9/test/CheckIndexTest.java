/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.jdk9.test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.replacements.test.MethodSubstitutionTest;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CheckIndexTest extends MethodSubstitutionTest {

    static ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder()).putInt(0, 42).putInt(1, 43);
    static byte[] array = buffer.array();
    static int arrayLength = 16;
    static int negativeLength = -1;
    static int someIndex = 4;

    static final VarHandle byteArrayIntView = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.nativeOrder());
    static final VarHandle byteBufferIntView = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.nativeOrder());
    static final VarHandle byteArrayByteView = MethodHandles.arrayElementVarHandle(byte[].class);

    private boolean withExceptions;

    public static int objectsCheckIndex() {
        return array[Objects.checkIndex(someIndex, array.length)];
    }

    public static int objectsCheckIndex0() {
        return array[Objects.checkIndex(0, array.length)];
    }

    public static int objectsCheckIndex1() {
        return array[Objects.checkIndex(1, array.length)];
    }

    public static int objectsCheckIndexLoop() {
        int sum = 0;
        for (int i = 0; i < arrayLength; i++) {
            sum += array[Objects.checkIndex(i, array.length)];
        }
        return sum;
    }

    public static int objectsCheckIndexLoopOverLength() {
        int sum = 0;
        for (int i = 0; i < array.length; i++) {
            sum += array[Objects.checkIndex(i, array.length)];
        }
        return sum;
    }

    public static int objectsCheckIndexNonArrayLength() {
        int index = Objects.checkIndex(someIndex, arrayLength);
        if (index >= 0 && index < arrayLength) {
            return index;
        } else {
            return -1;
        }
    }

    public static int objectsCheckIndexNonArrayLength1() {
        int index = Objects.checkIndex(1, arrayLength);
        if (index >= 0 && index < arrayLength) {
            return index;
        } else {
            return -1;
        }
    }

    public static int objectsCheckIndexConstant() {
        return Objects.checkIndex(1, 2);
    }

    public static int byteArrayViewVarHandleGetInt() {
        return (int) byteArrayIntView.get(array, someIndex);
    }

    public static int byteArrayViewVarHandleGetIntConstIndex() {
        return (int) byteArrayIntView.get(array, 4);
    }

    public static int byteBufferViewVarHandleGetInt() {
        return (int) byteBufferIntView.get(buffer, someIndex);
    }

    public static int byteBufferViewVarHandleGetIntConstIndex() {
        return (int) byteBufferIntView.get(buffer, 4);
    }

    public static byte byteArrayViewVarHandleGetByte() {
        return (byte) byteArrayByteView.get(array, someIndex);
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

    @Test
    public void testByteArrayViewVarHandleGetInt() {
        test("byteArrayViewVarHandleGetInt");
        testGraph("byteArrayViewVarHandleGetInt");
        test("byteArrayViewVarHandleGetIntConstIndex");
        testGraph("byteArrayViewVarHandleGetIntConstIndex");
    }

    @Test
    public void testByteBufferViewVarHandleGetInt() {
        Assume.assumeTrue("GR-23778", JavaVersionUtil.JAVA_SPEC <= 11);
        testGraph("byteBufferViewVarHandleGetInt");
        test("byteBufferViewVarHandleGetInt");
        testGraph("byteBufferViewVarHandleGetIntConstIndex");
        test("byteBufferViewVarHandleGetIntConstIndex");
    }

    @Test
    public void testByteArrayViewVarHandleGetByte() {
        test("byteArrayViewVarHandleGetByte");
        testGraph("byteArrayViewVarHandleGetByte");
    }

    public static int checkIndexOOB() {
        return array[Objects.checkIndex(16, array.length)];
    }

    public static int checkIndexNegativeLength() {
        return array[Objects.checkIndex(1, negativeLength)];
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
        final InvocationPlugin requireNonNullPlugin = new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode obj) {
                b.addPush(JavaKind.Object, b.addNonNullCast(obj));
                return true;
            }
        };
        final Registration objects = new Registration(invocationPlugins, Objects.class);
        objects.register1("requireNonNull", Object.class, requireNonNullPlugin);
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
