/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.test;

import static org.junit.Assert.assertEquals;

import jdk.graal.compiler.core.common.type.ArithmeticOpTable;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp.SignExtend;
import jdk.graal.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp.ZeroExtend;
import jdk.graal.compiler.test.GraalTest;
import org.junit.Test;

import jdk.vm.ci.code.CodeUtil;

/**
 * Tests the expected behavior of inverting stamps for different operations. During stamp inversion,
 * the input stamp is calculated from a given output stamp. If a particular stamp cannot be inverted
 * because of a contradiction regarding the operation's post condition, the inversion process is
 * supposed to return an empty stamp. An example for a contradiction would be both {@code 0} and
 * {@code 1} bits in the extension of a {@code SignExtend}.
 */
public class StampInverterTest extends GraalTest {

    private static Stamp invertSignExtend(Stamp toInvert) {
        IntegerConvertOp<SignExtend> signExtend = ArithmeticOpTable.forStamp(toInvert).getSignExtend();
        return signExtend.invertStamp(8, 32, toInvert);
    }

    @Test
    public void invertIntegerSignExtend01() {
        // 32- > 8bit: xx...xx 11xxxxxx -> 11xxxxxx
        IntegerStamp stamp = IntegerStamp.stampForMask(32, 128 | 64, CodeUtil.mask(32));
        Stamp expected = IntegerStamp.stampForMask(8, 128 | 64, CodeUtil.mask(8));
        assertEquals(expected, invertSignExtend(stamp));
    }

    @Test
    public void invertIntegerSignExtend02() {
        // 32 -> 8bit: xx...10 11xxxxxx -> cannot be inverted
        IntegerStamp stamp = IntegerStamp.stampForMask(32, 512 | 128 | 64, CodeUtil.mask(32) ^ 256);
        assertTrue("Stamp cannot be inverted and should be empty!", invertSignExtend(stamp).isEmpty());
    }

    @Test
    public void invertIntegerSignExtend03() {
        // 32 -> 8bit: xx...1x 0xxxxxxx -> cannot be inverted
        IntegerStamp stamp = IntegerStamp.stampForMask(32, 512, CodeUtil.mask(32) ^ 128);
        assertTrue("Stamp cannot be inverted and should be empty!", invertSignExtend(stamp).isEmpty());
    }

    @Test
    public void invertIntegerSignExtend04() {
        // 32 -> 8bit: xx...x0 1xxxxxxx -> cannot be inverted
        IntegerStamp stamp = IntegerStamp.stampForMask(32, 128, CodeUtil.mask(32) ^ 256);
        assertTrue("Stamp cannot be inverted and should be empty!", invertSignExtend(stamp).isEmpty());
    }

    @Test
    public void invertIntegerSignExtend05() {
        // 32 -> 8bit: xx...x0 xxxxxxxx -> 0xxxxxxx (msb has to be 0)
        IntegerStamp stamp = IntegerStamp.stampForMask(32, 0, CodeUtil.mask(32) ^ 256);
        Stamp expected = IntegerStamp.stampForMask(8, 0, CodeUtil.mask(7));
        assertEquals(expected, invertSignExtend(stamp));
    }

    @Test
    public void invertIntegerSignExtend06() {
        // 32 -> 8bit: xx...x1 xxxxxxxx -> 1xxxxxxx (msb has to be 1)
        IntegerStamp stamp = IntegerStamp.stampForMask(32, 256, CodeUtil.mask(32));
        Stamp expected = IntegerStamp.stampForMask(8, 128, CodeUtil.mask(8));
        assertEquals(expected, invertSignExtend(stamp));
    }

    @Test
    public void invertIntegerSignExtend07() {
        // 32 -> 8bit: [-128, 126] xx...xx xxxxxxx0 -> [-128, 126] xxxxxxx0
        IntegerStamp stamp = IntegerStamp.create(32, -128, 126, 0, CodeUtil.mask(32) ^ 1);
        Stamp expected = IntegerStamp.create(8, -128, 126, 0, CodeUtil.mask(32) ^ 1);
        assertEquals(expected, invertSignExtend(stamp));
    }

    @Test
    public void invertIntegerSignExtend08() {
        // 32 -> 8bit: [-256, 126] xx...xx 0xxxxxx0 -> [0, 126] 0xxxxxx0
        IntegerStamp stamp = IntegerStamp.create(32, -256, 126, 0, CodeUtil.mask(32) ^ (256 | 1));
        Stamp expected = IntegerStamp.create(8, 0, 126, 0, CodeUtil.mask(32) ^ (256 | 1));
        assertEquals(expected, invertSignExtend(stamp));
    }

    @Test
    public void invertIntegerSignExtend09() {
        // 32 -> 8bit: [-8, 126] xx...xx xxxxxxx0 -> [-8, 126] xxxxxxx0
        IntegerStamp stamp = IntegerStamp.create(32, -8, 126, 0, CodeUtil.mask(32) ^ 1);
        Stamp expected = IntegerStamp.create(8, -8, 126, 0, CodeUtil.mask(32) ^ 1);
        assertEquals(expected, invertSignExtend(stamp));
    }

    @Test
    public void invertIntegerSignExtend10() {
        // 32 -> 8bit: [int min, -1024] 1x...xx xxxxxxxx -> empty
        IntegerStamp stamp = IntegerStamp.create(32, Integer.MIN_VALUE, -1024);
        assertTrue("Stamp cannot be inverted and should be empty!", invertSignExtend(stamp).isEmpty());
    }

    @Test
    public void invertIntegerSignExtend11() {
        // 32 -> 8bit: [1024, int max] 0x...xx xxxxxxxx -> empty
        IntegerStamp stamp = IntegerStamp.create(32, 1024, Integer.MAX_VALUE);
        assertTrue("Stamp cannot be inverted and should be empty!", invertSignExtend(stamp).isEmpty());
    }

    @Test
    public void invertIntegerSignExtend12() {
        // 32 -> 8bit: [0, 255] 00...00 xxxxxxxx -> [0, 127] 0xxxxxxx
        IntegerStamp stamp = IntegerStamp.create(32, 0, 255, 0, CodeUtil.mask(8));
        Stamp expected = IntegerStamp.create(8, 0, 127, 0, CodeUtil.mask(7));
        assertEquals(expected, invertSignExtend(stamp));
    }

    private static Stamp invertZeroExtend(Stamp toInvert) {
        IntegerConvertOp<ZeroExtend> zeroExtend = ArithmeticOpTable.forStamp(toInvert).getZeroExtend();
        return zeroExtend.invertStamp(8, 32, toInvert);
    }

    @Test
    public void invertIntegerZeroExtend01() {
        // 32 -> 8bit: xx...xx 11xxxxxx -> 11xxxxxx
        IntegerStamp stamp = IntegerStamp.stampForMask(32, 128 | 64, CodeUtil.mask(32));
        Stamp expected = IntegerStamp.stampForMask(8, 128 | 64, CodeUtil.mask(8));
        assertEquals(expected, invertZeroExtend(stamp));
    }

    @Test
    public void invertIntegerZeroExtend02() {
        // 32 -> 8bit: xx...0x 01xxxxxx -> 01xxxxxx
        IntegerStamp stamp = IntegerStamp.stampForMask(32, 64, CodeUtil.mask(32) ^ (512 | 128));
        Stamp expected = IntegerStamp.stampForMask(8, 64, CodeUtil.mask(8) ^ 128);
        assertEquals(expected, invertZeroExtend(stamp));
    }

    @Test
    public void invertIntegerZeroExtend03() {
        // 32 -> 8bit: xx...1x 01xxxxxx -> cannot be inverted
        IntegerStamp stamp = IntegerStamp.stampForMask(32, 512 | 64, CodeUtil.mask(32) ^ 128);
        assertTrue("Stamp cannot be inverted and should be empty!", invertZeroExtend(stamp).isEmpty());
    }

    @Test
    public void invertIntegerZeroExtend04() {
        // 32 -> 8bit: [int min, int max -1] xx...xx xxxxxxx0 -> [-128, 126] xxxxxxx0
        IntegerStamp stamp = IntegerStamp.create(32, Integer.MIN_VALUE, Integer.MAX_VALUE - 1, 0, CodeUtil.mask(32) ^ 1);
        Stamp expected = IntegerStamp.create(8, -128, 126, 0, CodeUtil.mask(8) ^ 1);
        assertEquals(expected, invertZeroExtend(stamp));
    }

    @Test
    public void invertIntegerZeroExtend5() {
        /*
         * 32 -> 8bit: [0, 192] 00...00 xxxxxxx0 -> [-128, 126] xxxxxxx0
         *
         * The 32bit stamp bounds have different signs when converting to 8bit:
         * lowerUnsigned = 0000 0000, upperUnsigned = 1100 0000.
         * In order to respect the (unsigned) boundaries of the extended value, the signed input can be:
         * @formatter:off
         *
         * 0000 0000 - 0111 1111, i.e. 0 to 127
         * or
         * 1000 0000 - 1100 0000, i.e. -128 to -64
         *
         * @formatter:on
         * The resulting interval [-128, -64]u[0, 127] cannot be represented by a single upper and
         * lower bound. Thus, we have to return an unrestricted stamp, with respect to the bounds.
         */
        IntegerStamp stamp = IntegerStamp.create(32, 0, 192, 0, CodeUtil.mask(8) ^ 1);
        Stamp expected = IntegerStamp.create(8, -128, 126, 0, CodeUtil.mask(8) ^ 1);
        assertEquals(expected, invertZeroExtend(stamp));
    }

    @Test
    public void invertIntegerZeroExtend06() {
        /*
         * 32 -> 8bit: [-8, 16] xx...xx xxxxxxx0 -> [0, 16] xxxxxxx0
         *
         * Negative lower bounds can be clamped to 0.
         */
        IntegerStamp stamp = IntegerStamp.create(32, -8, 16, 0, CodeUtil.mask(32) ^ 1);
        Stamp expected = IntegerStamp.create(8, 0, 16, 0, CodeUtil.mask(8) ^ 1);
        assertEquals(expected, invertZeroExtend(stamp));
    }

    @Test
    public void invertIntegerZeroExtend07() {
        // 32 -> 8bit: [2, 18] 00...00 000xxx10 -> [2, 18] 000xxx10
        IntegerStamp stamp = IntegerStamp.create(32, 2, 18, 2, CodeUtil.mask(5) ^ 1);
        Stamp expected = IntegerStamp.create(8, 2, 18, 2, CodeUtil.mask(5) ^ 1);
        assertEquals(expected, invertZeroExtend(stamp));
    }

    @Test
    public void invertIntegerZeroExtend08() {
        // 32 -> 8bit: [128, 254] 00...00 1xxxxxx0 -> [-128, -2] 1xxxxxx0
        IntegerStamp stamp = IntegerStamp.create(32, 128, 254, 128, CodeUtil.mask(8) ^ 1);
        Stamp expected = IntegerStamp.create(8, -128, -2, 128, CodeUtil.mask(8) ^ 1);
        assertEquals(expected, invertZeroExtend(stamp));
    }

    @Test
    public void invertIntegerZeroExtend09() {
        // 32 -> 8bit: [int min ^ 128, 254] xx...xx xxxxxxx0 -> [-128, 126] xxxxxxx0
        IntegerStamp stamp = IntegerStamp.create(32, Integer.MIN_VALUE ^ 128, 254, 0, CodeUtil.mask(32) ^ 1);
        Stamp expected = IntegerStamp.create(8, -128, 126, 0, CodeUtil.mask(8) ^ 1);
        assertEquals(expected, invertZeroExtend(stamp));
    }
}
