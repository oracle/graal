/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.junit.Assert;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.CalcStringAttributesNode;
import jdk.internal.misc.Unsafe;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class TStringOpsTest<T extends Node> extends TStringTest {

    protected static final com.oracle.truffle.api.nodes.Node DUMMY_LOCATION = new com.oracle.truffle.api.nodes.Node() {
    };

    protected static final Class<?> T_STRING_OPS_CLASS;
    private static final long byteBufferAddressOffset;

    static {
        Field addressField;
        try {
            addressField = Buffer.class.getDeclaredField("address");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("exception while trying to get Buffer.address via reflection:", e);
        }
        byteBufferAddressOffset = UNSAFE.objectFieldOffset(addressField);
        try {
            T_STRING_OPS_CLASS = Class.forName("com.oracle.truffle.api.strings.TStringOps");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected static long byteArrayBaseOffset() {
        return Unsafe.ARRAY_BYTE_BASE_OFFSET;
    }

    protected static long charArrayBaseOffset() {
        return Unsafe.ARRAY_CHAR_BASE_OFFSET;
    }

    protected static long intArrayBaseOffset() {
        return Unsafe.ARRAY_INT_BASE_OFFSET;
    }

    private static long getBufferAddress(ByteBuffer buffer) {
        return UNSAFE.getLong(buffer, byteBufferAddressOffset);
    }

    private final Class<T> nodeClass;

    protected TStringOpsTest(Class<T> nodeClass) {
        this.nodeClass = nodeClass;
    }

    protected ResolvedJavaMethod getTStringOpsMethod(String methodName, Class<?>... argTypes) {
        Class<?>[] argTypesWithNode = new Class<?>[argTypes.length + 1];
        argTypesWithNode[0] = com.oracle.truffle.api.nodes.Node.class;
        System.arraycopy(argTypes, 0, argTypesWithNode, 1, argTypes.length);
        return getResolvedJavaMethod(T_STRING_OPS_CLASS, methodName, argTypesWithNode);
    }

    protected void testWithNative(ResolvedJavaMethod method, Object receiver, Object... args) {
        testWithNativeExcept(method, receiver, 0, args);
    }

    /**
     * Runs the given test once as-is and once replacing all argument pairs of
     * {@code (byte[], long)} with {@code (null, native-pointer)}.
     * <p>
     * In most TruffleString operations, {@code (byte[] array, long offset)} stands for a string's
     * managed or native contents, where in the managed case, the byte array is a regular Java
     * array, and the offset is a byte offset into the byte array. In the native case, the byte
     * array is {@code null}, and the offset a pointer into native (off-heap) memory.
     * <p>
     * This test helper covers the native case for all {@code (byte[], long)} pairs, except for
     * parameters that have been marked as excluded by setting their corresponding bit in the
     * {@code ignore}-parameter: If e.g. {@code ignore == (1 << 3)}, the fourth parameter will not
     * be replaced by a native pointer.
     */
    protected void testWithNativeExcept(ResolvedJavaMethod method, Object receiver, long ignore, Object... args) {
        test(method, receiver, args);
        Object[] argsWithNative = Arrays.copyOf(args, args.length);
        ByteBuffer[] byteBuffers = new ByteBuffer[2];
        int nByteBuffers = 0;
        try {
            ResolvedJavaMethod.Parameter[] parameters = method.getParameters();
            Assert.assertTrue(parameters.length <= 64);
            for (int i = 0; i < parameters.length; i++) {
                if (parameters[i].getType().getName().equals("[B") && (ignore & (1L << i)) == 0 && i + 1 < parameters.length && parameters[i + 1].getType().getName().equals("J")) {
                    Assert.assertTrue(argsWithNative[i].toString(), argsWithNative[i] instanceof byte[]);
                    byte[] array = (byte[]) argsWithNative[i];
                    byteBuffers[nByteBuffers] = ByteBuffer.allocateDirect(array.length);
                    long bufferAddress = getBufferAddress(byteBuffers[nByteBuffers++]);
                    UNSAFE.copyMemory(array, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, bufferAddress, array.length);
                    long offset = (long) argsWithNative[i + 1];
                    Assert.assertTrue(offset >= byteArrayBaseOffset());
                    argsWithNative[i] = null;
                    argsWithNative[i + 1] = (offset - byteArrayBaseOffset()) + bufferAddress;
                }
            }
            test(method, receiver, argsWithNative);
        } finally {
            Reference.reachabilityFence(byteBuffers);
        }
    }

    protected ResolvedJavaMethod getArrayCopyWithStride() {
        return getTStringOpsMethod("arraycopyWithStride",
                        byte[].class, long.class, int.class, int.class,
                        byte[].class, long.class, int.class, int.class, int.class);
    }

    protected ResolvedJavaMethod getArrayCopyWithStrideCB() {
        return getTStringOpsMethod("arraycopyWithStrideCB",
                        char[].class, long.class,
                        byte[].class, long.class, int.class, int.class);
    }

    protected ResolvedJavaMethod getArrayCopyWithStrideIB() {
        return getTStringOpsMethod("arraycopyWithStrideIB",
                        int[].class, long.class,
                        byte[].class, long.class, int.class, int.class);
    }

    protected ResolvedJavaMethod getByteSwapS1() {
        return getTStringOpsMethod("byteSwapS1",
                        byte[].class, long.class,
                        byte[].class, long.class, int.class);
    }

    protected ResolvedJavaMethod getByteSwapS2() {
        return getTStringOpsMethod("byteSwapS2",
                        byte[].class, long.class,
                        byte[].class, long.class, int.class);
    }

    protected ResolvedJavaMethod getMemcmpWithStrideIntl() {
        return getTStringOpsMethod("memcmpWithStrideIntl",
                        byte[].class, long.class, int.class,
                        byte[].class, long.class, int.class, int.class);
    }

    protected ResolvedJavaMethod getRegionEqualsWithOrMaskWithStrideIntl() {
        return getTStringOpsMethod("regionEqualsWithOrMaskWithStrideIntl",
                        byte[].class, long.class, int.class, int.class, int.class,
                        byte[].class, long.class, int.class, int.class, int.class, byte[].class, int.class);
    }

    protected ResolvedJavaMethod getIndexOfAnyByteIntl() {
        return getTStringOpsMethod("indexOfAnyByteIntl",
                        byte[].class, long.class, int.class, int.class, byte[].class);
    }

    protected ResolvedJavaMethod getIndexOfAnyCharIntl() {
        return getTStringOpsMethod("indexOfAnyCharIntl",
                        byte[].class, long.class, int.class, int.class, int.class, char[].class);
    }

    protected ResolvedJavaMethod getIndexOfAnyIntIntl() {
        return getTStringOpsMethod("indexOfAnyIntIntl",
                        byte[].class, long.class, int.class, int.class, int.class, int[].class);
    }

    protected ResolvedJavaMethod getIndexOfAnyIntRangeIntl() {
        return getTStringOpsMethod("indexOfAnyIntRangeIntl",
                        byte[].class, long.class, int.class, int.class, int.class, int[].class);
    }

    protected ResolvedJavaMethod getIndexOfTableIntl() {
        return getTStringOpsMethod("indexOfTableIntl",
                        byte[].class, long.class, int.class, int.class, int.class, byte[].class);
    }

    protected ResolvedJavaMethod getIndexOf2ConsecutiveWithStrideIntl() {
        return getTStringOpsMethod("indexOf2ConsecutiveWithStrideIntl",
                        byte[].class, long.class, int.class, int.class, int.class, int.class, int.class);
    }

    protected InstalledCode cacheInstalledCodeConstantStride(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, OptionValues options, ResolvedJavaMethod expectedMethod,
                    InstalledCode[] cache1, int strideA, int strideB) {
        return cacheInstalledCodeConstantStrideLength(installedCodeOwner, graph, options, expectedMethod, cache1, strideA, strideB, 0);
    }

    protected InstalledCode cacheInstalledCodeConstantStrideLength(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, OptionValues options, ResolvedJavaMethod expectedMethod,
                    InstalledCode[] cache1, int strideA, int strideB, int iLength) {
        Assert.assertEquals(expectedMethod, installedCodeOwner);
        int index = (iLength * 9) + (strideA * 3) + strideB;
        InstalledCode installedCode = cache1[index];
        while (installedCode == null || !installedCode.isValid()) {
            installedCode = super.getCode(installedCodeOwner, graph, true, false, options);
            cache1[index] = installedCode;
        }
        return installedCode;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        if (isSupportedArchitecture() && hasRequiredFeatures(getTarget().arch)) {
            for (Node node : graph.getNodes()) {
                if (nodeClass.isInstance(node)) {
                    checkIntrinsicNode((T) node);
                    return;
                }
            }
            Assert.fail("intrinsic not found in graph!");
        }
    }

    protected void checkIntrinsicNode(@SuppressWarnings("unused") T node) {
    }

    private boolean hasRequiredFeatures(Architecture arch) {
        if (nodeClass.equals(CalcStringAttributesNode.class) && arch instanceof AMD64) {
            return ((AMD64) arch).getFeatures().contains(AMD64.CPUFeature.SSE4_1);
        }
        return true;
    }
}
