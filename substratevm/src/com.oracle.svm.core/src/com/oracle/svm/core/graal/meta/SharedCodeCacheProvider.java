/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.meta;

import static com.oracle.svm.core.util.VMError.intentionallyUnimplemented;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.SpeculationLog;

public abstract class SharedCodeCacheProvider implements CodeCacheProvider {
    protected final TargetDescription target;
    protected final RegisterConfig registerConfig;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SharedCodeCacheProvider(TargetDescription target, RegisterConfig registerConfig) {
        this.target = target;
        this.registerConfig = registerConfig;
    }

    @Override
    public void invalidateInstalledCode(InstalledCode installedCode) {
        throw intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public long getMaxCallTargetOffset(long address) {
        throw intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public boolean shouldDebugNonSafepoints() {
        return false;
    }

    @Override
    public SpeculationLog createSpeculationLog() {
        throw intentionallyUnimplemented(); // ExcludeFromJacocoGeneratedReport
    }

    @Override
    public RegisterConfig getRegisterConfig() {
        return registerConfig;
    }

    @Override
    public int getMinimumOutgoingSize() {
        return 0;
    }

    @Override
    public TargetDescription getTarget() {
        return target;
    }
}
