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

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.remote.RemoteCatalogDownloader;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TestName;

/**
 *
 * @author sdedic
 */
public class UninstallTest extends CommandTestBase {
    @Rule public TestName name = new TestName();
    @Rule public ExpectedException exception = ExpectedException.none();

    CatalogContents componentCatalog;

    private void setupComponentsWithDeps() throws Exception {
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "19.3-dev");
        Path catalogFile = dataFile("cataloginstallDeps.properties");
        RemoteCatalogDownloader downloader = new RemoteCatalogDownloader(this, this,
                        catalogFile.toUri().toURL());
        componentCatalog = new CatalogContents(this, downloader.getStorage(), getLocalRegistry());

        ComponentInfo ruby = componentCatalog.findComponent("org.graalvm.ruby");
        ComponentInfo fastr = componentCatalog.findComponent("org.graalvm.r");
        Set<ComponentInfo> deps = new HashSet<>();
        componentCatalog.findDependencies(ruby, true, null, deps);
        componentCatalog.findDependencies(fastr, true, null, deps);
        deps.add(ruby);
        deps.add(fastr);
        storage.installed.addAll(deps);
        deps.forEach((ci) -> ci.setInfoPath(""));
    }

    /**
     * Schedules uninstallation of a leaf component, no breakages.
     */
    @Test
    public void testUninstallLeaf() throws Exception {
        setupComponentsWithDeps();

        UninstallCommand uc = new UninstallCommand();
        uc.init(this, this);
        textParams.add("ruby");
        uc.prepareUninstall();
        assertTrue(uc.getBrokenDependencies().isEmpty());
        assertEquals(1, uc.getUninstallComponents().size());

        // should succeed
        uc.checkBrokenDependencies();
    }

    /**
     * Schedules uninstallation of a library. One component should become broken.
     */
    @Test
    public void testUninstallLibraryFailure() throws Exception {
        setupComponentsWithDeps();

        UninstallCommand uc = new UninstallCommand();
        uc.init(this, this.withBundle(UninstallCommand.class));
        textParams.add("org.graalvm.native-image");

        uc.prepareUninstall();
        assertFalse(uc.getBrokenDependencies().isEmpty());
        assertEquals(1, uc.getUninstallComponents().size());

        ComponentInfo nimage = localRegistry.findComponent("org.graalvm.native-image");
        ComponentInfo ruby = localRegistry.findComponent("org.graalvm.ruby");
        assertSame(nimage, uc.getUninstallComponents().iterator().next());

        Collection<ComponentInfo> broken = uc.getBrokenDependencies().get(nimage);
        assertNotNull(broken);
        assertSame(ruby, broken.iterator().next());

        exception.expect(FailedOperationException.class);
        exception.expectMessage("UNINSTALL_BreakDependenciesTerminate");
        uc.checkBrokenDependencies();
    }

    @Test
    public void testUninstallIgnoreDeps() throws Exception {
        setupComponentsWithDeps();
        options.put(Commands.OPTION_NO_DEPENDENCIES, "");

        UninstallCommand uc = new UninstallCommand();
        uc.init(this, this.withBundle(UninstallCommand.class));
        textParams.add("org.graalvm.native-image");

        uc.prepareUninstall();
        assertTrue(uc.getBrokenDependencies().isEmpty());

        uc.checkBrokenDependencies();
        uc.includeAndOrderComponents();
        List<ComponentInfo> comps = uc.getUninstallSequence();
        assertEquals(1, comps.size());
    }

    /**
     * Library is to be uninstalled together with a feature it is using the library. Should succeed
     */
    @Test
    public void testUninstallLibraryWithUsers() throws Exception {
        setupComponentsWithDeps();

        UninstallCommand uc = new UninstallCommand();
        uc.init(this, this.withBundle(UninstallCommand.class));
        textParams.add("org.graalvm.native-image");
        textParams.add("org.graalvm.ruby");

        uc.prepareUninstall();
        // broken ruby is recorded
        assertFalse(uc.getBrokenDependencies().isEmpty());
        assertEquals(2, uc.getUninstallComponents().size());
        // however it will not fail the installation
        uc.checkBrokenDependencies();
    }

    /**
     * Force flag allows to broken components. Check that warning is printed.
     */
    @Test
    public void testUninstallBreakForced() throws Exception {
        setupComponentsWithDeps();

        UninstallCommand uc = new UninstallCommand();
        uc.init(this, this.withBundle(UninstallCommand.class));
        uc.setBreakDependent(true);
        textParams.add("org.graalvm.native-image");

        uc.prepareUninstall();
        // broken ruby is recorded
        assertFalse(uc.getBrokenDependencies().isEmpty());
        assertEquals(1, uc.getUninstallComponents().size());
        class FD extends FeedbackAdapter {
            boolean target;

            @Override
            public void error(String key, Throwable t, Object... params) {
                fail("No error should be printed");
            }

            @Override
            public void output(String bundleKey, Object... params) {
                switch (bundleKey) {
                    case "UNINSTALL_BreakDepSource":
                        assertEquals("org.graalvm.native-image", params[1]);
                        break;

                    case "UNINSTALL_BreakDepTarget":
                        target = true;
                        assertEquals("org.graalvm.ruby", params[1]);
                        break;
                }
            }

        }
        FD fd = new FD();
        delegateFeedback(fd);
        uc.checkBrokenDependencies();
        assertTrue(fd.target);
    }

    /**
     * Checks that uninstallation of a library with a flag will imply uninstallation of dependents.
     * Verifies the order of uninstallation.
     */
    @Test
    public void testUninstallLibraryImplyDependents() throws Exception {
        setupComponentsWithDeps();

        UninstallCommand uc = new UninstallCommand();
        uc.init(this, this.withBundle(UninstallCommand.class));
        uc.setRemoveDependent(true);
        textParams.add("org.graalvm.llvm-toolchain");

        uc.prepareUninstall();
        // broken ruby is recorded
        assertFalse(uc.getBrokenDependencies().isEmpty());
        assertEquals(1, uc.getUninstallComponents().size());
        // however it will not fail the installation
        uc.checkBrokenDependencies();

        uc.includeAndOrderComponents();

        List<ComponentInfo> comps = uc.getUninstallSequence();
        assertEquals(4, comps.size());

        // handle possible different ordering of the component removal
        int nativeIndex = comps.indexOf(localRegistry.findComponent("org.graalvm.native-image"));
        int rubyIndex = comps.indexOf(localRegistry.findComponent("org.graalvm.ruby"));
        int rIndex = comps.indexOf(localRegistry.findComponent("org.graalvm.r"));

        // native depends on llvm so llvm must be the last; native may not be the first, as ruby
        // depends on it
        assertTrue(nativeIndex > 0 && nativeIndex < 3);
        assertTrue(rubyIndex < nativeIndex);
        assertTrue(rIndex < 3);
        assertEquals("org.graalvm.llvm-toolchain", comps.get(3).getId());
    }

}
