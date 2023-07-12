/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InternalResource;
import com.oracle.truffle.api.OS;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.polyglot.io.FileSystem;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Stream;

final class InternalResourceCache {

    private static final char[] FILE_SYSTEM_SPECIAL_CHARACTERS = {'/', '\\', ':'};

    private static final Lock unpackLock = new ReentrantLock();
    private static volatile Pair<Path, Boolean> cacheRoot;

    private final String id;
    private final String resourceId;
    private final Supplier<InternalResource> resourceFactory;
    private volatile FileSystem resourceFileSystem;

    InternalResourceCache(String languageId, String resourceId, Supplier<InternalResource> resourceFactory) {
        this.id = Objects.requireNonNull(languageId);
        this.resourceId = Objects.requireNonNull(resourceId);
        this.resourceFactory = Objects.requireNonNull(resourceFactory);
    }

    FileSystem getResourceFileSystem(PolyglotEngineImpl polyglotEngine) throws IOException {
        FileSystem result = resourceFileSystem;
        if (result == null) {
            synchronized (this) {
                result = resourceFileSystem;
                if (result == null) {
                    Path root;
                    if (hasExplicitCacheRoot()) {
                        root = findStandaloneResourceRoot(getExplicitCacheRoot());
                    } else if (ImageInfo.inImageRuntimeCode()) {
                        root = findStandaloneResourceRoot(findCacheRootOnNativeImage());
                    } else {
                        InternalResource resource = resourceFactory.get();
                        InternalResource.Env env = EngineAccessor.LANGUAGE.createInternalResourceEnv(() -> polyglotEngine.inEnginePreInitialization);
                        root = findCacheRootOnHotSpot().resolve(Path.of(sanitize(id), sanitize(resourceId), sanitize(resource.versionHash(env))));
                        unpackResourceFiles(root, resource, env);
                    }
                    ResetableCachedRoot rootSupplier = new ResetableCachedRoot(root);
                    result = FileSystems.newInternalResourceFileSystem(rootSupplier);
                    resourceFileSystem = result;
                }
            }
        }
        return result;
    }

    private static void unpackResourceFiles(Path target, InternalResource resource, InternalResource.Env env) throws IOException {
        unpackLock.lock();
        try {
            if (!Files.exists(target)) {
                Path parent = target.getParent();
                if (parent == null) {
                    throw CompilerDirectives.shouldNotReachHere("Target must have a parent directory but was " + target);
                }
                Path owner = Files.createDirectories(Objects.requireNonNull(parent));
                Path tmpDir = Files.createTempDirectory(owner, null);
                resource.unpackFiles(tmpDir, env);
                try {
                    Files.move(tmpDir, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (FileAlreadyExistsException existsException) {
                    // race with other process that already moved the folder just unlink the tmp
                    // directory
                    unlink(tmpDir);
                }
            } else {
                verifyResourceRoot(target);
            }
        } finally {
            unpackLock.unlock();
        }
    }

    private static void verifyResourceRoot(Path resourceRoot) throws IOException {
        if (!Files.isDirectory(resourceRoot)) {
            throw new IOException("Resource cache root " + resourceRoot + " must be a directory.");
        }
        if (!Files.isReadable(resourceRoot)) {
            throw new IOException("Resource cache root " + resourceRoot + " must be readable.");
        }
    }

    private Path findStandaloneResourceRoot(Path root) {
        return root.resolve(Path.of(sanitize(id), sanitize(resourceId)));
    }

    private static String sanitize(String pathElement) {
        String result = pathElement;
        for (char fileSystemsSpecialChar : FILE_SYSTEM_SPECIAL_CHARACTERS) {
            result = result.replace(fileSystemsSpecialChar, '_');
        }
        return result;
    }

    private static boolean hasExplicitCacheRoot() throws IOException {
        Pair<Path, Boolean> res = cacheRoot;
        if (res == null) {
            String resourcesFolder = System.getProperty("polyglot.engine.ResourcesFolder");
            if (resourcesFolder != null) {
                Path cache = Paths.get(resourcesFolder).toRealPath();
                res = Pair.create(cache, true);
                cacheRoot = res;
            }
        }
        return res != null && res.getRight();
    }

    private static Path getExplicitCacheRoot() {
        Pair<Path, Boolean> res = cacheRoot;
        if (res == null || !res.getRight()) {
            throw CompilerDirectives.shouldNotReachHere("Can be only called when hasExplicitCacheRoot() returned true");
        }
        return res.getLeft();
    }

    private static Path findCacheRootOnHotSpot() throws IOException {
        Pair<Path, Boolean> res = cacheRoot;
        if (res == null) {
            String userHomeValue = System.getProperty("user.home");
            if (userHomeValue == null) {
                throw CompilerDirectives.shouldNotReachHere("The 'user.home' system property is not set.");
            }
            Path userHome = Paths.get(userHomeValue);
            Path container = switch (OS.getCurrent()) {
                case DARWIN -> userHome.resolve(Path.of("Library", "Caches"));
                case LINUX -> userHome.resolve(".cache");
                case WINDOWS -> userHome.resolve(Path.of("AppData", "Local"));
            };
            Path cache = container.resolve("org.graalvm.polyglot");
            cache = Files.createDirectories(cache).toRealPath();
            res = Pair.create(cache, false);
            cacheRoot = res;
        }
        return res.getLeft();
    }

    private static Path findCacheRootOnNativeImage() {
        Pair<Path, Boolean> res = cacheRoot;
        if (res == null) {
            assert ImageInfo.inImageRuntimeCode() : "Can be called only in the native-image execution time.";
            Path executable = Path.of(ProcessProperties.getExecutableName());
            Path cache = executable.resolve("resources");
            res = Pair.create(cache, false);
            cacheRoot = res;
        }
        return res.getLeft();
    }

    /**
     * Resets cache roots after closed word analyses. This method is called reflectively by the
     * {@code TruffleBaseFeature#afterAnalysis}.
     */
    static void resetNativeImageState() {
        cacheRoot = null;
        for (LanguageCache language : LanguageCache.languages().values()) {
            for (String resourceId : language.getResourceIds()) {
                InternalResourceCache cache = language.getResourceCache(resourceId);
                cache.resetFileSystemNativeImageState();
            }
        }
        for (InstrumentCache instrument : InstrumentCache.load()) {
            for (String resourceId : instrument.getResourceIds()) {
                InternalResourceCache cache = instrument.getResourceCache(resourceId);
                cache.resetFileSystemNativeImageState();
            }
        }
    }

    private void resetFileSystemNativeImageState() {
        FileSystem fs = resourceFileSystem;
        if (fs != null) {
            ((ResetableCachedRoot) FileSystems.getInternalResourceFileSystemRoot(fs)).resourceCacheRoot = null;
        }
    }

    /**
     * Unpacks internal resources after native-image write. This method is called reflectively by
     * the {@code TruffleBaseFeature#afterAnalysis}.
     */
    static List<Path> copyResourcesForNativeImage(Path target, String... components) throws IOException {
        List<Path> result = new ArrayList<>();
        Set<String> filter = components.length == 0 ? null : Set.of(components);
        for (LanguageCache language : LanguageCache.languages().values()) {
            if (filter == null || filter.contains(language.getId())) {
                for (String resourceId : language.getResourceIds()) {
                    InternalResourceCache cache = language.getResourceCache(resourceId);
                    Path resourceRoot = cache.copyResourcesForNativeImage(target);
                    if (resourceRoot != null) {
                        result.add(resourceRoot);
                    }
                }
            }
        }
        for (InstrumentCache instrument : InstrumentCache.load()) {
            if (filter == null || filter.contains(instrument.getId())) {
                for (String resourceId : instrument.getResourceIds()) {
                    InternalResourceCache cache = instrument.getResourceCache(resourceId);
                    Path resourceRoot = cache.copyResourcesForNativeImage(target);
                    if (resourceRoot != null) {
                        result.add(resourceRoot);
                    }
                }
            }
        }
        return result;
    }

    private Path copyResourcesForNativeImage(Path target) throws IOException {
        Path resourceRoot = findStandaloneResourceRoot(target);
        unlink(resourceRoot);
        Files.createDirectories(resourceRoot);
        InternalResource.Env env = EngineAccessor.LANGUAGE.createInternalResourceEnv(() -> false);
        resourceFactory.get().unpackFiles(resourceRoot, env);
        if (isEmpty(resourceRoot)) {
            Files.deleteIfExists(resourceRoot);
            return null;
        } else {
            return resourceRoot;
        }
    }

    private static boolean isEmpty(Path folder) throws IOException {
        try (Stream<Path> children = Files.list(folder)) {
            return children.findAny().isEmpty();
        }
    }

    private static void unlink(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
                for (Path child : children) {
                    unlink(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    /**
     * Sets the {@link #cacheRoot} in unit tests. This method is called reflectively by the
     * {@code InternalResourceTest}.
     */
    @SuppressWarnings("unused")
    private static void setTestCacheRoot(Path root, boolean disposeResourceFileSystem) {
        cacheRoot = root == null ? null : Pair.create(root, false);
        for (LanguageCache language : LanguageCache.languages().values()) {
            for (String resourceId : language.getResourceIds()) {
                InternalResourceCache cache = language.getResourceCache(resourceId);
                if (disposeResourceFileSystem) {
                    cache.resourceFileSystem = null;
                } else {
                    cache.resetFileSystemNativeImageState();
                }
            }
        }
        for (InstrumentCache instrument : InstrumentCache.load()) {
            for (String resourceId : instrument.getResourceIds()) {
                InternalResourceCache cache = instrument.getResourceCache(resourceId);
                if (disposeResourceFileSystem) {
                    cache.resourceFileSystem = null;
                } else {
                    cache.resetFileSystemNativeImageState();
                }
            }
        }
    }

    private final class ResetableCachedRoot implements Supplier<Path> {

        private volatile Path resourceCacheRoot;

        ResetableCachedRoot(Path resourceCacheRoot) {
            Objects.requireNonNull(resourceCacheRoot, "ResourceCacheRoot must be non-null.");
            this.resourceCacheRoot = resourceCacheRoot;
        }

        @Override
        public Path get() {
            Path res = resourceCacheRoot;
            if (res == null) {
                if (ImageInfo.inImageBuildtimeCode()) {
                    throw CompilerDirectives.shouldNotReachHere("Reintroducing internal resource cache path into an image heap.");
                }
                res = findStandaloneResourceRoot(findCacheRootOnNativeImage());
                resourceCacheRoot = res;
            }
            return res;
        }
    }
}
