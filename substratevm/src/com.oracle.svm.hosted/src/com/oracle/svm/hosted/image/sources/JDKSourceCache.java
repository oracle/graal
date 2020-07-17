/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.hosted.image.sources;

import static com.oracle.svm.hosted.image.sources.SourceCacheType.JDK;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.oracle.svm.core.util.VMError;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

public class JDKSourceCache extends SourceCache {

    @Override
    protected final SourceCacheType getType() {
        return JDK;
    }

    /*
     * properties needed to locate relevant JDK and app source roots
     */
    private static final String JAVA_HOME_PROP = "java.home";

    /**
     * Modules needing special case root processing.
     */
    private static final String[] specialRootModules = {
                    "jdk.internal.vm.ci",
                    "jdk.internal.vm.compiler",
    };

    /**
     * Extra root directories for files in the jdk.internal.vm.ci/compiler modules.
     */
    private HashMap<String, List<Path>> specialSrcRoots;

    @Override
    protected void initSrcRoots() {
        String javaHome = System.getProperty(JAVA_HOME_PROP);
        assert javaHome != null;
        Path javaHomePath = Paths.get("", javaHome);
        Path srcZipPath;
        if (JavaVersionUtil.JAVA_SPEC < 11) {
            Path srcZipDir = javaHomePath.getParent();
            if (srcZipDir == null) {
                VMError.shouldNotReachHere("Cannot resolve parent directory of " + javaHome);
            }
            srcZipPath = srcZipDir.resolve("src.zip");
        } else {
            srcZipPath = javaHomePath.resolve("lib").resolve("src.zip");
        }
        if (srcZipPath.toFile().exists()) {
            try {
                FileSystem srcFileSystem = FileSystems.newFileSystem(srcZipPath, (ClassLoader) null);
                for (Path root : srcFileSystem.getRootDirectories()) {
                    srcRoots.add(root);
                }
                specialSrcRoots = new HashMap<>();
                if (JavaVersionUtil.JAVA_SPEC >= 11) {
                    for (Path root : srcFileSystem.getRootDirectories()) {
                        // add dirs named "src" as extra roots for special modules
                        for (String specialRootModule : specialRootModules) {
                            ArrayList<Path> rootsList = new ArrayList<>();
                            specialSrcRoots.put(specialRootModule, rootsList);
                            Path specialModuleRoot = root.resolve(specialRootModule);
                            Files.find(specialModuleRoot, 2, (path, attributes) -> path.endsWith("src")).forEach(rootsList::add);
                        }
                    }
                }
            } catch (IOException | FileSystemNotFoundException ioe) {
                /* ignore this entry */
            }
        }
    }

    @Override
    public Path checkCacheFile(Path filePath) {
        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            for (String specialRootModule : specialRootModules) {
                if (filePath.startsWith(specialRootModule)) {
                    // handle this module specially as it has intermediate dirs
                    Path targetPath = cachedPath(filePath);
                    Path filePathNoModule = filePath.subpath(1, filePath.getNameCount());
                    for (Path srcRoot : specialSrcRoots.get(specialRootModule)) {
                        String srcRootGroup = srcRoot.subpath(1, 2).toString().replace(".", filePath.getFileSystem().getSeparator());
                        if (filePathNoModule.toString().startsWith(srcRootGroup)) {
                            Path sourcePath = extendPath(srcRoot, filePathNoModule);
                            try {
                                if (tryCheckCacheFile(sourcePath, targetPath)) {
                                    return filePath;
                                }
                            } catch (IOException e) {
                                /* delete the target file as it is invalid */
                                targetPath.toFile().delete();
                                /* have another go at caching it */
                                return tryCacheFile(filePath);
                            }
                        }
                    }
                }
            }
        }
        // try the usual check instead
        return super.checkCacheFile(filePath);
    }

    @Override
    public Path tryCacheFile(Path filePath) {
        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            for (String specialRootModule : specialRootModules) {
                if (filePath.startsWith(specialRootModule)) {
                    // handle this module specially as it has intermediate dirs
                    Path targetPath = cachedPath(filePath);
                    Path filePathNoModule = filePath.subpath(1, filePath.getNameCount());
                    for (Path srcRoot : specialSrcRoots.get(specialRootModule)) {
                        String srcRootGroup = srcRoot.subpath(1, 2).toString().replace(".", filePath.getFileSystem().getSeparator());
                        if (filePathNoModule.toString().startsWith(srcRootGroup)) {
                            Path sourcePath = extendPath(srcRoot, filePathNoModule);
                            if (tryCacheFileFromRoot(sourcePath, targetPath)) {
                                return filePath;
                            }
                        }
                    }
                }
            }
        }
        // try the usual lookup instead
        return super.tryCacheFile(filePath);
    }

    @Override
    protected void trySourceRoot(Path sourceRoot, boolean fromClassPath) {
        VMError.shouldNotReachHere();
    }
}
