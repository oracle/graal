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

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.ImageClassLoader;

/**
 * A thread safe cache manager for application, GraalVM and JDK Java source files.
 */

public class SourceCache {

    /**
     * A list of all entries in the classpath used by the native image classloader.
     */
    private static final List<Path> classPathEntries = new ArrayList<>();
    /**
     * A list of all entries in the module path used by the native image classloader.
     */
    private static final List<Path> modulePathEntries = new ArrayList<>();
    /**
     * A list of all entries in the source search path specified by the user on the command line.
     */
    private static final List<String> sourcePathEntries = new ArrayList<>();

    /**
     * A list of root directories which may contain source files from which this cache can be
     * populated.
     */
    private List<SourceRoot> srcRoots;

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
    private final HashMap<String, List<Path>> specialSrcRoots;

    /**
     * Create the source cache.
     */
    public SourceCache() {
        basePath = SubstrateOptions.getDebugInfoSourceCacheRoot();
        srcRoots = new ArrayList<>();
        specialSrcRoots = new HashMap<>();
        addJDKSources();
        addGraalSources();
        addApplicationSources();
    }

    private void addJDKSources() {
        String javaHome = System.getProperty("java.home");
        assert javaHome != null;
        Path javaHomePath = Paths.get("", javaHome);
        Path srcZipPath = javaHomePath.resolve("lib").resolve("src.zip");
        if (!srcZipPath.toFile().exists()) {
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

    private void addGraalSources() {
        classPathEntries.stream()
                        .forEach(classPathEntry -> addGraalSourceRoot(classPathEntry, true));
        modulePathEntries.stream()
                        .forEach(modulePathEntry -> addGraalSourceRoot(modulePathEntry, true));
        sourcePathEntries.stream()
                        .forEach(sourcePathEntry -> addGraalSourceRoot(Paths.get(sourcePathEntry), false));
    }

    private void addGraalSourceRoot(Path sourcePath, boolean fromClassPath) {
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
                    } else if (srcFile.isDirectory()) { /* Support for `MX_BUILD_EXPLODED=true` */
                        srcRoots.add(new SourceRoot(srcPath));
                    } else {
                        throw VMError.shouldNotReachHere();
                    }
                }
            } else {
                if (fromClassPath) {
                    /* graal classpath dir entries should have a src and/or src_gen subdirectory */
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

    private void addApplicationSources() {
        classPathEntries.stream()
                        .forEach(classPathEntry -> addApplicationSourceRoot(classPathEntry, true));
        modulePathEntries.stream()
                        .forEach(modulePathEntry -> addApplicationSourceRoot(modulePathEntry, true));
        sourcePathEntries.stream()
                        .forEach(sourcePathEntry -> addApplicationSourceRoot(Paths.get(sourcePathEntry), false));
    }

    private void addApplicationSourceRoot(Path sourceRoot, boolean fromClassPath) {
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
                            srcRoots.add(new SourceRoot(root));
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
                    srcRoots.add(new SourceRoot(sourcePath));
                }
            }
        } catch (NullPointerException npe) {
            // do nothing
        }
    }

    /**
     * The top level path relative to the root directory under which files belonging to this
     * specific cache are located.
     */
    private final Path basePath;

    /**
     * Cache the source file identified by the supplied prototype path if a legitimate candidate for
     * inclusion in this cache can be identified and is not yet included in the cache or
     * alternatively identify and validate any existing candidate cache entry to ensure it is not
     * out of date refreshing it if need be.
     *
     * @param source details of the source file to be searched for
     * @param moduleName The module classes in the source file are expected to belong to.
     */
    public void resolve(Source source, String moduleName) {
        assert source.isCaching() : "invalid state for source resolve";
        Path subPath = source.getPath();
        // first try to locate the file
        Path sourcePath = locateSourceFile(subPath, moduleName);
        if (sourcePath == null) {
            // mark it as missing, notifying any concurrent threads which may be trying to look up
            // the same file
            source.updateStatus(CacheStatus.MISSING);
        } else {
            // mark it as located, notifying any concurrent threads which may be trying to look up
            // the same file
            source.updateStatus(CacheStatus.LOCATED);
            // see if the same file already exists in the cache
            Path targetPath = cachedPath(subPath);
            // see whether we need to update the cached copy
            maybeCacheSource(source, sourcePath, targetPath);
        }
    }

    /**
     * Look for a source file in one of the source roots whose path matches that of the supplied
     * source.
     * 
     * @param subPath
     * @param moduleName The name of the module to which the classes in the source are expected to
     *            belong.
     * @return A path to the source file in the file system to which it belongs or null if a source
     *         cannot be found.
     */
    private Path locateSourceFile(Path subPath, String moduleName) {
        if (moduleName != null) {
            for (String specialRootModule : specialRootModules) {
                if (moduleName.equals(specialRootModule)) {
                    for (Path srcRoot : specialSrcRoots.get(specialRootModule)) {
                        String srcRootGroup = srcRoot.subpath(1, 2).toString().replace(".", subPath.getFileSystem().getSeparator());
                        if (subPath.toString().startsWith(srcRootGroup)) {
                            Path sourcePath = extendPath(srcRoot, subPath);
                            if (sourceExists(sourcePath)) {
                                return sourcePath;
                            }
                        }
                    }
                    break;
                }
            }
        }

        for (SourceRoot root : srcRoots) {
            final Path scopedSubPath;
            if (moduleName != null && root.isJDK) {
                scopedSubPath = Paths.get(moduleName, subPath.toString());
            } else {
                scopedSubPath = subPath;
            }
            final Path sourcePath = extendPath(root.path, scopedSubPath);
            if (sourceExists(sourcePath)) {
                return sourcePath;
            }
        }
        return null;
    }

    /**
     * Check whether we need ot update the cache with a copy of a source file located for the
     * current source and if so try ot copy it.
     * 
     * @param source Details of the source file that may need caching.
     * @param sourcePath A path for the file that may nee dot be cached
     * @param targetPath A path for the target file in the cache
     */
    void maybeCacheSource(Source source, Path sourcePath, Path targetPath) {
        // see if the target file already exists
        File cachedFile = targetPath.toFile();
        boolean doCopy;

        if (cachedFile.exists()) {
            try {
                // only copy if the size is different or the target is older than the source
                FileTime sourceTime = Files.getLastModifiedTime(sourcePath);
                FileTime destTime = Files.getLastModifiedTime(targetPath);
                long sourceSize = Files.size(sourcePath);
                long destSize = Files.size(targetPath);
                doCopy = sourceSize != destSize || destTime.compareTo(sourceTime) < 0;
            } catch (IOException e) {
                // copy anyway just in case
                System.out.println("IOException " + e);
                e.printStackTrace();
                doCopy = true;
            }
        } else {
            // we need to create the cached file and cache dir hierarchy
            ensureTargetDirs(targetPath.getParent());
            doCopy = true;
        }

        if (doCopy) {
            try {
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException e) {
                // cached file may be invalid so remove it
                cachedFile.delete();
                source.updateStatus(CacheStatus.MISSING);
                return;
            }
        }
        source.updateStatus(CacheStatus.CACHED);
    }

    /**
     * Extend a root path form one file system using a path potentially derived from another file
     * system by converting he latter to a text string and replacing the file separator if
     * necessary.
     *
     * @param root the path to be extended
     * @param subPath the subpath to extend it with
     * @return the extended path
     */
    private static Path extendPath(Path root, Path subPath) {
        String filePathString = subPath.toString();
        String fileSeparator = subPath.getFileSystem().getSeparator();
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
    private Path cachedPath(Path candidate) {
        return basePath.resolve(candidate);
    }

    /**
     * Indicate whether a source path identifies a file in the associated file system.
     *
     * @param sourcePath the path to check
     * @return true if the path identifies a file or false if no such file can be found.
     */
    private static boolean sourceExists(Path sourcePath) {
        return Files.isRegularFile(sourcePath);
    }

    /**
     * Ensure the directory hierarchy for a path exists creating any missing directories if needed.
     *
     * @param targetDir a path to the desired directory
     *
     *            TODO: investigate whether this needs to be synchronized in order to be thread safe
     */
    private static void ensureTargetDirs(Path targetDir) {
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
    static void addClassPathEntry(Path path) {
        classPathEntries.add(path);
    }

    /**
     * Add a path to the list of module path entries.
     *
     * @param path The path to add.
     */
    static void addModulePathEntry(Path path) {
        modulePathEntries.add(path);
    }

    /**
     * Add a path to the list of source path entries.
     *
     * @param path The path to add.
     */
    static void addSourcePathEntry(String path) {
        sourcePathEntries.add(path);
    }
}

/**
 * An automatic feature class which acquires the image loader class path via the afterAnalysis
 * callback.
 */
@AutomaticallyRegisteredFeature
@SuppressWarnings("unused")
class SourceCacheFeature implements InternalFeature {
    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        FeatureImpl.AfterAnalysisAccessImpl accessImpl = (FeatureImpl.AfterAnalysisAccessImpl) access;
        ImageClassLoader loader = accessImpl.getImageClassLoader();
        for (Path entry : loader.classpath()) {
            SourceCache.addClassPathEntry(entry);
        }
        for (Path entry : loader.modulepath()) {
            SourceCache.addModulePathEntry(entry);
        }
        // also add any necessary source path entries
        if (SubstrateOptions.DebugInfoSourceSearchPath.getValue() != null) {
            for (String searchPathEntry : OptionUtils.flatten(",", SubstrateOptions.DebugInfoSourceSearchPath.getValue())) {
                SourceCache.addSourcePathEntry(searchPathEntry);
            }
        }
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
