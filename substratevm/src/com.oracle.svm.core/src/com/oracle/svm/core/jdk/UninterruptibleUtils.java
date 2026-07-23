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
package com.oracle.svm.core.jdk;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.core.common.SuppressFBWarnings;

/**
 * String-related helpers that can be called from uninterruptible code.
 *
 * This part of {@code UninterruptibleUtils} remains in {@code svm.core} because several of its
 * implementations depend on the {@code Target_java_lang_String*} substitutions. The
 * {@link Character} helpers remain co-located with that String-related group.
 *
 * Once Terminus supports these substitutions, the remaining helpers will move to guest staging and
 * this core copy will be removed. This is tracked by GR-77810.
 *
 * @see com.oracle.svm.guest.staging.core.jdk.UninterruptibleUtils
 */
public class UninterruptibleUtils {
    public static class Character {
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isHighSurrogate(char ch) {
            return ch >= java.lang.Character.MIN_HIGH_SURROGATE && ch < (java.lang.Character.MAX_HIGH_SURROGATE + 1);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isLowSurrogate(char ch) {
            return ch >= java.lang.Character.MIN_LOW_SURROGATE && ch < (java.lang.Character.MAX_LOW_SURROGATE + 1);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static boolean isSurrogate(char ch) {
            return isHighSurrogate(ch) || isLowSurrogate(ch);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int toCodePoint(char high, char low) {
            return ((high << 10) + low) + (java.lang.Character.MIN_SUPPLEMENTARY_CODE_POINT - (java.lang.Character.MIN_HIGH_SURROGATE << 10) - java.lang.Character.MIN_LOW_SURROGATE);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int charCount(int codePoint) {
            return codePoint >= java.lang.Character.MIN_SUPPLEMENTARY_CODE_POINT ? 2 : 1;
        }
    }

    public static class String {
        private static final int MALFORMED_UTF8_REPLACEMENT = '?';

        /**
         * Gets the number of bytes for a char in modified UTF8 format.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private static int modifiedUTF8Length(char c) {
            return c == 0 ? 2 : utf8Length(c);
        }

        /**
         * Gets the number of bytes for a single UTF-16 code unit in UTF-8 format. This helper does
         * not combine surrogate pairs; callers that need code point semantics must use
         * {@link #utf8Length(int)} or one of the string-based overloads.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private static int utf8Length(char c) {
            return utf8Length((int) c);
        }

        /**
         * Gets the number of bytes for a code point in UTF-8 format.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int utf8Length(int codePoint) {
            if (codePoint <= 0x007F) {
                return 1;
            } else if (codePoint <= 0x07FF) {
                return 2;
            } else if (codePoint <= 0xFFFF) {
                return 3;
            } else {
                return 4;
            }
        }

        /**
         * Write a char in modified UTF8 format into the buffer.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private static Pointer writeModifiedUTF8(Pointer buffer, char c) {
            Pointer pos = buffer;
            if (c >= 0x0001 && c <= 0x007F) {
                pos.writeByte(0, (byte) c);
                pos = pos.add(1);
            } else if (c <= 0x07FF) {
                pos.writeByte(0, (byte) (0xC0 | (c >> 6)));
                pos.writeByte(1, (byte) (0x80 | (c & 0x3F)));
                pos = pos.add(2);
            } else {
                pos.writeByte(0, (byte) (0xE0 | (c >> 12)));
                pos.writeByte(1, (byte) (0x80 | ((c >> 6) & 0x3F)));
                pos.writeByte(2, (byte) (0x80 | (c & 0x3F)));
                pos = pos.add(3);
            }
            return pos;
        }

        /**
         * Write a code point in UTF-8 format into the buffer.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static Pointer writeUTF8(Pointer buffer, int codePoint) {
            Pointer pos = buffer;
            if (codePoint <= 0x007F) {
                pos.writeByte(0, (byte) codePoint);
                pos = pos.add(1);
            } else if (codePoint <= 0x07FF) {
                pos.writeByte(0, (byte) (0xC0 | (codePoint >> 6)));
                pos.writeByte(1, (byte) (0x80 | (codePoint & 0x3F)));
                pos = pos.add(2);
            } else if (codePoint <= 0xFFFF) {
                pos.writeByte(0, (byte) (0xE0 | (codePoint >> 12)));
                pos.writeByte(1, (byte) (0x80 | ((codePoint >> 6) & 0x3F)));
                pos.writeByte(2, (byte) (0x80 | (codePoint & 0x3F)));
                pos = pos.add(3);
            } else {
                pos.writeByte(0, (byte) (0xF0 | (codePoint >> 18)));
                pos.writeByte(1, (byte) (0x80 | ((codePoint >> 12) & 0x3F)));
                pos.writeByte(2, (byte) (0x80 | ((codePoint >> 6) & 0x3F)));
                pos.writeByte(3, (byte) (0x80 | (codePoint & 0x3F)));
                pos = pos.add(4);
            }
            return pos;
        }

        /**
         * Gets the length of {@code string} when encoded using modified UTF8 (null characters that
         * are present in the input will be encoded in a way that they do not interfere with a
         * null-terminator).
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int modifiedUTF8Length(java.lang.String string, boolean addNullTerminator) {
            return modifiedUTF8Length(string, addNullTerminator, null);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int modifiedUTF8Length(java.lang.String string, boolean addNullTerminator, CharReplacer replacer) {
            int result = 0;
            for (int index = 0; index < string.length(); index++) {
                char ch = charAt(string, index);
                if (replacer != null) {
                    ch = replacer.replace(ch);
                }
                result += modifiedUTF8Length(ch);
            }

            return result + (addNullTerminator ? 1 : 0);
        }

        /**
         * Gets the length of {@code string} when encoded using UTF-8.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int utf8Length(java.lang.String string) {
            return utf8Length(string, null);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int utf8Length(java.lang.String string, CharReplacer replacer) {
            return utf8Length(string, string.length(), replacer);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int utf8Length(java.lang.String string, int stringLength, CharReplacer replacer) {
            int result = 0;
            for (int index = 0; index < stringLength;) {
                int codePoint = utf8CodePointAt(string, index, stringLength, replacer);
                result += utf8Length(codePoint);
                index += Character.charCount(codePoint);
            }
            return result;
        }

        /**
         * Writes the encoded {@code string} into the given {@code buffer} using the modified UTF8
         * encoding (null characters that are present in the input will be encoded in a way that
         * they do not interfere with the null terminator).
         *
         * @return pointer on new position in buffer.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static Pointer toModifiedUTF8(java.lang.String string, Pointer buffer, Pointer bufferEnd, boolean addNullTerminator) {
            return toModifiedUTF8(string, buffer, bufferEnd, addNullTerminator, null);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static Pointer toModifiedUTF8(java.lang.String string, Pointer buffer, Pointer bufferEnd, boolean addNullTerminator, CharReplacer replacer) {
            return toModifiedUTF8(string, string.length(), buffer, bufferEnd, addNullTerminator, replacer);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static Pointer toModifiedUTF8(java.lang.String string, int stringLength, Pointer buffer, Pointer bufferEnd, boolean addNullTerminator, CharReplacer replacer) {
            Pointer pos = buffer;
            for (int index = 0; index < stringLength; index++) {
                char ch = charAt(string, index);
                if (replacer != null) {
                    ch = replacer.replace(ch);
                }
                pos = writeModifiedUTF8(pos, ch);
            }

            if (addNullTerminator) {
                pos.writeByte(0, (byte) 0);
                pos = pos.add(1);
            }
            VMError.guarantee(pos.belowOrEqual(bufferEnd), "Must not write out of bounds.");
            return pos;
        }

        /**
         * Writes the encoded {@code string} into the given {@code buffer} using UTF-8.
         *
         * @return pointer on new position in buffer.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static Pointer toUTF8(java.lang.String string, Pointer buffer, Pointer bufferEnd) {
            return toUTF8(string, buffer, bufferEnd, null);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static Pointer toUTF8(java.lang.String string, Pointer buffer, Pointer bufferEnd, CharReplacer replacer) {
            return toUTF8(string, string.length(), buffer, bufferEnd, replacer);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static Pointer toUTF8(java.lang.String string, int stringLength, Pointer buffer, Pointer bufferEnd, CharReplacer replacer) {
            Pointer pos = buffer;
            for (int index = 0; index < stringLength;) {
                int codePoint = utf8CodePointAt(string, index, stringLength, replacer);
                pos = writeUTF8(pos, codePoint);
                index += Character.charCount(codePoint);
            }
            VMError.guarantee(pos.belowOrEqual(bufferEnd), "Must not write out of bounds.");
            return pos;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int toUTF8UntilLimit(java.lang.String string, Pointer buffer, Pointer bufferEnd, int maxBytes) {
            Pointer pos = buffer;
            int bytesWritten = 0;
            int index = 0;
            while (index < string.length()) {
                int codePoint = utf8CodePointAt(string, index, string.length(), null);
                int byteLength = utf8Length(codePoint);
                if (maxBytes - bytesWritten < byteLength) {
                    break;
                }
                pos = writeUTF8(pos, codePoint);
                index += Character.charCount(codePoint);
                bytesWritten += byteLength;
            }
            VMError.guarantee(pos.belowOrEqual(bufferEnd), "Must not write out of bounds.");
            return bytesWritten;
        }

        /**
         * If {@code replacer} is non-null, it is applied to individual chars before UTF-8 encoding.
         * Valid surrogate pairs are combined into code points before replacement and are therefore
         * not passed to the replacer.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private static int utf8CodePointAt(java.lang.String string, int index, int stringLength, CharReplacer replacer) {
            char ch = charAt(string, index);
            if (Character.isHighSurrogate(ch) && index + 1 < stringLength) {
                char low = charAt(string, index + 1);
                if (Character.isLowSurrogate(low)) {
                    return Character.toCodePoint(ch, low);
                }
            }
            if (replacer != null) {
                ch = replacer.replace(ch);
            }
            if (Character.isSurrogate(ch)) {
                return MALFORMED_UTF8_REPLACEMENT;
            }
            return ch;
        }

        /**
         * Returns the Unicode code point at the given index in the string, combining surrogate
         * pairs into a single code point when applicable. Unpaired surrogates are returned as the
         * malformed UTF-8 replacement used by the other UTF-8 helpers in this class.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public static int codePointAt(java.lang.String string, int index) {
            return utf8CodePointAt(string, index, string.length(), null);
        }

        /**
         * Returns a character from a string at {@code index} position based on the encoding format.
         */
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static char charAt(java.lang.String string, int index) {
            Target_java_lang_String str = SubstrateUtil.cast(string, Target_java_lang_String.class);
            byte[] value = str.value;
            if (str.isLatin1()) {
                return Target_java_lang_StringLatin1.getChar(value, index);
            } else {
                return Target_java_lang_StringUTF16.getChar(value, index);
            }
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static byte coder(java.lang.String string) {
            return SubstrateUtil.cast(string, Target_java_lang_String.class).coder();
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static byte[] value(java.lang.String string) {
            return SubstrateUtil.cast(string, Target_java_lang_String.class).value;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static boolean startsWith(java.lang.String string, java.lang.String prefix) {
            if (prefix.length() > string.length()) {
                return false;
            }
            byte coder = coder(string);
            if (coder != coder(prefix) && coder == Target_java_lang_String.LATIN1) {
                /* string.coder == LATIN1 && prefix.coder == UTF16 */
                return false;
            }
            return compare(string, prefix, prefix.length());
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static boolean endsWith(java.lang.String string, java.lang.String suffix) {
            if (suffix.length() > string.length()) {
                return false;
            }
            byte coder = coder(string);
            if (coder != coder(suffix) && coder == Target_java_lang_String.LATIN1) {
                /* string.coder == LATIN1 && suffix.coder == UTF16 */
                return false;
            }
            return compare(string, string.length() - suffix.length(), suffix, 0, suffix.length());
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        @SuppressFBWarnings(value = "", justification = "The string comparison by reference is fine in this case.")
        public static boolean equals(java.lang.String a, java.lang.String b) {
            return a == b || (!Target_java_lang_String.COMPACT_STRINGS || coder(a) == coder(b)) && equals0(value(a), value(b));
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static boolean equals0(byte[] value, byte[] other) {
            if (value.length == other.length) {
                for (int i = 0; i < value.length; i++) {
                    if (value[i] != other[i]) {
                        return false;
                    }
                }
                return true;
            }
            return false;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static boolean compare(java.lang.String a, java.lang.String b, int length) {
            return compare(a, 0, b, 0, length);
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static boolean compare(java.lang.String a, int aOffset, java.lang.String b, int bOffset, int length) {
            for (int index = 0; index < length; index++) {
                if (charAt(a, aOffset + index) != charAt(b, bOffset + index)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Utilities for null-terminated {@link CCharPointer} strings encoded as ASCII. */
    public static class ASCII {
        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static boolean startsWith(CCharPointer string, java.lang.String prefix) {
            for (int i = 0; i < prefix.length(); i++) {
                int ch = string.read(i) & 0xFF;
                if (ch == 0 || ch != asciiCharAt(prefix, i)) {
                    return false;
                }
            }
            return true;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static boolean endsWith(CCharPointer string, int stringLength, java.lang.String suffix) {
            if (suffix.length() > stringLength) {
                return false;
            }
            int suffixStart = stringLength - suffix.length();
            for (int i = 0; i < suffix.length(); i++) {
                int ch = string.read(suffixStart + i) & 0xFF;
                if (ch != asciiCharAt(suffix, i)) {
                    return false;
                }
            }
            return true;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        public static boolean equals(CCharPointer string, int length, java.lang.String expected) {
            return length == expected.length() && startsWith(string, expected);
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static char asciiCharAt(java.lang.String value, int index) {
            char ch = SubstrateUtil.HOSTED ? value.charAt(index) : String.charAt(value, index);
            VMError.guarantee(ch <= 0x7F, "Expected an ASCII string.");
            return ch;
        }
    }

    @FunctionalInterface
    public interface CharReplacer {
        /**
         * Replaces a single char before UTF-8 encoding. Valid surrogate pairs are encoded as code
         * points and skip this replacement.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        char replace(char val);
    }

    public static final class ReplaceDotWithSlash implements CharReplacer {
        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        public char replace(char ch) {
            if (ch == '.') {
                return '/';
            }
            return ch;
        }
    }

}
