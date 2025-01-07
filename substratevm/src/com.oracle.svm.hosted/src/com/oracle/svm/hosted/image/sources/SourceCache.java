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
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import jdk.graal.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import com.oracle.svm.util.LogUtils;

/**
 * An abstract cache manager for some subspace of the JDK, GraalVM or application source file space.
 * This class implements core behaviours that manage a cache of source files in a specific
 * subdirectory of the local sources directory. It allows source files to be located when present in
 * the local cache or cached when not already present. Subclasses are responsible for providing
 * behaviours that identify an original source for addition to the cache and for verifying that a
 * cached file is not out of date with respect to its original.
 */

public class SourceCache {

    private static final class SourceRoots {
        /**
         * A list of root directories which may contain source files from which this cache can be
         * populated.
         */
        private static final List<SourceRoot> srcRoots = new ArrayList<>();

        /**
         * Modules needing special case root processing.
         */
        private static final String[] specialRootModules = {
                        "jdk.internal.vm.ci",
                        "jdk.graal.compiler",
        };

        /**
         * Extra root directories for files in the jdk.internal.vm.ci/compiler modules.
         */
        private static final HashMap<String, List<Path>> specialSrcRoots = new HashMap<>();

        static {
            addJDKSources();
            addGraalSources();
            addApplicationSources();
        }

        private static void addJDKSources() {
            String javaHome = System.getProperty("java.home");
            assert javaHome != null;
            Path javaHomePath = Paths.get("", javaHome);
            Path srcZipPath = javaHomePath.resolve("lib").resolve("src.zip");
            if (!srcZipPath.toFile().exists()) {
                LogUtils.warning("Unable to locate JDK sources file '%s'. Source line debug will not be available for JDK classes.", srcZipPath);
                return;
            }
            try {
                FileSystem srcFileSystem = FileSystems.newFileSystem(srcZipPath, (ClassLoader) null);
                for (Path root : srcFileSystem.getRootDirectories()) {
                    srcRoots.add(new SourceRoot(root, true));
                    // add dirs named "src" as extra roots for special modules
                    for (String specialRootModule : specialRootModules) {
                        ArrayList<Path> rootsList = new ArrayList<>();
                        specialSrcRoots.put(specialRootModule, rootsList);
                        Path specialModuleRoot = root.resolve(specialRootModule);
                        Files.find(specialModuleRoot, 2, (path, attributes) -> path.endsWith("src")).forEach(rootsList::add);
                    }
                }
            } catch (IOException | FileSystemNotFoundException ioe) {
                /* ignore this entry */
            }
        }

        private static void addGraalSources() {
            SourceCacheFeature.getClassPath().forEach(classPathEntry -> addGraalSourceRoot(classPathEntry, true));
            SourceCacheFeature.getModulePath().forEach(modulePathEntry -> addGraalSourceRoot(modulePathEntry, true));
            SourceCacheFeature.getSourceSearchPath().forEach(sourcePathEntry -> addGraalSourceRoot(sourcePathEntry, false));
        }

        private static void addGraalSourceRoot(Path sourcePath, boolean fromClassPath) {
            try {
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
                    File srcFile = srcPath.toFile();
                    if (srcFile.exists()) {
                        if (srcFile.isFile()) {
                            try {
                                FileSystem fileSystem = FileSystems.newFileSystem(srcPath, (ClassLoader) null);
                                for (Path root : fileSystem.getRootDirectories()) {
                                    srcRoots.add(new SourceRoot(root));
                                }
                            } catch (IOException | FileSystemNotFoundException ioe) {
                                /* ignore this entry */
                            }
                        } else if (srcFile.isDirectory()) { // Support for `MX_BUILD_EXPLODED=true`
                            srcRoots.add(new SourceRoot(srcPath));
                        } else {
                            throw VMError.shouldNotReachHereUnexpectedInput(srcFile);
                        }
                    }
                } else {
                    if (fromClassPath) {
                        /*
                         * graal classpath dir entries should have a src and/or src_gen subdirectory
                         */
                        Path srcPath = sourcePath.resolve("src");
                        srcRoots.add(new SourceRoot(srcPath));
                        srcPath = sourcePath.resolve("src_gen");
                        srcRoots.add(new SourceRoot(srcPath));
                    } else {
                        srcRoots.add(new SourceRoot(sourcePath));
                    }
                }
            } catch (NullPointerException npe) {
                // do nothing
            }
        }

        private static void addApplicationSources() {
            SourceCacheFeature.getClassPath().forEach(classPathEntry -> addApplicationSourceRoot(classPathEntry, true));
            SourceCacheFeature.getModulePath().forEach(modulePathEntry -> addApplicationSourceRoot(modulePathEntry, true));
            SourceCacheFeature.getSourceSearchPath().forEach(sourcePathEntry -> addApplicationSourceRoot(sourcePathEntry, false));
        }

        private static void addApplicationSourceRoot(Path sourceRoot, boolean fromClassPath) {
            try {
                Path sourcePath = sourceRoot;
                String fileNameString = sourcePath.getFileName().toString();
                if (fileNameString.endsWith(".jar") || fileNameString.endsWith(".zip")) {
                    if (fromClassPath && fileNameString.endsWith(".jar")) {
                        /*
                         * application jar /path/to/xxx.jar should have sources
                         * /path/to/xxx-sources.jar
                         */
                        int length = fileNameString.length();
                        fileNameString = fileNameString.substring(0, length - 4) + "-sources.jar";
                    }
                    sourcePath = sourcePath.getParent().resolve(fileNameString);
                    if (sourcePath.toFile().exists()) {
                        try {
                            FileSystem fileSystem = FileSystems.newFileSystem(sourcePath, (ClassLoader) null);
                            for (Path root : fileSystem.getRootDirectories()) {
                                srcRoots.add(new SourceRoot(root));
                            }
                        } catch (IOException | FileSystemNotFoundException ioe) {
                            /* ignore this entry */
                        }
                    }
                } else {
                    if (fromClassPath) {
                        /*
                         * for dir entries ending in classes or target/classes translate to a
                         * parallel src tree
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
                        srcRoots.add(new SourceRoot(sourcePath));
                    }
                }
            } catch (NullPointerException npe) {
                // do nothing
            }
        }
    }

    /**
     * The top level path relative to the root directory under which files belonging to this
     * specific cache are located.
     */
    private final Path basePath = SubstrateOptions.getDebugInfoSourceCacheRoot();

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
    public Path resolve(Path filePath, Class<?> clazz) {
        File cachedFile = findCandidate(filePath);
        if (cachedFile == null) {
            return tryCacheFile(filePath, clazz);
        } else {
            return checkCacheFile(filePath, clazz);
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
    protected Path tryCacheFile(Path filePath, Class<?> clazz) {
        final Path targetPath = cachedPath(filePath);
        String moduleName = null;
        if (clazz != null) {
            /* Paths require the module name as prefix */
            moduleName = clazz.getModule().getName();
        }

        if (moduleName != null) {
            for (String specialRootModule : SourceRoots.specialRootModules) {
                if (moduleName.equals(specialRootModule)) {
                    // handle this module specially as it has intermediate dirs
                    List<Path> specialModulePathList = SourceRoots.specialSrcRoots.get(specialRootModule);
                    // if we have no src.zip then there will be no entry in the hash table
                    if (specialModulePathList == null) {
                        break;
                    }
                    for (Path srcRoot : specialModulePathList) {
                        String srcRootGroup = srcRoot.subpath(1, 2).toString().replace(".", filePath.getFileSystem().getSeparator());
                        if (filePath.toString().startsWith(srcRootGroup)) {
                            Path sourcePath = extendPath(srcRoot, filePath);
                            if (tryCacheFileFromRoot(sourcePath, targetPath)) {
                                return filePath;
                            }
                        }
                    }
                    break;
                }
            }
        }

        for (SourceRoot root : SourceRoots.srcRoots) {
            final Path scopedFilePath;
            if (moduleName != null && root.isJDK) {
                scopedFilePath = Paths.get(moduleName, filePath.toString());
            } else {
                scopedFilePath = filePath;
            }
            final Path sourcePath = extendPath(root.path, scopedFilePath);
            if (tryCacheFileFromRoot(sourcePath, targetPath)) {
                // return the original filePath
                // we don't want the sources/ prefix to go into the debuginfo
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
    protected Path checkCacheFile(Path filePath, Class<?> clazz) {
        Path targetPath = cachedPath(filePath);
        String moduleName = null;
        if (clazz != null) {
            /* Paths require the module name as prefix */
            moduleName = clazz.getModule().getName();
        }

        if (moduleName != null) {
            for (String specialRootModule : SourceRoots.specialRootModules) {
                if (moduleName.equals(specialRootModule)) {
                    // handle this module specially as it has intermediate dirs
                    List<Path> specialModulePathList = SourceRoots.specialSrcRoots.get(specialRootModule);
                    // if we have no src.zip then there will be no entry in the hash table
                    if (specialModulePathList == null) {
                        break;
                    }
                    for (Path srcRoot : specialModulePathList) {
                        String srcRootGroup = srcRoot.subpath(1, 2).toString().replace(".", filePath.getFileSystem().getSeparator());
                        if (filePath.toString().startsWith(srcRootGroup)) {
                            Path sourcePath = extendPath(srcRoot, filePath);
                            try {
                                if (tryCheckCacheFile(sourcePath, targetPath)) {
                                    return filePath;
                                }
                            } catch (IOException e) {
                                /* delete the target file as it is invalid */
                                targetPath.toFile().delete();
                                /* have another go at caching it */
                                return tryCacheFile(filePath, clazz);
                            }
                        }
                    }
                    break;
                }
            }
        }

        for (SourceRoot root : SourceRoots.srcRoots) {
            final Path scopedFilePath;
            if (moduleName != null && root.isJDK) {
                scopedFilePath = Paths.get(moduleName, filePath.toString());
            } else {
                scopedFilePath = filePath;
            }
            final Path sourcePath = extendPath(root.path, scopedFilePath);
            try {
                if (tryCheckCacheFile(sourcePath, targetPath)) {
                    return filePath;
                }
            } catch (IOException e) {
                /* delete the target file as it is invalid */
                targetPath.toFile().delete();
                /* have another go at caching it */
                return tryCacheFile(filePath, clazz);
            }
        }
        /* delete the cached file as it is invalid */
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
}

/**
 * An automatic feature class which acquires the image loader class path via the afterAnalysis
 * callback.
 */
@AutomaticallyRegisteredFeature
@SuppressWarnings("unused")
class SourceCacheFeature implements InternalFeature {

    public static class Options {
        @Option(help = "Search path for source files for application or GraalVM classes (list of comma-separated directories or jar files)")//
        static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Paths> DebugInfoSourceSearchPath = new HostedOptionKey<>(
                        AccumulatingLocatableMultiOptionValue.Paths.buildWithCommaDelimiter());
    }

    ImageClassLoader imageClassLoader;

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        imageClassLoader = ((FeatureImpl.AfterAnalysisAccessImpl) access).getImageClassLoader();
    }

    static List<Path> getClassPath() {
        return ImageSingletons.lookup(SourceCacheFeature.class).imageClassLoader.classpath();
    }

    static List<Path> getModulePath() {
        return ImageSingletons.lookup(SourceCacheFeature.class).imageClassLoader.modulepath();
    }

    static List<Path> getSourceSearchPath() {
        return Options.DebugInfoSourceSearchPath.getValue().values();
    }
}

class SourceRoot {
    Path path;
    boolean isJDK;

    SourceRoot(Path path) {
        this(path, false);
    }

    SourceRoot(Path path, boolean isJDK) {
        this.path = path;
        this.isJDK = isJDK;
    }
}
