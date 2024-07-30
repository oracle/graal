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
package jdk.graal.compiler.truffle.test.strings;

import java.nio.ByteOrder;

import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.lir.amd64.AMD64ComplexVectorOp;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.replacements.ConstantBindingParameterPlugin;
import jdk.graal.compiler.replacements.test.MethodSubstitutionTest;
import jdk.graal.compiler.truffle.substitutions.TruffleInvocationPlugins;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;

public abstract class TStringTest extends MethodSubstitutionTest {

    public boolean isSupportedArchitecture() {
        return isSupportedArchitecture(getArchitecture());
    }

    public static boolean isSupportedArchitecture(Architecture architecture) {
        return architecture instanceof AMD64 || architecture instanceof AArch64;
    }

    protected void addConstantParameterBinding(GraphBuilderConfiguration conf, Object[] constantArgs) {
        if (constantArgs != null) {
            conf.getPlugins().appendParameterPlugin(new ConstantBindingParameterPlugin(constantArgs, getMetaAccess(), getSnippetReflection()));
        }
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        TruffleInvocationPlugins.register(getBackend().getTarget().arch, invocationPlugins, getReplacements());
        super.registerInvocationPlugins(invocationPlugins);
    }

    void assertConstantReturnForLength(StructuredGraph graph, int length) {
        if (isSupportedArchitecture()) {
            if (length < GraalOptions.StringIndexOfConstantLimit.getValue(graph.getOptions())) {
                assertConstantReturn(graph);
            }
        }
    }

    protected static void assertConstantReturn(StructuredGraph graph) {
        StartNode start = graph.start();
        FixedNode next = start.next();
        assertTrue(next instanceof ReturnNode);
        assertTrue(((ReturnNode) next).result().isConstant());
    }

    protected static void writeValue(byte[] array, int stride, int index, int value) {
        int i = index << stride;
        if (stride == 0) {
            array[i] = (byte) value;
            return;
        }
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            if (stride == 1) {
                array[i] = (byte) value;
                array[i + 1] = (byte) (value >> 8);
            } else {
                array[i] = (byte) value;
                array[i + 1] = (byte) (value >> 8);
                array[i + 2] = (byte) (value >> 16);
                array[i + 3] = (byte) (value >> 24);
            }
        } else {
            if (stride == 1) {
                array[i] = (byte) (value >> 8);
                array[i + 1] = (byte) value;
            } else {
                array[i] = (byte) (value >> 24);
                array[i + 1] = (byte) (value >> 16);
                array[i + 2] = (byte) (value >> 8);
                array[i + 3] = (byte) value;
            }
        }
    }

    protected static int readValue(byte[] array, int stride, int index) {
        int i = index << stride;
        if (stride == 0) {
            return Byte.toUnsignedInt(array[i]);
        }
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            if (stride == 1) {
                return Byte.toUnsignedInt(array[i]) | (Byte.toUnsignedInt(array[i + 1]) << 8);
            } else {
                return Byte.toUnsignedInt(array[i]) | (Byte.toUnsignedInt(array[i + 1]) << 8) | (Byte.toUnsignedInt(array[i + 2]) << 16) | (Byte.toUnsignedInt(array[i + 3]) << 24);
            }
        } else {
            if (stride == 1) {
                return Byte.toUnsignedInt(array[i + 1]) | (Byte.toUnsignedInt(array[i]) << 8);
            } else {
                return Byte.toUnsignedInt(array[i + 3]) | (Byte.toUnsignedInt(array[i + 2]) << 8) | (Byte.toUnsignedInt(array[i + 1]) << 16) | (Byte.toUnsignedInt(array[i]) << 24);
            }
        }
    }

    protected static int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }

    protected static boolean contains(int[] array, int value) {
        return indexOf(array, value) >= 0;
    }

    private int getConfigMaxVectorSize() {
        if (!(this.getBackend() instanceof HotSpotBackend)) {
            return -1;
        }
        HotSpotBackend backend = (HotSpotBackend) this.getBackend();
        return backend.getRuntime().getVMConfig().maxVectorSize;
    }

    protected AVXKind.AVXSize getMaxVectorSize() {
        int maxVectorSize = getConfigMaxVectorSize();
        if (AMD64ComplexVectorOp.supports(getTarget(), null, AMD64.CPUFeature.AVX512VL)) {
            if (maxVectorSize < 0 || maxVectorSize >= 64) {
                return AVXKind.AVXSize.ZMM;
            }
        }
        if (AMD64ComplexVectorOp.supports(getTarget(), null, AMD64.CPUFeature.AVX2)) {
            if (maxVectorSize < 0 || maxVectorSize >= 32) {
                return AVXKind.AVXSize.YMM;
            }
        }
        return AVXKind.AVXSize.XMM;
    }

    protected static char[] toCharArray(byte[] array) {
        char[] charArray = new char[array.length / 2];
        for (int i = 0; i < charArray.length; i++) {
            charArray[i] = (char) readValue(array, 1, i);
        }
        return charArray;
    }

    protected static int[] toIntArray(byte[] array) {
        int[] intArray = new int[array.length / 4];
        for (int i = 0; i < intArray.length; i++) {
            intArray[i] = readValue(array, 2, i);
        }
        return intArray;
    }

    protected static long[] toLongArray(byte[] array) {
        long[] longArray = new long[array.length / 8];
        for (int i = 0; i < longArray.length; i++) {
            longArray[i] = readValue(array, 3, i);
        }
        return longArray;
    }
}
