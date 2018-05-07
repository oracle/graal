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
package org.graalvm.component.installer.persist;

import java.net.ConnectException;
import java.net.URL;
import java.nio.file.Path;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.MockURLConnection;
import org.graalvm.component.installer.commands.MockStorage;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.test.Handler;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class RemoteCatalogDownloaderTest extends NetworkTestBase {
    @Rule public ExpectedException exception = ExpectedException.none();

    @Rule public TemporaryFolder folder = new TemporaryFolder();
    Path targetPath;
    ComponentRegistry localRegistry;
    MockStorage storage;

    ComponentRegistry registry;

    @Before
    public void setUp() throws Exception {
        targetPath = folder.newFolder("inst").toPath();
        storage = new MockStorage();
        localRegistry = new ComponentRegistry(this, storage);
    }

    @Test
    public void testDownloadCatalogBadGraalVersion() throws Exception {
        URL clu = getClass().getResource("catalog");
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");
        Handler.bind(u.toString(),
                        clu);

        RemoteCatalogDownloader d = new RemoteCatalogDownloader(this, localRegistry, u);
        exception.expect(FailedOperationException.class);
        exception.expectMessage("REMOTE_UnsupportedGraalVersion");
        d.openCatalog();
    }

    @Test
    public void testDownloadCatalogCorrupted() throws Exception {
        URL clu = getClass().getResource("catalogCorrupted");
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");
        Handler.bind(u.toString(),
                        clu);

        RemoteCatalogDownloader d = new RemoteCatalogDownloader(this, localRegistry, u);
        exception.expect(FailedOperationException.class);
        exception.expectMessage("REMOTE_CorruptedCatalogFile");
        d.openCatalog();
    }

    private void loadRegistry() throws Exception {
        URL clu = getClass().getResource("catalog");
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");
        Handler.bind(u.toString(),
                        clu);
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "0.33-dev");
        RemoteCatalogDownloader d = new RemoteCatalogDownloader(this, localRegistry, u);
        registry = d.openCatalog();
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

        RemoteCatalogDownloader d = new RemoteCatalogDownloader(this, localRegistry, u);
        exception.expect(FailedOperationException.class);
        exception.expectMessage("REMOTE_CorruptedCatalogFile");
        d.openCatalog();
    }

    @Test
    public void testCannotConnectCatalog() throws Exception {
        URL clu = getClass().getResource("catalogCorrupted");
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");
        Handler.bind(u.toString(),
                        new MockURLConnection(clu.openConnection(), u, new ConnectException()));

        RemoteCatalogDownloader d = new RemoteCatalogDownloader(this, localRegistry, u);
        exception.expect(FailedOperationException.class);
        exception.expectMessage("REMOTE_ErrorDownloadCatalogProxy");
        d.openCatalog();
    }
}
