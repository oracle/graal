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

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONException;
import com.oracle.truffle.tools.utils.json.JSONObject;
import com.oracle.truffle.tools.utils.json.JSONTokener;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.CommonConstants;
import static org.graalvm.component.installer.CommonConstants.RELEASE_GDS_PRODUCT_ID_KEY;
import org.graalvm.component.installer.ComponentInstaller;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.IncompatibleException;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.SystemUtils.ARCH;
import org.graalvm.component.installer.SystemUtils.OS;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.gds.GdsCommands;
import org.graalvm.component.installer.gds.GraalChannelBase;
import org.graalvm.component.installer.gds.MailStorage;
import static org.graalvm.component.installer.gds.rest.GDSRESTConnector.HEADER_VAL_GZIP;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.model.ComponentStorage;
import org.graalvm.component.installer.persist.MetadataLoader;
import org.graalvm.component.installer.persist.MetadataLoaderAdapter;
import org.graalvm.component.installer.remote.FileDownloader;
import org.graalvm.component.installer.remote.ProxyConnectionFactory.HttpConnectionException;
import java.net.HttpURLConnection;
import java.util.zip.GZIPInputStream;

/**
 * Accesses GDS REST API, the catalogs referenced from it and turns them into a ComponentStorage.
 * Currently just ONE single version of GraalVM is supported (see filter in {@link #acceptsVersion}.
 * if more versions are selected, all their components are merged, as it is the case of
 * multi-versioned catalogs (which are not actively deployed at the moment).
 * <p>
 *
 * @author odouda
 */
public class GDSChannel extends GraalChannelBase {
    private static final Logger LOG = Logger.getLogger(GDSChannel.class.getName());

    private static final String HEADER_DOWNLOAD_CONFIG = "x-download-token";
    private static final String HEADER_CONTENT_ENCODING = "Content-Encoding";
    private static final String JSON_ITEMS = "items";
    private static final String JSON_EXC_CODE = "code";
    private static final String EXC_URL_END = "/content";
    private static final String EXC_CODE_UNVERIFIED_CONFIG = "UnverifiedToken";
    private static final String EXC_CODE_INVALID_CONFIG = "InvalidToken";
    private static final String EXC_CODE_UNACCEPTED = "InvalidLicenseAcceptance";

    /**
     * Helper to read/store GDS token settings.
     */
    private GDSTokenStorage tokenStorage;

    private GDSRESTConnector gdsConnector;

    public GDSChannel(CommandInput aInput, Feedback aFeedback, ComponentRegistry aRegistry) {
        super(aInput, aFeedback.withBundle(GDSChannel.class), aRegistry);
    }

    void setTokenStorage(GDSTokenStorage s) {
        this.tokenStorage = s;
    }

    private String getToken() {
        if (tokenStorage == null) {
            tokenStorage = new GDSTokenStorage(fb, input);
        }
        return tokenStorage.getToken();
    }

    /**
     * GDS will require the user to supply an e-mail that can be used collected by the GDS services.
     *
     * @param info original info
     * @param dn downloader instance
     * @return configured downloader
     */
    @Override
    public FileDownloader configureDownloader(ComponentInfo info, FileDownloader dn) {
        String token = getToken();
        if (!SystemUtils.nonBlankString(token)) {
            token = getToken(info.getLicensePath());
        }
        dn.addRequestHeader(HEADER_DOWNLOAD_CONFIG, token);
        getConnector().fillBasics(dn);
        dn.setDownloadExceptionInterceptor(this::interceptDownloadException);
        return dn;
    }

    public IOException interceptDownloadException(IOException downloadException, FileDownloader fileDownloader) {
        if (!(downloadException instanceof HttpConnectionException)) {
            return downloadException;
        }
        HttpConnectionException hex = (HttpConnectionException) downloadException;
        if (hex.getRetCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
            return (IOException) hex.getCause();
        }
        String downloadURL = hex.getConnectionUrl().toString();
        if (!downloadURL.endsWith(EXC_URL_END)) {
            return (IOException) hex.getCause();
        }
        String licensePath = findLicensePath(downloadURL);
        if (!SystemUtils.nonBlankString(licensePath)) {
            return (IOException) hex.getCause();
        }
        String code;
        try {
            code = new JSONObject(hex.getResponse()).getString(JSON_EXC_CODE);
        } catch (JSONException ex) {
            return (IOException) hex.getCause();
        }
        String token = getToken();
        switch (code) {
            case EXC_CODE_INVALID_CONFIG:
                fb.output("ERR_WrongToken", token);
                if (tokenStorage.getConfSource().equals(GDSTokenStorage.Source.FIL)) {
                    setToken("");
                }
                token = getToken(licensePath);
                break;
            case EXC_CODE_UNVERIFIED_CONFIG:
                fb.output("ERR_InvalidToken", token);
                fb.acceptLine(false);
                break;
            case EXC_CODE_UNACCEPTED:
                if (fileDownloader.getAttemptNr() == 1) {
                    getConnector().sendVerificationEmail(null, licensePath, token);
                }
                fb.output("PROMPT_VerifyEmailAddressEntry", fb.l10n("MSG_YourEmail"));
                fb.acceptLine(false);
                break;
            default:
                return (IOException) hex.getCause();
        }
        fileDownloader.addRequestHeader(HEADER_DOWNLOAD_CONFIG, token);
        return null;
    }

    private String findLicensePath(String artifactURL) {
        try {
            for (String id : storage.listComponentIDs()) {
                for (ComponentInfo info : storage.loadComponentMetadata(id)) {
                    if (info.getRemoteURL().toString().equals(artifactURL)) {
                        return info.getLicensePath();
                    }
                }
            }
        } catch (IOException ex) {
            throw fb.withBundle(ComponentInstaller.class)
                            .failure("REGISTRY_ReadingComponentList", ex, ex.getLocalizedMessage());
        }
        return null;
    }

    private String getToken(String licensePath) {
        fb.output("MSG_InputTokenEntry");
        fb.outputPart("PROMPT_InputTokenEntry");
        String token = fb.acceptLine(false);
        if (!SystemUtils.nonBlankString(token)) {
            String email = MailStorage.checkEmailAddress(receiveEmailAddress(), fb);
            token = getConnector().sendVerificationEmail(email, licensePath, null);
            saveToken(token);
            fb.output("PROMPT_VerifyEmailAddressEntry", email);
            fb.acceptLine(false);
        } else {
            saveToken(token);
        }
        return token;
    }

    private void saveToken(String token) {
        fb.output("MSG_ObtainedToken", token);
        setToken(token);
    }

    private void setToken(String token) {
        try {
            tokenStorage.setToken(token);
            tokenStorage.save();
        } catch (IOException ex) {
            fb.error("WARN_CannotSaveToken", ex, tokenStorage.getPropertiesPath());
        }
    }

    @Override
    public MetadataLoader interceptMetadataLoader(ComponentInfo info, MetadataLoader delegate) {
        return new MetadataLoaderAdapter(delegate) {
            @Override
            public String getLicenseType() {
                return null;
            }

            @Override
            public String getLicenseID() {
                return null;
            }
        };
    }

    String receiveEmailAddress() {
        String mail = input.optValue(GdsCommands.OPTION_EMAIL_ADDRESS);
        if (mail == null) {
            fb.output("MSG_EmailAddressEntry");
            fb.outputPart("PROMPT_EmailAddressEntry");
            mail = fb.acceptLine(false);
        }
        if (mail == null) {
            throw fb.failure("ERR_EmailAddressMissing", null);
        }
        return mail;
    }

    /**
     * Initializes the component storage.
     *
     * @return merged storage.
     * @throws IOException in case of an I/O error.
     */
    @Override
    protected ComponentStorage loadStorage() throws IOException {
        FileDownloader fd = getConnector().obtainArtifacts(localRegistry.getJavaVersion());
        Path storagePath = fd.getLocalFile().toPath();
        List<ComponentInfo> artifacts = loadArtifacts(storagePath, fd.getResponseHeader().get(HEADER_CONTENT_ENCODING));
        if (artifacts.isEmpty()) {
            return throwEmptyStorage();
        }
        return new GDSCatalogStorage(localRegistry, fb, storagePath.toUri().toURL(), artifacts);
    }

    /**
     * Loads the release index. Must be loaded from a local file.
     *
     * @param releasesIndexPath path to the downloaded releases index.
     * @return list of entries in the index
     * @throws IOException in case of I/O error.
     */
    List<ComponentInfo> loadArtifacts(Path releasesIndexPath, List<String> contentEncoding) throws IOException {
        if (edition == null) {
            edition = localRegistry.getGraalCapabilities().get(CommonConstants.CAP_EDITION);
        }
        List<ComponentInfo> result = new ArrayList<>();
        try (InputStreamReader urlReader = new InputStreamReader(
                        contentEncoding != null && contentEncoding.contains(HEADER_VAL_GZIP)
                                        ? new GZIPInputStream(Files.newInputStream(releasesIndexPath))
                                        : Files.newInputStream(releasesIndexPath))) {
            JSONTokener tokener = new JSONTokener(urlReader);
            JSONObject obj = new JSONObject(tokener);

            JSONArray releases = obj.getJSONArray(JSON_ITEMS);
            if (releases == null) {
                // malformed releases file;
                throw new IncompatibleException(fb.l10n("OLDS_InvalidReleasesFile"));
            }

            Version v = localRegistry.getGraalVersion();
            for (Object k : releases) {
                JSONObject jo = (JSONObject) k;
                ArtifactParser e;
                try {
                    e = new ArtifactParser(jo);
                } catch (JSONException | IllegalArgumentException ex) {
                    fb.error("OLDS_ErrorReadingRelease", ex, k, ex.getLocalizedMessage());
                    continue;
                }
                if (!OS.get().equals(OS.fromName(e.getOs()))) {
                    LOG.log(Level.FINER, "Incorrect OS: {0}", k);
                } else if (!ARCH.get().equals(ARCH.fromName(e.getArch()))) {
                    LOG.log(Level.FINER, "Incorrect Arch: {0}", k);
                } else if (!localRegistry.getJavaVersion().equals(e.getJava())) {
                    LOG.log(Level.FINER, "Incorrect Java: {0}", k);
                } else if (edition != null && !edition.equals(e.getEdition())) {
                    LOG.log(Level.FINER, "Incorrect edition: {0}", k);
                } else if (!acceptsVersion(v, Version.fromString(e.getVersion()))) {
                    LOG.log(Level.FINER, "Old version: {0} != {1}", new Object[]{v, Version.fromString(e.getVersion()), e.getVersion()});
                } else if (e.getLabel() == null) {
                    LOG.log(Level.FINER, "Isn't installable component: {0}", new Object[]{e});
                } else {
                    result.add(e.asComponentInfo(getConnector(), fb));
                }
            }
        }
        return result;
    }

    GDSRESTConnector getConnector() {
        if (gdsConnector == null) {
            gdsConnector = new GDSRESTConnector(
                            getIndexURL().toString(),
                            fb,
                            input.getLocalRegistry().getGraalCapabilities()
                                            .get(RELEASE_GDS_PRODUCT_ID_KEY),
                            input.getLocalRegistry().getGraalVersion());
        }
        return gdsConnector;
    }
}
