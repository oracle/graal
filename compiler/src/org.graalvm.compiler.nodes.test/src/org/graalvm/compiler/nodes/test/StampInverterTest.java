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
package org.graalvm.compiler.nodes.test;

import static org.junit.Assert.assertEquals;

import org.graalvm.compiler.core.common.type.ArithmeticOpTable;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp.SignExtend;
import org.graalvm.compiler.core.common.type.ArithmeticOpTable.IntegerConvertOp.ZeroExtend;
import org.graalvm.compiler.test.GraalTest;
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

    private static Stamp invertZeroExtend(Stamp toInvert) {
        IntegerConvertOp<ZeroExtend> signExtend = ArithmeticOpTable.forStamp(toInvert).getZeroExtend();
        return signExtend.invertStamp(8, 32, toInvert);
    }

    @Test
    public void invertIntegerZeroExtend01() {
        // 32- > 8bit: xx...xx 11xxxxxx -> 11xxxxxx
        IntegerStamp stamp = IntegerStamp.stampForMask(32, 128 | 64, CodeUtil.mask(32));
        Stamp expected = IntegerStamp.stampForMask(8, 128 | 64, CodeUtil.mask(8));
        assertEquals(expected, invertZeroExtend(stamp));
    }

    @Test
    public void invertIntegerZeroExtend02() {
        // 32- > 8bit: xx...0x 01xxxxxx -> 01xxxxxx
        IntegerStamp stamp = IntegerStamp.stampForMask(32, 64, CodeUtil.mask(32) ^ (512 | 128));
        Stamp expected = IntegerStamp.stampForMask(8, 64, CodeUtil.mask(8) ^ 128);
        assertEquals(expected, invertZeroExtend(stamp));
    }

    @Test
    public void invertIntegerZeroExtend03() {
        // 32- > 8bit: xx...1x 01xxxxxx -> cannot be inverted
        IntegerStamp stamp = IntegerStamp.stampForMask(32, 512 | 64, CodeUtil.mask(32) ^ 128);
        assertTrue("Stamp cannot be inverted and should be empty!", invertZeroExtend(stamp).isEmpty());
    }
}