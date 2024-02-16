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
package jdk.graal.compiler.hotspot.aarch64;

import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;

public class AArch64HotSpotMacroAssembler extends AArch64MacroAssembler {
    private final GraalHotSpotVMConfig config;

    public AArch64HotSpotMacroAssembler(TargetDescription target, GraalHotSpotVMConfig config) {
        super(target);
        this.config = config;
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
}
