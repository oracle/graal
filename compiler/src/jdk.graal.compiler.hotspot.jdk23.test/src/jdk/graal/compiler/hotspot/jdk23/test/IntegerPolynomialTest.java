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

package jdk.graal.compiler.hotspot.jdk23.test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.hotspot.test.HotSpotGraalCompilerTest;
import jdk.graal.compiler.test.AddExports;
import jdk.vm.ci.code.InstalledCode;
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

        InstalledCode intpolyAssign = getCode(getResolvedJavaMethod(IntegerPolynomial.class, "conditionalAssign"), null, true, true, GraalCompilerTest.getInitialOptions());
        InstalledCode intpolyMontgomeryMultP256 = getCode(getResolvedJavaMethod(MontgomeryIntegerPolynomialP256.class, "mult"), null, true, true, GraalCompilerTest.getInitialOptions());

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
}
