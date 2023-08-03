/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jdk;

import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.ImageSingletons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public final class JDKConfigFiles {

    public static JDKConfigFiles singleton() {
        return ImageSingletons.lookup(JDKConfigFiles.class);
    }

    final Set<String> configFiles = Collections.newSetFromMap(new ConcurrentHashMap<>());

    JDKConfigFiles() {
    }

    public void register(String configFile) {
        if (configFile.startsWith("/")) {
            configFiles.add(configFile.substring(1));
        } else {
            configFiles.add(configFile);
        }
    }
}

@AutomaticallyRegisteredFeature
final class JDKConfigFilesFeature implements InternalFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(JDKConfigFiles.class, new JDKConfigFiles());
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        Path jdkHomeDir = Path.of(System.getProperty("java.home")).normalize();
        for (String configFile : new TreeSet<>(JDKConfigFiles.singleton().configFiles)) {
            Path jdkConfigPath = jdkHomeDir.resolve(configFile).normalize();
            VMError.guarantee(jdkConfigPath.startsWith(jdkHomeDir));
            Path imageConfigPath = access.getImagePath().resolveSibling(configFile);
            try {
                Files.createDirectories(imageConfigPath.getParent());
                Files.copy(jdkConfigPath, imageConfigPath, REPLACE_EXISTING);
                BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.JDK_CONFIG_FILE, imageConfigPath);
            } catch (IOException e) {
                VMError.shouldNotReachHere(e);
            }
        }
    }
}
