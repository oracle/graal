/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.gds;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.commands.InstallCommand;
import org.graalvm.component.installer.commands.LicensePresenter;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.MetadataLoader;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.persist.test.Handler;
import org.graalvm.component.installer.remote.CatalogIterable;
import org.graalvm.component.installer.remote.GraalEditionList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class InstallFromGdsTest extends CommandTestBase {
    @Rule public final ProxyResource proxyResource = new ProxyResource();

    private Path gdsCatalogPath;
    private Path gdsDownloadFolder;
    private String gdsUrl;
    private GraalEditionList gl;
    private InstallCommand inst;

    private Set<String> userAccepted = new HashSet<>();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        storage.graalInfo.put(CommonConstants.CAP_GRAALVM_VERSION, "20.3.0");
        Handler.bind("test://acme.org/GRAALVM_EE_JAVA8_20.3.0/license.txt",
                        dataFile("data/license.txt").toUri().toURL());
        gdsDownloadFolder = dataFile("data");
        gdsCatalogPath = gdsDownloadFolder.resolve("insttest.json");

        gdsUrl = "{ee}gds:" + gdsCatalogPath.toUri().toURL().toString();
        catalogStorage.graalInfo.put(CommonConstants.RELEASE_CATALOG_KEY,
                        gdsUrl);
        iterableInstance = new CatalogIterable(this, this);
        gl = new GraalEditionList(this, this, getLocalRegistry());
        gl.setDefaultCatalogSpec(gdsUrl);
        gl.setDefaultEdition(gl.getEdition("ee"));
    }

    @Override
    public CatalogFactory getCatalogFactory() {
        return gl;
    }

    class TestPresenter extends LicensePresenter {

        TestPresenter(Feedback feedback, ComponentRegistry localRegistry, Map<String, List<MetadataLoader>> licenseIDs) {
            super(feedback, localRegistry, licenseIDs);
        }

        @Override
        protected void acceptLicense(String licenseId) {
            super.acceptLicense(licenseId);
            userAccepted.add(licenseId);
        }
    }

    class TestInstallCommand extends InstallCommand {

        @Override
        protected LicensePresenter createLicensePresenter() {
            return new TestPresenter(InstallFromGdsTest.this, localRegistry, getLicensesToAccept());
        }
    }

    /**
     * Checks that license will not be recorded, if the user does not provide the email.
     */
    @Test
    public void testLicenseAcceptedWithoutEmail() throws Exception {
        textParams.add("llvm-toolchain");
        userInput.append("y");
        installAndCheckLicenseNotRecorded();
    }

    private void installAndCheckLicenseNotRecorded() throws Exception {
        inst = new TestInstallCommand();
        inst.init(this, withBundle(InstallCommand.class));
        inst.execute();

        // the user DID accept the license:
        assertFalse(userAccepted.isEmpty());
        // check the license was NOT recorded.
        assertTrue(storage.acceptedLicenses.isEmpty());
    }

    private void checkPropertyExists(String expectedMail) throws IOException {
        Path propPath = targetPath.resolve(MailStorage.PROPERTIES_PATH);
        if (expectedMail == null) {
            assertFalse(Files.exists(propPath));
        } else {
            Properties props = new Properties();
            try (InputStream is = Files.newInputStream(propPath)) {
                props.load(is);
            }
            assertEquals(expectedMail, props.getProperty(MailStorage.PROP_LAST_EMAIL));
        }
    }

    /**
     * Checks that noninteractive mode will not record the license acceptance, even if -a is in
     * effect.
     * 
     * @throws Exception
     */
    @Test
    public void testLicenseNotRecordedInNoninteractive() throws Exception {
        textParams.add("llvm-toolchain");
        autoYesEnabled = true;
        installAndCheckLicenseNotRecorded();
        checkPropertyExists(null);
    }

    @Test
    public void testInvalidEmailAddressErrors() throws Exception {
        textParams.add("llvm-toolchain");
        userInput.append("y\n");
        userInput.append("blahblah");
        try {
            installAndCheckLicenseNotRecorded();
            fail("Should abort the installation");
        } catch (FailedOperationException ex) {
            // expected
        }
        checkPropertyExists(null);
    }

    @Test
    public void testValidEmailAddressMakesRecord() throws Exception {
        textParams.add("llvm-toolchain");
        userInput.append("y\n");
        userInput.append("nobody@acme.org");
        checkLicenseRecorded("nobody@acme.org");
    }

    private void checkLicenseRecorded(String expected) throws Exception {
        inst = new TestInstallCommand();
        inst.init(this, withBundle(InstallCommand.class));
        inst.execute();

        // the user DID accept the license:
        assertFalse(userAccepted.isEmpty());
        // check the license was recorded.
        assertFalse(storage.acceptedLicenses.isEmpty());

        checkPropertyExists(expected);
    }

    /**
     * Checks that --email will provide email for GDS service and the accepted license is recorded.
     * 
     * @throws Exception
     */
    @Test
    public void testProvideEmailCommandline() throws Exception {
        options.put(GdsCommands.OPTION_EMAIL_ADDRESS, "nobody@acme.org");
        textParams.add("llvm-toolchain");
        userInput.append("y\n");
        checkLicenseRecorded("nobody@acme.org");
    }

    /**
     * Checks that an email that was once recorded into the storage is fetched and is used without
     * asking the user for anything.
     * 
     * @throws Exception
     */
    @Test
    public void testOnceRecordedMailIsLoaded() throws Exception {
        Path p = targetPath.resolve(MailStorage.PROPERTIES_PATH);
        Files.createDirectories(p.getParent());
        Files.write(p,
                        Arrays.asList("last.email=joker@acme.org"));
        textParams.add("llvm-toolchain");
        userInput.append("y\n");
        checkLicenseRecorded("joker@acme.org");
    }

    /**
     * Checks e-mail is not asked for again after rejection.
     * 
     * @throws Exception
     */
    @Test
    public void testEmailRejectedJustOnce() throws Exception {
        textParams.add("llvm-toolchain");
        textParams.add("native-image");
        userInput.append("y\n");
        userInput.append("\n");

        class FB extends FeedbackAdapter {
            int emailPrompts;

            @Override
            public void output(String bundleKey, Object... params) {
                super.outputPart(bundleKey, params);
                if ("PROMPT_EmailAddressEntry".equals(bundleKey)) {
                    emailPrompts++;
                }
            }

        }
        FB fb = new FB();
        delegateFeedback(fb);

        installAndCheckLicenseNotRecorded();
        checkPropertyExists(null);

        assertEquals(1, fb.emailPrompts);
    }

}
