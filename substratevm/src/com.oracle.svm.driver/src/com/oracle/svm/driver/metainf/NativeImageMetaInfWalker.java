/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.driver.metainf;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.oracle.svm.core.util.ClasspathUtils;

public class NativeImageMetaInfWalker {

    public static final String nativeImageMetaInf = "META-INF/native-image";
    public static final String nativeImagePropertiesFilename = "native-image.properties";

    public static class MetaInfWalkException extends Exception {
        private static final long serialVersionUID = 7185681203564964445L;

        public final Throwable cause;

        public MetaInfWalkException(String message, Throwable cause) {
            super(message);
            this.cause = cause;
        }
    }

    public static boolean walkMetaInfForCPEntry(Path classpathEntry, NativeImageMetaInfResourceProcessor metaInfProcessor) throws MetaInfWalkException {
        try {
            boolean removeFromClassPath = false;
            if (Files.isDirectory(classpathEntry)) {
                Path nativeImageMetaInfBase = classpathEntry.resolve(Paths.get(nativeImageMetaInf));
                removeFromClassPath = processNativeImageMetaInf(classpathEntry, nativeImageMetaInfBase, metaInfProcessor);
            } else {
                if (ClasspathUtils.isJar(classpathEntry)) {
                    URI jarFileURI = URI.create("jar:" + classpathEntry.toUri());
                    FileSystem probeJarFS;
                    try {
                        probeJarFS = FileSystems.newFileSystem(jarFileURI, Collections.emptyMap());
                    } catch (UnsupportedOperationException e) {
                        probeJarFS = null;
                        metaInfProcessor.showWarning(classpathEntry + " does not describe valid jar-file");
                    }
                    if (probeJarFS != null) {
                        try (FileSystem jarFS = probeJarFS) {
                            Path nativeImageMetaInfBase = jarFS.getPath("/" + nativeImageMetaInf);
                            removeFromClassPath = processNativeImageMetaInf(classpathEntry, nativeImageMetaInfBase, metaInfProcessor);
                        }
                    }
                }
            }
            return removeFromClassPath;
        } catch (IOException | FileSystemNotFoundException e) {
            throw new MetaInfWalkException("Invalid classpath entry " + classpathEntry, e);
        }
    }

    private static boolean processNativeImageMetaInf(Path classpathEntry, Path nativeImageMetaInfBase, NativeImageMetaInfResourceProcessor metaInfProcessor) throws MetaInfWalkException {
        boolean ignoreOnClassPath = false;
        if (Files.isDirectory(nativeImageMetaInfBase)) {
            for (MetaInfFileType fileType : MetaInfFileType.values()) {
                List<Path> nativeImageMetaInfFiles;
                try (Stream<Path> pathStream = Files.walk(nativeImageMetaInfBase)) {
                    nativeImageMetaInfFiles = pathStream.filter(p -> p.endsWith(fileType.fileName)).collect(Collectors.toList());
                } catch (IOException e) {
                    throw new MetaInfWalkException("Processing " + nativeImageMetaInfBase.toUri() + " failed.", e);
                }
                for (Path nativeImageMetaInfFile : nativeImageMetaInfFiles) {
                    boolean excluded = metaInfProcessor.isExcluded(nativeImageMetaInfFile, classpathEntry);
                    if (excluded) {
                        continue;
                    }

                    Path resourceRoot = nativeImageMetaInfBase.getParent().getParent();
                    metaInfProcessor.showVerboseMessage("Apply " + nativeImageMetaInfFile.toUri());
                    try {
                        ignoreOnClassPath = ignoreOnClassPath | metaInfProcessor.processMetaInfResource(classpathEntry, resourceRoot, nativeImageMetaInfFile, fileType);
                    } catch (Throwable err) {
                        throw new MetaInfWalkException("Processing " + nativeImageMetaInfFile.toUri() + " failed", err);
                    }
                }
            }
        }
        return ignoreOnClassPath;
    }
}
