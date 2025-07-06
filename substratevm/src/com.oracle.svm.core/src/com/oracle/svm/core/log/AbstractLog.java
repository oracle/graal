/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.jdk.UninterruptibleUtils.Integer.highestOneBit;
import static com.oracle.svm.core.jdk.UninterruptibleUtils.Math.abs;
import static com.oracle.svm.core.jdk.UninterruptibleUtils.Math.max;
import static com.oracle.svm.core.jdk.UninterruptibleUtils.Math.min;
import static com.oracle.svm.core.jdk.UninterruptibleUtils.String.charAt;

import java.nio.charset.StandardCharsets;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jdk.JDKUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.calc.UnsignedMath;
import jdk.graal.compiler.word.Word;

abstract class AbstractLog implements Log {
    private static final byte[] NEWLINE = System.lineSeparator().getBytes(StandardCharsets.US_ASCII);

    private int indent;

    /** Writes the logging data. This method is used by all the logging methods below. */
    protected abstract Log rawBytes(CCharPointer bytes, UnsignedWord length);

    /**
     * Prints a backtrace and returns the number of frames that were not printed in case that the
     * back trace was truncated.
     */
    protected abstract int printBacktrace(Throwable t, int maxFrames);

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void string0(String value) {
        if (value == null) {
            string0("null");
        } else {
            writeBytes0(value, 0, value.length());
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void string0(String value, int maxLength) {
        if (maxLength <= 0) {
            /* Nothing to do. */
        } else if (value == null) {
            /* Ignore maxLength. */
            string0("null");
        } else {
            int length = min(value.length(), maxLength);
            writeBytes0(value, 0, length);
            if (value.length() > length) {
                string0("...");
            }
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void string0(String s, int fill, int align) {
        String value = (s == null) ? "null" : s;
        int spaces = fill - value.length();
        if (align == Log.RIGHT_ALIGN) {
            spaces0(spaces);
        }

        string0(value);

        if (align == Log.LEFT_ALIGN) {
            spaces0(spaces);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void string0(char[] value) {
        if (value == null) {
            string0("null");
        } else {
            writeBytes0(value, 0, value.length);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void string0(byte[] value) {
        if (value == null) {
            string0("null");
        } else {
            writeBytes0(value, 0, value.length);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void string0(byte[] value, int offset, int length) {
        if (value == null) {
            string0("null");
        } else if ((offset < 0) || (offset > value.length) || (length < 0) || ((offset + length) > value.length) || ((offset + length) < 0)) {
            string0("OUT OF BOUNDS");
        } else if (Heap.getHeap().isInImageHeap(value)) {
            writeBytes0(NonmovableArrays.addressOf(NonmovableArrays.fromImageHeap(value), offset), Word.unsigned(length));
        } else {
            writeBytes0(value, offset, length);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void string0(CCharPointer value) {
        if (value.isNull()) {
            string0("null");
        } else {
            writeBytes0(value, SubstrateUtil.strlen(value));
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void string0(CCharPointer value, int length) {
        if (length <= 0) {
            return;
        }

        if (value.isNull()) {
            string0("null");
        } else {
            writeBytes0(value, Word.unsigned(length));
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void character0(char value) {
        CCharPointer bytes = UnsafeStackValue.get(CCharPointer.class);
        bytes.write((byte) value);
        writeBytes0(bytes, Word.unsigned(1));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void newline0() {
        string0(NEWLINE);
        spaces0(indent);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void number0(long value, int radix, boolean signed) {
        rawNumber0(value, radix, signed, 0, Log.NO_ALIGN);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void signed0(WordBase value) {
        number0(value.rawValue(), 10, true);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void signed0(int value) {
        number0(value, 10, true);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void signed0(long value) {
        number0(value, 10, true);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void signed0(long value, int fill, int align) {
        rawNumber0(value, 10, true, fill, align);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void unsigned0(WordBase value) {
        number0(value.rawValue(), 10, false);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void unsigned0(WordBase value, int fill, int align) {
        rawNumber0(value.rawValue(), 10, false, fill, align);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void unsigned0(int value) {
        // unsigned expansion from int to long
        number0(value & 0xffffffffL, 10, false);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void unsigned0(long value) {
        number0(value, 10, false);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void unsigned0(long value, int fill, int align) {
        rawNumber0(value, 10, false, fill, align);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void rawNumber0(long value, int radix, boolean signed, int fill, int align) {
        if (radix < 2 || radix > 36) {
            /* Ignore bogus parameter value. */
            return;
        }

        /* Enough space for 64 digits in binary format, and the '-' for a negative value. */
        int chunkSize = Long.SIZE + 1;
        CCharPointer bytes = UnsafeStackValue.get(chunkSize, CCharPointer.class);
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

        if (align == Log.RIGHT_ALIGN) {
            int spaces = fill - length;
            spaces0(spaces);
        }

        writeBytes0(bytes.addressOf(charPos), Word.unsigned(length));

        if (align == Log.LEFT_ALIGN) {
            int spaces = fill - length;
            spaces0(spaces);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void rational0(long numerator, long denominator, long decimals) {
        if (denominator == 0) {
            throw VMError.shouldNotReachHere("Division by zero");
        }
        if (decimals < 0) {
            throw VMError.shouldNotReachHere("Number of decimals smaller than 0");
        }

        long value = numerator / denominator;
        unsigned0(value);
        if (decimals > 0) {
            character0('.');

            // we don't care if overflow happens in these abs
            long positiveNumerator = abs(numerator);
            long positiveDenominator = abs(denominator);

            long remainder = positiveNumerator % positiveDenominator;
            for (int i = 0; i < decimals; i++) {
                remainder *= 10;
                unsigned0(remainder / positiveDenominator);
                remainder = remainder % positiveDenominator;
            }
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void rational0(UnsignedWord numerator, long denominator, long decimals) {
        rational0(numerator.rawValue(), denominator, decimals);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void hex0(WordBase value) {
        string0("0x");
        number0(value.rawValue(), 16, false);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void hex0(int value) {
        string0("0x");
        number0(value & 0xffffffffL, 16, false);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void hex0(long value) {
        string0("0x");
        number0(value, 16, false);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void zhex0(WordBase value) {
        zhex0(value.rawValue());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void zhex0(long value) {
        string0("0x");
        int zeros = Long.numberOfLeadingZeros(value);
        int hexZeros = zeros / 4;
        for (int i = 0; i < hexZeros; i += 1) {
            character0('0');
        }
        if (value != 0) {
            number0(value, 16, false);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void zhex0(int value) {
        zhex0(value, 4);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void zhex0(short value) {
        int intValue = value & 0xffff;
        zhex0(intValue, 2);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void zhex0(byte value) {
        int intValue = value & 0xff;
        zhex0(intValue, 1);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void zhex0(int value, int wordSizeInBytes) {
        string0("0x");
        int zeros = Integer.numberOfLeadingZeros(value) - 32 + (wordSizeInBytes * 8);
        int hexZeros = zeros / 4;
        for (int i = 0; i < hexZeros; i += 1) {
            character0('0');
        }
        if (value != 0) {
            number0(value & 0xffffffffL, 16, false);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void hexdump0(PointerBase from, int wordSize, int numWords) {
        hexdump0(from, wordSize, numWords, 16);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void hexdump0(PointerBase from, int wordSize, int numWords, int bytesPerLine) {
        Pointer base = Word.pointer(from.rawValue());
        int sanitizedWordSize = wordSize > 0 ? highestOneBit(min(wordSize, 8)) : 2;
        for (int offset = 0; offset < sanitizedWordSize * numWords; offset += sanitizedWordSize) {
            if (offset % bytesPerLine == 0) {
                zhex0(base.add(offset));
                string0(":");
            }
            string0(" ");
            switch (sanitizedWordSize) {
                case 1:
                    zhex0(base.readByte(offset));
                    break;
                case 2:
                    zhex0(base.readShort(offset));
                    break;
                case 4:
                    zhex0(base.readInt(offset));
                    break;
                case 8:
                    zhex0(base.readLong(offset));
                    break;
            }
            if ((offset + sanitizedWordSize) % bytesPerLine == 0 && (offset + sanitizedWordSize) < sanitizedWordSize * numWords) {
                newline0();
            }
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void redent0(boolean addOrRemove) {
        int delta = addOrRemove ? 2 : -2;
        indent = max(0, indent + delta);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void indent0(boolean addOrRemove) {
        redent0(addOrRemove);
        newline0();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void resetIndentation0() {
        indent = 0;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final int getIndentation0() {
        return indent;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void bool0(boolean value) {
        string0(value ? "true" : "false");
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void object0(Object value) {
        if (value == null) {
            string0("null");
        } else {
            string0(value.getClass().getName());
            string0("@");
            zhex0(Word.objectToUntrackedPointer(value));
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void spaces0(int value) {
        for (int i = 0; i < value; i++) {
            character0(' ');
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void exception0(Throwable t) {
        exception0(t, Integer.MAX_VALUE);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void exception0(Throwable t, int maxFrames) {
        if (t == null) {
            string0("null");
            return;
        }

        Throwable cur = t;
        final int maxCauses = 25;
        for (int i = 0; i < maxCauses && cur != null; i++) {
            if (i > 0) {
                newline0();
                string0("Caused by: ");
            }

            /*
             * We do not want to call getMessage(), since it can be overridden by subclasses of
             * Throwable. So we access the raw detailMessage directly from the field in Throwable.
             * That is better than printing nothing.
             */
            String detailMessage = JDKUtils.getRawMessage(cur);

            string0(cur.getClass().getName());
            string0(": ");
            string0(detailMessage);
            if (!JDKUtils.isStackTraceValid(cur)) {
                /*
                 * We accept that there might be a race with concurrent calls to
                 * `Throwable#fillInStackTrace`, which changes `Throwable#backtrace`. We accept that
                 * and the code can deal with that. Worst case we don't get a stack trace.
                 */
                int remaining = printBacktrace0(cur, maxFrames);
                printRemainingFramesCount0(remaining);
            } else {
                StackTraceElement[] stackTrace = JDKUtils.getRawStackTrace(cur);
                if (stackTrace != null) {
                    int j;
                    for (j = 0; j < stackTrace.length && j < maxFrames; j++) {
                        StackTraceElement element = stackTrace[j];
                        if (element != null) {
                            printJavaFrame0(element.getClassName(), element.getMethodName(), element.getFileName(), element.getLineNumber());
                        }
                    }
                    int remaining = stackTrace.length - j;
                    printRemainingFramesCount0(remaining);
                }
            }

            cur = JDKUtils.getRawCause(cur);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    final void printJavaFrame0(String className, String methodName, String fileName, int lineNumber) {
        newline0();
        string0("    at ");
        string0(className);
        string0(".");
        string0(methodName);
        string0("(");
        string0(fileName);
        string0(":");
        signed0(lineNumber);
        string0(")");
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void printRemainingFramesCount0(int remaining) {
        if (remaining > 0) {
            newline0();
            string0("    ... ");
            unsigned0(remaining);
            string0(" more");
        }
    }

    /**
     * Write a raw java array by copying it first to a stack allocated temporary buffer. Caller must
     * ensure that the offset and length are within bounds.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void writeBytes0(Object value, int offset, int length) {
        /*
         * Stack allocation needs an allocation size that is a compile time constant, so we split
         * the byte array up in multiple chunks and write them separately.
         */
        final int chunkSize = 256;
        CCharPointer bytes = UnsafeStackValue.get(chunkSize);

        int chunkOffset = offset;
        int inputLength = length;
        while (inputLength > 0) {
            int chunkLength = min(inputLength, chunkSize);

            for (int i = 0; i < chunkLength; i++) {
                int index = chunkOffset + i;
                byte b;
                if (value instanceof String s) {
                    b = (byte) charAt(s, index);
                } else if (value instanceof char[] arr) {
                    b = (byte) arr[index];
                } else {
                    b = ((byte[]) value)[index];
                }
                bytes.write(i, b);
            }
            writeBytes0(bytes, Word.unsigned(chunkLength));

            chunkOffset += chunkLength;
            inputLength -= chunkLength;
        }
    }

    @Uninterruptible(reason = "Some implementations are interruptible.", callerMustBe = true, calleeMustBe = false)
    private Log writeBytes0(CCharPointer bytes, UnsignedWord length) {
        return rawBytes(bytes, length);
    }

    @Uninterruptible(reason = "Some implementations are interruptible.", callerMustBe = true, calleeMustBe = false)
    private int printBacktrace0(Throwable t, int maxFrames) {
        return printBacktrace(t, maxFrames);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static byte digit(long d) {
        return (byte) (d + (d < 10 ? '0' : 'a' - 10));
    }
}
