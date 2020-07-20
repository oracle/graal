/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
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

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;

/**
 * An abstract cache manager for some subspace of the JDK, GraalVM or application source file space.
 * This class implements core behaviours that manage a cache of source files in a specific
 * subdirectory of the local sources directory. It allows source files to be located when present in
 * the local cache or cached when not already present. Subclasses are responsible for providing
 * behaviours that identify an original source for addition to the cache and for verifying that a
 * cached file is not out of date with respect to its original.
 */

public abstract class SourceCache {

    /**
     * A list of all entries in the classpath used by the native image classloader.
     */
    protected static final List<Path> classPathEntries = new ArrayList<>();
    /**
     * A list of all entries in the classpath used by the native image classloader.
     */
    protected static final List<String> sourcePathEntries = new ArrayList<>();

    /**
     * A list of root directories which may contain source files from which this cache can be
     * populated.
     */
    protected List<Path> srcRoots;

    /**
     * Create some flavour of source cache.
     */
    protected SourceCache() {
        basePath = SubstrateOptions.getDebugInfoSourceCacheRoot().resolve(getType().getSubdir());
        srcRoots = new ArrayList<>();
        initSrcRoots();
    }

    /** Add dirs or jars found in the classpath. */
    protected void initSrcRoots() {
        for (Path classPathEntry : classPathEntries) {
            trySourceRoot(classPathEntry, true);
        }
        for (String sourcePathEntry : sourcePathEntries) {
            trySourceRoot(sourcePathEntry, false);
        }
    }

    /**
     * Implementing this method allows to add to the {@link SourceCache#srcRoots} based on the given
     * sourceRoot path (jar-file or directory). Different subclasses might implement different
     * strategies how to extract {@link SourceCache#srcRoots} entries from the given sourceRoot.
     * 
     * @param sourceRoot path {@link SourceCache#srcRoots} entries should be added for.
     * @param fromClassPath true, if the given sourceRoot is a classpath entry.
     */
    protected abstract void trySourceRoot(Path sourceRoot, boolean fromClassPath);

    /**
     * Identify the specific type of this source cache.
     * 
     * @return the source cache type
     */
    protected abstract SourceCacheType getType();

    /**
     * The top level path relative to the root directory under which files belonging to this
     * specific cache are located.
     */
    private final Path basePath;

    /**
     * Fallback for trySourceRoot that accepts Stings instead of Paths.
     */
    private void trySourceRoot(String sourceRoot, boolean fromClassPath) {
        trySourceRoot(Paths.get(sourceRoot), fromClassPath);
    }

    /**
     * Cache the source file identified by the supplied prototype path if a legitimate candidate for
     * inclusion in this cache can be identified and is not yet included in the cache or
     * alternatively identify and validate any existing candidate cache entry to ensure it is not
     * out of date refreshing it if need be.
     *
     * @param filePath a prototype path for a file to be included in the cache derived from the name
     *            of some associated class.
     * @return a path identifying the cached file or null if the candidate cannot be found.
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
     * Given a prototype path for a file to be resolved return a File identifying a cached candidate
     * for for that Path or null if no cached candidate exists.
     * 
     * @param filePath a prototype path for a file to be included in the cache derived from the name
     *            of some associated class.
     * @return a File identifying a cached candidate or null.
     */
    public File findCandidate(Path filePath) {
        /*
         * JDK source candidates are stored in the src.zip file using the path we are being asked
         * for. A cached version should exist under this cache's root using that same path.
         */
        File file = cachedFile(filePath);
        if (file.exists()) {
            return file;
        }
        return null;
    }

    /**
     * Attempt to copy a source file from one of this cache's source roots to the local sources
     * directory storing it in the subdirectory that belongs to this cache.
     * 
     * @param filePath a path appended to each of the cache's source roots in turn until an
     *            acceptable source file is found and copied to the local source directory.
     * @return the supplied path if the file has been located and copied to the local sources
     *         directory or null if it was not found or the copy failed.
     */
    protected Path tryCacheFile(Path filePath) {
        for (Path root : srcRoots) {
            Path targetPath = cachedPath(filePath);
            Path sourcePath = extendPath(root, filePath);
            if (tryCacheFileFromRoot(sourcePath, targetPath)) {
                // return the original filePath
                // we don't want the sources/jdk prefix to go into the debuginfo
                return filePath;
            }
        }
        return null;
    }

    protected boolean tryCacheFileFromRoot(Path sourcePath, Path targetPath) {
        try {
            if (checkSourcePath(sourcePath)) {
                ensureTargetDirs(targetPath.getParent());
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return true;
            }
        } catch (IOException ioe) {
        }
        return false;
    }

    /**
     * Check whether the copy of a given source file in the local source cache is up to date with
     * respect to any original located in this cache's and if not copy the original to the
     * subdirectory that belongs to this cache.
     * 
     * @param filePath a path appended to each of the cache's source roots in turn until an matching
     *            original source is found for comparison against the local source directory.
     * @return the supplied path if the file is up to date or if an updated version has been copied
     *         to the local sources directory or null if was not found or the copy failed.
     */
    protected Path checkCacheFile(Path filePath) {
        Path targetPath = cachedPath(filePath);
        for (Path root : srcRoots) {
            Path sourcePath = extendPath(root, filePath);
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
        /* delete the target file as it is invalid */
        targetPath.toFile().delete();

        return null;
    }

    protected boolean tryCheckCacheFile(Path sourcePath, Path targetPath) throws IOException {
        if (checkSourcePath(sourcePath)) {
            FileTime sourceTime = Files.getLastModifiedTime(sourcePath);
            FileTime destTime = Files.getLastModifiedTime(targetPath);
            if (destTime.compareTo(sourceTime) < 0) {
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
            return true;
        }
        return false;
    }

    /**
     * Create and initialize the source cache used to locate and cache sources of a given type as
     * determined by the supplied key.
     * 
     * @param type an enum identifying both the type of Java sources cached by the returned cache
     *            and the subdir of the cached source subdirectory in which those sources are
     *            located.
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
     * Extend a root path form one file system using a path potentially derived from another file
     * system by converting he latter to a text string and replacing the file separator if
     * necessary.
     * 
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
     * Convert a potential resolved candidate path to the corresponding local Path in this cache.
     * 
     * @param candidate a resolved candidate path for some given resolution request
     * @return the corresponding local Path
     */
    protected Path cachedPath(Path candidate) {
        return basePath.resolve(candidate);
    }

    /**
     * Convert a potential resolved candidate path to the corresponding local File in this cache.
     * 
     * @param candidate a resolved candidate path for some given resolution request
     * @return the corresponding local File
     */
    protected File cachedFile(Path candidate) {
        return cachedPath(candidate).toFile();
    }

    /**
     * Indicate whether a source path identifies a file in the associated file system.
     * 
     * @param sourcePath the path to check
     * @return true if the path identifies a file or false if no such file can be found.
     */
    protected static boolean checkSourcePath(Path sourcePath) {
        return Files.isRegularFile(sourcePath);
    }

    /**
     * Ensure the directory hierarchy for a path exists creating any missing directories if needed.
     * 
     * @param targetDir a path to the desired directory
     */
    protected static void ensureTargetDirs(Path targetDir) {
        if (targetDir != null) {
            File targetFile = targetDir.toFile();
            if (!targetFile.exists()) {
                targetDir.toFile().mkdirs();
            }
        }
    }

    /**
     * Add a path to the list of classpath entries.
     * 
     * @param path The path to add.
     */
    private static void addClassPathEntry(Path path) {
        classPathEntries.add(path);
    }

    /**
     * Add a path to the list of source path entries.
     * 
     * @param path The path to add.
     */
    private static void addSourcePathEntry(String path) {
        sourcePathEntries.add(path);
    }

    /**
     * An automatic feature class which acquires the image loader class path via the afterAnalysis
     * callback.
     */
    @AutomaticFeature
    @SuppressWarnings("unused")
    public static class SourceCacheFeature implements Feature {
        @Override
        public void afterAnalysis(AfterAnalysisAccess access) {
            FeatureImpl.AfterAnalysisAccessImpl accessImpl = (FeatureImpl.AfterAnalysisAccessImpl) access;
            ImageClassLoader loader = accessImpl.getImageClassLoader();
            for (Path entry : loader.classpath()) {
                addClassPathEntry(entry);
            }
            // also add any necessary source path entries
            if (SubstrateOptions.DebugInfoSourceSearchPath.getValue() != null) {
                for (String searchPathEntry : OptionUtils.flatten(",", SubstrateOptions.DebugInfoSourceSearchPath.getValue())) {
                    addSourcePathEntry(searchPathEntry);
                }
            }
        }
    }
}
