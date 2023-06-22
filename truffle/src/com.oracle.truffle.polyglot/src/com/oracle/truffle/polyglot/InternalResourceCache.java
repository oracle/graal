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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

final class InternalResourceCache {

    private static final ConcurrentHashMap<Path, Lock> pendingUnpackLocks = new ConcurrentHashMap<>();
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
            Path root;
            if (ImageInfo.inImageRuntimeCode()) {
                root = getResourceRootOnNativeImage();
            } else {
                root = getResourceRootOnHotSpot();
            }
            result = FileSystems.newInternalResourceFileSystem(root);
            resourceFileSystem = result;
        }
        return result;
    }

    private Path getResourceRootOnHotSpot() throws IOException {
        Path root = findCacheRootOnHotSpot();
        Path target = root.resolve(id).resolve(resource.name()).resolve(resource.versionHash());
        Lock unpackLock = pendingUnpackLocks.computeIfAbsent(target, (p) -> new ReentrantLock());
        unpackLock.lock();
        try {
            if (!Files.exists(target)) {
                Path owner = Files.createDirectories(target.getParent());
                Path tmpDir = Files.createTempDirectory(owner, null);
                resource.unpackFiles(tmpDir);
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
            pendingUnpackLocks.remove(target, unpackLock);
        }
        return target;
    }

    private Path getResourceRootOnNativeImage() throws IOException {
        Path root = findCacheRootOnNativeImage();
        Path target = root.resolve(id).resolve(resource.name());
        verifyResourceRoot(target);
        return target;
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

    private static Path findCacheRootOnHotSpot() {
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
                container = userHome.resolve("Library/Caches");
            } else if (os.startsWith("Windows")) {
                container = userHome.resolve("AppData").resolve("Local");
            } else {
                // Fallback
                container = userHome.resolve(".cache");
            }
            res = container.resolve("org.graalvm.polyglot");
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
     * Method intended for unit tests only. Used reflectively by {@code InternalResourceTest}.
     */
    static void setCacheRoot(Path root) {
        cacheRoot = root;
    }
}
