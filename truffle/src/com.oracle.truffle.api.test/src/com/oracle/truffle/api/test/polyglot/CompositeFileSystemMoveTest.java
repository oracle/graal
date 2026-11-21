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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.FileSystem.Selector;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Moves and copies through a composite file system are performed by the delegate owning both
 * paths, so that the delegate can honor options such as {@link StandardCopyOption#ATOMIC_MOVE}.
 */
public class CompositeFileSystemMoveTest {

    private Path root;
    private Path readOnlyFolder;
    private FileSystem fileSystem;

    @Before
    public void setUp() throws IOException {
        root = Files.createTempDirectory(CompositeFileSystemMoveTest.class.getSimpleName()).toRealPath();
        readOnlyFolder = Files.createDirectory(root.resolve("readOnly"));
        FileSystem readOnly = FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem());
        fileSystem = FileSystem.newCompositeFileSystem(FileSystem.newDefaultFileSystem(), Selector.of(readOnly, (p) -> p.startsWith(readOnlyFolder)));
    }

    @After
    public void tearDown() throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    @Test
    public void testAtomicMoveWithinDelegate() throws IOException {
        Path source = write(root.resolve("source"), "new");
        Path target = write(root.resolve("target"), "old");
        fileSystem.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        Assert.assertFalse(Files.exists(source));
        Assert.assertEquals("new", Files.readString(target));
    }

    @Test
    public void testMoveDirectoryWithinDelegate() throws IOException {
        Path source = Files.createDirectory(root.resolve("folder"));
        write(source.resolve("child"), "content");
        fileSystem.move(source, root.resolve("moved"), StandardCopyOption.ATOMIC_MOVE);
        Assert.assertFalse(Files.exists(source));
        Assert.assertEquals("content", Files.readString(root.resolve("moved").resolve("child")));
    }

    @Test
    public void testRelativeAtomicMoveUsesCompositeWorkingDirectory() throws IOException {
        write(root.resolve("relativeSource"), "relative");
        fileSystem.setCurrentWorkingDirectory(root);
        fileSystem.move(fileSystem.parsePath("relativeSource"), fileSystem.parsePath("relativeTarget"), StandardCopyOption.ATOMIC_MOVE);
        Assert.assertEquals("relative", Files.readString(root.resolve("relativeTarget")));
    }

    @Test
    public void testCopyWithinDelegate() throws IOException {
        Path source = write(root.resolve("copySource"), "copy");
        fileSystem.copy(source, root.resolve("copyTarget"));
        Assert.assertEquals("copy", Files.readString(source));
        Assert.assertEquals("copy", Files.readString(root.resolve("copyTarget")));
    }

    @Test
    public void testMoveWithinReadOnlyDelegate() throws IOException {
        Path source = write(readOnlyFolder.resolve("source"), "readOnly");
        Assert.assertThrows(SecurityException.class, () -> fileSystem.move(source, readOnlyFolder.resolve("target")));
        Assert.assertThrows(SecurityException.class, () -> fileSystem.copy(source, readOnlyFolder.resolve("target")));
        Assert.assertTrue(Files.exists(source));
    }

    @Test
    public void testAtomicMoveAcrossFileSystems() throws IOException {
        Path source = write(root.resolve("crossSource"), "cross");
        Assert.assertThrows(AtomicMoveNotSupportedException.class, () -> fileSystem.move(source, readOnlyFolder.resolve("target"), StandardCopyOption.ATOMIC_MOVE));
        Assert.assertTrue(Files.exists(source));
    }

    private static Path write(Path path, String content) throws IOException {
        return Files.write(path, content.getBytes(StandardCharsets.UTF_8));
    }
}
