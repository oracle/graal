/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.aarch64;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.lir.aarch64.AArch64Move;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;

public class AArch64HotSpotMacroAssembler extends AArch64MacroAssembler {
    private final GraalHotSpotVMConfig config;
    private final Register heapBaseRegister;

    private final ScratchRegister[] scratchRegister = new ScratchRegister[]{new ScratchRegister(AArch64.rscratch1), new ScratchRegister(AArch64.rscratch2)};

    public AArch64HotSpotMacroAssembler(TargetDescription target, GraalHotSpotVMConfig config, Register heapBaseRegister) {
        super(target);
        this.config = config;
        this.heapBaseRegister = heapBaseRegister;
    }

    @Override
    protected ScratchRegister[] getScratchRegisters() {
        return scratchRegister;
    }

    @Override
    public void postCallNop(Call call) {
        if (config.continuationsEnabled) {
            // Support for loom requires custom nops after call sites that might deopt
            if (call.debugInfo != null) {
                // Expected post call nop pattern taken from
                // src/hotspot/cpu/aarch64/macroAssembler_aarch64.cpp in
                // MacroAssembler::post_call_nop(). JVMCI will add a relocation during installation.
                nop();
                // HotSpot wants a very particular pattern but movk asserts when moving into zr so
                // emit the pattern explicitly instead.
                // instruction: 64-bit movk zr, 0
                int movkEncoding = (0b1_11_100101_00 << 21) | AArch64.zr.encoding();
                emitInt(movkEncoding);
                emitInt(movkEncoding);
                return;
            }
        }
        super.postCallNop(call);
    }

    /**
     * Perform some lightweight verification that value is a valid object or null. It checks that
     * the value is an instance of its own class though it only checks the primary super table for
     * compactness.
     */
    public void verifyOop(Register value, Register tmp, Register tmp2, boolean compressed, boolean nonNull) {
        guaranteeDifferentRegisters(value, tmp, tmp2);
        Label ok = new Label();

        if (!nonNull) {
            // null check the value
            cbz(compressed ? 32 : 64, value, ok);
        }

        Register object = value;
        if (compressed) {
            CompressEncoding encoding = config.getOopEncoding();
            mov(32, tmp, value);
            AArch64Move.UncompressPointerOp.emitUncompressCode(this, tmp, tmp, encoding, true, heapBaseRegister, false);
            object = tmp;
        }

        // Load the class
        if (config.useCompressedClassPointers) {
            if (config.useCompactObjectHeaders) {
                loadCompactClassPointer(tmp, object);
            } else {
                ldr(32, tmp, makeAddress(32, object, config.hubOffset));
            }
            AArch64HotSpotMove.decodeKlassPointer(this, tmp, tmp, config.getKlassEncoding());
        } else {
            ldr(64, tmp, makeAddress(64, object, config.hubOffset));
        }
        // Klass::_super_check_offset
        ldr(32, tmp2, makeAddress(32, tmp, config.superCheckOffsetOffset));
        compare(32, tmp2, config.secondarySuperCacheOffset);
        branchConditionally(AArch64Assembler.ConditionFlag.EQ, ok);

        // Load the klass from the primary supers
        ldr(64, tmp2, AArch64Address.createRegisterOffsetAddress(64, tmp, tmp2, false));
        cmp(64, tmp2, tmp);
        branchConditionally(AArch64Assembler.ConditionFlag.EQ, ok);
        illegal();
        bind(ok);
    }

    public void verifyHeapBase() {
        if (heapBaseRegister != null && config.narrowOopBase != 0) {
            try (AArch64MacroAssembler.ScratchRegister sc = getScratchRegister()) {
                Label skip = new Label();
                Register scratch = sc.getRegister();
                mov(scratch, config.narrowOopBase);
                cmp(64, heapBaseRegister, scratch);
                branchConditionally(AArch64Assembler.ConditionFlag.EQ, skip);
                AArch64Address base = makeAddress(64, heapBaseRegister, 0);
                ldr(64, scratch, base);
                bind(skip);
            }
        }
    }

    public void loadCompactClassPointer(Register result, Register receiver) {
        GraalError.guarantee(config.useCompactObjectHeaders, "Load class pointer from markWord only when UseCompactObjectHeaders is on");
        ldr(64, result, makeAddress(64, receiver, config.markOffset));
        lsr(64, result, result, config.markWordKlassShift);
    }

    @Override
    public boolean useLSE() {
        return config.useLSE;
    }

    @Override
    public boolean avoidUnalignedAccesses() {
        return config.avoidUnalignedAccesses;
    }
}
