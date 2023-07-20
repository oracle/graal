/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c.libc;

import java.util.ServiceLoader;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.libc.LibCBase;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.UserError;

@AutomaticallyRegisteredFeature
public class HostedLibCFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return HostedLibCBase.isPlatformEquivalent(Platform.LINUX.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        String targetLibC = SubstrateOptions.UseLibC.getValue();
        ServiceLoader<HostedLibCBase> loader = ServiceLoader.load(HostedLibCBase.class);
        for (HostedLibCBase libc : loader) {
            if (libc.getName().equals(targetLibC)) {
                libc.checkIfLibCSupported();
                ImageSingletons.add(LibCBase.class, libc);
                return;
            }
        }
        throw UserError.abort("Unknown libc %s selected. Please use one of the available libc implementations.", targetLibC);
    }
}
