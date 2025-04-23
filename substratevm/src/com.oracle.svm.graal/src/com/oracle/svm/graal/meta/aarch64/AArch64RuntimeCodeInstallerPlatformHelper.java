/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Arm Limited. All rights reserved.
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
package com.oracle.svm.graal.meta.aarch64;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.CodeSynchronizationOperations;
import com.oracle.svm.core.code.AbstractRuntimeCodeInstaller.RuntimeCodeInstallerPlatformHelper;
import com.oracle.svm.core.code.RuntimeCodeCache;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.UserError;

@AutomaticallyRegisteredImageSingleton(RuntimeCodeInstallerPlatformHelper.class)
@Platforms(Platform.AARCH64.class)
public class AArch64RuntimeCodeInstallerPlatformHelper implements RuntimeCodeInstallerPlatformHelper {
    AArch64RuntimeCodeInstallerPlatformHelper() {
        RuntimeOptionKey<Boolean> writeableCodeOption = RuntimeCodeCache.Options.WriteableCodeCache;
        if (writeableCodeOption.getValue()) {
            throw UserError.abort("Enabling %s is not supported on this platform.", writeableCodeOption.getName());
        }
    }

    /**
     * According to the <a href="https://developer.arm.com/documentation/ddi0487/latest">ARM
     * Architecture Reference Manual</a> (see Section B2.2.5), it is necessary to flush the
     * instruction cache and to issue an ISB (instruction synchronization barrier) if new code was
     * made executable.
     */
    @Override
    public boolean needsInstructionCacheSynchronization() {
        return true;
    }

    @Override
    public void performCodeSynchronization(long codeStart, long codeSize) {
        CodeSynchronizationOperations.clearCache(codeStart, codeSize);
        VMThreads.ActionOnTransitionToJavaSupport.requestAllThreadsSynchronizeCode();
    }
}
