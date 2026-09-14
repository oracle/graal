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

import jdk.graal.compiler.api.replacements.Fold;

/// Stores log bytes in a native buffer owned by the current thread's logging state.
public final class NativeMemoryLog extends RealLog {

    private static final int INITIAL_CAPACITY = 1024;

    /// Identifies the fast thread-local buffer used by a stateless log facade.
    public enum BufferKind {
        MESSAGE,
        OUTPUT,
        DECORATOR
    }

    private final BufferKind kind;

    public NativeMemoryLog(BufferKind kind) {
        this.kind = kind;
    }

    /// Appends raw log bytes, growing the current thread's native buffer when necessary.
    @Override
    protected Log rawBytes(CCharPointer bytes, UnsignedWord length) {
        int maximumCapacity = maximumCapacity();
        VMError.guarantee(length.belowOrEqual(Word.unsigned(maximumCapacity)), "NativeMemoryLog input is too large.");
        int byteCount = (int) length.rawValue();
        if (byteCount == 0) {
            return this;
        }
        VMError.guarantee(bytes.isNonNull(), "NativeMemoryLog received a null input buffer.");

        Data data = data();
        int position = data.isNull() ? 0 : data.getPosition();
        int capacity = data.isNull() ? 0 : data.getCapacity();
        VMError.guarantee(position >= 0 && position <= capacity && capacity <= maximumCapacity, "Invalid NativeMemoryLog buffer state.");
        VMError.guarantee(byteCount <= maximumCapacity - position, "NativeMemoryLog capacity is too large.");
        int requiredCapacity = position + byteCount;
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

    /// Writes the accumulated bytes to `other` without creating a Java string.
    public void writeTo(Log other) {
        int position = getPosition();
        if (position != 0) {
            other.string(getBuffer(), position);
        }
    }

    /// Releases the native buffer and restores this log to its initial state.
    public void clear() {
        Data data = data();
        LogThreadLocal.setBuffer(kind, Word.nullPointer());
        NullableNativeMemory.free(data);
    }

    /// Clears the current contents while retaining the allocated native buffer.
    public void reset() {
        reset(data());
    }

    /// Applies doubling growth algorithm to the native buffer.
    private void grow(int minimumCapacity) {
        int maximumCapacity = maximumCapacity();
        guaranteeValidMinimumCapacity(minimumCapacity, maximumCapacity);

        int newCapacity = INITIAL_CAPACITY;
        Data oldData = data();
        if (oldData.isNonNull()) {
            int oldCapacity = oldData.getCapacity();
            newCapacity = Math.max(newCapacity, oldCapacity);
        }
        while (newCapacity < minimumCapacity) {
            if (newCapacity > maximumCapacity / 2) {
                newCapacity = maximumCapacity;
            } else {
                newCapacity *= 2;
            }
        }
        int allocationSize = headerSizeAsInt() + newCapacity;
        Data newData = oldData.isNull() ? NullableNativeMemory.malloc(allocationSize, NmtCategory.Logging) : NullableNativeMemory.realloc(oldData, allocationSize, NmtCategory.Logging);
        guaranteeSuccessfulAllocation(newData, allocationSize);
        if (oldData.isNull()) {
            newData.setPosition(0);
        }
        newData.setCapacity(newCapacity);
        setData(newData);
    }

    private static void guaranteeValidMinimumCapacity(int minimumCapacity, int maximumCapacity) {
        if (minimumCapacity < 0 || minimumCapacity > maximumCapacity) {
            Log.log().string("minimum capacity: ").signed(minimumCapacity).newline().newline();
            throw VMError.shouldNotReachHere("NativeMemoryLog capacity is too large.");
        }
    }

    private static void guaranteeSuccessfulAllocation(Data newData, int allocationSize) {
        if (newData.isNull()) {
            Log.log().string("allocation size: ").signed(allocationSize).newline();
            throw VMError.shouldNotReachHere("Could not grow the NativeMemoryLog buffer.");
        }
    }

    private Data data() {
        return LogThreadLocal.getBuffer(kind);
    }

    private void setData(Data value) {
        LogThreadLocal.setBuffer(kind, value);
    }

    /// Resets a resolved thread-local buffer without looking up its storage owner again.
    static void reset(Data data) {
        if (data.isNonNull()) {
            data.setPosition(0);
        }
    }

    private static CCharPointer dataStart(Data data) {
        return (CCharPointer) ((Pointer) data).add(headerSize());
    }

    @Fold
    static UnsignedWord headerSize() {
        return UnsignedUtils.roundUp(SizeOf.unsigned(Data.class), Word.unsigned(SubstrateTarget.getWordSize()));
    }

    /// Returns the native header size as a signed value for checked allocation arithmetic.
    @Fold
    static int headerSizeAsInt() {
        long size = headerSize().rawValue();
        VMError.guarantee(size >= 0 && size <= Integer.MAX_VALUE, "NativeMemoryLog header is too large.");
        return (int) size;
    }

    /// Returns the largest data capacity whose complete allocation fits in a signed integer.
    @Fold
    static int maximumCapacity() {
        return Integer.MAX_VALUE - headerSizeAsInt();
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
