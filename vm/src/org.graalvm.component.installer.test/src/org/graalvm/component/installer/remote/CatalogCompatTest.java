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
package org.graalvm.component.installer.remote;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.ComponentCatalog;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.persist.test.Handler;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class CatalogCompatTest extends CommandTestBase {
    @Rule public ProxyResource proxyResource = new ProxyResource();

    ComponentCatalog openCatalog(SoftwareChannel ch) throws IOException {
        return openCatalog(ch, getLocalRegistry().getGraalVersion());
    }

    ComponentCatalog openCatalog(SoftwareChannel ch, Version v) throws IOException {
        ComponentCatalog cc = new CatalogContents(this, ch.getStorage(), getLocalRegistry(), v);
        cc.getComponentIDs();
        return cc;
    }

    void setupCatalogFormat1(String res) throws Exception {
        URL clu = getClass().getResource(res);
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");
        Handler.bind(u.toString(),
                        clu);
        RemoteCatalogDownloader d = new RemoteCatalogDownloader(this, this, u);
        registry = openCatalog(d);
    }

    /**
     * Checks that the pre 1.0 catalog format is still readable.
     * 
     * @throws Exception
     */
    @Test
    public void testOldFormatReadable() throws Exception {
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "1.0.0");
        setupCatalogFormat1("catalogFormat1.properties");
        assertNotNull(registry.findComponent("ruby"));
        assertNotNull(registry.findComponent("python"));
        assertNotNull(registry.findComponent("r"));
    }

    /**
     * Checks that previous versions (RCs) are ignored with the old format.
     * 
     * @throws Exception
     */
    @Test
    public void testOldFormatIgnoresPrevVersionsAvailable() throws Exception {
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "1.0.0");
        setupCatalogFormat1("catalogFormat1.properties");

        // interprets user input for 'available'
        Version gv = getLocalRegistry().getGraalVersion();
        Version.Match selector = gv.match(Version.Match.Type.INSTALLABLE);

        List<ComponentInfo> infos;

        // check that versions 1.0.0-rcX are ignored for version 1.0.0
        infos = new ArrayList<>(registry.loadComponents("ruby", selector, verbose));
        assertEquals(1, infos.size());
        infos = new ArrayList<>(registry.loadComponents("python", selector, verbose));
        assertEquals(1, infos.size());
        infos = new ArrayList<>(registry.loadComponents("r", selector, verbose));
        assertEquals(1, infos.size());
    }

    /**
     * Checks that previous versions (RCs) are ignored with the old format.
     * 
     * @throws Exception
     */
    @Test
    public void testOldFormatIgnoresPrevVersionsMostRecent() throws Exception {
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "1.0.0");
        setupCatalogFormat1("catalogFormat1.properties");

        // copied from CatalogIterable, this is what interprets user input for install
        Version gv = getLocalRegistry().getGraalVersion();
        Version.Match selector = gv.match(Version.Match.Type.MOSTRECENT);

        List<ComponentInfo> infos;

        // check that versions 1.0.0-rcX are ignored for version 1.0.0
        infos = new ArrayList<>(registry.loadComponents("ruby", selector, verbose));
        assertEquals(1, infos.size());
        infos = new ArrayList<>(registry.loadComponents("python", selector, verbose));
        assertEquals(1, infos.size());
        infos = new ArrayList<>(registry.loadComponents("r", selector, verbose));
        assertEquals(1, infos.size());
    }

    /**
     * Checks that future versions in OLD format are not accepted.
     */
    @Test
    public void testOldFormatIgnoresFutureVersionsAvailable() throws Exception {
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "1.0.0");
        setupCatalogFormat1("catalogFormat2.properties");

        // this is what interprets user input for 'available'
        Version gv = getLocalRegistry().getGraalVersion();
        Version.Match selector = gv.match(Version.Match.Type.INSTALLABLE);

        List<ComponentInfo> infos;

        // check that versions 1.0.0-rcX are ignored for version 1.0.0
        infos = new ArrayList<>(registry.loadComponents("ruby", selector, verbose));
        assertEquals(1, infos.size());
        infos = new ArrayList<>(registry.loadComponents("python", selector, verbose));
        assertEquals(1, infos.size());
        infos = new ArrayList<>(registry.loadComponents("r", selector, verbose));
        assertEquals(1, infos.size());
    }

    /**
     * Checks that future versions in OLD format are not accepted.
     */
    @Test
    public void testOldFormatIgnoresFutureVersionsInstall() throws Exception {
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "1.0.0");
        setupCatalogFormat1("catalogFormat2.properties");

        // copied from CatalogIterable, this is what interprets user input for install
        Version gv = getLocalRegistry().getGraalVersion();
        Version.Match selector = gv.match(Version.Match.Type.MOSTRECENT);

        List<ComponentInfo> infos;

        // check that versions 1.0.0-rcX are ignored for version 1.0.0
        infos = new ArrayList<>(registry.loadComponents("ruby", selector, verbose));
        assertEquals(1, infos.size());
        infos = new ArrayList<>(registry.loadComponents("python", selector, verbose));
        assertEquals(1, infos.size());
        infos = new ArrayList<>(registry.loadComponents("r", selector, verbose));
        assertEquals(1, infos.size());
    }

    /**
     * Checks that if a catalog mixes in new-format entries, they're read and processed for new
     * versions, too.
     * 
     * @throws Exception
     */
    @Test
    public void testMixedFormatAllowsFutureVersions() throws Exception {
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "1.0.0");
        setupCatalogFormat1("catalogFormatMix.properties");

        List<ComponentInfo> infos;

        Version gv = getLocalRegistry().getGraalVersion();
        Version.Match selector = gv.match(Version.Match.Type.INSTALLABLE);
        // check that versions 1.0.0-rcX are ignored for version 1.0.0
        infos = new ArrayList<>(registry.loadComponents("ruby", selector, verbose));

        assertNotNull(infos);
        assertEquals(3, infos.size());

        Collections.sort(infos, ComponentInfo.versionComparator());

        ComponentInfo one = infos.get(0);
        ComponentInfo two = infos.get(1);
        ComponentInfo three = infos.get(2);
        assertEquals("1.0.0", one.getVersionString());
        assertEquals("1.0.1.0", two.getVersionString());
        assertEquals("1.0.2.0-1", three.getVersionString());
    }

    /**
     * Checks that install will attempt to install the most recent stuff for the current release.
     * Will add the same-release component to existing instalation
     */
    @Test
    public void testMixedFormatInstallSameRelease() throws Exception {
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "1.0.0");
        setupCatalogFormat1("catalogFormatMix.properties");

        List<ComponentInfo> infos;

        Version gv = getLocalRegistry().getGraalVersion();
        Version.Match selector = gv.match(Version.Match.Type.MOSTRECENT);
        // check that versions 1.0.0-rcX are ignored for version 1.0.0
        infos = new ArrayList<>(registry.loadComponents("ruby", selector, verbose));

        assertNotNull(infos);
        assertEquals(1, infos.size());
        ComponentInfo one = infos.get(0);
        assertEquals("1.0.0", one.getVersionString());
    }

    /**
     * Checks that install will attempt to install the most recent stuff with mixed catalog. Will
     * upgrade the installation
     */
    @Test
    public void testMixedFormatInstallUpgrades() throws Exception {
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "1.0.0");
        setupCatalogFormat1("catalogFormatMix.properties");

        List<ComponentInfo> infos;

        Version gv = getLocalRegistry().getGraalVersion();
        registry.setAllowDistUpdate(true);
        Version.Match selector = gv.match(Version.Match.Type.MOSTRECENT);
        // check that versions 1.0.0-rcX are ignored for version 1.0.0
        infos = new ArrayList<>(registry.loadComponents("ruby", selector, verbose));

        assertNotNull(infos);
        assertEquals(1, infos.size());
        ComponentInfo one = infos.get(0);
        assertEquals("1.0.2.0-1", one.getVersionString());
    }
}
