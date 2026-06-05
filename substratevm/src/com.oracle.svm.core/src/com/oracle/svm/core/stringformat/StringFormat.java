/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.stringformat;

import java.math.BigInteger;
import java.util.Formattable;
import java.util.Formatter;
import java.util.IllegalFormatCodePointException;
import java.util.IllegalFormatConversionException;
import java.util.IllegalFormatException;
import java.util.Locale;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.shared.util.VMError;

/**
 * The classes in this package optimize {@link String#format} for simple format strings that are
 * compile time constants. When the format string is constant, it can be parsed already at image
 * build time. As long as it contains only the simple format specifiers defined in this class, and
 * no complicated width and alignment formats are used, the call to {@link String#format} is
 * replaced with a call to {@link StringFormat#format}. The arguments are transformed to avoid
 * format string parsing at run time. For example, the call to
 * 
 * String.format("string: %s number: %d", new Object[] {someString, someNumber});
 * 
 * is transformed to
 * 
 * StringFormat.format("sssd", new Object[] {"string: ", someString, " number: ", someNumber});
 * 
 * The format string is already split at format specifier positions, and the literal parts of the
 * format strings are treated the same as string format arguments. The new first parameter, called
 * "conversions" since it is only contains the conversion specifiers of the original format string,
 * has a length that always matches the number of array elements. It also only contains valid
 * conversion characters.
 * 
 * The only exception to that rule is when a zero padding option is added to a conversion. In that
 * case, the length of the padding is added immediately after the conversion character. Only
 * zero-paddings up to 9 characters are accepted, so the length always takes up only one character.
 * For example, the call to
 * 
 * String.format("number: %02d", new Object[] {someNumber});
 * 
 * is transformed to
 * 
 * StringFormat.format("sd2", new Object[] {"number: ", someNumber};
 * 
 * Argument indexing is already fully resolved. For example, the call to
 * 
 * String.format("string: %2$s number: %1$d again: %<d", new Object[] {someNumber, someString});
 *
 * is transformed to
 *
 * StringFormat.format("sssdsd", new Object[] {"string: ", someString, " number: ", someNumber, "
 * again: ", someNumber});
 * 
 * Complicated format specifiers, like floating point numbers and dates, are not supported at all.
 * One goal of the intrinsification is to make all the localization code unnecessary, and floating
 * pointer numbers / dates would always require localization code. For integer numbers, decimal
 * separators are not supported. But the "zero" character localization needs to be applied even for
 * the most basic %d format string. To avoid pulling localization code in, all non-default "zero"
 * characters of all {@link Locale} in the image heap are collected at image build time in
 * {@link #zeroChars}, using an object replacer in {@link StringFormatFeature}.
 * 
 * The "alternate" formatting mode is not supported, with the exception of the hexadecimal format
 * specifiers %x and %x - because there "alternate" just means prepending the number with "0x" or
 * "0X", which are appended like string literals. %n and %% are also appended already as string
 * literals. For example,
 * 
 * String.format("special %% hex %#x", new Object[] {someNumber});
 * 
 * is transformed to
 * 
 * StringFormat.format("sx", new Object[] {"special % hex 0x", someNumber});
 * 
 * Any kind of format string parsing errors are ignored at image build time and not intrinsified, so
 * that the parsing error is thrown by String.format at run time when it parses the format string.
 * However, proper argument types for %d, %x, and %c modifiers cannot be checked at image build time
 * for validity, i.e., the intrinsified string formatting can throw a few kinds
 * {@link IllegalFormatException}.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class)
public final class StringFormat {
    static final char DECIMAL_INTEGER = 'd';
    static final char HEXADECIMAL_INTEGER = 'x';
    static final char HEXADECIMAL_INTEGER_UPPER = 'X';
    static final char CHARACTER = 'c';
    static final char STRING = 's';

    static final String SUPPORTED_CONVERSIONS = "" + DECIMAL_INTEGER + HEXADECIMAL_INTEGER + HEXADECIMAL_INTEGER_UPPER + CHARACTER + STRING;

    public final EconomicMap<Locale, Character> zeroChars = ImageHeapMap.create("zeroChars");

    public static StringFormat singleton() {
        return ImageSingletons.lookup(StringFormat.class);
    }

    static String format(String conversions, Object[] args) {
        return format(true, null, conversions, args);
    }

    static String format(Locale locale, String conversions, Object[] args) {
        return format(false, locale, conversions, args);
    }

    private static String format(boolean defaultLocale, Locale explicitLocale, String conversions, Object[] args) {
        StringBuilder result = new StringBuilder();
        /* Formatter is created lazily, it might also involve looking up the default Locale. */
        Formatter formatter = null;
        /* Initialized lazily because it depends on the Locale. */
        Character zeroChar = null;

        int numConversions = conversions.length();
        assert numConversions >= args.length;
        int nextCharIndex = 0;
        for (int i = 0; i < args.length; i++, nextCharIndex++) {
            Object arg = args[i];
            char conversion = conversions.charAt(nextCharIndex);
            if (arg == null) {
                result.append(conversion == HEXADECIMAL_INTEGER_UPPER ? "NULL" : "null");
                continue;
            }

            int zeroPadding = -1;
            if (nextCharIndex + 1 < numConversions && isPaddingDigit(conversions.charAt(nextCharIndex + 1))) {
                nextCharIndex++;
                zeroPadding = conversions.charAt(nextCharIndex) - '0';
            }
            switch (conversion) {
                case DECIMAL_INTEGER:
                    formatter = ensureFormatter(formatter, defaultLocale, explicitLocale, result);
                    zeroChar = ensureZeroChar(zeroChar, formatter);
                    appendNumber(defaultLocale, explicitLocale, result, arg, conversion, zeroChar.charValue(), zeroPadding);
                    break;
                case HEXADECIMAL_INTEGER:
                case HEXADECIMAL_INTEGER_UPPER:
                    appendNumber(defaultLocale, explicitLocale, result, arg, conversion, '0', zeroPadding);
                    break;
                case CHARACTER:
                    appendCharacter(result, arg, conversion);
                    break;
                case STRING:
                    if (arg instanceof Formattable) {
                        formatter = ensureFormatter(formatter, defaultLocale, explicitLocale, result);
                        ((Formattable) arg).formatTo(formatter, 0, -1, -1);
                    } else {
                        result.append(arg);
                    }
                    break;
                default:
                    throw VMError.shouldNotReachHere("Illegal modifier at index " + nextCharIndex + " in " + conversions);
            }
        }
        return result.toString();
    }

    private static boolean isPaddingDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static void appendNumber(boolean defaultLocale, Locale explicitLocale, StringBuilder result, Object arg, char conversion, char zeroChar, int zeroPaddingWidth) {
        boolean hexadecimal = (conversion == HEXADECIMAL_INTEGER || conversion == HEXADECIMAL_INTEGER_UPPER);
        boolean uppercase = conversion == HEXADECIMAL_INTEGER_UPPER;
        String string;
        if (arg instanceof BigInteger) {
            BigInteger bigInteger = (BigInteger) arg;
            if (hexadecimal) {
                string = bigInteger.toString(16);
            } else {
                string = bigInteger.toString();
            }

        } else {
            long value;
            if (arg instanceof Byte) {
                value = ((Byte) arg).byteValue();
                if (value < 0 && hexadecimal) {
                    value += (1L << 8);
                }
            } else if (arg instanceof Short) {
                value = ((Short) arg).shortValue();
                if (value < 0 && hexadecimal) {
                    value += (1L << 16);
                }
            } else if (arg instanceof Integer) {
                value = ((Integer) arg).intValue();
                if (value < 0 && hexadecimal) {
                    value += (1L << 32);
                }
            } else if (arg instanceof Long) {
                value = ((Long) arg).longValue();
            } else {
                throw new IllegalFormatConversionException(conversion, arg.getClass());
            }

            if (hexadecimal) {
                string = Long.toHexString(value);
            } else {
                string = Long.toString(value);
            }
        }

        if (zeroPaddingWidth >= 0) {
            for (int i = string.length(); i < zeroPaddingWidth; ++i) {
                result.append(zeroChar);
            }
        }

        if (uppercase) {
            string = string.toUpperCase(defaultLocale ? Locale.getDefault(Locale.Category.FORMAT) : explicitLocale);
        }
        for (int i = 0; i < string.length(); i++) {
            char localized = (char) (string.charAt(i) - '0' + zeroChar);
            result.append(localized);
        }
    }

    private static void appendCharacter(StringBuilder result, Object arg, Character conversion) {
        String string;
        if (arg instanceof Character) {
            string = ((Character) arg).toString();
        } else {
            int value;
            if (arg instanceof Byte) {
                value = ((Byte) arg).byteValue();
            } else if (arg instanceof Short) {
                value = ((Short) arg).shortValue();
            } else if (arg instanceof Integer) {
                value = ((Integer) arg).intValue();
            } else {
                throw new IllegalFormatConversionException(conversion, arg.getClass());
            }
            if (Character.isValidCodePoint(value)) {
                string = new String(Character.toChars(value));
            } else {
                throw new IllegalFormatCodePointException(value);
            }
        }
        result.append(string);
    }

    private static Formatter ensureFormatter(Formatter formatter, boolean defaultLocale, Locale explicitLocale, Appendable appendable) {
        if (formatter != null) {
            return formatter;
        } else if (defaultLocale) {
            return new Formatter(appendable);
        } else {
            return new Formatter(appendable, explicitLocale);
        }
    }

    private static Character ensureZeroChar(Character zeroChar, Formatter formatter) {
        if (zeroChar != null) {
            return zeroChar;
        }
        Locale locale = formatter.locale();
        return getZeroChar(locale);
    }

    static Character getZeroChar(Locale locale) {
        if (locale == null) {
            /* No Locale means no localization requested. */
            return Character.valueOf('0');
        }
        Character result = singleton().zeroChars.get(locale);
        if (result == null) {
            /* Default zero character is not stored in the map to reduce its size. */
            return Character.valueOf('0');
        } else {
            return result;
        }
    }
}
