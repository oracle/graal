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

import com.oracle.svm.core.util.ClasspathUtils;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    public static void walkMetaInfForCPEntry(Path classpathEntry, NativeImageMetaInfResourceProcessor metaInfProcessor) throws MetaInfWalkException {
        try {
            if (Files.isDirectory(classpathEntry)) {
                Path nativeImageMetaInfBase = classpathEntry.resolve(Paths.get(nativeImageMetaInf));
                processNativeImageMetaInf(classpathEntry, nativeImageMetaInfBase, metaInfProcessor);
            } else {
                List<Path> jarFileMatches = Collections.emptyList();
                if (classpathEntry.endsWith(ClasspathUtils.cpWildcardSubstitute)) {
                    try {
                        jarFileMatches = Files.list(classpathEntry.getParent())
                                        .filter(ClasspathUtils::isJar)
                                        .collect(Collectors.toList());
                    } catch (NoSuchFileException e) {
                        /* Fallthrough */
                    }
                } else if (ClasspathUtils.isJar(classpathEntry)) {
                    jarFileMatches = Collections.singletonList(classpathEntry);
                }

                for (Path jarFile : jarFileMatches) {
                    URI jarFileURI = URI.create("jar:" + jarFile.toUri());
                    FileSystem probeJarFS;
                    try {
                        probeJarFS = FileSystems.newFileSystem(jarFileURI, Collections.emptyMap());
                    } catch (UnsupportedOperationException e) {
                        probeJarFS = null;
                        metaInfProcessor.showWarning(ClasspathUtils.classpathToString(classpathEntry) + " does not describe valid jarfile" + (jarFileMatches.size() > 1 ? "s" : ""));
                    }
                    if (probeJarFS != null) {
                        try (FileSystem jarFS = probeJarFS) {
                            Path nativeImageMetaInfBase = jarFS.getPath("/" + nativeImageMetaInf);
                            processNativeImageMetaInf(jarFile, nativeImageMetaInfBase, metaInfProcessor);
                        }
                    }
                }
            }
        } catch (IOException | FileSystemNotFoundException e) {
            throw new MetaInfWalkException("Invalid classpath entry " + ClasspathUtils.classpathToString(classpathEntry), e);
        }
    }

    private static void processNativeImageMetaInf(Path classpathEntry, Path nativeImageMetaInfBase, NativeImageMetaInfResourceProcessor metaInfProcessor) throws MetaInfWalkException {
        if (Files.isDirectory(nativeImageMetaInfBase)) {
            for (MetaInfFileType fileType : MetaInfFileType.values()) {
                List<Path> nativeImageMetaInfFiles;
                try {
                    nativeImageMetaInfFiles = Files.walk(nativeImageMetaInfBase)
                                    .filter(p -> p.endsWith(fileType.fileName))
                                    .collect(Collectors.toList());
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
                        metaInfProcessor.processMetaInfResource(classpathEntry, resourceRoot, nativeImageMetaInfFile, fileType);
                    } catch (Throwable err) {
                        throw new MetaInfWalkException("Processing " + nativeImageMetaInfFile.toUri() + " failed", err);
                    }
                }
            }
        }
    }
}
