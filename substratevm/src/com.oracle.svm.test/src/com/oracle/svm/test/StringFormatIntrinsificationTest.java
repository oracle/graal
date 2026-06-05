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
package com.oracle.svm.test;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;
import java.util.Locale;

import org.junit.Test;

public class StringFormatIntrinsificationTest {

    @Test
    public void testExistingSimpleFormats() {
        assertEquals("string: value number: 42", String.format("string: %s number: %d", "value", 42));
        assertEquals("number: 07", String.format("number: %02d", 7));
        assertEquals("number: -01", String.format("number: %03d", -1));
        Locale arabicDigits = Locale.forLanguageTag("ar-u-nu-arab");
        assertEquals("arab", arabicDigits.getUnicodeLocaleType("nu"));
        assertEquals("localized: -\u0660\u0661", String.format(arabicDigits, "localized: %03d", -1));
        assertEquals("string: text number: 12 again: 12", String.format("string: %2$s number: %1$d again: %<d", 12, "text"));
        assertEquals("special % hex 0xff", String.format("special %% hex %#x", 255));
        assertEquals("negative big hex: -0x1 octal: -01", String.format("negative big hex: %#x octal: %#o", new BigInteger("-1"), new BigInteger("-1")));
        assertEquals("line" + System.lineSeparator() + "next", "line%nnext".formatted());
    }

    @Test
    public void testOctalBooleanAndHashCodeFormats() {
        Object value = new Object() {
            @Override
            public int hashCode() {
                return 0x12ab;
            }
        };

        assertEquals("octal: 377 0377 37777777777", String.format("octal: %o %#o %o", (byte) -1, 255, -1));
        assertEquals("padded: 007 0x0a 0X0A", String.format("padded: %#03o %#04x %#04X", 7, 10, 10));
        assertEquals("big: 1234567012345670", String.format("big: %o", new BigInteger("1234567012345670", 8)));
        assertEquals("booleans: true false true FALSE", String.format("booleans: %b %b %b %B", true, false, "non-null", null));
        assertEquals("hash: 12ab 12AB null NULL", String.format("hash: %h %H %h %H", value, value, null, null));
    }

    @Test
    public void testWidthPrecisionAndUpperCaseFormats() {
        assertEquals("[   hi]", String.format("[%5s]", "hi"));
        assertEquals("[   42]", String.format("[%5d]", 42));
        assertEquals("[   ff]", String.format("[%5x]", 255));
        assertEquals("[  377]", String.format("[%5o]", 255));
        assertEquals("[   tru   12A]", String.format("[%6.3b %5.3H]", true, 0x12ab));
        assertEquals("[HELLO A]", String.format("[%S %C]", "hello", 'a'));
        assertEquals("[hel]", String.format("[%.3s]", "hello"));
        assertEquals("[  HEL]", String.format("[%5.3S]", "hello"));

        Locale previousLocale = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("tr"));
            assertEquals("[\u0130 \u0130]", String.format((Locale) null, "[%S %C]", "i", 'i'));
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, previousLocale);
        }
    }

    @Test
    public void testUnsupportedFormatStillFallsBackToFormatter() {
        assertEquals("float: 3.14 simple: ok hex: 0xff", String.format("float: %.2f simple: %s hex: %#x", Math.PI, "ok", 255));
        assertEquals("padded alternate:  0xff", String.format("padded alternate: %#5x", 255));
        assertEquals("fallback: OK OK 1970", String.format("fallback: %1$S %<S %3$tY", "ok", "unused", 12 * 60 * 60 * 1000L));
    }
}
