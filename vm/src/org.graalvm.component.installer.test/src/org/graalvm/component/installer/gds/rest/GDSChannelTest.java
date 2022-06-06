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
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.CommonConstants;
import static org.graalvm.component.installer.CommonConstants.CAP_JAVA_VERSION;
import static org.graalvm.component.installer.CommonConstants.RELEASE_GDS_PRODUCT_ID_KEY;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.Version;
import static org.graalvm.component.installer.gds.rest.GDSChannelTest.TestGDSChannel.MockGDSCatalogStorage.ID;
import org.graalvm.component.installer.MemoryFeedback.Case;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.ProxyResource;
import org.graalvm.component.installer.remote.FileDownloader;
import org.graalvm.component.installer.remote.ProxyConnectionFactory.HttpConnectionException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Rule;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Collections;
import java.util.Set;

/**
 *
 * @author odouda
 */
public class GDSChannelTest extends CommandTestBase {
    @Rule public ProxyResource proxyResource = new ProxyResource();

    TestGDSChannel channel;
    MemoryFeedback mf;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        delegateFeedback(mf = new MemoryFeedback());
        channel = new TestGDSChannel(this, this, this.getLocalRegistry());

        storage.graalInfo.put(CommonConstants.CAP_EDITION, "ee");

        storage.graalInfo.put(CommonConstants.CAP_OS_NAME, "linux");
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, "19.3.6");
        storage.graalInfo.put(CommonConstants.RELEASE_GDS_PRODUCT_ID_KEY, "mockProductId");
        storage.graalInfo.put(CAP_JAVA_VERSION, "11");
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
        assertTrue(mf.isEmpty());
    }

    @Test
    public void testNoUsableComponents() throws Exception {
        Path p = dataFile("data/gdsreleases.json");
        channel.setIndexURL(p.toUri().toURL());
        storage.graalInfo.put(BundleConstants.GRAAL_VERSION, "22.0.0");
        List<ComponentInfo> infos = channel.loadArtifacts(p, Collections.emptyList());
        assertTrue(infos.isEmpty());
        assertTrue(mf.isEmpty());
    }

// @Test
// public void testFilterUpdates() throws Exception {
// Path p = dataFile("data/gdsreleases.json");
// channel.setIndexURL(p.toUri().toURL());
// List<ComponentInfo> infos = channel.loadArtifacts(p, Collections.emptyList());
// assertEquals(infos.toString(), 5, infos.size());
// channel.setAllowUpdates(true);
// infos = channel.loadArtifacts(p, Collections.emptyList());
// assertEquals(infos.toString(), 17, infos.size());
// assertTrue(mf.mem.isEmpty());
// }
//
// @Test
// public void testFailOnNoURL() throws Exception {
// Path p = dataFile("data/gdsreleases.json");
// try {
// channel.loadArtifacts(p, Collections.emptyList());
// fail("Expected NPE from missing URL.");
// } catch (NullPointerException ex) {
// StackTraceElement stackTrace = ex.getStackTrace()[0];
// assertEquals("org.graalvm.component.installer.gds.rest.GDSChannelTest$TestGDSChannel",
// stackTrace.getClassName());
// assertEquals("getConnector", stackTrace.getMethodName());
// }
// assertTrue(mf.mem.isEmpty());
// }
//
// @Test
// public void testLoadComponentStorage() throws Exception {
// Path p = dataFile("data/gdsreleases.json");
// channel.setIndexURL(p.toUri().toURL());
// ComponentStorage store = channel.getStorage();
// assertTrue(mf.mem.isEmpty());
// Collection<String> cids = store.listComponentIDs();
// mf.checkMem(0, Case.FRM, "OLDS_ReleaseFile");
// mf.checkMem(1, Case.MSG, "MSG_UsingFile", "OLDS_ReleaseFile", "");
// assertEquals("Messages size.", 2, mf.mem.size());
// List<ComponentInfo> infos = new ArrayList<>();
// for (String id : cids) {
// infos.addAll(store.loadComponentMetadata(id));
// }
// assertEquals(infos.toString(), 5, infos.size());
// }

    @Test
    public void testInterceptDownloadException() throws Exception {
        String mockUrlString = "http://some.mock.url/";
        URL url = new URL(mockUrlString);
        channel.setIndexURL(url);
        IOException ioExc = new IOException("some Exception.");
        FileDownloader fd = new FileDownloader("something", url, this);
        IOException out = channel.interceptDownloadException(ioExc, fd);
        assertSame(ioExc, out);
        HttpConnectionException httpExc = new HttpConnectionException("my msg", ioExc, false, channel.new MockHttpURLConnection(url));
        out = channel.interceptDownloadException(httpExc, fd);
        assertSame(ioExc, out);
        channel.mockStorage();
        httpExc = new HttpConnectionException("my msg", ioExc, false, channel.new MockHttpURLConnection(channel.getConnector().makeArtifactDownloadURL(ID + 2)));
        out = channel.interceptDownloadException(httpExc, fd);
        assertSame(ioExc, out);
        httpExc = new HttpConnectionException("my msg", ioExc, false, channel.new MockHttpURLConnection(channel.getConnector().makeArtifactDownloadURL(ID)));
        out = channel.interceptDownloadException(httpExc, fd);
        assertSame(ioExc, out);
        channel.respErr = "{code:\"InvalidToken\"}";
        httpExc = new HttpConnectionException("my msg", ioExc, false, channel.new MockHttpURLConnection(channel.getConnector().makeArtifactDownloadURL(ID)));
        try {
            out = channel.interceptDownloadException(httpExc, fd);
            fail("Expected exception.");
        } catch (FailedOperationException ex) {
            // expected ATM
        }
        mf.checkMem(0, Case.MSG, "ERR_WrongToken", new Object[]{null});
        mf.checkMem(1, Case.MSG, "MSG_InputTokenEntry");
        mf.checkMem(2, Case.MSG, "PROMPT_InputTokenEntry");
        mf.checkMem(3, Case.INP, null);
        channel.respErr = "{code:\"InvalidLicenseAcceptance\"}";
        httpExc = new HttpConnectionException("my msg", ioExc, false, channel.new MockHttpURLConnection(channel.getConnector().makeArtifactDownloadURL(ID)));
        out = channel.interceptDownloadException(httpExc, fd);
        assertSame(null, out);
        mf.checkMem(4, Case.MSG, "MSG_EmailAddressEntry");
        mf.checkMem(5, Case.MSG, "PROMPT_EmailAddressEntry");
        mf.checkMem(6, Case.INP, null);
        mf.checkMem(7, Case.FRM, "MSG_YourEmail");
        mf.checkMem(8, Case.MSG, "PROMPT_VerifyEmailAddressEntry", "MSG_YourEmail");
        mf.checkMem(9, Case.INP, null);
        assertEquals(mf.toString(), 10, mf.size());
    }

    final class TestGDSChannel extends GDSChannel {
        GDSRESTConnector conn = null;
        String respErr = "Mock error response.";
        int respCode = 401;

        TestGDSChannel(CommandInput aInput, Feedback aFeedback, ComponentRegistry aRegistry) {
            super(aInput, aFeedback, aRegistry);
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
