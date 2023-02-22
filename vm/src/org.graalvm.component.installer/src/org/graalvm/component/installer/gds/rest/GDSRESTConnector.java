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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class serves as GDS REST Endpoint connector for communication with service.
 *
 * @author odouda
 */
class GDSRESTConnector {
    static final String ENDPOINT_ARTIFACTS = "artifacts/";
    static final String ENDPOINT_DOWNLOAD = "/content";
    static final String ENDPOINT_PRODUCTS = "products";
    static final String ENDPOINT_LICENSE = "licenses/";
    static final String ENDPOINT_LICENSE_ACCEPT = "licenseAcceptance/";
    static final String ENDPOINT_REVOKE_TOKEN = "tokenRequests";

    static final String QUERRY_DISPLAY_NAME = "displayName";
    static final String QUERRY_PRODUCT_NAME = "GraalVM";
    static final String QUERRY_PRODUCT = "productId";
    static final String QUERRY_METADATA = "metadata";
    static final String QUERRY_ARCH = "arch:";
    static final String QUERRY_OS = "os:";
    static final String QUERRY_TYPE = "type:";
    static final String QUERRY_TYPE_CORE = QUERRY_TYPE + "core";
    static final String QUERRY_TYPE_COMP = QUERRY_TYPE + "component";
    static final String QUERRY_RELEASE = "release:";
    static final String QUERRY_JAVA = "java:";
    static final String QUERRY_LIMIT_KEY = "limit";
    static final String QUERRY_LIMIT_VAL = "1000";

    public static final String HEADER_VAL_GZIP = "gzip";
    static final String HEADER_ENCODING = "Accept-Encoding";
    static final String HEADER_USER_AGENT = "User-Agent";
    final String gdsUserAgent;

    final String baseURL;
    final Feedback feedback;
    final Map<String, List<String>> params = new HashMap<>();

    final String productId;

    GDSRESTConnector(String baseURL, Feedback feedback, String productId, Version gvmVersion) {
        if (baseURL == null || baseURL.isBlank()) {
            throw new IllegalArgumentException("Base URL String can't be empty.");
        }
        URL url = null;
        try {
            url = SystemUtils.toURL(baseURL);
        } catch (MalformedURLException ex) {
            throw new IllegalArgumentException("Base URL String must be convertible to URL.");
        }
        this.baseURL = url.toString();
        if (feedback == null) {
            throw new IllegalArgumentException("Feedback can't be null.");
        }
        this.feedback = feedback.withBundle(GDSRESTConnector.class);
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("Product ID can't be empty.");
        }
        this.productId = productId;
        if (gvmVersion == null || gvmVersion == Version.NO_VERSION) {
            throw new IllegalArgumentException("Version can't be empty.");
        }
        this.gdsUserAgent = String.format("GVM/%s (arch:%s; os:%s; java:%s)",
                        gvmVersion.toString(),
                        SystemUtils.ARCH.sysName(),
                        SystemUtils.OS.sysName(),
                        SystemUtils.getJavaMajorVersion());
    }

    Map<String, List<String>> getParams() {
        return params;
    }

    public void revokeToken(String token) {
        try {
            GDSRequester tr = getGDSRequester(baseURL + ENDPOINT_REVOKE_TOKEN, token);
            tr.revokeToken(token);
        } catch (IOException ex) {
            throw feedback.failure("ERR_VerificationEmail", ex, feedback.l10n("MSG_YourEmail"));
        }
    }

    public void revokeTokens(String email) {
        try {
            GDSRequester tr = getGDSRequester(baseURL + ENDPOINT_REVOKE_TOKEN, email);
            tr.revokeTokens(email);
        } catch (IOException ex) {
            throw feedback.failure("ERR_VerificationEmail", ex, email);
        }
    }

    public FileDownloader obtainComponents() {
        fillMetaComponent();
        return obtainArtifacts();
    }

    public FileDownloader obtainComponents(String releaseVersion) {
        fillMetaRelease(releaseVersion);
        return obtainComponents();
    }

    public FileDownloader obtainReleases(String javaVersion) {
        fillMetaJava(javaVersion);
        return obtainReleases();
    }

    public FileDownloader obtainReleases() {
        fillMetaCore();
        return obtainArtifacts();
    }

    public FileDownloader obtainArtifacts(String javaVersion) {
        fillMetaJava(javaVersion);
        return obtainArtifacts();
    }

    public FileDownloader obtainArtifacts() {
        fillArtifacts();
        return obtain(ENDPOINT_ARTIFACTS);
    }

    public FileDownloader obtainProduct() {
        fillDisplayName();
        return obtain(ENDPOINT_PRODUCTS);
    }

    public String sendVerificationEmail(String email, String licAddr, String config) {
        assert (SystemUtils.nonBlankString(email) && config == null) || (SystemUtils.nonBlankString(config) && email == null);
        String licID = licAddr.substring(licAddr.lastIndexOf("/") + 1);
        String acceptLicLink = baseURL + ENDPOINT_LICENSE_ACCEPT;
        String token = config;
        try {
            GDSRequester tr = getGDSRequester(acceptLicLink, licID);
            token = email != null ? tr.obtainConfig(email) : tr.acceptLic(config);
        } catch (IOException ex) {
            throw feedback.failure("ERR_VerificationEmail", ex, email == null ? feedback.l10n("MSG_YourEmail") : email);
        }
        return token;
    }

    public String makeLicenseURL(String licenseId) {
        return baseURL + ENDPOINT_LICENSE + licenseId;
    }

    public String makeArtifactsURL(String javaVersion) {
        fillArtifacts();
        fillMetaJava(javaVersion);
        String out = SystemUtils.buildUrlStringWithParameters(baseURL + ENDPOINT_ARTIFACTS, getParams());
        params.clear();
        return out;
    }

    public String makeReleaseCatalogURL(String releaseVersion, String javaVersion) {
        fillArtifacts();
        fillMetaJava(javaVersion);
        fillMetaRelease(releaseVersion);
        String out = SystemUtils.buildUrlStringWithParameters(baseURL + ENDPOINT_ARTIFACTS, getParams());
        params.clear();
        return out;
    }

    URL makeArtifactDownloadURL(String id) {
        String url = baseURL + ENDPOINT_ARTIFACTS + id + ENDPOINT_DOWNLOAD;
        try {
            return SystemUtils.toURL(url);
        } catch (MalformedURLException ex) {
            feedback.error("ERR_MalformedArtifactUrl", ex, url);
        }
        return null;
    }

    protected FileDownloader obtain(String endpoint) {
        addParam(QUERRY_LIMIT_KEY, QUERRY_LIMIT_VAL);
        try {
            FileDownloader dn = new FileDownloader(
                            feedback.l10n("OLDS_ReleaseFile"),
                            SystemUtils.toURL(SystemUtils.buildUrlStringWithParameters(baseURL + endpoint, getParams())),
                            feedback);
            fillBasics(dn);
            dn.download();
            return dn;
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

    GDSRequester getGDSRequester(String acceptLicLink, String licID) throws MalformedURLException {
        return new GDSRequester(SystemUtils.toURL(acceptLicLink), licID);
    }

    class GDSRequester {
        static final String GENERATE_CONFIG = "GENERATE_TOKEN_AND_ACCEPT_LICENSE";
        static final String ACCEPT_LICENSE = "ACCEPT_LICENSE_USING_TOKEN";
        static final String REVOKE_TOKEN = "REVOKE";

        static final String HEADER_CONTENT = "Content-Type";
        static final String HEADER_VAL_JSON = "application/json";

        static final String REQUEST_CONFIG_BODY = "{\"type\":\"%s\",\"email\":\"%s\",\"licenseId\":\"%s\"}";
        static final String REQUEST_ACCEPT_BODY = "{\"type\":\"%s\",\"token\":\"%s\",\"licenseId\":\"%s\"}";
        static final String REQUEST_REVOKE_BODY = "{\"type\":\"%s\",\"downloadToken\":\"%s\"}";
        static final String REQUEST_REVOKE_ALL_BODY = "{\"type\":\"%s\",\"email\":\"%s\"}";

        static final String JSON_CONFIG = "token";

        final URL url;
        final String id;
        URLConnectionFactory factory;

        GDSRequester(URL url, String id) {
            this.url = url;
            this.id = id;
        }

        public String obtainConfig(String email) throws IOException {
            String config = null;
            URLConnection connector = getConnectionFactory().createConnection(url, createConfigCallBack(Request.GENERATE, email));
            String response = null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connector.getInputStream()))) {
                response = reader.lines().collect(Collectors.joining());
                config = new JSONObject(response).getString(JSON_CONFIG);
            } catch (JSONException ex) {
                throw feedback.failure("ERR_ResponseBody", ex, response);
            }
            if (!SystemUtils.nonBlankString(config)) {
                throw feedback.failure("ERR_ResponseBody", null, response);
            }
            return config;
        }

        public String acceptLic(String config) throws IOException {
            return connect(Request.ACCEPT, config);
        }

        public void revokeTokens(String email) throws IOException {
            connect(Request.REVOKE_ALL, email);
        }

        public void revokeToken(String token) throws IOException {
            connect(Request.REVOKE, token);
        }

        private String connect(Request req, String content) throws IOException {
            getConnectionFactory().createConnection(url, createConfigCallBack(req, content)).connect();
            return content;
        }

        private String generateRequestBody(Request req, String content) {
            return String.format(getRequestFormat(req),
                            getRequestName(req),
                            content,
                            id);
        }

        private String getRequestFormat(Request req) {
            switch (req) {
                case REVOKE_ALL:
                    return REQUEST_REVOKE_ALL_BODY;
                case REVOKE:
                    return REQUEST_REVOKE_BODY;
                case ACCEPT:
                    return REQUEST_ACCEPT_BODY;
                case GENERATE:
                    return REQUEST_CONFIG_BODY;
            }
            return null;
        }

        private String getRequestName(Request req) {
            switch (req) {
                case REVOKE_ALL:
                case REVOKE:
                    return REVOKE_TOKEN;
                case ACCEPT:
                    return ACCEPT_LICENSE;
                case GENERATE:
                    return GENERATE_CONFIG;
            }
            return null;
        }

        private URLConnectionFactory.Configure createConfigCallBack(Request req, String content) {
            return (URLConnection connector) -> {
                connector.addRequestProperty(HEADER_USER_AGENT, gdsUserAgent);
                connector.setDoOutput(true);
                connector.setRequestProperty(HEADER_CONTENT, HEADER_VAL_JSON);
                try (OutputStreamWriter out = new OutputStreamWriter(connector.getOutputStream())) {
                    out.append(generateRequestBody(req, content));
                }
            };
        }

        URLConnectionFactory getConnectionFactory() throws MalformedURLException {
            if (factory == null) {
                factory = new ProxyConnectionFactory(feedback, SystemUtils.toURL(baseURL));
            }
            return factory;
        }
    }

    enum Request {
        GENERATE,
        ACCEPT,
        REVOKE,
        REVOKE_ALL
    }
}
