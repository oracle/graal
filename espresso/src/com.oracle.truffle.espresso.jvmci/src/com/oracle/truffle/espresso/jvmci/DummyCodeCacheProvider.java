/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.jvmci;

import static jdk.vm.ci.amd64.AMD64.CPUFeature.CMOV;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.CX8;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.FXSR;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.MMX;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.POPCNT;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE3;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_1;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSE4_2;
import static jdk.vm.ci.amd64.AMD64.CPUFeature.SSSE3;
import static jdk.vm.ci.riscv64.RISCV64.CPUFeature.A;
import static jdk.vm.ci.riscv64.RISCV64.CPUFeature.C;
import static jdk.vm.ci.riscv64.RISCV64.CPUFeature.D;
import static jdk.vm.ci.riscv64.RISCV64.CPUFeature.F;
import static jdk.vm.ci.riscv64.RISCV64.CPUFeature.I;
import static jdk.vm.ci.riscv64.RISCV64.CPUFeature.M;

import java.util.EnumSet;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.riscv64.RISCV64;

public final class DummyCodeCacheProvider implements CodeCacheProvider {
    private final TargetDescription target;

    public DummyCodeCacheProvider(TargetDescription target) {
        this.target = target;
    }

    @Override
    public InstalledCode installCode(ResolvedJavaMethod method, CompiledCode compiledCode, InstalledCode installedCode, SpeculationLog log, boolean isDefault, boolean profileDeopt) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public void invalidateInstalledCode(InstalledCode installedCode) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public RegisterConfig getRegisterConfig() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public int getMinimumOutgoingSize() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public TargetDescription getTarget() {
        return target;
    }

    @Override
    public SpeculationLog createSpeculationLog() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public long getMaxCallTargetOffset(long address) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public boolean shouldDebugNonSafepoints() {
        throw JVMCIError.unimplemented();
    }

    /**
     * Returns a {@link TargetDescription} that matches the host's architecture.
     * <p>
     * Note that beyond the general ISA type (AMD64, AArch64, ...) details of the actual host target
     * are currently not accurate.
     */
    public static TargetDescription getHostTarget() {
        String archString = System.getProperty("os.arch");
        Architecture arch = switch (archString) {
            case "amd64", "x86_64" -> {
                EnumSet<AMD64.CPUFeature> x8664v2 = EnumSet.of(CMOV, CX8, FXSR, MMX, SSE, SSE2, POPCNT, SSE3, SSE4_1, SSE4_2, SSSE3);
                yield new AMD64(x8664v2);
            }
            case "aarch64", "arm64" -> new AArch64(EnumSet.of(AArch64.CPUFeature.FP));
            case "riscv64" -> {
                EnumSet<RISCV64.CPUFeature> imacfd = EnumSet.of(I, M, A, C, F, D);
                yield new RISCV64(imacfd);
            }
            default -> throw JVMCIError.unimplemented(archString);
        };
        return new TargetDescription(arch, true, 16, 4096, true);
    }
}
