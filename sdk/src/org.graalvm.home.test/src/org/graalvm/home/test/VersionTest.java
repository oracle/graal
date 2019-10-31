/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.home.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.home.Version;
import org.junit.Test;

public class VersionTest {

    @Test
    public void testParsing() {
        assertParseEquals("1.0.0", "1.0");
        assertParseEquals("1.0.0-", "1.0-");
        assertParseEquals("1.0.0--", "1.0--");
        assertParseEquals("1.0.0-dev", "1.0-dev");
        assertParseEquals("0.1.0", "0.1.0");
        assertParseEquals("0.0.1", "0.0.1.0");
        assertParseEquals(Integer.MAX_VALUE + "." + Integer.MAX_VALUE + ".1", Integer.MAX_VALUE + "." + Integer.MAX_VALUE + ".1.0");
        assertParseEquals("1.0.0-1", "1-1");
        assertParseEquals("1.0.0-1", "1-1");
        assertParseEquals("0.0.0.0.0.0.0.0.1", "0.0.0.0.0.0.0.0.1.0");

        assertParseFails("");
        assertParseFails(".");
        assertParseFails("_");
        assertParseFails("_");
        assertParseFails("..");
        assertParseFails(".0");
        assertParseFails(".1");
        assertParseFails("0.");
        assertParseFails("0_");
        assertParseFails("1.");
        assertParseFails("1_");
        assertParseFails(".0.");
        assertParseFails(".1.");
        assertParseFails("..0");
        assertParseFails("..1");
        assertParseFails("0..");
        assertParseFails("1..");
        assertParseFails("0");
        assertParseFails("0.0");
        assertParseFails("0.0.0");
        assertParseFails("0.0.0-dev");
        assertParseFails("0.0.0.0.0.0.0.0-");
        assertParseFails("-");
        assertParseFails("-1");
        assertParseFails("1.-1");
        assertParseFails("-1.1");
        assertParseFails(Integer.MAX_VALUE + "0.1");
    }

    @Test
    public void testSnapshot() {
        assertEquals(-1, Version.parse("snapshot").compareTo(0, 0, 0, 0, 1));
        assertEquals(1, Version.create(0, 0, 0, 0, 1).compareTo(Version.parse("snapshot")));
        assertEquals(Version.parse("snapshot"), Version.parse("snapshot"));
        assertEquals("snapshot", Version.parse("snapshot").toString());
        assertEquals(Version.parse("snapshot").hashCode(), Version.parse("snapshot").hashCode());
        assertTrue(Version.parse("snapshot").isSnapshot());
        assertFalse(Version.parse("snapshot").isRelease());
    }

    @Test
    public void testDevSuffix() {
        assertEquals(0, Version.parse("19.3-dev").compareTo(19, 3));
        assertEquals(0, Version.create(19, 3).compareTo(Version.parse("19.3-dev")));
        assertEquals(-1, Version.parse("19.2-dev").compareTo(19, 3));
        assertEquals(1, Version.parse("19.4-dev").compareTo(19, 3));

        assertEquals(Version.parse("19.3-dev"), Version.parse("19.3-dev"));
        assertNotEquals(Version.parse("19.3-dev"), Version.parse("19.3"));
        assertEquals("19.3.0-dev", Version.parse("19.3-dev").toString());
        assertEquals(Version.parse("19.3-dev").hashCode(), Version.parse("19.3-dev").hashCode());

        assertTrue(Version.parse("19.3-dev").isSnapshot());
        assertFalse(Version.parse("19.3-dev").isRelease());
    }

    @Test
    public void testCreate() {
        assertNPE(() -> Version.create((int[]) null));
        assertIllegalArgument(() -> Version.create(-1));
        assertIllegalArgument(() -> Version.create(0, 0));
        assertIllegalArgument(() -> Version.create(1, -1));
        assertEquals(Version.create(1, 0), Version.create(1));
        assertEquals(Version.create(1, 0, 0), Version.create(1));
        assertEquals(0, Version.create(1, 0, 0).compareTo(Version.create(1)));
        assertTrue(Version.create(19).isRelease());
        assertTrue(!Version.create(19).isSnapshot());
    }

    @SuppressWarnings("unlikely-arg-type")
    @Test
    public void testEquals() {
        assertFalse(Version.create(19).equals(null));
        assertFalse(Version.create(19).equals(this));
        assertEquals(Version.create(19), Version.parse("19"));
        assertNotEquals(Version.create(19), Version.parse("19-dev"));
        assertEquals(Version.parse("19-dev"), Version.parse("19-dev"));
    }

    @Test
    public void testCurrent() {
        assertNotNull(Version.getCurrent());
        assertNotNull(Version.getCurrent().toString());
        assertTrue(Version.getCurrent().isSnapshot() || Version.getCurrent().isRelease());

    }

    @Test
    public void testCompare() {
        Version v19 = Version.create(19);
        assertNPE(() -> v19.compareTo((Version) null));

        assertEquals(0, Version.parse("19.3").compareTo(Version.parse("19.3-dev")));
        assertEquals(0, Version.parse("19.3").compareTo(Version.parse("19.3-42")));
        assertEquals(0, Version.parse("19.3-dev").compareTo(Version.parse("19.3-42")));

        assertEquals(0, Version.create(19).compareTo(19));
        assertEquals(-1, Version.create(19).compareTo(20));

        assertEquals(-1, Version.create(19, 0).compareTo(19, 1));
        assertEquals(1, Version.create(19, 1).compareTo(19, 0));
        assertEquals(1, Version.create(20, 0).compareTo(19, 1));

        assertEquals(0, Version.create(19).compareTo(19));
        assertEquals(-1, Version.create(19, 0).compareTo(19, 1));
        assertEquals(1, Version.create(19, 1).compareTo(19, 0));
        assertEquals(1, Version.create(20, 0).compareTo(19, 1));

        List<Version> expected = new ArrayList<>();
        expected.add(Version.create(19));
        expected.add(Version.create(19, 0, 1));
        expected.add(Version.create(19, 0, 2));
        expected.add(Version.create(19, 1));
        expected.add(Version.create(20, 0));
        expected.add(Version.create(20, 0, 1));
        expected.add(Version.create(20, 0, 2));
        expected.add(Version.create(20, 1));
        List<Version> actual = new ArrayList<>(expected);
        Collections.shuffle(actual);
        Collections.sort(actual);
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i));
        }
    }

    private static void assertNPE(Runnable v) {
        try {
            v.run();
            fail();
        } catch (NullPointerException e) {
        }
    }

    private static void assertIllegalArgument(Runnable v) {
        try {
            v.run();
            fail();
        } catch (IllegalArgumentException e) {
        }
    }

    private static void assertParseFails(String v) {
        try {
            Version.parse(v);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid version string '" + v + "'.", e.getMessage());
        }
    }

    private static void assertParseEquals(String expected, String parse) {
        assertEquals(expected, Version.parse(parse).toString());
        assertEquals(Version.parse(expected).toString(), Version.parse(parse).toString());
        assertTrue(Version.parse(expected).equals(Version.parse(parse)));
        assertTrue(Version.parse(expected).compareTo(Version.parse(parse)) == 0);
    }

}
