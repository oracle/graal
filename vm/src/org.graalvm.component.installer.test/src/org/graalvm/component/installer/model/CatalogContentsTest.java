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
package org.graalvm.component.installer.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Properties;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.remote.RemotePropertiesStorage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class CatalogContentsTest extends CommandTestBase {

    public CatalogContentsTest() {
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, "1.0.0.0");
    }

    RemotePropertiesStorage remstorage;
    CatalogContents coll;

    private Version initVersion(String s) throws IOException {
        Version v = Version.fromString(s);
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, s);
        Path catalogPath = dataFile("../repo/catalog.properties");
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(catalogPath)) {
            props.load(is);
        }
        remstorage = new RemotePropertiesStorage(this,
                        getLocalRegistry(),
                        props, "linux_amd64", v, catalogPath.toUri().toURL());
        coll = new CatalogContents(this, remstorage, getLocalRegistry());
        return v;
    }

    /**
     * Checks that components for the version are provided.
     */
    @Test
    public void testComponentsForVersion19() throws Exception {
        initVersion("1.0.0.0");

        Collection<String> ids = coll.getComponentIDs();
        assertTrue(ids.contains("org.graalvm.python"));
        assertTrue(ids.contains("org.graalvm.ruby"));
        assertTrue(ids.contains("org.graalvm.r"));
        // plus graalvm
        assertEquals(4, ids.size());

    }

    @Test
    public void testComponentsForVersion1901() throws Exception {
        initVersion("1.0.1.0");
        Collection<String> ids = coll.getComponentIDs();
        assertTrue(ids.contains("org.graalvm.python"));
        assertTrue(ids.contains("org.graalvm.r"));
        assertTrue(ids.contains("org.graalvm.ruby"));
        // plus graalvm
        assertEquals(4, ids.size());
    }

    @Test
    public void testComponentsInLatestUpdate() throws Exception {
        initVersion("1.0.1.0");

        Version v;
        v = coll.findComponent("org.graalvm.ruby").getVersion();
        assertEquals("1.0.1.1", v.toString());
        v = coll.findComponent("org.graalvm.python").getVersion();
        assertEquals("1.0.1.0", v.toString());
        v = coll.findComponent("org.graalvm.r").getVersion();
        assertEquals("1.0.1.1", v.toString());
    }

    @Test
    public void testComponentsInPatchedGraal() throws Exception {
        initVersion("1.0.1.1");

        Version v;

        v = coll.findComponent("org.graalvm.ruby").getVersion();
        assertEquals("1.0.1.1", v.toString());
        v = coll.findComponent("org.graalvm.python").getVersion();
        assertEquals("1.0.1.0", v.toString());
        v = coll.findComponent("org.graalvm.r").getVersion();
        assertEquals("1.0.1.1", v.toString());
    }

    @Test
    public void testComponentsForVersion191() throws Exception {
        initVersion("1.1.0.0");

        Collection<String> ids = coll.getComponentIDs();
        assertTrue(ids.contains("org.graalvm.python"));
        assertTrue(ids.contains("org.graalvm.r"));
        // plus graalvm
        assertEquals(3, ids.size());
    }

    /**
     * Checks that the catalog will provide component versions that require to update the
     * distribution for 1.0.1.0. Components in versions 1.1.x.x should be returned.
     */
    @Test
    public void testDistUpdateFor1901() throws Exception {
        initVersion("1.0.1.0");
        coll.setAllowDistUpdate(true);

        Version v;
        v = coll.findComponent("org.graalvm.ruby").getVersion();
        assertEquals("1.0.1.1", v.toString());
        v = coll.findComponent("org.graalvm.python").getVersion();
        assertEquals("1.1.0.0", v.toString());
        v = coll.findComponent("org.graalvm.r").getVersion();
        assertEquals("1.1.0.1", v.toString());
    }

    /**
     * Checks that a specific version is provided.
     */
    @Test
    public void testSpecificVersionNoDistUpdate() throws Exception {
        initVersion("1.0.1.0");

        Version v;
        Version.Match vm = Version.fromString("1.1.0.0").match(Version.Match.Type.EXACT);
        ComponentInfo ci = coll.findComponent("ruby", vm);
        assertNull(ci);

        vm = Version.fromString("1.0.1.0").match(Version.Match.Type.GREATER);
        ci = coll.findComponent("ruby", vm);
        v = ci.getVersion();
        assertEquals("1.0.1.1", v.toString());

        vm = Version.fromString("1.0.1.0").match(Version.Match.Type.GREATER);
        v = coll.findComponent("python", vm).getVersion();
        assertEquals("1.1.0.0", v.toString());

        vm = Version.fromString("1.0.0.0").match(Version.Match.Type.EXACT);
        ci = coll.findComponent("python", vm);
        assertNull(ci);

        v = coll.findComponent("org.graalvm.r").getVersion();
        assertEquals("1.0.1.1", v.toString());

        vm = Version.fromString("1.0.1.0").match(Version.Match.Type.MOSTRECENT);
        v = coll.findComponent("r", vm).getVersion();
        assertEquals("1.0.1.1", v.toString());
    }

    /**
     * Checks that a specific version is provided.
     */
    @Test
    public void testSpecificVersionDistUpdate() throws Exception {
        initVersion("1.0.1.0");
        coll.setAllowDistUpdate(true);

        Version v;
        Version.Match vm = Version.fromString("1.1.0.0").match(Version.Match.Type.EXACT);
        ComponentInfo ci = coll.findComponent("ruby", vm);
        assertNull(ci);

        vm = Version.fromString("1.0.1.0").match(Version.Match.Type.GREATER);
        ci = coll.findComponent("ruby", vm);
        v = ci.getVersion();
        assertEquals("1.0.1.1", v.toString());

        vm = Version.fromString("1.0.1.0").match(Version.Match.Type.GREATER);
        v = coll.findComponent("python", vm).getVersion();
        assertEquals("1.1.0.0", v.toString());

        vm = Version.fromString("1.0.0.0").match(Version.Match.Type.EXACT);
        ci = coll.findComponent("python", vm);
        assertNull(ci);

        v = coll.findComponent("org.graalvm.r").getVersion();
        assertEquals("1.1.0.1", v.toString());

        vm = Version.fromString("1.0.1.0").match(Version.Match.Type.MOSTRECENT);
        v = coll.findComponent("r", vm).getVersion();
        assertEquals("1.1.0.1", v.toString());
    }

    /**
     * 
     */
    @Test
    public void testFindDependentComponents() {

    }
}
