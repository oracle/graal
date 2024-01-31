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

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.options.OptionValues;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;

public class AMD64HotSpotMacroAssembler extends AMD64MacroAssembler {
    private final GraalHotSpotVMConfig config;

    public AMD64HotSpotMacroAssembler(GraalHotSpotVMConfig config, TargetDescription target, OptionValues optionValues, boolean hasIntelJccErratum) {
        super(target, optionValues, hasIntelJccErratum);
        this.config = config;
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
}
