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
package org.graalvm.component.installer.remote;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.SoftwareChannelSource;
import org.graalvm.component.installer.model.GraalEdition;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class GraalEditionListTest extends CommandTestBase {

    private void loadReleaseFile(String name) throws IOException {
        try (InputStream stm = getClass().getResourceAsStream("data/" + name)) {
            Properties props = new Properties();
            props.load(stm);
            props.stringPropertyNames().forEach((s) -> {
                this.storage.graalInfo.put(s, props.getProperty(s));
            });
        }
    }

    /**
     * Checks that GraalVM 20.x style single property is parsed properly. Multiple SoftwareChannel
     * Sources should be created, but just one Edition, the default one.
     * 
     * @throws Exception
     */
    @Test
    public void testParseRelease20CatalogProperty() throws Exception {
        loadReleaseFile("release20ce.properties");
        storage.graalInfo.put(CommonConstants.CAP_EDITION, "ce");

        GraalEditionList list = new GraalEditionList(this, this, getLocalRegistry());
        list.setDefaultCatalogSpec(storage.graalInfo.get(CommonConstants.RELEASE_CATALOG_KEY));
        List<GraalEdition> eds = list.editions();
        assertFalse(eds.isEmpty());
        assertEquals(1, eds.size());
        GraalEdition ge = eds.get(0);

        assertSame(list.getDefaultEdition(), ge);
        assertEquals("Expected ce edition", "ce", ge.getId());
        assertEquals("Label from graal caps should be capitalized", "CE", ge.getDisplayName());
    }

    @Test
    public void testParseRelease20EEWithLabel() throws Exception {
        // this one contains multiple
        loadReleaseFile("release20ee.properties");
        // substitute "ee", since that's what the DirectoryStorage does in presence of
        // 'vm-enterprise' now:
        storage.graalInfo.put(CommonConstants.CAP_EDITION, "ee");

        GraalEditionList list = new GraalEditionList(this, this, getLocalRegistry());
        list.setDefaultCatalogSpec(storage.graalInfo.get(CommonConstants.RELEASE_CATALOG_KEY));
        List<GraalEdition> eds = list.editions();
        assertFalse(eds.isEmpty());
        assertEquals(1, eds.size());

        GraalEdition ge = eds.get(0);
        assertSame(list.getDefaultEdition(), ge);
        assertEquals("Expected ee for vm-enterprise", "ee", ge.getId());
        assertEquals("Unqualified label should be picked up", "Enterprise Edition", ge.getDisplayName());

        List<SoftwareChannelSource> sources = ge.getSoftwareSources();
        assertEquals("Multiple software sources must be read", 3, sources.size());
        assertEquals("Expected decreasing priority", 2, sources.get(1).getPriority());
    }

    /**
     * Checks multi-property definition parsing from the release file. The default property may be
     * present, but will be ignored.
     * 
     * @throws Exception
     */
    @Test
    public void testParseRelease20MultiCatalogDefs() throws Exception {
        // this one contains multiple
        loadReleaseFile("release20ceWithEE.properties");
        storage.graalInfo.put(CommonConstants.CAP_EDITION, "ce");
        GraalEditionList list = new GraalEditionList(this, this, getLocalRegistry());
        list.setDefaultCatalogSpec(storage.graalInfo.get(CommonConstants.RELEASE_CATALOG_KEY));
        List<GraalEdition> eds = list.editions();
        assertFalse(eds.isEmpty());
        assertEquals(2, eds.size());

        GraalEdition ge = eds.get(0);
        assertSame(list.getDefaultEdition(), ge);
        assertEquals("Expected ce edition to be the default/installed", "ce", ge.getId());
        assertEquals("Label from graal caps should be capitalized", "CE", ge.getDisplayName());

        List<SoftwareChannelSource> sources = ge.getSoftwareSources();
        assertEquals("CE edition has single source", 1, sources.size());

        ge = eds.get(1);
        assertEquals("Enterprise Edition must be present", "ee", ge.getId());
        assertEquals("Enterprise label should be parsed out", "Enterprise Edition", ge.getDisplayName());
        sources = ge.getSoftwareSources();
        assertEquals("EE has more sources", 2, sources.size());
        assertEquals("Expected decreasing priority", 2, sources.get(1).getPriority());

        assertSame(ge, list.getEdition("ee"));
    }

    /**
     * Check function of the 'override' switch: the catalog software sources from Graal-20 release
     * file should be ignored.
     * 
     * @throws Exception
     */
    @Test
    public void testCheckUserUserOverride20() throws Exception {
        // this one contains multiple
        loadReleaseFile("release20ceWithEE.properties");
        storage.graalInfo.put(CommonConstants.CAP_EDITION, "ce");
        GraalEditionList list = new GraalEditionList(this, this, getLocalRegistry());
        list.setDefaultCatalogSpec(storage.graalInfo.get(CommonConstants.RELEASE_CATALOG_KEY));
        list.setOverrideCatalogSpec("http://somewhere/catalog.properties|http://somewhereElse/catalog.properties");

        List<GraalEdition> eds = list.editions();
        assertEquals(1, eds.size());
        GraalEdition ge = eds.get(0);
        assertEquals("Expected default edition", "ce", ge.getId());
        List<SoftwareChannelSource> sources = ge.getSoftwareSources();
        assertEquals("Expected 2 sources from override", 2, sources.size());
        assertTrue(sources.stream().allMatch(s -> s.getLocationURL().contains("somewhere")));
        assertEquals(2, sources.get(1).getPriority());
    }

    @Test
    public void testCheck21MultipleProperties() throws Exception {
        loadReleaseFile("release21ce.properties");
        storage.graalInfo.put(CommonConstants.CAP_EDITION, "ce");
        GraalEditionList list = new GraalEditionList(this, this, getLocalRegistry());
        list.setDefaultCatalogSpec(storage.graalInfo.get(CommonConstants.RELEASE_CATALOG_KEY));

        List<GraalEdition> eds = list.editions();
        assertEquals(1, eds.size());
        GraalEdition ge = eds.get(0);
        List<SoftwareChannelSource> sources = ge.getSoftwareSources();
        assertEquals("Expected default edition", "ce", ge.getId());
        assertEquals("Expected 2 sources from multiproperties", 2, sources.size());

    }

    @Test
    public void testCheck21MultipleEditions() throws Exception {
        loadReleaseFile("release21ceWithEE.properties");
        storage.graalInfo.put(CommonConstants.CAP_EDITION, "ce");
        GraalEditionList list = new GraalEditionList(this, this, getLocalRegistry());
        list.setDefaultCatalogSpec(storage.graalInfo.get(CommonConstants.RELEASE_CATALOG_KEY));

        List<GraalEdition> eds = list.editions();
        assertEquals(2, eds.size());
        assertSame(list.getDefaultEdition(), eds.get(0));
        assertSame(list.getDefaultEdition(), list.getEdition(""));

        GraalEdition ge = list.getEdition("ce");
        assertSame(list.getDefaultEdition(), ge);
        assertSame(list.getEdition(""), ge);
        assertFalse("CE".equalsIgnoreCase(ge.getDisplayName()));

        List<SoftwareChannelSource> sources = ge.getSoftwareSources();
        assertEquals(2, sources.size());
        assertNotNull(sources.get(0).getLabel());
        assertTrue(sources.get(0).getLabel().contains("CE 8"));
        assertNotNull(sources.get(1).getLabel());
        assertTrue(sources.get(1).getLabel().contains("CE 11"));

        // check lowercasing
        ge = list.getEdition("eE");
        sources = ge.getSoftwareSources();
        assertEquals(3, sources.size());
        assertFalse("EE".equalsIgnoreCase(ge.getDisplayName()));

        String u = sources.get(0).getLocationURL();
        assertTrue(u.startsWith("gds:"));
        assertTrue(sources.get(0).getLabel().contains("GDS"));

        u = sources.get(1).getLocationURL();
        assertTrue(u.contains("java8.properties"));
        assertTrue(sources.get(1).getLabel().contains("Github"));
        assertEquals("valueY", sources.get(1).getParameter("paramx"));

        u = sources.get(2).getLocationURL();
        assertTrue(u.contains("ee-extras"));
        assertTrue(sources.get(2).getLabel().contains("experimental"));
    }

    /**
     * Check function of the 'override' switch: the catalog software sources from release file
     * should be ignored. This test checks function for multiple-property definition, even in the
     * presence of single-property definition which should be ignored.
     * 
     * @throws Exception
     */
    @Test
    public void testCheckUserUserOverride21() throws Exception {
        loadReleaseFile("release21ce.properties");
        storage.graalInfo.put(CommonConstants.CAP_EDITION, "ce");
        checkOverrideCommon();
    }

    private void checkOverrideCommon() throws Exception {
        GraalEditionList list = new GraalEditionList(this, this, getLocalRegistry());
        list.setOverrideCatalogSpec("https://www.graalvm.org/component-catalog/graal-updater-component-catalog-java8x.properties");

        List<GraalEdition> eds = list.editions();
        assertEquals(1, eds.size());
        assertEquals(list.getDefaultEdition(), eds.get(0));

        GraalEdition ge = eds.get(0);
        assertEquals("ce", ge.getId());
        assertSame(ge, list.getEdition(null));

        List<SoftwareChannelSource> channels = ge.getSoftwareSources();
        assertEquals(1, channels.size());
        assertTrue(channels.get(0).getLocationURL().endsWith("java8x.properties"));
    }

    /**
     * Checks that explicit URL (commandline) will override multi-edition setup. This must set up
     * the default edition to the set of software channes from the explicit URL.
     * 
     * @throws Exception
     */
    @Test
    public void testOverrideOneOverMultipleEditions() throws Exception {
        loadReleaseFile("release21ceWithEE.properties");
        storage.graalInfo.put(CommonConstants.CAP_EDITION, "ce");
        checkOverrideCommon();
    }

    @Test
    public void testEmptyReleaseInitializesOK() throws Exception {
        loadReleaseFile("emptyRelease.properties");
        storage.graalInfo.put(CommonConstants.CAP_EDITION, "ee");
        GraalEditionList list = new GraalEditionList(this, this, getLocalRegistry());

        GraalEdition ge = list.getDefaultEdition();
        assertNotNull(ge);
        assertEquals("ee", ge.getId());
        assertEquals(0, ge.getSoftwareSources().size());
    }

    /**
     * Checks that exception is thrown when accessing undefined edition.
     */
    @Test
    public void testNonExistentEditionFails() throws Exception {
        loadReleaseFile("release21ceWithEE.properties");
        storage.graalInfo.put(CommonConstants.CAP_EDITION, "ce");
        GraalEditionList list = new GraalEditionList(this, this, getLocalRegistry());

        assertNotNull(list.getEdition("ee"));
        assertNotNull(list.getEdition("ce"));
        try {
            assertNull(list.getEdition("fake"));
            fail("Expected exception");
        } catch (FailedOperationException ex) {
            // expected
        }
    }
}
