/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.jfr;

import java.nio.file.Files;
import java.nio.file.Path;

import jdk.jfr.Recording;
import org.junit.Test;
import com.oracle.svm.core.jfr.JfrSymbolRepository;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.shared.Uninterruptible;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TestJfrSymbolRepository extends JfrRecordingTest {
    private static final String EMBEDDED_NUL_SYMBOL = "modified-symbol\0encoding";
    private static final String EMOJI_SYMBOL = "modified-symbol \uD83D\uDE80";
    private static final String MALFORMED_SURROGATE_SYMBOL = "modified-symbol \uD83D x";

    @Test
    public void test() throws Throwable {
        // Ensure JFR is created in case this is the first test to run
        String[] events = new String[]{};
        Path path = createTempJfrFile();
        Recording recording = startRecording(events, getDefaultConfiguration(), null, path);
        try {
            JfrSymbolRepository repo = SubstrateJVM.getSymbolRepository();

            assertLookupInvariants(repo);
            recording.dump(createTempJfrFile());
            assertLookupInvariants(repo);
        } finally {
            stopRecording(recording, null);
        }

        assertSymbolUsesUTF8Encoding(path, EMBEDDED_NUL_SYMBOL);
        assertSymbolUsesUTF8Encoding(path, EMOJI_SYMBOL);
        assertSymbolUsesUTF8Encoding(path, MALFORMED_SURROGATE_SYMBOL);
    }

    @Uninterruptible(reason = "Needed for JfrSymbolRepository.getSymbolId().")
    private static long getSymbolId(JfrSymbolRepository repo, String str) {
        return repo.getSymbolId(str, false);
    }

    private static void assertLookupInvariants(JfrSymbolRepository repo) {
        long id1 = getSymbolId(repo, "string1");
        long id2 = getSymbolId(repo, "string2");
        long id1copy = getSymbolId(repo, "string1");
        long empty1 = getSymbolId(repo, "");
        long empty2 = getSymbolId(repo, "");
        long embeddedNul1 = getSymbolId(repo, EMBEDDED_NUL_SYMBOL);
        long embeddedNul2 = getSymbolId(repo, EMBEDDED_NUL_SYMBOL);
        long emoji1 = getSymbolId(repo, EMOJI_SYMBOL);
        long emoji2 = getSymbolId(repo, EMOJI_SYMBOL);
        long malformedSurrogate1 = getSymbolId(repo, MALFORMED_SURROGATE_SYMBOL);
        long malformedSurrogate2 = getSymbolId(repo, MALFORMED_SURROGATE_SYMBOL);
        long nullId = getSymbolId(repo, null);

        assertNotEquals(0, id1);
        assertNotEquals(0, id2);
        assertEquals(id1, id1copy);
        assertNotEquals(id1, id2);
        assertNotEquals(0, empty1);
        assertEquals(empty1, empty2);
        assertNotEquals(0, embeddedNul1);
        assertEquals(embeddedNul1, embeddedNul2);
        assertNotEquals(id1, embeddedNul1);
        assertNotEquals(id2, embeddedNul1);
        assertNotEquals(0, emoji1);
        assertEquals(emoji1, emoji2);
        assertNotEquals(id1, emoji1);
        assertNotEquals(id2, emoji1);
        assertNotEquals(embeddedNul1, emoji1);
        assertNotEquals(0, malformedSurrogate1);
        assertEquals(malformedSurrogate1, malformedSurrogate2);
        assertNotEquals(id1, malformedSurrogate1);
        assertNotEquals(id2, malformedSurrogate1);
        assertNotEquals(embeddedNul1, malformedSurrogate1);
        assertNotEquals(emoji1, malformedSurrogate1);
        assertEquals(0, nullId);
    }

    private static void assertSymbolUsesUTF8Encoding(Path path, String symbol) throws Exception {
        byte[] fileBytes = Files.readAllBytes(path);
        assertTrue("Recording file must contain symbol data encoded as UTF-8: " + symbol,
                        containsByteSequence(fileBytes, toUTF8Bytes(symbol)));
    }
}
