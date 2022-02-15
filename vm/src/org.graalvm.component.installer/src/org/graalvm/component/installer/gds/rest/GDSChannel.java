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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.IncompatibleException;
import org.graalvm.component.installer.SystemUtils.ARCH;
import org.graalvm.component.installer.SystemUtils.OS;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.ce.WebCatalog;
import org.graalvm.component.installer.gds.GdsCommands;
import org.graalvm.component.installer.gds.GraalChannelBase;
import org.graalvm.component.installer.gds.MailStorage;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.model.ComponentStorage;
import org.graalvm.component.installer.persist.MetadataLoader;
import org.graalvm.component.installer.persist.MetadataLoaderAdapter;
import org.graalvm.component.installer.remote.FileDownloader;
import org.graalvm.component.installer.remote.MergeStorage;
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

    private static final String HEADER_DOWNLOAD_TOKEN = "x-download-token";
    private static final String JSON_ITEMS = "items";
    private static final String JSON_EXC_CODE = "code";
    private static final String EXC_URL_END = "/content";
    private static final String EXC_CODE_INVALID_TOKEN = "InvalidToken";
    private static final String EXC_CODE_UNACCEPTED = "InvalidLicenseAcceptance";

    /**
     * Helper to read/store last email and token setting.
     */
    private TokenStorage tokenStorage;

    private GDSRESTConnector gdsConnector;

    public GDSChannel(CommandInput aInput, Feedback aFeedback, ComponentRegistry aRegistry) {
        super(aInput, aFeedback.withBundle(GDSChannel.class), aRegistry);
    }

    void setTokenStorage(TokenStorage s) {
        this.tokenStorage = s;
    }

    private TokenStorage initTokenStorage() {
        if (tokenStorage == null) {
            tokenStorage = new TokenStorage(fb);
        }
        return tokenStorage;
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
        String token = getToken(info.getLicensePath());
        dn.addRequestHeader(HEADER_DOWNLOAD_TOKEN, token);
        gdsConnector.fillBasics(dn);
        dn.setDownloadExceptionInterceptor(this::interceptDownloadException);
        return dn;
    }

    public IOException interceptDownloadException(IOException downloadException, FileDownloader fileDownloader) {
        if (downloadException instanceof HttpConnectionException) {
            HttpConnectionException hex = (HttpConnectionException) downloadException;
            if (hex.getRetCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
                return (IOException) hex.getCause();
            }
            String downloadURL = hex.getConnectionUrl().toString();
            if (!downloadURL.endsWith(EXC_URL_END)) {
                return (IOException) hex.getCause();
            }
            String licensePath = findLicensePath(downloadURL);
            if (!nonBlankString(licensePath)) {
                return (IOException) hex.getCause();
            }
            String code;
            try {
                code = new JSONObject(hex.getResponse()).getString(JSON_EXC_CODE);
            } catch (JSONException ex) {
                return (IOException) hex.getCause();
            }
            String token = getToken(licensePath);
            switch (code) {
                case EXC_CODE_INVALID_TOKEN:
                    fb.output("ERR_InvalidToken", hex, token);
                    token = getToken(licensePath, true);
                    break;
                case EXC_CODE_UNACCEPTED:
                    Map.Entry<String, String> downloadToken = initTokenStorage().getToken();
                    if (fileDownloader.getAttemptNr() == 1) {
                        token = gdsConnector.sendVerificationEmail(downloadToken.getKey(), licensePath, downloadToken.getValue());
                    } else {
                        token = downloadToken.getValue();
                    }
                    if (nonBlankString(token)) {
                        fb.output("PROMPT_VerifyEmailAddressEntry", downloadToken.getKey());
                        fb.acceptLine(false);
                    }
                    break;
                default:
                    return (IOException) hex.getCause();
            }
            fileDownloader.addRequestHeader(HEADER_DOWNLOAD_TOKEN, token);
        } else {
            return downloadException;
        }
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
        return getToken(licensePath, false);
    }

    private String getToken(String licensePath, boolean forceToken) {
        String token = input.optValue(GdsCommands.OPTION_DOWNLOAD_TOKEN);
        if (!forceToken && nonBlankString(token)) {
            return token;
        }
        if (forceToken && nonBlankString(token)) {
            return askForToken(true);
        } else {
            Map.Entry<String, String> downloadToken = initTokenStorage().getToken();
            if (downloadToken == null || forceToken) {
                if (input == null) {
                    throw fb.failure("ERR_EmailAddressMissing", null);
                }
                String email = downloadToken != null ? downloadToken.getKey() : MailStorage.checkEmailAddress(receiveEmailAddress(), fb);
                token = receiveToken(email, licensePath);
                downloadToken = Map.entry(email, token);
                try {
                    tokenStorage.setToken(downloadToken);
                    tokenStorage.save();
                } catch (IOException ex) {
                    fb.error("WARN_CannotSaveEmailAddress", ex, ex.getLocalizedMessage());
                }
            }
            return downloadToken.getValue();
        }
    }

    private String askForToken(boolean tokenFromCmdLine) {
        fb.output(tokenFromCmdLine ? "MSG_InputCmdTokenEntry" : "MSG_InputTokenEntry");
        fb.outputPart("PROMPT_InputTokenEntry");
        String token = fb.acceptLine(false);
        if (nonBlankString(token)) {
            return token;
        }
        return null;
    }

    private String receiveToken(String email, String licensePath) {
        String token = askForToken(false);
        if (nonBlankString(token)) {
            return token;
        }
        token = gdsConnector.sendVerificationEmail(email, licensePath, null);
        fb.output("PROMPT_VerifyEmailAddressEntry", email);
        fb.acceptLine(false);
        return token;
    }

    private static boolean nonBlankString(String token) {
        return token != null && !token.isBlank();
    }

    @Override
    public MetadataLoader interceptMetadataLoader(ComponentInfo info, MetadataLoader delegate) {
        return new MetadataLoaderAdapter(delegate) {
            @Override
            public String getLicenseType() {
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
     * Initializes the component storage. Loads the releases index, selects matching releases and
     * creates {@link WebCatalog} for each of the catalogs. Merges using {@link MergeStorage}.
     *
     * @return merged storage.
     * @throws IOException in case of an I/O error.
     */
    @Override
    protected ComponentStorage loadStorage() throws IOException {
        Path storagePath = getConnector().obtainArtifacts(localRegistry.getJavaVersion());
        List<ComponentInfo> artifacts = loadArtifacts(storagePath);
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
    List<ComponentInfo> loadArtifacts(Path releasesIndexPath) throws IOException {
        if (edition == null) {
            edition = localRegistry.getGraalCapabilities().get(CommonConstants.CAP_EDITION);
        }
        List<ComponentInfo> result = new ArrayList<>();
        try (InputStreamReader urlReader = new InputStreamReader(new GZIPInputStream(Files.newInputStream(releasesIndexPath)))) {
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
                } else {
                    result.add(e.asComponentInfo(gdsConnector, fb));
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
