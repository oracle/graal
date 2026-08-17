/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.sboutlining.concat;

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.UniqueLocationIdentity;
import org.graalvm.word.PointerBase;

import com.oracle.svm.shared.AlwaysInline;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.Target_java_lang_String;
import com.oracle.svm.shared.util.BasedOnJDKFile;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.GraalError;
import jdk.internal.misc.Unsafe;
import jdk.internal.util.ArraysSupport;
import jdk.internal.util.DecimalDigits;

/**
 * Runtime support for outlined {@link StringBuilder} and {@link StringBuffer} materialization.
 *
 * <p>
 * Mixer methods update a stack-allocated {@link LengthCoderAndCapacityStruct} with the accumulated
 * character length, compact-string coder, and character capacity. Capacity calculation mirrors
 * JDK 25 {@code AbstractStringBuilder} growth, including byte-size changes when Latin-1 storage
 * inflates to UTF-16. The final helpers allocate the backing byte array and initialize a builder or
 * buffer with the computed value, coder, count, and capacity.
 *
 * <p>
 * This class extends the runtime operations in {@link SubstrateStringConcatHelper}. The hosted
 * graph builder emits the corresponding calls and the raw stack structure.
 * Constructor validation helpers preserve the exceptions of the original builder or buffer
 * operations.
 */
@SuppressWarnings("all")
public final class SubstrateSBConcatHelper {

    @RawStructure
    public interface LengthCoderAndCapacityStruct extends PointerBase {
        @RawField
        @UniqueLocationIdentity
        long getLengthCoder();

        @RawField
        @UniqueLocationIdentity
        void setLengthCoder(long value);

        @RawField
        @UniqueLocationIdentity
        long getCapacity();

        @RawField
        @UniqueLocationIdentity
        void setCapacity(long value);
    }

    @TargetClass(className = "java.lang.AbstractStringBuilder")
    private static final class Target_java_lang_AbstractStringBuilder {
        @Alias byte[] value;

        @Alias byte coder;

        @Alias int count;
    }

    private SubstrateSBConcatHelper() {
        // no instantiation
    }

    /**
     * Check for overflow, throw exception on overflow.
     *
     * @param lengthCoder String length with coder packed into higher bits the upper word.
     * @return the given parameter value, if valid
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L160-L165")
    private static long checkOverflow(long lengthCoder) {
        if ((int) lengthCoder >= 0) {
            return lengthCoder;
        }
        throw new OutOfMemoryError("Required length exceeds implementation limit");
    }

    /**
     * Mix value length and coder into current length and coder.
     *
     * @param lengthCoderAndCapacity String length with coder packed into higher bits the upper
     *            word.
     * @param value value to mix in
     * @return new length and coder
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L174-L176")
    static LengthCoderAndCapacityStruct mix(LengthCoderAndCapacityStruct lengthCoderAndCapacity, boolean value) {
        int addend = (value ? 4 : 5);
        long newLengthCoder = checkOverflow(lengthCoderAndCapacity.getLengthCoder() + addend);
        long newCapacity = CapacityCalculator.calculateNewCapacity(lengthCoderAndCapacity, addend, newLengthCoder);
        lengthCoderAndCapacity.setLengthCoder(newLengthCoder);
        lengthCoderAndCapacity.setCapacity(newCapacity);
        return lengthCoderAndCapacity;
    }

    /**
     * Mix value length and coder into current length and coder.
     *
     * @param lengthCoderAndCapacity String length with coder packed into higher bits the upper
     *            word.
     * @param value value to mix in
     * @return new length and coder
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L185-L187")
    static LengthCoderAndCapacityStruct mix(LengthCoderAndCapacityStruct lengthCoderAndCapacity, char value) {
        long addend = 1;
        long newLengthCoder = checkOverflow(lengthCoderAndCapacity.getLengthCoder() + addend) | SubstrateStringConcatHelper.coder(value);
        long newCapacity = CapacityCalculator.calculateNewCapacity(lengthCoderAndCapacity, addend, newLengthCoder);
        lengthCoderAndCapacity.setLengthCoder(newLengthCoder);
        lengthCoderAndCapacity.setCapacity(newCapacity);
        return lengthCoderAndCapacity;
    }

    /**
     * Mix value length and coder into current length and coder.
     *
     * @param lengthCoderAndCapacity String length with coder packed into higher bits the upper
     *            word.
     * @param value value to mix in
     * @return new length and coder
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L196-L198")
    static LengthCoderAndCapacityStruct mix(LengthCoderAndCapacityStruct lengthCoderAndCapacity, int value) {
        long addend = DecimalDigits.stringSize(value);
        long newLengthCoder = checkOverflow(lengthCoderAndCapacity.getLengthCoder() + addend);
        long newCapacity = CapacityCalculator.calculateNewCapacity(lengthCoderAndCapacity, addend, newLengthCoder);
        lengthCoderAndCapacity.setLengthCoder(newLengthCoder);
        lengthCoderAndCapacity.setCapacity(newCapacity);
        return lengthCoderAndCapacity;
    }

    /**
     * Mix value length and coder into current length and coder.
     *
     * @param lengthCoderAndCapacity String length with coder packed into higher bits the upper
     *            word.
     * @param value value to mix in
     * @return new length and coder
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L207-L209")
    static LengthCoderAndCapacityStruct mix(LengthCoderAndCapacityStruct lengthCoderAndCapacity, long value) {
        long addend = DecimalDigits.stringSize(value);
        long newLengthCoder = checkOverflow(lengthCoderAndCapacity.getLengthCoder() + addend);
        long newCapacity = CapacityCalculator.calculateNewCapacity(lengthCoderAndCapacity, addend, newLengthCoder);
        lengthCoderAndCapacity.setLengthCoder(newLengthCoder);
        lengthCoderAndCapacity.setCapacity(newCapacity);
        return lengthCoderAndCapacity;
    }

    /**
     * Mix value length and coder into current length and coder.
     *
     * @param lengthCoderAndCapacity String length with coder packed into higher bits the upper
     *            word.
     * @param value value to mix in
     * @return new length and coder
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L218-L224")
    static LengthCoderAndCapacityStruct mix(LengthCoderAndCapacityStruct lengthCoderAndCapacity, String value) {
        long addend = value.length();
        long newLengthCoder = lengthCoderAndCapacity.getLengthCoder() + value.length();
        if (SubstrateUtil.cast(value, Target_java_lang_String.class).coder() == SubstrateStringConcatHelper.StringUtil.UTF16) {
            newLengthCoder |= SubstrateStringConcatHelper.UTF16;
        }
        newLengthCoder = checkOverflow(newLengthCoder);
        long newCapacity = CapacityCalculator.calculateNewCapacity(lengthCoderAndCapacity, addend, newLengthCoder);
        lengthCoderAndCapacity.setLengthCoder(newLengthCoder);
        lengthCoderAndCapacity.setCapacity(newCapacity);
        return lengthCoderAndCapacity;
    }

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /**
     * Allocates an uninitialized byte array based on the length and coder information in
     * indexCoder.
     *
     * @param indexCoder
     * @return the newly allocated byte array
     */
    @AlwaysInline("@ForceInline in JDK")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L561-L566")
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L573-L579")
    static byte[] newArray(LengthCoderAndCapacityStruct lengthCoderAndCapacity) {
        byte coder = (byte) (lengthCoderAndCapacity.getLengthCoder() >> 32);
        int capacity = (int) lengthCoderAndCapacity.getCapacity();
        return (byte[]) UNSAFE.allocateUninitializedArray(byte.class, capacity << coder);
    }

    /*
     * Extra helpers added to allow materialization of String(Builder|Buffer)s.
     */

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L388-L398")
    static byte getCoder(long indexCoder) {
        if (indexCoder == SubstrateStringConcatHelper.LATIN1) {
            return SubstrateStringConcatHelper.StringUtil.LATIN1;
        } else if (indexCoder == SubstrateStringConcatHelper.UTF16) {
            return SubstrateStringConcatHelper.StringUtil.UTF16;
        } else {
            throw new InternalError("Storage is not completely initialized, " + (int) indexCoder + " bytes left");
        }
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringBuilder.java#L117-L120")
    static StringBuilder newStringBuilder(byte[] buf, long indexCoder, long count) {
        Target_java_lang_AbstractStringBuilder sb;
        try {
            sb = SubstrateUtil.cast(UNSAFE.allocateInstance(StringBuilder.class), Target_java_lang_AbstractStringBuilder.class);
        } catch (Throwable ex) {
            throw GraalError.shouldNotReachHere(ex); // ExcludeFromJacocoGeneratedReport
        }
        sb.value = buf;
        sb.coder = getCoder(indexCoder);
        sb.count = (int) count;
        return SubstrateUtil.cast(sb, StringBuilder.class);
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringBuffer.java#L117-L147")
    static StringBuffer newStringBuffer(byte[] buf, long indexCoder, long count) {
        Target_java_lang_AbstractStringBuilder sb;
        try {
            sb = SubstrateUtil.cast(UNSAFE.allocateInstance(StringBuffer.class), Target_java_lang_AbstractStringBuilder.class);
        } catch (Throwable ex) {
            throw GraalError.shouldNotReachHere(ex); // ExcludeFromJacocoGeneratedReport
        }
        sb.value = buf;
        sb.coder = getCoder(indexCoder);
        sb.count = (int) count;
        return SubstrateUtil.cast(sb, StringBuffer.class);
    }

    static long getIndexCoder(LengthCoderAndCapacityStruct struct) {
        return struct.getLengthCoder();
    }

    static long getCount(LengthCoderAndCapacityStruct struct) {
        return struct.getLengthCoder() & NumUtil.getNbitNumberLong(32);
    }

    static LengthCoderAndCapacityStruct initializeCoderAndCapacity(LengthCoderAndCapacityStruct struct, int initialCapacity) {
        long indexCoder = SubstrateStringConcatHelper.initialCoder();
        struct.setLengthCoder(indexCoder);
        struct.setCapacity(initialCapacity);
        return struct;
    }

    /**
     * Preserves the capacity validation performed by the
     * {@code AbstractStringBuilder(int)} constructor. String outlining removes the original
     * constructor allocation, so the replacement graph calls this method to reject a negative
     * capacity at the same point. A valid capacity is returned unchanged for subsequent outlining
     * operations.
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/AbstractStringBuilder.java#L99-L107")
    public static int validateCapacity(int capacity) {
        if (capacity < 0) {
            throw new NegativeArraySizeException(Integer.toString(capacity));
        } else {
            return capacity;
        }
    }

    /**
     * This class contains utility methods for calculating capacity. The calculation matches JDK25
     * {@code AbstractStringBuilder}, but works with character capacities instead of byte-array
     * lengths.
     */
    public static final class CapacityCalculator {

        /**
         * Note that this method can throw a {@link NullPointerException} if {@code str} is null.
         * This is the same behavior as in AbstractStringBuilder.
         */
        @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/AbstractStringBuilder.java#L117-L125")
        public static int initialCapacityFor(String str) {
            int length = str.length();
            int capacity = (length < Integer.MAX_VALUE - 16)
                            ? length + 16
                            : Integer.MAX_VALUE;
            return capacity;
        }

        @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringBuilder.java#L100-L107")
        @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringBuffer.java#L127-L134")
        public static int defaultInitialCapacity() {
            return 16;
        }

        private static long calculateNewCapacity(LengthCoderAndCapacityStruct struct, long addend, long newLengthCoder) {
            int curLength = (int) struct.getLengthCoder();
            int curCapacity = NumUtil.safeToInt(struct.getCapacity());
            int oldCoder = struct.getLengthCoder() < SubstrateStringConcatHelper.UTF16 ? 0 : 1;
            int newCoder = newLengthCoder < SubstrateStringConcatHelper.UTF16 ? 0 : 1;
            return ensureCapacityInternal(curCapacity, curLength, (int) addend, oldCoder, newCoder);
        }

        /**
         * Calculates the character capacity needed after appending {@code addend} characters. This
         * models the grow-or-keep decision in {@code AbstractStringBuilder} without allocating,
         * copying, or inflating a backing array. {@code oldCoder} and {@code newCoder} are zero for
         * Latin-1 and one for UTF-16.
         *
         * @return the existing or grown capacity in characters
         */
        @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/AbstractStringBuilder.java#L267-L291")
        private static long ensureCapacityInternal(int curCapacity, int curLength, int addend, int oldCoder, int newCoder) {
            int minimumCapacity = curLength + addend;
            if (minimumCapacity - curCapacity > 0) {
                return newCapacity(curCapacity, minimumCapacity, oldCoder, newCoder);
            }
            return curCapacity;
        }

        /*
         * Checkstyle: stop
         */
        /**
         * Applies the {@code AbstractStringBuilder} backing-array growth rule and returns the result
         * as a character capacity. The current character capacity is converted to an old-coder byte
         * length. The required character capacity is converted to a new-coder byte length. The
         * resulting byte length is converted back with {@code newCoder}.
         *
         * @return the grown capacity in characters
         */
        @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/AbstractStringBuilder.java#L340-L349")
        private static int newCapacity(int curCapacity, int minimumCapacity, int oldCoder, int newCoder) {
            int oldLength = curCapacity << oldCoder;
            int minimumLength = minimumCapacity << newCoder;
            int minimumGrowth = minimumLength - oldLength;
            int preferredGrowth = oldLength + (2 << newCoder);
            int newLength = ArraysSupport.newLength(oldLength, minimumGrowth, preferredGrowth);
            if (newLength == Integer.MAX_VALUE) {
                throw new OutOfMemoryError("Required length exceeds implementation limit");
            }
            return newLength >> newCoder;
        }

        /*
         * Checkstyle: resume
         */
    }
}
