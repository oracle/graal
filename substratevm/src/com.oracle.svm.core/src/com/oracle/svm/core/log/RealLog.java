/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.charset.StandardCharsets;

import org.graalvm.compiler.core.common.calc.UnsignedMath;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.JDKUtils;
import com.oracle.svm.core.util.VMError;

public class RealLog extends Log {

    private boolean autoflush = false;
    private int indent = 0;

    protected RealLog() {
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log string(String value) {
        rawString(value == null ? "null" : value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log string(String str, int fill, int align) {

        int spaces = fill - str.length();

        if (align == RIGHT_ALIGN) {
            spaces(spaces);
        }

        string(str);

        if (align == LEFT_ALIGN) {
            spaces(spaces);
        }

        return this;
    }

    private static final char[] NULL_CHARS = "null".toCharArray();

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log string(char[] value) {
        rawString(value == null ? NULL_CHARS : value);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log string(byte[] value, int offset, int length) {
        if (value == null) {
            rawString("null");
        } else if ((offset < 0) || (offset > value.length) || (length < 0) || ((offset + length) > value.length) || ((offset + length) < 0)) {
            rawString("OUT OF BOUNDS");
        } else if (Heap.getHeap().isInImageHeap(value)) {
            rawBytes(NonmovableArrays.addressOf(NonmovableArrays.fromImageHeap(value), offset), WordFactory.unsigned(length));
        } else {
            rawBytes(value, offset, length);
        }
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log string(byte[] value) {
        string(value, 0, value.length);
        return this;
    }

    /**
     * Write a raw java array by copying it first to a stack allocated temporary buffer. Caller must
     * ensure that the offset and length are within bounds.
     */
    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    private void rawBytes(Object value, int offset, int length) {
        /*
         * Stack allocation needs an allocation size that is a compile time constant, so we split
         * the byte array up in multiple chunks and write them separately.
         */
        final int chunkSize = 256;
        final CCharPointer bytes = StackValue.get(chunkSize);

        int chunkOffset = offset;
        int inputLength = length;
        while (inputLength > 0) {
            int chunkLength = Math.min(inputLength, chunkSize);

            for (int i = 0; i < chunkLength; i++) {
                int index = chunkOffset + i;
                byte b;
                if (value instanceof String) {
                    b = (byte) charAt((String) value, index);
                } else if (value instanceof char[]) {
                    b = (byte) ((char[]) value)[index];
                } else {
                    b = ((byte[]) value)[index];
                }
                bytes.write(i, b);
            }
            rawBytes(bytes, WordFactory.unsigned(chunkLength));

            chunkOffset += chunkLength;
            inputLength -= chunkLength;
        }
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "String.charAt can allocate exception, but we know that our access is in bounds")
    private static char charAt(String s, int index) {
        return s.charAt(index);
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log string(CCharPointer value) {
        if (value.notEqual(WordFactory.nullPointer())) {
            rawBytes(value, SubstrateUtil.strlen(value));
        } else {
            rawString("null");
        }
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log character(char value) {
        CCharPointer bytes = StackValue.get(CCharPointer.class);
        bytes.write((byte) value);
        rawBytes(bytes, WordFactory.unsigned(1));
        return this;
    }

    private static final byte[] NEWLINE = System.lineSeparator().getBytes(StandardCharsets.US_ASCII);

    @Override
    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log newline() {
        string(NEWLINE);
        if (autoflush) {
            flush();
        }
        spaces(indent);
        return this;
    }

    /**
     * Prints the value according according to the given format specification. The digits '0' to '9'
     * followed by the letters 'a' to 'z' are used to represent the digits.
     *
     * @param value The value to print.
     * @param radix The base of the value, between 2 and 36.
     * @param signed true if the value should be treated as a signed value (and the digits are
     *            preceded by '-' for negative values).
     */

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log number(long value, int radix, boolean signed) {
        number(value, radix, signed, 0, NO_ALIGN);
        return this;
    }

    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    private Log number(long value, int radix, boolean signed, int fill, int align) {
        if (radix < 2 || radix > 36) {
            /* Ignore bogus parameter value. */
            return this;
        }

        /* Enough space for 64 digits in binary format, and the '-' for a negative value. */
        final int chunkSize = Long.SIZE + 1;
        CCharPointer bytes = StackValue.get(chunkSize, CCharPointer.class);
        int charPos = chunkSize;

        boolean negative = signed && value < 0;
        long curValue;
        if (negative) {
            /*
             * We do not have to worry about the overflow of Long.MIN_VALUE here, since we treat
             * curValue as an unsigned value.
             */
            curValue = -value;
        } else {
            curValue = value;
        }

        while (UnsignedMath.aboveOrEqual(curValue, radix)) {
            charPos--;
            bytes.write(charPos, digit(Long.remainderUnsigned(curValue, radix)));
            curValue = Long.divideUnsigned(curValue, radix);
        }
        charPos--;
        bytes.write(charPos, digit(curValue));

        if (negative) {
            charPos--;
            bytes.write(charPos, (byte) '-');
        }

        int length = chunkSize - charPos;

        if (align == RIGHT_ALIGN) {
            int spaces = fill - length;
            spaces(spaces);
        }

        rawBytes(bytes.addressOf(charPos), WordFactory.unsigned(length));

        if (align == LEFT_ALIGN) {
            int spaces = fill - length;
            spaces(spaces);
        }

        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log signed(WordBase value) {
        number(value.rawValue(), 10, true);
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log signed(int value) {
        number(value, 10, true);
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log signed(long value) {
        number(value, 10, true);
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log unsigned(WordBase value) {
        number(value.rawValue(), 10, false);
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log unsigned(WordBase value, int fill, int align) {
        number(value.rawValue(), 10, false, fill, align);
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log unsigned(int value) {
        // unsigned expansion from int to long
        number(value & 0xffffffffL, 10, false);
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log unsigned(long value) {
        number(value, 10, false);
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log unsigned(long value, int fill, int align) {
        number(value, 10, false, fill, align);
        return this;
    }

    /**
     * Fast printing of a rational numbers without allocation memory.
     *
     * <p>
     * Note: this method will not perform rounding.
     * </p>
     * <p>
     * Note: this method will print all trailing zeros, i.e., {@code rational(1, 2, 4)} prints
     * {@code 0.5000}
     * </p>
     *
     * @param numerator Numerator in division
     * @param denominator or divisor
     * @param decimals number of decimals after the . to be printed. Note that no rounding is
     *            performed and trailing zeros are printed.
     */

    @Override
    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log rational(long numerator, long denominator, long decimals) {
        if (denominator == 0) {
            throw VMError.shouldNotReachHere("Division by zero");
        }
        if (decimals < 0) {
            throw VMError.shouldNotReachHere("Number of decimals smaller than 0");
        }

        long value = numerator / denominator;
        unsigned(value);
        if (decimals > 0) {
            character('.');

            long positiveNumerator = Math.abs(numerator);
            long positiveDenominator = Math.abs(denominator);

            long remainder = positiveNumerator % positiveDenominator;
            for (int i = 0; i < decimals; i++) {
                remainder *= 10;
                unsigned(remainder / positiveDenominator);
                remainder = remainder % positiveDenominator;
            }
        }
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log hex(WordBase value) {
        string("0x").number(value.rawValue(), 16, false);
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log hex(int value) {
        string("0x").number(value & 0xffffffffL, 16, false);
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log hex(long value) {
        string("0x").number(value, 16, false);
        return this;
    }

    private static final byte[] trueString = Boolean.TRUE.toString().getBytes();
    private static final byte[] falseString = Boolean.FALSE.toString().getBytes();

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log bool(boolean value) {
        string(value ? trueString : falseString);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log object(Object value) {
        if (value == null) {
            string("null");
        } else {
            string(value.getClass().getName());
            string("@");
            zhex(Word.objectToUntrackedPointer(value));
        }
        return this;
    }

    private static final char spaceChar = ' ';

    @Override
    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log spaces(int value) {
        for (int i = 0; i < value; i += 1) {
            character(spaceChar);
        }
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log flush() {
        ImageSingletons.lookup(LogHandler.class).flush();
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log autoflush(boolean onOrOff) {
        autoflush = onOrOff;
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log redent(boolean addOrRemove) {
        int delta = addOrRemove ? 2 : -2;
        indent = Math.max(0, indent + delta);
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public final Log indent(boolean addOrRemove) {
        redent(addOrRemove).newline();
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log resetIndentation() {
        indent = 0;
        return this;
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    private static byte digit(long d) {
        return (byte) (d + (d < 10 ? '0' : 'a' - 10));
    }

    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    protected Log rawBytes(CCharPointer bytes, UnsignedWord length) {
        ImageSingletons.lookup(LogHandler.class).log(bytes, length);
        return this;
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    private void rawString(String value) {
        rawBytes(value, 0, value.length());
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    private void rawString(char[] value) {
        rawBytes(value, 0, value.length);
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log zhex(WordBase value) {
        zhex(value.rawValue());
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log zhex(long value) {
        string("0x");
        int zeros = Long.numberOfLeadingZeros(value);
        int hexZeros = zeros / 4;
        for (int i = 0; i < hexZeros; i += 1) {
            character('0');
        }
        if (value != 0) {
            number(value, 16, false);
        }
        return this;
    }

    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    private Log zhex(int value, int wordSizeInBytes) {
        string("0x");
        int zeros = Integer.numberOfLeadingZeros(value) - 32 + (wordSizeInBytes * 8);
        int hexZeros = zeros / 4;
        for (int i = 0; i < hexZeros; i += 1) {
            character('0');
        }
        if (value != 0) {
            number(value & 0xffffffffL, 16, false);
        }
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log zhex(int value) {
        zhex(value, 4);
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log zhex(short value) {
        int intValue = value;
        zhex(intValue & 0xffff, 2);
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log zhex(byte value) {
        int intValue = value;
        zhex(intValue & 0xff, 1);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log hexdump(PointerBase from, int wordSize, int numWords) {
        Pointer base = WordFactory.pointer(from.rawValue());
        int sanitizedWordsize = wordSize > 0 ? Integer.highestOneBit(Math.min(wordSize, 8)) : 2;
        for (int offset = 0; offset < sanitizedWordsize * numWords; offset += sanitizedWordsize) {
            if (offset % 16 == 0) {
                zhex(base.add(offset).rawValue());
                string(":");
            }
            string(" ");
            switch (sanitizedWordsize) {
                case 1:
                    zhex(base.readByte(offset));
                    break;
                case 2:
                    zhex(base.readShort(offset));
                    break;
                case 4:
                    zhex(base.readInt(offset));
                    break;
                case 8:
                    zhex(base.readLong(offset));
                    break;
            }
            if ((offset + sanitizedWordsize) % 16 == 0 && (offset + sanitizedWordsize) < sanitizedWordsize * numWords) {
                newline();
            }
        }
        return this;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log exception(Throwable t) {
        exception(t, Integer.MAX_VALUE);
        return this;
    }

    @Override
    @NeverInline("Logging is always slow-path code")
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate when logging.")
    public Log exception(Throwable t, int maxFrames) {
        if (t == null) {
            object(t);
            return this;
        }

        /*
         * We do not want to call getMessage(), since it can be overridden by subclasses of
         * Throwable. So we access the raw detailMessage directly from the field in Throwable. That
         * is better than printing nothing.
         */
        String detailMessage = JDKUtils.getRawMessage(t);
        StackTraceElement[] stackTrace = JDKUtils.getRawStackTrace(t);

        string(t.getClass().getName()).string(": ").string(detailMessage);
        if (stackTrace != null) {
            int i;
            for (i = 0; i < stackTrace.length && i < maxFrames; i++) {
                StackTraceElement element = stackTrace[i];
                if (element != null) {
                    newline();
                    string("    at ").string(element.getClassName()).string(".").string(element.getMethodName());
                    string("(").string(element.getFileName()).string(":").signed(element.getLineNumber()).string(")");
                }
            }
            int remaining = stackTrace.length - i;
            if (remaining > 0) {
                newline().string("    ... ").unsigned(remaining).string(" more");
            }
        }
        newline();
        return this;
    }
}
