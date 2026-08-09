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
package com.oracle.svm.core.log;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.logging.LogLevel;
import com.oracle.svm.core.logging.LogTagSet;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.shared.util.VMError;

// Stores log bytes in native memory.
public final class NativeMemoryLog extends RealLog {

    private static final int INITIAL_CAPACITY = 1024;

    /// Native memory holding the accumulated bytes.
    private CCharPointer buffer;

    /// Number of bytes currently stored in the native buffer.
    private int position;

    /// Number of bytes available in the native buffer.
    private int capacity;

    /// Appends raw log bytes, growing the native buffer when necessary.
    @Override
    protected Log rawBytes(CCharPointer bytes, UnsignedWord length) {
        long lengthValue = length.rawValue();
        VMError.guarantee(lengthValue <= Integer.MAX_VALUE, "NativeMemoryLog input is too large.");
        int byteCount = (int) lengthValue;
        if (byteCount == 0) {
            return this;
        }
        VMError.guarantee(bytes.isNonNull(), "NativeMemoryLog received a null input buffer.");

        long requiredCapacity = (long) position + byteCount;
        if (requiredCapacity > capacity) {
            grow(requiredCapacity);
        }

        for (int index = 0; index < byteCount; index++) {
            buffer.write(position + index, bytes.read(index));
        }
        position += byteCount;
        return this;
    }

    /// Does not flush because the native buffer remains owned by this log.
    @Override
    public Log flush() {
        return this;
    }

    public CCharPointer getBuffer() {
        return buffer;
    }

    public int getPosition() {
        return position;
    }

    /// Reserves native storage for at least `minimumCapacity` bytes.
    public void reserve(int minimumCapacity) {
        VMError.guarantee(minimumCapacity >= 0, "NativeMemoryLog capacity must not be negative.");
        if (minimumCapacity > capacity) {
            grow(minimumCapacity);
        }
    }

    /// Writes the accumulated bytes to `other` without creating a Java string.
    public void writeTo(Log other) {
        if (position != 0) {
            other.string(buffer, position);
        }
    }

    /// Writes the selected native byte range to `other` without creating a Java string.
    public void writeRangeTo(Log other, int start, int end) {
        VMError.guarantee(other != null, "NativeMemoryLog cannot write to a null log.");
        VMError.guarantee(start >= 0 && start <= end && end <= position, "Invalid NativeMemoryLog range.");
        if (start != end) {
            other.string(buffer.addressOf(start), end - start);
        }
    }

    /// Releases the native buffer and restores this log to its initial state.
    public void clear() {
        NullableNativeMemory.free(buffer);
        buffer = Word.nullPointer();
        position = 0;
        capacity = 0;
    }

    /// Clears the current contents while retaining the allocated native buffer.
    public void reset() {
        position = 0;
    }

    /// Rewinds the write position to an earlier byte in the native buffer.
    public void rewind(int newPosition) {
        VMError.guarantee(newPosition >= 0 && newPosition < position, "NativeMemoryLog rewind position must be before the current position.");
        position = newPosition;
    }

    /// Applies doubling growth algorithm to the native buffer.
    private void grow(long minimumCapacity) {
        boolean initial = capacity == 0;
        long newCapacity = initial ? INITIAL_CAPACITY : (long) capacity * 2;
        if (newCapacity < minimumCapacity) {
            newCapacity = minimumCapacity;
        }
        VMError.guarantee(newCapacity <= Integer.MAX_VALUE, "NativeMemoryLog capacity is too large.");

        buffer = initial ? //
                        NullableNativeMemory.malloc((int) newCapacity, NmtCategory.Logging) : //
                        NullableNativeMemory.realloc(buffer, (int) newCapacity, NmtCategory.Logging);
        VMError.guarantee(buffer.isNonNull(), "Could not grow the NativeMemoryLog buffer.");
        capacity = (int) newCapacity;
    }

    /// Logs the capacity of this buffer if it's non-zero.
    ///
    /// @param name name to use for the log message
    public void logNativeBufferUsage(String name, LogTagSet tagSet, LogLevel level) {
        if (capacity != 0) {
            tagSet.log(level, name + " = " + capacity + " bytes");
        }
    }
}
