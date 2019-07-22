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
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.option.HostedOptionKey;

public final class FileSystemProviderSupport {

    public static class Options {
        @Option(help = "Make all supported providers returned by FileSystemProvider.installedProviders() available at run time.")//
        public static final HostedOptionKey<Boolean> AddAllFileSystemProviders = new HostedOptionKey<>(true);
    }

    final List<FileSystemProvider> installedProvidersMutable;
    final List<FileSystemProvider> installedProvidersImmutable;

    @Platforms(Platform.HOSTED_ONLY.class)
    FileSystemProviderSupport(List<FileSystemProvider> installedProviders) {
        this.installedProvidersMutable = installedProviders;
        this.installedProvidersImmutable = Collections.unmodifiableList(installedProviders);
    }

    /**
     * Registers the provided {@link FileSystemProvider}. If a provider with the same
     * {@link FileSystemProvider#getScheme()} is already registered, the old provider is
     * overwritten.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void register(FileSystemProvider provider) {
        List<FileSystemProvider> installedProviders = ImageSingletons.lookup(FileSystemProviderSupport.class).installedProvidersMutable;

        String scheme = provider.getScheme();
        for (int i = 0; i < installedProviders.size(); i++) {
            /*
             * The "equalsIgnoreCase" check corresponds to the way the initial list of
             * installedProviders is built in
             * java.nio.file.spi.FileSystemProvider.loadInstalledProviders().
             */
            if (installedProviders.get(i).getScheme().equalsIgnoreCase(scheme)) {
                installedProviders.set(i, provider);
                return;
            }
        }
        installedProviders.add(provider);
    }

    /**
     * Removes the {@link FileSystemProvider} with the provided
     * {@link FileSystemProvider#getScheme() scheme} name.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void remove(String scheme) {
        List<FileSystemProvider> installedProviders = ImageSingletons.lookup(FileSystemProviderSupport.class).installedProvidersMutable;

        for (int i = 0; i < installedProviders.size(); i++) {
            /*
             * The "equalsIgnoreCase" check corresponds to the way the initial list of
             * installedProviders is built in
             * java.nio.file.spi.FileSystemProvider.loadInstalledProviders().
             */
            if (installedProviders.get(i).getScheme().equalsIgnoreCase(scheme)) {
                installedProviders.remove(i);
                return;
            }
        }
    }
}

@AutomaticFeature
final class FileSystemProviderFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        List<FileSystemProvider> installedProviders = new ArrayList<>();
        if (FileSystemProviderSupport.Options.AddAllFileSystemProviders.getValue()) {
            /*
             * The first invocation of FileSystemProvider.installedProviders() causes the default
             * provider to be initialized (if not already initialized) and loads any other installed
             * providers as described by the {@link FileSystems} class.
             *
             * Even though it is not specified, it seems as if the order of elements in the list
             * matters. At least the JDK implementation explicitly adds the "file" provider as the
             * first element of the list. Therefore, we preserve the order of the providers observed
             * during image generation.
             */
            installedProviders.addAll(FileSystemProvider.installedProviders());
        }
        ImageSingletons.add(FileSystemProviderSupport.class, new FileSystemProviderSupport(installedProviders));

        /* Currently we do not support access to Java modules (jimage/jrtfs access) in images */
        FileSystemProviderSupport.remove("jrt");
    }
}

@TargetClass(java.nio.file.spi.FileSystemProvider.class)
final class Target_java_nio_file_spi_FileSystemProvider {
    @Substitute
    public static List<FileSystemProvider> installedProviders() {
        return ImageSingletons.lookup(FileSystemProviderSupport.class).installedProvidersImmutable;
    }
}

@TargetClass(className = "jdk.internal.jrtfs.JrtFileSystemProvider", onlyWith = JDK11OrLater.class)
@Delete
final class Target_jdk_internal_jrtfs_JrtFileSystemProvider {
}
