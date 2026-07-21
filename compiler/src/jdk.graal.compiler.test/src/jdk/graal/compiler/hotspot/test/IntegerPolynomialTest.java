/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Random;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.replacements.nodes.IntegerPolynomialAssignNode;
import jdk.graal.compiler.replacements.nodes.IntegerPolynomialP256MontgomeryMultNode;
import jdk.graal.compiler.test.AddExports;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.security.util.math.ImmutableIntegerModuloP;
import sun.security.util.math.MutableIntegerModuloP;
import sun.security.util.math.intpoly.Curve25519OrderField;
import sun.security.util.math.intpoly.Curve448OrderField;
import sun.security.util.math.intpoly.IntegerPolynomial;
import sun.security.util.math.intpoly.IntegerPolynomial1305;
import sun.security.util.math.intpoly.IntegerPolynomial25519;
import sun.security.util.math.intpoly.IntegerPolynomial448;
import sun.security.util.math.intpoly.IntegerPolynomialP256;
import sun.security.util.math.intpoly.IntegerPolynomialP384;
import sun.security.util.math.intpoly.IntegerPolynomialP521;
import sun.security.util.math.intpoly.MontgomeryIntegerPolynomialP256;
import sun.security.util.math.intpoly.P256OrderField;
import sun.security.util.math.intpoly.P384OrderField;
import sun.security.util.math.intpoly.P521OrderField;

@AddExports({"java.base/sun.security.util.math", "java.base/sun.security.util.math.intpoly"})
public final class IntegerPolynomialTest extends HotSpotGraalCompilerTest {

    @Test
    public void testIntegerPolynomial() {
        IntegerPolynomial[] testFields = {
                        IntegerPolynomial1305.ONE,
                        IntegerPolynomial25519.ONE,
                        IntegerPolynomial448.ONE,
                        IntegerPolynomialP256.ONE,
                        MontgomeryIntegerPolynomialP256.ONE,
                        IntegerPolynomialP384.ONE,
                        IntegerPolynomialP521.ONE,
                        P256OrderField.ONE,
                        P384OrderField.ONE,
                        P521OrderField.ONE,
                        Curve25519OrderField.ONE,
                        Curve448OrderField.ONE};

        Random rnd = getRandomInstance();

        Assume.assumeTrue("IntegerPolynomialAssignNode not supported", IntegerPolynomialAssignNode.isSupportedForRuntimeCheckedStub(getArchitecture()));
        Assume.assumeTrue("IntegerPolynomialP256MontgomeryMultNode not supported",
                        IntegerPolynomialP256MontgomeryMultNode.isSupportedForRuntimeCheckedStub(getArchitecture()));

        ResolvedJavaMethod intpolyAssignMethod = getResolvedJavaMethod(IntegerPolynomial.class, "conditionalAssign");
        ResolvedJavaMethod intpolyMontgomeryMultP256Method = getResolvedJavaMethod(MontgomeryIntegerPolynomialP256.class, "mult");
        StructuredGraph intpolyAssignGraph = getIntrinsicGraph(intpolyAssignMethod, IntegerPolynomialAssignNode.class);
        StructuredGraph intpolyMontgomeryMultP256Graph = getIntrinsicGraph(intpolyMontgomeryMultP256Method, IntegerPolynomialP256MontgomeryMultNode.class);

        InstalledCode intpolyAssign = getCode(intpolyAssignMethod, intpolyAssignGraph, true, false, GraalCompilerTest.getInitialOptions());
        InstalledCode intpolyMontgomeryMultP256 = getCode(intpolyMontgomeryMultP256Method, intpolyMontgomeryMultP256Graph, true, false, GraalCompilerTest.getInitialOptions());

        for (IntegerPolynomial field : testFields) {
            ImmutableIntegerModuloP aRef = field.getElement(new BigInteger(32 * 64, rnd));
            MutableIntegerModuloP a = aRef.mutable();
            ImmutableIntegerModuloP bRef = field.getElement(new BigInteger(32 * 64, rnd));
            MutableIntegerModuloP b = bRef.mutable();

            a.conditionalSet(b, 0); // Don't assign
            assertFalse(Arrays.equals(a.getLimbs(), b.getLimbs()));

            a.conditionalSet(b, 1); // Assign
            assertTrue(Arrays.equals(a.getLimbs(), b.getLimbs()));
        }

        assertTrue(intpolyAssign.isValid());
        assertTrue(intpolyMontgomeryMultP256.isValid());
        intpolyAssign.invalidate();
        intpolyMontgomeryMultP256.invalidate();
    }

    @Test
    public void testAMD64FeaturePredicates() {
        AMD64 minFeatures = amd64With(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.AVX_IFMA);
        AMD64 maxFeatures = amd64With(IntegerPolynomialAssignNode.maxFeaturesAMD64());
        AMD64 unsupportedFeatures = amd64With(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2);

        assertTrue(IntegerPolynomialAssignNode.isSupportedForRuntimeCheckedStub(minFeatures));
        assertTrue(IntegerPolynomialP256MontgomeryMultNode.isSupportedForRuntimeCheckedStub(minFeatures));
        assertTrue(IntegerPolynomialAssignNode.isSupportedForRuntimeCheckedStub(maxFeatures));
        assertTrue(IntegerPolynomialP256MontgomeryMultNode.isSupportedForRuntimeCheckedStub(maxFeatures));

        assertFalse(IntegerPolynomialAssignNode.isSupportedForRuntimeCheckedStub(unsupportedFeatures));
        assertFalse(IntegerPolynomialP256MontgomeryMultNode.isSupportedForRuntimeCheckedStub(unsupportedFeatures));
        assertFalse(IntegerPolynomialAssignNode.isSupported(minFeatures));
        assertFalse(IntegerPolynomialP256MontgomeryMultNode.isSupported(minFeatures));
    }

    @Test
    public void testAMD64MaximumFeaturePredicate() {
        Assert.assertEquals(EnumSet.of(AMD64.CPUFeature.AVX, AMD64.CPUFeature.AVX2, AMD64.CPUFeature.AVX512_IFMA, AMD64.CPUFeature.AVX512VL,
                        AMD64.CPUFeature.AVX512BW, AMD64.CPUFeature.AVX512F),
                        IntegerPolynomialAssignNode.maxFeaturesAMD64());
        Assert.assertEquals(IntegerPolynomialAssignNode.maxFeaturesAMD64(), IntegerPolynomialP256MontgomeryMultNode.maxFeaturesAMD64());
        assertTrue(IntegerPolynomialAssignNode.isSupported(amd64With(IntegerPolynomialAssignNode.maxFeaturesAMD64())));
        assertTrue(IntegerPolynomialP256MontgomeryMultNode.isSupported(amd64With(IntegerPolynomialP256MontgomeryMultNode.maxFeaturesAMD64())));
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

    private StructuredGraph getIntrinsicGraph(ResolvedJavaMethod method, Class<? extends Node> expectedNodeClass) {
        StructuredGraph graph = getIntrinsicGraph(method);
        assertTrue("missing intrinsic graph for " + method.format("%H.%n(%p)"), graph != null);
        boolean found = false;
        for (Node node : graph.getNodes()) {
            if (expectedNodeClass.isInstance(node)) {
                found = true;
                break;
            }
        }
        assertTrue("expected node " + expectedNodeClass.getSimpleName(), found);
        return graph;
    }
}
