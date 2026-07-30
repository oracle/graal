/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.test.suites.arithmetic;

import static org.graalvm.wasm.WasmType.I32_TYPE;
import static org.graalvm.wasm.WasmType.I64_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Random;
import java.util.function.Consumer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.wasm.test.AbstractBinarySuite;
import org.junit.Test;

public class WideArithmeticSuite extends AbstractBinarySuite {
    private static final BigInteger TWO_TO_64 = BigInteger.ONE.shiftLeft(64);
    private static final BigInteger MASK_64 = TWO_TO_64.subtract(BigInteger.ONE);
    private static final BigInteger MASK_128 = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);

    private static final String ADD128 = "i64.add128";
    private static final String SUB128 = "i64.sub128";
    private static final String MUL_WIDE_S = "i64.mul_wide_s";
    private static final String MUL_WIDE_U = "i64.mul_wide_u";

    @Test
    public void testInterpreter() throws IOException {
        runRuntimeTest(createModule(), options(false), WideArithmeticSuite::testOperations);
    }

    @Test
    public void testCompiled() throws IOException {
        runRuntimeTest(createModule(), options(true), WideArithmeticSuite::testOperations);
    }

    @Test
    public void testOverlongOpcodes() throws IOException {
        final BinaryBuilder builder = newBuilder();
        builder.addType(new int[]{I64_TYPE, I64_TYPE, I64_TYPE, I64_TYPE}, new int[]{I64_TYPE, I64_TYPE});
        builder.addType(new int[]{I64_TYPE, I64_TYPE}, new int[]{I64_TYPE, I64_TYPE});
        builder.addFunction(0, EMPTY_INTS, "20 00 20 01 20 02 20 03 FC 93 80 00 0B");
        builder.addFunction(0, EMPTY_INTS, "20 00 20 01 20 02 20 03 FC 94 00 0B");
        builder.addFunction(1, EMPTY_INTS, "20 00 20 01 FC 95 80 80 80 00 0B");
        builder.addFunction(1, EMPTY_INTS, "20 00 20 01 FC 96 80 80 00 0B");
        builder.addFunctionExport(0, ADD128);
        builder.addFunctionExport(1, SUB128);
        builder.addFunctionExport(2, MUL_WIDE_S);
        builder.addFunctionExport(3, MUL_WIDE_U);
        final byte[] module = builder.build();
        runRuntimeTest(module, options(false), exports -> {
            assertResult(exports.getMember(ADD128), new long[]{4, 6}, 1L, 2L, 3L, 4L);
            assertResult(exports.getMember(SUB128), new long[]{-2, -3}, 1L, 2L, 3L, 4L);
            assertResult(exports.getMember(MUL_WIDE_S), new long[]{-2, -1}, 1L, -2L);
            assertResult(exports.getMember(MUL_WIDE_U), new long[]{6, 0}, 3L, 2L);
        });
    }

    @Test
    public void testMultipleInstantiations() throws IOException {
        runParserTest(createModule(), options(false), (context, source) -> {
            Value module = context.eval(source);
            Value firstExports = module.newInstance().getMember("exports");
            assertResult(firstExports.getMember(ADD128), new long[]{0, 1}, 1L, 0L, -1L, 0L);
            Value secondExports = module.newInstance().getMember("exports");
            assertResult(secondExports.getMember(MUL_WIDE_U), new long[]{1, -2}, -1L, -1L);
        });
    }

    @Test
    public void testDisabledByDefault() throws IOException {
        runParserTest(createModule(), (context, source) -> {
            PolyglotException exception = assertThrows(PolyglotException.class, () -> context.eval(source));
            assertTrue(exception.getMessage().contains("Wide arithmetic is not enabled"));
        });
    }

    @Test
    public void testMissingOperand() throws IOException {
        final BinaryBuilder builder = newBuilder();
        builder.addType(new int[]{I64_TYPE}, new int[]{I64_TYPE, I64_TYPE});
        builder.addFunction(0, EMPTY_INTS, "20 00 FC 15 0B");
        final byte[] module = builder.build();
        assertInvalid(module, "Expected type [i64], but got [].");
    }

    @Test
    public void testWrongOperandType() throws IOException {
        final BinaryBuilder builder = newBuilder();
        builder.addType(new int[]{I32_TYPE, I64_TYPE}, new int[]{I64_TYPE, I64_TYPE});
        builder.addFunction(0, EMPTY_INTS, "20 00 20 01 FC 15 0B");
        final byte[] module = builder.build();
        assertInvalid(module, "Expected type [i64], but got [i32].");
    }

    @Test
    public void testWrongResultArity() throws IOException {
        final BinaryBuilder builder = newBuilder();
        builder.addType(new int[]{I64_TYPE, I64_TYPE}, new int[]{I64_TYPE});
        builder.addFunction(0, EMPTY_INTS, "20 00 20 01 FC 15 0B");
        final byte[] module = builder.build();
        assertInvalid(module, "Expected result types [i64], but got [i64,i64].");
    }

    private static Consumer<Context.Builder> options(boolean compiled) {
        return builder -> {
            builder.allowExperimentalOptions(true);
            builder.option("wasm.WideArithmetic", "true");
            builder.option("engine.Compilation", Boolean.toString(compiled));
            if (compiled) {
                builder.option("engine.BackgroundCompilation", "false");
                builder.option("engine.CompileImmediately", "true");
            }
        };
    }

    private static void assertInvalid(byte[] module, String expectedMessage) throws IOException {
        runParserTest(module, options(false), (context, source) -> {
            PolyglotException exception = assertThrows(PolyglotException.class, () -> context.eval(source));
            assertEquals(expectedMessage, exception.getMessage());
        });
    }

    private static byte[] createModule() {
        final BinaryBuilder builder = newBuilder();
        builder.addType(new int[]{I64_TYPE, I64_TYPE, I64_TYPE, I64_TYPE}, new int[]{I64_TYPE, I64_TYPE});
        builder.addType(new int[]{I64_TYPE, I64_TYPE}, new int[]{I64_TYPE, I64_TYPE});
        builder.addFunction(0, EMPTY_INTS, "20 00 20 01 20 02 20 03 FC 13 0B");
        builder.addFunction(0, EMPTY_INTS, "20 00 20 01 20 02 20 03 FC 14 0B");
        builder.addFunction(1, EMPTY_INTS, "20 00 20 01 FC 15 0B");
        builder.addFunction(1, EMPTY_INTS, "20 00 20 01 FC 16 0B");
        builder.addFunctionExport(0, ADD128);
        builder.addFunctionExport(1, SUB128);
        builder.addFunctionExport(2, MUL_WIDE_S);
        builder.addFunctionExport(3, MUL_WIDE_U);
        return builder.build();
    }

    private static void testOperations(Value exports) {
        Value add128 = exports.getMember(ADD128);
        Value sub128 = exports.getMember(SUB128);
        Value mulWideS = exports.getMember(MUL_WIDE_S);
        Value mulWideU = exports.getMember(MUL_WIDE_U);

        assertResult(add128, new long[]{0, 0}, 0L, 0L, 0L, 0L);
        assertResult(add128, new long[]{0, 1}, 1L, 0L, -1L, 0L);
        assertResult(add128, new long[]{0, 1}, 1L, 1L, -1L, -1L);
        assertResult(add128, new long[]{-2, -1}, -1L, -1L, -1L, -1L);

        assertResult(sub128, new long[]{0, 0}, 0L, 0L, 0L, 0L);
        assertResult(sub128, new long[]{-1, -1}, 0L, 0L, 1L, 0L);
        assertResult(sub128, new long[]{-1, -2}, 0L, 0L, 1L, 1L);
        assertResult(sub128, new long[]{0, 0}, -1L, -1L, -1L, -1L);

        assertResult(mulWideS, new long[]{1, 0}, -1L, -1L);
        assertResult(mulWideS, new long[]{-1, -1}, -1L, 1L);
        assertResult(mulWideS, new long[]{0, 0x4000_0000_0000_0000L}, Long.MIN_VALUE, Long.MIN_VALUE);
        assertResult(mulWideU, new long[]{-1, 0}, -1L, 1L);
        assertResult(mulWideU, new long[]{1, -2}, -1L, -1L);
        assertResult(mulWideU, new long[]{0, 0}, Long.MIN_VALUE, 0L);

        Random random = new Random(0x5EED_128L);
        for (int i = 0; i < 100; i++) {
            long lhsLow = random.nextLong();
            long lhsHigh = random.nextLong();
            long rhsLow = random.nextLong();
            long rhsHigh = random.nextLong();
            assertResult(add128, split(wide(lhsLow, lhsHigh).add(wide(rhsLow, rhsHigh))), lhsLow, lhsHigh, rhsLow, rhsHigh);
            assertResult(sub128, split(wide(lhsLow, lhsHigh).subtract(wide(rhsLow, rhsHigh))), lhsLow, lhsHigh, rhsLow, rhsHigh);
            assertResult(mulWideS, split(BigInteger.valueOf(lhsLow).multiply(BigInteger.valueOf(rhsLow))), lhsLow, rhsLow);
            assertResult(mulWideU, split(unsigned(lhsLow).multiply(unsigned(rhsLow))), lhsLow, rhsLow);
        }
    }

    private static BigInteger wide(long low, long high) {
        return unsigned(high).shiftLeft(64).or(unsigned(low));
    }

    private static BigInteger unsigned(long value) {
        return BigInteger.valueOf(value).and(MASK_64);
    }

    private static long[] split(BigInteger value) {
        BigInteger wrapped = value.and(MASK_128);
        return new long[]{wrapped.longValue(), wrapped.shiftRight(64).longValue()};
    }

    private static void assertResult(Value function, long[] expected, long... arguments) {
        Value result = function.execute((Object[]) box(arguments));
        assertTrue(result.hasArrayElements());
        assertEquals(2, result.getArraySize());
        assertEquals(expected[0], result.getArrayElement(0).asLong());
        assertEquals(expected[1], result.getArrayElement(1).asLong());
    }

    private static Long[] box(long[] values) {
        Long[] boxed = new Long[values.length];
        for (int i = 0; i < values.length; i++) {
            boxed[i] = values[i];
        }
        return boxed;
    }
}
