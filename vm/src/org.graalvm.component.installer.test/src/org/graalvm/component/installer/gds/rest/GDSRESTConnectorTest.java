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

import org.graalvm.component.installer.MemoryFeedback;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.TestBase;
import org.graalvm.component.installer.URLConnectionFactory;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.gds.rest.GDSRESTConnector.GDSRequester;
import org.graalvm.component.installer.gds.rest.GDSRESTConnectorTest.GDSTestConnector.TestGDSRequester.TestURLConnectionFactory.TestURLConnection;
import org.graalvm.component.installer.MemoryFeedback.Case;
import org.graalvm.component.installer.remote.FileDownloader;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 * @author odouda
 */
public class GDSRESTConnectorTest extends TestBase {
    static final String VERSION_STRING = "21.3.0";
    static final Version TEST_VERSION = Version.fromString(VERSION_STRING);
    static final String TEST_EMAIL = "mock@email.ofc";
    static final String TEST_ID = "D35D04EC5DB2F1F6E0531614000A615C";
    static final String TEST_TOKEN = "RDhCMUFEMUYwMjM1OEQ0MkUwNTMyRDE0MDAwQTAyRjI6NGZjMTFiMzAxMDVkOTAzMDk1NjM4MWJmODY4NWFmODRiYTk2NjEwYQ";
    static final String TEST_TOKEN_OLD = "OldCMUFEMUYwMjM1OEQ0MkUwNTMyRDE0MDAwQTAyRjI6NGZjMTFiMzAxMDVkOTAzMDk1NjM4MWJmODY4NWFmODRiYTk2NjEOld";
    static final String TEST_TOKEN_RESPONSE = "{\n" + "  \"token\": \"" + TEST_TOKEN + "\",\n" + "  \"status\": \"UNVERIFIED\"\n" + "}";
    static final String TEST_JAVA = "11";
    static final String TEST_JDK = "jdk" + TEST_JAVA;
    static final String TEST_TOKEN_REQUEST_ACCEPT = "{\"token\":\"" + TEST_TOKEN_OLD + "\",\"licenseId\":\"gdsreleases.json\",\"type\":\"" + GDSRequester.ACCEPT_LICENSE + "\"}";
    static final String TEST_TOKEN_REQUEST_GENERATE = "{\"email\":\"" + TEST_EMAIL + "\",\"licenseId\":\"gdsreleases.json\",\"type\":\"" + GDSRequester.GENERATE_CONFIG + "\"}";
    static final String TEST_GDS_AGENT = String.format("GVM/%s (arch:%s; os:%s; java:%s)",
                    TEST_VERSION.toString(),
                    SystemUtils.ARCH.sysName(),
                    SystemUtils.OS.sysName(),
                    SystemUtils.getJavaMajorVersion());
    static final String WRONG_RESPONSE = "wrong response";

    final String testURL;
    final GDSTestConnector testConnector;
    final MemoryFeedback mf;

    public GDSRESTConnectorTest() throws IOException {
        testURL = dataFile("data/gdsreleases.json").toUri().toURL().toString();
        testConnector = new GDSTestConnector(testURL, this, TEST_ID, TEST_VERSION);
        delegateFeedback(mf = new MemoryFeedback());
    }

    @Test
    public void testConstructor() {
        assertEquals(testURL, testConnector.baseURL);
        assertEquals(TEST_ID, testConnector.productId);
        assertEquals(TEST_GDS_AGENT, testConnector.gdsUserAgent);
        GDSTestConnector conn = null;
        try {
            conn = new GDSTestConnector(null, this, TEST_ID, TEST_VERSION);
            fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            conn = new GDSTestConnector("", this, TEST_ID, TEST_VERSION);
            fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            conn = new GDSTestConnector("notURL", this, TEST_ID, TEST_VERSION);
            fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            conn = new GDSTestConnector(testURL, null, TEST_ID, TEST_VERSION);
            fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            conn = new GDSTestConnector(testURL, this, null, TEST_VERSION);
            fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            conn = new GDSTestConnector(testURL, this, "", TEST_VERSION);
            fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            conn = new GDSTestConnector(testURL, this, TEST_ID, null);
            fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException ex) {
            // expected
        }
        try {
            conn = new GDSTestConnector(testURL, this, TEST_ID, Version.NO_VERSION);
            fail("IllegalArgumentException expected.");
        } catch (IllegalArgumentException ex) {
            // expected
        }

        assertTrue(conn == null);
        assertTrue(mf.toString(), mf.isEmpty());
    }

    @Test
    public void testObtainArtifacts() {
        testConnector.obtainArtifacts();
        checkBaseParams(GDSRESTConnector.ENDPOINT_ARTIFACTS);

        testConnector.obtainArtifacts(TEST_JAVA);
        List<String> metas = checkBaseParams(GDSRESTConnector.ENDPOINT_ARTIFACTS);
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_JAVA + TEST_JDK));

        mf.checkMem(0, Case.FRM, "OLDS_ReleaseFile");
        mf.checkMem(1, Case.MSG, "MSG_UsingFile", "OLDS_ReleaseFile", "");
        mf.checkMem(2, Case.FRM, "OLDS_ReleaseFile");
        mf.checkMem(3, Case.MSG, "MSG_UsingFile", "OLDS_ReleaseFile", "");
        assertTrue(mf.toString(), mf.size() == 4);
    }

    @Test
    public void testObtainComponents() {
        testConnector.obtainComponents();
        List<String> metas = checkBaseParams(GDSRESTConnector.ENDPOINT_ARTIFACTS);
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_TYPE_COMP));

        testConnector.obtainComponents(VERSION_STRING);
        metas = checkBaseParams(GDSRESTConnector.ENDPOINT_ARTIFACTS);
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_TYPE_COMP));
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_RELEASE + VERSION_STRING));

        mf.checkMem(0, Case.FRM, "OLDS_ReleaseFile");
        mf.checkMem(1, Case.MSG, "MSG_UsingFile", "OLDS_ReleaseFile", "");
        mf.checkMem(2, Case.FRM, "OLDS_ReleaseFile");
        mf.checkMem(3, Case.MSG, "MSG_UsingFile", "OLDS_ReleaseFile", "");
        assertTrue(mf.toString(), mf.size() == 4);
    }

    @Test
    public void testObtainProduct() {
        testConnector.obtainProduct();
        Map<String, List<String>> params = testConnector.testParams;
        testConnector.getParams();
        assertTrue(testConnector.testParams.isEmpty());
        assertEquals(GDSRESTConnector.QUERRY_PRODUCT_NAME, params.get(GDSRESTConnector.QUERRY_DISPLAY_NAME).get(0));
        assertEquals(GDSRESTConnector.QUERRY_LIMIT_VAL, params.get(GDSRESTConnector.QUERRY_LIMIT_KEY).get(0));
        assertEquals(GDSRESTConnector.ENDPOINT_PRODUCTS, testConnector.endpoint);

        mf.checkMem(0, Case.FRM, "OLDS_ReleaseFile");
        mf.checkMem(1, Case.MSG, "MSG_UsingFile", "OLDS_ReleaseFile", "");
        assertTrue(mf.toString(), mf.size() == 2);
    }

    @Test
    public void testObtainReleases() {
        testConnector.obtainReleases();
        List<String> metas = checkBaseParams(GDSRESTConnector.ENDPOINT_ARTIFACTS);
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_TYPE_CORE));

        testConnector.obtainReleases(TEST_JAVA);
        metas = checkBaseParams(GDSRESTConnector.ENDPOINT_ARTIFACTS);
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_TYPE_CORE));
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_JAVA + TEST_JDK));

        mf.checkMem(0, Case.FRM, "OLDS_ReleaseFile");
        mf.checkMem(1, Case.MSG, "MSG_UsingFile", "OLDS_ReleaseFile", "");
        mf.checkMem(2, Case.FRM, "OLDS_ReleaseFile");
        mf.checkMem(3, Case.MSG, "MSG_UsingFile", "OLDS_ReleaseFile", "");
        assertTrue(mf.toString(), mf.size() == 4);
    }

    @Test
    public void testMakeArtifactsURL() {
        String artURL = testConnector.makeArtifactsURL(TEST_JAVA);
        assertTrue(artURL.equals(testURL + GDSRESTConnector.ENDPOINT_ARTIFACTS));
        Map<String, List<String>> params = testConnector.testParams;
        testConnector.getParams();
        assertTrue(testConnector.testParams.isEmpty());
        assertEquals(TEST_ID, params.get(GDSRESTConnector.QUERRY_PRODUCT).get(0));
        List<String> metas = params.get(GDSRESTConnector.QUERRY_METADATA);
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_OS + SystemUtils.OS.get().getName()));
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_ARCH + SystemUtils.ARCH.get().getName()));
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_JAVA + TEST_JDK));

        assertTrue(mf.toString(), mf.isEmpty());
    }

    @Test
    public void testMakeReleaseCatalogURL() {
        String artURL = testConnector.makeReleaseCatalogURL(VERSION_STRING, TEST_JAVA);
        assertTrue(artURL.equals(testURL + GDSRESTConnector.ENDPOINT_ARTIFACTS));
        Map<String, List<String>> params = testConnector.testParams;
        testConnector.getParams();
        assertTrue(testConnector.testParams.isEmpty());
        assertEquals(TEST_ID, params.get(GDSRESTConnector.QUERRY_PRODUCT).get(0));
        List<String> metas = params.get(GDSRESTConnector.QUERRY_METADATA);
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_OS + SystemUtils.OS.get().getName()));
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_ARCH + SystemUtils.ARCH.get().getName()));
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_JAVA + TEST_JDK));
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_RELEASE + VERSION_STRING));

        assertTrue(mf.toString(), mf.isEmpty());
    }

    @Test
    public void testMakeArtifactDownloadURL() {
        String mockId = "mockArtifactId";
        URL artURL = testConnector.makeArtifactDownloadURL(mockId);
        assertEquals(testURL + GDSRESTConnector.ENDPOINT_ARTIFACTS + mockId + GDSRESTConnector.ENDPOINT_DOWNLOAD, artURL.toString());

        assertTrue(mf.toString(), mf.isEmpty());
    }

    @Test
    public void testFillBasics() throws MalformedURLException {
        FileDownloader fd = new FileDownloader(TEST_ID, new URL(testURL), this);
        testConnector.fillBasics(fd);
        Map<String, String> header = fd.getRequestHeaders();
        assertEquals(GDSRESTConnector.HEADER_VAL_GZIP, header.get(GDSRESTConnector.HEADER_ENCODING));
        assertEquals(TEST_GDS_AGENT, header.get(GDSRESTConnector.HEADER_USER_AGENT));

        assertTrue(mf.toString(), mf.isEmpty());
    }

    @Test
    public void testSendVerificationEmail() {
        String tkn = testConnector.sendVerificationEmail(null, testURL, TEST_TOKEN_OLD);
        assertEquals(TEST_TOKEN_OLD, tkn);
        TestURLConnection tc = testConnector.conn;
        assertEquals(TEST_TOKEN_REQUEST_ACCEPT,
                        tc.os.toString());
        assertTrue(tc.getDoOutput());
        Map<String, List<String>> headers = tc.getRequestProperties();
        assertTrue(headers.size() == 2);
        assertEquals(GDSRequester.HEADER_VAL_JSON, headers.get(GDSRequester.HEADER_CONTENT).get(0));
        assertEquals(TEST_GDS_AGENT, headers.get(GDSRESTConnector.HEADER_USER_AGENT).get(0));

        testConnector.conn = null;
        testConnector.is = new ByteArrayInputStream(TEST_TOKEN_RESPONSE.getBytes(UTF_8));

        tkn = testConnector.sendVerificationEmail(TEST_EMAIL, testURL, null);
        assertEquals(TEST_TOKEN, tkn);
        tc = testConnector.conn;
        assertEquals(TEST_TOKEN_REQUEST_GENERATE,
                        tc.os.toString());
        assertTrue(tc.getDoOutput());
        headers = tc.getRequestProperties();
        assertTrue(headers.size() == 2);
        assertEquals(GDSRequester.HEADER_VAL_JSON, headers.get(GDSRequester.HEADER_CONTENT).get(0));
        assertEquals(TEST_GDS_AGENT, headers.get(GDSRESTConnector.HEADER_USER_AGENT).get(0));

        testConnector.conn = null;
        testConnector.is = new ByteArrayInputStream(WRONG_RESPONSE.getBytes(UTF_8));
        try {
            testConnector.sendVerificationEmail(TEST_EMAIL, testURL, null);
            fail("FailedOperationException expected.");
        } catch (FailedOperationException ex) {
            assertEquals("ERR_ResponseBody", ex.getMessage());
            // expected
        }
        tc = testConnector.conn;
        assertEquals(TEST_TOKEN_REQUEST_GENERATE,
                        tc.os.toString());
        assertTrue(tc.getDoOutput());
        headers = tc.getRequestProperties();
        assertTrue(headers.size() == 2);
        assertEquals(GDSRequester.HEADER_VAL_JSON, headers.get(GDSRequester.HEADER_CONTENT).get(0));
        assertEquals(TEST_GDS_AGENT, headers.get(GDSRESTConnector.HEADER_USER_AGENT).get(0));

        assertEquals(mf.toString(), 0, mf.size());
    }

    private List<String> checkBaseParams(String endpoint) {
        Map<String, List<String>> params = testConnector.testParams;
        testConnector.getParams();
        assertTrue(testConnector.testParams.isEmpty());
        assertEquals(GDSRESTConnector.QUERRY_LIMIT_VAL, params.get(GDSRESTConnector.QUERRY_LIMIT_KEY).get(0));
        assertEquals(TEST_ID, params.get(GDSRESTConnector.QUERRY_PRODUCT).get(0));
        List<String> metas = params.get(GDSRESTConnector.QUERRY_METADATA);
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_OS + SystemUtils.OS.get().getName()));
        assertTrue(metas.contains(GDSRESTConnector.QUERRY_ARCH + SystemUtils.ARCH.get().getName()));
        assertEquals(endpoint, testConnector.endpoint);
        return metas;
    }

    static final class GDSTestConnector extends GDSRESTConnector {
        String endpoint;
        Map<String, List<String>> testParams;
        TestURLConnection conn = null;
        ByteArrayInputStream is = new ByteArrayInputStream(TEST_TOKEN_RESPONSE.getBytes(UTF_8));

        GDSTestConnector(String baseURL, Feedback feedback, String productId, Version gvmVersion) {
            super(baseURL, feedback, productId, gvmVersion);
        }

        @Override
        public Map<String, List<String>> getParams() {
            // used to build URL querry then cleared
            Map<String, List<String>> parms = super.getParams();
            testParams = Map.copyOf(parms);
            return Collections.emptyMap();
        }

        @Override
        protected FileDownloader obtain(String endp) {
            this.endpoint = endp;
            // endpoint is appended to baseURL
            return super.obtain("");
        }

        @Override
        GDSRequester getGDSRequester(String acceptLicLink, String licID) throws MalformedURLException {
            return new TestGDSRequester(new URL(acceptLicLink), licID);
        }

        final class TestGDSRequester extends GDSRESTConnector.GDSRequester {
            TestURLConnectionFactory fac = new TestURLConnectionFactory();

            TestGDSRequester(URL licenseUrl, String licID) {
                super(licenseUrl, licID);
            }

            @Override
            URLConnectionFactory getConnectionFactory() throws MalformedURLException {
                return fac;
            }

            final class TestURLConnectionFactory implements URLConnectionFactory {

                @Override
                public URLConnection createConnection(URL u, Configure configCallback) throws IOException {
                    if (conn == null) {
                        conn = new TestURLConnection(u);
                        configCallback.accept(conn);
                    }
                    return conn;
                }

                final class TestURLConnection extends URLConnection {
                    ByteArrayOutputStream os = new ByteArrayOutputStream();

                    TestURLConnection(URL url) {
                        super(url);
                    }

                    @Override
                    public void connect() throws IOException {
                    }

                    @Override
                    public OutputStream getOutputStream() throws IOException {
                        return os;
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        return is;
                    }
                }
            }
        }
    }
}
