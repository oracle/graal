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
import java.nio.channels.ClosedChannelException;

/**
 * Represents a lock on a region of a file acquired through a {@link FileChannel}.
 * <p>
 * A lock remains valid until it is released by {@link #close()} or its associated channel is
 * closed, whichever occurs first. File locks are held on behalf of the entire Java virtual machine
 * and are not suitable for coordinating access between threads in the same virtual machine.
 *
 * @since 25.5
 */
public interface FileLock extends AutoCloseable {

    /**
     * Returns the channel through which this lock was acquired.
     *
     * @return the channel through which this lock was acquired
     * @since 25.5
     */
    FileChannel acquiredBy();

    /**
     * Returns the position of the first byte in the locked region. The position may exceed the
     * current size of the file.
     *
     * @return the position of the first byte in the locked region
     * @since 25.5
     */
    long position();

    /**
     * Returns the size of the locked region in bytes. The locked region need not be contained
     * within or overlap the current file contents.
     *
     * @return the size of the locked region in bytes
     * @since 25.5
     */
    long size();

    /**
     * Returns whether this lock is shared. A request for a shared lock may be converted to an
     * exclusive lock when shared locks are not supported by the operating system.
     *
     * @return {@code true} if this lock is shared, or {@code false} if it is exclusive
     * @since 25.5
     */
    boolean isShared();

    /**
     * Returns whether this lock is valid.
     *
     * @return {@code true} if this lock is valid
     * @since 25.5
     */
    boolean isValid();

    /**
     * Releases this lock. If the lock is already invalid, invoking this method has no effect.
     *
     * @throws ClosedChannelException if the channel used to acquire this lock is closed
     * @throws IOException if an I/O error occurs
     * @since 25.5
     */
    @Override
    void close() throws IOException;

}
