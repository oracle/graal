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
package org.graalvm.component.installer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.component.installer.CatalogIterable.RemoteComponentParam;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.MetadataLoader;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.persist.RemoteStorage;
import org.graalvm.component.installer.persist.test.Handler;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CatalogIterableTest extends CommandTestBase implements Supplier<ComponentRegistry> {
    @Rule public final ProxyResource proxyResource = new ProxyResource();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        if (param != null) {
            param.close();
        }
    }

    @Test
    public void testRemoteNames() throws Exception {
        initRemoteComponent("persist/data/truffleruby2.jar", "test://graalvm.io/download/truffleruby.zip", "testComponent", "test");

        assertEquals("test", param.getSpecification());
        assertEquals("testComponent", param.getDisplayName());
        assertFalse(param.isComplete());
        assertFalse(Handler.isVisited(url));
    }

    @Test
    public void testCreateMetaLoader() throws Exception {
        initRemoteComponent("persist/data/truffleruby3.jar", "test://graalvm.io/download/truffleruby.zip", "testComponent", "test");

        MetadataLoader ldr = param.createMetaLoader();
        ldr.setNoVerifySymlinks(false);
        ldr.loadPaths();
        ldr.loadPermissions();
        ldr.loadSymlinks();
        ComponentInfo meta = ldr.getComponentInfo();

        assertEquals("ruby", meta.getId());
        assertNull(meta.getInfoPath());
        assertNull(meta.getLicensePath());
        assertTrue(meta.getPaths().isEmpty());
        assertEquals("TruffleRuby 0.33-dev", meta.getName());
        assertEquals("0.33-dev", meta.getVersionString());

        assertFalse(Handler.isVisited(url));
    }

    @Test
    public void testCreateFileLoader() throws Exception {
        initRemoteComponent("persist/data/truffleruby3.jar", "test://graalvm.io/download/truffleruby.zip", "testComponent", "test");

        MetadataLoader ldr = param.createFileLoader();
        ldr.setNoVerifySymlinks(false);
        ldr.loadPaths();
        ldr.loadPermissions();
        ldr.loadSymlinks();
        ComponentInfo meta = ldr.getComponentInfo();

        assertEquals("ruby", meta.getId());
        assertNull(meta.getInfoPath());
        assertNotNull(meta.getLicensePath());
        assertFalse(meta.getPaths().isEmpty());
        assertEquals("TruffleRuby 0.33-dev", meta.getName());
        assertEquals("0.33-dev", meta.getVersionString());

        assertTrue(Handler.isVisited(url));
    }

    @Test
    public void testVerifyRemoteJars() throws Exception {
        initRemoteComponent("persist/data/truffleruby3.jar", "test://graalvm.io/download/truffleruby.zip", "testComponent", "test");
        info.setShaDigest(RemoteStorage.toHashBytes(null, "d3a45ea326b379cc3d543cc56130ee9bd395fd1c1d51a470e8c2c8af1129829c", this));

        try {
            exception.expect(IOException.class);
            exception.expectMessage("ERR_FileDigestError");
            param.createFileLoader();
        } finally {
            assertTrue(Handler.isVisited(url));
        }
    }

    void addRemoteComponent(String relative, String u, boolean addParam) throws IOException {
        initRemoteComponent(relative, u, null, null);
        storage.installed.add(info);
        if (addParam) {
            components.add(param);
        }
    }

    @Override
    public ComponentRegistry get() {
        return registry;
    }

    @Test
    public void testReadComponentMetadataNoNetwork() throws Exception {
        addRemoteComponent("persist/data/truffleruby3.jar", "test://graalvm.io/download/truffleruby.zip", false);
        textParams.add("ruby");
        CatalogIterable cit = new CatalogIterable(this, this, this);
        assertTrue(cit.iterator().hasNext());
        for (ComponentParam p : cit) {
            URL remoteU = p.createMetaLoader().getComponentInfo().getRemoteURL();
            assertEquals(url, remoteU);
        }
        assertFalse(Handler.isVisited(url));
    }

    @Test
    public void testUnknownComponentSpecified() throws Exception {
        exception.expect(FailedOperationException.class);
        exception.expectMessage("REMOTE_UnknownComponentId");
        addRemoteComponent("persist/data/truffleruby3.jar", "test://graalvm.io/download/truffleruby.zip", false);
        textParams.add("r");
        CatalogIterable cit = new CatalogIterable(this, this, this);
        assertTrue(cit.iterator().hasNext());
        cit.iterator().next();
    }

    @Test
    public void testMetaAccessesDirectURL() throws Exception {
        addRemoteComponent("persist/data/truffleruby3.jar", "test://graalvm.io/download/truffleruby.zip", false);
        rparam = new RemoteComponentParam(url, rparam.getDisplayName(), rparam.getSpecification(), this, false);
        components.add(param);

        URL remoteU = rparam.createMetaLoader().getComponentInfo().getRemoteURL();
        assertEquals(url, remoteU);
        assertTrue(Handler.isVisited(url));
        assertTrue(rparam.isComplete());
    }

    @Test
    public void testDirectURLAccessedJustOnce() throws Exception {
        addRemoteComponent("persist/data/truffleruby3.jar", "test://graalvm.io/download/truffleruby.zip", false);
        rparam = new RemoteComponentParam(url, rparam.getDisplayName(), rparam.getSpecification(), this, false);
        components.add(param);

        URL remoteU = rparam.createMetaLoader().getComponentInfo().getRemoteURL();
        assertEquals(url, remoteU);
        assertTrue(Handler.isVisited(url));
        assertTrue(rparam.isComplete());

        Handler.clearVisited();

        JarFile jf = rparam.getJarFile();
        assertNotNull(jf);
        assertFalse(Handler.isVisited(url));
    }

    @Test
    public void testDirectURLJarClosedAfterMeta() throws Exception {
        addRemoteComponent("persist/data/truffleruby3.jar", "test://graalvm.io/download/truffleruby.zip", false);
        rparam = new RemoteComponentParam(url, rparam.getDisplayName(), rparam.getSpecification(), this, false);
        components.add(param);

        URL remoteU = rparam.createMetaLoader().getComponentInfo().getRemoteURL();
        assertEquals(url, remoteU);
        JarFile jf = rparam.getJarFile();
        assertNotNull(jf.getEntry("META-INF"));

        rparam.close();

        exception.expect(IllegalStateException.class);
        jf.getEntry("META-INF");
    }

    @Test
    public void testDirectURLJarClosedAfterJar() throws Exception {
        addRemoteComponent("persist/data/truffleruby3.jar", "test://graalvm.io/download/truffleruby.zip", false);
        rparam = new RemoteComponentParam(url, rparam.getDisplayName(), rparam.getSpecification(), this, false);
        components.add(param);
        JarFile jf = rparam.getJarFile();
        assertNotNull(jf.getEntry("META-INF"));
        rparam.close();
        exception.expect(IllegalStateException.class);
        jf.getEntry("META-INF");
    }

    @Test
    public void testURLDoesNotExist() throws Exception {
        addRemoteComponent("persist/data/truffleruby3.jar", "test://graalvm.io/download/truffleruby.zip", false);
        textParams.add("ruby");
        Handler.bind(url.toString(), new URLConnection(url) {
            @Override
            public InputStream getInputStream() throws IOException {
                connect();
                return clu.openStream();
            }

            @Override
            public String getHeaderField(int n) {
                try {
                    connect();
                } catch (IOException ex) {
                    Logger.getLogger(CatalogIterableTest.class.getName()).log(Level.SEVERE, null, ex);
                }
                return super.getHeaderField(n);
            }

            @Override
            public Map<String, List<String>> getHeaderFields() {
                try {
                    connect();
                } catch (IOException ex) {
                    Logger.getLogger(CatalogIterableTest.class.getName()).log(Level.SEVERE, null, ex);
                }
                return super.getHeaderFields();
            }

            @Override
            public void connect() throws IOException {
                throw new FileNotFoundException();
            }
        });

        CatalogIterable cit = new CatalogIterable(this, this, this);
        ComponentParam rubyComp = cit.iterator().next();

        exception.expect(FailedOperationException.class);
        exception.expectMessage("REMOTE_ErrorDownloadingNotExist");

        rubyComp.createFileLoader().getComponentInfo();
    }
}
