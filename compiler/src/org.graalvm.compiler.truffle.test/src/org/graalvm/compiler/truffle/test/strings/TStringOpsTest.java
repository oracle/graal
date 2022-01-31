/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test.strings;

import java.nio.ByteOrder;

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.replacements.amd64.AMD64CalcStringAttributesNode;
import org.graalvm.compiler.replacements.test.MethodSubstitutionTest;
import org.graalvm.compiler.truffle.compiler.amd64.substitutions.TruffleAMD64InvocationPlugins;
import org.junit.Assert;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class TStringOpsTest<T> extends MethodSubstitutionTest {

    protected static final com.oracle.truffle.api.nodes.Node DUMMY_LOCATION = new com.oracle.truffle.api.nodes.Node() {
    };

    public static final String T_STRING_OPS_CLASS_NAME = "com.oracle.truffle.api.strings.TStringOps";
    private final Class<T> nodeClass;

    protected TStringOpsTest(Class<T> nodeClass) {
        this.nodeClass = nodeClass;
    }

    protected ResolvedJavaMethod getTStringOpsMethod(String methodName, Class<?>... argTypes) throws ClassNotFoundException {
        Class<?> javaClass = Class.forName(T_STRING_OPS_CLASS_NAME);
        Class<?>[] argTypesWithNode = new Class<?>[argTypes.length + 1];
        argTypesWithNode[0] = com.oracle.truffle.api.nodes.Node.class;
        System.arraycopy(argTypes, 0, argTypesWithNode, 1, argTypes.length);
        return getResolvedJavaMethod(javaClass, methodName, argTypesWithNode);
    }

    protected ResolvedJavaMethod getArrayCopyWithStride() throws ClassNotFoundException {
        return getTStringOpsMethod("arraycopyWithStride",
                        Object.class, int.class, int.class, int.class,
                        Object.class, int.class, int.class, int.class, int.class);
    }

    protected ResolvedJavaMethod getArrayCopyWithStrideCB() throws ClassNotFoundException {
        return getTStringOpsMethod("arraycopyWithStrideCB",
                        char[].class, int.class,
                        byte[].class, int.class, int.class, int.class);
    }

    protected ResolvedJavaMethod getArrayCopyWithStrideIB() throws ClassNotFoundException {
        return getTStringOpsMethod("arraycopyWithStrideIB",
                        int[].class, int.class,
                        byte[].class, int.class, int.class, int.class);
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        new TruffleAMD64InvocationPlugins().registerInvocationPlugins(getProviders(), getBackend().getTarget().arch, invocationPlugins, true);
        super.registerInvocationPlugins(invocationPlugins);
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        if (getTarget().arch instanceof AMD64 && hasRequiredFeatures(((AMD64) getTarget().arch))) {
            for (Node node : graph.getNodes()) {
                if (nodeClass.isInstance(node)) {
                    return;
                }
            }
            Assert.fail("intrinsic not found in graph!");
        }
    }

    private boolean hasRequiredFeatures(AMD64 arch) {
        if (nodeClass.equals(AMD64CalcStringAttributesNode.class)) {
            return arch.getFeatures().contains(AMD64.CPUFeature.SSE4_1);
        }
        return true;
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

    static int readValue(byte[] array, int stride, int index) {
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
}
