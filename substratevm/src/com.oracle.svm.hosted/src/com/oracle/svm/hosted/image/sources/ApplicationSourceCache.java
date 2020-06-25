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

import static com.oracle.svm.hosted.image.sources.SourceCacheType.APPLICATION;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class ApplicationSourceCache extends SourceCache {

    @Override
    protected final SourceCacheType getType() {
        return APPLICATION;
    }

    @Override
    protected void trySourceRoot(Path sourceRoot, boolean fromClassPath) {
        try {
            Path sourcePath = sourceRoot;
            String fileNameString = sourcePath.getFileName().toString();
            if (fileNameString.endsWith(".jar") || fileNameString.endsWith(".zip")) {
                if (fromClassPath && fileNameString.endsWith(".jar")) {
                    /*
                     * application jar /path/to/xxx.jar should have sources /path/to/xxx-sources.jar
                     */
                    int length = fileNameString.length();
                    fileNameString = fileNameString.substring(0, length - 4) + "-sources.jar";
                }
                sourcePath = sourcePath.getParent().resolve(fileNameString);
                if (sourcePath.toFile().exists()) {
                    try {
                        FileSystem fileSystem = FileSystems.newFileSystem(sourcePath, (ClassLoader) null);
                        for (Path root : fileSystem.getRootDirectories()) {
                            srcRoots.add(root);
                        }
                    } catch (IOException | FileSystemNotFoundException ioe) {
                        /* ignore this entry */
                    }
                }
            } else {
                if (fromClassPath) {
                    /*
                     * for dir entries ending in classes or target/classes translate to a parallel
                     * src tree
                     */
                    if (sourcePath.endsWith("classes")) {
                        Path parent = sourcePath.getParent();
                        if (parent.endsWith("target")) {
                            parent = parent.getParent();
                        }
                        sourcePath = (parent.resolve("src"));
                    }
                }
                // try the path as provided
                File file = sourcePath.toFile();
                if (file.exists() && file.isDirectory()) {
                    // see if we have src/main/java or src/java
                    Path subPath = sourcePath.resolve("main").resolve("java");
                    file = subPath.toFile();
                    if (file.exists() && file.isDirectory()) {
                        sourcePath = subPath;
                    } else {
                        subPath = sourcePath.resolve("java");
                        file = subPath.toFile();
                        if (file.exists() && file.isDirectory()) {
                            sourcePath = subPath;
                        }
                    }
                    srcRoots.add(sourcePath);
                }
            }
        } catch (NullPointerException npe) {
            // do nothing
        }
    }
}
