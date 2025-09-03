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

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

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
}
