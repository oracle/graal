/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.DWORD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.QWORD;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.XMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.YMM;
import static jdk.graal.compiler.asm.amd64.AVXKind.AVXSize.ZMM;

import java.util.EnumSet;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.asm.amd64.AVXKind.AVXSize;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.test.GraalTest;
import org.junit.Test;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.Register;

public class AMD64AssemblerUtilityTest extends GraalTest {

    @Test
    public void testAVXSizeFitsWithin() {
        assertTrue(DWORD.fitsWithin(DWORD));
        assertFalse(DWORD.fitsWithin(QWORD));
        assertFalse(DWORD.fitsWithin(XMM));
        assertFalse(DWORD.fitsWithin(YMM));
        assertFalse(DWORD.fitsWithin(ZMM));

        assertFalse(QWORD.fitsWithin(DWORD));
        assertTrue(QWORD.fitsWithin(QWORD));
        assertFalse(QWORD.fitsWithin(XMM));
        assertFalse(QWORD.fitsWithin(YMM));
        assertFalse(QWORD.fitsWithin(ZMM));

        assertFalse(XMM.fitsWithin(DWORD));
        assertFalse(XMM.fitsWithin(QWORD));
        assertTrue(XMM.fitsWithin(XMM));
        assertTrue(XMM.fitsWithin(YMM));
        assertTrue(XMM.fitsWithin(ZMM));

        assertFalse(YMM.fitsWithin(DWORD));
        assertFalse(YMM.fitsWithin(QWORD));
        assertFalse(YMM.fitsWithin(XMM));
        assertTrue(YMM.fitsWithin(YMM));
        assertTrue(YMM.fitsWithin(ZMM));

        assertFalse(ZMM.fitsWithin(DWORD));
        assertFalse(ZMM.fitsWithin(QWORD));
        assertFalse(ZMM.fitsWithin(XMM));
        assertFalse(ZMM.fitsWithin(YMM));
        assertTrue(ZMM.fitsWithin(ZMM));
    }

    @Test
    public void testAVXKindGetRegisterSize() {
        for (AMD64Kind kind : AMD64Kind.values()) {
            AVXSize size = AVXKind.getRegisterSize(new ConstantValue(LIRKind.value(kind), null));
            if (kind.isXMM()) {
                assertTrue(size.getBytes() >= kind.getSizeInBytes());
            } else {
                assertTrue(size == XMM);
            }
        }
    }

    @Test
    public void testAVXKindChangeSize() {
        for (AMD64Kind kind : AMD64Kind.values()) {
            if (kind.isMask()) {
                continue;
            }
            for (AVXSize size : EnumSet.of(XMM, YMM, ZMM)) {
                AMD64Kind newKind = AVXKind.changeSize(kind, size);
                assertDeepEquals(newKind.getScalar(), kind.getScalar());
                assertTrue(newKind.getScalar() == kind.getScalar());
                assertTrue(newKind.getSizeInBytes() == size.getBytes());
            }
        }
    }

    @Test
    public void testAMD64AddressToString() {
        assertDeepEquals("[rsp]", new AMD64Address(AMD64.rsp).toString());
        assertDeepEquals("[rip + 255]", new AMD64Address(AMD64.rip, 0x000000FF).toString());
        assertDeepEquals("[rax + rcx * 4]", new AMD64Address(AMD64.rax, AMD64.rcx, Stride.S4).toString());
        assertDeepEquals("[r11 + r9 * 2 + 32]", new AMD64Address(AMD64.r11, AMD64.r9, Stride.S2, 0x00000020).toString());
        assertDeepEquals("[r8 * 1 + annotation]", new AMD64Address(Register.None, AMD64.r8, Stride.S1, 0, "annotation").toString());
    }
}
