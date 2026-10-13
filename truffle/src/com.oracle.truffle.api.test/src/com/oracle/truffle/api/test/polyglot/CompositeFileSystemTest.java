/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.polyglot.io.FileSystem;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

public class CompositeFileSystemTest {

    @Test
    public void testAtomicMove() throws Exception {
        Assume.assumeTrue("OS does not support atomic move", osSupportsAtomicMove());
        Path root = Files.createTempDirectory("test").toRealPath();
        Path fallback = Files.createDirectories(root.resolve("fallback"));
        Path readonly = Files.createDirectory(root.resolve("readonly"));
        try {
            FileSystem fs = FileSystem.newCompositeFileSystem(FileSystem.newDefaultFileSystem(),
                            FileSystem.Selector.of(FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem()), p -> p.startsWith(readonly)));
            Path sourceInFallback = Files.writeString(fallback.resolve("sourceInFallback"), "content");
            Path targetInFallback = fallback.resolve("targetInFallback");
            fs.move(sourceInFallback, targetInFallback, StandardCopyOption.ATOMIC_MOVE);

            Path sourceInReadOnly = Files.writeString(readonly.resolve("sourceInReadonly"), "content");
            Path targetInFallback2 = fallback.resolve("targetInFallback2");
            AbstractPolyglotTest.assertFails(() -> {
                fs.move(sourceInReadOnly, targetInFallback2, StandardCopyOption.ATOMIC_MOVE);
                return null;
            }, AtomicMoveNotSupportedException.class);
        } finally {
            delete(root);
        }
    }

    private static boolean osSupportsAtomicMove() throws IOException {
        Path root = Files.createTempDirectory("probe").toRealPath();
        Path src = Files.createDirectory(root.resolve("src"));
        Path dest = root.resolve("dest");
        try {
            Files.move(src, dest, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (AtomicMoveNotSupportedException e) {
            return false;
        } finally {
            delete(root);
        }
    }

    private static void delete(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    delete(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    @Test
    public void testCompositeFileSystemCompleteness() throws Exception {
        Assume.assumeFalse("Not supported in native image", ImageInfo.inImageCode());
        // Methods that are implemented by CompositeFileSystem and do not delegate
        Set<String> nonDelegatingMethodNames = Set.of(
                        "toAbsolutePath",
                        "setCurrentWorkingDirectory");
        // Methods that always use fallback filesystem
        Set<String> fallbackOnlyMethodNames = Set.of(
                        "parsePath",
                        "getTempDirectory",
                        "getSeparator",
                        "getPathSeparator");
        Path selectionRoot = Path.of("selected").toAbsolutePath();
        Path fallbackRoot = Path.of("fallback").toAbsolutePath();
        InvokeVerifier fallback = new InvokeVerifier(fallbackRoot);
        InvokeVerifier selected = new InvokeVerifier(selectionRoot);
        FileSystem compositeFs = FileSystem.newCompositeFileSystem(fallback, new FileSystem.Selector(selected) {
            @Override
            public boolean test(Path path) {
                return path.startsWith(selectionRoot);
            }
        });

        for (Method method : FileSystem.class.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers) || !Modifier.isPublic(modifiers) || nonDelegatingMethodNames.contains(method.getName())) {
                continue;
            }
            if (!fallbackOnlyMethodNames.contains(method.getName())) {
                selected.invokedMethodName = null;
                method.invoke(compositeFs, createFileSystemMethodArguments(method, selectionRoot));
                Assert.assertEquals(String.format("%s is not implemented by composite file system", method.getName()), method.getName(), selected.invokedMethodName);
            }

            fallback.invokedMethodName = null;
            method.invoke(compositeFs, createFileSystemMethodArguments(method, fallbackRoot));
            Assert.assertEquals(String.format("%s does not delegate to fallback file system", method.getName()), method.getName(), fallback.invokedMethodName);
        }
    }

    private static Object[] createFileSystemMethodArguments(Method method, Path pathRoot) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        int pathCounter = 1;
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> parameterType = parameterTypes[i];
            Object argument;
            if (Path.class.equals(parameterType)) {
                argument = pathRoot.resolve(String.format("file_%d", pathCounter++));
            } else if (String.class.equals(parameterType)) {
                argument = pathRoot.resolve(String.format("file_%d", pathCounter++)).toString();
            } else if (URI.class.equals(parameterType)) {
                argument = pathRoot.resolve(String.format("file_%d", pathCounter++)).toUri();
            } else if (parameterType.isArray()) {
                argument = Array.newInstance(parameterType.componentType(), 0);
            } else {
                argument = null;
            }
            arguments[i] = argument;
        }
        return arguments;
    }

    private static final class InvokeVerifier implements FileSystem {

        private final Path root;
        String invokedMethodName;

        InvokeVerifier(Path root) {
            this.root = Objects.requireNonNull(root);
        }

        @Override
        public Path parsePath(URI uri) {
            fetchMethodName();
            return Path.of(uri);
        }

        @Override
        public Path parsePath(String path) {
            fetchMethodName();
            return Path.of(path);
        }

        @Override
        public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions) {
            fetchMethodName(path);
        }

        @Override
        public void createDirectory(Path dir, FileAttribute<?>... attrs) {
            fetchMethodName(dir);
        }

        @Override
        public void delete(Path path) {
            fetchMethodName(path);
        }

        @Override
        public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) {
            fetchMethodName(path);
            return null;
        }

        @Override
        public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) {
            fetchMethodName(dir);
            return null;
        }

        @Override
        public Path toAbsolutePath(Path path) {
            fetchMethodName(path);
            return path.toAbsolutePath();
        }

        @Override
        public Path toRealPath(Path path, LinkOption... linkOptions) {
            fetchMethodName(path);
            return path;
        }

        @Override
        public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
            fetchMethodName(path);
            return Map.of();
        }

        @Override
        public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
            fetchMethodName(path);
        }

        @Override
        public void copy(Path source, Path target, CopyOption... options) {
            fetchMethodName(source, target);
        }

        @Override
        public void move(Path source, Path target, CopyOption... options) {
            fetchMethodName(source, target);
        }

        @Override
        public void createLink(Path link, Path existing) {
            fetchMethodName(link, existing);
        }

        @Override
        public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) {
            fetchMethodName(link, target);
        }

        @Override
        public Path readSymbolicLink(Path link) {
            fetchMethodName(link);
            return null;
        }

        @Override
        public void setCurrentWorkingDirectory(Path currentWorkingDirectory) {
            fetchMethodName(currentWorkingDirectory);
        }

        @Override
        public String getSeparator() {
            fetchMethodName();
            return File.separator;
        }

        @Override
        public String getPathSeparator() {
            fetchMethodName();
            return File.pathSeparator;
        }

        @Override
        public String getMimeType(Path path) {
            fetchMethodName(path);
            return null;
        }

        @Override
        public Charset getEncoding(Path path) {
            fetchMethodName(path);
            return null;
        }

        @Override
        public Path getTempDirectory() {
            fetchMethodName();
            return null;
        }

        @Override
        public boolean isSameFile(Path path1, Path path2, LinkOption... options) {
            fetchMethodName(path1, path2);
            return false;
        }

        @Override
        public long getFileStoreTotalSpace(Path path) {
            fetchMethodName(path);
            return 0;
        }

        @Override
        public long getFileStoreUnallocatedSpace(Path path) {
            fetchMethodName(path);
            return 0;
        }

        @Override
        public long getFileStoreUsableSpace(Path path) {
            fetchMethodName(path);
            return 0;
        }

        @Override
        public long getFileStoreBlockSize(Path path) {
            fetchMethodName(path);
            return 0;
        }

        @Override
        public boolean isFileStoreReadOnly(Path path) {
            fetchMethodName(path);
            return false;
        }

        private void fetchMethodName(Path... paths) {
            for (Path path : paths) {
                Assert.assertTrue(path.startsWith(root));
            }
            invokedMethodName = StackWalker.getInstance().walk((frames) -> frames.skip(1).findFirst().map(StackWalker.StackFrame::getMethodName)).orElseThrow(() -> new NoSuchElementException(
                            "No caller method"));
        }
    }
}
