/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot.io;

import java.io.IOException;
import java.lang.reflect.Method;
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

import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.io.MessageTransport.VetoException;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;

final class IOHelper {

    static final IOAccessorImpl ACCESS = new IOAccessorImpl();

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
        Path sourceReal = sourceFileSystem.toRealPath(source, LinkOption.NOFOLLOW_LINKS);
        Path targetReal;
        try {
            targetReal = targetFileSystem.toRealPath(target, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException doesNotExist) {
            // Target does not exist
            targetReal = target;
        }
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
            throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "Atomic move not supported");
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
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "Atomic move not supported");
            }
        }
        fileSystem.copy(source, target, options);
        fileSystem.delete(source);
    }

    static void move(final Path source, final Path target, final FileSystem sourceFileSystem, final FileSystem targetFileSystem, CopyOption... options) throws IOException {
        for (CopyOption option : options) {
            if (StandardCopyOption.ATOMIC_MOVE.equals(option)) {
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "Atomic move not supported");
            }
        }
        copy(source, target, sourceFileSystem, targetFileSystem, options);
        sourceFileSystem.delete(source);
    }

    /*
     * ImplHolder is needed because at the time of initializing this Engine might not yet be fully
     * initialized.
     */
    static class ImplHolder {

        static final AbstractPolyglotImpl IMPL = initImpl();

        private static AbstractPolyglotImpl initImpl() {
            try {
                Method method = Engine.class.getDeclaredMethod("getImpl");
                method.setAccessible(true);
                AbstractPolyglotImpl polyglotImpl = (AbstractPolyglotImpl) method.invoke(null);
                return polyglotImpl;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to initialize execution listener class.", e);
            }
        }

    }

    private static final class IOAccessorImpl extends AbstractPolyglotImpl.IOAccessor {

        @Override
        public Object createIOAccess(String name, boolean allowHostFileAccess, boolean allowSocketAccess, FileSystem fileSystem) {
            return new IOAccess(name, allowHostFileAccess, allowSocketAccess, fileSystem);
        }

        @Override
        public FileSystem getFileSystem(Object ioAccess) {
            return ((IOAccess) ioAccess).getFileSystem();
        }

        @Override
        public boolean hasHostFileAccess(Object ioAccess) {
            return ((IOAccess) ioAccess).hasHostFileAccess();
        }

        @Override
        public boolean hasHostSocketAccess(Object ioAccess) {
            return ((IOAccess) ioAccess).hasHostSocketAccess();
        }

        @Override
        public boolean isVetoException(Throwable exception) {
            return exception instanceof VetoException;
        }

        @Override
        public Exception createVetoException(String message) {
            return new VetoException(message);
        }
    }
}
