/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.Random;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;
import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.replacements.nodes.BigIntegerLeftShiftWorkerNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMontgomeryMultiplyNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerMontgomerySquareNode;
import jdk.graal.compiler.replacements.nodes.BigIntegerRightShiftWorkerNode;
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
 * via BigInteger.multiply() and .modPow(). The direct Montgomery tests below
 * additionally assert that the Graal intrinsic node is present in the graph.
 *
 */
public final class BigIntegerIntrinsicsTest extends HotSpotGraalCompilerTest {

    static final int N = 100;
    // BigInteger checks that Montgomery inputs have positive even 32-bit int lengths before it
    // reaches implMontgomeryMultiply/implMontgomerySquare. The x86 stubs divide that count by two
    // and operate on 64-bit longword array lengths, so these even int lengths still exercise the
    // longword length-one path (len == 2) and odd longword paths (for example len == 6, 10, 14).
    private static final int[] MONTGOMERY_MULTIPLY_LENGTHS = {
                    2, 4, 6, 8, 10, 14, 30, 32, 34, 62, 64, 66, 126, 128, 130, 254, 256, 258, 510, 512
    };
    // BigInteger.montgomerySquare routes longer arguments through the Java fallback before it
    // reaches implMontgomerySquare.
    private static final int[] MONTGOMERY_SQUARE_LENGTHS = {
                    2, 4, 6, 8, 10, 14, 30, 32, 34, 62, 64, 66, 126, 128, 130, 254, 256, 258, 510, 512
    };
    private static final int MONTGOMERY_EDGE_CASES = 6;
    private ResolvedJavaMethod expectedMontgomeryIntrinsicMethod;
    private Class<? extends Node> expectedMontgomeryIntrinsicNode;

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        if (expectedMontgomeryIntrinsicMethod != null && expectedMontgomeryIntrinsicMethod.equals(graph.method())) {
            assertTrue("expected " + expectedMontgomeryIntrinsicNode.getSimpleName() + " in " + graph.method().format("%H.%n(%p)"),
                            graph.getNodes().filter(expectedMontgomeryIntrinsicNode).isNotEmpty());
        }
    }

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
            if (big2.signum() > 0) {
                expectedResults.put(Pair.create(big1, big2), big1.modPow(bigTwo, big2));
            }
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
            if (big2.signum() > 0) {
                expectedResults.put(Pair.create(big1, big2), big1.modPow(bigTwo, big2));
            }
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
        Assume.assumeTrue(BigIntegerMontgomeryMultiplyNode.isSupported(getTarget().arch) || BigIntegerMontgomerySquareNode.isSupported(getTarget().arch));

        Class<?> javaclass = Class.forName("java.math.BigInteger");

        TestIntrinsic tin = new TestIntrinsic("testMontgomeryAux", javaclass,
                        "modPow", BigInteger.class, BigInteger.class);

        for (int i = 0; i < N; i++) {

            BigInteger big1 = randomBig(i);
            BigInteger big2 = randomBig(i);

            if (big2.signum() > 0) {
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
    }

    @Test
    public void testMontgomeryMultiplyIntrinsic() throws InvalidInstalledCodeException {
        Assume.assumeTrue(BigIntegerMontgomeryMultiplyNode.isSupported(getTarget().arch));

        InstalledCode intrinsic = getMontgomeryIntrinsicCode(getResolvedJavaMethod(BigInteger.class, "montgomeryMultiply", int[].class, int[].class, int[].class, int.class, long.class, int[].class),
                        BigIntegerMontgomeryMultiplyNode.class);
        assertTrue(intrinsic.isValid());

        for (int len : MONTGOMERY_MULTIPLY_LENGTHS) {
            MontgomeryModulus modulus = deterministicMontgomeryModulus(len);
            for (int i = 0; i < MONTGOMERY_EDGE_CASES; i++) {
                checkMontgomeryMultiply(intrinsic, edgeMontgomeryCase(len, modulus, i), len);
            }
            for (int i = 0; i < montgomeryRandomCases(len); i++) {
                checkMontgomeryMultiply(intrinsic, randomMontgomeryCase(len), len);
            }
        }

        intrinsic.invalidate();
    }

    @Test
    public void testMontgomerySquareIntrinsic() throws InvalidInstalledCodeException {
        Assume.assumeTrue(BigIntegerMontgomerySquareNode.isSupported(getTarget().arch));

        InstalledCode intrinsic = getMontgomeryIntrinsicCode(getResolvedJavaMethod(BigInteger.class, "montgomerySquare", int[].class, int[].class, int.class, long.class, int[].class),
                        BigIntegerMontgomerySquareNode.class);
        assertTrue(intrinsic.isValid());

        for (int len : MONTGOMERY_SQUARE_LENGTHS) {
            MontgomeryModulus modulus = deterministicMontgomeryModulus(len);
            for (int i = 0; i < MONTGOMERY_EDGE_CASES; i++) {
                checkMontgomerySquare(intrinsic, edgeMontgomeryCase(len, modulus, i), len);
            }
            for (int i = 0; i < montgomeryRandomCases(len); i++) {
                checkMontgomerySquare(intrinsic, randomMontgomeryCase(len), len);
            }
        }

        intrinsic.invalidate();
    }

    @Test
    public void testMontgomeryMultiplyIntrinsicMaterialization() throws InvalidInstalledCodeException {
        Assume.assumeTrue(BigIntegerMontgomeryMultiplyNode.isSupported(getTarget().arch));

        ResolvedJavaMethod method = getResolvedJavaMethod(BigInteger.class, "montgomeryMultiply", int[].class, int[].class, int[].class, int.class, long.class, int[].class);
        InstalledCode intrinsic = getMontgomeryIntrinsicCode(method, BigIntegerMontgomeryMultiplyNode.class);
        assertTrue(intrinsic.isValid());

        int len = 8;
        MontgomeryCase testCase = edgeMontgomeryCase(len, deterministicMontgomeryModulus(len), 5);
        checkMontgomeryMultiplyNullProduct(intrinsic, testCase, len);
        checkMontgomeryMultiplyLongProduct(intrinsic, testCase, len);

        intrinsic.invalidate();
    }

    @Test
    public void testMontgomerySquareIntrinsicMaterialization() throws InvalidInstalledCodeException {
        Assume.assumeTrue(BigIntegerMontgomerySquareNode.isSupported(getTarget().arch));

        ResolvedJavaMethod method = getResolvedJavaMethod(BigInteger.class, "montgomerySquare", int[].class, int[].class, int.class, long.class, int[].class);
        InstalledCode intrinsic = getMontgomeryIntrinsicCode(method, BigIntegerMontgomerySquareNode.class);
        assertTrue(intrinsic.isValid());

        int len = 8;
        MontgomeryCase testCase = edgeMontgomeryCase(len, deterministicMontgomeryModulus(len), 5);
        checkMontgomerySquareNullProduct(intrinsic, testCase, len);
        checkMontgomerySquareLongProduct(intrinsic, testCase, len);

        intrinsic.invalidate();
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
        Assume.assumeTrue(isBigIntegerLeftShiftWorkerSupported());

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

    @Test
    public void testLeftShiftWorkerStubProbe() throws InvalidInstalledCodeException {
        Assume.assumeTrue(isBigIntegerLeftShiftWorkerSupported());

        int[] mag = randomInts(97);
        mag[0] |= 1;
        int shiftCount = 17;
        int[] expected = shiftLeftExpected(mag, shiftCount);

        ResolvedJavaMethod method = getResolvedJavaMethod(BigInteger.class, "shiftLeft", int[].class, int.class);
        InstalledCode intrinsic = getCode(method, null, true, true, GraalCompilerTest.getInitialOptions());
        assertTrue(intrinsic.isValid());
        assertDeepEquals(expected, intrinsic.executeVarargs(mag, shiftCount));
        intrinsic.invalidate();
    }

    public static BigInteger bigIntegerLeftShiftWorker(BigInteger src, int n) {
        return src.shiftLeft(n);
    }

    @Test
    public void testLeftShiftWorkerCompileOnly() throws InvalidInstalledCodeException {
        Assume.assumeTrue(isBigIntegerLeftShiftWorkerSupported());

        ResolvedJavaMethod method = getResolvedJavaMethod("bigIntegerLeftShiftWorkerCompileProbe");
        InstalledCode intrinsic = getCode(method, null, true, true, GraalCompilerTest.getInitialOptions());
        assertTrue(intrinsic.isValid());
        intrinsic.invalidate();
    }

    public static BigInteger bigIntegerLeftShiftWorkerCompileProbe(BigInteger src) {
        return src.shiftLeft(17);
    }

    @Test
    public void testRightShiftWorker() throws ClassNotFoundException {
        // Intrinsic must be available.
        Assume.assumeTrue(isBigIntegerRightShiftWorkerSupported());

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

    @Test
    public void testRightShiftWorkerStubProbe() throws InvalidInstalledCodeException {
        Assume.assumeTrue(isBigIntegerRightShiftWorkerSupported());

        BigInteger big = new BigInteger(4096, rnd).setBit(4095);
        int shiftCount = 17;
        BigInteger expected = big.shiftRight(shiftCount);

        ResolvedJavaMethod method = getResolvedJavaMethod(BigInteger.class, "shiftRightImpl", int.class);
        InstalledCode intrinsic = getCode(method, null, true, true, GraalCompilerTest.getInitialOptions());
        assertTrue(intrinsic.isValid());
        assertDeepEquals(expected, intrinsic.executeVarargs(big, shiftCount));
        intrinsic.invalidate();
    }

    public static BigInteger bigIntegerRightShiftWorker(BigInteger src, int n) {
        return src.shiftRight(n);
    }

    @Test
    public void testRightShiftWorkerCompileOnly() throws InvalidInstalledCodeException {
        Assume.assumeTrue(isBigIntegerRightShiftWorkerSupported());

        ResolvedJavaMethod method = getResolvedJavaMethod("bigIntegerRightShiftWorkerCompileProbe");
        InstalledCode intrinsic = getCode(method, null, true, true, GraalCompilerTest.getInitialOptions());
        assertTrue(intrinsic.isValid());
        intrinsic.invalidate();
    }

    public static BigInteger bigIntegerRightShiftWorkerCompileProbe(BigInteger src) {
        return src.shiftRight(17);
    }

    private static int[] randomInts(int length) {
        int[] values = new int[length];
        for (int i = 0; i < values.length; i++) {
            values[i] = rnd.nextInt();
        }
        return values;
    }

    private static int[] shiftLeftExpected(int[] mag, int n) {
        int nInts = n >>> 5;
        int nBits = n & 0x1f;
        int magLen = mag.length;
        int[] newMag;

        if (nBits == 0) {
            newMag = new int[magLen + nInts];
            System.arraycopy(mag, 0, newMag, 0, magLen);
        } else {
            int i = 0;
            int nBits2 = 32 - nBits;
            int highBits = mag[0] >>> nBits2;
            if (highBits != 0) {
                newMag = new int[magLen + nInts + 1];
                newMag[i++] = highBits;
            } else {
                newMag = new int[magLen + nInts];
            }
            int numIter = magLen - 1;
            leftShiftWorkerExpected(newMag, mag, i, nBits, numIter);
            newMag[numIter + i] = mag[numIter] << nBits;
        }
        return newMag;
    }

    private static void leftShiftWorkerExpected(int[] newArr, int[] oldArr, int newIdx, int shiftCount, int numIter) {
        int shiftCountRight = 32 - shiftCount;
        int oldIdx = 0;
        while (oldIdx < numIter) {
            newArr[newIdx++] = (oldArr[oldIdx++] << shiftCount) | (oldArr[oldIdx] >>> shiftCountRight);
        }
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

    private static BigInteger bigTwo = BigInteger.valueOf(2);
    private static BigInteger twoPow64 = BigInteger.ONE.shiftLeft(64);
    private static Random rnd = GraalCompilerTest.getRandomInstance();

    private boolean isBigIntegerLeftShiftWorkerSupported() {
        return BigIntegerLeftShiftWorkerNode.isSupported(getTarget().arch);
    }

    private boolean isBigIntegerRightShiftWorkerSupported() {
        return BigIntegerRightShiftWorkerNode.isSupported(getTarget().arch);
    }

    private static BigInteger randomBig(int i) {
        return new BigInteger(rnd.nextInt(4096) + i2sz(i), rnd);
    }

    private static int i2sz(int i) {
        return i * 3 + 1;
    }

    private static MontgomeryCase randomMontgomeryCase(int len) {
        int bits = len * Integer.SIZE;
        BigInteger modulusValue = new BigInteger(bits, rnd).setBit(bits - 1).setBit(0);
        MontgomeryModulus modulus = montgomeryModulus(len, modulusValue);
        BigInteger a = new BigInteger(bits, rnd).mod(modulus.value);
        BigInteger b = new BigInteger(bits, rnd).mod(modulus.value);
        return montgomeryCase(len, modulus, a, b, "random");
    }

    private static MontgomeryModulus deterministicMontgomeryModulus(int len) {
        int bits = len * Integer.SIZE;
        BigInteger modulus = BigInteger.ONE.shiftLeft(bits - 1).add(BigInteger.ONE.shiftLeft(bits / 2)).add(BigInteger.valueOf(0x1_0000_0001L));
        return montgomeryModulus(len, modulus);
    }

    private static MontgomeryModulus montgomeryModulus(int len, BigInteger modulus) {
        int[] n = toFixedIntArray(modulus, len);
        long inv = unsignedLong(n[len - 2], n[len - 1]).modInverse(twoPow64).negate().mod(twoPow64).longValue();
        BigInteger rInverse = BigInteger.ONE.shiftLeft(len * Integer.SIZE).modInverse(modulus);
        return new MontgomeryModulus(modulus, n, inv, rInverse);
    }

    private static MontgomeryCase edgeMontgomeryCase(int len, MontgomeryModulus modulus, int variant) {
        BigInteger max = modulus.value.subtract(BigInteger.ONE);
        BigInteger high = BigInteger.ONE.shiftLeft(len * Integer.SIZE - 1).subtract(BigInteger.ONE).mod(modulus.value);
        BigInteger alternatingA = repeatedWordValue(len, 0xAAAA_AAAA, 0x5555_5555).mod(modulus.value);
        BigInteger alternatingB = repeatedWordValue(len, 0x3333_3333, 0xCCCC_CCCC).mod(modulus.value);
        return switch (variant) {
            case 0 -> montgomeryCase(len, modulus, BigInteger.ZERO, BigInteger.ZERO, "zero");
            case 1 -> montgomeryCase(len, modulus, BigInteger.ONE, BigInteger.ONE, "one");
            case 2 -> montgomeryCase(len, modulus, max, max, "max");
            case 3 -> montgomeryCase(len, modulus, max, BigInteger.ONE, "max-one");
            case 4 -> montgomeryCase(len, modulus, high, max.shiftRight(1), "high");
            case 5 -> montgomeryCase(len, modulus, alternatingA, alternatingB, "alternating");
            default -> throw new AssertionError(variant);
        };
    }

    private static MontgomeryCase montgomeryCase(int len, MontgomeryModulus modulus, BigInteger a, BigInteger b, String description) {
        return new MontgomeryCase(toFixedIntArray(a.mod(modulus.value), len), toFixedIntArray(b.mod(modulus.value), len), modulus.n.clone(), modulus.inv, modulus.value, modulus.rInverse, description);
    }

    private static int montgomeryRandomCases(int len) {
        if (len >= 512) {
            return 2;
        } else if (len >= 128) {
            return 4;
        }
        return 8;
    }

    private InstalledCode getMontgomeryIntrinsicCode(ResolvedJavaMethod method, Class<? extends Node> expectedNode) {
        expectedMontgomeryIntrinsicMethod = method;
        expectedMontgomeryIntrinsicNode = expectedNode;
        try {
            return getCode(method, null, true, true, GraalCompilerTest.getInitialOptions());
        } finally {
            expectedMontgomeryIntrinsicMethod = null;
            expectedMontgomeryIntrinsicNode = null;
        }
    }

    private void checkMontgomeryMultiply(InstalledCode intrinsic, MontgomeryCase testCase, int len) throws InvalidInstalledCodeException {
        int[] aBefore = testCase.a.clone();
        int[] bBefore = testCase.b.clone();
        int[] nBefore = testCase.n.clone();
        int[] product = randomInts(len);
        int[] expected = montgomeryExpected(testCase, testCase.b, len);
        Object result = intrinsic.executeVarargs(testCase.a, testCase.b, testCase.n, len, testCase.inv, product);

        try {
            assertMontgomeryResult(expected, product, len, testCase.n);
            assertDeepEquals(product, result);
            assertDeepEquals(aBefore, testCase.a);
            assertDeepEquals(bBefore, testCase.b);
            assertDeepEquals(nBefore, testCase.n);
        } catch (AssertionError e) {
            throw new AssertionError("montgomeryMultiply len=" + len + " case=" + testCase.description, e);
        }
    }

    private void checkMontgomerySquare(InstalledCode intrinsic, MontgomeryCase testCase, int len) throws InvalidInstalledCodeException {
        int[] aBefore = testCase.a.clone();
        int[] nBefore = testCase.n.clone();
        int[] product = randomInts(len);
        int[] expected = montgomeryExpected(testCase, testCase.a, len);
        Object result = intrinsic.executeVarargs(testCase.a, testCase.n, len, testCase.inv, product);

        try {
            assertMontgomeryResult(expected, product, len, testCase.n);
            assertDeepEquals(product, result);
            assertDeepEquals(aBefore, testCase.a);
            assertDeepEquals(nBefore, testCase.n);
        } catch (AssertionError e) {
            throw new AssertionError("montgomerySquare len=" + len + " case=" + testCase.description, e);
        }
    }

    private void checkMontgomeryMultiplyNullProduct(InstalledCode intrinsic, MontgomeryCase testCase, int len) throws InvalidInstalledCodeException {
        int[] expected = montgomeryExpected(testCase, testCase.b, len);
        int[] result = (int[]) intrinsic.executeVarargs(testCase.a, testCase.b, testCase.n, len, testCase.inv, null);
        assertDeepEquals(len, result.length);
        assertMontgomeryResult(expected, result, len, testCase.n);
    }

    private void checkMontgomerySquareNullProduct(InstalledCode intrinsic, MontgomeryCase testCase, int len) throws InvalidInstalledCodeException {
        int[] expected = montgomeryExpected(testCase, testCase.a, len);
        int[] result = (int[]) intrinsic.executeVarargs(testCase.a, testCase.n, len, testCase.inv, null);
        assertDeepEquals(len, result.length);
        assertMontgomeryResult(expected, result, len, testCase.n);
    }

    private void checkMontgomeryMultiplyLongProduct(InstalledCode intrinsic, MontgomeryCase testCase, int len) throws InvalidInstalledCodeException {
        int[] product = randomInts(len + 4);
        int[] tail = Arrays.copyOfRange(product, len, product.length);
        int[] expected = montgomeryExpected(testCase, testCase.b, len);
        Object result = intrinsic.executeVarargs(testCase.a, testCase.b, testCase.n, len, testCase.inv, product);
        assertDeepEquals(product, result);
        assertMontgomeryResult(expected, product, len, testCase.n);
        assertDeepEquals(tail, Arrays.copyOfRange(product, len, product.length));
    }

    private void checkMontgomerySquareLongProduct(InstalledCode intrinsic, MontgomeryCase testCase, int len) throws InvalidInstalledCodeException {
        int[] product = randomInts(len + 4);
        int[] tail = Arrays.copyOfRange(product, len, product.length);
        int[] expected = montgomeryExpected(testCase, testCase.a, len);
        Object result = intrinsic.executeVarargs(testCase.a, testCase.n, len, testCase.inv, product);
        assertDeepEquals(product, result);
        assertMontgomeryResult(expected, product, len, testCase.n);
        assertDeepEquals(tail, Arrays.copyOfRange(product, len, product.length));
    }

    private static int[] montgomeryExpected(MontgomeryCase testCase, int[] b, int len) {
        BigInteger result = fromIntArray(testCase.a).multiply(fromIntArray(b)).multiply(testCase.rInverse).mod(testCase.modulus);
        return toFixedIntArray(result, len);
    }

    private void assertMontgomeryResult(int[] expected, int[] actual, int len, int[] n) {
        BigInteger modulus = fromIntArray(n);
        BigInteger expectedValue = fromIntArray(expected);
        BigInteger actualValue = fromIntArray(Arrays.copyOf(actual, len));
        assertTrue(actualValue.equals(expectedValue) || actualValue.equals(expectedValue.add(modulus)));
        assertTrue(actualValue.compareTo(modulus.shiftLeft(1)) < 0);
    }

    private static BigInteger unsignedLong(int hi, int lo) {
        return BigInteger.valueOf(hi & 0xFFFF_FFFFL).shiftLeft(Integer.SIZE).add(BigInteger.valueOf(lo & 0xFFFF_FFFFL));
    }

    private static BigInteger fromIntArray(int[] value) {
        BigInteger result = BigInteger.ZERO;
        for (int word : value) {
            result = result.shiftLeft(Integer.SIZE).add(BigInteger.valueOf(word & 0xFFFF_FFFFL));
        }
        return result;
    }

    private static int[] toFixedIntArray(BigInteger value, int len) {
        int[] result = new int[len];
        BigInteger current = value;
        for (int i = len - 1; i >= 0; i--) {
            result[i] = current.intValue();
            current = current.shiftRight(Integer.SIZE);
        }
        return result;
    }

    private static BigInteger repeatedWordValue(int len, int evenWord, int oddWord) {
        int[] words = new int[len];
        for (int i = 0; i < len; i++) {
            words[i] = (i & 1) == 0 ? evenWord : oddWord;
        }
        return fromIntArray(words);
    }

    private static final class MontgomeryModulus {
        final BigInteger value;
        final int[] n;
        final long inv;
        final BigInteger rInverse;

        MontgomeryModulus(BigInteger value, int[] n, long inv, BigInteger rInverse) {
            this.value = value;
            this.n = n;
            this.inv = inv;
            this.rInverse = rInverse;
        }
    }

    private static final class MontgomeryCase {
        final int[] a;
        final int[] b;
        final int[] n;
        final long inv;
        final BigInteger modulus;
        final BigInteger rInverse;
        final String description;

        MontgomeryCase(int[] a, int[] b, int[] n, long inv, BigInteger modulus, BigInteger rInverse, String description) {
            this.a = a;
            this.b = b;
            this.n = n;
            this.inv = inv;
            this.modulus = modulus;
            this.rInverse = rInverse;
            this.description = description;
        }
    }
}
