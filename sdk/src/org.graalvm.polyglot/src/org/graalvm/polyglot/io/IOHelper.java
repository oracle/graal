/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class IOHelper {

    private IOHelper() {
        throw new IllegalStateException("No instance allowed.");
    }

    static void copy(final Path source, final Path target, final FileSystem fileSystem, CopyOption... options) throws IOException {
        copy(source, target, fileSystem, fileSystem, options);
    }

    static void copy(final Path source, final Path target, final FileSystem sourceFileSystem, final FileSystem targetFileSystem, CopyOption... options) throws IOException {
        if (source.equals(target)) {
            return;
        }
        final Path sourceReal = sourceFileSystem.toRealPath(source, LinkOption.NOFOLLOW_LINKS);
        final Path targetReal = targetFileSystem.toRealPath(target, LinkOption.NOFOLLOW_LINKS);
        if (sourceReal.equals(targetReal)) {
            return;
        }
        final Set<LinkOption> linkOptions = new HashSet<>();
        final Set<StandardCopyOption> copyOptions = EnumSet.noneOf(StandardCopyOption.class);
        for (CopyOption option : options) {
            if (option instanceof StandardCopyOption) {
                copyOptions.add((StandardCopyOption) option);
            } else if (option instanceof LinkOption) {
                linkOptions.add((LinkOption) option);
            }
        }
        if (copyOptions.contains(StandardCopyOption.ATOMIC_MOVE)) {
            throw new AtomicMoveNotSupportedException(source.getFileName().toString(), target.getFileName().toString(), "Atomic move not supported");
        }
        final Map<String, Object> sourceAttributes = sourceFileSystem.readAttributes(
                        sourceReal,
                        "basic:isSymbolicLink,isDirectory,lastModifiedTime,lastAccessTime,creationTime",
                        linkOptions.toArray(new LinkOption[linkOptions.size()]));
        if ((Boolean) sourceAttributes.getOrDefault("isSymbolicLink", false)) {
            throw new IOException("Copying of symbolic links is not supported.");
        }
        if (copyOptions.contains(StandardCopyOption.REPLACE_EXISTING)) {
            try {
                targetFileSystem.delete(targetReal);
            } catch (NoSuchFileException notFound) {
                // Does not exist - nothing to delete
            }
        } else {
            boolean exists;
            try {
                targetFileSystem.checkAccess(targetReal, EnumSet.noneOf(AccessMode.class));
                exists = true;
            } catch (IOException ioe) {
                exists = false;
            }
            if (exists) {
                throw new FileAlreadyExistsException(target.toString());
            }
        }
        if ((Boolean) sourceAttributes.getOrDefault("isDirectory", false)) {
            targetFileSystem.createDirectory(targetReal);
        } else {
            final Set<StandardOpenOption> readOptions = EnumSet.of(StandardOpenOption.READ);
            final Set<StandardOpenOption> writeOptions = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
            try (SeekableByteChannel sourceChannel = sourceFileSystem.newByteChannel(sourceReal, readOptions);
                            SeekableByteChannel targetChannel = targetFileSystem.newByteChannel(targetReal, writeOptions)) {
                final ByteBuffer buffer = ByteBuffer.allocateDirect(1 << 16);
                while (sourceChannel.read(buffer) != -1) {
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        targetChannel.write(buffer);
                    }
                    buffer.clear();
                }
            }
        }
        if (copyOptions.contains(StandardCopyOption.COPY_ATTRIBUTES)) {
            String[] basicMutableAttributes = {"lastModifiedTime", "lastAccessTime", "creationTime"};
            try {
                for (String key : basicMutableAttributes) {
                    final Object value = sourceAttributes.get(key);
                    if (value != null) {
                        targetFileSystem.setAttribute(targetReal, key, value);
                    }
                }
            } catch (Throwable rootCause) {
                try {
                    targetFileSystem.delete(targetReal);
                } catch (Throwable suppressed) {
                    rootCause.addSuppressed(suppressed);
                }
                throw rootCause;
            }
        }
    }

    static void move(final Path source, final Path target, final FileSystem fileSystem, CopyOption... options) throws IOException {
        for (CopyOption option : options) {
            if (StandardCopyOption.ATOMIC_MOVE.equals(option)) {
                throw new AtomicMoveNotSupportedException(source.getFileName().toString(), target.getFileName().toString(), "Atomic move not supported");
            }
        }
        fileSystem.copy(source, target, options);
        fileSystem.delete(source);
    }

    static void move(final Path source, final Path target, final FileSystem sourceFileSystem, final FileSystem targetFileSystem, CopyOption... options) throws IOException {
        for (CopyOption option : options) {
            if (StandardCopyOption.ATOMIC_MOVE.equals(option)) {
                throw new AtomicMoveNotSupportedException(source.getFileName().toString(), target.getFileName().toString(), "Atomic move not supported");
            }
        }
        copy(source, target, sourceFileSystem, targetFileSystem, options);
        sourceFileSystem.delete(source);
    }
}
