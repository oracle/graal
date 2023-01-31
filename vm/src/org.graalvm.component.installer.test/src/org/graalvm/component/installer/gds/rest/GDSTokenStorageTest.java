/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.gds.rest;

import org.graalvm.component.installer.CommandTestBase;
import static org.graalvm.component.installer.CommonConstants.PATH_GDS_CONFIG;
import static org.graalvm.component.installer.CommonConstants.PATH_USER_GU;
import static org.graalvm.component.installer.CommonConstants.SYSPROP_USER_HOME;
import org.graalvm.component.installer.gds.GdsCommands;
import static org.graalvm.component.installer.gds.rest.GDSTokenStorage.GRAAL_EE_DOWNLOAD_TOKEN;
import static org.graalvm.component.installer.gds.rest.GDSTokenStorage.Source;
import org.graalvm.component.installer.MemoryFeedback.Case;
import org.graalvm.component.installer.MemoryFeedback;
import static org.graalvm.component.installer.gds.rest.TestGDSTokenStorage.testPath;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 *
 * @author odouda
 */
public class GDSTokenStorageTest extends CommandTestBase {
    static final Path tstPath = Path.of(System.getProperty(SYSPROP_USER_HOME), PATH_USER_GU, PATH_GDS_CONFIG + "test");
    static final String MOCK_TOKEN_DEFAULT = "MOCK_TOKEN_DEFAULT";
    static final String MOCK_TOKEN_ENV = "MOCK_TOKEN_ENV";
    static final String MOCK_TOKEN_USER = "MOCK_TOKEN_USER";
    static final String MOCK_EMAIL = "some@mock.email";

    MemoryFeedback mf;
    TestGDSTokenStorage ts;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        delegateFeedback(mf = new MemoryFeedback());
        reload();
        clean();
    }

    @After
    public void tearDown() throws IOException {
        clean();
        assertTrue(mf.toString(), mf.isEmpty());
    }

    private void reload() {
        ts = new TestGDSTokenStorage(this, this);
    }

    private static void clean() throws IOException {
        Files.deleteIfExists(testPath);
        Files.deleteIfExists(tstPath);
    }

    @Test
    public void testPropPath() {
        Path pth = new GDSTokenStorage(this, this).getPropertiesPath();
        assertEquals(Path.of(System.getProperty(SYSPROP_USER_HOME), PATH_USER_GU, PATH_GDS_CONFIG), pth);
    }

    @Test
    public void testGetTokenSource() throws IOException {
        assertEquals(Source.NON, ts.getConfSource());

        assertTokenSource(null, Source.NON);

        store(testPath, "SOME_DIFFERENT_KEY", MOCK_TOKEN_DEFAULT);
        assertTokenSource(null, Source.NON);

        store(testPath, GRAAL_EE_DOWNLOAD_TOKEN, MOCK_TOKEN_DEFAULT);
        assertTokenSource(MOCK_TOKEN_DEFAULT, Source.FIL);

        envParameters.put(GRAAL_EE_DOWNLOAD_TOKEN, MOCK_TOKEN_ENV);
        assertTokenSource(MOCK_TOKEN_ENV, Source.ENV);

        options.put(GdsCommands.OPTION_GDS_CONFIG, tstPath.toString());
        store(tstPath, GRAAL_EE_DOWNLOAD_TOKEN, MOCK_TOKEN_USER);
        assertTokenSource(MOCK_TOKEN_USER, Source.CMD);

        store(tstPath, GRAAL_EE_DOWNLOAD_TOKEN, "");
        assertTokenSource(MOCK_TOKEN_ENV, Source.ENV);

        envParameters.put(GRAAL_EE_DOWNLOAD_TOKEN, "");
        assertTokenSource(MOCK_TOKEN_DEFAULT, Source.FIL);

        store(testPath, GRAAL_EE_DOWNLOAD_TOKEN, "");
        assertTokenSource(null, Source.NON);

        try (OutputStream os = Files.newOutputStream(ts.getPropertiesPath())) {
            os.write("key1=foo\\u0123\nkey2=bar\\u0\\n".getBytes());
            os.flush();
        }
        reload();
        ts.getToken();
        mf.checkMem(Case.ERR, "ERR_CouldNotLoadToken", ts.getPropertiesPath(), "Malformed \\uxxxx encoding.");
    }

    @Test
    public void testSetToken() {
        assertEquals(null, ts.getToken());
        assertEquals(Source.NON, ts.getConfSource());

        ts.setToken(MOCK_TOKEN_DEFAULT);
        assertEquals(MOCK_TOKEN_DEFAULT, ts.getToken());
        assertEquals(Source.FIL, ts.getConfSource());

        ts.setToken("");
        assertEquals(null, ts.getToken());
        assertEquals(Source.NON, ts.getConfSource());

        try {
            ts.setToken(null);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException ex) {
            // expected
        }
    }

    @Test
    public void testSave() throws IOException {
        assertTokenSource(null, Source.NON);
        ts.setToken(MOCK_TOKEN_DEFAULT);
        assertTokenSource(null, Source.NON);
        ts.setToken(MOCK_TOKEN_DEFAULT);
        ts.save();
        assertTokenSource(MOCK_TOKEN_DEFAULT, Source.FIL);
    }

    @Test
    public void testPrintToken() throws IOException {
        ts.printToken();
        store(testPath, GRAAL_EE_DOWNLOAD_TOKEN, MOCK_TOKEN_DEFAULT);
        reload();
        ts.printToken();
        envParameters.put(GRAAL_EE_DOWNLOAD_TOKEN, MOCK_TOKEN_ENV);
        ts.printToken();
        options.put(GdsCommands.OPTION_GDS_CONFIG, tstPath.toString());
        store(tstPath, GRAAL_EE_DOWNLOAD_TOKEN, MOCK_TOKEN_USER);
        ts.printToken();
        mf.checkMem(Case.MSG, "MSG_EmptyToken", null, "");
        mf.checkMem(Case.FRM, "MSG_PrintTokenFile", testPath.toString());
        mf.checkMem(Case.MSG, "MSG_PrintToken", MOCK_TOKEN_DEFAULT, "MSG_PrintTokenFile");
        mf.checkMem(Case.FRM, "MSG_PrintTokenEnv", GRAAL_EE_DOWNLOAD_TOKEN);
        mf.checkMem(Case.MSG, "MSG_PrintToken", MOCK_TOKEN_ENV, "MSG_PrintTokenEnv");
        mf.checkMem(Case.FRM, "MSG_PrintTokenCmdFile", tstPath.toString());
        mf.checkMem(Case.MSG, "MSG_PrintToken", MOCK_TOKEN_USER, "MSG_PrintTokenCmdFile");
    }

    @Test
    public void testRevokeToken() {
        ts.revokeToken(null);
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "MSG_NoRevokableToken");

        ts.revokeToken("");
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "MSG_NoRevokableToken");

        ts.revokeToken(MOCK_TOKEN_DEFAULT);
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "GDSRESTConnector.revokeToken:" + MOCK_TOKEN_DEFAULT);
        mf.checkMem(Case.MSG, "MSG_AcceptRevoke");

        ts.setToken(MOCK_TOKEN_DEFAULT);
        assertEquals(MOCK_TOKEN_DEFAULT, ts.getToken());
        ts.revokeToken(null);
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "GDSRESTConnector.revokeToken:" + MOCK_TOKEN_DEFAULT);
        mf.checkMem(Case.MSG, "MSG_AcceptRevoke");

        ts.makeConn = false;

        ts.revokeToken(null);
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "MSG_NoGDSAddress");

        ts.revokeToken("");
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "MSG_NoGDSAddress");

        ts.revokeToken(MOCK_TOKEN_DEFAULT);
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "MSG_NoGDSAddress");

        ts.setToken(MOCK_TOKEN_DEFAULT);
        assertEquals(MOCK_TOKEN_DEFAULT, ts.getToken());
        ts.revokeToken(null);
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "MSG_NoGDSAddress");
    }

    @Test
    public void testRevokeAllTokens() {
        ts.revokeAllTokens(null);
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "MSG_NoEmail");

        ts.revokeAllTokens("");
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "MSG_NoEmail");

        try {
            ts.revokeAllTokens(MOCK_TOKEN_DEFAULT);
            fail("Expected FailedOperationException.");
        } catch (org.graalvm.component.installer.FailedOperationException ex) {
            assertEquals("ERR_EmailNotValid", ex.getMessage());
        }
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");

        ts.revokeAllTokens(MOCK_EMAIL);
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "GDSRESTConnector.revokeTokens:" + MOCK_EMAIL);
        mf.checkMem(Case.MSG, "MSG_AcceptRevoke");

        ts.makeConn = false;

        ts.revokeAllTokens(null);
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "MSG_NoGDSAddress");

        ts.revokeAllTokens("");
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "MSG_NoGDSAddress");

        ts.revokeAllTokens(MOCK_TOKEN_DEFAULT);
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "MSG_NoGDSAddress");

        ts.revokeAllTokens(MOCK_EMAIL);
        mf.checkMem(Case.MSG, "GDSTokenStorage.makeConnector");
        mf.checkMem(Case.MSG, "MSG_NoGDSAddress");
    }

    private void assertTokenSource(String tkn, Source src) {
        reload();
        assertEquals(tkn, ts.getToken());
        assertEquals(src, ts.getConfSource());
    }

    private static void store(Path dest, String key, String val) throws IOException {
        Properties props = new Properties();
        props.put(key, val);
        try (OutputStream os = Files.newOutputStream(dest)) {
            props.store(os, null);
        }
    }
}
