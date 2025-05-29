/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.lir.SyncPort;
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
            testAndJcc(compressed ? DWORD : QWORD, value, value, ConditionFlag.Zero, ok, true);
        }

        Register object = value;
        if (compressed) {
            CompressEncoding encoding = config.getOopEncoding();
            Register heapBaseRegister = AMD64Move.UncompressPointerOp.hasBase(encoding) ? providers.getRegisters().getHeapBaseRegister() : Register.None;
            movq(tmp, value);
            AMD64Move.UncompressPointerOp.emitUncompressCode(this, tmp, encoding.getShift(), heapBaseRegister, true);
            object = tmp;
        }

        // Load the klass into tmp
        if (config.useCompressedClassPointers) {
            if (config.useCompactObjectHeaders) {
                loadCompactClassPointer(tmp, object);
            } else {
                movl(tmp, new AMD64Address(object, config.hubOffset));
            }
            AMD64HotSpotMove.decodeKlassPointer(this, tmp, tmp2, config);
        } else {
            movq(tmp, new AMD64Address(object, config.hubOffset));
        }
        // Klass::_super_check_offset
        movl(tmp2, new AMD64Address(tmp, config.superCheckOffsetOffset));
        // if the super check offset is offsetof(_secondary_super_cache) then we skip the search of
        // the secondary super array
        cmplAndJcc(tmp2, config.secondarySuperCacheOffset, ConditionFlag.Equal, ok, true);
        // Load the klass from the primary supers
        movq(tmp2, new AMD64Address(tmp, tmp2, Stride.S1));
        // the Klass* should be equal
        cmpqAndJcc(tmp2, tmp, ConditionFlag.Equal, ok, true);
        illegal();
        bind(ok);
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/765cef45465806e53f11fa7d92b9c184899b0932/src/hotspot/cpu/x86/assembler_x86.cpp#L208-L242",
              sha1 = "7e213e437f5d3e7740874d69457de4ffebbee1c5")
    // @formatter:on
    @Override
    protected final int membarOffset() {
        // All usable chips support "locked" instructions which suffice
        // as barriers, and are much faster than the alternative of
        // using cpuid instruction. We use here a locked add [esp-C],0.
        // This is conveniently otherwise a no-op except for blowing
        // flags, and introducing a false dependency on target memory
        // location. We can't do anything with flags, but we can avoid
        // memory dependencies in the current method by locked-adding
        // somewhere else on the stack. Doing [esp+C] will collide with
        // something on stack in current method, hence we go for [esp-C].
        // It is convenient since it is almost always in data cache, for
        // any small C. We need to step back from SP to avoid data
        // dependencies with other things on below SP (callee-saves, for
        // example). Without a clear way to figure out the minimal safe
        // distance from SP, it makes sense to step back the complete
        // cache line, as this will also avoid possible second-order effects
        // with locked ops against the cache line. Our choice of offset
        // is bounded by x86 operand encoding, which should stay within
        // [-128; +127] to have the 8-byte displacement encoding.
        //
        // Any change to this code may need to revisit other places in
        // the code where this idiom is used, in particular the
        // orderAccess code.
        int offset = -config.l1LineSize;
        if (offset < -128) {
            offset = -128;
        }
        return offset;
    }

    @Override
    public Register getZeroValueRegister() {
        return providers.getRegisters().getZeroValueRegister(config);
    }

    public void loadCompactClassPointer(Register result, Register receiver) {
        GraalError.guarantee(config.useCompactObjectHeaders, "Load class pointer from markWord only when UseCompactObjectHeaders is on");
        movq(result, new AMD64Address(receiver, config.markOffset));
        shrq(result, config.markWordKlassShift);
    }
}
