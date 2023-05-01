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
import com.oracle.truffle.tools.utils.json.JSONException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.CommandTestBase;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.CommonConstants;
import static org.graalvm.component.installer.CommonConstants.CAP_JAVA_VERSION;
import static org.graalvm.component.installer.CommonConstants.RELEASE_GDS_PRODUCT_ID_KEY;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.Version;
import static org.graalvm.component.installer.gds.rest.GDSChannelTest.TestGDSChannel.MockGDSCatalogStorage.ID;
import org.graalvm.component.installer.MemoryFeedback.Case;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.model.ComponentStorage;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.remote.FileDownloader;
import org.graalvm.component.installer.remote.ProxyConnectionFactory.HttpConnectionException;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertFalse;

/**
 *
 * @author odouda
 */
public class GDSChannelTest extends CommandTestBase {
    private static final String MOCK_TOKEN = "MOCK_TOKEN";
    private static final String HEADER_DOWNLOAD_CONFIG = "x-download-token";
    @Rule public TestName name = new TestName();
    @Rule public ProxyResource proxyResource = new ProxyResource();

    TestGDSChannel channel;
    MemoryFeedback mf;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        delegateFeedback(mf = new MemoryFeedback());

        storage.graalInfo.put(CommonConstants.CAP_EDITION, "ee");

        storage.graalInfo.put(CommonConstants.CAP_OS_NAME, "linux");
        storage.graalInfo.put(CommonConstants.CAP_OS_ARCH, "amd64");
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, "22.1.0");
        storage.graalInfo.put(CommonConstants.RELEASE_GDS_PRODUCT_ID_KEY, "mockProductId");
        storage.graalInfo.put(CAP_JAVA_VERSION, "11");

        refreshChannel();
    }

    @After
    public void tearDown() {
        assertTrue(name.getMethodName() + ": " + mf.toString(), mf.isEmpty());
    }

    private void refreshChannel() {
        this.localRegistry = null;
        channel = new TestGDSChannel(this, this, this.getLocalRegistry());
    }

    private void refreshChannel(Path p) throws MalformedURLException {
        refreshChannel();
        channel.setIndexURL(p.toUri().toURL());
    }

    @Test
    public void testWrongJson() throws Exception {
        Path p = dataFile("../data/rel-no-bases.json");
        channel.setIndexURL(p.toUri().toURL());
        try {
            channel.loadArtifacts(p, Collections.emptyList());
            fail("JSONexception expected.");
        } catch (JSONException ex) {
            // expected
        }
    }

    @Test
    public void testNoUsableComponents() throws Exception {
        Path p = dataFile("data/gdsreleases.json");
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, "22.0.0");
        refreshChannel(p);
        List<ComponentInfo> infos = channel.loadArtifacts(p, Collections.emptyList());
        assertTrue(infos.isEmpty());
    }

    @Test
    public void testFilterUpdates() throws Exception {
        Path p = dataFile("data/gdsreleases.json");
        channel.setIndexURL(p.toUri().toURL());
        tstUpdates(p, 9, 18);
        storage.graalInfo.put(CommonConstants.CAP_OS_NAME, "macos");
        refreshChannel(p);
        tstUpdates(p, 9, 18);
        storage.graalInfo.put(CommonConstants.CAP_OS_NAME, "windows");
        refreshChannel(p);
        tstUpdates(p, 5, 10);
        storage.graalInfo.put(CommonConstants.CAP_OS_ARCH, "aarch64");
        storage.graalInfo.put(CommonConstants.CAP_OS_NAME, "linux");
        refreshChannel(p);
        tstUpdates(p, 7, 14);
        storage.graalInfo.put(CommonConstants.CAP_OS_NAME, "macos");
        refreshChannel(p);
        tstUpdates(p, 0, 0);
        storage.graalInfo.put(CommonConstants.CAP_OS_NAME, "windows");
        refreshChannel(p);
        tstUpdates(p, 0, 0);
    }

    private void tstUpdates(Path p, int noUpdates, int allowedUpdates) throws IOException {
        channel.setAllowUpdates(false);
        checkArtifactsCount(p, noUpdates);
        channel.setAllowUpdates(true);
        checkArtifactsCount(p, allowedUpdates);
    }

    private void checkArtifactsCount(Path p, int count) throws IOException {
        List<ComponentInfo> infos = channel.loadArtifacts(p, Collections.emptyList());
        assertEquals(infos.toString(), count, infos.size());
    }

    @Test
    public void testFailOnNoURL() throws Exception {
        Path p = dataFile("data/gdsreleases.json");
        try {
            channel.loadArtifacts(p, Collections.emptyList());
            fail("Expected NPE from missing URL.");
        } catch (NullPointerException ex) {
            StackTraceElement stackTrace = ex.getStackTrace()[0];
            assertEquals("org.graalvm.component.installer.gds.rest.GDSChannelTest$TestGDSChannel",
                            stackTrace.getClassName());
            assertEquals("getConnector", stackTrace.getMethodName());
        }
    }

    @Test
    public void testLoadComponentStorage() throws Exception {
        Path p = dataFile("data/gdsreleases.json");
        channel.setIndexURL(p.toUri().toURL());
        ComponentStorage store = channel.getStorage();
        assertTrue(mf.isEmpty());
        Collection<String> cids = store.listComponentIDs();
        mf.checkMem(Case.FRM, "OLDS_ReleaseFile");
        mf.checkMem(Case.MSG, "MSG_UsingFile", "OLDS_ReleaseFile", "");
        List<ComponentInfo> infos = new ArrayList<>();
        for (String id : cids) {
            infos.addAll(store.loadComponentMetadata(id));
        }
        assertEquals(infos.toString(), 9, infos.size());
    }

    @Test
    public void testInterceptDownloadException() throws Exception {
        String mockUrlString = "http://some.mock.url/";
        URL url = SystemUtils.toURL(mockUrlString);
        channel.setIndexURL(url);
        IOException ioExc = new IOException("some Exception.");
        FileDownloader fd = new FileDownloader("something", url, this);
        try {
            fd.download();
        } catch (Throwable t) {
        }
        mf.checkMem(Case.MSG, "MSG_Downloading", "something", url.getHost());
        channel.mockStorage();

        channel.respErr = "{code:\"UnverifiedToken\"}";
        mf.nextInput("something");
        HttpConnectionException httpExc = new HttpConnectionException("my msg", ioExc, false, channel.new MockHttpURLConnection(channel.getConnector().makeArtifactDownloadURL(ID)));
        IOException out = channel.interceptDownloadException(httpExc, fd);
        assertSame(null, out);
        mf.checkMem(Case.MSG, "ERR_InvalidToken", new Object[]{null});
        mf.checkMem(Case.INP, "something");

        channel.respErr = "{code:\"InvalidToken\"}";
        channel.tokStore.setToken(MOCK_TOKEN);
        mf.nextInput("token");
        httpExc = new HttpConnectionException("my msg", ioExc, false, channel.new MockHttpURLConnection(channel.getConnector().makeArtifactDownloadURL(ID)));
        out = channel.interceptDownloadException(httpExc, fd);
        assertSame(null, out);
        assertEquals("token", channel.tokStore.getToken());
        mf.checkMem(Case.MSG, "ERR_WrongToken", MOCK_TOKEN);
        mf.checkMem(Case.MSG, "MSG_InputTokenEntry");
        mf.checkMem(Case.MSG, "PROMPT_InputTokenEntry");
        mf.checkMem(Case.INP, "token");
        mf.checkMem(Case.MSG, "MSG_ObtainedToken", "token");

        mf.nextInput(null);
        mf.nextInput("email@dot.com");
        out = channel.interceptDownloadException(httpExc, fd);
        assertSame(null, out);
        assertEquals(GDSFileConnector.MOCK_TOKEN_NEW, channel.tokStore.getToken());
        mf.checkMem(Case.MSG, "ERR_WrongToken", "token");
        mf.checkMem(Case.MSG, "MSG_InputTokenEntry");
        mf.checkMem(Case.MSG, "PROMPT_InputTokenEntry");
        mf.checkMem(Case.INP, null);
        mf.checkMem(Case.MSG, "MSG_EmailAddressEntry");
        mf.checkMem(Case.MSG, "PROMPT_EmailAddressEntry");
        mf.checkMem(Case.INP, "email@dot.com");
        mf.checkMem(Case.MSG, "MSG_ObtainedToken", GDSFileConnector.MOCK_TOKEN_NEW);
        mf.checkMem(Case.MSG, "PROMPT_VerifyEmailAddressEntry", "email@dot.com");
        mf.checkMem(Case.INP, null);

        channel.respErr = "{code:\"InvalidLicenseAcceptance\"}";
        httpExc = new HttpConnectionException("my msg", ioExc, false, channel.new MockHttpURLConnection(channel.getConnector().makeArtifactDownloadURL(ID)));
        out = channel.interceptDownloadException(httpExc, fd);
        assertSame(null, out);
        mf.checkMem(Case.FRM, "MSG_YourEmail");
        mf.checkMem(Case.MSG, "PROMPT_VerifyEmailAddressEntry", "MSG_YourEmail");
        mf.checkMem(Case.INP, null);

        channel.respErr = "invalid json";
        httpExc = new HttpConnectionException("my msg", ioExc, false, channel.new MockHttpURLConnection(channel.getConnector().makeArtifactDownloadURL(ID)));
        out = channel.interceptDownloadException(httpExc, fd);
        assertSame(ioExc, out);

        httpExc = new HttpConnectionException("my msg", ioExc, false, channel.new MockHttpURLConnection(channel.getConnector().makeArtifactDownloadURL(ID + 2)));
        out = channel.interceptDownloadException(httpExc, fd);
        assertSame(ioExc, out);

        httpExc = new HttpConnectionException("my msg", ioExc, false, channel.new MockHttpURLConnection(url));
        out = channel.interceptDownloadException(httpExc, fd);
        assertSame(ioExc, out);

        channel.respCode = 300;
        httpExc = new HttpConnectionException("my msg", ioExc, false, channel.new MockHttpURLConnection(channel.getConnector().makeArtifactDownloadURL(ID)));
        out = channel.interceptDownloadException(httpExc, fd);
        assertSame(ioExc, out);

        out = channel.interceptDownloadException(ioExc, fd);
        assertSame(ioExc, out);
    }

    @Test
    public void testConfigureDownloader() throws MalformedURLException {
        String mockUrlString = "http://some.mock.url/";
        URL url = SystemUtils.toURL(mockUrlString);
        channel.setIndexURL(url);
        channel.tokStore.setToken(MOCK_TOKEN);
        FileDownloader fd = new FileDownloader("something", url, this);
        ComponentInfo ci = new ComponentInfo("ID", "name", "1.0.0");
        channel.configureDownloader(ci, fd);
        Map<String, String> headers = fd.getRequestHeaders();
        assertEquals(MOCK_TOKEN, headers.get(HEADER_DOWNLOAD_CONFIG));
        assertEquals(3, headers.size());

        fd = new FileDownloader("something", url, this);
        ci.setImplicitlyAccepted(true);
        channel.configureDownloader(ci, fd);
        headers = fd.getRequestHeaders();
        assertEquals(null, headers.get(HEADER_DOWNLOAD_CONFIG));
        assertEquals(2, headers.size());
    }

    @Test
    public void testNeedToken() {
        ComponentInfo ci = new ComponentInfo("ID", "name", "1.0.0");
        assertTrue(channel.needToken(ci));

        ci.setImplicitlyAccepted(true);
        assertFalse(channel.needToken(ci));

        ci.setImplicitlyAccepted(false);
        assertTrue(channel.needToken(ci));
    }

    final class TestGDSChannel extends GDSChannel {
        GDSRESTConnector conn = null;
        String respErr = "Mock error response.";
        int respCode = 401;
        TestGDSTokenStorage tokStore;

        TestGDSChannel(CommandInput aInput, Feedback aFeedback, ComponentRegistry aRegistry) {
            super(aInput, aFeedback, aRegistry);
            setTokenStorage(tokStore = new TestGDSTokenStorage(aFeedback, aInput) {
                @Override
                public void save() throws IOException {
                }
            });
        }

        @Override
        GDSRESTConnector getConnector() {
            return conn == null ? conn = new GDSFileConnector(
                            channel.getIndexURL().toString(),
                            GDSChannelTest.this,
                            getLocalRegistry().getGraalCapabilities()
                                            .get(RELEASE_GDS_PRODUCT_ID_KEY),
                            getLocalRegistry().getGraalVersion()) : conn;
        }

        public void mockStorage() {
            storage = new MockGDSCatalogStorage(localRegistry, fb, getIndexURL(), Collections.emptyList());
        }

        final class MockGDSCatalogStorage extends GDSCatalogStorage {
            static final String ID = "id";
            final ComponentInfo ci = new ComponentInfo(ID, ID, Version.NO_VERSION);

            MockGDSCatalogStorage(ComponentRegistry localRegistry, Feedback feedback, URL baseURL, Collection<ComponentInfo> artifacts) {
                super(localRegistry, feedback, baseURL, artifacts);
                ci.setRemoteURL(getConnector().makeArtifactDownloadURL(ID));
                ci.setLicensePath(getConnector().makeLicenseURL(ID));
            }

            @Override
            public Set<String> listComponentIDs() throws IOException {
                return Collections.singleton(ID);
            }

            @Override
            public Set<ComponentInfo> loadComponentMetadata(String id) throws IOException {
                assertSame(ID, id);
                return Collections.singleton(ci);
            }
        }

        final class MockHttpURLConnection extends HttpURLConnection {
            MockHttpURLConnection(URL url) {
                super(url);
            }

            @Override
            public void disconnect() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public boolean usingProxy() {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public void connect() throws IOException {
                throw new UnsupportedOperationException("Not supported yet.");
            }

            @Override
            public InputStream getErrorStream() {
                return new ByteArrayInputStream(respErr.getBytes(UTF_8));
            }

            @Override
            public int getResponseCode() throws IOException {
                return respCode;
            }
        }
    }
}
