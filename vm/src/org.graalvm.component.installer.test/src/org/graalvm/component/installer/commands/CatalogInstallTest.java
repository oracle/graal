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
package org.graalvm.component.installer.commands;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import org.graalvm.component.installer.remote.CatalogIterable;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.DependencyException;
import org.graalvm.component.installer.IncompatibleException;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.remote.RemoteCatalogDownloader;
import org.graalvm.component.installer.persist.test.Handler;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;

/**
 *
 * @author sdedic
 */
public class CatalogInstallTest extends CommandTestBase {
    private static final String TEST_CATALOG_URL = "test://release/catalog.properties";

    @Rule public TestName name = new TestName();
    @Rule public ExpectedException exception = ExpectedException.none();
    @Rule public ProxyResource proxyResource = new ProxyResource();

    protected InstallCommand inst;
    private RemoteCatalogDownloader downloader;

    private void setupVersion(String v) {
        String version = v == null ? "0.33" : v;
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, version);
    }

    private void setupCatalog(String rel) throws Exception {
        String relSpec;

        if (rel == null) {
            relSpec = "catalog-" + name + ".properties";
            if (getClass().getResource(relSpec) == null) {
                relSpec = "catalogInstallTest.properties";
            }
        } else {
            relSpec = rel;
        }
        URL u = getClass().getResource(relSpec);
        if (u == null) {
            u = getClass().getResource("catalogInstallTest.properties");
        }
        Handler.bind(TEST_CATALOG_URL, u);

        downloader = new RemoteCatalogDownloader(this, this, new URL(TEST_CATALOG_URL));
        this.registry = new CatalogContents(this, downloader.getStorage(), getLocalRegistry());
    }

    /**
     * Checks that mismatched catalog is rejected entirely.
     * 
     * @throws Exception
     */
    @Test
    public void testRejectMismatchingCatalog() throws Exception {
        setupVersion("1.0.0-rc1");

        URL rubyURL = new URL("test://release/graalvm-ruby.zip");
        Handler.bind(rubyURL.toString(), new URLConnection(rubyURL) {
            @Override
            public void connect() throws IOException {
                throw new UnsupportedOperationException("Should not be touched");
            }
        });

        exception.expect(IncompatibleException.class);
        exception.expectMessage("REMOTE_UnsupportedGraalVersion");

        setupCatalog(null);
        textParams.add("ruby");
        paramIterable = new CatalogIterable(this, this, getRegistry(), downloader);
        paramIterable.iterator().next();
    }

    /**
     * Checks that mismatched version is rejected based on catalog metadata, and the component URL
     * is not opened at all.
     * 
     * Because of versioning support, the "ruby" will not be even identifier as available, as it
     * requires an incompatible graalvm version.
     */
    @Test
    public void testRejectMetaDontDownloadPackage() throws Exception {
        setupVersion("0.33-dev");

        URL rubyURL = new URL("test://release/graalvm-ruby.zip");
        Handler.bind(rubyURL.toString(), new URLConnection(rubyURL) {
            @Override
            public void connect() throws IOException {
                throw new UnsupportedOperationException(
                                "Should not be touched");
            }
        });

        exception.expect(DependencyException.Mismatch.class);
        exception.expectMessage("VERIFY_ObsoleteGraalVM");

        setupCatalog(null);
        paramIterable = new CatalogIterable(this, this, registry, downloader);
        textParams.add("ruby");
        InstallCommand cmd = new InstallCommand();
        cmd.init(this,
                        withBundle(InstallCommand.class));
        cmd.execute();
    }

    @Test
    public void testCheckPostinstMessageLoaded() throws Exception {
        setupVersion("0.33");
        URL x = getClass().getResource("postinst2.jar");
        URL rubyURL = new URL("test://release/postinst2.jar");
        Handler.bind(rubyURL.toString(), x);

        setupCatalog(null);
        paramIterable = new CatalogIterable(this, this, getRegistry(), downloader);
        textParams.add("ruby");
        InstallCommand cmd = new InstallCommand();
        cmd.init(this, withBundle(InstallCommand.class));

        cmd.prepareInstallation();
        cmd.completeInstallers();

        assertFalse(cmd.realInstallers.isEmpty());
        for (Installer i : cmd.realInstallers.values()) {
            assertNotNull(i.getComponentInfo().getPostinstMessage());
        }
    }

    @Test
    public void testPostinstMessagePrinted() throws Exception {
        setupVersion("0.33");
        URL x = getClass().getResource("postinst2.jar");
        URL rubyURL = new URL("test://release/postinst2.jar");
        Handler.bind(rubyURL.toString(), x);

        setupCatalog(null);
        paramIterable = new CatalogIterable(this, this, getRegistry(), downloader);
        textParams.add("ruby");
        InstallCommand cmd = new InstallCommand();
        cmd.init(this, withBundle(InstallCommand.class));

        String[] formatted = new String[1];
        delegateFeedback(new FeedbackAdapter() {
            @Override
            public boolean verbatimOut(String aMsg, boolean beVerbose) {
                if (aMsg.contains("Ruby openssl")) { // NOI18N
                    formatted[0] = aMsg;
                }
                return super.verbatimOut(aMsg, beVerbose);
            }
        });
        cmd.execute();

        assertNotNull(formatted[0]);
    }
}
