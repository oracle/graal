/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.logging;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.log.RealLog;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.shared.util.VMError;

/// Stores log bytes in an owner-provided native buffer.
public final class NativeMemoryLog extends RealLog {

    private static final int INITIAL_CAPACITY = 1024;

    /// Identifies the fast thread-local buffer used by a stateless log facade.
    public enum BufferKind {
        MESSAGE,
        OUTPUT,
        DECORATOR,
        RECORD
    }

    private final BufferKind kind;

    /// Holds a record-owned buffer when this facade is used by the asynchronous queue.
    private Data recordData;

    public NativeMemoryLog() {
        this(BufferKind.RECORD);
    }

    public NativeMemoryLog(BufferKind kind) {
        this.kind = kind;
    }

    /// Appends raw log bytes, growing the owner-provided native buffer when necessary.
    @Override
    protected Log rawBytes(CCharPointer bytes, UnsignedWord length) {
        long lengthValue = length.rawValue();
        VMError.guarantee(lengthValue <= Integer.MAX_VALUE, "NativeMemoryLog input is too large.");
        int byteCount = (int) lengthValue;
        if (byteCount == 0) {
            return this;
        }
        VMError.guarantee(bytes.isNonNull(), "NativeMemoryLog received a null input buffer.");

        Data data = data();
        int position = data.isNull() ? 0 : data.getPosition();
        int capacity = data.isNull() ? 0 : data.getCapacity();
        long requiredCapacity = (long) position + byteCount;
        if (requiredCapacity > capacity) {
            grow(requiredCapacity);
            data = data();
        }

        CCharPointer target = dataStart(data);
        position = data.getPosition();
        for (int index = 0; index < byteCount; index++) {
            target.write(position + index, bytes.read(index));
        }
        data.setPosition(position + byteCount);
        return this;
    }

    /// Does not flush because the native buffer remains owned by this log.
    @Override
    public Log flush() {
        return this;
    }

    public CCharPointer getBuffer() {
        Data data = data();
        return data.isNull() ? Word.nullPointer() : dataStart(data);
    }

    public int getPosition() {
        Data data = data();
        return data.isNull() ? 0 : data.getPosition();
    }

    /// Reserves native storage for at least `minimumCapacity` bytes.
    public void reserve(int minimumCapacity) {
        VMError.guarantee(minimumCapacity >= 0, "NativeMemoryLog capacity must not be negative.");
        Data data = data();
        if (data.isNull() || minimumCapacity > data.getCapacity()) {
            grow(minimumCapacity);
        }
    }

    /// Writes the accumulated bytes to `other` without creating a Java string.
    public void writeTo(Log other) {
        int position = getPosition();
        if (position != 0) {
            other.string(getBuffer(), position);
        }
    }

    /// Writes the selected native byte range to `other` without creating a Java string.
    public void writeRangeTo(Log other, int start, int end) {
        VMError.guarantee(other != null, "NativeMemoryLog cannot write to a null log.");
        int position = getPosition();
        VMError.guarantee(start >= 0 && start <= end && end <= position, "Invalid NativeMemoryLog range.");
        if (start != end) {
            other.string(getBuffer().addressOf(start), end - start);
        }
    }

    /// Releases the native buffer and restores this log to its initial state.
    public void clear() {
        Data data = data();
        if (kind == BufferKind.RECORD) {
            NullableNativeMemory.free(data);
            recordData = Word.nullPointer();
        } else {
            LogThreadLocal.setBuffer(kind, Word.nullPointer());
            NullableNativeMemory.free(data);
        }
    }

    /// Clears the current contents while retaining the allocated native buffer.
    public void reset() {
        reset(data());
    }

    /// Applies doubling growth algorithm to the native buffer.
    private void grow(long minimumCapacity) {
        long newCapacity = INITIAL_CAPACITY;
        Data oldData = data();
        if (oldData.isNonNull()) {
            newCapacity = Math.max(newCapacity, oldData.getCapacity());
            while (newCapacity < minimumCapacity) {
                newCapacity = Math.min(Long.MAX_VALUE, newCapacity * 2);
                VMError.guarantee(newCapacity >= minimumCapacity, "NativeMemoryLog capacity is too large.");
            }
        }
        VMError.guarantee(newCapacity <= Integer.MAX_VALUE, "NativeMemoryLog capacity is too large.");

        UnsignedWord size = headerSize().add(Word.unsigned(newCapacity));
        Data newData = oldData.isNull() ? NullableNativeMemory.malloc(size, NmtCategory.Logging) : NullableNativeMemory.realloc(oldData, size, NmtCategory.Logging);
        VMError.guarantee(newData.isNonNull(), "Could not grow the NativeMemoryLog buffer.");
        if (oldData.isNull()) {
            newData.setPosition(0);
        }
        newData.setCapacity((int) newCapacity);
        setData(newData);
    }

    private Data data() {
        return kind == BufferKind.RECORD ? recordData : LogThreadLocal.getBuffer(kind);
    }

    private void setData(Data value) {
        if (kind == BufferKind.RECORD) {
            recordData = value;
        } else {
            LogThreadLocal.setBuffer(kind, value);
        }
    }

    /// Resets an owner-provided buffer without resolving its storage owner.
    static void reset(Data data) {
        if (data.isNonNull()) {
            data.setPosition(0);
        }
    }

    private static CCharPointer dataStart(Data data) {
        return (CCharPointer) ((Pointer) data).add(headerSize());
    }

    private static UnsignedWord headerSize() {
        return UnsignedUtils.roundUp(SizeOf.unsigned(Data.class), Word.unsigned(SubstrateTarget.getWordSize()));
    }

    // @formatter:off
    /// A struct that has 2 fields followed immediately by inline byte storage.
    ///
    /// ```c
    /// struct NativeMemoryLog.Data {
    ///     int position;
    ///     int capacity;
    ///     unsigned char bytes[]; // capacity bytes of inline storage
    /// };
    /// ```
    @RawStructure
    public interface Data extends PointerBase {
        @RawField int  getPosition();
        @RawField void setPosition(int value);

        @RawField int  getCapacity();
        @RawField void setCapacity(int value);
    }
    // @formatter:on
}
