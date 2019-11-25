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
package org.graalvm.component.installer.persist;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.ComponentCatalog;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.SoftwareChannelSource;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.test.Handler;
import org.graalvm.component.installer.remote.RemoteCatalogDownloader;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class DirectoryCatalogProviderTest extends CommandTestBase {
    @Rule public ProxyResource proxyResource = new ProxyResource();

    @Test
    public void testLoadFromEmptyDirectory() throws Exception {
        Path nf = testFolder.newFolder().toPath();
        DirectoryCatalogProvider prov = new DirectoryCatalogProvider(nf, this);

        assertTrue(prov.listComponentIDs().isEmpty());
    }

    @Test
    public void testLoadFromNonDirectory() throws Exception {
        Path nf = testFolder.newFile().toPath();
        DirectoryCatalogProvider prov = new DirectoryCatalogProvider(nf, this);

        assertTrue(prov.listComponentIDs().isEmpty());
    }

    @Test
    public void testLoadComponentsJars() throws Exception {
        Path ruby033 = dataFile("data/truffleruby2.jar");
        Path ruby10 = dataFile("../remote/data/truffleruby2.jar");
        Path llvm = dataFile("data/llvm-toolchain.jar");

        Path nf = testFolder.newFolder().toPath();
        Files.copy(ruby033, nf.resolve(ruby033.getFileName()));
        Files.copy(ruby10, nf.resolve("truffleruby10.jar"));
        Files.copy(llvm, nf.resolve(llvm.getFileName()));

        DirectoryCatalogProvider prov = new DirectoryCatalogProvider(nf, this);
        Set<String> ids = prov.listComponentIDs();

        assertEquals(2, ids.size());
        assertTrue(ids.contains("org.graalvm.ruby"));
        assertTrue(ids.contains("org.graalvm.llvm-toolchain"));
    }

    static class E {
        Throwable ex;
        String file;
        String msg;
    }

    class FB extends FeedbackAdapter {
        List<E> errs = new ArrayList<>();

        @Override
        public void error(String key, Throwable t, Object... params) {
            E e = new E();
            e.ex = t;
            if (params != null && params.length > 1) {
                e.file = Objects.toString(params[0]);
                e.msg = Objects.toString(params[1]);
            }
            errs.add(e);
        }
    }

    /**
     * Checks that invalid/non-components/broken stuff is reported. When the user specifies
     * directory as catalog source, it's probably important to report invalid data.
     */
    @Test
    public void testReportBrokenComponents() throws Exception {
        Path testData = dataFile("dir1");
        DirectoryCatalogProvider prov = new DirectoryCatalogProvider(testData, this);
        FB fb = new FB();
        delegateFeedback(fb);

        Set<String> ids = prov.listComponentIDs();
        assertEquals(2, ids.size());
        assertEquals(3, fb.errs.size());
    }

    /**
     * Implied directory catalogs (to resolve dependencies) can scan directories with random
     * contents. broken or non-components should be silently ignored.
     */
    @Test
    public void testSuppressErrorComponents() throws Exception {
        Path testData = dataFile("dir1");
        DirectoryCatalogProvider prov = new DirectoryCatalogProvider(testData, this);
        prov.setReportErrors(false);
        FB fb = new FB();
        delegateFeedback(fb);

        Set<String> ids = prov.listComponentIDs();
        assertEquals(2, ids.size());
        assertTrue(fb.errs.isEmpty());
    }

    @Test
    public void testDifferentRequirementsFiltered() throws Exception {
        Path testData = dataFile("dir1");
        DirectoryCatalogProvider prov = new DirectoryCatalogProvider(testData, this);
        Set<String> ids = prov.listComponentIDs();
        assertEquals(2, ids.size());
        // typo is there...
        Collection<ComponentInfo> infos = prov.loadComponentMetadata("org.graavm.ruby");
        assertEquals(2, infos.size());

        CatalogContents contents = new CatalogContents(this, prov, getLocalRegistry());
        Version.Match m = getLocalRegistry().getGraalVersion().match(Version.Match.Type.INSTALLABLE);

        // check that the JDK11 component gets removed
        Collection<ComponentInfo> catInfos = contents.loadComponents("org.graavm.ruby", m, false);
        assertEquals(1, catInfos.size());

        ComponentInfo ci = catInfos.iterator().next();
        assertEquals(testData.resolve("ruby.jar").toUri().toURL(), ci.getRemoteURL());
    }

    @Test
    public void testSpecificJavaPresent() throws Exception {
        storage.graalInfo.put(CommonConstants.CAP_JAVA_VERSION, "11");
        Path testData = dataFile("dir1");
        DirectoryCatalogProvider prov = new DirectoryCatalogProvider(testData, this);
        Set<String> ids = prov.listComponentIDs();
        assertEquals(2, ids.size());
        // typo is there...
        Collection<ComponentInfo> infos = prov.loadComponentMetadata("org.graavm.ruby");
        assertEquals(2, infos.size());

        CatalogContents contents = new CatalogContents(this, prov, getLocalRegistry());
        Version.Match m = getLocalRegistry().getGraalVersion().match(Version.Match.Type.INSTALLABLE);

        // both Ruby should pass: ruby.jar has no jdk restriction
        Collection<ComponentInfo> catInfos = contents.loadComponents("org.graavm.ruby", m, false);
        assertEquals(2, catInfos.size());

        Set<URL> urls = new HashSet<>(Arrays.asList(
                        testData.resolve("ruby.jar").toUri().toURL(),
                        testData.resolve("ruby-11.jar").toUri().toURL()));
        Iterator<ComponentInfo> itC = catInfos.iterator();
        ComponentInfo ci = itC.next();
        assertTrue(urls.remove(ci.getRemoteURL()));
        ci = itC.next();
        assertTrue(urls.remove(ci.getRemoteURL()));
    }

    /**
     * Checks that the merging catalog initializes the directory provider correctly.
     * 
     * @throws Exception
     */
    @Test
    public void testNoErrorsWithLocalCatalogs() throws Exception {
        URL clu = getClass().getResource("data/catalog");
        URL u = new URL("test://graalvm.io/download/truffleruby.zip");
        Handler.bind(u.toString(),
                        clu);

        Path testData = dataFile("dir1");
        SoftwareChannelSource scs = new SoftwareChannelSource(testData.toUri().toURL().toString(), "local dir");
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "0.33-dev");

        FB fb = new FB();
        delegateFeedback(fb);

        RemoteCatalogDownloader d = new RemoteCatalogDownloader(this, this, u);
        d.addLocalChannelSource(scs);
        registry = openCatalog(d);

        // directory contents scanned / component found
        ComponentInfo info = registry.findComponent("org.graalvm.llvm-toolchain");
        assertNotNull(info);
        // catalog loaded / component found
        info = registry.findComponent("ruby");
        assertNotNull(info);

        // no errors
        assertTrue(fb.errs.isEmpty());
    }

    ComponentCatalog openCatalog(SoftwareChannel ch) throws IOException {
        return openCatalog(ch, getLocalRegistry().getGraalVersion());
    }

    ComponentCatalog openCatalog(SoftwareChannel ch, Version v) throws IOException {
        ComponentCatalog cc = new CatalogContents(this, ch.getStorage(), getLocalRegistry(), v);
        cc.getComponentIDs();
        return cc;
    }
}
