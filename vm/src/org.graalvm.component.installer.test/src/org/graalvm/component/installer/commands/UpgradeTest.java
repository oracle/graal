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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.ComponentParam;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.UnknownVersionException;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.MetadataLoader;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.persist.test.Handler;
import org.graalvm.component.installer.remote.CatalogIterable;
import org.graalvm.component.installer.remote.RemoteCatalogDownloader;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class UpgradeTest extends CommandTestBase {
    private UpgradeProcess helper;
    private RemoteCatalogDownloader downloader;

    private Version initVersion(String s) throws IOException {
        return initVersion(s, "../repo/catalog.properties");
    }

    private Version initVersion(String s, String catalogResource) throws IOException {
        Version v = Version.fromString(s);
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, s);
        Path catalogPath = dataFile(catalogResource);
        downloader = new RemoteCatalogDownloader(
                        this,
                        this,
                        catalogPath.toUri().toURL());

        registry = new CatalogContents(this, downloader.getStorage(), localRegistry);
        paramIterable = new CatalogIterable(this, this);
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
        assertEquals("1.1.1-0.rc.1", installedGraalVMVersion.originalString());
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
     * Upgrade command is used to install a new component, but the core is most recent one and need
     * not to be upgraded. Component must go to the existing install.
     * 
     * @throws Exception
     */
    @Test
    public void testInstallNewWithoutCoreUpgrade() throws Exception {
        initVersion("1.0.1.0");
        textParams.add("ruby");

        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        helper = cmd.getProcess();

        ComponentInfo info = cmd.configureProcess();
        assertNotNull(info);
        // will not install core
        assertFalse(helper.installGraalCore(info));
        assertNull(helper.getTargetInfo());

        // found the current graalvm:
        assertEquals(getLocalRegistry().getGraalVersion(), info.getVersion());
        List<ComponentParam> toDownload = helper.allComponents();

        // no component (e.g. migration) was added, requested ruby is in there
        assertEquals(1, toDownload.size());
        assertEquals("org.graalvm.ruby", toDownload.get(0).getShortName());
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
        assertFalse(cmd.getProcess().prepareInstall(info));
    }

    @Test
    public void testRefuseNonAdminUpgrade() throws Exception {
        initVersion("1.0.0.0");
        storage.writableUser = "hero"; // NOI18N

        textParams.add("1.0.1");
        ComponentInfo ci = new ComponentInfo("org.graalvm.ruby", "Installed Ruby", "1.0.0.0");
        storage.installed.add(ci);
        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);

        exception.expect(FailedOperationException.class);
        exception.expectMessage("ADMIN");

        cmd.execute();
    }

    /**
     * Checks that the 'components can migrate' check succeed, if an existing component is specified
     * for upgrade.
     */
    @Test
    public void testUpgradeExistingComponent() throws Exception {
        initVersion("1.0.0.0");
        ComponentInfo ci = new ComponentInfo("org.graalvm.ruby", "Installed Ruby", "1.0.0.0");
        storage.installed.add(ci);
        textParams.add("ruby");

        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        helper = cmd.getProcess();

        ComponentInfo info = cmd.configureProcess();
        assertNotNull(info);
        assertEquals(Version.fromString("1.0.1"), info.getVersion());
    }

    /**
     * Fails on non-empty directory.
     */
    @Test
    public void testInstallIntoExistingNonempty() throws Exception {
        initVersion("1.0.0.0");
        ComponentInfo ci = new ComponentInfo("org.graalvm.ruby", "Installed Ruby", "1.0.0.0");
        storage.installed.add(ci);
        textParams.add("ruby");

        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        helper = cmd.getProcess();

        ComponentInfo info = cmd.configureProcess();
        Path p = getGraalHomePath().normalize();
        Path ndir = p.resolveSibling("graalvm-ce-1.0.1");
        Files.createDirectories(ndir);
        Files.write(ndir.resolve("some-content"), Arrays.asList("Fail"));

        exception.expect(FailedOperationException.class);
        exception.expectMessage("UPGRADE_TargetExistsNotEmpty");
        helper.prepareInstall(info);
    }

    /**
     * Fails on non-empty directory.
     */
    @Test
    public void testInstallIntoExistingRelease() throws Exception {
        initVersion("1.0.0.0");
        ComponentInfo ci = new ComponentInfo("org.graalvm.ruby", "Installed Ruby", "1.0.0.0");
        storage.installed.add(ci);
        textParams.add("ruby");

        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        helper = cmd.getProcess();

        ComponentInfo info = cmd.configureProcess();
        Path p = getGraalHomePath().normalize();
        Path ndir = p.resolveSibling("graalvm-ce-1.0.1");
        Files.createDirectories(ndir);
        Files.write(ndir.resolve("some-content"), Arrays.asList("Fail"));
        Path toCopy = dataFile("../persist/release_simple.properties");
        Files.copy(toCopy, ndir.resolve("release"));

        exception.expect(FailedOperationException.class);
        exception.expectMessage("UPGRADE_TargetExistsContainsGraalVM");
        helper.prepareInstall(info);
    }

    /**
     * Allows to install to an empty location.
     */
    @Test
    public void testInstallIntoExistingEmpty() throws Exception {
        initVersion("1.0.0.0");
        ComponentInfo ci = new ComponentInfo("org.graalvm.ruby", "Installed Ruby", "1.0.0.0");
        storage.installed.add(ci);
        textParams.add("ruby");

        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        helper = cmd.getProcess();

        ComponentInfo info = cmd.configureProcess();
        Path p = getGraalHomePath().normalize();
        Path ndir = p.resolveSibling("graalvm-ce-1.0.1");
        Files.createDirectories(ndir);

        assertTrue(helper.prepareInstall(info));
    }

    /**
     * Tests upgrade to version AT LEAST 1.0.1.
     */
    @Test
    public void testUpgradePythonMostRecent() throws Exception {
        initVersion("1.0.0.0");
        textParams.add("python+1.0.1");
        ComponentInfo ci = new ComponentInfo("org.graalvm.python", "Installed Python", "1.0.0.0");
        storage.installed.add(ci);
        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        assertEquals(0, cmd.execute());

        ComponentRegistry newReg = cmd.getProcess().getNewGraalRegistry();
        ComponentInfo python = newReg.findComponent("python");
        assertEquals("1.1.0.0", python.getVersion().toString());
    }

    /**
     * Tests upgrade to version EXACTLY 1.0.1.
     */
    @Test
    public void testUpgradeExact() throws Exception {
        initVersion("1.0.0.0");
        textParams.add("python=1.0.1");
        ComponentInfo ci = new ComponentInfo("org.graalvm.python", "Installed Python", "1.0.0.0");
        storage.installed.add(ci);
        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        assertEquals(0, cmd.execute());

        ComponentRegistry newReg = cmd.getProcess().getNewGraalRegistry();
        ComponentInfo python = newReg.findComponent("python");
        // note the difference, the user string does not contain trailing .0
        assertEquals("1.0.1", python.getVersion().displayString());
    }

    /**
     * Tests upgrade to a next version's RC.
     */
    @Test
    public void testUpgradeToNextRC() throws Exception {
        initVersion("1.0.0.0");
        textParams.add("1.1.1-rc");
        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        assertEquals(0, cmd.execute());

        ComponentRegistry newReg = cmd.getProcess().getNewGraalRegistry();
        assertEquals("1.1.1.0-0.rc.1", newReg.getGraalVersion().toString());
    }

    /**
     * Tests NO upgrade to RC when requesting a release.
     */
    @Test
    public void testNoUpgradeToRCInsteadOfRelease() throws Exception {
        initVersion("1.0.0.0");
        textParams.add("1.1.1");
        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        try {
            cmd.execute();
        } catch (UnknownVersionException ex) {
            assertNotNull(ex.getCandidate());
            assertTrue(ex.getCandidate().toString().contains("rc"));
        }
    }

    /**
     * Tests NO upgrade to RC when requesting a release.
     */
    @Test
    public void testUpgradeIfAllowsNewer() throws Exception {
        initVersion("1.0.0.0");
        textParams.add("+1.1.1");
        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        assertEquals(0, cmd.execute());
        ComponentRegistry newReg = cmd.getProcess().getNewGraalRegistry();
        assertEquals("1.1.1.0-0.rc.1", newReg.getGraalVersion().toString());
    }

    /**
     * Checks that upgrade will install graal of the specified version.
     */
    @Test
    public void testUpgradeFromDevToSpecificVersion() throws Exception {
        initVersion("1.0.0-dev");
        textParams.add("1.0.1");
        textParams.add("python");
        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        ComponentInfo graalInfo = cmd.configureProcess();
        assertNotNull(graalInfo);
        // check that GraalVM appropriate for 1.0.1 component is selected
        assertEquals("1.0.1.0", graalInfo.getVersion().toString());
        assertEquals(1, cmd.getProcess().addedComponents().size());
        ComponentParam p = cmd.getProcess().addedComponents().iterator().next();
        ComponentInfo ci = p.createMetaLoader().getComponentInfo();
        // check that component 1.0.1 will be installed
        assertEquals("1.0.1.0", ci.getVersion().toString());
    }

    /**
     * Checks that upgrade will install graal for the specific Component.
     */
    @Test
    public void testUpgradeFromDevToSpecificVersion2() throws Exception {
        initVersion("1.0.0-dev");
        textParams.add("python=1.0.1");
        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        ComponentInfo graalInfo = cmd.configureProcess();
        assertNotNull(graalInfo);
        // check that GraalVM appropriate for 1.0.1 component is selected
        assertEquals("1.0.1.0", graalInfo.getVersion().toString());
        assertEquals(1, cmd.getProcess().addedComponents().size());
        ComponentParam p = cmd.getProcess().addedComponents().iterator().next();
        ComponentInfo ci = p.createMetaLoader().getComponentInfo();
        // check that component 1.0.1 will be installed
        assertEquals("1.0.1.0", ci.getVersion().toString());
    }

    /**
     * Checks that upgrade will install graal for the specific Component.
     */
    @Test
    public void testUpgradeToSameVersion() throws Exception {
        initVersion("1.0.0-dev");
        textParams.add("1.0.0-dev");
        textParams.add("python");
        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        ComponentInfo graalInfo = cmd.configureProcess();
        assertNotNull(graalInfo);
        // check that GraalVM appropriate for 1.0.1 component is selected
        assertEquals("1.0.0-dev", graalInfo.getVersion().displayString());
        assertEquals(1, cmd.getProcess().addedComponents().size());
        ComponentParam p = cmd.getProcess().addedComponents().iterator().next();
        ComponentInfo ci = p.createMetaLoader().getComponentInfo();
        // check that component 1.0.1 will be installed
        assertEquals("1.0.0-dev", ci.getVersion().displayString());
    }

    static class InstallTrampoline extends InstallCommand {

        @Override
        protected void prepareInstallation() throws IOException {
            super.prepareInstallation();
        }

        @Override
        protected void executionInit() throws IOException {
            super.executionInit();
        }

    }

    CatalogFactory factory;

    @Override
    public CatalogFactory getCatalogFactory() {
        return factory != null ? factory : super.getCatalogFactory();
    }

    /**
     * Upgrade an installation with "ruby" to a newer one, where "ruby" has a dependency on an
     * additional component. The other component should be auto-installed.
     */
    @Test
    public void testUpgradeWithDependencies() throws Exception {
        initVersion("1.0.0.0", "../repo/catalog-19.3.properties");

        ComponentInfo ci = new ComponentInfo("org.graalvm.r", "Installed R", "1.0.0.0");
        storage.installed.add(ci);
        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);

        textParams.add("r");
        ComponentInfo graalInfo = cmd.configureProcess();
        assertNotNull(graalInfo);
        assertEquals(Version.fromString("19.3.0.0"), graalInfo.getVersion());

        boolean installed = cmd.getProcess().installGraalCore(graalInfo);
        assertTrue(installed);

        factory = (in, reg) -> {
            RemoteCatalogDownloader dnl = new RemoteCatalogDownloader(in, this, downloader.getOverrideCatalogSpec());
            // carry over the override spec
            return new CatalogContents(this, dnl.getStorage(), reg);
        };

        InstallTrampoline targetInstall = new InstallTrampoline();
        cmd.getProcess().configureInstallCommand(targetInstall);
        targetInstall.executionInit();
        targetInstall.prepareInstallation();

        assertTrue(targetInstall.getUnresolvedDependencies().isEmpty());
        List<ComponentParam> deps = targetInstall.getDependencies();
        assertEquals(1, deps.size());
        MetadataLoader ldr = deps.iterator().next().createFileLoader();
        assertEquals("org.graalvm.llvm-toolchain", ldr.getComponentInfo().getId());
    }

    @Rule public ProxyResource proxyResource = new ProxyResource();

    @After
    public void clearHandlerBindings() {
        Handler.clear();
    }

    /**
     * The target GraalVM installation may have configured the catalog URLs differently. When
     * installing components or dependencies to the target, the target's URLs / release file
     * settings should be respected.
     * 
     * @throws Exception
     */
    @Test
    public void testUpgradeRespectsTargetCatalogURLs() throws Exception {
        URL u = new URL("test://catalog-19.3.properties");
        Handler.bind(u.toString(), dataFile("../repo/catalog-19.3.properties").toUri().toURL());

        initVersion("1.0.0.0", "../repo/catalog-19.3.properties");

        ComponentInfo ci = new ComponentInfo("org.graalvm.r", "Installed R", "1.0.0.0");
        storage.installed.add(ci);
        UpgradeCommand cmd = new UpgradeCommand();
        cmd.init(this, this);
        ComponentInfo graalInfo = cmd.configureProcess();
        boolean installed = cmd.getProcess().installGraalCore(graalInfo);
        assertTrue(installed);
        factory = (in, reg) -> {
            // creating a downloader WITHOUT explicit catalog value - will be read from the
            // installation.
            RemoteCatalogDownloader dnl = new RemoteCatalogDownloader(in, this, (String) null);
            return new CatalogContents(this, dnl.getStorage(), reg);
        };
        InstallTrampoline targetInstall = new InstallTrampoline();
        cmd.getProcess().configureInstallCommand(targetInstall);
        targetInstall.executionInit();
        targetInstall.prepareInstallation();

        // verify the URL from the target installation was visited.
        assertTrue(Handler.isVisited(u));
    }
}
