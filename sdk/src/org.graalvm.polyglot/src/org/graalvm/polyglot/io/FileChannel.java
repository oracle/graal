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
package org.graalvm.polyglot.io;

import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileLockInterruptionException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Set;

/**
 * A seekable byte channel that supports file locking and, on JDK 22 and later, mapping regions of
 * its file into memory.
 * <p>
 * File system implementations can support locking and memory mapping by returning either a
 * {@link java.nio.channels.FileChannel} or a {@link FileChannel} from
 * {@link FileSystem#newByteChannel(Path, Set, FileAttribute[])}.
 *
 * @since 25.5
 */
public interface FileChannel extends SeekableByteChannel {

    /**
     * {@inheritDoc}
     *
     * @since 25.5
     */
    FileChannel position(long newPosition) throws IOException;

    /**
     * {@inheritDoc}
     *
     * @since 25.5
     */
    FileChannel truncate(long size) throws IOException;

    /**
     * Acquires an exclusive lock on this channel's file, blocking until the lock is acquired, this
     * channel is closed, or the invoking thread is interrupted. This method is equivalent to
     * invoking {@link #lock(long, long, boolean) lock(0, Long.MAX_VALUE, false)}.
     *
     * @return a newly acquired exclusive lock
     * @throws ClosedChannelException if this channel is closed
     * @throws AsynchronousCloseException if another thread closes this channel while the invoking
     *             thread is blocked
     * @throws FileLockInterruptionException if the invoking thread is interrupted while blocked
     * @throws OverlappingFileLockException if an overlapping lock is already held by this Java
     *             virtual machine, or another thread is waiting for an overlapping lock
     * @throws NonWritableChannelException if this channel was not opened for writing
     * @throws UnsupportedOperationException if this channel does not support file locking
     * @throws IOException if another I/O error occurs
     * @since 25.5
     */
    default FileLock lock() throws IOException {
        return lock(0, Long.MAX_VALUE, false);
    }

    /**
     * Acquires a lock on a region of this channel's file, blocking until the lock is acquired, this
     * channel is closed, or the invoking thread is interrupted. The region need not be contained
     * within or overlap the current file contents. A requested shared lock may be converted to an
     * exclusive lock when shared locks are not supported by the operating system.
     *
     * @param position the position of the first byte in the region; must be non-negative
     * @param size the size of the region; must be non-negative and {@code position + size} must not
     *            overflow
     * @param shared {@code true} to request a shared lock, or {@code false} to request an exclusive
     *            lock
     * @return the newly acquired lock
     * @throws IllegalArgumentException if {@code position} or {@code size} is negative or their sum
     *             overflows
     * @throws ClosedChannelException if this channel is closed
     * @throws AsynchronousCloseException if another thread closes this channel while the invoking
     *             thread is blocked
     * @throws FileLockInterruptionException if the invoking thread is interrupted while blocked
     * @throws OverlappingFileLockException if an overlapping lock is already held by this Java
     *             virtual machine, or another thread is waiting for an overlapping lock
     * @throws NonReadableChannelException if a shared lock is requested and this channel was not
     *             opened for reading
     * @throws NonWritableChannelException if an exclusive lock is requested and this channel was
     *             not opened for writing
     * @throws UnsupportedOperationException if this channel does not support file locking
     * @throws IOException if another I/O error occurs
     * @since 25.5
     */
    default FileLock lock(long position, long size, boolean shared) throws IOException {
        throw new UnsupportedOperationException("Channel does not support locking");
    }

    /**
     * Attempts to acquire an exclusive lock on this channel's file without blocking. This method is
     * equivalent to invoking {@link #tryLock(long, long, boolean) tryLock(0, Long.MAX_VALUE, false)}.
     *
     * @return a newly acquired exclusive lock, or {@code null} if another program holds an
     *         overlapping lock
     * @throws ClosedChannelException if this channel is closed
     * @throws OverlappingFileLockException if an overlapping lock is already held by this Java
     *             virtual machine, or another thread is waiting for an overlapping lock
     * @throws NonWritableChannelException if this channel was not opened for writing
     * @throws UnsupportedOperationException if this channel does not support file locking
     * @throws IOException if another I/O error occurs
     * @since 25.5
     */
    default FileLock tryLock() throws IOException {
        return tryLock(0, Long.MAX_VALUE, false);
    }

    /**
     * Attempts to acquire a lock on a region of this channel's file without blocking. The region
     * need not be contained within or overlap the current file contents. A requested shared lock
     * may be converted to an exclusive lock when shared locks are not supported by the operating
     * system.
     *
     * @param position the position of the first byte in the region; must be non-negative
     * @param size the size of the region; must be non-negative and {@code position + size} must not
     *            overflow
     * @param shared {@code true} to request a shared lock, or {@code false} to request an exclusive
     *            lock
     * @return the newly acquired lock, or {@code null} if another program holds an overlapping lock
     * @throws IllegalArgumentException if {@code position} or {@code size} is negative or their sum
     *             overflows
     * @throws ClosedChannelException if this channel is closed
     * @throws OverlappingFileLockException if an overlapping lock is already held by this Java
     *             virtual machine, or another thread is waiting for an overlapping lock
     * @throws NonReadableChannelException if a shared lock is requested and this channel was not
     *             opened for reading
     * @throws NonWritableChannelException if an exclusive lock is requested and this channel was
     *             not opened for writing
     * @throws UnsupportedOperationException if this channel does not support file locking
     * @throws IOException if another I/O error occurs
     * @since 25.5
     */
    default FileLock tryLock(long position, long size, boolean shared) throws IOException {
        throw new UnsupportedOperationException("Channel does not support locking");
    }

    /**
     * Maps a region of this channel's file into a memory segment associated with the given arena.
     * <p>
     * This channel must have been opened for reading for a read-only mapping and for both reading
     * and writing for a read/write or private mapping. Closing this channel does not invalidate the
     * returned segment. Closing {@code arena} invalidates the segment and permits the mapped region
     * to be unmapped.
     * <p>
     * The arena parameter and return type are intentionally erased to {@link Object} to avoid a
     * static dependency on the Foreign Function and Memory API, which is not available on all JDK
     * versions supported by this API. The {@code arena} argument must be a
     * {@code java.lang.foreign.Arena}, and the returned object is a
     * {@code java.lang.foreign.MemorySegment}. When JDK 22 or later becomes the minimum supported
     * version, this method is expected to be deprecated and replaced by a strongly typed variant.
     *
     * @param mode the mode in which the file region is mapped
     * @param offset the offset within the file at which the mapped region starts; must be
     *            non-negative
     * @param size the size of the mapped region, in bytes; must be non-negative
     * @param arena the {@code java.lang.foreign.Arena} associated with the returned segment
     * @return a {@code java.lang.foreign.MemorySegment} whose lifetime is controlled by
     *         {@code arena}
     * @throws IOException if an I/O error occurs
     * @throws NonReadableChannelException if {@code mode} requires read access and this channel was
     *             not opened for reading
     * @throws NonWritableChannelException if {@code mode} requires write access and this channel was
     *             not opened for writing
     * @throws UnsupportedOperationException if this channel does not support memory mapping or the
     *             requested {@code mode}
     * @throws IllegalArgumentException if {@code offset} or {@code size} is negative or their sum
     *             overflows the range of {@code long}
     * @throws IllegalStateException if {@code arena} has already been closed
     * @throws ClassCastException if {@code arena} is not a {@code java.lang.foreign.Arena}
     * @throws RuntimeException if {@code arena} is confined and this method is called from a thread
     *             other than the arena's owner thread; on JDK 22 and later, the concrete exception
     *             is {@code java.lang.WrongThreadException}
     * @throws NullPointerException if {@code mode} or {@code arena} is {@code null}
     * @throws SecurityException if the {@link FileSystem} denied the operation
     * @since 25.5
     */
    default Object map(java.nio.channels.FileChannel.MapMode mode, long offset, long size, Object arena) throws IOException {
        throw new UnsupportedOperationException("Memory mapping is not supported");
    }
}
