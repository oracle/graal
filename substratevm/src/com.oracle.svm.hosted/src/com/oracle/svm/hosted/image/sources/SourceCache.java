/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
/**
 * An abstract cache manager for some subspace of the
 * JDK, GraalVM or application source file space. This class
 * implements core behaviours that manage a cache of source
 * files in a specific subdirectory of the local sources
 * directory. It allows source files to be located
 * when present in the local cache or cached when not
 * already present. Subclasses are responsible for providing
 * behaviours that identify an original source for addition
 * to the cache and for verifying that a cached file is not
 * out of date with respect to its original.
 */

public abstract class SourceCache {

    /*
     * properties needed to locate relevant JDK and app source roots
     */
    protected static final String JAVA_CLASSPATH_PROP = "java.class.path";
    protected static final String JAVA_HOME_PROP = "java.home";
    protected static final String JAVA_SPEC_VERSION_PROP = "java.specification.version";
    /**
     * A list of root directories which may contain source files
     * from which this cache can be populated
     */
    protected List<Path> srcRoots;

    /**
     * Create some flavour of source cache.
     */
    protected SourceCache() {
        basePath = Paths.get(SOURCE_CACHE_ROOT_DIR).resolve(getType().getSubdir());
        srcRoots = new ArrayList<>();
    }

    /**
     * Idenitfy the specific type of this source cache
     * @return
     */
    protected abstract SourceCacheType getType();

    /**
     * A local directory serving as the root for all
     * source trees maintained by the different
     * available source caches.
     */
    private static final String SOURCE_CACHE_ROOT_DIR = "sources";
    /**
     * The top level path relative to the root directory
     * under which files belonging to this specific cache
     * are located.
     */
    private final Path basePath;
    /**
     * Cache the source file identified by the supplied prototype
     * path if a legitimate candidate for inclusion in this cache
     * can be identified and is not yet included in the cache or
     * alternatively identify and validate any existing candidate
     * cache entry to ensure it is not out of date refreshing it
     * if need be.
     *
     * @param filePath a prototype path for a file to be included
     * in the cache derived from the name of some associated class.
     * @return a path identifying the cached file or null
     * if the candidate cannot be found.
     */
    public Path resolve(Path filePath) {
        File cachedFile = findCandidate(filePath);
        if (cachedFile == null) {
            return tryCacheFile(filePath);
        } else {
            return checkCacheFile(filePath);
        }
    }

    /**
     * Given a prototype path for a file to be resolved
     * return a File identifying a cached candidate for
     * for that Path or null if no cached candidate exists.
     * @param filePath  a prototype path for a file to be included
     * in the cache derived from the name of some associated class.
     * @return a File identifying a cached candidate or null.
     */
    public File findCandidate(Path filePath) {
        /*
         * JDK source candidates are stored in the src.zip file
         * using the path we are being asked for. A cached version
         * should exist under this cache's root using that same
         * path.
         */
        File file = cachedFile(filePath);
        if (file.exists()) {
            return file;
        }
        return null;
    }
    public Path tryCacheFile(Path filePath) {
        for (Path root : srcRoots) {
            Path targetPath = cachedPath(filePath);
            Path sourcePath = extendPath(root, filePath);
            try {
                if (checkSourcePath(sourcePath)) {
                    ensureTargetDirs(targetPath.getParent());
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    // return the original filePath
                    // we don't want the sources/jdk prefix to go into the debuginfo
                    return filePath;
                }
            } catch (IOException e) {
            }
        }
        return null;
    }
    public Path checkCacheFile(Path filePath) {
        for (Path root : srcRoots) {
            Path targetPath = cachedPath(filePath);
            Path sourcePath = extendPath(root, filePath);
            try {
                if (checkSourcePath(sourcePath)) {
                    FileTime sourceTime = Files.getLastModifiedTime(sourcePath);
                    FileTime destTime = Files.getLastModifiedTime(targetPath);
                    if (destTime.compareTo(sourceTime) < 0) {
                        try {
                            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                        } catch (IOException e) {
                            return null;
                        }
                    }
                    return filePath;
                } else {
                    /* delete the target file as it is out of date */
                    targetPath.toFile().delete();
                }
            } catch (IOException e) {
                // hmm last modified time blew up?
                return tryCacheFile(filePath);
            }
        }
        return null;
    }
    /**
     * Create and intialize the source cache used to locate and cache
     * sources of a given type as determined by the supplied key.
     * @param type an enum identifying both the type of Java sources
     * cached by the returned cache and the subdir of the cached
     * source subdirectory in which those sources are located.
     * @return the desired source cache.
     */
    public static SourceCache createSourceCache(SourceCacheType type) {
        SourceCache sourceCache = null;
        switch (type) {
            case JDK:
                sourceCache = new JDKSourceCache();
                break;
            case GRAALVM:
                sourceCache = new GraalVMSourceCache();
                break;
            case APPLICATION:
                sourceCache = new ApplicationSourceCache();
                break;
            default:
                assert false;
        }
        return sourceCache;
    }
    /**
     * Extend a root path form one file system using a path potentially derived
     * from another file system by converting he latter to a text string and
     * replacing the file separator if necessary.
     * @param root the path to be extended
     * @param filePath the subpath to extend it with
     * @return the extended path
     */
    protected Path extendPath(Path root, Path filePath) {
        String filePathString = filePath.toString();
        String fileSeparator = filePath.getFileSystem().getSeparator();
        String newSeparator = root.getFileSystem().getSeparator();
        if (!fileSeparator.equals(newSeparator)) {
            filePathString = filePathString.replace(fileSeparator, newSeparator);
        }
        return root.resolve(filePathString);
    }

    /**
     * convert a potential resolved candidate path to
     * the corresponding local Path in this cache.
     * @param candidate a resolved candidate path for
     * some given resolution request
     * @return the corresponding local Path
     */
    protected Path cachedPath(Path candidate) {
        return basePath.resolve(candidate);
    }
    /**
     * convert a potential resolved candidate path to
     * the corresponding local File in this cache.
     * @param candidate a resolved candidate path for
     * some given resolution request
     * @return the corresponding local File
     */
    protected File cachedFile(Path candidate) {
        return cachedPath(candidate).toFile();
    }
    /**
     * indicate whether a source path identifies a fie in the associated file system
     * @param sourcePath
     * @return true if the path identifies a file or false if no such file can be found
     * @throws IOException if there is some error in resolving the path
     */
    private boolean checkSourcePath(Path sourcePath) throws IOException {
        return Files.isRegularFile(sourcePath);
    }
    /**
     * ensure the directory hierarchy for a path exists
     * creating any missing directories if needed
     * @param targetDir a path to the desired directory
     * @throws IOException if it is not possible to create
     * one or more directories in the path
     */
    private void ensureTargetDirs(Path targetDir) throws IOException {
        if (targetDir != null) {
            File targetFile = targetDir.toFile();
            if (!targetFile.exists()) {
                targetDir.toFile().mkdirs();
            }
        }
    }
}
