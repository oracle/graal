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

import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.replacements.amd64.AMD64CalcStringAttributesNode;
import org.junit.Assert;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class TStringOpsTest<T> extends TStringTest {

    protected static final com.oracle.truffle.api.nodes.Node DUMMY_LOCATION = new com.oracle.truffle.api.nodes.Node() {
    };

    public static final String T_STRING_OPS_CLASS_NAME = "com.oracle.truffle.api.strings.TStringOps";
    private final Class<T> nodeClass;

    protected TStringOpsTest(Class<T> nodeClass) {
        this.nodeClass = nodeClass;
    }

    protected ResolvedJavaMethod getTStringOpsMethod(String methodName, Class<?>... argTypes) {
        try {
            Class<?> javaClass = Class.forName(T_STRING_OPS_CLASS_NAME);
            Class<?>[] argTypesWithNode = new Class<?>[argTypes.length + 1];
            argTypesWithNode[0] = com.oracle.truffle.api.nodes.Node.class;
            System.arraycopy(argTypes, 0, argTypesWithNode, 1, argTypes.length);
            return getResolvedJavaMethod(javaClass, methodName, argTypesWithNode);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected ResolvedJavaMethod getArrayCopyWithStride() {
        return getTStringOpsMethod("arraycopyWithStride",
                        Object.class, int.class, int.class, int.class,
                        Object.class, int.class, int.class, int.class, int.class);
    }

    protected ResolvedJavaMethod getArrayCopyWithStrideCB() {
        return getTStringOpsMethod("arraycopyWithStrideCB",
                        char[].class, int.class,
                        byte[].class, int.class, int.class, int.class);
    }

    protected ResolvedJavaMethod getArrayCopyWithStrideIB() {
        return getTStringOpsMethod("arraycopyWithStrideIB",
                        int[].class, int.class,
                        byte[].class, int.class, int.class, int.class);
    }

    protected ResolvedJavaMethod getMemcmpWithStrideIntl() {
        return getTStringOpsMethod("memcmpWithStrideIntl",
                        Object.class, int.class, int.class,
                        Object.class, int.class, int.class, int.class);
    }

    protected ResolvedJavaMethod getRegionEqualsWithOrMaskWithStrideIntl() {
        return getTStringOpsMethod("regionEqualsWithOrMaskWithStrideIntl",
                        Object.class, int.class, int.class, int.class, int.class,
                        Object.class, int.class, int.class, int.class, int.class, byte[].class, int.class);
    }

    protected ResolvedJavaMethod getIndexOfAnyByteIntl() {
        return getTStringOpsMethod("indexOfAnyByteIntl",
                        Object.class, int.class, int.class, int.class, byte[].class);
    }

    protected ResolvedJavaMethod getIndexOfAnyCharIntl() {
        return getTStringOpsMethod("indexOfAnyCharIntl",
                        Object.class, int.class, int.class, int.class, int.class, char[].class);
    }

    protected ResolvedJavaMethod getIndexOfAnyIntIntl() {
        return getTStringOpsMethod("indexOfAnyIntIntl",
                        Object.class, int.class, int.class, int.class, int.class, int[].class);
    }

    protected ResolvedJavaMethod getIndexOf2ConsecutiveWithStrideIntl() {
        return getTStringOpsMethod("indexOf2ConsecutiveWithStrideIntl",
                        Object.class, int.class, int.class, int.class, int.class, int.class, int.class);
    }

    protected InstalledCode cacheInstalledCodeConstantStride(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, OptionValues options, ResolvedJavaMethod expectedMethod,
                    InstalledCode[] cache, int strideA, int strideB) {
        return cacheInstalledCodeConstantStrideLength(installedCodeOwner, graph, options, expectedMethod, cache, strideA, strideB, 0);
    }

    protected InstalledCode cacheInstalledCodeConstantStrideLength(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, OptionValues options, ResolvedJavaMethod expectedMethod,
                    InstalledCode[] cache, int strideA, int strideB, int iLength) {
        Assert.assertEquals(expectedMethod, installedCodeOwner);
        int index = (iLength * 9) + (strideA * 3) + strideB;
        InstalledCode installedCode = cache[index];
        while (installedCode == null || !installedCode.isValid()) {
            installedCode = super.getCode(installedCodeOwner, graph, true, false, options);
            cache[index] = installedCode;
        }
        return installedCode;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        if (getTarget().arch instanceof AMD64 && hasRequiredFeatures(((AMD64) getTarget().arch))) {
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

    private boolean hasRequiredFeatures(AMD64 arch) {
        if (nodeClass.equals(AMD64CalcStringAttributesNode.class)) {
            return arch.getFeatures().contains(AMD64.CPUFeature.SSE4_1);
        }
        return true;
    }
}
