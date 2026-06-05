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
import java.util.FormattableFlags;
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
 * build time. Simple format specifiers defined in this class are replaced with direct
 * {@link StringBuilder} operations. More complicated single-argument specifiers can be mixed into
 * the same format string and are represented as small fallback segments that use
 * {@link Formatter}. The arguments are transformed to avoid format string parsing at run time for
 * the direct segments. For example, the call to
 * 
 * String.format("string: %s number: %d", new Object[] {someString, someNumber});
 * 
 * is transformed to
 * 
 * StringFormat.format("sssd", new Object[] {"string: ", someString, " number: ", someNumber});
 * 
 * The format string is already split at format specifier positions, and the literal parts of the
 * format strings are treated the same as string format arguments. The new first parameter, called
 * "conversions" since it contains the conversion specifiers of the original format string,
 * contains the direct conversion characters and compact metadata for width and precision. Literal
 * parts have the string conversion character. Fallback segments have an internal conversion
 * character and consume two array elements: the fallback format string and the original argument.
 * 
 * If alternate formatting is added to a direct conversion, a {@code #} marker is added after the
 * conversion character. If width is added to a direct conversion, the width and padding character
 * are added after the conversion character and possible alternate marker. Only widths up to 9
 * characters are accepted, so the width always takes up only one character. For example, the call to
 * 
 * String.format("number: %02d", new Object[] {someNumber});
 * 
 * is transformed to
 * 
 * StringFormat.format("sd20", new Object[] {"number: ", someNumber};
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
 * Complicated format specifiers, like floating point numbers and dates, use fallback segments when
 * they appear in an otherwise supported format string. For integer numbers, decimal separators are
 * not supported by the direct path. But the "zero" character localization needs to be applied even
 * for the most basic %d format string. To avoid pulling localization code in, all non-default
 * "zero" characters of all {@link Locale} in the image heap are collected at image build time in
 * {@link #zeroChars}, using an object replacer in {@code StringFormatFeature}. For dynamic
 * locales, a few Unicode locale extension numbering systems are handled directly.
 * 
 * The "alternate" formatting mode is not supported, with the exception of the octal and
 * hexadecimal integer format specifiers %o, %x, and %X, where it only adds a small prefix. %n and
 * %% are also appended already as string literals. For example,
 * 
 * String.format("special %% hex %#x", new Object[] {someNumber});
 * 
 * is transformed to
 * 
 * StringFormat.format("sx#", new Object[] {"special % hex ", someNumber});
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
    static final char OCTAL_INTEGER = 'o';
    static final char HEXADECIMAL_INTEGER = 'x';
    static final char HEXADECIMAL_INTEGER_UPPER = 'X';
    static final char BOOLEAN = 'b';
    static final char BOOLEAN_UPPER = 'B';
    static final char HASHCODE = 'h';
    static final char HASHCODE_UPPER = 'H';
    static final char CHARACTER = 'c';
    static final char CHARACTER_UPPER = 'C';
    static final char STRING = 's';
    static final char STRING_UPPER = 'S';
    static final char FORMATTER = 'f';

    static final String SUPPORTED_CONVERSIONS = "" + DECIMAL_INTEGER + OCTAL_INTEGER + HEXADECIMAL_INTEGER + HEXADECIMAL_INTEGER_UPPER + BOOLEAN + BOOLEAN_UPPER + HASHCODE + HASHCODE_UPPER +
                    CHARACTER + CHARACTER_UPPER + STRING + STRING_UPPER;

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
        /* Initialized lazily because it depends on the Locale. */
        Character zeroChar = null;

        int nextArgIndex = 0;
        int nextCharIndex = 0;
        while (nextArgIndex < args.length) {
            char conversion = conversions.charAt(nextCharIndex++);
            Object arg = args[nextArgIndex++];
            FormatModifiers modifiers = readModifiers(conversions, nextCharIndex);
            nextCharIndex = modifiers.nextCharIndex;
            zeroChar = appendArgument(defaultLocale, explicitLocale, result, zeroChar, null, arg, conversion, modifiers);
        }
        assert nextCharIndex == conversions.length();
        return result.toString();
    }

    static String formatWithFormatterFallback(String conversions, Object[] args) {
        return formatWithFormatterFallback(true, null, conversions, args);
    }

    static String formatWithFormatterFallback(Locale locale, String conversions, Object[] args) {
        return formatWithFormatterFallback(false, locale, conversions, args);
    }

    private static String formatWithFormatterFallback(boolean defaultLocale, Locale explicitLocale, String conversions, Object[] args) {
        StringBuilder result = new StringBuilder();
        /* Formatter is created lazily, it might also involve looking up the default Locale. */
        Formatter formatter = null;
        /* Initialized lazily because it depends on the Locale. */
        Character zeroChar = null;

        int nextArgIndex = 0;
        int nextCharIndex = 0;
        while (nextArgIndex < args.length) {
            char conversion = conversions.charAt(nextCharIndex++);
            if (conversion == FORMATTER) {
                String format = (String) args[nextArgIndex++];
                Object arg = args[nextArgIndex++];
                formatter = ensureFormatter(formatter, defaultLocale, explicitLocale, result);
                formatter.format(format, arg);
                continue;
            }

            Object arg = args[nextArgIndex++];
            FormatModifiers modifiers = readModifiers(conversions, nextCharIndex);
            nextCharIndex = modifiers.nextCharIndex;
            zeroChar = appendArgument(defaultLocale, explicitLocale, result, zeroChar, formatter, arg, conversion, modifiers);
        }
        assert nextCharIndex == conversions.length();
        return result.toString();
    }

    private static FormatModifiers readModifiers(String conversions, int nextCharIndex) {
        int charIndex = nextCharIndex;
        boolean alternate = false;
        int width = -1;
        char padding = ' ';
        int precision = -1;
        if (charIndex < conversions.length() && conversions.charAt(charIndex) == '#') {
            alternate = true;
            charIndex++;
        }
        if (charIndex < conversions.length() && isPaddingDigit(conversions.charAt(charIndex))) {
            width = conversions.charAt(charIndex) - '0';
            charIndex++;
            padding = conversions.charAt(charIndex++);
        }
        if (charIndex < conversions.length() && conversions.charAt(charIndex) == '.') {
            charIndex++;
            precision = conversions.charAt(charIndex) - '0';
            charIndex++;
        }
        return new FormatModifiers(alternate, width, padding, precision, charIndex);
    }

    private static Character appendArgument(boolean defaultLocale, Locale explicitLocale, StringBuilder result, Character zeroChar, Formatter formatter, Object arg, char conversion,
                    FormatModifiers modifiers) {
        Character updatedZeroChar = zeroChar;
        if (arg == null) {
            String string;
            if (conversion == BOOLEAN) {
                string = "false";
            } else if (conversion == BOOLEAN_UPPER) {
                string = "FALSE";
            } else {
                string = isUpperCaseConversion(conversion) ? "NULL" : "null";
            }
            appendString(result, string, modifiers.width, ' ', modifiers.precision);
            return updatedZeroChar;
        }

        switch (conversion) {
            case DECIMAL_INTEGER:
                updatedZeroChar = ensureZeroChar(updatedZeroChar, defaultLocale, explicitLocale);
                appendNumber(defaultLocale, explicitLocale, result, arg, conversion, updatedZeroChar.charValue(), modifiers.alternate, modifiers.width, modifiers.padding);
                break;
            case OCTAL_INTEGER:
            case HEXADECIMAL_INTEGER:
            case HEXADECIMAL_INTEGER_UPPER:
                appendNumber(defaultLocale, explicitLocale, result, arg, conversion, '0', modifiers.alternate, modifiers.width, modifiers.padding);
                break;
            case BOOLEAN:
            case BOOLEAN_UPPER:
                appendBoolean(defaultLocale, explicitLocale, result, arg, conversion, modifiers.width, modifiers.precision);
                break;
            case HASHCODE:
            case HASHCODE_UPPER:
                appendHashCode(defaultLocale, explicitLocale, result, arg, conversion, modifiers.width, modifiers.precision);
                break;
            case CHARACTER:
            case CHARACTER_UPPER:
                appendCharacter(defaultLocale, explicitLocale, result, arg, conversion, modifiers.width);
                break;
            case STRING:
            case STRING_UPPER:
                if (arg instanceof Formattable) {
                    Formatter stringFormatter = ensureFormatter(formatter, defaultLocale, explicitLocale, result);
                    ((Formattable) arg).formatTo(stringFormatter, conversion == STRING_UPPER ? FormattableFlags.UPPERCASE : 0, modifiers.width, modifiers.precision);
                } else {
                    appendString(defaultLocale, explicitLocale, result, arg.toString(), conversion == STRING_UPPER, modifiers.width, modifiers.precision);
                }
                break;
            default:
                throw VMError.shouldNotReachHere("Illegal modifier " + conversion);
        }
        return updatedZeroChar;
    }

    private static final class FormatModifiers {
        final boolean alternate;
        final int width;
        final char padding;
        final int precision;
        final int nextCharIndex;

        FormatModifiers(boolean alternate, int width, char padding, int precision, int nextCharIndex) {
            this.alternate = alternate;
            this.width = width;
            this.padding = padding;
            this.precision = precision;
            this.nextCharIndex = nextCharIndex;
        }
    }

    private static boolean isUpperCaseConversion(char conversion) {
        return conversion == HEXADECIMAL_INTEGER_UPPER || conversion == BOOLEAN_UPPER || conversion == HASHCODE_UPPER || conversion == CHARACTER_UPPER || conversion == STRING_UPPER;
    }

    private static boolean isPaddingDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static void appendNumber(boolean defaultLocale, Locale explicitLocale, StringBuilder result, Object arg, char conversion, char zeroChar, boolean alternate, int width, char padding) {
        boolean octal = conversion == OCTAL_INTEGER;
        boolean hexadecimal = (conversion == HEXADECIMAL_INTEGER || conversion == HEXADECIMAL_INTEGER_UPPER);
        boolean uppercase = conversion == HEXADECIMAL_INTEGER_UPPER;
        String string;
        if (arg instanceof BigInteger) {
            BigInteger bigInteger = (BigInteger) arg;
            if (hexadecimal) {
                string = bigInteger.toString(16);
            } else if (octal) {
                string = bigInteger.toString(8);
            } else {
                string = bigInteger.toString();
            }

        } else {
            long value;
            if (arg instanceof Byte) {
                value = ((Byte) arg).byteValue();
                if (value < 0 && (hexadecimal || octal)) {
                    value += (1L << 8);
                }
            } else if (arg instanceof Short) {
                value = ((Short) arg).shortValue();
                if (value < 0 && (hexadecimal || octal)) {
                    value += (1L << 16);
                }
            } else if (arg instanceof Integer) {
                value = ((Integer) arg).intValue();
                if (value < 0 && (hexadecimal || octal)) {
                    value += (1L << 32);
                }
            } else if (arg instanceof Long) {
                value = ((Long) arg).longValue();
            } else {
                throw new IllegalFormatConversionException(conversion, arg.getClass());
            }

            if (hexadecimal) {
                string = Long.toHexString(value);
            } else if (octal) {
                string = Long.toOctalString(value);
            } else {
                string = Long.toString(value);
            }
        }

        if (uppercase) {
            string = string.toUpperCase(upperCaseLocale(defaultLocale, explicitLocale));
        }

        String prefix = "";
        if (alternate) {
            if (octal) {
                prefix = "0";
            } else if (conversion == HEXADECIMAL_INTEGER) {
                prefix = "0x";
            } else if (conversion == HEXADECIMAL_INTEGER_UPPER) {
                prefix = "0X";
            } else {
                throw VMError.shouldNotReachHere("Illegal alternate conversion " + conversion);
            }
        }

        int start = 0;
        boolean negative = string.charAt(0) == '-';
        boolean emitSignBeforePadding = negative && (padding == '0' || !prefix.isEmpty());
        if (emitSignBeforePadding) {
            result.append('-');
            start = 1;
        }
        if (!prefix.isEmpty()) {
            result.append(prefix);
        }
        if (width >= 0) {
            for (int i = string.length() + prefix.length(); i < width; ++i) {
                result.append(padding == '0' ? zeroChar : padding);
            }
        }

        for (int i = start; i < string.length(); i++) {
            char c = string.charAt(i);
            if (c >= '0' && c <= '9') {
                result.append((char) (c - '0' + zeroChar));
            } else {
                result.append(c);
            }
        }
    }

    private static void appendString(boolean defaultLocale, Locale explicitLocale, StringBuilder result, String string, boolean upperCase, int width, int precision) {
        String value = string;
        if (precision >= 0 && string.length() > precision) {
            value = string.substring(0, precision);
        }
        if (upperCase) {
            value = value.toUpperCase(upperCaseLocale(defaultLocale, explicitLocale));
        }
        appendString(result, value, width, ' ', -1);
    }

    private static void appendString(StringBuilder result, String string, int width, char padding, int precision) {
        String value = string;
        if (precision >= 0 && string.length() > precision) {
            value = string.substring(0, precision);
        }
        if (width >= 0) {
            for (int i = value.length(); i < width; i++) {
                result.append(padding);
            }
        }
        result.append(value);
    }

    private static void appendBoolean(boolean defaultLocale, Locale explicitLocale, StringBuilder result, Object arg, char conversion, int width, int precision) {
        String string = arg instanceof Boolean ? arg.toString() : "true";
        appendString(defaultLocale, explicitLocale, result, string, conversion == BOOLEAN_UPPER, width, precision);
    }

    private static void appendHashCode(boolean defaultLocale, Locale explicitLocale, StringBuilder result, Object arg, char conversion, int width, int precision) {
        String string = Integer.toHexString(arg.hashCode());
        appendString(defaultLocale, explicitLocale, result, string, conversion == HASHCODE_UPPER, width, precision);
    }

    private static void appendCharacter(boolean defaultLocale, Locale explicitLocale, StringBuilder result, Object arg, Character conversion, int width) {
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
        appendString(defaultLocale, explicitLocale, result, string, conversion == CHARACTER_UPPER, width, -1);
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

    private static Character ensureZeroChar(Character zeroChar, boolean defaultLocale, Locale explicitLocale) {
        if (zeroChar != null) {
            return zeroChar;
        }
        return getZeroChar(defaultLocale ? Locale.getDefault(Locale.Category.FORMAT) : explicitLocale);
    }

    private static Locale upperCaseLocale(boolean defaultLocale, Locale explicitLocale) {
        if (defaultLocale) {
            return Locale.getDefault(Locale.Category.FORMAT);
        } else if (explicitLocale != null) {
            return explicitLocale;
        }
        return Locale.getDefault(Locale.Category.FORMAT);
    }

    static Character getZeroChar(Locale locale) {
        if (locale == null) {
            /* No Locale means no localization requested. */
            return Character.valueOf('0');
        }
        Character result = singleton().zeroChars.get(locale);
        if (result != null) {
            return result;
        }
        Character numberingSystemZeroChar = getZeroCharForNumberingSystem(locale.getUnicodeLocaleType("nu"));
        if (numberingSystemZeroChar != null) {
            return numberingSystemZeroChar;
        }
        /* Default zero character is not stored in the map to reduce its size. */
        return Character.valueOf('0');
    }

    private static Character getZeroCharForNumberingSystem(String numberingSystem) {
        if (numberingSystem == null) {
            return null;
        }
        switch (numberingSystem) {
            case "arab":
                return Character.valueOf('\u0660');
            case "arabext":
                return Character.valueOf('\u06F0');
            default:
                return null;
        }
    }
}
