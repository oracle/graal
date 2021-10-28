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
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;

public abstract class AbstractRawFileOperationSupport implements RawFileOperationSupport {
    private final boolean useNativeByteOrder;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected AbstractRawFileOperationSupport(boolean useNativeByteOrder) {
        this.useNativeByteOrder = useNativeByteOrder;
    }

    @Override
    public RawFileDescriptor open(String filename, FileAccessMode mode) {
        return open(new File(filename), mode);
    }

    @Override
    @Uninterruptible(reason = "Array must not move.")
    public boolean write(RawFileDescriptor fd, byte[] data) {
        DynamicHub hub = KnownIntrinsics.readHub(data);
        UnsignedWord baseOffset = LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding());
        Pointer dataPtr = Word.objectToUntrackedPointer(data).add(baseOffset);
        return write(fd, dataPtr, WordFactory.unsigned(data.length));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeBoolean(RawFileDescriptor fd, boolean data) {
        return writeByte(fd, (byte) (data ? 1 : 0));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeByte(RawFileDescriptor fd, byte data) {
        int sizeInBytes = Byte.BYTES;
        Pointer dataPtr = StackValue.get(sizeInBytes);
        dataPtr.writeByte(0, data);
        return write(fd, dataPtr, WordFactory.unsigned(sizeInBytes));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeShort(RawFileDescriptor fd, short data) {
        int sizeInBytes = Short.BYTES;
        Pointer dataPtr = StackValue.get(sizeInBytes);
        dataPtr.writeShort(0, useNativeByteOrder ? data : Short.reverseBytes(data));
        return write(fd, dataPtr, WordFactory.unsigned(sizeInBytes));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeChar(RawFileDescriptor fd, char data) {
        int sizeInBytes = Character.BYTES;
        Pointer dataPtr = StackValue.get(sizeInBytes);
        dataPtr.writeChar(0, useNativeByteOrder ? data : Character.reverseBytes(data));
        return write(fd, dataPtr, WordFactory.unsigned(sizeInBytes));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeInt(RawFileDescriptor fd, int data) {
        int sizeInBytes = Integer.BYTES;
        Pointer dataPtr = StackValue.get(sizeInBytes);
        dataPtr.writeInt(0, useNativeByteOrder ? data : Integer.reverseBytes(data));
        return write(fd, dataPtr, WordFactory.unsigned(sizeInBytes));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeLong(RawFileDescriptor fd, long data) {
        int sizeInBytes = Long.BYTES;
        Pointer dataPtr = StackValue.get(sizeInBytes);
        dataPtr.writeLong(0, useNativeByteOrder ? data : Long.reverseBytes(data));
        return write(fd, dataPtr, WordFactory.unsigned(sizeInBytes));
    }

    public static class RawFileOperationSupportHolder {
        private final RawFileOperationSupport littleEndian;
        private final RawFileOperationSupport bigEndian;
        private final RawFileOperationSupport nativeByteOrder;

        @Platforms(Platform.HOSTED_ONLY.class)
        public RawFileOperationSupportHolder(RawFileOperationSupport littleEndian, RawFileOperationSupport bigEndian, RawFileOperationSupport nativeByteOrder) {
            this.littleEndian = littleEndian;
            this.bigEndian = bigEndian;
            this.nativeByteOrder = nativeByteOrder;
        }

        @Fold
        public static RawFileOperationSupport getLittleEndian() {
            RawFileOperationSupportHolder holder = ImageSingletons.lookup(RawFileOperationSupportHolder.class);
            return holder.littleEndian;
        }

        @Fold
        public static RawFileOperationSupport getBigEndian() {
            RawFileOperationSupportHolder holder = ImageSingletons.lookup(RawFileOperationSupportHolder.class);
            return holder.bigEndian;
        }

        @Fold
        public static RawFileOperationSupport getNativeByteOrder() {
            RawFileOperationSupportHolder holder = ImageSingletons.lookup(RawFileOperationSupportHolder.class);
            return holder.nativeByteOrder;
        }
    }
}
