/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.CodeSynchronizationOperations;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.code.AbstractRuntimeCodeInstaller.RuntimeCodeInstallerPlatformHelper;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.thread.VMThreads;

@AutomaticFeature
@Platforms(Platform.AARCH64.class)
class AArch64RuntimeCodeInstallerPlatformHelperFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(RuntimeCodeInstallerPlatformHelper.class, new AArch64RuntimeCodeInstallerPlatformHelper());
    }
}

public class AArch64RuntimeCodeInstallerPlatformHelper implements RuntimeCodeInstallerPlatformHelper {

    @Override
    public void performCodeSynchronization(CodeInfo codeInfo) {
        CodeSynchronizationOperations.clearCache(CodeInfoAccess.getCodeStart(codeInfo).rawValue(), CodeInfoAccess.getCodeSize(codeInfo).rawValue());
        VMThreads.ActionOnTransitionToJavaSupport.requestAllThreadsSynchronizeCode();
    }
}
