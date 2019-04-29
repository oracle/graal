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
import java.net.ConnectException;
import java.net.URL;
import java.util.Collection;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.ComponentCollection;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.IncompatibleException;
import org.graalvm.component.installer.MockURLConnection;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.NetworkTestBase;
import org.graalvm.component.installer.persist.test.Handler;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class RemoteCatalogDownloaderTest extends NetworkTestBase {

    ComponentCollection openCatalog(SoftwareChannel ch) throws IOException {
        return openCatalog(ch, getLocalRegistry().getGraalVersion());
    }

    ComponentCollection openCatalog(SoftwareChannel ch, Version v) throws IOException {
        ComponentCollection cc = new CatalogContents(this, ch.getStorage(), getLocalRegistry(), v);
        cc.getComponentIDs();
        return cc;
    }

    @Test
    public void testDownloadCatalogBadGraalVersion() throws Exception {
        URL clu = getClass().getResource("catalog");
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");
        Handler.bind(u.toString(),
                        clu);

        RemoteCatalogDownloader d = new RemoteCatalogDownloader(this, this, u);
        exception.expect(IncompatibleException.class);
        exception.expectMessage("REMOTE_UnsupportedGraalVersion");
        openCatalog(d);
    }

    @Test
    public void testDownloadCatalogCorrupted() throws Exception {
        URL clu = getClass().getResource("catalogCorrupted");
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");
        Handler.bind(u.toString(),
                        clu);

        RemoteCatalogDownloader d = new RemoteCatalogDownloader(this, this, u);
        exception.expect(FailedOperationException.class);
        exception.expectMessage("REMOTE_CorruptedCatalogFile");
        openCatalog(d);
    }

    private void loadRegistry() throws Exception {
        URL clu = getClass().getResource("catalog");
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");
        Handler.bind(u.toString(),
                        clu);
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "0.33-dev");
        RemoteCatalogDownloader d = new RemoteCatalogDownloader(this, this, u);
        registry = openCatalog(d);
    }

    @Test
    public void testDownloadCatalogGood() throws Exception {
        loadRegistry();
        assertNotNull(registry);
    }

    @Test
    public void testRemoteComponents() throws Exception {
        loadRegistry();
        assertEquals(2, registry.getComponentIDs().size());

        assertNotNull(registry.findComponent("r"));
        assertNotNull(registry.findComponent("ruby"));
    }

    @Test
    public void testDownloadCorruptedCatalog() throws Exception {
        URL clu = getClass().getResource("catalogCorrupted");
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");
        Handler.bind(u.toString(),
                        clu);

        RemoteCatalogDownloader d = new RemoteCatalogDownloader(this, this, u);
        exception.expect(FailedOperationException.class);
        exception.expectMessage("REMOTE_CorruptedCatalogFile");
        openCatalog(d);
    }

    @Test
    public void testCannotConnectCatalog() throws Exception {
        URL clu = getClass().getResource("catalogCorrupted");
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");
        Handler.bind(u.toString(),
                        new MockURLConnection(clu.openConnection(), u, new ConnectException()));

        RemoteCatalogDownloader d = new RemoteCatalogDownloader(this, this, u);
        exception.expect(FailedOperationException.class);
        exception.expectMessage("REMOTE_ErrorDownloadCatalogProxy");
        openCatalog(d);
    }

    RemoteCatalogDownloader rcd;

    private void setupJoinedCatalog(String firstPart) throws IOException {
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "1.0.0.0");
        URL u1 = new URL("test://graalvm.io/catalog1");
        URL u2 = new URL("test://graalvm.io/catalog2");

        URL clu1 = getClass().getResource(firstPart);
        URL clu2 = getClass().getResource("catalogMultiPart2");

        Handler.bind(u1.toString(), clu1);
        Handler.bind(u2.toString(), clu2);

        String list = String.join("|", u1.toString(), u2.toString());

        rcd = new RemoteCatalogDownloader(this, this, list);
    }

    private static ComponentInfo findComponent(ComponentCollection col, String id) {
        Collection<ComponentInfo> infos = col.loadComponents(id, Version.NO_VERSION.match(Version.Match.Type.GREATER), false);
        return infos == null || infos.isEmpty() ? null : infos.iterator().next();
    }

    /**
     * Checks that if a single catalog does not correspond to graalvm version, other catalogs will
     * be read.
     * 
     * @throws Exception
     */
    @Test
    public void testSingleNonMatchingCatalogIgnored() throws Exception {
        setupJoinedCatalog("catalogMultiPart1");
        ComponentCollection col = openCatalog(rcd);
        assertNotNull(findComponent(col, "r"));
        assertNotNull(findComponent(col, "ruby"));
        assertNull(findComponent(col, "python"));
    }

    /**
     * Checks that multiple catalogs are merged together.
     */
    @Test
    public void testMultipleCatalogsJoined() throws Exception {
        setupJoinedCatalog("catalogMultiPart1Mergeable");
        ComponentCollection col = openCatalog(rcd);
        assertNotNull(findComponent(col, "r"));
        assertNotNull(findComponent(col, "ruby"));
        assertNotNull(findComponent(col, "python"));
    }
}
