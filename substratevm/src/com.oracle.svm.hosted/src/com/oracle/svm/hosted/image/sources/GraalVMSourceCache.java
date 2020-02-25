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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.oracle.svm.hosted.image.sources.SourceManager.GRAALVM_SRC_PACKAGE_PREFIXES;
public class GraalVMSourceCache extends SourceCache {
    /**
     * create a GraalVM source cache
     */
    protected GraalVMSourceCache() {
        super(SourceCache.GRAALVM_CACHE_KEY);
        initSrcRoots();
    }

    private static final String JAVA_CLASSPATH_PROP = "java.class.path";

    private void initSrcRoots() {
        String javaClassPath = System.getProperty(JAVA_CLASSPATH_PROP);
        assert javaClassPath != null;
        String[] classPathEntries = javaClassPath.split(File.pathSeparator);
        for (String classPathEntry : classPathEntries) {
            Path entryPath = Paths.get(classPathEntry);
            String fileNameString = entryPath.getFileName().toString();
            if (fileNameString.endsWith(".jar")) {
                // GraalVM jar /path/to/xxx.jar should have
                // sources /path/to/xxx.src.zip.jar
                int length = fileNameString.length();
                String srcFileNameString = fileNameString.substring(0, length - 3) + "src.zip";
                Path srcPath = entryPath.getParent().resolve(srcFileNameString);
                if (srcPath.toFile().exists()) {
                    try {
                        FileSystem fileSystem = FileSystems.newFileSystem(srcPath, null);
                        for (Path root : fileSystem.getRootDirectories()) {
                            if (filterSrcRoot(root)) {
                                srcRoots.add(root);
                            }
                        }
                    } catch (IOException ioe) {
                        /* ignore this entry */
                    } catch (FileSystemNotFoundException fnfe) {
                        /* ignore this entry */
                    }
                }
            } else  {
                /* graal classpath dir entries should have a src and/or src_gen subdirectory */
                Path srcPath = entryPath.resolve("src");
                if (filterSrcRoot(srcPath)) {
                    srcRoots.add(srcPath);
                }
                srcPath = entryPath.resolve("src_gen");
                if (filterSrcRoot(srcPath)) {
                    srcRoots.add(srcPath);
                }
            }
        }
    }
    /**
     * Ensure that the supplied root dir contains
     * at least one  subdirectory that matches one
     * of the expected Graal package dir hierarchies.
     *
     * @param root A root path under which to locate
     * the desired subdirectory
     * @return true if a
     */
    private boolean filterSrcRoot(Path root) {
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
