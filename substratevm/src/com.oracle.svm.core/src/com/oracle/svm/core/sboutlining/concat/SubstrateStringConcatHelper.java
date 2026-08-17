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

import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.Target_java_lang_String;
import com.oracle.svm.shared.util.BasedOnJDKClass;
import com.oracle.svm.shared.util.BasedOnJDKFile;
import com.oracle.svm.shared.util.ReflectionUtil;

import jdk.internal.misc.Unsafe;
import jdk.internal.util.DecimalDigits;

/**
 * Runtime implementation of string concatenation operations emitted by
 * the hosted string concat factory.
 *
 * <p>
 * The helper follows JDK 25's packed {@code StringConcatHelper} API: the low 32 bits of a
 * {@code long} hold the character length or write index, and the high bits hold the compact-string
 * coder. Mixer methods compute that state with overflow checks, prepender methods fill a single
 * allocated buffer from right to left, and the final methods create the resulting {@link String}.
 * Fast paths handle unary and binary concatenations without constructing a full combinator chain.
 *
 * <p>
 * Private JDK string operations are reached through substitution aliases. This copy intentionally
 * keeps byte and short adapters required by outlined method signatures.
 */
@SuppressWarnings("all")
@BasedOnJDKClass(className = "java.lang.StringConcatHelper")
public final class SubstrateStringConcatHelper {

    @TargetClass(className = "java.lang.StringLatin1")
    static final class Target_java_lang_StringLatin1 {

        @Alias
        static native boolean canEncode(int cp);

        @Alias
        static native int length(byte[] value);

    }

    @TargetClass(className = "java.lang.StringUTF16")
    static final class Target_java_lang_StringUTF16 {
        @Alias
        static native void putChar(byte[] val, int index, int c);

        @Alias
        static native int length(byte[] value);
    }

    static class StringUtil {

        static final boolean COMPACT_STRINGS;
        static final byte UTF16;
        static final byte LATIN1;

        static {
            LATIN1 = ReflectionUtil.readStaticField(String.class, "LATIN1");
            UTF16 = ReflectionUtil.readStaticField(String.class, "UTF16");
            COMPACT_STRINGS = ReflectionUtil.readStaticField(String.class, "COMPACT_STRINGS");
        }

        static void getBytes(String string, byte[] dst, int dstBegin, byte coder) {
            SubstrateUtil.cast(string, Target_java_lang_String.class).getBytes(dst, dstBegin, coder);
        }

        static String init(byte[] value, byte code) {
            return SubstrateUtil.cast(new Target_java_lang_String(value, code), String.class);
        }
    }

    // Suppressing formatters to more closely match JDK 25 code
    /*
     * Checkstyle: stop
     * @formatter:off
     */

    private SubstrateStringConcatHelper() {
        // no instantiation
    }

    /**
     * Return the packed coder for the character.
     *
     * @param value character
     * @return packed coder
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L149-L151")
    static long coder(char value) {
        return Target_java_lang_StringLatin1.canEncode(value) ? LATIN1 : UTF16;
    }

    /**
     * Check for overflow, throw exception on overflow.
     *
     * @param lengthCoder String length with coder packed into higher bits
     *                    the upper word.
     * @return            the given parameter value, if valid
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L160-L165")
    private static long checkOverflow(long lengthCoder) {
        if ((int)lengthCoder >= 0) {
            return lengthCoder;
        }
        throw new OutOfMemoryError("Overflow: String length out of range");
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L782-L788")
    static int checkOverflow(int value) {
        if (value >= 0) {
            return value;
        }
        throw new OutOfMemoryError("Overflow: String length out of range");
    }

    /**
     * Mix value length and coder into current length and coder.
     * @param lengthCoder String length with coder packed into higher bits
     *                    the upper word.
     * @param value       value to mix in
     * @return            new length and coder
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L174-L176")
    static long mix(long lengthCoder, boolean value) {
        return checkOverflow(lengthCoder + (value ? 4 : 5));
    }

    /**
     * Mix value length and coder into current length and coder.
     * @param lengthCoder String length with coder packed into higher bits
     *                    the upper word.
     * @param value       value to mix in
     * @return            new length and coder
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L185-L187")
    static long mix(long lengthCoder, char value) {
        return checkOverflow(lengthCoder + 1) | coder(value);
    }

    /**
     * Mix value length and coder into current length and coder.
     * @param lengthCoder String length with coder packed into higher bits
     *                    the upper word.
     * @param value       value to mix in
     * @return            new length and coder
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L196-L198")
    static long mix(long lengthCoder, int value) {
        return checkOverflow(lengthCoder + DecimalDigits.stringSize(value));
    }

    /**
     * Mix value length and coder into current length and coder.
     * @param lengthCoder String length with coder packed into higher bits
     *                    the upper word.
     * @param value       value to mix in
     * @return            new length and coder
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L207-L209")
    static long mix(long lengthCoder, long value) {
        return checkOverflow(lengthCoder + DecimalDigits.stringSize(value));
    }

    /**
     * Mix value length and coder into current length and coder.
     * @param lengthCoder String length with coder packed into higher bits
     *                    the upper word.
     * @param value       value to mix in
     * @return            new length and coder
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L218-L224")
    public static long mix(long lengthCoder, String value) {
        lengthCoder += value.length();
        if (SubstrateUtil.cast(value, Target_java_lang_String.class).coder() == StringUtil.UTF16) {
            lengthCoder |= UTF16;
        }
        return checkOverflow(lengthCoder);
    }

    /**
     * Prepends constant and the stringly representation of value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param indexCoder final char index in the buffer, along with coder packed
     *                   into higher bits.
     * @param buf        buffer to append to
     * @param value      boolean value to encode
     * @param prefix     a constant to prepend before value
     * @return           updated index (coder value retained)
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L237-L276")
    static long prepend(long indexCoder, byte[] buf, boolean value, String prefix) {
        int index = (int)indexCoder;
        if (indexCoder < UTF16) {
            if (value) {
                index -= 4;
                buf[index] = 't';
                buf[index + 1] = 'r';
                buf[index + 2] = 'u';
                buf[index + 3] = 'e';
            } else {
                index -= 5;
                buf[index] = 'f';
                buf[index + 1] = 'a';
                buf[index + 2] = 'l';
                buf[index + 3] = 's';
                buf[index + 4] = 'e';
            }
            index -= prefix.length();
            StringUtil.getBytes(prefix, buf, index, StringUtil.LATIN1);
            return index;
        } else {
            if (value) {
                index -= 4;
                Target_java_lang_StringUTF16.putChar(buf, index, 't');
                Target_java_lang_StringUTF16.putChar(buf, index + 1, 'r');
                Target_java_lang_StringUTF16.putChar(buf, index + 2, 'u');
                Target_java_lang_StringUTF16.putChar(buf, index + 3, 'e');
            } else {
                index -= 5;
                Target_java_lang_StringUTF16.putChar(buf, index, 'f');
                Target_java_lang_StringUTF16.putChar(buf, index + 1, 'a');
                Target_java_lang_StringUTF16.putChar(buf, index + 2, 'l');
                Target_java_lang_StringUTF16.putChar(buf, index + 3, 's');
                Target_java_lang_StringUTF16.putChar(buf, index + 4, 'e');
            }
            index -= prefix.length();
            StringUtil.getBytes(prefix, buf, index, StringUtil.UTF16);
            return index | UTF16;
        }
    }

    /**
     * Prepends constant and the stringly representation of value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param indexCoder final char index in the buffer, along with coder packed
     *                   into higher bits.
     * @param buf        buffer to append to
     * @param value      char value to encode
     * @param prefix     a constant to prepend before value
     * @return           updated index (coder value retained)
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L289-L302")
    static long prepend(long indexCoder, byte[] buf, char value, String prefix) {
        int index = (int)indexCoder;
        if (indexCoder < UTF16) {
            buf[--index] = (byte) (value & 0xFF);
            index -= prefix.length();
            StringUtil.getBytes(prefix, buf, index, StringUtil.LATIN1);
            return index;
        } else {
            Target_java_lang_StringUTF16.putChar(buf, --index, value);
            index -= prefix.length();
            StringUtil.getBytes(prefix, buf, index, StringUtil.UTF16);
            return index | UTF16;
        }
    }

    /**
     * Prepends constant and the stringly representation of value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param indexCoder final char index in the buffer, along with coder packed
     *                   into higher bits.
     * @param buf        buffer to append to
     * @param value      integer value to encode
     * @param prefix     a constant to prepend before value
     * @return           updated index (coder value retained)
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L315-L328")
    static long prepend(long indexCoder, byte[] buf, int value, String prefix) {
        int index = (int)indexCoder;
        if (indexCoder < UTF16) {
            index = DecimalDigits.uncheckedGetCharsLatin1(value, index, buf);
            index -= prefix.length();
            StringUtil.getBytes(prefix, buf, index, StringUtil.LATIN1);
            return index;
        } else {
            index = DecimalDigits.uncheckedGetCharsUTF16(value, index, buf);
            index -= prefix.length();
            StringUtil.getBytes(prefix, buf, index, StringUtil.UTF16);
            return index | UTF16;
        }
    }

    /**
     * Prepends constant and the stringly representation of value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param indexCoder final char index in the buffer, along with coder packed
     *                   into higher bits.
     * @param buf        buffer to append to
     * @param value      long value to encode
     * @param prefix     a constant to prepend before value
     * @return           updated index (coder value retained)
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L341-L354")
    static long prepend(long indexCoder, byte[] buf, long value, String prefix) {
        int index = (int)indexCoder;
        if (indexCoder < UTF16) {
            index = DecimalDigits.uncheckedGetCharsLatin1(value, index, buf);
            index -= prefix.length();
            StringUtil.getBytes(prefix, buf, index, StringUtil.LATIN1);
            return index;
        } else {
            index = DecimalDigits.uncheckedGetCharsUTF16(value, index, buf);
            index -= prefix.length();
            StringUtil.getBytes(prefix, buf, index, StringUtil.UTF16);
            return index | UTF16;
        }
    }

    /**
     * Prepends constant and the stringly representation of value into buffer,
     * given the coder and final index. Index is measured in chars, not in bytes!
     *
     * @param indexCoder final char index in the buffer, along with coder packed
     *                   into higher bits.
     * @param buf        buffer to append to
     * @param value      String value to encode
     * @param prefix     a constant to prepend before value
     * @return           updated index (coder value retained)
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L367-L380")
    static long prepend(long indexCoder, byte[] buf, String value, String prefix) {
        int index = ((int)indexCoder) - value.length();
        if (indexCoder < UTF16) {
            StringUtil.getBytes(value, buf, index, StringUtil.LATIN1);
            index -= prefix.length();
            StringUtil.getBytes(prefix, buf, index, StringUtil.LATIN1);
            return index;
        } else {
            StringUtil.getBytes(value, buf, index, StringUtil.UTF16);
            index -= prefix.length();
            StringUtil.getBytes(prefix, buf, index, StringUtil.UTF16);
            return index | UTF16;
        }
    }

    /**
     * Instantiates the String with given buffer and coder
     * @param buf           buffer to use
     * @param indexCoder    remaining index (should be zero) and coder
     * @return String       resulting string
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L388-L398")
    static String newString(byte[] buf, long indexCoder) {
        // Use the private, non-copying constructor (unsafe!)
        if (indexCoder == LATIN1) {
            return StringUtil.init(buf, StringUtil.LATIN1);
        } else if (indexCoder == UTF16) {
            return StringUtil.init(buf, StringUtil.UTF16);
        } else {
            throw new InternalError("Storage is not completely initialized, " + (int)indexCoder + " bytes left");
        }
    }

    /**
     * Perform a simple concatenation between two objects. Added for startup
     * performance, but also demonstrates the code that would be emitted by
     * {@code java.lang.invoke.StringConcatFactory$MethodHandleInlineCopyStrategy}
     * for two Object arguments.
     *
     * @param first         first argument
     * @param second        second argument
     * @return String       resulting string
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L410-L423")
    static String simpleConcat(Object first, Object second) {
        String s1 = stringOf(first);
        String s2 = stringOf(second);
        if (s1.isEmpty()) {
            // newly created string required, see JLS 15.18.1
            return new String(s2);
        }
        if (s2.isEmpty()) {
            // newly created string required, see JLS 15.18.1
            return new String(s1);
        }
        return doConcat(s1, s2);
    }

    /**
     * Perform a simple concatenation between two non-empty strings.
     *
     * @param s1 first argument
     * @param s2 second argument
     * @return resulting string
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L432-L440")
    static String doConcat(String s1, String s2) {
        byte coder = (byte) (SubstrateUtil.cast(s1, Target_java_lang_String.class).coder() |
                        SubstrateUtil.cast(s2, Target_java_lang_String.class).coder());
        int newLength = checkOverflow(s1.length() + s2.length()) << coder;
        byte[] buf = newArray(newLength);
        StringUtil.getBytes(s1, buf, 0, coder);
        StringUtil.getBytes(s2, buf, s1.length(), coder);
        return newString(buf, ((long) coder) << 32);
    }

    /**
     * Produce a String from a concatenation of single argument, which we
     * end up using for trivial concatenations like {@code "" + arg}.
     *
     * This will always create a new Object to comply with JLS 15.18.1:
     * "The String object is newly created unless the expression is a
     * compile-time constant expression".
     *
     * @param arg           the only argument
     * @return String       resulting string
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L453-L456")
    static String newStringOf(Object arg) {
        return new String(stringOf(arg));
    }

    /**
     * We need some additional conversion for Objects in general, because
     * {@code String.valueOf(Object)} may return null. String conversion rules
     * in Java state we need to produce "null" String in this case, so we
     * provide a customized version that deals with this problematic corner case.
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L464-L467")
    static String stringOf(Object value) {
        String s;
        return (value == null || (s = value.toString()) == null) ? "null" : s;
    }

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L469")
    static final long LATIN1 = ((long) StringUtil.LATIN1) << 32;

    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L471")
    static final long UTF16 = ((long) StringUtil.UTF16) << 32;

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    /**
     * Allocates an uninitialized byte array based on the length and coder
     * information, then prepends the given suffix string at the end of the
     * byte array before returning it. The calling code must adjust the
     * indexCoder so that it's taken the coder of the suffix into account, but
     * subtracted the length of the suffix.
     *
     * @param suffix
     * @param indexCoder
     * @return the newly allocated byte array
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L544-L553")
    static byte[] newArrayWithSuffix(String suffix, long indexCoder) {
        byte[] buf = newArray(indexCoder + suffix.length());
        if (indexCoder < UTF16) {
            StringUtil.getBytes(suffix, buf, (int)indexCoder, StringUtil.LATIN1);
        } else {
            StringUtil.getBytes(suffix, buf, (int)indexCoder, StringUtil.UTF16);
        }
        return buf;
    }

    /**
     * Allocates an uninitialized byte array based on the length and coder information
     * in indexCoder
     * @param indexCoder
     * @return the newly allocated byte array
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L561-L566")
    static byte[] newArray(long indexCoder) {
        byte coder = (byte)(indexCoder >> 32);
        int index = ((int)indexCoder) << coder;
        return newArray(index);
    }

    /**
     * Allocates an uninitialized byte array with the given length.
     *
     * @param length array length
     * @return uninitialized byte array
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L573-L579")
    static byte[] newArray(int length) {
        if (length < 0) {
            throw new OutOfMemoryError("Overflow: String length out of range");
        }
        return (byte[]) UNSAFE.allocateUninitializedArray(byte.class, length);
    }

    /**
     * Provides the initial coder for the String.
     * @return initial coder, adjusted into the upper half
     */
    @BasedOnJDKFile("https://github.com/graalvm/labs-openjdk/blob/jdk-25-ga/src/java.base/share/classes/java/lang/StringConcatHelper.java#L585-L587")
    public static long initialCoder() {
        return StringUtil.COMPACT_STRINGS ? LATIN1 : UTF16;
    }

    /*
     * @formatter:on
     * Checkstyle: resume
     */
}
