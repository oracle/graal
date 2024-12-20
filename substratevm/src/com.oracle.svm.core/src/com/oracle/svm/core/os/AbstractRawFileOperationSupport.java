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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.KnownIntrinsics;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;

public abstract class AbstractRawFileOperationSupport implements RawFileOperationSupport {
    private final boolean useNativeByteOrder;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected AbstractRawFileOperationSupport(boolean useNativeByteOrder) {
        this.useNativeByteOrder = useNativeByteOrder;
    }

    @Override
    public RawFileDescriptor create(String filename, FileCreationMode creationMode, FileAccessMode accessMode) {
        return create(new File(filename), creationMode, accessMode);
    }

    @Override
    public RawFileDescriptor open(String filename, FileAccessMode accessMode) {
        return open(new File(filename), accessMode);
    }

    @Override
    @Uninterruptible(reason = "Array must not move.")
    public boolean write(RawFileDescriptor fd, byte[] data) {
        DynamicHub hub = KnownIntrinsics.readHub(data);
        UnsignedWord baseOffset = LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding());
        Pointer dataPtr = Word.objectToUntrackedPointer(data).add(baseOffset);
        return write(fd, dataPtr, Word.unsigned(data.length));
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
        return write(fd, dataPtr, Word.unsigned(sizeInBytes));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeShort(RawFileDescriptor fd, short data) {
        int sizeInBytes = Short.BYTES;
        Pointer dataPtr = StackValue.get(sizeInBytes);
        dataPtr.writeShort(0, useNativeByteOrder ? data : Short.reverseBytes(data));
        return write(fd, dataPtr, Word.unsigned(sizeInBytes));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeChar(RawFileDescriptor fd, char data) {
        int sizeInBytes = Character.BYTES;
        Pointer dataPtr = StackValue.get(sizeInBytes);
        dataPtr.writeChar(0, useNativeByteOrder ? data : Character.reverseBytes(data));
        return write(fd, dataPtr, Word.unsigned(sizeInBytes));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeInt(RawFileDescriptor fd, int data) {
        int sizeInBytes = Integer.BYTES;
        Pointer dataPtr = StackValue.get(sizeInBytes);
        dataPtr.writeInt(0, useNativeByteOrder ? data : Integer.reverseBytes(data));
        return write(fd, dataPtr, Word.unsigned(sizeInBytes));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeLong(RawFileDescriptor fd, long data) {
        int sizeInBytes = Long.BYTES;
        Pointer dataPtr = StackValue.get(sizeInBytes);
        dataPtr.writeLong(0, useNativeByteOrder ? data : Long.reverseBytes(data));
        return write(fd, dataPtr, Word.unsigned(sizeInBytes));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeFloat(RawFileDescriptor fd, float data) {
        return writeInt(fd, Float.floatToIntBits(data));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean writeDouble(RawFileDescriptor fd, double data) {
        return writeLong(fd, Double.doubleToLongBits(data));
    }

    public static class RawFileOperationSupportHolder {
        private final RawFileOperationSupport littleEndian;
        private final RawFileOperationSupport bigEndian;
        private final RawFileOperationSupport nativeOrder;

        @Platforms(Platform.HOSTED_ONLY.class)
        public RawFileOperationSupportHolder(RawFileOperationSupport littleEndian, RawFileOperationSupport bigEndian, RawFileOperationSupport nativeOrder) {
            this.littleEndian = littleEndian;
            this.bigEndian = bigEndian;
            this.nativeOrder = nativeOrder;
        }

        @Fold
        static RawFileOperationSupportHolder singleton() {
            return ImageSingletons.lookup(RawFileOperationSupportHolder.class);
        }

        @Fold
        public static RawFileOperationSupport getLittleEndian() {
            return singleton().littleEndian;
        }

        @Fold
        public static RawFileOperationSupport getBigEndian() {
            return singleton().bigEndian;
        }

        @Fold
        public static RawFileOperationSupport getNativeByteOrder() {
            return singleton().nativeOrder;
        }
    }
}
