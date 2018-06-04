/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@AutomaticFeature
public final class FileSystemProviderFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(FileSystemProviderSupport.class, new FileSystemProviderSupport());
        /*
         * The first invocation of FileSystemProvider.installedProviders() causes the default
         * provider to be initialized (if not already initialized) and loads any other installed
         * providers as described by the {@link FileSystems} class.
         */
        List<FileSystemProvider> providers = FileSystemProvider.installedProviders();
        providers.forEach(p -> addFileSystemProvider(p));
    }

    public static void addFileSystemProvider(FileSystemProvider fileSystemProvider) {
        Map<String, FileSystemProvider> fileSystemProviders = ImageSingletons.lookup(FileSystemProviderSupport.class).fileSystemProviders;
        fileSystemProviders.put(fileSystemProvider.getScheme(), fileSystemProvider);
    }

}

final class FileSystemProviderSupport {
    final Map<String, FileSystemProvider> fileSystemProviders;

    @Platforms(Platform.HOSTED_ONLY.class)
    FileSystemProviderSupport() {
        fileSystemProviders = new HashMap<>();
    }
}

@TargetClass(java.nio.file.spi.FileSystemProvider.class)
final class Target_java_nio_file_spi_FileSystemProvider {
    @Substitute
    public static List<FileSystemProvider> installedProviders() {
        return new ArrayList<>(ImageSingletons.lookup(FileSystemProviderSupport.class).fileSystemProviders.values());
    }
}
