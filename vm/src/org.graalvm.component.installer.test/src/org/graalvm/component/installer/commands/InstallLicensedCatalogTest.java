/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.CommonConstants;
import static org.graalvm.component.installer.CommonConstants.CAP_JAVA_VERSION;
import org.graalvm.component.installer.ComponentCatalog;
import org.graalvm.component.installer.gds.GraalChannel;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.model.GraalEdition;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.persist.test.Handler;
import org.graalvm.component.installer.remote.CatalogIterable;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class InstallLicensedCatalogTest extends CommandTestBase {
    @Rule public ProxyResource proxyResource = new ProxyResource();

    private static final String METADATA_URL = "test://oca.opensource.oracle.com/gds/meta-data.json";
    private static final String CATALOG_URL = "test://oca.opensource.oracle.com/gds/catalog-19.3.2.properties";
    private static final String LICENSE_URL = "test://oca.opensource.oracle.com/gds/GRAALVM_EE_JAVA8_19_3_2/license.txt";

    private static final String NATIVE_IMAGE_URL = "test://github.com/graalvm/graalvm-ce-builds/releases/download/vm-19.3.2/native-image-installable-svm-java8-linux-amd64-19.3.2.jar";
    private static final String LLVM_URL = "test://github.com/graalvm/graalvm-ce-builds/releases/download/vm-19.3.2/llvm-toolchain-installable-java8-linux-amd64-19.3.2.jar";

    private void initCatalogIterable(URL u) {
        GraalChannel channel = new GraalChannel(this, this, getLocalRegistry());
        channel.setReleasesIndexURL(u);
        cfactory = new CatalogFactory() {
            @Override
            public ComponentCatalog createComponentCatalog(CommandInput input) {
                try {
                    return new CatalogContents(InstallLicensedCatalogTest.this, channel.getStorage(), localRegistry);
                } catch (IOException ex) {
                    fail("Unexpected exception");
                    return null;
                }
            }

            @Override
            public List<GraalEdition> listEditions(ComponentRegistry targetGraalVM) {
                return Collections.emptyList();
            }
        };
        paramIterable = new CatalogIterable(this, this);
    }

    private CatalogFactory cfactory;

    @Override
    public CatalogFactory getCatalogFactory() {
        if (cfactory != null) {
            return cfactory;
        } else {
            return super.getCatalogFactory();
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        storage.graalInfo.put(CommonConstants.CAP_EDITION, "ee");

        storage.graalInfo.put(CommonConstants.CAP_OS_NAME, "linux");
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, "19.3.2");
        storage.graalInfo.put(CAP_JAVA_VERSION, "8");

        Path r = dataFile("data/releases.json");
        Path l = dataFile("data/license.txt");
        Path c = dataFile("data/catalog-19.3.2.properties");

        options.put("C", METADATA_URL);
        Handler.bind(METADATA_URL, r.toUri().toURL());
        Handler.bind(LICENSE_URL, l.toUri().toURL());
        Handler.bind(CATALOG_URL, c.toUri().toURL());

        Path llvm = dataFile("data/llvm-deps-19.3.2.jar");
        Path nimage = dataFile("data/native-image-deps-19.3.2.jar");

        Handler.bind(LLVM_URL, llvm.toUri().toURL());
        Handler.bind(NATIVE_IMAGE_URL, nimage.toUri().toURL());
    }

    static class Abort extends RuntimeException {
        private static final long serialVersionUID = -22;
    }

    AtomicBoolean licenseAccepted = new AtomicBoolean(false);

    class LicensedInstallCommand extends InstallCommand {
        @Override
        void completeInstallers() throws IOException {
            if (licenseAccepted.get()) {
                super.completeInstallers();
            } else {
                fail("Must not happen before accepting licenses");
            }
        }

        @Override
        protected LicensePresenter createLicensePresenter() {
            assertFalse("Must not accept license twice", licenseAccepted.get());
            return new LicensePresenter(getFeedback(), getLocalRegistry(), getLicensesToAccept()) {

                @Override
                void displaySingleLicense() {
                    assertEquals(1, getLicensesToAccept().size());

                    userInput.append("R");
                    super.displaySingleLicense();
                }

                @Override
                void displayLicenseText() throws IOException {
                    userInput.append("Y");
                    super.displayLicenseText();
                    licenseAccepted.set(true);
                }

                @Override
                int processUserInputForList() {
                    fail("Should be just one license");
                    return -1;
                }
            };
        }
    }

    @Test
    public void testInstallLicensedComponent() throws Exception {
        initCatalogIterable(new URL(METADATA_URL));
        textParams.add("llvm-toolchain");

        InstallCommand cmd = new LicensedInstallCommand();

        cmd.init(this, this.withBundle(InstallCommand.class));
        cmd.execute();

        assertTrue(Handler.isVisited(LLVM_URL));
        assertTrue(Handler.isVisited(LICENSE_URL));
        assertFalse(Handler.isVisited(NATIVE_IMAGE_URL));
    }

    /**
     * Checks that the license will not be presented twice, if accepted.
     * 
     * @throws Exception
     */
    @Test
    public void testDependentLicensedComponent() throws Exception {
        initCatalogIterable(new URL(METADATA_URL));
        textParams.add("native-image");

        InstallCommand cmd = new LicensedInstallCommand();

        cmd.init(this, this.withBundle(InstallCommand.class));
        cmd.execute();

        assertTrue(Handler.isVisited(LLVM_URL));
        assertTrue(Handler.isVisited(NATIVE_IMAGE_URL));
        assertTrue(Handler.isVisited(LICENSE_URL));

        assertNotNull(localRegistry.findComponent("llvm-toolchain"));
        assertNotNull(localRegistry.findComponent("native-image"));
    }

    /**
     * 
     * @throws Exception
     */
    public void testProvideEmailAfterLicense() throws Exception {

    }

}
