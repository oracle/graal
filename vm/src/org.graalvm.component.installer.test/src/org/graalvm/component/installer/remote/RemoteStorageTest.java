/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.remote;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.MetadataException;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.TestBase;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.commands.MockStorage;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class RemoteStorageTest extends TestBase {
    private static final String TEST_GRAAL_FLAVOUR = "linux_amd64";
    private static final String TEST_BASE_URL_DIR = "https://graalvm.io/";
    private static final String TEST_BASE_URL = TEST_BASE_URL_DIR + "download/catalog";
    private RemotePropertiesStorage remStorage;
    private MockStorage storage;
    private ComponentRegistry localRegistry;
    private Properties catalogProps = new Properties();

    @Rule public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        storage = new MockStorage();
        localRegistry = new ComponentRegistry(this, storage);
        remStorage = new RemotePropertiesStorage(this, localRegistry, catalogProps, TEST_GRAAL_FLAVOUR,
                        Version.fromString("0.33-dev"), new URL(TEST_BASE_URL));
        try (InputStream is = getClass().getResourceAsStream("catalog.properties")) {
            catalogProps.load(is);
        }
    }

    private void loadCatalog(String s) throws IOException {
        catalogProps.clear();
        try (InputStream is = getClass().getResourceAsStream(s)) {
            catalogProps.load(is);
        }
    }

    @Test
    public void testListIDs() throws Exception {
        Set<String> ids = remStorage.listComponentIDs();

        List<String> l = new ArrayList<>(ids);
        Collections.sort(l);

        assertEquals(Arrays.asList("r", "ruby"), l);
    }

    private ComponentInfo loadLastComponent(String id) throws IOException {
        Set<ComponentInfo> infos = remStorage.loadComponentMetadata(id);
        if (infos == null || infos.isEmpty()) {
            return null;
        }
        List<ComponentInfo> sorted = new ArrayList<>(infos);
        Collections.sort(sorted, ComponentInfo.versionComparator());
        return sorted.get(sorted.size() - 1);
    }

    @Test
    public void testLoadMetadata() throws Exception {
        ComponentInfo rInfo = loadLastComponent("r");
        assertEquals("FastR 0.33-dev", rInfo.getName());
        assertEquals("R", rInfo.getId());
    }

    @Test
    public void testRelativeRemoteURL() throws Exception {
        ComponentInfo rInfo = loadLastComponent("r");
        assertEquals(new URL(TEST_BASE_URL_DIR + "0.33-dev/graalvm-fastr.zip"), rInfo.getRemoteURL());
    }

    @Test
    public void testInvalidRemoteURL() throws Exception {
        loadCatalog("catalog.bad1.properties");
        // load good compoennt:
        ComponentInfo rInfo = loadLastComponent("ruby");
        assertEquals("ruby", rInfo.getId());

        // and now bad component
        exception.expect(MalformedURLException.class);
        rInfo = loadLastComponent("r");
    }

    @Test
    public void testLoadMetadataMalformed() throws Exception {
        loadCatalog("catalog.bad2.properties");
        // load good compoennt:
        ComponentInfo rInfo = loadLastComponent("r");
        assertEquals("R", rInfo.getId());

        // and now bad component
        exception.expect(MetadataException.class);
        exception.expectMessage("ERROR_InvalidVersion");
        rInfo = loadLastComponent("ruby");
    }

    static byte[] truffleruby2Hash = {
                    (byte) 0xae,
                    (byte) 0xd6,
                    0x7c,
                    0x4d,
                    0x3c,
                    0x04,
                    0x01,
                    0x15,
                    0x13,
                    (byte) 0xf8,
                    (byte) 0x94,
                    0x0b,
                    (byte) 0xf6,
                    (byte) 0xe6,
                    (byte) 0xea,
                    0x22,
                    0x5d,
                    0x34,
                    0x5c,
                    0x27,
                    (byte) 0xa1,
                    (byte) 0xa3,
                    (byte) 0xcd,
                    (byte) 0xe4,
                    (byte) 0xdd,
                    0x0c,
                    0x46,
                    0x36,
                    0x45,
                    0x3f,
                    0x42,
                    (byte) 0xba
    };

    static String truffleruby2HashString = "aed67c4d3c04011513f8940bf6e6ea225d345c27a1a3cde4dd0c4636453f42ba";
    static String truffleruby2HashString2 = "ae:d6:7c:4d:3c:04:01:15:13:f8:94:0b:f6:e6:ea:22:5d:34:5c:27:a1:a3:cd:e4:dd:0c:46:36:45:3f:42:ba";

    @Test
    public void testHashString() throws Exception {
        byte[] bytes = SystemUtils.toHashBytes(truffleruby2HashString);
        assertArrayEquals(truffleruby2Hash, bytes);
    }

    @Test
    public void testHashStringDivided() throws Exception {
        byte[] bytes = SystemUtils.toHashBytes(truffleruby2HashString2);
        assertArrayEquals(truffleruby2Hash, bytes);
    }

    /**
     * Checks that multi-version properties load without error.
     */
    @Test
    public void loadMultipleVersions() throws Exception {
        loadCatalog("catalogMultiVersions.properties");
        Set<String> ids = remStorage.listComponentIDs();
        assertEquals(3, ids.size());
        assertTrue(ids.contains("python"));
    }

    /**
     * Checks that versions prior the graalvm version are not included.
     */
    @Test
    public void obsoleteVersionsNotIncluded() throws Exception {
        loadCatalog("catalogMultiVersions.properties");
        remStorage = new RemotePropertiesStorage(this, localRegistry, catalogProps, TEST_GRAAL_FLAVOUR,
                        Version.fromString("1.0.0.0"), new URL(TEST_BASE_URL));
        Set<ComponentInfo> rubies = remStorage.loadComponentMetadata("ruby");
        // 1.0.0.0 and 1.0.1.0 versions
        assertEquals(2, rubies.size());

        Set<ComponentInfo> rs = remStorage.loadComponentMetadata("r");
        assertEquals(1, rs.size());

        Set<ComponentInfo> pythons = remStorage.loadComponentMetadata("python");
        assertEquals(1, pythons.size());
    }

    @Test
    public void checkMultipleGraalVMDependencies() throws Exception {
        loadCatalog("catalogMultiVersions.properties");

        Set<ComponentInfo> rubies = remStorage.loadComponentMetadata("ruby");
        Set<Version> versions = new HashSet<>();
        for (ComponentInfo ci : rubies) {
            Version compVersion = ci.getVersion();
            assertTrue(versions.add(compVersion));

            String gv = ci.getRequiredGraalValues().get(BundleConstants.GRAAL_VERSION);
            assertEquals(gv, compVersion.toString());
        }
    }

    @Test
    public void loadMultipleComponentFlavours() throws Exception {
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "1.0.0.0");
        loadCatalog("catalogMultiFlavours.properties");
        Set<String> ids = remStorage.listComponentIDs();
        assertEquals(3, ids.size());
        assertTrue(ids.contains("python"));
        List<ComponentInfo> infos = new ArrayList<>(remStorage.loadComponentMetadata("ruby"));
        assertEquals(4, infos.size());

        Collections.sort(infos, (a, b) -> compare(
                        a.getRequiredGraalValues().get(CommonConstants.CAP_JAVA_VERSION),
                        b.getRequiredGraalValues().get(CommonConstants.CAP_JAVA_VERSION)));
        ComponentInfo ci;

        ci = infos.get(2);
        assertEquals("ruby", ci.getId());
        assertEquals("11", ci.getRequiredGraalValues().get(CommonConstants.CAP_JAVA_VERSION));

        ci = infos.get(3);
        assertEquals("ruby", ci.getId());
        assertEquals("8", ci.getRequiredGraalValues().get(CommonConstants.CAP_JAVA_VERSION));

    }

    static int compare(String a, String b) {
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return -1;
        } else if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

}
