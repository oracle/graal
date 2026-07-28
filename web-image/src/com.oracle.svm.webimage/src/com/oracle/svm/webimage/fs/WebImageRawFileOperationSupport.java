/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.fs;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.io.File;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.shared.Uninterruptible;

/// Web Image generally doesn't support raw file operations because it doesn't have a concept of file descriptors.
public class WebImageRawFileOperationSupport implements RawFileOperationSupport {
    private static final UnsupportedOperationException UNSUPPORTED_OPERATION = new UnsupportedOperationException("Raw file operations are not supported in Web Image");

    @Override
    public RawFilePath allocatePath(String path) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public RawFileDescriptor create(String filename, FileCreationMode creationMode, FileAccessMode accessMode) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public RawFileDescriptor create(File file, FileCreationMode creationMode, FileAccessMode accessMode) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public RawFileDescriptor create(RawFilePath path, FileCreationMode creationMode, FileAccessMode accessMode) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public String getTempDirectory() {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public RawFileDescriptor open(String filename, FileAccessMode accessMode) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    public RawFileDescriptor open(File file, FileAccessMode accessMode) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public RawFileDescriptor open(RawFilePath path, FileAccessMode accessMode) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isValid(RawFileDescriptor fd) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean close(RawFileDescriptor fd) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long size(RawFileDescriptor fd) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long position(RawFileDescriptor fd) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean seek(RawFileDescriptor fd, long position) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean write(RawFileDescriptor fd, Pointer data, UnsignedWord size) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean write(RawFileDescriptor fd, byte[] data) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean writeBoolean(RawFileDescriptor fd, boolean data) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean writeByte(RawFileDescriptor fd, byte data) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean writeShort(RawFileDescriptor fd, short data) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean writeChar(RawFileDescriptor fd, char data) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean writeInt(RawFileDescriptor fd, int data) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean writeLong(RawFileDescriptor fd, long data) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean writeFloat(RawFileDescriptor fd, float data) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean writeDouble(RawFileDescriptor fd, double data) {
        throw UNSUPPORTED_OPERATION;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long read(RawFileDescriptor fd, Pointer buffer, UnsignedWord bufferSize) {
        throw UNSUPPORTED_OPERATION;
    }
}
