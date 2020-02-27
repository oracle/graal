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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ApplicationSourceCache extends SourceCache {
    /**
     * Create an application source cache.
     */
    protected ApplicationSourceCache() {
        super(SourceCache.APPLICATION_CACHE_KEY);
        initSrcRoots();
    }

    private void initSrcRoots() {
        String javaClassPath = System.getProperty(JAVA_CLASSPATH_PROP);
        assert javaClassPath != null;
        String[] classPathEntries = javaClassPath.split(File.pathSeparator);
        /* add dirs or jars found in the classpath */
        for (String classPathEntry : classPathEntries) {
            Path entryPath = Paths.get(classPathEntry);
            String fileNameString = entryPath.getFileName().toString();
            if (fileNameString.endsWith(".jar")) {
                // application jar /path/to/xxx.jar should have
                // sources /path/to/xxx-sources.jar
                int length = fileNameString.length();
                String srcFileNameString = fileNameString.substring(0, length - 4) + "-sources.zip";
                Path srcPath = entryPath.getParent().resolve(srcFileNameString);
                if (srcPath.toFile().exists()) {
                    try {
                        FileSystem fileSystem = FileSystems.newFileSystem(srcPath, null);
                        for (Path root : fileSystem.getRootDirectories()) {
                            srcRoots.add(root);
                        }
                    } catch (IOException ioe) {
                        /* ignore this entry */
                    } catch (FileSystemNotFoundException fnfe) {
                        /* ignore this entry */
                    }
                }
            } else  {
                /*
                * for dir entries ending in classes or target/classes
                * look for a parallel src tree
                */
                if (entryPath.endsWith("classes")) {
                    Path parent = entryPath.getParent();
                    if (parent.endsWith("target")) {
                        parent = parent.getParent();
                    }
                    Path srcPath = (parent.resolve("src"));
                    File file = srcPath.toFile();
                    if (file.exists() && file.isDirectory()) {
                        srcRoots.add(srcPath);
                    }
                }
            }
        }
        /* add the current working directory as a path of last resort */
        srcRoots.add(Paths.get("."));
    }
}
