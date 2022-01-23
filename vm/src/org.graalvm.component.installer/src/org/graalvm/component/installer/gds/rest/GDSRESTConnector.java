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

import com.oracle.truffle.tools.utils.json.JSONException;
import com.oracle.truffle.tools.utils.json.JSONObject;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import static org.graalvm.component.installer.SystemUtils.getJavaMajorVersion;
import org.graalvm.component.installer.remote.FileDownloader;
import org.graalvm.component.installer.URLConnectionFactory;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.remote.ProxyConnectionFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class serves as GDS REST Endpoint connector for communication with service.
 *
 * @author odouda
 */
public class GDSRESTConnector {
    private static final String ENDPOINT_ARTIFACTS = "artifacts/";
    private static final String ENDPOINT_DOWNLOAD = "/content";
    private static final String ENDPOINT_PRODUCTS = "products";
    private static final String ENDPOINT_LICENSE = "licenses/";
    private static final String ENDPOINT_LICENSE_ACCEPT = "licenseAcceptance/";

    private static final String QUERRY_DISPLAY_NAME = "displayName";
    private static final String QUERRY_PRODUCT_NAME = "GraalVM";
    private static final String QUERRY_PRODUCT = "productId";
    private static final String QUERRY_METADATA = "metadata";
    private static final String QUERRY_ARCH = "arch:";
    private static final String QUERRY_OS = "os:";
    private static final String QUERRY_TYPE = "type:";
    private static final String QUERRY_TYPE_CORE = QUERRY_TYPE + "core";
    private static final String QUERRY_TYPE_COMP = QUERRY_TYPE + "component";
    private static final String QUERRY_RELEASE = "release:";
    private static final String QUERRY_JAVA = "java:";
    private static final String QUERRY_LIMIT_KEY = "limit";
    private static final String QUERRY_LIMIT_VAL = "1000";

    private static final String HEADER_ENCODING = "Accept-Encoding";
    private static final String HEADER_VAL_GZIP = "gzip";
    private static final String HEADER_USER_AGENT = "User-Agent";
    private final String gdsUserAgent;

    private final String baseURL;
    private final Feedback feedback;
    private final Map<String, List<String>> params = new HashMap<>();

    private final String productId;

    public GDSRESTConnector(String baseURL, Feedback feedback, String productId, Version gvmVersion) {
        this.baseURL = baseURL;
        if (baseURL == null || baseURL.isEmpty()) {
            throw new IllegalArgumentException("Base URL can't be empty.");
        }
        this.feedback = feedback.withBundle(GDSRESTConnector.class);
        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("Product ID can't be empty.");
        }
        if (gvmVersion == null || gvmVersion == Version.NO_VERSION) {
            throw new IllegalArgumentException("Version can't be empty.");
        }
        this.productId = productId;
        this.gdsUserAgent = String.format("GVM/%s (arch:%s; os:%s; java:%s)",
                        gvmVersion.toString(),
                        SystemUtils.ARCH.sysName(),
                        SystemUtils.OS.sysName(),
                        getJavaMajorVersion());
    }

    public Path obtainComponents() {
        fillMetaComponent();
        return obtainArtifacts();
    }

    public Path obtainComponents(String releaseVersion) {
        fillMetaRelease(releaseVersion);
        return obtainComponents();
    }

    public Path obtainReleases(String javaVersion) {
        fillMetaJava(javaVersion);
        return obtainReleases();
    }

    public Path obtainReleases() {
        fillMetaCore();
        return obtainArtifacts();
    }

    public Path obtainArtifacts(String javaVersion) {
        fillMetaJava(javaVersion);
        return obtainArtifacts();
    }

    public Path obtainArtifacts() {
        fillArtifacts();
        return obtain(ENDPOINT_ARTIFACTS);
    }

    public Path obtainProduct() {
        fillDisplayName();
        return obtain(ENDPOINT_PRODUCTS);
    }

    public String sendVerificationEmail(String email, String licAddr, String oldToken) {
        String licID = licAddr.substring(licAddr.lastIndexOf("/") + 1);
        String acceptLicLink = baseURL + ENDPOINT_LICENSE_ACCEPT;
        String token = null;
        try {
            TokenRequester tr = new TokenRequester(new URL(acceptLicLink), licID, email, oldToken);
            token = tr.obtainToken();
        } catch (IOException ex) {
            throw feedback.failure("ERR_VerificationEmail", ex, email);
        }
        return token;
    }

    public String makeLicenseURL(String licenseId) {
        return baseURL + ENDPOINT_LICENSE + licenseId;
    }

    public String makeArtifactsURL(String javaVersion) {
        fillArtifacts();
        fillMetaJava(javaVersion);
        String out = SystemUtils.buildUrlStringWithParameters(baseURL + ENDPOINT_ARTIFACTS, params);
        params.clear();
        return out;
    }

    public String makeReleaseCatalogURL(String releaseVersion, String javaVersion) {
        fillArtifacts();
        fillMetaJava(javaVersion);
        fillMetaRelease(releaseVersion);
        String out = SystemUtils.buildUrlStringWithParameters(baseURL + ENDPOINT_ARTIFACTS, params);
        params.clear();
        return out;
    }

    URL makeArtifactDownloadURL(String id) {
        String url = baseURL + ENDPOINT_ARTIFACTS + id + ENDPOINT_DOWNLOAD;
        try {
            return new URL(url);
        } catch (MalformedURLException ex) {
            feedback.error("ERR_MalformedArtifactUrl", ex, url);
        }
        return null;
    }

    private Path obtain(String endpoint) {
        addParam(QUERRY_LIMIT_KEY, QUERRY_LIMIT_VAL);
        try {
            FileDownloader dn = new FileDownloader(
                            feedback.l10n("OLDS_ReleaseFile"),
                            new URL(SystemUtils.buildUrlStringWithParameters(baseURL + endpoint, params)),
                            feedback);
            fillBasics(dn);
            dn.download();
            return dn.getLocalFile().toPath();
        } catch (IOException ex) {
            throw feedback.failure("ERR_CouldNotLoadGDS", ex, baseURL, ex.getLocalizedMessage());
        } finally {
            params.clear();
        }
    }

    private void fillArtifacts() {
        fillProductId();
        fillMetaArchAndOS();
    }

    private void fillProductId() {
        addParam(QUERRY_PRODUCT, productId);
    }

    private void fillDisplayName() {
        addParam(QUERRY_DISPLAY_NAME, QUERRY_PRODUCT_NAME);
    }

    private void fillMetaArchAndOS() {
        addParam(QUERRY_METADATA, QUERRY_ARCH + SystemUtils.ARCH.get().getName());
        addParam(QUERRY_METADATA, QUERRY_OS + SystemUtils.OS.get().getName());
    }

    private void fillMetaCore() {
        addParam(QUERRY_METADATA, QUERRY_TYPE_CORE);
    }

    private void fillMetaComponent() {
        addParam(QUERRY_METADATA, QUERRY_TYPE_COMP);
    }

    private void fillMetaRelease(String releaseVersion) {
        addParam(QUERRY_METADATA, QUERRY_RELEASE + releaseVersion);
    }

    private void fillMetaJava(String javaVersion) {
        addParam(QUERRY_METADATA, QUERRY_JAVA + "jdk" + javaVersion);
    }

    private void addParam(String key, String value) {
        params.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    public void fillBasics(FileDownloader fd) {
        fd.addRequestHeader(HEADER_USER_AGENT, gdsUserAgent);
        fd.addRequestHeader(HEADER_ENCODING, HEADER_VAL_GZIP);
    }

    private class TokenRequester {
        private static final String GENERATE_TOKEN = "GENERATE_TOKEN_AND_ACCEPT_LICENSE";
        private static final String ACCEPT_LICENSE = "ACCEPT_LICENSE";

        private static final String HEADER_CONTENT = "Content-Type";
        private static final String HEADER_VAL_JSON = "application/json";

        private static final String REQUEST_BODY = "{\"email\":\"%s\",\"licenseId\":\"%s\",\"type\":\"%s\"}";

        private static final String JSON_TOKEN = "token";

        private final URL licenseUrl;
        private final String licID;
        private final String email;
        private final String oldToken;
        private URLConnectionFactory factory;

        TokenRequester(URL licenseUrl, String licID, String email, String oldToken) {
            this.licenseUrl = licenseUrl;
            this.licID = licID;
            this.email = email;
            this.oldToken = oldToken;
        }

        public String obtainToken() throws IOException {
            String token = null;
            URLConnection connector = getConnectionFactory().createConnection(licenseUrl, this::configCallBack);
            String response = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connector.getInputStream()))) {
                response = reader.lines().reduce("", (s1, s2) -> s1 + s2);
                token = new JSONObject(response).getString(JSON_TOKEN);
            } catch (JSONException ex) {
                if (response != null && !response.isBlank()) {
                    feedback.error("ERR_ResponseBody", ex, response);
                }
            }
            return oldToken == null && token != null && !token.isBlank() ? token : oldToken;
        }

        private String generateRequestBody() {
            return String.format(REQUEST_BODY,
                            email,
                            licID,
                            oldToken == null ? GENERATE_TOKEN : ACCEPT_LICENSE);
        }

        private void configCallBack(URLConnection connector) {
            connector.addRequestProperty(HEADER_USER_AGENT, gdsUserAgent);
            connector.setDoOutput(true);
            connector.setRequestProperty(HEADER_CONTENT, HEADER_VAL_JSON);
            try (OutputStreamWriter out = new OutputStreamWriter(connector.getOutputStream())) {
                String body = generateRequestBody();
                out.append(body);
            } catch (IOException ex) {
                // swallow
            }
        }

        private URLConnectionFactory getConnectionFactory() throws MalformedURLException {
            if (factory == null) {
                factory = new ProxyConnectionFactory(feedback, new URL(baseURL));
            }
            return factory;
        }
    }
}
