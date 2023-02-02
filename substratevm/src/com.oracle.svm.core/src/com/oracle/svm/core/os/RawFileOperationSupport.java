/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.os;

import java.io.File;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.os.AbstractRawFileOperationSupport.RawFileOperationSupportHolder;

/**
 * Provides an OS-independent abstraction for operations on files. Most of the code is implemented
 * in a way that it can be used from uninterruptible code.
 */
public interface RawFileOperationSupport {
    /**
     * Returns a {@link RawFileOperationSupport} singleton that uses little endian byte ordering.
     */
    @Fold
    static RawFileOperationSupport littleEndian() {
        return RawFileOperationSupportHolder.getLittleEndian();
    }

    /**
     * Returns a {@link RawFileOperationSupport} singleton that uses big endian byte ordering.
     */
    @Fold
    static RawFileOperationSupport bigEndian() {
        return RawFileOperationSupportHolder.getBigEndian();
    }

    /**
     * Returns a {@link RawFileOperationSupport} singleton that uses the native byte ordering of the
     * underlying architecture.
     */
    @Fold
    static RawFileOperationSupport nativeByteOrder() {
        return RawFileOperationSupportHolder.getNativeByteOrder();
    }

    /**
     * Opens or creates a file with the specified {@link FileAccessMode access mode}.
     *
     * @return If the operation is successful, it returns the file descriptor. Otherwise, it returns
     *         a value where {@link #isValid} will return false.
     */
    RawFileDescriptor open(String filename, FileAccessMode mode);

    /**
     * Opens or creates a file with the specified {@link FileAccessMode access mode}.
     *
     * @return If the operation is successful, it returns the file descriptor. Otherwise, it returns
     *         a value where {@link #isValid} will return false.
     */
    RawFileDescriptor open(File file, FileAccessMode mode);

    /**
     * Checks if a file descriptor is valid or if it represents an error value.
     *
     * @return true if the file descriptor is valid, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isValid(RawFileDescriptor fd);

    /**
     * Closes a file descriptor.
     *
     * @return true if the file descriptor was closed by the call, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean close(RawFileDescriptor fd);

    /**
     * Returns the size of a file.
     *
     * @return If the operation is successful, it returns the size of the file. Otherwise, it
     *         returns a value less than 0.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    SignedWord size(RawFileDescriptor fd);

    /**
     * Gets the current file position within a file.
     *
     * @return If the operation is successful, it returns the current file position. Otherwise, it
     *         returns a value less than 0.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    SignedWord position(RawFileDescriptor fd);

    /**
     * Sets the current file position within a file.
     *
     * @return true if the file position was updated to the given value, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean seek(RawFileDescriptor fd, SignedWord position);

    /**
     * Writes data to the current file position and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean write(RawFileDescriptor fd, Pointer data, UnsignedWord size);

    /**
     * Writes data to the current file position and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Array must not move.")
    boolean write(RawFileDescriptor fd, byte[] data);

    /**
     * Writes data to the current file position and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean writeBoolean(RawFileDescriptor fd, boolean data);

    /**
     * Writes data to the current file position and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean writeByte(RawFileDescriptor fd, byte data);

    /**
     * Writes a short value in the specified byte ordering to the current file position and advances
     * the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean writeShort(RawFileDescriptor fd, short data);

    /**
     * Writes a char value in the specified byte ordering to the current file position and advances
     * the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean writeChar(RawFileDescriptor fd, char data);

    /**
     * Writes an integer value in the specified byte ordering to the current file position and
     * advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean writeInt(RawFileDescriptor fd, int data);

    /**
     * Writes a long value in the specified byte ordering to the current file position and advances
     * the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean writeLong(RawFileDescriptor fd, long data);

    /**
     * Reads up to bufferSize bytes of data from to the current file position and advances the file
     * position.
     *
     * @return If the operation is successful, it returns the number of read bytes. Otherwise, it
     *         returns a negative value.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    SignedWord read(RawFileDescriptor fd, Pointer buffer, UnsignedWord bufferSize);

    /**
     * OS-specific signed value that represents a file descriptor. It is OS-specific which values
     * represent valid file descriptors and which values represent error values, see
     * {@link RawFileOperationSupport#isValid}.
     */
    interface RawFileDescriptor extends WordBase {
    }

    /**
     * The file access modes that can be used when opening/creating a file.
     */
    enum FileAccessMode {
        READ,
        READ_WRITE,
        WRITE
    }
}
