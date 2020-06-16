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
package org.graalvm.component.installer.commands;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.DownloadURLIterable;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.remote.CatalogIterable;
import org.graalvm.component.installer.remote.RemoteCatalogDownloader;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class InfoTest extends CommandTestBase {
    @Rule public final ProxyResource proxyResource = new ProxyResource();

    private Version initVersion(String s) throws IOException {
        Version v = Version.fromString(s);
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, v.toString());
        Path catalogPath = dataFile("../repo/catalog.properties");
        RemoteCatalogDownloader downloader = new RemoteCatalogDownloader(
                        this,
                        this,
                        catalogPath.toUri().toURL());

        registry = new CatalogContents(this, downloader.getStorage(), localRegistry);
        paramIterable = new CatalogIterable(this, this);
        return v;
    }

    @Test
    public void testInfoLocalComponent() throws Exception {
        Path p = dataFile("truffleruby2.jar");
        textParams.add(p.toString());
        p = dataFile("../repo/python/1.0.0.0");
        textParams.add(p.toString());

        InfoCommand cmd = new InfoCommand();
        cmd.init(this, this.withBundle(InfoCommand.class));
        cmd.collectComponents();

        List<ComponentInfo> comps = cmd.getComponents();
        assertEquals(2, comps.size());
        ComponentInfo ruby = comps.get(0);
        assertEquals("org.graalvm.ruby", ruby.getId());
        ruby.getVersion().toString();
        assertEquals("1.0", ruby.getVersion().originalString());

        ComponentInfo python = comps.get(1);
        assertEquals("org.graalvm.python", python.getId());
        assertEquals("1.0.0.0", python.getVersion().originalString());
    }

    @Test
    public void testInfoDownloadedComponent() throws Exception {
        Path p = dataFile("truffleruby2.jar");
        options.put(Commands.OPTION_URLS, "");
        textParams.add(p.toUri().toString());

        paramIterable = new DownloadURLIterable(this, this);
        InfoCommand cmd = new InfoCommand();
        cmd.init(this, this.withBundle(InfoCommand.class));
        cmd.collectComponents();

        List<ComponentInfo> comps = cmd.getComponents();
        assertEquals(1, comps.size());
        ComponentInfo ruby = comps.get(0);
        assertEquals("org.graalvm.ruby", ruby.getId());
        ruby.getVersion().toString();
        assertEquals("1.0", ruby.getVersion().originalString());
    }

    @Test
    public void testInfoCompatibleComponents() throws Exception {
        initVersion("1.0.0");

        textParams.add("ruby");
        textParams.add("python");

        InfoCommand cmd = new InfoCommand();
        cmd.init(this, this.withBundle(InfoCommand.class));
        cmd.collectComponents();

        List<ComponentInfo> comps = cmd.getComponents();
        assertEquals(2, comps.size());
    }

    @Test
    public void testIncompatibleComponentInfo() throws Exception {
        initVersion("1.0.0");

        textParams.add("r");

        InfoCommand cmd = new InfoCommand();
        cmd.init(this, this.withBundle(InfoCommand.class));
        cmd.collectComponents();

        List<ComponentInfo> comps = cmd.getComponents();
        assertEquals(1, comps.size());
    }

    @Test
    public void testSpecificVersionInfo() throws Exception {
        initVersion("1.0.0");

        textParams.add("ruby=1.0.1.1");
        InfoCommand cmd = new InfoCommand();
        cmd.init(this, this.withBundle(InfoCommand.class));
        cmd.collectComponents();

        List<ComponentInfo> comps = cmd.getComponents();
        assertEquals(1, comps.size());

    }
}
