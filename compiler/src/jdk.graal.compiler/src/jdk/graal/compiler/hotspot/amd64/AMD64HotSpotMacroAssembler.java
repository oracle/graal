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
package jdk.graal.compiler.hotspot.amd64;

import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.DWORD;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.lir.amd64.AMD64Move;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;

public class AMD64HotSpotMacroAssembler extends AMD64MacroAssembler {
    private final GraalHotSpotVMConfig config;
    private final HotSpotProviders providers;

    public AMD64HotSpotMacroAssembler(GraalHotSpotVMConfig config, TargetDescription target, OptionValues optionValues, HotSpotProviders providers, boolean hasIntelJccErratum) {
        super(target, optionValues, hasIntelJccErratum);
        this.config = config;
        this.providers = providers;
    }

    @Override
    public void postCallNop(Call call) {
        if (config.continuationsEnabled) {
            // Support for loom requires custom nops after call sites that might deopt
            if (call.debugInfo != null) {
                // Expected nop pattern taken from src/hotspot/cpu/x86/macroAssembler_x86.cpp in
                // MacroAssembler::post_call_nop(). JVMCI will add a relocation during installation.
                emitByte(0x0f);
                emitByte(0x1f);
                emitByte(0x84);
                emitByte(0x00);
                emitInt(0x00);
                return;
            }
        }
        super.postCallNop(call);
    }

    /**
     * Emit the expected patchable code sequence for the nmethod entry barrier. The int sized
     * payload must be naturally aligned so it can be patched atomically.
     */
    public void nmethodEntryCompare(int displacement) {
        // cmp dword ptr [r15 + <displacement>], 0x00000000
        // 41 81 7f <db> 00 00 00 00
        emitByte(0x41);
        emitByte(0x81);
        emitByte(0x7f);
        GraalError.guarantee(NumUtil.isByte(displacement), "expected byte sized displacement");
        emitByte(displacement & 0xff);
        GraalError.guarantee(position() % 4 == 0, "must be aligned");
        emitInt(0);
    }

    public void loadObject(Register dst, AMD64Address address) {
        if (config.useCompressedOops) {
            movl(dst, address);
            CompressEncoding encoding = config.getOopEncoding();
            Register baseReg = encoding.hasBase() ? providers.getRegisters().getHeapBaseRegister() : Register.None;
            AMD64Move.UncompressPointerOp.emitUncompressCode(this, dst, encoding.getShift(), baseReg, false);
        } else {
            movq(dst, address);
        }
    }

    /**
     * Perform some lightweight verification that value is a valid object or null. It checks that
     * the value is an instance of its own class though it only checks the primary super table for
     * compactness.
     */
    public void verifyOop(Register value, Register tmp, Register tmp2, boolean compressed, boolean nonNull) {
        Label ok = new Label();

        if (!nonNull) {
            // first null check the value
            testAndJcc(compressed ? DWORD : QWORD, value, value, AMD64Assembler.ConditionFlag.Zero, ok, true);
        }

        AMD64Address hubAddress;
        if (compressed) {
            CompressEncoding encoding = config.getOopEncoding();
            Register heapBaseRegister = AMD64Move.UncompressPointerOp.hasBase(encoding) ? providers.getRegisters().getHeapBaseRegister() : Register.None;
            movq(tmp, value);
            AMD64Move.UncompressPointerOp.emitUncompressCode(this, tmp, encoding.getShift(), heapBaseRegister, true);
            hubAddress = new AMD64Address(tmp, config.hubOffset);
        } else {
            hubAddress = new AMD64Address(value, config.hubOffset);
        }

        // Load the klass
        if (config.useCompressedClassPointers) {
            AMD64HotSpotMove.decodeKlassPointer(this, tmp, tmp2, hubAddress, config);
        } else {
            movq(tmp, hubAddress);
        }
        // Klass::_super_check_offset
        movl(tmp2, new AMD64Address(tmp, config.superCheckOffsetOffset));
        // if the super check offset is offsetof(_secondary_super_cache) then we skip the search of
        // the secondary super array
        cmplAndJcc(tmp2, config.secondarySuperCacheOffset, ConditionFlag.Equal, ok, true);
        // Load the klass from the primary supers
        movq(tmp2, new AMD64Address(tmp, tmp2, Stride.S1));
        // the Klass* should be equal
        cmpqAndJcc(tmp2, tmp, AMD64Assembler.ConditionFlag.Equal, ok, true);
        illegal();
        bind(ok);
    }
}
