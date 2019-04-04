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
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.remote.CatalogIterable;
import org.graalvm.component.installer.remote.RemoteCatalogDownloader;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class UpgradeTest extends CommandTestBase {
    private UpgradeProcess helper;

    private Version initVersion(String s) throws IOException {
        Version v = Version.fromString(s);
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, s);
        Path catalogPath = dataFile("../repo/catalog.properties");
        RemoteCatalogDownloader downloader = new RemoteCatalogDownloader(
                        this,
                        this,
                        catalogPath.toUri().toURL());

        registry = new CatalogContents(this, downloader.getStorage(), localRegistry);
        paramIterable = new CatalogIterable(this, this, getRegistry(), downloader);
        helper = new UpgradeProcess(this, this, registry);
        return v;
    }

    /**
     * Checks situation when no upgrade is available.
     */
    @Test
    public void testNoUpgrade() throws Exception {
        initVersion("1.1.1-0.rc.1");
        ComponentInfo info = helper.findGraalVersion(localRegistry.getGraalVersion().match(Version.Match.Type.INSTALLABLE));
        assertNotNull(info);
        assertEquals(localRegistry.getGraalVersion(), info.getVersion());
        assertFalse(helper.prepareInstall(info));
    }

    /**
     * Tests "gu upgrade" commandline on a most recent version.
     */
    @Test
    public void testNoUpgradeCommand() throws Exception {
        initVersion("1.1.1-0.rc.1");

        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        // no upgrade performed.
        assertEquals(1, cmd.execute());

        assertFalse(Files.list(getGraalHomePath().getParent()).anyMatch((fn) -> fn.getFileName().toString().contains("rc.1")));
    }

    @Test
    public void testRefuseDowngradeComponentsFail() throws Exception {
        initVersion("1.1.0.1");
        ComponentInfo info = helper.findGraalVersion(
                        Version.fromString("1.0.0.0").match(Version.Match.Type.EXACT));
        assertNull(info);
    }

    @Test
    public void testRefuseDowngradeFromCommandline() throws Exception {
        exception.expect(FailedOperationException.class);
        exception.expectMessage("UPGRADE_CannotDowngrade");

        initVersion("1.1.0.1");
        textParams.add("1.0.0.0");
        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        cmd.execute();
    }

    @Test
    public void testUpgradeToCompatibleVersion() throws Exception {
        initVersion("1.0.0.0");
        textParams.add("1.0.1");
        ComponentInfo ci = new ComponentInfo("org.graalvm.ruby", "Installed Ruby", "1.0.0.0");
        storage.installed.add(ci);
        helper.resetExistingComponents();
        ComponentInfo info = helper.findGraalVersion(
                        Version.fromString("1.0.1").match(Version.Match.Type.COMPATIBLE));
        assertNotNull(info);
        assertEquals("1.0.1.0", info.getVersion().toString());
    }

    /**
     * Tests "gu upgrade 1.0.1" on 1.0.1 installation. 1.0.1 core should be installed with ruby
     * 1.0.1.1
     */
    @Test
    public void testUpgradeToCompatibleVersionCommandline() throws Exception {
        initVersion("1.0.0.0");
        textParams.add("1.0.1");
        ComponentInfo ci = new ComponentInfo("org.graalvm.ruby", "Installed Ruby", "1.0.0.0");
        storage.installed.add(ci);
        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        assertEquals(0, cmd.execute());

        ComponentRegistry newReg = cmd.getProcess().getNewGraalRegistry();
        ComponentInfo ruby = newReg.findComponent("ruby");
        assertEquals("1.0.1.1", ruby.getVersion().toString());
    }

    @Test
    public void testRefuseUpgradeUnsatisfiedComponent() throws Exception {
        exception.expect(FailedOperationException.class);
        exception.expectMessage("UPGRADE_ComponentsCannotMigrate");

        initVersion("1.0.0.0");
        ComponentInfo ci = new ComponentInfo("org.graalvm.ruby", "Installed Ruby", "1.0.0.0");
        storage.installed.add(ci);
        helper.resetExistingComponents();
        ComponentInfo info = helper.findGraalVersion(
                        Version.fromString("1.1.0").match(Version.Match.Type.COMPATIBLE));
        assertNotNull(info);
    }

    /**
     * Checks "gu upgrade 1.1.0". Should fail because ruby is not available in 1.1.x
     */
    @Test
    public void testRefuseUnsatisfiedCommandline() throws Exception {
        initVersion("1.0.0.0");
        ComponentInfo ci = new ComponentInfo("org.graalvm.ruby", "Installed Ruby", "1.0.0.0");
        textParams.add("1.1.0");
        storage.installed.add(ci);

        exception.expect(FailedOperationException.class);
        exception.expectMessage("UPGRADE_ComponentsCannotMigrate");
        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        cmd.execute();
    }

    /**
     * Checks "gu upgrade". Should select 1.0.1.0 with ruby 1.0.1.1 since in newer Graals Ruby is
     * not available
     */
    @Test
    public void testUpgradeToNewestAvailable() throws Exception {
        initVersion("1.0.0.0");
        ComponentInfo ci = new ComponentInfo("org.graalvm.ruby", "Installed Ruby", "1.0.0.0");
        storage.installed.add(ci);

        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        cmd.execute();

        Version installedGraalVMVersion = cmd.getProcess().getNewGraalRegistry().getGraalVersion();
        Version installedRubyVersion = cmd.getProcess().getNewGraalRegistry().findComponent("ruby").getVersion();

        assertEquals("1.0.1.0", installedGraalVMVersion.toString());
        assertEquals("1.0.1.1", installedRubyVersion.toString());
    }

    /**
     * Ignores unsatisfied component dependency and install the most recent GraalVM version.
     */
    @Test
    public void testIgnoreUnsatisfiedComponent() throws Exception {
        initVersion("1.0.0.0");
        ComponentInfo ci = new ComponentInfo("org.graalvm.ruby", "Installed Ruby", "1.0.0.0");
        storage.installed.add(ci);
        helper.resetExistingComponents();
        helper.setAllowMissing(true);
        ComponentInfo info = helper.findGraalVersion(
                        Version.fromString("1.1.0").match(Version.Match.Type.COMPATIBLE));
        assertNotNull(info);
        assertEquals("1.1.0.1", info.getVersion().toString());
    }

    /**
     * Checks "gu upgrade". Should select 1.0.1.0 with ruby 1.0.1.1 since in newer Graals Ruby is
     * not available
     */
    @Test
    public void testIgnoreUnsatisfiedComponentCommandline() throws Exception {
        initVersion("1.0.0.0");
        ComponentInfo ci = new ComponentInfo("org.graalvm.ruby", "Installed Ruby", "1.0.0.0");
        storage.installed.add(ci);
        options.put(Commands.OPTION_IGNORE_MISSING_COMPONENTS, "");

        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        cmd.execute();

        Version installedGraalVMVersion = cmd.getProcess().getNewGraalRegistry().getGraalVersion();
        assertEquals("1.1.1-0.rc.1", installedGraalVMVersion.toString());
        assertNull("Ruby should not be migrated", cmd.getProcess().getNewGraalRegistry().findComponent("ruby"));
    }

    /**
     * Checks installation without core , even though the user has specified the version. Simulates
     * "gu install ruby" on 1.0.1.0 installation which ought to install 1.0.1.1
     */
    @Test
    public void testInstallWithoutCoreUpgrade() throws Exception {
        initVersion("1.0.1.0");
        ComponentInfo ci = new ComponentInfo("org.graalvm.ruby", "Installed Ruby", "1.0.1.0");
        storage.installed.add(ci);
        helper.resetExistingComponents();

        ComponentInfo info = helper.findGraalVersion(
                        Version.fromString("1.0.1").match(Version.Match.Type.COMPATIBLE));
        assertNotNull(info);
        boolean inst = helper.installGraalCore(info);
        assertFalse(inst);
    }

    /**
     * Checks that "gu update r" will only update to a minor update, not to the next major.
     * 
     * @throws Exception
     */
    @Test
    public void testInstallMinorUpdate() throws Exception {
        initVersion("1.0.0.0");
        ComponentInfo ci = new ComponentInfo("org.graalvm.ruby", "Installed Ruby", "1.0.0.0");
        storage.installed.add(ci);

        UpgradeCommand cmd = new UpgradeCommand(false);
        cmd.init(this, this);
        ComponentInfo info = cmd.configureProcess();
        assertNull(info);
    }
}
