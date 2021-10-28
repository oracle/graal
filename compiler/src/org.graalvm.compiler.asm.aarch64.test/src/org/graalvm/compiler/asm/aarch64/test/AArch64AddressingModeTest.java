/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.asm.aarch64.test;

import static org.junit.Assert.assertArrayEquals;

import java.util.EnumSet;

import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.test.GraalTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64.CPUFeature;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

public class AArch64AddressingModeTest extends GraalTest {

    private AArch64MacroAssembler masm;
    private TestProtectedAssembler asm;
    private Register base;
    private Register index;
    private Register scratch;

    private static EnumSet<AArch64.CPUFeature> computeFeatures() {
        EnumSet<AArch64.CPUFeature> features = EnumSet.noneOf(AArch64.CPUFeature.class);
        features.add(CPUFeature.FP);
        return features;
    }

    private static EnumSet<AArch64.Flag> computeFlags() {
        EnumSet<AArch64.Flag> flags = EnumSet.noneOf(AArch64.Flag.class);
        return flags;
    }

    private static TargetDescription createTarget() {
        final int stackFrameAlignment = 16;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = true;
        Architecture arch = new AArch64(computeFeatures(), computeFlags());
        return new TargetDescription(arch, true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects);
    }

    @Before
    public void setupEnvironment() {
        TargetDescription target = createTarget();
        masm = new AArch64MacroAssembler(target);
        asm = new TestProtectedAssembler(target);
        base = AArch64.r10;
        index = AArch64.r13;
        scratch = AArch64.r15;
    }

    @Test
    public void testMakeAddressNoActionScaled() {
        int displacement = NumUtil.getNbitNumberInt(12) << 3;
        AArch64Address address = masm.makeAddress(64, base, displacement);
        Assert.assertTrue(address.getAddressingMode() == AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED && address.getBase().equals(base) &&
                        address.getOffset().equals(AArch64.zr) && address.getImmediateRaw() == displacement >> 3);
        // No code generated.
        compareAssembly();
    }

    @Test
    public void testMakeAddressNoActionUnscaled() {
        int displacement = -1;
        AArch64Address address = masm.makeAddress(64, base, displacement);
        Assert.assertTrue(address.getAddressingMode() == AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED && address.getBase().equals(base) &&
                        address.getOffset().equals(AArch64.zr) && address.getImmediateRaw() == displacement);
        // No code generated.
        compareAssembly();
    }

    @Test
    public void testMakeAddressScratch() {
        int displacement = 0xDEAD;
        AArch64Address address = masm.makeAddress(64, base, displacement, scratch);
        Assert.assertTrue(address.getAddressingMode() == AArch64Address.AddressingMode.REGISTER_OFFSET && address.getBase().equals(base) &&
                        address.getOffset().equals(scratch));
        asm.movz(64, scratch, displacement, 0);
        compareAssembly();
    }

    @Test
    public void testLoadAddressUnscaled() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED, base, NumUtil.getNbitNumberInt(8));
        masm.loadAddress(dst, address);
        asm.add(64, dst, base, NumUtil.getNbitNumberInt(8));
        compareAssembly();
    }

    @Test
    public void testLoadAddressUnscaled2() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED, base, -NumUtil.getNbitNumberInt(8));
        masm.loadAddress(dst, address);
        asm.sub(64, dst, base, NumUtil.getNbitNumberInt(8));
        compareAssembly();
    }

    @Test
    public void testLoadAddressScaled() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, base, NumUtil.getNbitNumberInt(12) << 3);
        masm.loadAddress(dst, address);
        asm.add(64, dst, base, NumUtil.getNbitNumberInt(3) << 12);
        asm.add(64, dst, dst, NumUtil.getNbitNumberInt(9) << 3);
        compareAssembly();
    }

    @Test
    public void testLoadAddressScaledLowerOnly() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, base, NumUtil.getNbitNumberInt(5) << 3);
        masm.loadAddress(dst, address);
        asm.add(64, dst, base, NumUtil.getNbitNumberInt(5) << 3);
        compareAssembly();
    }

    @Test
    public void testLoadAddressScaledHigherOnly() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED, base, 1 << 14);
        masm.loadAddress(dst, address);
        asm.add(64, dst, base, 1 << 11 << 3);
        compareAssembly();
    }

    @Test
    public void testLoadAddressRegisterOffsetUnscaled() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createRegisterOffsetAddress(32, base, index, false);
        masm.loadAddress(dst, address);
        asm.add(64, dst, base, index, AArch64Assembler.ShiftType.LSL, 0);
        compareAssembly();
    }

    @Test
    public void testLoadAddressRegisterOffsetScaled() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createRegisterOffsetAddress(32, base, index, true);
        masm.loadAddress(dst, address);
        asm.add(64, dst, base, index, AArch64Assembler.ShiftType.LSL, 2);
        compareAssembly();
    }

    @Test
    public void testLoadAddressExtendedRegisterOffsetUnscaled() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createExtendedRegisterOffsetAddress(32, base, index, false, AArch64Assembler.ExtendType.SXTW);
        masm.loadAddress(dst, address);
        asm.add(64, dst, base, index, AArch64Assembler.ExtendType.SXTW, 0);
        compareAssembly();
    }

    @Test
    public void testLoadAddressExtendedRegisterOffsetScaled() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createExtendedRegisterOffsetAddress(32, base, index, true, AArch64Assembler.ExtendType.SXTW);
        masm.loadAddress(dst, address);
        asm.add(64, dst, base, index, AArch64Assembler.ExtendType.SXTW, 2);
        compareAssembly();
    }

    /**
     * Compares assembly generated by the macro assembler to the hand-generated assembly.
     */
    private void compareAssembly() {
        byte[] expected = asm.close(true);
        byte[] actual = masm.close(true);
        assertArrayEquals(expected, actual);
    }

}
