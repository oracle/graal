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

package jdk.graal.compiler.asm.amd64.test;

import static org.junit.Assert.assertThrows;

import java.util.EnumSet;
import java.util.HexFormat;

import org.junit.Test;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.test.GraalTest;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64.CPUFeature;
import jdk.vm.ci.code.TargetDescription;

public class AMD64MovLongTest extends GraalTest {

    // Only encoding is exercised; the target need not match the host.
    private static final TargetDescription TARGET = new TargetDescription(new AMD64(EnumSet.of(CPUFeature.SSE, CPUFeature.SSE2)), true, 16, 4096, true);

    @Test
    public void splitStoreDisplacementBoundaries() {
        assertEncoding("c7 80 fb ff ff 7f ef cd ab 89 c7 80 ff ff ff 7f 67 45 23 01", Integer.MAX_VALUE - Integer.BYTES, 0x0123_4567_89ab_cdefL);
        assertEncoding("c7 80 00 00 00 80 ef cd ab 89 c7 80 04 00 00 80 67 45 23 01", Integer.MIN_VALUE, 0x0123_4567_89ab_cdefL);
    }

    @Test
    public void rejectSplitStoreDisplacementOverflow() {
        for (int offset = 0; offset < Integer.BYTES; offset++) {
            int displacement = Integer.MAX_VALUE - offset;
            for (long value : new long[]{0x0123_4567_89ab_cdefL, Long.MIN_VALUE, Long.MAX_VALUE}) {
                for (AMD64Address address : new AMD64Address[]{
                                new AMD64Address(AMD64.rax, displacement),
                                new AMD64Address(AMD64.rax, AMD64.rbx, Stride.S8, displacement)}) {
                    AMD64MacroAssembler asm = new AMD64MacroAssembler(TARGET);
                    asm.emitByte(0x90);
                    int position = asm.position();
                    assertThrows(GraalError.class, () -> asm.movlong(address, value, false));
                    assertDeepEquals("Rejected stores must not emit partial code", position, asm.position());
                }
            }
        }
    }

    @Test
    public void singleStoreAllowsMaximumDisplacement() {
        assertEncoding("48 c7 80 ff ff ff 7f 00 00 00 00", Integer.MAX_VALUE, 0);
        assertEncoding("48 c7 80 ff ff ff 7f ff ff ff ff", Integer.MAX_VALUE, -1);
        assertEncoding("48 c7 80 ff ff ff 7f ff ff ff 7f", Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEncoding("48 c7 80 ff ff ff 7f 00 00 00 80", Integer.MAX_VALUE, Integer.MIN_VALUE);
    }

    private void assertEncoding(String expected, int displacement, long value) {
        AMD64MacroAssembler asm = new AMD64MacroAssembler(TARGET);
        asm.movlong(new AMD64Address(AMD64.rax, displacement), value, false);
        assertDeepEquals(expected, HexFormat.ofDelimiter(" ").formatHex(asm.copy(0, asm.position())));
    }
}
