/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.gds;

import com.oracle.truffle.tools.utils.json.JSONException;
import com.oracle.truffle.tools.utils.json.JSONObject;
import com.oracle.truffle.tools.utils.json.JSONTokener;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.CommonConstants;
import static org.graalvm.component.installer.CommonConstants.CAP_JAVA_VERSION;
import org.graalvm.component.installer.IncompatibleException;
import org.graalvm.component.installer.model.ComponentStorage;
import org.graalvm.component.installer.persist.ProxyResource;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class GraalChannelTest extends CommandTestBase {
    @Rule public ProxyResource proxyResource = new ProxyResource();

    GraalChannel channel;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        channel = new GraalChannel(this, this, this.getLocalRegistry());
        storage.graalInfo.put(CommonConstants.CAP_EDITION, "ee");

        storage.graalInfo.put(CommonConstants.CAP_OS_NAME, "linux");
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, "19.3.2");
        storage.graalInfo.put(CAP_JAVA_VERSION, "8");
    }

    @Test
    public void testThrowEmptyStorage() throws Exception {
        try {
            channel.throwEmptyStorage();
            fail("Exception expected");
        } catch (IncompatibleException ex) {
            // ok
        }
        ComponentStorage chStorage = channel.throwEmptyStorage();
        assertNotNull("Stub storage expected for 2nd time", chStorage);

        assertEquals(0, chStorage.listComponentIDs().size());
        assertEquals(0, chStorage.loadComponentMetadata("org.graalvm").size());
        assertEquals(0, chStorage.loadGraalVersionInfo().size());
    }

    @Test
    public void testReleaseNoBases() throws Exception {
        Path p = dataFile("data/rel-no-bases.json");
        try (InputStream is = Files.newInputStream(p)) {
            JSONObject o = new JSONObject(new JSONTokener(is));
            ReleaseEntry e = channel.jsonToRelease("19.3.3-ee-jdk11", o);
            assertNull(e);
            fail("JSONexception expected.");
        } catch (JSONException ex) {
            // OK
        }
    }

    @Test
    public void testReleaseInvalidCatalog() throws Exception {
        Path p = dataFile("data/rel-invalid-catalog.json");
        try (InputStream is = Files.newInputStream(p)) {
            JSONObject o = new JSONObject(new JSONTokener(is));
            ReleaseEntry e = channel.jsonToRelease("19.3.3-ee-jdk11", o);

            assertNull(e);
        }
    }

    private void assertNoRelease(String data) throws Exception {
        Path p = dataFile(data);
        try (InputStream is = Files.newInputStream(p)) {
            JSONObject o = new JSONObject(new JSONTokener(is));
            ReleaseEntry e = channel.jsonToRelease("19.3.3-ee-jdx11", o);
            assertNull(e);
        }
    }

    @Test
    public void testReleaseInvalidJava() throws Exception {
        assertNoRelease("data/rel-badJava.json");
    }

    @Test
    public void testReleaseInvalidVersion() throws Exception {
        try {
            assertNoRelease("data/rel-badVersion.json");
        } catch (IllegalArgumentException ex) {
            // ok
        }
    }

    @Test
    public void testReleaseInvalidEdition() throws Exception {
        try {
            assertNoRelease("data/rel-missingEdition.json");
        } catch (JSONException ex) {
            // OK
        }
    }

    /**
     * Checks that older releases, different javas and unmatching editions are filtered out.
     * 
     * @throws Exception
     */
    @Test
    public void testReleasesFilteredOut() throws Exception {
        Path p = dataFile("data/releases-mix.json");
        channel.setReleasesIndexURL(p.toUri().toURL());
        channel.setAllowUpdates(false);
        List<ReleaseEntry> loaded = channel.loadReleasesIndex(p);
        assertEquals(1, loaded.size());

        channel.setAllowUpdates(true);
        loaded = channel.loadReleasesIndex(p);
        assertEquals(2, loaded.size());
    }

    @Test
    public void testLoadComponentStorage() throws Exception {
        Path p = dataFile("data/releases2.json");
        channel.setAllowUpdates(false);
        channel.setReleasesIndexURL(p.toUri().toURL());
        ComponentStorage store = channel.getStorage();
        Collection<String> cids = store.listComponentIDs();

        assertEquals(5, cids.size());
    }

    @Test
    public void testNormalizedOsAndArch() throws Exception {
        List<ReleaseEntry> loaded;
        Path p = dataFile("data/releases-mix.json");
        channel.setReleasesIndexURL(p.toUri().toURL());
        // disable updates to get the test to previous state
        channel.setAllowUpdates(false);

        storage.graalInfo.put(CAP_JAVA_VERSION, "11");
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, "19.3.2");
        storage.graalInfo.put(CommonConstants.CAP_OS_NAME, CommonConstants.OS_TOKEN_LINUX);
        storage.graalInfo.put(CommonConstants.CAP_OS_ARCH, CommonConstants.ARCH_AMD64);
        loaded = channel.loadReleasesIndex(p);
        assertEquals(1, loaded.size());

        storage.graalInfo.put(CommonConstants.CAP_OS_NAME, CommonConstants.OS_TOKEN_MACOS);
        storage.graalInfo.put(CommonConstants.CAP_OS_ARCH, CommonConstants.ARCH_AMD64);
        loaded = channel.loadReleasesIndex(p);
        assertEquals(1, loaded.size());

        storage.graalInfo.put(CommonConstants.CAP_OS_NAME, CommonConstants.OS_TOKEN_WINDOWS);
        storage.graalInfo.put(CommonConstants.CAP_OS_ARCH, CommonConstants.ARCH_AMD64);
        loaded = channel.loadReleasesIndex(p);
        assertEquals(1, loaded.size());

        storage.graalInfo.put(CAP_JAVA_VERSION, "11");
        storage.graalInfo.put(CommonConstants.CAP_OS_NAME, CommonConstants.OS_TOKEN_MACOS);
        storage.graalInfo.put(CommonConstants.CAP_OS_ARCH, CommonConstants.ARCH_AMD64);
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, "19.3.3");
        loaded = channel.loadReleasesIndex(p);
        assertEquals(1, loaded.size());

    }
}
