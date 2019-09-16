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

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.ComponentCatalog;
import org.graalvm.component.installer.SoftwareChannelSource;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.remote.RemoteCatalogDownloader;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class DirectoryChannelFactoryTest extends CommandTestBase implements CommandInput.CatalogFactory {
    RemoteCatalogDownloader downloader;

    @Test
    public void testEmptyDownloaderProducesNothing() throws Exception {
        downloader = new RemoteCatalogDownloader(this, this, (URL) null);

        ComponentInfo info = getRegistry().findComponent("org.graalvm.ruby");
        assertNull(info);
    }

    @Test
    public void testLoadFromEmptyDirectory() throws Exception {
        Path nf = testFolder.newFolder().toPath();
        Path ruby033 = dataFile("data/truffleruby3.jar");
        Files.copy(ruby033, nf.resolve(ruby033.getFileName()));
        // truffleruby3 declares that version
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, "0.33-dev");

        downloader = new RemoteCatalogDownloader(this, this, (URL) null);
        SoftwareChannelSource scs = new SoftwareChannelSource(nf.toUri().toString());
        downloader.addLocalChannelSource(scs);

        ComponentInfo info = getRegistry().findComponent("ruby");
        assertNotNull(info);
    }

    @Override
    public ComponentCatalog createComponentCatalog(CommandInput input, ComponentRegistry targetGraalVM) {
        return new CatalogContents(this, downloader.getStorage(), getLocalRegistry());
    }

    @Override
    public CatalogFactory getCatalogFactory() {
        return this;
    }

}
