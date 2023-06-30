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

final class InternalResourceCache {

    private static final char[] FILE_SYSTEM_SPECIAL_CHARACTERS = {'/', '\\', ':'};

    private static final Lock unpackLock = new ReentrantLock();
    private static volatile Path cacheRoot;

    private final String id;
    private final InternalResource resource;
    private volatile FileSystem resourceFileSystem;

    InternalResourceCache(String languageId, InternalResource forResource) {
        this.id = Objects.requireNonNull(languageId);
        this.resource = Objects.requireNonNull(forResource);
    }

    FileSystem getResourceFileSystem() throws IOException {
        FileSystem result = resourceFileSystem;
        if (result == null) {
            synchronized (this) {
                result = resourceFileSystem;
                if (result == null) {
                    Path root;
                    if (ImageInfo.inImageRuntimeCode()) {
                        /*
                         * TODO: Shouldn't we rather force a filesystem creation in the image
                         * building-time and throw an assertion error here?
                         */
                        root = findResourceRootOnNativeImage(findCacheRootOnNativeImage());
                    } else {
                        root = findCacheRootOnHotSpot().resolve(Path.of(sanitize(id), sanitize(resource.name()), sanitize(resource.versionHash())));
                        unpackFiles(root, resource);
                    }
                    ResetableCachedRoot rootSupplier = new ResetableCachedRoot(root);
                    result = FileSystems.newInternalResourceFileSystem(rootSupplier);
                    resourceFileSystem = result;
                }
            }
        }
        return result;
    }

    private static void unpackFiles(Path target, InternalResource resourceToUnpack) throws IOException {
        unpackLock.lock();
        try {
            if (!Files.exists(target)) {
                Path owner = Files.createDirectories(target.getParent());
                Path tmpDir = Files.createTempDirectory(owner, null);
                resourceToUnpack.unpackFiles(tmpDir);
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

    private static void unlink(Path f) throws IOException {
        if (Files.isDirectory(f)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(f)) {
                for (Path child : children) {
                    unlink(child);
                }
            }
        }
        Files.delete(f);
    }

    private Path findResourceRootOnNativeImage(Path root) {
        return root.resolve(Path.of(sanitize(id), sanitize(resource.name())));
    }

    private static String sanitize(String pathElement) {
        String result = pathElement;
        for (char fileSystemsSpecialChar : FILE_SYSTEM_SPECIAL_CHARACTERS) {
            result = result.replace(fileSystemsSpecialChar, '_');
        }
        return result;
    }

    private static Path findCacheRootOnHotSpot() throws IOException {
        Path res = cacheRoot;
        if (cacheRoot == null) {
            String userHomeValue = System.getProperty("user.home");
            if (userHomeValue == null) {
                throw CompilerDirectives.shouldNotReachHere("The 'user.home' system property is not set.");
            }
            Path userHome = Paths.get(userHomeValue);
            Path container;
            String os = System.getProperty("os.name");
            if (os == null) {
                throw CompilerDirectives.shouldNotReachHere("The 'os.name' system property is not set.");
            } else if (os.equals("Linux")) {
                container = userHome.resolve(".cache");
            } else if (os.equals("Mac OS X") || os.equals("Darwin")) {
                container = userHome.resolve(Path.of("Library", "Caches"));
            } else if (os.startsWith("Windows")) {
                container = userHome.resolve(Path.of("AppData", "Local"));
            } else {
                // Fallback
                container = userHome.resolve(".cache");
            }
            res = container.resolve("org.graalvm.polyglot");
            res = Files.createDirectories(res).toRealPath();
            cacheRoot = res;
        }
        return res;
    }

    private static Path findCacheRootOnNativeImage() {
        Path res = cacheRoot;
        if (cacheRoot == null) {
            assert ImageInfo.inImageRuntimeCode() : "Can be called only in the native-image execution time.";
            Path executable = Path.of(ProcessProperties.getExecutableName());
            res = executable.resolve("resources");
            cacheRoot = res;
        }
        return res;
    }

    /**
     * Resets cache roots after closed word analyses. This method is called reflectively by the
     * {@code TruffleBaseFeature#afterAnalysis}.
     */
    static void resetNativeImageState() {
        cacheRoot = null;
        for (LanguageCache language : LanguageCache.languages().values()) {
            for (Class<? extends InternalResource> resourceType : language.getResourceTypes()) {
                InternalResourceCache cache = language.getResourceCache(resourceType);
                cache.resetFileSystemNativeImageState();
            }
        }
        for (InstrumentCache instrument : InstrumentCache.load()) {
            for (Class<? extends InternalResource> resourceType : instrument.getResourceTypes()) {
                InternalResourceCache cache = instrument.getResourceCache(resourceType);
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
    static List<Path> buildInternalResourcesForNativeImage(Path target, Set<String> filter) throws IOException {
        List<Path> result = new ArrayList<>();
        for (LanguageCache language : LanguageCache.languages().values()) {
            if (filter == null || filter.contains(language.getId())) {
                for (Class<? extends InternalResource> resourceType : language.getResourceTypes()) {
                    InternalResourceCache cache = language.getResourceCache(resourceType);
                    result.add(cache.buildInternalResourcesForNativeImage(target));
                }
            }
        }
        for (InstrumentCache instrument : InstrumentCache.load()) {
            if (filter == null || filter.contains(instrument.getId())) {
                for (Class<? extends InternalResource> resourceType : instrument.getResourceTypes()) {
                    InternalResourceCache cache = instrument.getResourceCache(resourceType);
                    result.add(cache.buildInternalResourcesForNativeImage(target));
                }
            }
        }
        return result;
    }

    private Path buildInternalResourcesForNativeImage(Path target) throws IOException {
        Path resourceRoot = findResourceRootOnNativeImage(target);
        deleteIfExists(resourceRoot);
        Files.createDirectories(resourceRoot);
        resource.unpackFiles(resourceRoot);
        return resourceRoot;
    }

    private static void deleteIfExists(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(path)) {
                for (Path child : children) {
                    deleteIfExists(child);
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
        cacheRoot = root;
        for (LanguageCache language : LanguageCache.languages().values()) {
            for (Class<? extends InternalResource> resourceType : language.getResourceTypes()) {
                InternalResourceCache cache = language.getResourceCache(resourceType);
                if (disposeResourceFileSystem) {
                    cache.resourceFileSystem = null;
                } else {
                    cache.resetFileSystemNativeImageState();
                }
            }
        }
        for (InstrumentCache instrument : InstrumentCache.load()) {
            for (Class<? extends InternalResource> resourceType : instrument.getResourceTypes()) {
                InternalResourceCache cache = instrument.getResourceCache(resourceType);
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
                res = findResourceRootOnNativeImage(findCacheRootOnNativeImage());
                resourceCacheRoot = res;
            }
            return res;
        }
    }
}
