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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.UserAbortException;
import org.graalvm.component.installer.persist.MetadataLoader;
import org.graalvm.component.installer.jar.JarMetaLoader;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.DirectoryMetaLoader;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.persist.test.Handler;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Rule;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class LicensePresenterTest extends CommandTestBase {
    MetadataLoader loader;
    Path dirPath;
    Map<String, List<MetadataLoader>> licenseIDs = new LinkedHashMap<>();

    ComponentInfo licensedInfo;

    ComponentInfo createLicensedComponentInfo() throws IOException {
        Path p = dataFile("licensetest.jar");
        JarFile jf = new JarFile(p.toFile());
        loader = new JarMetaLoader(jf, this);
        licensedInfo = loader.completeMetadata();

        return licensedInfo;
    }

    private void initLoader(String f) throws IOException {
        if (loader != null) {
            loader.close();
        }
        Path p = expandedFolder.getRoot().toPath().resolve(testName.getMethodName());
        if (!Files.isDirectory(p)) {
            Files.createDirectory(p);
        }
        dirPath = dataFile(f);
        loader = DirectoryMetaLoader.create(dirPath, this);
        licensedInfo = loader.completeMetadata();
    }

    @Test
    public void testAcceptedLicensesWillBeSuppressed() throws Exception {
        initLoader("license.ruby");

        LicensePresenter p = new LicensePresenter(this, getLocalRegistry(), licenseIDs);
        licenseIDs.put(loader.getLicenseID(), new ArrayList<>(Arrays.asList(loader)));
        Map<String, Date> comps = new HashMap<>();
        comps.put(loader.getLicenseID(), new Date());
        this.storage.acceptedLicenses.put(loader.getComponentInfo().getId(), comps);
        p.init();
        assertTrue(p.isFinished());
    }

    @Test
    public void testLicensesWillShow() throws Exception {
        initLoader("license.ruby");

        LicensePresenter p = new LicensePresenter(this, getLocalRegistry(), licenseIDs);
        licenseIDs.put(loader.getLicenseID(), new ArrayList<>(Arrays.asList(loader)));

        p.init();
        assertFalse(p.isFinished());
        assertEquals(LicensePresenter.State.SINGLE, p.getState());
    }

    class LicFeedback extends FeedbackAdapter {
        boolean questionAsked;
        String answer;
        String licenseQuestion;

        LicFeedback(String licenseQuestion) {
            this.licenseQuestion = licenseQuestion;
        }

        @Override
        public String l10n(String key, Object... params) {
            if (key.contains("AcceptPrompt")) {
                return reallyl10n(key, params);
            }
            return super.l10n(key, params);
        }

        @Override
        public String acceptLine(boolean yes) {
            return answer;
        }

        @Override
        public void output(String bundleKey, Object... params) {
            if (bundleKey.equals(licenseQuestion)) {
                questionAsked = true;
            }
            super.output(bundleKey, params);
        }
    }

    void addLoader(String rpmName) throws Exception {
        initLoader(rpmName);
        licenseIDs.computeIfAbsent(loader.getLicenseID(), (n) -> new ArrayList<>()).add(loader);
    }

    LicensePresenter pres;
    LicFeedback licF;

    /**
     * Checks that single license will display prompt and accepts "Read".
     */
    @Test
    public void testSingleLicenseDisplaysQuestion() throws Exception {
        addLoader("license.ruby");

        pres = new LicensePresenter(this, getLocalRegistry(), licenseIDs);
        pres.init();

        licF = new LicFeedback("INSTALL_AcceptLicense");
        delegateFeedback(licF);
        licF.answer = "r";
        pres.singleStep();
        assertEquals(LicensePresenter.State.LICENSE, pres.getState());
        assertTrue(licF.questionAsked);

        pres.init();
        licF.answer = "R";
        pres.singleStep();
        assertEquals(LicensePresenter.State.LICENSE, pres.getState());

        pres.init();
        licF.answer = "Read";
        pres.singleStep();
        assertEquals(LicensePresenter.State.LICENSE, pres.getState());

        pres.init();
        licF.answer = "rEaD";
        pres.singleStep();
        assertEquals(LicensePresenter.State.LICENSE, pres.getState());
    }

    /**
     * Any other answer than Y or R will abort the operation.
     */
    public void testSingleLicenseUserAbort() throws Exception {
        addLoader("license.ruby");

        LicensePresenter p = new LicensePresenter(this, getLocalRegistry(), licenseIDs);
        p.init();

        LicFeedback d = new LicFeedback("INSTALL_AcceptLicense");
        delegateFeedback(d);
        d.answer = "a";
        exception.expect(UserAbortException.class);
        p.singleStep();
    }

    /**
     * License can be accepted even without reading the text.
     */
    @Test
    public void testSingleLicenseAcceptNoRead() throws Exception {
        addLoader("license.ruby");

        pres = new LicensePresenter(this, getLocalRegistry(), licenseIDs);
        pres.init();

        LicFeedback d = new LicFeedback("INSTALL_AcceptLicense");
        delegateFeedback(d);
        d.answer = "y";
        pres.singleStep();
        assertTrue(pres.isFinished());
        assertTrue(!storage.acceptedLicenses.isEmpty());
    }

    /**
     * License can be accepted after reading its text.
     */
    @Test
    public void testSingleLicenseAcceptAfterRead() throws Exception {
        addLoader("license.ruby");

        pres = new LicensePresenter(this, getLocalRegistry(), licenseIDs);
        pres.init();

        licF = new LicFeedback("INSTALL_AcceptLicense");
        delegateFeedback(licF);
        licF.answer = "r";
        pres.singleStep();
        assertEquals(LicensePresenter.State.LICENSE, pres.getState());
        assertTrue(licF.questionAsked);

        // prepare
        licF.questionAsked = false;
        licF.licenseQuestion = "INSTALL_AcceptLicensePrompt";
        licF.answer = "y";
        pres.singleStep();

        assertTrue(pres.isFinished());
        assertTrue(licenseIDs.isEmpty());
        assertFalse(storage.acceptedLicenses.isEmpty());
    }

    /**
     * License can be accepted after reading its text.
     */
    @Test
    public void testSingleLicenseAAbortAfterRead() throws Exception {
        addLoader("license.ruby");

        pres = new LicensePresenter(this, getLocalRegistry(), licenseIDs);
        pres.init();

        licF = new LicFeedback("INSTALL_AcceptLicense");
        delegateFeedback(licF);
        licF.answer = "r";
        pres.singleStep();
        assertEquals(LicensePresenter.State.LICENSE, pres.getState());
        assertTrue(licF.questionAsked);

        // prepare
        licF.questionAsked = false;
        licF.licenseQuestion = "INSTALL_AcceptLicensePrompt";
        licF.answer = "X";
        exception.expect(UserAbortException.class);
        pres.singleStep();
    }

    Map<String, List<MetadataLoader>> savedLicenseIDs;

    public void assertYes() throws Exception {
        licenseIDs.clear();
        licenseIDs.putAll(savedLicenseIDs);
        pres.init();
        licF.answer = "Y";
        pres.singleStep();
        assertEquals(LicensePresenter.State.LICENSE, pres.getState());

        licenseIDs.clear();
        licenseIDs.putAll(savedLicenseIDs);
        pres.init();
        licF.answer = "yes";
        pres.singleStep();
        assertEquals(LicensePresenter.State.LICENSE, pres.getState());

        licenseIDs.clear();
        licenseIDs.putAll(savedLicenseIDs);
        pres.init();
        licF.answer = "YeS";
        pres.singleStep();
        assertEquals(LicensePresenter.State.LICENSE, pres.getState());
    }

    @Test
    public void testMultiLicensesDisplaysMenu() throws Exception {
        addLoader("license.python");
        addLoader("license.ruby");

        pres = new LicensePresenter(this, getLocalRegistry(), licenseIDs);
        licenseIDs.put(loader.getLicenseID(), new ArrayList<>(Arrays.asList(loader)));
        pres.init();

        assertFalse(pres.isFinished());
        assertEquals(LicensePresenter.State.LIST, pres.getState());

        pres.singleStep();
        assertEquals(LicensePresenter.State.LISTINPUT, pres.getState());
    }

    @Test
    public void testMultiAcceptAll() throws Exception {
        addLoader("license.python");
        addLoader("license.ruby");

        licF = new LicFeedback("INSTALL_AcceptAllLicensesPrompt");
        pres = new LicensePresenter(this, getLocalRegistry(), licenseIDs);
        pres.init();
        pres.singleStep();
        licF.answer = "Y";
        delegateFeedback(licF);
        pres.singleStep();

        assertTrue(pres.isFinished());
        assertEquals(2, storage.acceptedLicenses.size());
    }

    @Test
    public void testMultiReadsLicense() throws Exception {
        addLoader("license.python");
        addLoader("license.ruby");

        pres = new LicensePresenter(this, getLocalRegistry(), licenseIDs);
        pres.init();

        savedLicenseIDs = new LinkedHashMap<>(licenseIDs);

        class X extends LicFeedback {
            StringBuilder licText = new StringBuilder();

            X(String licenseQuestion) {
                super(licenseQuestion);
            }

            @Override
            public boolean verbatimOut(String msg, boolean beVerbose) {
                licText.append(msg);
                return super.verbatimOut(msg, beVerbose);
            }
        }
        X x = new X("INSTALL_AcceptAllLicensesPrompt");
        licF = x;
        licF.answer = "1";
        delegateFeedback(licF);
        pres.singleStep();
        pres.singleStep();
        assertEquals(LicensePresenter.State.LICENSE, pres.getState());

        licF.answer = "y";
        pres.singleStep();
        assertTrue(x.licText.toString().toLowerCase().contains("python"));

        // single license remains, but still in LIST state
        assertEquals(LicensePresenter.State.LIST, pres.getState());

        // reinitialize
        licenseIDs.clear();
        licenseIDs.putAll(savedLicenseIDs);
        storage.acceptedLicenses.clear();
        x.licText.delete(0, x.licText.length());

        pres.init();
        licF.answer = "2";
        pres.singleStep();
        pres.singleStep();
        assertEquals(LicensePresenter.State.LICENSE, pres.getState());

        licF.answer = "y";
        pres.singleStep();
        assertTrue(x.licText.toString().toLowerCase().contains("ruby"));

        assertEquals(LicensePresenter.State.LIST, pres.getState());
    }

    @Rule public ProxyResource proxyResource = new ProxyResource();

    /**
     * Checks that a remote license is downloaded from the URL.
     * 
     * @throws Exception
     */
    @Test
    public void testLicenseRemoteDownload() throws Exception {
        addLoader("license.ruby");
        URL licURL = getClass().getResource("license.ruby/license.txt");

        URL remoteUrl = new URL("test://somewhere.org/license.txt");
        Handler.bind(remoteUrl.toString(), licURL);
        licensedInfo.setLicensePath(remoteUrl.toString());

        pres = new LicensePresenter(this, getLocalRegistry(), licenseIDs);
        pres.init();

        LicFeedback d = new LicFeedback("INSTALL_AcceptLicense");
        delegateFeedback(d);
        d.answer = "y";
        pres.singleStep();
        assertTrue(pres.isFinished());

        assertTrue(Handler.isVisited(remoteUrl));
    }
}
