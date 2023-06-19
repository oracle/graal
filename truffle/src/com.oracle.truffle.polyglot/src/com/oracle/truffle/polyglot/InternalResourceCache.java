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

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static com.oracle.truffle.polyglot.PolyglotEngineImpl.HOST_LANGUAGE_INDEX;
import static com.oracle.truffle.polyglot.PolyglotFastThreadLocals.LANGUAGE_CONTEXT_OFFSET;
import static com.oracle.truffle.polyglot.PolyglotFastThreadLocals.computeLanguageIndexFromStaticIndex;

final class InternalResourceCache {

    private static final ConcurrentHashMap<Path, Lock> pendingUnpackLocks = new ConcurrentHashMap<>();

    private InternalResourceCache() {
    }

    static Path getInternalResource(PolyglotEngineImpl engine, Class<? extends InternalResource> resourceType) {
        return getInternalResource(resourceType, (e) -> toHostException(engine, e));
    }

    static Path getInternalResource(Class<? extends InternalResource> resourceType) {
        return getInternalResource(resourceType, (e) -> PolyglotLanguageContext.silenceException(RuntimeException.class, e));
    }

    private static Path getInternalResource(Class<? extends InternalResource> resourceType, Function<Exception, RuntimeException> exceptionHandler) {

        InternalResource internalResource;
        try {
            internalResource = resourceType.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw exceptionHandler.apply(e);
        }
        Path cacheRoot = findSystemCacheRoot();
        Path resourceRoot = cacheRoot.resolve(internalResource.name()).resolve(internalResource.versionHash());
        Lock unpackLock = pendingUnpackLocks.computeIfAbsent(resourceRoot, (p) -> new ReentrantLock());
        unpackLock.lock();
        try {
            if (!Files.exists(resourceRoot)) {
                if (ImageInfo.inImageRuntimeCode()) {
                    throw new IOException("Missing native image resources " + resourceRoot);
                } else {
                    Path tmpDir = Files.createTempDirectory(cacheRoot, null);
                    internalResource.unpackFiles(tmpDir);
                    try {
                        Files.move(tmpDir, resourceRoot, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.COPY_ATTRIBUTES);
                    } catch (FileAlreadyExistsException existsException) {
                        // race with other process that already moved the folder
                        // just unlink the tmp directory
                        unlink(tmpDir);
                    }
                }
            }
            verifyResourceRoot(resourceRoot);
        } catch (IOException ioe) {
            throw exceptionHandler.apply(ioe);
        } finally {
            unpackLock.unlock();
        }
        return resourceRoot;
    }

    private static void verifyResourceRoot(Path resourceRoot) throws IOException {
        if (!Files.isDirectory(resourceRoot)) {
            throw new IOException("Resource cache root " + resourceRoot + " must be a directory.");
        }
        if (!Files.isReadable(resourceRoot)) {
            throw new IOException("Resource cache root " + resourceRoot + " must be directory ");
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

    private static Path findSystemCacheRoot() {
        if (ImageInfo.inImageRuntimeCode()) {
            return findSystemCacheRootForNativeImage();
        } else {
            return findSystemCacheRootForHotSpot();
        }
    }

    private static Path findSystemCacheRootForHotSpot() {
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
        return container.resolve("org.graalvm.polyglot");
    }

    private static Path findSystemCacheRootForNativeImage() {
        assert ImageInfo.inImageRuntimeCode() : "Can be called only in the native-image execution time.";
        Path executable = Path.of(ProcessProperties.getExecutableName());
        return executable.resolve("resources");
    }

    private static RuntimeException toHostException(PolyglotEngineImpl engine, Exception e) {
        return engine.host.toHostException(PolyglotFastThreadLocals.getLanguageContext(null, computeLanguageIndexFromStaticIndex(HOST_LANGUAGE_INDEX, LANGUAGE_CONTEXT_OFFSET)), e);
    }
}
