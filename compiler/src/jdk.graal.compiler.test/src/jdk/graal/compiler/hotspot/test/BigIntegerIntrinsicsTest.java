/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.Random;

import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.runtime.RuntimeProvider;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/*
 * Indirectly test intrinsic/call substitutions for (innate) methods:
 *
 *      BigInteger.implMultiplyToLen
 *      BigInteger.implMulAdd
 *      BigInteger.implMontgomeryMultiply
 *      BigInteger.implMontgomerySquare
 *      BigInteger.implSquareToLen
 *
 * via BigInteger.multiply() and .modPow(). Note that the actual substitution
 * is not tested per se (only execution based on admissible intrinsics).
 *
 */
public final class BigIntegerIntrinsicsTest extends HotSpotGraalCompilerTest {

    static final int N = 100;

    @Test
    public void testMultiplyToLen() {
        EconomicMap<Pair<BigInteger, BigInteger>, BigInteger> expectedResults = EconomicMap.create();

        // interpreter
        for (int i = 0; i < N; i++) {
            BigInteger big1 = randomBig(i);
            BigInteger big2 = randomBig(i);

            expectedResults.put(Pair.create(big1, big2), big1.multiply(big2));
        }

        InstalledCode intrinsic = getCode(getResolvedJavaMethod(BigInteger.class, "multiplyToLen"), null, true, true, GraalCompilerTest.getInitialOptions());

        for (Pair<BigInteger, BigInteger> key : expectedResults.getKeys()) {
            BigInteger big1 = key.getLeft();
            BigInteger big2 = key.getRight();

            assertDeepEquals(big1.multiply(big2), expectedResults.get(key));
        }

        intrinsic.invalidate();
    }

    @Test
    public void testMulAdd() {
        EconomicMap<Pair<BigInteger, BigInteger>, BigInteger> expectedResults = EconomicMap.create();

        // interpreter
        for (int i = 0; i < N; i++) {
            BigInteger big1 = randomBig(i);
            BigInteger big2 = randomBig(i);

            // mulAdd is exercised via the call path modPow -> oddModPow -> montReduce
            expectedResults.put(Pair.create(big1, big2), big1.modPow(bigTwo, big2));
        }

        InstalledCode intrinsic = getCode(getResolvedJavaMethod(BigInteger.class, "mulAdd"), null, true, true, GraalCompilerTest.getInitialOptions());

        for (Pair<BigInteger, BigInteger> key : expectedResults.getKeys()) {
            BigInteger big1 = key.getLeft();
            BigInteger big2 = key.getRight();

            assertDeepEquals(big1.modPow(bigTwo, big2), expectedResults.get(key));
        }

        intrinsic.invalidate();
    }

    @Test
    public void testSquareToLen() {
        EconomicMap<Pair<BigInteger, BigInteger>, BigInteger> expectedResults = EconomicMap.create();

        // interpreter
        for (int i = 0; i < N; i++) {
            BigInteger big1 = randomBig(i);
            BigInteger big2 = randomBig(i);

            // squareToLen is exercised via the call path modPow -> oddModPow -> montgomerySquare
            expectedResults.put(Pair.create(big1, big2), big1.modPow(bigTwo, big2));
        }

        InstalledCode intrinsic = getCode(getResolvedJavaMethod(BigInteger.class, "squareToLen"), null, true, true, GraalCompilerTest.getInitialOptions());

        for (Pair<BigInteger, BigInteger> key : expectedResults.getKeys()) {
            BigInteger big1 = key.getLeft();
            BigInteger big2 = key.getRight();

            assertDeepEquals(big1.modPow(bigTwo, big2), expectedResults.get(key));
        }

        intrinsic.invalidate();
    }

    @Test
    public void testMontgomery() throws ClassNotFoundException {
        // Intrinsic must be available.
        Assume.assumeTrue(config.montgomeryMultiply != 0L || config.montgomerySquare != 0L);

        Class<?> javaclass = Class.forName("java.math.BigInteger");

        TestIntrinsic tin = new TestIntrinsic("testMontgomeryAux", javaclass,
                        "modPow", BigInteger.class, BigInteger.class);

        for (int i = 0; i < N; i++) {

            BigInteger big1 = randomBig(i);
            BigInteger big2 = randomBig(i);

            // Invoke BigInteger BigInteger.modPow(BigExp, BigInteger)
            BigInteger res1 = (BigInteger) tin.invokeJava(big1, bigTwo, big2);

            // Invoke BigInteger testMontgomeryAux(BigInteger, BigExp, BigInteger)
            BigInteger res2 = (BigInteger) tin.invokeTest(big1, bigTwo, big2);

            assertDeepEquals(res1, res2);

            // Invoke BigInteger testMontgomeryAux(BigInteger, BigExp, BigInteger)
            // through code handle.
            BigInteger res3 = (BigInteger) tin.invokeCode(big1, bigTwo, big2);

            assertDeepEquals(res1, res3);
        }
    }

    public static BigInteger testMultiplyAux(BigInteger a, BigInteger b) {
        return a.multiply(b);
    }

    public static BigInteger testMontgomeryAux(BigInteger a, BigInteger exp, BigInteger b) {
        return a.modPow(exp, b);
    }

    @Test
    public void testLeftShiftWorker() throws ClassNotFoundException {
        // Intrinsic must be available.
        Assume.assumeTrue(config.bigIntegerLeftShiftWorker != 0L);

        Class<?> javaclass = Class.forName("java.math.BigInteger");

        TestIntrinsic tin = new TestIntrinsic("bigIntegerLeftShiftWorker", javaclass,
                        "shiftLeft", int.class);

        for (int i = 0; i < N; i++) {

            BigInteger big1 = randomBig(i);
            int n = rnd.nextInt();

            // Invoke BigInteger.shiftLeft(int)
            BigInteger res1 = (BigInteger) tin.invokeJava(big1, n);

            // Invoke bigIntegerLeftShiftWorker(BigInteger, int)
            BigInteger res2 = (BigInteger) tin.invokeTest(big1, n);

            assertDeepEquals(res1, res2);

            // Invoke bigIntegerLeftShiftWorker(BigInteger, int)
            // through code handle.
            BigInteger res3 = (BigInteger) tin.invokeCode(big1, n);

            assertDeepEquals(res1, res3);
        }
    }

    public static BigInteger bigIntegerLeftShiftWorker(BigInteger src, int n) {
        return src.shiftLeft(n);
    }

    @Test
    public void testRightShiftWorker() throws ClassNotFoundException {
        // Intrinsic must be available.
        Assume.assumeTrue(config.bigIntegerLeftShiftWorker != 0L);

        Class<?> javaclass = Class.forName("java.math.BigInteger");

        TestIntrinsic tin = new TestIntrinsic("bigIntegerRightShiftWorker", javaclass,
                        "shiftRight", int.class);

        for (int i = 0; i < N; i++) {

            BigInteger big1 = randomBig(i);
            int n = rnd.nextInt();

            // Invoke BigInteger.shiftRight(int)
            BigInteger res1 = (BigInteger) tin.invokeJava(big1, n);

            // Invoke bigIntegerRightShiftWorker(BigInteger, int)
            BigInteger res2 = (BigInteger) tin.invokeTest(big1, n);

            assertDeepEquals(res1, res2);

            // Invoke bigIntegerRightShiftWorker(BigInteger, int)
            // through code handle.
            BigInteger res3 = (BigInteger) tin.invokeCode(big1, n);

            assertDeepEquals(res1, res3);
        }
    }

    public static BigInteger bigIntegerRightShiftWorker(BigInteger src, int n) {
        return src.shiftRight(n);
    }

    private class TestIntrinsic {

        TestIntrinsic(String testmname, Class<?> javaclass, String javamname, Class<?>... params) {
            javamethod = getResolvedJavaMethod(javaclass, javamname, params);
            testmethod = getResolvedJavaMethod(testmname);

            assert javamethod != null;
            assert testmethod != null;

            // Force the test method to be compiled.
            testcode = getCode(testmethod);

            assert testcode != null;
            assert testcode.isValid();
        }

        Object invokeJava(BigInteger big, Object... args) {
            return invokeSafe(javamethod, big, args);
        }

        Object invokeTest(Object... args) {
            return invokeSafe(testmethod, null, args);
        }

        Object invokeCode(Object... args) {
            try {
                return testcode.executeVarargs(args);
            } catch (InvalidInstalledCodeException e) {
                // Ensure the installed code is valid, possibly recompiled.
                testcode = getCode(testmethod);

                assert testcode != null;
                assert testcode.isValid();

                return invokeCode(args);
            }
        }

        private Object invokeSafe(ResolvedJavaMethod method, Object receiver, Object... args) {
            try {
                return invoke(method, receiver, args);
            } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | InstantiationException e) {
                throw new RuntimeException(e);
            }
        }

        // Private data section:
        private ResolvedJavaMethod javamethod;
        private ResolvedJavaMethod testmethod;
        private InstalledCode testcode;
    }

    private static GraalHotSpotVMConfig config = ((HotSpotGraalRuntimeProvider) Graal.getRequiredCapability(RuntimeProvider.class)).getVMConfig();

    private static BigInteger bigTwo = BigInteger.valueOf(2);
    private static Random rnd = GraalCompilerTest.getRandomInstance();

    private static BigInteger randomBig(int i) {
        return new BigInteger(rnd.nextInt(4096) + i2sz(i), rnd);
    }

    private static int i2sz(int i) {
        return i * 3 + 1;
    }
}
