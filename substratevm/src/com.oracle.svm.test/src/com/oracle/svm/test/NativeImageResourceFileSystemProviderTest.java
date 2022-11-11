/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.test;

import static com.oracle.svm.test.NativeImageResourceUtils.RESOURCE_DIR;
import static com.oracle.svm.test.NativeImageResourceUtils.RESOURCE_FILE_1;
import static com.oracle.svm.test.NativeImageResourceUtils.RESOURCE_FILE_2;
import static com.oracle.svm.test.NativeImageResourceUtils.ROOT_DIRECTORY;
import static com.oracle.svm.test.NativeImageResourceUtils.resourceNameToPath;
import static com.oracle.svm.test.NativeImageResourceUtils.resourceNameToURI;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

@AddExports("java.base/java.lang")
public class NativeImageResourceFileSystemProviderTest {

    private static final String NEW_DIRECTORY = RESOURCE_DIR + "/tmp";

    private static final int TIME_SPAN = 1_000_000;

    private FileSystem fileSystem;

    @Before
    public void createNewFileSystem() {
        URI resource = resourceNameToURI(RESOURCE_FILE_1, true);

        Map<String, String> env = new HashMap<>();
        env.put("create", "true");

        boolean exceptionThrown = false;
        try {
            // Try to get file system. This should raise exception.
            fileSystem = FileSystems.getFileSystem(resource);
        } catch (FileSystemNotFoundException e) {
            // File system not found. Create new one.
            exceptionThrown = true;
            try {
                fileSystem = FileSystems.newFileSystem(resource, env);
            } catch (IOException ioException) {
                Assert.fail("Error during creating a new file system!");
            }
        }

        Assert.assertTrue("File system is already created!", exceptionThrown);
        Assert.assertNotNull("File system is not created!", fileSystem);
    }

    @After
    public void closeFileSystem() {
        try {
            fileSystem.close();
        } catch (IOException e) {
            Assert.fail("Exception occurs during closing file system!");
        }
    }

    /**
     * Query the empty path. Test inspired by issues:
     * <a href="https://github.com/oracle/graal/issues/5081">5081</a>
     */
    @Test
    public void githubIssue5081() {
        fileSystem.getPath("").resolve(RESOURCE_FILE_1);
    }

    /**
     * <p>
     * Query the root path and iterate through all folders. Test inspired by issues:
     * <a href="https://github.com/oracle/graal/issues/5020">5020</a>
     * </p>
     *
     * <p>
     * <b>Description: </b> We are doing next operations: </br>
     * <ol>
     * <li>Check if the root is proper.</li>
     * <li>Iterating through all folders in file system starting for the root.</li>
     * </ol>
     * </p>
     */
    @Test
    public void githubIssue5020() {
        // 1. Check if the root is proper.
        for (Path rootDirectory : fileSystem.getRootDirectories()) {
            Assert.assertEquals("The root directory is not equals to " + ROOT_DIRECTORY + "!", ROOT_DIRECTORY, rootDirectory.toString());
        }

        // 2. Iterating through all resources in file system starting for the root.
        Path rootPath = fileSystem.getPath(ROOT_DIRECTORY);
        try (Stream<Path> files = Files.walk(rootPath)) {
            files.forEach(path -> {
            });
        } catch (IOException e) {
            Assert.fail("IOException occurred during file system walk, starting from the root!");
        }
    }

    /**
     * Query the path with trailing slash and iterate though all its sub-folders. Test inspired by
     * issues: <a href="https://github.com/oracle/graal/issues/5080">5080</a>
     */
    @Test
    public void githubIssue5080() {
        Path path = fileSystem.getPath(RESOURCE_DIR + "/");
        try (Stream<Path> files = Files.walk(path)) {
            files.forEach(p -> {
            });
        } catch (IOException e) {
            Assert.fail("IOException occurred during file system walk, starting from the path: " + path + "!");
        }
    }

    /**
     * Reading from file using {@link java.nio.channels.ByteChannel}.
     */
    @Test
    public void readingFileByteChannel() {
        Path resourceDirectory = fileSystem.getPath(RESOURCE_DIR);
        Path resourceFile1 = resourceNameToPath(RESOURCE_FILE_1, true);

        try (SeekableByteChannel channel = Files.newByteChannel(resourceDirectory, StandardOpenOption.READ)) {
            ByteBuffer byteBuffer = ByteBuffer.allocate((int) channel.size());
            channel.read(byteBuffer);
            Assert.fail("Trying to read from directory as a file!");
        } catch (IOException ignored) {
        }

        try (SeekableByteChannel channel = Files.newByteChannel(resourceFile1, StandardOpenOption.READ)) {
            ByteBuffer byteBuffer = ByteBuffer.allocate((int) channel.size());
            channel.read(byteBuffer);
            String content = new String(byteBuffer.array());
            Assert.assertTrue("Nothing has been read from file!", content.length() > 0);
        } catch (IOException ioException) {
            Assert.fail("Exception occurs during reading from file!");
        }
    }

    /**
     * Writing into file using {@link java.nio.channels.ByteChannel}.
     */
    @Test
    public void writingFileByteChannel() {
        Path resourceDirectory = fileSystem.getPath(RESOURCE_DIR);
        Path resourceFile1 = resourceNameToPath(RESOURCE_FILE_1, true);

        try {
            Files.newByteChannel(resourceDirectory, StandardOpenOption.WRITE);
            Assert.fail("Trying to write into directory as a file!");
        } catch (IOException ignored) {
        }

        try (SeekableByteChannel channel = Files.newByteChannel(resourceFile1, StandardOpenOption.READ)) {
            ByteBuffer byteBuffer = ByteBuffer.wrap("test string".getBytes());
            channel.write(byteBuffer);
            Assert.fail("Wrong write permissions!");
        } catch (IOException | NonWritableChannelException ignored) {
        }

        try (SeekableByteChannel channel = Files.newByteChannel(resourceFile1, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            ByteBuffer byteBuffer = ByteBuffer.wrap("test string#".getBytes());
            channel.write(byteBuffer);
            ByteBuffer byteBuffer2 = ByteBuffer.allocate((int) channel.size());
            channel.read(byteBuffer2);
            String content = new String(byteBuffer.array());
            Assert.assertTrue("Nothing has been writen into file!", content.length() > 0);
            Assert.assertTrue("Content has been writen into file improperly!", content.startsWith("test string#"));
        } catch (IOException ioException) {
            Assert.fail("Exception occurs during writing into file!");
        }
    }

    /**
     * Reading from file using {@link java.nio.channels.FileChannel}.
     */
    @Test
    public void readingFileFileChannel() {
        Path resourceDirectory = fileSystem.getPath(RESOURCE_DIR);
        Path resourceFile1 = resourceNameToPath(RESOURCE_FILE_1, true);

        Set<StandardOpenOption> permissions = Collections.singleton(StandardOpenOption.READ);
        try (SeekableByteChannel channel = fileSystem.provider().newFileChannel(resourceDirectory, permissions)) {
            ByteBuffer byteBuffer = ByteBuffer.allocate((int) channel.size());
            channel.read(byteBuffer);
            Assert.fail("Trying to read from directory as a file!");
        } catch (IOException ignored) {
        }

        try (SeekableByteChannel channel = fileSystem.provider().newFileChannel(resourceFile1, permissions)) {
            ByteBuffer byteBuffer = ByteBuffer.allocate((int) channel.size());
            channel.read(byteBuffer);
            String content = new String(byteBuffer.array());
            Assert.assertTrue("Nothing has been read from file!", content.length() > 0);
        } catch (IOException ioException) {
            Assert.fail("Exception occurs during reading from file!");
        }
    }

    /**
     * Writing into file using {@link java.nio.channels.FileChannel}.
     */
    @Test
    public void writingFileFileChannel() {
        FileSystemProvider provider = fileSystem.provider();

        Path resourceDirectory = fileSystem.getPath(RESOURCE_DIR);
        Path resourceFile1 = resourceNameToPath(RESOURCE_FILE_1, true);

        Set<StandardOpenOption> readPermissions = Collections.singleton(StandardOpenOption.READ);
        Set<StandardOpenOption> writePermissions = Collections.singleton(StandardOpenOption.WRITE);
        Set<StandardOpenOption> readWritePermissions = new HashSet<>(Collections.emptySet());
        readWritePermissions.addAll(readPermissions);
        readWritePermissions.addAll(writePermissions);

        try {
            provider.newFileChannel(resourceDirectory, writePermissions);
            Assert.fail("Trying to write into directory as a file!");
        } catch (IOException ignored) {
        }

        try (SeekableByteChannel channel = provider.newFileChannel(resourceFile1, readPermissions)) {
            ByteBuffer byteBuffer = ByteBuffer.wrap("test string".getBytes());
            channel.write(byteBuffer);
            Assert.fail("Wrong write permissions!");
        } catch (IOException | NonWritableChannelException ignored) {
        }

        try (SeekableByteChannel channel = provider.newFileChannel(resourceFile1, readWritePermissions)) {
            ByteBuffer byteBuffer = ByteBuffer.wrap("test string#".getBytes());
            channel.write(byteBuffer);
            ByteBuffer byteBuffer2 = ByteBuffer.allocate((int) channel.size());
            channel.read(byteBuffer2);
            String content = new String(byteBuffer.array());
            Assert.assertTrue("Nothing has been writen into file!", content.length() > 0);
            Assert.assertTrue("Content has been writen into file improperly!", content.startsWith("test string#"));
        } catch (IOException ioException) {
            Assert.fail("Exception occurs during writing into file!");
        }
    }

    /**
     * <p>
     * Basic file system operations.
     * </p>
     *
     * <p>
     * <b>Description: </b> We are doing next operations: </br>
     * <ol>
     * <li>Creating new directory</li>
     * <li>Copy file to newly create directory</li>
     * <li>Moving file to newly create directory</li>
     * <li>Listing newly create directory</li>
     * <li>Deleting file from newly created directory</li>
     * </ol>
     * </p>
     */
    @Test
    public void fileSystemOperations() {
        Path resourceFile1 = resourceNameToPath(RESOURCE_FILE_1, true);
        Path resourceFile2 = resourceNameToPath(RESOURCE_FILE_2, true);

        // 1. Creating new directory.
        Path newDirectory = fileSystem.getPath(NEW_DIRECTORY);
        try {
            Files.createDirectory(newDirectory);
        } catch (IOException ioException) {
            Assert.fail("Exception occurs during creating new directory!");
        }

        // 2. Copy file to newly create directory.
        Path destination = fileSystem.getPath(newDirectory.toString(),
                        resourceFile1.getName(resourceFile1.getNameCount() - 1).toString());
        try {
            Files.copy(resourceFile1, destination, StandardCopyOption.REPLACE_EXISTING);
            try (SeekableByteChannel channel = Files.newByteChannel(resourceFile1, StandardOpenOption.READ)) {
                ByteBuffer byteBuffer = ByteBuffer.allocate((int) channel.size());
                channel.read(byteBuffer);
                String content = new String(byteBuffer.array());
                Assert.assertTrue("Nothing has been read from new file!", content.length() > 0);
            }
        } catch (IOException ioException) {
            Assert.fail("Exception occurs during copying file into new directory!");
        }

        // 3. Moving file to newly create directory.
        destination = fileSystem.getPath(newDirectory.toString(),
                        resourceFile2.getName(resourceFile2.getNameCount() - 1).toString());
        try {
            Files.move(resourceFile2, destination, StandardCopyOption.REPLACE_EXISTING);
            try (SeekableByteChannel channel = Files.newByteChannel(destination, StandardOpenOption.READ)) {
                ByteBuffer byteBuffer = ByteBuffer.allocate((int) channel.size());
                channel.read(byteBuffer);
                String content = new String(byteBuffer.array());
                Assert.assertTrue("Nothing has been read from new file!", content.length() > 0);
            }
        } catch (IOException ioException) {
            Assert.fail("Exception occurs during moving file into new directory!");
        }

        try {
            Files.newByteChannel(resourceFile2, StandardOpenOption.READ);
            Assert.fail("File is still existing after deletion!");
        } catch (IOException ignored) {
        }

        // 4. Listing newly create directory.
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(newDirectory)) {
            Iterator<Path> iterator = directoryStream.iterator();
            boolean anyEntry = false;
            while (iterator.hasNext()) {
                Path path = iterator.next();
                Assert.assertNotNull("Path is null!", path);
                anyEntry = true;
            }
            Assert.assertTrue("New directory is empty!", anyEntry);
        } catch (IOException e) {
            Assert.fail("Exception occurs during listing new directory!");
        }

        // 5. Deleting file from newly created directory.
        Path target = fileSystem.getPath(newDirectory.toString(),
                        resourceFile1.getName(resourceFile1.getNameCount() - 1).toString());
        try {
            Files.delete(target);
            try {
                Files.newByteChannel(target, StandardOpenOption.READ);
                Assert.fail("File is still existing after deletion!");
            } catch (IOException ignored) {
            }
        } catch (IOException ignored) {
            Assert.fail("Exception occurs during file deletion!");
        }
    }

    /**
     * <p>
     * Reading file/directory attributes.
     * </p>
     *
     * <p>
     * <b>Description: </b> We are doing next operations: </br>
     * <ol>
     * <li>Reading file attributes</li>
     * <li>Reading directory attributes</li>
     * </ol>
     * </p>
     */
    @Test
    public void readingFileAttributes() {
        Path resourceDirectory = fileSystem.getPath(RESOURCE_DIR);
        Path resourceFile1 = resourceNameToPath(RESOURCE_FILE_1, true);

        try {
            // 1. Reading file attributes.
            BasicFileAttributes fileAttributes = Files.readAttributes(resourceFile1,
                            BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Assert.assertNotNull("fileAttributes.lastAccessTime is null!", fileAttributes.lastAccessTime());
            Assert.assertNotNull("fileAttributes.creationTime is null!", fileAttributes.creationTime());
            Assert.assertNotNull("fileAttributes.lastModifiedTime is null!", fileAttributes.lastModifiedTime());
            Assert.assertFalse("fileAttributes.isDirectory is true!", fileAttributes.isDirectory());
            Assert.assertTrue("fileAttributes.isRegularFile is false!", fileAttributes.isRegularFile());
            Assert.assertFalse("fileAttributes.isOther is true!", fileAttributes.isOther());
            Assert.assertFalse("fileAttributes.isSymbolicLink is true!", fileAttributes.isSymbolicLink());
            Assert.assertNull("fileAttributes.fileKey is not null!", fileAttributes.fileKey());

            // 2. Reading directory attributes.
            BasicFileAttributes directoryAttributes = Files.readAttributes(resourceDirectory,
                            BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Assert.assertNotNull("directoryAttributes.lastAccessTime is null!", directoryAttributes.lastAccessTime());
            Assert.assertNotNull("directoryAttributes.creationTime is null!", directoryAttributes.creationTime());
            Assert.assertNotNull("directoryAttributes.lastModifiedTime is null!", directoryAttributes.lastModifiedTime());
            Assert.assertTrue("directoryAttributes.isDirectory is false!", directoryAttributes.isDirectory());
            Assert.assertFalse("directoryAttributes.isRegularFile is true!", directoryAttributes.isRegularFile());
            Assert.assertFalse("directoryAttributes.isOther is true!", directoryAttributes.isOther());
            Assert.assertFalse("directoryAttributes.isSymbolicLink is true!", directoryAttributes.isSymbolicLink());
            Assert.assertNull("directoryAttributes.fileKey is not null!", directoryAttributes.fileKey());
        } catch (IOException e) {
            Assert.fail("Exception occurs during attributes operations!");
        }
    }

    /**
     * Writing file attributes.
     */
    @Test
    public void writingFileAttributes() {
        Path resourceFile1 = resourceNameToPath(RESOURCE_FILE_1, true);

        try {
            // 2. Writing file attributes.
            long lastModifiedTime = System.currentTimeMillis() + TIME_SPAN;
            Files.setAttribute(resourceFile1, "lastModifiedTime", FileTime.fromMillis(lastModifiedTime),
                            LinkOption.NOFOLLOW_LINKS);
            BasicFileAttributes fileAttributes = Files.readAttributes(resourceFile1, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            Assert.assertEquals("lastModifiedTime is not set properly!", fileAttributes.lastModifiedTime(),
                            FileTime.fromMillis(lastModifiedTime));
        } catch (IOException e) {
            Assert.fail("Exception occurs during attributes operations!");
        }
    }
}
