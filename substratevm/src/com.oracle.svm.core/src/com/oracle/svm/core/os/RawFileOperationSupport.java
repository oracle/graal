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

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.Pointer;
import org.graalvm.word.SignedWord;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;

/**
 * Provides an OS-independent abstraction for operations on files. Most of the code is implemented
 * in a way that it can be used from uninterruptible code.
 */
public abstract class RawFileOperationSupport {
    public static RawFileOperationSupport get() {
        return ImageSingletons.lookup(RawFileOperationSupport.class);
    }

    /**
     * Opens or creates a file with the specified {@link FileAccessMode access mode}.
     *
     * @return If the operation is successful, it returns the file descriptor. Otherwise, it returns
     *         a value where {@link #isValid} will return false.
     */
    public RawFileDescriptor open(String filename, FileAccessMode mode) {
        return open(new File(filename), mode);
    }

    /**
     * Opens or creates a file with the specified {@link FileAccessMode access mode}.
     *
     * @return If the operation is successful, it returns the file descriptor. Otherwise, it returns
     *         a value where {@link #isValid} will return false.
     */
    public abstract RawFileDescriptor open(File file, FileAccessMode mode);

    /**
     * Checks if a file descriptor is valid or if it represents an error value.
     *
     * @return true if the file descriptor is valid, false otherwise.
     */
    public abstract boolean isValid(RawFileDescriptor fd);

    /**
     * Closes a file descriptor.
     *
     * @return true if the file descriptor was closed by the call, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract boolean close(RawFileDescriptor fd);

    /**
     * Returns the size of a file.
     *
     * @return If the operation is successful, it returns the size of the file. Otherwise, it
     *         returns a value less than 0.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract SignedWord size(RawFileDescriptor fd);

    /**
     * Gets the current file position within a file.
     *
     * @return If the operation is successful, it returns the current file position. Otherwise, it
     *         returns a value less than 0.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract SignedWord position(RawFileDescriptor fd);

    /**
     * Sets the current file position within a file.
     *
     * @return true if the file position was updated to the given value, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract boolean seek(RawFileDescriptor fd, SignedWord position);

    /**
     * Writes data to the current file position and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract boolean write(RawFileDescriptor fd, Pointer data, UnsignedWord size);

    /**
     * Writes data to the current file position and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Array must not move.")
    public boolean write(RawFileDescriptor fd, byte[] data) {
        DynamicHub hub = KnownIntrinsics.readHub(data);
        UnsignedWord baseOffset = LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding());
        Pointer dataPtr = Word.objectToUntrackedPointer(data).add(baseOffset);
        return write(fd, dataPtr, WordFactory.unsigned(data.length));
    }

    /**
     * Writes data to the current file position and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeBoolean(RawFileDescriptor fd, boolean data) {
        return writeByte(fd, (byte) (data ? 1 : 0));
    }

    /**
     * Writes data to the current file position and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeByte(RawFileDescriptor fd, byte data) {
        int sizeInBytes = Byte.BYTES;
        Pointer dataPtr = StackValue.get(sizeInBytes);
        dataPtr.writeByte(0, data);
        return write(fd, dataPtr, WordFactory.unsigned(sizeInBytes));
    }

    /**
     * Writes a short value in the target architecture's byte ordering to the current file position
     * and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeShort(RawFileDescriptor fd, short data) {
        int sizeInBytes = Short.BYTES;
        Pointer dataPtr = StackValue.get(sizeInBytes);
        dataPtr.writeShort(0, data);
        return write(fd, dataPtr, WordFactory.unsigned(sizeInBytes));
    }

    /**
     * Writes a char value in the target architecture's byte ordering to the current file position
     * and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeChar(RawFileDescriptor fd, char data) {
        int sizeInBytes = Character.BYTES;
        Pointer dataPtr = StackValue.get(sizeInBytes);
        dataPtr.writeChar(0, data);
        return write(fd, dataPtr, WordFactory.unsigned(sizeInBytes));
    }

    /**
     * Writes an integer value in the target architecture's byte ordering to the current file
     * position and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeInt(RawFileDescriptor fd, int data) {
        int sizeInBytes = Integer.BYTES;
        Pointer dataPtr = StackValue.get(sizeInBytes);
        dataPtr.writeInt(0, data);
        return write(fd, dataPtr, WordFactory.unsigned(sizeInBytes));
    }

    /**
     * Writes a long value in the target architecture's byte ordering to the current file position
     * and advances the file position.
     *
     * @return true if the data was written, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeLong(RawFileDescriptor fd, long data) {
        int sizeInBytes = Long.BYTES;
        Pointer dataPtr = StackValue.get(sizeInBytes);
        dataPtr.writeLong(0, data);
        return write(fd, dataPtr, WordFactory.unsigned(sizeInBytes));
    }

    /**
     * Reads up to bufferSize bytes of data from to the current file position and advances the file
     * position.
     *
     * @return If the operation is successful, it returns the number of read bytes. Otherwise, it
     *         returns a negative value.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract SignedWord read(RawFileDescriptor fd, Pointer buffer, UnsignedWord bufferSize);

    /**
     * OS-specific signed value that represents a file descriptor. It is OS-specific which values
     * represent valid file descriptors and which values represent error values, see
     * {@link RawFileOperationSupport#isValid}.
     */
    public interface RawFileDescriptor extends WordBase {
    }

    /**
     * The file access modes that can be used when opening/creating a file.
     */
    public enum FileAccessMode {
        READ,
        READ_WRITE,
        WRITE;
    }
}
