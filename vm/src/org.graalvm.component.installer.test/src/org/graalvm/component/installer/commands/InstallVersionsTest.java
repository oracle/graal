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
import java.util.ArrayList;
import java.util.List;
import org.graalvm.component.installer.Archive;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.ComponentParam;
import org.graalvm.component.installer.DependencyException;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.MetadataLoader;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.remote.CatalogIterable;
import org.graalvm.component.installer.remote.RemoteCatalogDownloader;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class InstallVersionsTest extends CommandTestBase {
    @Rule public final ProxyResource proxyResource = new ProxyResource();

    private InstallCommand cmd;

    private List<ComponentInfo> installInfos = new ArrayList<>();
    private List<ComponentParam> installParams = new ArrayList<>();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        cmd = new InstallCommand() {
            @Override
            Installer createInstaller(ComponentParam p, MetadataLoader ldr) throws IOException {
                installInfos.add(ldr.getComponentInfo());
                installParams.add(p);
                Installer override = overrideCreateInstaller(p, ldr);
                if (override == null) {
                    return super.createInstaller(p, ldr);
                } else {
                    configureInstaller(override);
                    return override;
                }
            }
        };
        cmd.init(this, withBundle(InstallCommand.class));
    }

    @SuppressWarnings("unused")
    protected Installer overrideCreateInstaller(ComponentParam p, MetadataLoader ldr) throws IOException {
        ComponentInfo partialInfo;
        partialInfo = ldr.getComponentInfo();
        ldr.loadPaths();
        Archive a = null;
        Installer inst = new Installer(this, getFileOperations(), partialInfo, getLocalRegistry(),
                        getRegistry(), a);
        inst.setPermissions(ldr.loadPermissions());
        inst.setSymlinks(ldr.loadSymlinks());
        return inst;

    }

    private Version initVersion(String s) throws IOException {
        Version v = Version.fromString(s);
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, s);
        Path catalogPath = dataFile("../repo/catalog.properties");
        RemoteCatalogDownloader downloader = new RemoteCatalogDownloader(
                        this,
                        this,
                        catalogPath.toUri().toURL());

        registry = new CatalogContents(this, downloader.getStorage(), localRegistry);
        paramIterable = new CatalogIterable(this, this);
        return v;
    }

    /**
     * Installs a missing component from the same distribution.
     */
    @Test
    public void testMissingComponent() throws Exception {
        initVersion("1.0.1.0");

        textParams.add("python");
        cmd.prepareInstallation();
        assertEquals(1, installInfos.size());
        ComponentInfo ci = installInfos.get(0);
        assertEquals("1.0.1.0", ci.getVersion().toString());
    }

    /**
     * Installs a new component, but updated one, from a newer distribution.
     */
    @Test
    public void testInstallNewUpdatedComponent() throws Exception {
        initVersion("1.0.1.0");

        textParams.add("ruby");
        cmd.prepareInstallation();
        assertEquals(1, installInfos.size());
        ComponentInfo ci = installInfos.get(0);
        assertEquals("1.0.1.1", ci.getVersion().toString());
    }

    /**
     * Refuses to upgrade the core distribution.
     */
    @Test
    public void testRefuseCoreUpgrade() throws Exception {
        initVersion("1.0.0.0");

        exception.expect(FailedOperationException.class);
        exception.expectMessage("REMOTE_UpgradeGraalVMCore");
        textParams.add("r");
        cmd.prepareInstallation();
    }

    @Test
    public void testUnknownComponent() throws Exception {
        initVersion("1.0.0.0");

        exception.expect(FailedOperationException.class);
        exception.expectMessage("REMOTE_UnknownComponentId");
        textParams.add("x");
        cmd.prepareInstallation();
    }

    @Test
    public void testRefusedDowngrade() throws Exception {
        initVersion("1.1.0.0");

        exception.expect(FailedOperationException.class);
        exception.expectMessage("REMOTE_UnknownComponentId");
        textParams.add("ruby");
        cmd.prepareInstallation();
    }

    @Test
    public void testDowngradeToSpecificVersion() throws Exception {
        initVersion("1.1.0.0");

        exception.expect(FailedOperationException.class);
        // catalogs do not even load obsolete versions
        exception.expectMessage("REMOTE_UnknownComponentId");
        textParams.add("ruby=1.0.0.0");
        cmd.prepareInstallation();
    }

    @Test
    public void testRefuseExplicitUpgrade() throws Exception {
        initVersion("1.0.1.0");

        exception.expect(DependencyException.Mismatch.class);
        exception.expectMessage("VERIFY_UpdateGraalVM");
        textParams.add("python=1.1.0.0");
        cmd.prepareInstallation();
    }

    @Test
    public void testFailOneOfComponents() throws Exception {
        initVersion("1.0.1.0");
        textParams.add("ruby");
        textParams.add("python=1.1.0.0");
        exception.expect(DependencyException.Mismatch.class);
        exception.expectMessage("VERIFY_UpdateGraalVM");
        cmd.prepareInstallation();
    }

}
