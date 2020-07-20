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
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.graalvm.component.installer.remote.CatalogIterable;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.ComponentParam;
import org.graalvm.component.installer.DependencyException;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.FileIterable;
import org.graalvm.component.installer.IncompatibleException;
import org.graalvm.component.installer.SoftwareChannelSource;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.remote.RemoteCatalogDownloader;
import org.graalvm.component.installer.persist.test.Handler;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
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
            relSpec = "catalog-" + name.getMethodName() + ".properties";
            if (getClass().getResource(relSpec) == null) {
                if (name.getMethodName().contains("Deps")) {
                    relSpec = "cataloginstallDeps.properties";
                } else {
                    relSpec = "catalogInstallTest.properties";
                }
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

    private void setupCatalog2(String rel) throws IOException {
        Path p = dataFile(rel);
        URL u = p.toUri().toURL();
        downloader = new RemoteCatalogDownloader(this, this, u);
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
        paramIterable = new CatalogIterable(this, this);
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
        paramIterable = new CatalogIterable(this, this);
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
        paramIterable = new CatalogIterable(this, this);
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
        paramIterable = new CatalogIterable(this, this);
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

    @Test
    public void testInstallWithDepsSingleLevel() throws Exception {
        setupVersion("19.3-dev");
        setupCatalog(null);
        paramIterable = new CatalogIterable(this, this);
        textParams.add("r");

        InstallCommand cmd = new InstallCommand();
        cmd.init(this, withBundle(InstallCommand.class));
        cmd.executionInit();

        cmd.executeStep(cmd::prepareInstallation, false);

        List<ComponentParam> deps = cmd.getDependencies();
        assertEquals(1, deps.size());
        assertEquals("org.graalvm.llvm-toolchain", deps.get(0).createMetaLoader().getComponentInfo().getId());
    }

    @Test
    public void testInstallWithIgnoredDeps() throws Exception {
        setupVersion("19.3-dev");
        setupCatalog(null);
        paramIterable = new CatalogIterable(this, this);
        textParams.add("r");
        options.put(Commands.OPTION_NO_DEPENDENCIES, "");

        InstallCommand cmd = new InstallCommand();
        cmd.init(this, withBundle(InstallCommand.class));
        cmd.executionInit();

        cmd.executeStep(cmd::prepareInstallation, false);

        List<ComponentParam> deps = cmd.getDependencies();
        assertTrue(deps.isEmpty());
    }

    @Test
    public void testInstallWithBrokenDeps() throws Exception {
        setupVersion("19.3-dev");
        setupCatalog(null);
        paramIterable = new CatalogIterable(this, this);
        textParams.add("additional");

        InstallCommand cmd = new InstallCommand();
        cmd.init(this, withBundle(InstallCommand.class));
        cmd.executionInit();

        exception.expect(FailedOperationException.class);
        exception.expectMessage("INSTALL_UnresolvedDependencies");
        try {
            cmd.executeStep(cmd::prepareInstallation, false);
        } catch (FailedOperationException ex) {
            Set<String> u = cmd.getUnresolvedDependencies();
            assertFalse(u.isEmpty());
            assertEquals("org.graalvm.unknown", u.iterator().next());
            throw ex;
        }
    }

    @Test
    public void testInstallWithBrokenIgnoredDeps() throws Exception {
        setupVersion("19.3-dev");
        setupCatalog(null);
        paramIterable = new CatalogIterable(this, this);
        textParams.add("additional");
        options.put(Commands.OPTION_NO_DEPENDENCIES, "");

        InstallCommand cmd = new InstallCommand();
        cmd.init(this, withBundle(InstallCommand.class));
        cmd.executionInit();

        cmd.executeStep(cmd::prepareInstallation, false);
    }

    /**
     * Checks that a dependency that is already installed is not installed again.
     */
    @Test
    public void testInstallDepsWithDependecnyInstalled() throws Exception {
        setupVersion("19.3-dev");
        setupCatalog(null);
        testInstallDepsWithDependecnyInstalledCommon();
    }

    private void testInstallDepsWithDependecnyInstalledCommon() throws Exception {
        ComponentInfo fakeInfo = new ComponentInfo("org.graalvm.llvm-toolchain", "Fake Toolchain", "19.3-dev");
        fakeInfo.setInfoPath("");
        storage.installed.add(fakeInfo);

        paramIterable = new CatalogIterable(this, this);
        textParams.add("r");

        InstallCommand cmd = new InstallCommand();
        cmd.init(this, withBundle(InstallCommand.class));
        cmd.executionInit();

        cmd.executeStep(cmd::prepareInstallation, false);
        assertTrue(cmd.getDependencies().isEmpty());
    }

    /**
     * Checks that if a dependency is specified also on the commandline, the component is actually
     * installed just once.
     */
    @Test
    public void testInstallDepsOnCommandLine() throws Exception {
        setupVersion("19.3-dev");
        setupCatalog2("cataloginstallDeps.properties");
        testInstallDepsOnCommandLineCommon();
    }

    private void testInstallDepsOnCommandLineCommon() throws Exception {
        paramIterable = new CatalogIterable(this, this);
        textParams.add("r");
        textParams.add("llvm-toolchain");

        InstallCommand cmd = new InstallCommand();
        cmd.init(this, withBundle(InstallCommand.class));
        cmd.executionInit();

        cmd.executeStep(cmd::prepareInstallation, false);
        cmd.executeStep(cmd::completeInstallers, false);

        List<Installer> instSequence = cmd.getInstallers();
        assertEquals(2, instSequence.size());
        ComponentInfo ci = instSequence.get(0).getComponentInfo();
        assertEquals("org.graalvm.llvm-toolchain", ci.getId());
        ci = instSequence.get(1).getComponentInfo();
        assertEquals("org.graalvm.r", ci.getId());
    }

    /**
     * Checks that if a dependency is specified also on the commandline, the component is actually
     * installed just once. In this case, the catalog does NOT specify dependencies, they are
     * discovered only when the component's archive is loaded.
     */
    @Test
    public void testInstallDepsOnCommandLine2() throws Exception {
        setupVersion("19.3-dev");
        setupCatalog2("cataloginstallDeps2.properties");
        testInstallDepsOnCommandLineCommon();
    }

    /**
     * Checks that dependencies precede the component that uses them. In this case ruby >
     * native-image > llvm-toolchain, so they should be installed in the opposite order.
     */
    @Test
    public void testDepsBeforeUsage() throws Exception {
        setupVersion("19.3-dev");
        setupCatalog2("cataloginstallDeps.properties");
        testDepsBeforeUsageCommon();
    }

    /**
     * The same as {@link #testDepsBeforeUsage()} except that dependency info is incomplete in the
     * catalog.
     * 
     * @throws Exception
     */
    @Test
    public void testDepsBeforeUsage2() throws Exception {
        setupVersion("19.3-dev");
        setupCatalog2("cataloginstallDeps2.properties");
        testDepsBeforeUsageCommon();
    }

    private void testDepsBeforeUsageCommon() throws Exception {
        paramIterable = new CatalogIterable(this, this);
        textParams.add("ruby");

        InstallCommand cmd = new InstallCommand();
        cmd.init(this, withBundle(InstallCommand.class));
        cmd.executionInit();

        cmd.executeStep(cmd::prepareInstallation, false);
        cmd.executeStep(cmd::completeInstallers, false);

        List<Installer> instSequence = cmd.getInstallers();
        assertEquals(3, instSequence.size());
        ComponentInfo ci = instSequence.get(0).getComponentInfo();
        assertEquals("org.graalvm.llvm-toolchain", ci.getId());
        ci = instSequence.get(1).getComponentInfo();
        assertEquals("org.graalvm.native-image", ci.getId());
        ci = instSequence.get(2).getComponentInfo();
        assertEquals("org.graalvm.ruby", ci.getId());
    }

    /**
     * Checks that two same components on the commandline are merged, including their dependencies.
     */
    @Test
    public void testTwoSameComponentsCommandLineDeps() throws Exception {
        setupVersion("19.3-dev");
        setupCatalog2("cataloginstallDeps.properties");
        testTwoSameComponentsCommandLineDepsCommon();
    }

    /**
     * Checks that two same components on the commandline are merged, including their dependencies.
     */
    @Test
    public void testTwoSameComponentsCommandLineDeps2() throws Exception {
        setupVersion("19.3-dev");
        setupCatalog2("cataloginstallDeps2.properties");
        testTwoSameComponentsCommandLineDepsCommon();
    }

    private void testTwoSameComponentsCommandLineDepsCommon() throws Exception {
        paramIterable = new CatalogIterable(this, this);
        textParams.add("r");
        textParams.add("r");

        InstallCommand cmd = new InstallCommand();
        cmd.init(this, withBundle(InstallCommand.class));
        cmd.executionInit();

        cmd.executeStep(cmd::prepareInstallation, false);
        cmd.executeStep(cmd::completeInstallers, false);

        List<Installer> instSequence = cmd.getInstallers();
        assertEquals(2, instSequence.size());
        ComponentInfo ci = instSequence.get(0).getComponentInfo();
        assertEquals("org.graalvm.llvm-toolchain", ci.getId());
        ci = instSequence.get(1).getComponentInfo();
        assertEquals("org.graalvm.r", ci.getId());
    }

    CatalogFactory catalogFactory = null;

    @Override
    public CatalogFactory getCatalogFactory() {
        if (catalogFactory == null) {
            return super.getCatalogFactory();
        } else {
            return catalogFactory;
        }
    }

    /**
     * Checks that dependencies can be loaded from the same directory as the installed Component.
     */
    @Test
    public void testInstallDependencyFromSameDirectory() throws Exception {
        Path ruby193Source = dataFile("../repo/19.3.0.0/r");
        Path llvm193Source = dataFile("../repo/19.3.0.0/llvm-toolchain");

        // they should be next to eah other
        assertEquals(ruby193Source.getParent(), llvm193Source.getParent());
        files.add(ruby193Source.toFile());
        setupVersion("19.3.0.0");
        // no external catalog
        downloader = new RemoteCatalogDownloader(this, this, (URL) null);
        downloader.addLocalChannelSource(
                        new SoftwareChannelSource(ruby193Source.getParent().toFile().toURI().toString()));
        catalogFactory = (input, reg) -> new CatalogContents(this, downloader.getStorage(), reg);
        FileIterable fit = new FileIterable(this, this);
        fit.setCatalogFactory(catalogFactory);
        paramIterable = fit;

        InstallCommand cmd = new InstallCommand();
        cmd.init(this, withBundle(InstallCommand.class));
        cmd.executionInit();

        cmd.executeStep(cmd::prepareInstallation, false);
        assertFalse(cmd.getDependencies().isEmpty());
    }

    /**
     * Tests, that a correct java version flavour gets selected if the catalog contains two flavours
     * of the component in the requested version.
     */
    @Test
    public void testInstallCorrectJavaVersion8() throws Exception {
        storage.graalInfo.put(CommonConstants.CAP_JAVA_VERSION, "8");
        setupVersion("1.0.0.0");
        setupCatalog("catalogMultiFlavours.properties");

        paramIterable = new CatalogIterable(this, this);
        textParams.add("ruby");

        InstallCommand cmd = new InstallCommand();
        cmd.init(this, withBundle(InstallCommand.class));
        cmd.executionInit();

        cmd.executeStep(cmd::prepareInstallation, false);
        List<Installer> installers = cmd.getInstallers();
        assertEquals(1, installers.size());
    }

    @Test
    public void testInstallCorrectJavaVersion11() throws Exception {
        storage.graalInfo.put(CommonConstants.CAP_JAVA_VERSION, "11");
        setupVersion("1.0.0.0");
        setupCatalog("catalogMultiFlavours.properties");

        paramIterable = new CatalogIterable(this, this);
        textParams.add("ruby");

        InstallCommand cmd = new InstallCommand();
        cmd.init(this, withBundle(InstallCommand.class));
        cmd.executionInit();

        cmd.executeStep(cmd::prepareInstallation, false);
        List<Installer> installers = cmd.getInstallers();
        assertEquals(1, installers.size());
    }
}
