/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk;

import java.nio.file.spi.FileSystemProvider;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.FutureDefaultsOptions;
import com.oracle.svm.core.jdk.buildtimeinit.FileSystemProviderBuildTimeInitSupport;
import com.oracle.svm.core.util.VMError;

/**
 * Legacy delegate for backwards compatibility. It should go away eventually (GR-65809).
 *
 * @see com.oracle.svm.core.jdk.buildtimeinit.FileSystemProviderBuildTimeInitSupport
 */
@SuppressWarnings("unused")
public final class FileSystemProviderSupport {

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void register(FileSystemProvider provider) {
        VMError.guarantee(!FutureDefaultsOptions.isJDKInitializedAtRunTime(), "No need to register FileSystemProvider if the JDK is initialized at run time.");
        FileSystemProviderBuildTimeInitSupport.register(provider);
    }

}
