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

import static com.oracle.svm.hosted.image.sources.SourceCacheType.GRAALVM;
import static com.oracle.svm.hosted.image.sources.SourceManager.GRAALVM_SRC_PACKAGE_PREFIXES;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class GraalVMSourceCache extends SourceCache {

    @Override
    protected final SourceCacheType getType() {
        return GRAALVM;
    }

    @Override
    protected void trySourceRoot(Path sourceRoot, boolean fromClassPath) {
        try {
            Path sourcePath = sourceRoot;
            String fileNameString = sourcePath.getFileName().toString();
            if (fileNameString.endsWith(".jar") || fileNameString.endsWith(".src.zip")) {
                if (fromClassPath && fileNameString.endsWith(".jar")) {
                    /*
                     * GraalVM jar /path/to/xxx.jar in classpath should have sources
                     * /path/to/xxx.src.zip
                     */
                    int length = fileNameString.length();
                    fileNameString = fileNameString.substring(0, length - 3) + "src.zip";
                }
                Path srcPath = sourcePath.getParent().resolve(fileNameString);
                if (srcPath.toFile().exists()) {
                    try {
                        FileSystem fileSystem = FileSystems.newFileSystem(srcPath, (ClassLoader) null);
                        for (Path root : fileSystem.getRootDirectories()) {
                            if (filterSrcRoot(root)) {
                                srcRoots.add(root);
                            }
                        }
                    } catch (IOException | FileSystemNotFoundException ioe) {
                        /* ignore this entry */
                    }
                }
            } else {
                if (fromClassPath) {
                    /* graal classpath dir entries should have a src and/or src_gen subdirectory */
                    Path srcPath = sourcePath.resolve("src");
                    if (filterSrcRoot(srcPath)) {
                        srcRoots.add(srcPath);
                    }
                    srcPath = sourcePath.resolve("src_gen");
                    if (filterSrcRoot(srcPath)) {
                        srcRoots.add(srcPath);
                    }
                } else {
                    // try the path as provided
                    if (filterSrcRoot(sourcePath)) {
                        srcRoots.add(sourcePath);
                    }
                }
            }
        } catch (NullPointerException npe) {
            // do nothing
        }
    }

    /**
     * Ensure that the supplied root dir contains at least one subdirectory that matches one of the
     * expected Graal package dir hierarchies.
     *
     * @param root A root path under which to locate the desired subdirectory
     * @return true if a
     */
    private static boolean filterSrcRoot(Path root) {
        String separator = root.getFileSystem().getSeparator();

        /* if any of the graal paths exist accept this root */
        for (String prefix : GRAALVM_SRC_PACKAGE_PREFIXES) {
            String subDir = prefix.replaceAll("\\.", separator);
            if (Files.isDirectory(root.resolve(subDir))) {
                return true;
            }
        }

        return false;
    }
}
