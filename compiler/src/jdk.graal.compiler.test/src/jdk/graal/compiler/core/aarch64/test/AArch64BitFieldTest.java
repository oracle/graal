/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2021, Arm Limited and affiliates. All rights reserved.
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

package jdk.graal.compiler.core.aarch64.test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.aarch64.AArch64BitFieldOp;
import org.junit.Test;

import java.util.function.Predicate;

public class AArch64BitFieldTest extends AArch64MatchRuleTest {
    private static final Predicate<LIRInstruction> predicate = op -> (op instanceof AArch64BitFieldOp);

    // Keep the zero mask opaque until after the last canonicalization so the match rules see it.
    public static int zeroMaskExtractInt(int input) {
        return (input >>> 1) & GraalDirectives.opaque(0);
    }

    @Test
    public void testZeroMaskExtractInt() {
        test("zeroMaskExtractInt", -1);
        checkLIR("zeroMaskExtractInt", predicate, 0);
    }

    public static long zeroMaskExtractLong(long input) {
        return (input >>> 1) & GraalDirectives.opaque(0L);
    }

    @Test
    public void testZeroMaskExtractLong() {
        test("zeroMaskExtractLong", -1L);
        checkLIR("zeroMaskExtractLong", predicate, 0);
    }

    public static int zeroMaskInsertInt(int input) {
        return (input & GraalDirectives.opaque(0)) << 3;
    }

    @Test
    public void testZeroMaskInsertInt() {
        test("zeroMaskInsertInt", -1);
        checkLIR("zeroMaskInsertInt", predicate, 0);
    }

    public static long zeroMaskInsertLong(long input) {
        return (input & GraalDirectives.opaque(0L)) << 3;
    }

    @Test
    public void testZeroMaskInsertLong() {
        test("zeroMaskInsertLong", -1L);
        checkLIR("zeroMaskInsertLong", predicate, 0);
    }

    private void testAndCheckLIR(String method, String negativeMethod, Object input) {
        testAndCheckLIR(method, input);
        test(negativeMethod, input);
        checkLIR(negativeMethod, predicate, 0);
    }

    private void testAndCheckLIR(String method, Object input) {
        test(method, input);
        checkLIR(method, predicate, 1);
    }

    /**
     * unsigned bit field extract int.
     */
    public static int extractInt(int input) {
        return (input >>> 6) & 0xffff;
    }

    /**
     * unsigned bit field extract int (negative cases).
     */
    public static int invalidExtractInt(int input) {
        int result = 0;
        result += ((input >>> 10) & 0xfff0);    // Invalid mask
        result += ((input >>> 2) & 0x7fffffff); // Bit field width too large
        result += ((input >>> 16) & 0x1ffff);   // Width + lsb exceeds limit
        return result;
    }

    @Test
    public void testExtractInt() {
        testAndCheckLIR("extractInt", "invalidExtractInt", 0x12345678);
    }

    /**
     * unsigned bit field extract long.
     */
    public static long extractLong(long input) {
        return (input >>> 25) & 0xffffffffL;
    }

    /**
     * unsigned bit field extract long (negative cases).
     */
    public static long invalidExtractLong(long input) {
        long result = 0L;
        result += ((input >>> 10) & 0x230L);                // Invalid mask
        result += ((input >>> 2) & 0x7fffffffffffffffL);    // Bit field width too large
        result += ((input >>> 62) & 0x7L);                  // Width + lsb exceeds limit
        return result;
    }

    @Test
    public void testExtractLong() {
        testAndCheckLIR("extractLong", "invalidExtractLong", 0xfedcba9876543210L);
    }

    /**
     * unsigned bit field insert int.
     */
    public static int insertInt(int input) {
        return (input & 0xfff) << 10;
    }

    /**
     * unsigned bit field insert int (negative cases).
     */
    public static int invalidInsertInt(int input) {
        int result = 0;
        result += ((input & 0xe) << 25);        // Invalid mask
        result += ((input & 0x7fffffff) << 1);  // Bit field width too large
        result += ((input & 0x1ffff) << 16);    // Width + lsb exceeds limit
        return result;
    }

    @Test
    public void testInsertInt() {
        testAndCheckLIR("insertInt", "invalidInsertInt", 0xcafebabe);
    }

    /**
     * unsigned bit field insert long.
     */
    public static long insertLong(long input) {
        return (input & 0x3fffffffffffL) << 7;
    }

    /**
     * unsigned bit field insert long (negative cases).
     */
    public static long invalidInsertLong(long input) {
        long result = 0L;
        result += ((input & 0x1a) << 39);                   // Invalid mask
        result += ((input & 0x7fffffffffffffffL) << 1);     // Bit field width too large
        result += ((input & 0x3fffffff) << 52);             // Width + lsb exceeds limit
        return result;
    }

    @Test
    public void testInsertLong() {
        testAndCheckLIR("insertLong", "invalidInsertLong", 0xdeadbeefdeadbeefL);
    }

    // Keep the zero shift opaque until the end of low tier so that canonicalization does not
    // remove the shift before the bitfield insert match rules see it.
    public static int signedInsertIntZeroShift(int input) {
        return ((short) input) << GraalDirectives.opaque(0);
    }

    public static long signedInsertLongZeroShift(long input) {
        return ((long) (short) input) << GraalDirectives.opaque(0);
    }

    public static int unsignedInsertIntZeroShift(int input) {
        return (input & 0xffff) << GraalDirectives.opaque(0);
    }

    public static long unsignedInsertLongZeroShift(long input) {
        return (input & 0xffffL) << GraalDirectives.opaque(0);
    }

    @Test
    public void testSignedInsertIntZeroShift() {
        for (int input : new int[]{0, 0x7fff, 0x8000, -1, 0xcafebabe, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            test("signedInsertIntZeroShift", input);
        }
        checkLIR("signedInsertIntZeroShift", predicate, 1);
    }

    @Test
    public void testSignedInsertLongZeroShift() {
        for (long input : new long[]{0, 0x7fff, 0x8000, -1, 0xdeadbeefdeadbeefL, Long.MIN_VALUE, Long.MAX_VALUE}) {
            test("signedInsertLongZeroShift", input);
        }
        checkLIR("signedInsertLongZeroShift", predicate, 1);
    }

    @Test
    public void testUnsignedInsertIntZeroShift() {
        for (int input : new int[]{0, 0x7fff, 0x8000, -1, 0xcafebabe, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            test("unsignedInsertIntZeroShift", input);
        }
        checkLIR("unsignedInsertIntZeroShift", predicate, 1);
    }

    @Test
    public void testUnsignedInsertLongZeroShift() {
        for (long input : new long[]{0, 0x7fff, 0x8000, -1, 0xdeadbeefdeadbeefL, Long.MIN_VALUE, Long.MAX_VALUE}) {
            test("unsignedInsertLongZeroShift", input);
        }
        checkLIR("unsignedInsertLongZeroShift", predicate, 1);
    }

    // Tests for unsigned bitfield move, with integration of zero extend (I2L) operation.
    //
    // UBFIZ with I2L.
    public static long unsignedInsertExtend(int input) {
        return ((long) (input & 0xffff)) << 8;
    }

    @Test
    public void testUnsignedInsertExtend() {
        testAndCheckLIR("unsignedInsertExtend", 0x234);
    }

    // I2L with UBFIZ.
    public static long unsignedExtendInsert(int input) {
        return (input & 0xfff) << 5;
    }

    @Test
    public void testUnsignedExtendInsert() {
        testAndCheckLIR("unsignedExtendInsert", 0x4334);
    }

    // I2L with UBFX.
    public static long unsignedExtendExtract(int input) {
        return (input >>> 6) & 0xffffff;
    }

    @Test
    public void testUnsignedExtendExtract() {
        testAndCheckLIR("unsignedExtendExtract", 0x325ab);
    }

    // Signed bitfield insert with extend, generated by (LeftShift (SignExtend value) a) match
    // rule.
    // SBFIZ with B2L.
    public long signedB2LInsert(long input) {
        byte b = (byte) input;
        return ((long) b) << 2;
    }

    @Test
    public void testSignedB2LInsert() {
        testAndCheckLIR("signedB2LInsert", 0xab3213efL);
    }

    // SBFIZ with S2L.
    public long signedS2LInsert(long input) {
        short s = (short) input;
        return ((long) s) << -5;
    }

    @Test
    public void testSignedS2LInsert() {
        testAndCheckLIR("signedS2LInsert", 0x328032bL);
    }

    // SBFIZ with I2L.
    public static long signedI2LInsert(int input) {
        return ((long) input) << 1;
    }

    @Test
    public void testSignedI2LInsert() {
        testAndCheckLIR("signedI2LInsert", 31);
    }

    // SBFIZ with B2I.
    public int signedB2IInsert(int input) {
        byte b = (byte) input;
        return b << 30;
    }

    @Test
    public void testSignedB2IInsert() {
        testAndCheckLIR("signedB2IInsert", 0x23);
    }

    // SBFIZ with S2I.
    public int signedS2IInsert(int input) {
        short s = (short) input;
        return s << 2;
    }

    @Test
    public void testSignedS2IInsert() {
        testAndCheckLIR("signedS2IInsert", 0x92);
    }

    // Tests for bitfield move generated by ([Unsigned]RightShift (LeftShift value a) b) match
    // rules.
    // SBFX for int.
    public static int signedExtractInt(int input) {
        return (input << 8) >> 15;
    }

    @Test
    public void testSignedExtractInt() {
        testAndCheckLIR("signedExtractInt", 0x123);
    }

    // SBFX for long.
    public static long signedExtractLong(long input) {
        return (input << 8) >> 15;
    }

    @Test
    public void testSignedExtractLong() {
        testAndCheckLIR("signedExtractLong", 0x125L);
    }

    // SBFIZ for int.
    public static int signedInsertInt(int input) {
        return (input << 15) >> 8;
    }

    @Test
    public void testSignedInsertInt() {
        testAndCheckLIR("signedInsertInt", 0x1253);
    }

    // SBFIZ for long.
    public static long signedInsertLong(long input) {
        return (input << 15) >> 8;
    }

    @Test
    public void testSignedInsertLong() {
        testAndCheckLIR("signedInsertLong", 0xabcddbc325L);
    }

    // UBFX for int.
    public static int unsignedExtractInt(int input) {
        return (input << 8) >>> 31;
    }

    @Test
    public void testUnsignedExtractInt() {
        testAndCheckLIR("unsignedExtractInt", 0x125);
    }

    // UBFX for long.
    public static long unsignedExtractLong(long input) {
        return (input << 8) >>> 12;
    }

    @Test
    public void testUnsignedExtractLong() {
        testAndCheckLIR("unsignedExtractLong", 0x32222e125L);
    }

    // UBFIZ for int.
    public static int unsignedInsertInt(int input) {
        return (input << 15) >>> 8;
    }

    @Test
    public void testUnsignedInsertInt() {
        testAndCheckLIR("unsignedInsertInt", 125);
    }

    // UBFIZ for long.
    public static long unsignedInsertLong(long input) {
        return (input << 63) >>> 1;
    }

    @Test
    public void testUnsignedInsertLong() {
        testAndCheckLIR("unsignedInsertLong", 0x2339fb125L);
    }
}
