/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.strings.test;

import static com.oracle.truffle.api.strings.test.Encodings.J_CODINGS_MAP;
import static com.oracle.truffle.api.strings.test.Encodings.toEnumName;

import java.util.Comparator;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import org.graalvm.shadowed.org.jcodings.Encoding;

import com.oracle.truffle.api.strings.TruffleString;

public class TruffleStringEncodingsEnumGenerator {

    /**
     * Generator for {@link TruffleString.Encoding}. To run, execute the following mx command.
     *
     * <pre>
     *     mx java -cp `mx paths truffle:TRUFFLE_API`:`mx paths truffle:TRUFFLE_TEST`:`mx paths sdk:COLLECTIONS`:`mx paths truffle:TRUFFLE_JCODINGS` \
     *     com.oracle.truffle.api.strings.test.TruffleStringEncodingsEnumGenerator
     * </pre>
     */
    public static void main(String[] args) {
        // Checkstyle: stop
        int utf32 = 0;
        int utf16 = 1;
        int latin1 = 2;
        int utf8 = 3;
        int ascii = 4;
        int bytes = 5;
        int firstUnsupported = 6;
        Set<String> supported = Set.of("UTF-32", "UTF-32LE", "UTF-32BE", "UTF-16", "UTF-16LE", "UTF-16BE", "UTF-8", "ISO-8859-1", "US-ASCII", "ASCII-8BIT");
        long lastID = J_CODINGS_MAP.size() - supported.size() + firstUnsupported;
        int utf32Unsupported = (int) lastID;
        int utf16Unsupported = (int) (lastID + 1);
        System.out.println("/* directly supported encodings */");
        System.out.printf("""
                        /**
                         * UTF-32LE. Directly supported if the current system is little-endian.
                         *
                         * @since 22.1
                         */
                        UTF_32LE(littleEndian() ? %d : %d, "UTF-32LE", littleEndian() ? 2 : 0, littleEndian()),
                        /**
                         * UTF-32BE. Directly supported if the current system is big-endian.
                         *
                         * @since 22.1
                         */
                        UTF_32BE(littleEndian() ? %d : %d, "UTF-32BE", littleEndian() ? 0 : 2, bigEndian()),
                        /**
                         * UTF-16LE. Directly supported if the current system is little-endian.
                         *
                         * @since 22.1
                         */
                        UTF_16LE(littleEndian() ? %d : %d, "UTF-16LE", littleEndian() ? 1 : 0, false),
                        /**
                         * UTF-16BE. Directly supported if the current system is big-endian.
                         *
                         * @since 22.1
                         */
                        UTF_16BE(littleEndian() ? %d : %d, "UTF-16BE", littleEndian() ? 0 : 1, false),
                        """,
                        utf32, utf32Unsupported, utf32Unsupported, utf32,
                        utf16, utf16Unsupported, utf16Unsupported, utf16);
        System.out.println("""
                        /**
                         * ISO-8859-1, also known as LATIN-1, which is equivalent to US-ASCII + the LATIN-1
                         * Supplement Unicode block.
                         *
                         * @since 22.1
                         */""");
        printInitializer(J_CODINGS_MAP.get("ISO_8859_1"), latin1, 0);
        System.out.println("""
                        /**
                         * UTF-8.
                         *
                         * @since 22.1
                         */""");
        printInitializer(J_CODINGS_MAP.get("UTF_8"), utf8, 0);
        System.out.println("""
                        /**
                         * US-ASCII, which maps only 7-bit characters.
                         *
                         * @since 22.1
                         */""");
        printInitializer(J_CODINGS_MAP.get("US_ASCII"), ascii, 0);
        System.out.println("""
                        /**
                         * Special "encoding" BYTES: This encoding is identical to US-ASCII, but treats all values
                         * outside the us-ascii range as valid codepoints as well. Caution: no codepoint mappings
                         * are defined for non-us-ascii values in this encoding, so {@link SwitchEncodingNode} will
                         * replace all of them with {@code '?'} when converting from or to BYTES! To preserve all
                         * bytes and "reinterpret" a BYTES string in another encoding, use
                         * {@link ForceEncodingNode}.
                         *
                         * @since 22.1
                         */""");
        printInitializer(J_CODINGS_MAP.get("BYTES"), bytes, 0);
        System.out.println();
        System.out.println("/* encodings supported by falling back to JCodings */");
        System.out.println();
        int firstNonAsciiCompatible = printInitializerExotic(e -> e.isAsciiCompatible() && !supported.contains(e.toString()), firstUnsupported);
        System.out.println("/* non-ascii-compatible encodings */");
        int last = printInitializerExotic(e -> !e.isAsciiCompatible() && !supported.contains(e.toString()), firstNonAsciiCompatible);
        if (last > utf16Unsupported || last > utf32Unsupported) {
            throw new IllegalStateException("overlapping ids!");
        }

        System.out.println();
        printCompatibilityMethod(7, firstNonAsciiCompatible);
        printCompatibilityMethod(8, latin1 + 1);
        printCompatibilityMethod(16, utf16 + 1);
        printCompatibilityMethod(32, utf32 + 1);
        System.out.printf("""
                        static boolean isSupported(int encoding) {
                            return encoding < %d;
                        }
                        """, firstUnsupported);
        System.out.printf("""
                        static boolean isUnsupported(int encoding) {
                            return encoding >= %d;
                        }
                        """, firstUnsupported);
        // Checkstyle: resume
    }

    private static void printCompatibilityMethod(int bits, int threshold) {
        // Checkstyle: stop
        System.out.printf("""
                        static boolean is%dBitCompatible(int encoding) {
                            return encoding < %d;
                        }
                        """, bits, threshold);
        // Checkstyle: resume
    }

    private static int printInitializerExotic(Predicate<Encoding> filter, int iParam) {
        Encoding[] filtered = StreamSupport.stream(J_CODINGS_MAP.getValues().spliterator(), false).filter(filter).sorted(Comparator.comparing(a -> toEnumName(a.toString()))).toArray(Encoding[]::new);
        int i = iParam;
        for (Encoding e : filtered) {
            // Checkstyle: stop
            System.out.printf("/**\n * %s.\n *\n * @since 22.1\n */\n", toEnumName(e.toString()).replace('_', '-'));
            // Checkstyle: resume
            printInitializer(e, i++);
        }
        return i;
    }

    private static void printInitializer(Encoding e, int i) {
        // Checkstyle: stop
        System.out.printf("%s(%d, \"%s\", %s),\n", toEnumName(e.toString()), i, e.toString(), e.isFixedWidth());
        // Checkstyle: resume
    }

    private static void printInitializer(Encoding e, int i, int naturalStride) {
        // Checkstyle: stop
        System.out.printf("%s(%d, \"%s\", %d, %s),\n", toEnumName(e.toString()), i, e.toString(), naturalStride, e.isFixedWidth());
        // Checkstyle: resume
    }
}
