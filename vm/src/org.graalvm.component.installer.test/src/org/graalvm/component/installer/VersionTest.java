/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 *
 * @author sdedic
 */
public class VersionTest {
    @Rule public ExpectedException exception = ExpectedException.none();

    @Test
    public void testNoVersionInfimum() throws Exception {
        Version otherNullVersion = Version.fromString(Version.NO_VERSION.toString());

        assertTrue(otherNullVersion.compareTo(Version.NO_VERSION) > 0);
        assertTrue(Version.NO_VERSION.compareTo(otherNullVersion) < 0);

        assertFalse(otherNullVersion.equals(Version.NO_VERSION));
        assertFalse(Version.NO_VERSION.equals(otherNullVersion));
    }

    @Test
    public void testNoVersionEqualToSelf() throws Exception {
        assertTrue(Version.NO_VERSION.compareTo(Version.NO_VERSION) == 0);
        assertTrue(Version.NO_VERSION.equals(Version.NO_VERSION));
    }

    @Test
    public void testNormalizeTo4Numbers() throws Exception {
        assertEquals("1.0.0.0-0.r", Version.fromString("1.0-r").toString());
        assertEquals("1.0.0.0-0.r", Version.fromString("1.0.0-r").toString());
        assertEquals("1.0.0.0-0.r", Version.fromString("1.0.0.0-r").toString());
    }

    @Test
    public void testFailOnTooManyVersions() throws Exception {
        exception.expect(IllegalArgumentException.class);
        assertEquals("1.0.0.0.0-r", Version.fromString("1.0.0.0.0-r").toString());
    }

    @Test
    public void testFailOnTooFewVersions() throws Exception {
        exception.expect(IllegalArgumentException.class);
        assertEquals("1-r", Version.fromString("1.0.0.0.0-r").toString());
    }

    private static void assertOlder(String older, String newer) {
        assertTrue("Versions didn't compare " + older + " < " + newer,
                        Version.fromString(older).compareTo(Version.fromString(newer)) < 0);
        assertFalse(older.equals(newer));
    }

    @Test
    public void testVersionOrder() throws Exception {
        String[] versionSequence = {
                        "1.0",
                        "1.0.1",
                        "1.1.0",
                        "1.1.0-0.dev.1",
                        "1.1.0-0.dev.2",
                        "1.1.0-0.dev8",
                        "1.1.0-0.dev.10",
                        "1.1.0-0.dev13",
                        "1.1.0-1.beta1",
                        "1.1.0-1.beta-3",
                        "1.1.0-1.beta.9",
                        "1.1.0-1.beta.10",
                        "1.1.0-1.beta.13",
                        "1.1.0-1.beta15",
                        "1.1.0-2",
                        "1.1.0.2-0.rc.1",
                        "1.1.0.2-1",
                        "1.1.0.3",
        };

        for (int i = 0; i < versionSequence.length; i++) {
            for (int j = i + 1; j < versionSequence.length; j++) {
                assertOlder(versionSequence[i], versionSequence[j]);
            }
        }
    }

    @Test
    public void testComponentizeVersion() {
        assertEquals("1.0.0.0-0.rc.1", Version.fromString("1.0.0-rc1").toString());
    }

    @Test
    public void testDisplayReleaseVersions() {
        assertEquals("1.0.0", Version.fromString("1.0.0.0").displayString());
        assertEquals("1.0.0", Version.fromString("1.0.0.0-1").displayString());
    }

    @Test
    public void testDisplayPreReleases() {
        assertEquals("1.0.0-rc1", Version.fromString("1.0.0.0-1.rc.1").displayString());
        assertEquals("1.0.0-rc9", Version.fromString("1.0.0.0-1.rc.9").displayString());
    }

    @Test
    public void testDisplayPreReleaseBuilds() {
        assertEquals("1.0.0-beta1.1", Version.fromString("1.0.0.0-1.beta.1.1").displayString());
        assertEquals("1.0.0-beta1.b2", Version.fromString("1.0.0.0-1.beta.1.b.2").displayString());
    }

    @Test
    public void testDisplayWildardVersions() {
        assertEquals("1.0.0-beta1.1", Version.fromString("1.0.0.0-*.beta.1.1").displayString());
    }
}
