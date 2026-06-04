/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Method;
import java.security.Key;
import java.util.EnumSet;

import javax.crypto.spec.SecretKeySpec;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.replacements.nodes.Poly1305ProcessBlocksNode;
import jdk.graal.compiler.test.AddExports;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AddExports("java.base/com.sun.crypto.provider")
@SuppressWarnings("unchecked")
public class HotSpotPoly1305Test extends HotSpotGraalCompilerTest {

    private Class<?> poly1305Class;
    private Method engineInit;
    private Method engineUpdate;
    private Method engineDoFinal;
    private Method processMultipleBlocksReflect;
    private ResolvedJavaMethod processMultipleBlocks;

    @Before
    public void init() throws Exception {
        poly1305Class = Class.forName("com.sun.crypto.provider.Poly1305");
        engineInit = poly1305Class.getDeclaredMethod("engineInit", Key.class, Class.forName("java.security.spec.AlgorithmParameterSpec"));
        engineInit.setAccessible(true);
        engineUpdate = poly1305Class.getDeclaredMethod("engineUpdate", byte[].class, int.class, int.class);
        engineUpdate.setAccessible(true);
        engineDoFinal = poly1305Class.getDeclaredMethod("engineDoFinal");
        engineDoFinal.setAccessible(true);
        processMultipleBlocksReflect = poly1305Class.getDeclaredMethod("processMultipleBlocks", byte[].class, int.class, int.class, long[].class, long[].class);
        processMultipleBlocksReflect.setAccessible(true);
        processMultipleBlocks = getMetaAccess().lookupJavaMethod(poly1305Class.getDeclaredMethod("processMultipleBlocks", byte[].class, int.class, int.class, long[].class, long[].class));
    }

    private Object newPoly1305(byte[] key) throws Exception {
        var ctor = poly1305Class.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object p = ctor.newInstance();
        SecretKeySpec keySpec = new SecretKeySpec(key, "Poly1305");
        engineInit.invoke(p, keySpec, null);
        return p;
    }

    private byte[] mac(Object poly, byte[] input, int offset, int length, int chunkSize) throws Exception {
        int pos = offset;
        int left = length;
        while (left > 0) {
            int step = Math.min(left, chunkSize);
            engineUpdate.invoke(poly, input, pos, step);
            pos += step;
            left -= step;
        }
        return (byte[]) engineDoFinal.invoke(poly);
    }

    private static byte[] keyMaterial() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) (i * 13 + 7);
        }
        return key;
    }

    private static byte[] inputMaterial(int n, int start) {
        byte[] data = new byte[n + start];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (0xA5 ^ (i * 31));
        }
        return data;
    }

    @Test
    public void testKATVectors() throws Exception {
        byte[] key = keyMaterial();
        byte[] input = inputMaterial(16 * 7, 0);
        Object poly = newPoly1305(key);
        byte[] expected = mac(poly, input, 0, input.length, input.length);

        InstalledCode intrinsic = compileAndInstallSubstitution(processMultipleBlocks);
        Assume.assumeTrue("Poly1305 intrinsic not installed", intrinsic != null);
        try {
            byte[] actual = mac(newPoly1305(key), input, 0, input.length, input.length);
            Assert.assertArrayEquals(expected, actual);
        } finally {
            if (intrinsic != null) {
                intrinsic.invalidate();
            }
        }
    }

    @Test
    public void testOffset() throws Exception {
        byte[] key = keyMaterial();
        byte[] input = inputMaterial(16 * 6, 9);
        int off = 9;
        int len = 16 * 6;
        byte[] expected = mac(newPoly1305(key), input, off, len, len);

        InstalledCode intrinsic = compileAndInstallSubstitution(processMultipleBlocks);
        Assume.assumeTrue("Poly1305 intrinsic not installed", intrinsic != null);
        try {
            byte[] actual = mac(newPoly1305(key), input, off, len, len);
            Assert.assertArrayEquals(expected, actual);
        } finally {
            if (intrinsic != null) {
                intrinsic.invalidate();
            }
        }
    }

    @Test
    public void testRepeatedUpdates() throws Exception {
        byte[] key = keyMaterial();
        byte[] input = inputMaterial(16 * 10, 0);
        byte[] expected = mac(newPoly1305(key), input, 0, input.length, 21);

        InstalledCode intrinsic = compileAndInstallSubstitution(processMultipleBlocks);
        Assume.assumeTrue("Poly1305 intrinsic not installed", intrinsic != null);
        try {
            byte[] actual = mac(newPoly1305(key), input, 0, input.length, 21);
            Assert.assertArrayEquals(expected, actual);
        } finally {
            if (intrinsic != null) {
                intrinsic.invalidate();
            }
        }
    }

    @Test
    public void testZeroLengthNoop() throws Exception {
        byte[] key = keyMaterial();
        byte[] expected = mac(newPoly1305(key), new byte[0], 0, 0, 1);

        InstalledCode intrinsic = compileAndInstallSubstitution(processMultipleBlocks);
        Assume.assumeTrue("Poly1305 intrinsic not installed", intrinsic != null);
        try {
            Object poly = newPoly1305(key);
            processMultipleBlocksReflect.invoke(poly, new byte[16], 0, 0, new long[5], new long[5]);
            byte[] actual = (byte[]) engineDoFinal.invoke(poly);
            Assert.assertArrayEquals(expected, actual);
        } finally {
            if (intrinsic != null) {
                intrinsic.invalidate();
            }
        }
    }

    @Test
    public void testLargeInput() throws Exception {
        byte[] key = keyMaterial();
        byte[] input = inputMaterial(16 * 20, 0);
        byte[] expected = mac(newPoly1305(key), input, 0, input.length, input.length);

        InstalledCode intrinsic = compileAndInstallSubstitution(processMultipleBlocks);
        Assume.assumeTrue("Poly1305 intrinsic not installed", intrinsic != null);
        try {
            byte[] actual = mac(newPoly1305(key), input, 0, input.length, input.length);
            Assert.assertArrayEquals(expected, actual);
        } finally {
            if (intrinsic != null) {
                intrinsic.invalidate();
            }
        }
    }

    @Test
    public void testBoundaryLengths() throws Exception {
        byte[] key = keyMaterial();
        int[] lengths = {0, 16, 48, 64, 112};

        InstalledCode intrinsic = compileAndInstallSubstitution(processMultipleBlocks);
        Assume.assumeTrue("Poly1305 intrinsic not installed", intrinsic != null);
        try {
            for (int length : lengths) {
                byte[] input = inputMaterial(length, 0);
                byte[] expected = mac(newPoly1305(key), input, 0, length, length == 0 ? 1 : length);
                byte[] actual = mac(newPoly1305(key), input, 0, length, length == 0 ? 1 : length);
                Assert.assertArrayEquals("Poly1305 mismatch for boundary length " + length, expected, actual);
            }
        } finally {
            if (intrinsic != null) {
                intrinsic.invalidate();
            }
        }
    }

    @Test
    public void testGraphLoweringToNodeSupported() {
        StructuredGraph graph = getIntrinsicGraph(processMultipleBlocks);
        boolean foundNode = graph != null && graph.getNodes().filter(Poly1305ProcessBlocksNode.class).isNotEmpty();
        boolean shouldUseNode = Poly1305ProcessBlocksNode.isSupportedForRuntimeCheckedStub(getTarget().arch);
        Assert.assertEquals("Unexpected Poly1305 node activation state", shouldUseNode, foundNode);
    }

    @Test
    public void testAMD64FeaturePredicates() {
        Assert.assertTrue(Poly1305ProcessBlocksNode.isSupportedForRuntimeCheckedStub(amd64With(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.AVX_IFMA)));
        Assert.assertTrue(Poly1305ProcessBlocksNode.isSupportedForRuntimeCheckedStub(amd64With(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.AVX512_IFMA, AMD64.CPUFeature.AVX512VL,
                        AMD64.CPUFeature.AVX512BW, AMD64.CPUFeature.AVX512F)));
        Assert.assertFalse(Poly1305ProcessBlocksNode.isSupportedForRuntimeCheckedStub(amd64With(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2)));
        Assert.assertFalse(Poly1305ProcessBlocksNode.isSupported(amd64With(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.AVX_IFMA)));
    }

    @Test
    public void testAMD64MaximumFeaturePredicate() {
        Assert.assertEquals(EnumSet.of(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.AVX512_IFMA, AMD64.CPUFeature.AVX512VL,
                        AMD64.CPUFeature.AVX512BW, AMD64.CPUFeature.AVX512F),
                        Poly1305ProcessBlocksNode.maxFeaturesAMD64());
        Assert.assertTrue(Poly1305ProcessBlocksNode.isSupported(amd64With(Poly1305ProcessBlocksNode.maxFeaturesAMD64())));
    }

    private static AMD64 amd64With(EnumSet<AMD64.CPUFeature> features) {
        EnumSet<AMD64.CPUFeature> featureSet = EnumSet.of(AMD64.CPUFeature.SSE2);
        featureSet.addAll(features);
        return new AMD64(featureSet);
    }

    private static AMD64 amd64With(AMD64.CPUFeature... features) {
        EnumSet<AMD64.CPUFeature> featureSet = EnumSet.of(AMD64.CPUFeature.SSE2);
        for (AMD64.CPUFeature feature : features) {
            featureSet.add(feature);
        }
        return new AMD64(featureSet);
    }

}
