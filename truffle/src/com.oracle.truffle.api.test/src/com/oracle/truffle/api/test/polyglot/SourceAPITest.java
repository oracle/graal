/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.test.polyglot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Test;

public class SourceAPITest {

    @Test
    public void testCharSequenceNotMaterialized() throws IOException {
        AtomicBoolean materialized = new AtomicBoolean(false);
        final CharSequence testString = "testString";
        Source source = Source.newBuilder(SourceAPITestLanguage.ID, new CharSequence() {

            public CharSequence subSequence(int start, int end) {
                return testString.subSequence(start, end);
            }

            public int length() {
                return testString.length();
            }

            public char charAt(int index) {
                return testString.charAt(index);
            }

            @Override
            public String toString() {
                materialized.set(true);
                throw new AssertionError("Should not materialize CharSequence.");
            }
        }, "testsource").buildLiteral();

        Context context = Context.create(SourceAPITestLanguage.ID);
        context.eval(source);

        assertEquals(1, source.getLineCount());
        assertTrue(equalsCharSequence(testString, source.getCharacters()));
        assertTrue(equalsCharSequence(testString, source.getCharacters(1)));
        assertEquals(0, source.getLineStartOffset(1));
        assertNull(source.getURL());
        assertNotNull(source.getName());
        assertNull(source.getPath());
        assertEquals(6, source.getColumnNumber(5));
        assertEquals(SourceAPITestLanguage.ID, source.getLanguage());
        assertEquals(testString.length(), source.getLength());
        assertFalse(source.isInteractive());
        assertFalse(source.isInternal());

        // consume reader CharSequence should not be materialized
        CharBuffer charBuffer = CharBuffer.allocate(source.getLength());
        Reader reader = source.getReader();
        reader.read(charBuffer);
        charBuffer.position(0);
        assertEquals(testString, charBuffer.toString());

        assertFalse(materialized.get());
    }

    private static boolean equalsCharSequence(CharSequence seq1, CharSequence seq2) {
        if (seq1.length() != seq2.length()) {
            return false;
        }
        for (int i = 0; i < seq1.length(); i++) {
            if (seq1.charAt(i) != seq2.charAt(i)) {
                return false;
            }
        }
        return true;
    }

}
