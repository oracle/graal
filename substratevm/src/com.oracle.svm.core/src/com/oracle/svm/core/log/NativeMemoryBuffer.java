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

import static com.oracle.svm.core.jdk.UninterruptibleUtils.String.charAt;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.thread.ThreadListenerSupport;
import com.oracle.svm.guest.staging.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.guest.staging.core.jdk.UninterruptibleUtils.Math;
import com.oracle.svm.guest.staging.core.thread.ThreadListener;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.UnsignedUtils;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

/// Formats primitive values into a reusable native buffer without Java allocation or safepoints.
/// For ordinary logging, the image-heap facade owns an independent native allocation and indentation
/// on each carrier thread. All facade accesses belong to one uninterruptible region.
/// This formatter has no dependency on legacy log handlers or unified-logging configuration.
/// A diagnostic facade can instead borrow bounded stack storage for one formatting operation;
/// its binding must be cleared before calling interruptible code.
public final class NativeMemoryBuffer implements ThreadListener {
    /// Keeps native pointers and carrier-owned state valid across a formatting operation.
    private static final String BUFFER_ACCESS = "Native buffer state requires an uninterruptible caller.";

    /// Keeps ordinary short messages within the first native allocation.
    private static final int INITIAL_CAPACITY = 1024;

    /// Matches the alignment accepted by shared VM diagnostic formatting.
    private static final int LEFT_ALIGN = 1;

    /// Matches the alignment accepted by shared VM diagnostic formatting.
    private static final int RIGHT_ALIGN = 2;

    /// Message construction, shared by the mutually exclusive unified and legacy GC routes.
    public static final int PRIMARY = 0;

    /// Complete records waiting for a native destination write.
    public static final int OUTPUT = 1;

    /// Temporary formatting whose bytes are copied into another buffer.
    public static final int SCRATCH = 2;

    /// Bounded storage borrowed only while formatting a fatal-diagnostic record.
    public static final int DIAGNOSTIC = 3;

    /// Maximum number of simultaneously used formatting buffers on one carrier.
    private static final int SLOT_COUNT = 4;

    /// Fast thread-local receivers must be constants even in generic formatter methods.
    private static final FastThreadLocalBytes<WordPointer> slots = FastThreadLocalFactory.createBytes(() -> SLOT_COUNT * SubstrateTarget.getWordSize(), "NativeMemoryBuffer.slots");

    /// Index of the carrier's native state used by this facade.
    private final int slot;

    /// Uses LF for message bytes whose output expands the platform line separator later.
    private final boolean useLF;

    /// Creates a facade for a slot unique among the buffers used by this image configuration.
    @Platforms(Platform.HOSTED_ONLY.class)
    public NativeMemoryBuffer(int slot, boolean useLF) {
        VMError.guarantee(slot >= 0 && slot < SLOT_COUNT, "Invalid native buffer slot.");
        this.slot = slot;
        this.useLF = useLF;
    }

    /// Registers cleanup only for buffers used by this image configuration.
    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerCleanup() {
        VMError.guarantee(slot != DIAGNOSTIC, "Borrowed diagnostic storage must not be freed.");
        ThreadListenerSupport.get().register(this);
    }

    /// Binds caller-owned stack storage to the diagnostic facade. The caller must clear the
    /// binding in a `finally` block before leaving its uninterruptible formatting region.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public void initializeStackBuffer(Data storage, int capacity) {
        VMError.guarantee(slot == DIAGNOSTIC && capacity >= 0, "Invalid diagnostic buffer.");
        storage.setPosition(0);
        storage.setIndentation(0);
        storage.setCapacity(capacity);
        setData(storage);
    }

    /// Removes the borrowed pointer before control can reach an interruptible legacy handler.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public void clearStackBuffer() {
        VMError.guarantee(slot == DIAGNOSTIC, "Only diagnostic buffers borrow storage.");
        setData(Word.nullPointer());
    }

    /// Includes the native header in a caller's bounded stack allocation.
    @Fold
    public static int stackBufferSize(int capacity) {
        VMError.guarantee(capacity >= 0 && capacity <= maximumCapacity(), "Invalid diagnostic buffer capacity.");
        return headerSizeAsInt() + capacity;
    }

    /// Appends text using the same byte narrowing as low-level VM diagnostics.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer string(String value) {
        String text = value == null ? "null" : value;
        Data data = reserve(text.length());
        int position = data.getPosition();
        CCharPointer bytes = dataStart(data);
        for (int index = 0; index < text.length(); index++) {
            bytes.write(position + index, (byte) charAt(text, index));
        }
        data.setPosition(position + text.length());
        return this;
    }

    /// Appends aligned text without constructing padding strings.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer string(String value, int width, int align) {
        String text = value == null ? "null" : value;
        if (align == RIGHT_ALIGN) {
            spaces(width - text.length());
        }
        string(text);
        if (align == LEFT_ALIGN) {
            spaces(width - text.length());
        }
        return this;
    }

    /// Appends an aligned unsigned word.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer unsigned(WordBase value, int width, int align) {
        long remaining = value.rawValue();
        int digits = 1;
        while (Long.compareUnsigned(remaining, 10) >= 0) {
            remaining = Long.divideUnsigned(remaining, 10);
            digits++;
        }
        if (align == RIGHT_ALIGN) {
            spaces(width - digits);
        }
        unsigned(value);
        if (align == LEFT_ALIGN) {
            spaces(width - digits);
        }
        return this;
    }

    /// Appends a native byte range that does not refer to this buffer, whose storage may grow.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer string(CCharPointer bytes, int length) {
        if (length > 0) {
            if (bytes.isNull()) {
                return string("null");
            }
            rawBytes(bytes, Word.unsigned(length));
        }
        return this;
    }

    /// Appends a single byte.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer character(char value) {
        Data data = reserve(1);
        int position = data.getPosition();
        dataStart(data).write(position, (byte) value);
        data.setPosition(position + 1);
        return this;
    }

    /// Appends a line separator followed by the current indentation.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer newline() {
        if (!useLF && Platform.includedIn(Platform.WINDOWS.class)) {
            character('\r');
        }
        character('\n');
        return spaces(getIndentation());
    }

    /// Appends spaces without allocating padding text.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer spaces(int count) {
        for (int index = 0; index < count; index++) {
            character(' ');
        }
        return this;
    }

    /// Changes indentation without starting another line.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer redent(boolean add) {
        Data data = reserve(0);
        data.setIndentation(Math.max(0, data.getIndentation() + (add ? 2 : -2)));
        return this;
    }

    /// Changes indentation and starts another line.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer indent(boolean add) {
        return redent(add).newline();
    }

    /// Clears indentation for subsequent lines.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer resetIndentation() {
        reserve(0).setIndentation(0);
        return this;
    }

    /// Appends a signed decimal value, including `Long.MIN_VALUE`.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer signed(long value) {
        return number(value, 10, true);
    }

    /// Sign-extends `value` before decimal formatting.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer signed(int value) {
        return signed((long) value);
    }

    /// Formats the word's raw bits as a signed decimal value.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer signed(WordBase value) {
        return signed(value.rawValue());
    }

    /// Formats all 64 bits as an unsigned decimal value.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer unsigned(long value) {
        return number(value, 10, false);
    }

    /// Zero-extends `value` before decimal formatting.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer unsigned(int value) {
        return unsigned(value & 0xffff_ffffL);
    }

    /// Formats the word's raw bits as an unsigned decimal value.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer unsigned(WordBase value) {
        return unsigned(value.rawValue());
    }

    /// Appends unsigned hexadecimal digits preceded by `0x`.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer hex(long value) {
        return string("0x").number(value, 16, false);
    }

    /// Zero-extends `value` before hexadecimal formatting.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer hex(int value) {
        return hex(value & 0xffff_ffffL);
    }

    /// Formats the word's raw bits as unsigned hexadecimal digits.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer hex(WordBase value) {
        return hex(value.rawValue());
    }

    /// Pads hexadecimal output to sixteen digits after `0x`.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer zhex(long value) {
        return string("0x").number(value, 16, false, Long.BYTES * 2);
    }

    /// Pads the word's raw bits to the same hexadecimal width as a `long`.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer zhex(WordBase value) {
        return zhex(value.rawValue());
    }

    /// Appends the lowercase boolean literal.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer bool(boolean value) {
        return string(value ? "true" : "false");
    }

    /// Appends a truncated fixed-point fraction using the legacy VM diagnostic conventions.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer rational(long numerator, long denominator, long decimals) {
        VMError.guarantee(denominator != 0 && decimals >= 0, "Invalid native buffer rational.");
        unsigned(numerator / denominator);
        if (decimals > 0) {
            character('.');
            long remainder = Math.abs(numerator) % Math.abs(denominator);
            for (int index = 0; index < decimals; index++) {
                remainder *= 10;
                unsigned(remainder / Math.abs(denominator));
                remainder %= Math.abs(denominator);
            }
        }
        return this;
    }

    /// Appends an unsigned-word rational value.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer rational(UnsignedWord numerator, long denominator, long decimals) {
        return rational(numerator.rawValue(), denominator, decimals);
    }

    /// Appends a number in the specified radix.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public NativeMemoryBuffer number(long value, int radix, boolean signed) {
        return number(value, radix, signed, 0);
    }

    /// Formats backwards into a stack buffer; unsigned arithmetic also handles `Long.MIN_VALUE`.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    private NativeMemoryBuffer number(long value, int radix, boolean signed, int minimumDigits) {
        VMError.guarantee(radix >= 2 && radix <= 36, "Invalid native buffer radix.");
        CCharPointer bytes = UnsafeStackValue.get(Long.SIZE + 1);
        int start = Long.SIZE + 1;
        boolean negative = signed && value < 0;
        long remaining = negative ? -value : value;
        do {
            long digit = Long.remainderUnsigned(remaining, radix);
            bytes.write(--start, (byte) (digit + (digit < 10 ? '0' : 'a' - 10)));
            remaining = Long.divideUnsigned(remaining, radix);
        } while (remaining != 0);
        while (Long.SIZE + 1 - start < minimumDigits) {
            bytes.write(--start, (byte) '0');
        }
        if (negative) {
            bytes.write(--start, (byte) '-');
        }
        return string(bytes.addressOf(start), Long.SIZE + 1 - start);
    }

    /// Appends raw log bytes, growing the current thread's native buffer when necessary.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    private NativeMemoryBuffer rawBytes(CCharPointer bytes, UnsignedWord length) {
        int maximumCapacity = maximumCapacity();
        VMError.guarantee(length.belowOrEqual(Word.unsigned(maximumCapacity)), "NativeMemoryBuffer input is too large.");
        int byteCount = (int) length.rawValue();
        if (byteCount == 0) {
            return this;
        }
        VMError.guarantee(bytes.isNonNull(), "NativeMemoryBuffer received a null input buffer.");

        Data data = reserve(byteCount);
        CCharPointer target = dataStart(data);
        int position = data.getPosition();
        for (int index = 0; index < byteCount; index++) {
            target.write(position + index, bytes.read(index));
        }
        data.setPosition(position + byteCount);
        return this;
    }

    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public int getIndentation() {
        Data data = data();
        return data.isNull() ? 0 : data.getIndentation();
    }

    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public CCharPointer getBuffer() {
        Data data = data();
        return data.isNull() ? Word.nullPointer() : dataStart(data);
    }

    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public int getPosition() {
        Data data = data();
        return data.isNull() ? 0 : data.getPosition();
    }

    /// Copies the accumulated bytes without entering another logging subsystem.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public void writeTo(NativeMemoryBuffer other) {
        VMError.guarantee(other.slot != slot, "Native buffer copying requires distinct slots.");
        other.string(getBuffer(), getPosition());
    }

    /// Retains the native allocation for reuse by the next message.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public void reset() {
        reset(data());
    }

    /// Reserves enough space before appending, with overflow-checked allocation arithmetic.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    private Data reserve(int count) {
        Data data = data();
        int position = data.isNull() ? 0 : data.getPosition();
        VMError.guarantee(count >= 0 && count <= maximumCapacity() - position, "Native logging buffer capacity exceeded.");
        if (data.isNull() || position + count > data.getCapacity()) {
            grow(position + count);
            data = data();
        }
        return data;
    }

    @Override
    @Uninterruptible(reason = "Frees carrier-owned storage after Java execution, including launcher handoff.")
    public void afterThreadRun() {
        release(CurrentIsolate.getCurrentThread());
    }

    @Override
    @Uninterruptible(reason = "Frees buffers allocated during the remainder of thread exit.")
    public void afterThreadExit(IsolateThread isolateThread, Thread javaThread) {
        release(isolateThread);
    }

    /// Clears and frees the allocation owned by `isolateThread`. The caller ensures that the
    /// thread cannot concurrently use the buffer. Repeated cleanup is harmless; borrowed
    /// diagnostic storage must instead be unbound with [#clearStackBuffer()].
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    public void release(IsolateThread isolateThread) {
        VMError.guarantee(slot != DIAGNOSTIC, "Borrowed diagnostic storage must not be freed.");
        WordPointer threadSlots = slots.getAddress(isolateThread);
        Data data = threadSlots.read(slot);
        threadSlots.write(slot, Word.nullPointer());
        NullableNativeMemory.free(data);
    }

    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    private static void reportFailure() {
        StdErrWriter.write("Could not grow native logging buffer.\n");
    }

    /// Applies doubling growth algorithm to the native buffer.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    private void grow(int minimumCapacity) {
        VMError.guarantee(slot != DIAGNOSTIC, "Bounded diagnostic buffer capacity exceeded.");
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
        guaranteeSuccessfulAllocation(newData);
        if (oldData.isNull()) {
            newData.setPosition(0);
            newData.setIndentation(0);
        }
        newData.setCapacity(newCapacity);
        setData(newData);
    }

    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    private static void guaranteeValidMinimumCapacity(int minimumCapacity, int maximumCapacity) {
        if (minimumCapacity < 0 || minimumCapacity > maximumCapacity) {
            reportFailure();
            throw VMError.shouldNotReachHere("NativeMemoryBuffer capacity is too large.");
        }
    }

    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    private static void guaranteeSuccessfulAllocation(Data newData) {
        if (newData.isNull()) {
            reportFailure();
            throw VMError.shouldNotReachHere("Could not grow the NativeMemoryBuffer buffer.");
        }
    }

    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    private Data data() {
        return slots.getAddress().read(slot);
    }

    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    private void setData(Data value) {
        slots.getAddress().write(slot, value);
    }

    /// Resets a resolved thread-local buffer without looking up its storage owner again.
    @Uninterruptible(reason = BUFFER_ACCESS, callerMustBe = true)
    private static void reset(Data data) {
        if (data.isNonNull()) {
            data.setPosition(0);
            data.setIndentation(0);
        }
    }

    /// Resolves the bytes of caller-owned storage. If its owner is a stack frame, the caller must
    /// prevent continuation unmounting until the last use of the returned pointer.
    @Uninterruptible(reason = Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static CCharPointer dataStart(Data data) {
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
        VMError.guarantee(size >= 0 && size <= Integer.MAX_VALUE, "NativeMemoryBuffer header is too large.");
        return (int) size;
    }

    /// Returns the largest data capacity whose complete allocation fits in a signed integer.
    @Fold
    static int maximumCapacity() {
        return Integer.MAX_VALUE - headerSizeAsInt();
    }

    // @formatter:off
    /// Native header followed immediately by inline byte storage.
    ///
    /// ```c
    /// struct NativeMemoryBuffer.Data {
    ///     int position;
    ///     int capacity;
    ///     int indentation;
    ///     unsigned char bytes[]; // capacity bytes of inline storage
    /// };
    /// ```
    @RawStructure
    public interface Data extends PointerBase {
        @RawField int  getPosition();
        @RawField void setPosition(int value);

        @RawField int  getIndentation();
        @RawField void setIndentation(int value);

        @RawField int  getCapacity();
        @RawField void setCapacity(int value);
    }
    // @formatter:on
}
