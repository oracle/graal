/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.resident;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionType;

import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.OS;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;

/**
 * Copies {@code lib:svmjdwp} from the GraalVM native libraries, if it exists, to the native-image
 * destination directory.
 *
 * <p>
 * This is an optional feature to improve usability; so that native images compiled with
 * {@link JDWPOptions#JDWP} support are ready to be debugged right out-of-the-box.
 *
 * <p>
 * <strong>Disable with {@link Options#CopyNativeJDWPLibrary -H:-CopyNativeJDWPLibrary} to build
 * {@code lib:svmjdwp} with JDWP support, otherwise it gets overwritten.</strong>
 */
@AutomaticallyRegisteredFeature
final class CopyNativeJDWPLibraryFeature implements InternalFeature {

    public static class Options {
        @Option(help = "Copy lib:svmjdwp, if available, to the native image destination directory", type = OptionType.Expert)//
        public static final HostedOptionKey<Boolean> CopyNativeJDWPLibrary = new HostedOptionKey<>(true);
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JDWPOptions.JDWP.getValue() && Options.CopyNativeJDWPLibrary.getValue();
    }

    @Override
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "imagePath.getParent() is never null")
    public void afterImageWrite(AfterImageWriteAccess access) {
        Path javaHome = Paths.get(System.getProperty("java.home"));
        String svmjdwpLibraryName = System.mapLibraryName("svmjdwp");
        Path svmjdwpLibraryPath = javaHome
                        .resolve(OS.WINDOWS.isCurrent() ? "bin" : "lib")
                        .resolve(svmjdwpLibraryName);

        if (Files.isRegularFile(svmjdwpLibraryPath)) {
            Path imagePath = access.getImagePath();
            Path targetPath = imagePath.getParent().resolve(svmjdwpLibraryName);
            try {
                Files.copy(svmjdwpLibraryPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.SHARED_LIBRARY, targetPath);
        }
    }
}
