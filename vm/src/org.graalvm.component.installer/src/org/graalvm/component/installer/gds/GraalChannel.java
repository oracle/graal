/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.tools.utils.json.JSONException;
import com.oracle.truffle.tools.utils.json.JSONObject;
import com.oracle.truffle.tools.utils.json.JSONTokener;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.IncompatibleException;
import org.graalvm.component.installer.SoftwareChannelSource;
import org.graalvm.component.installer.SuppressFBWarnings;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.ce.WebCatalog;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.model.ComponentStorage;
import org.graalvm.component.installer.persist.MetadataLoader;
import org.graalvm.component.installer.persist.MetadataLoaderAdapter;
import org.graalvm.component.installer.remote.FileDownloader;
import org.graalvm.component.installer.remote.MergeStorage;
import org.graalvm.component.installer.remote.RemotePropertiesStorage;

/**
 * Accesses GDS Release file, the catalogs referenced from it and turns them into a
 * ComponentStorage. Currently just ONE single version of GraalVM is supported (see filter in
 * {@link #acceptsVersion}. if more versions are selected, all their components are merged, as it is
 * the case of multi-versioned catalogs (which are not actively deployed at the moment).
 * <p>
 *
 * @author sdedic
 */
public class GraalChannel extends GraalChannelBase {
    private static final Logger LOG = Logger.getLogger(GraalChannel.class.getName());

    /**
     * Collected info about invalid release entries.
     */
    private final List<String> invalidReleases = new ArrayList<>();

    /**
     * List of invalid arch/os base entries.
     */
    private final List<String> invalidOsArchEntries = new ArrayList<>();

    /**
     * Malformed URLs found during releases processing.
     */
    private final List<String> invalidURLs = new ArrayList<>();

    private static final String KEY_RELEASES = "Releases";
    private static final String KEY_RELEASE_NAME = "name";
    private static final String KEY_RELEASE_BASES = "base";
    private static final String KEY_RELEASE_CATALOG = "catalog";
    private static final String KEY_RELEASE_LICENSE = "license";
    private static final String KEY_RELEASE_LICENSE_LABEL = "licenseLabel";
    private static final String KEY_RELEASE_VRESION = "version";
    private static final String KEY_RELEASE_EDITION = "edition";
    private static final String KEY_RELEASE_JAVA = "java";

    /**
     * Helper to read/store last email setting.
     */
    private MailStorage mailStorage;

    /**
     * Flag that the user has been already prompted for email this session. If the user has refused
     * to provide the email, no more prompts will be printed (this session).
     */
    private boolean prompted;

    public GraalChannel(CommandInput aInput, Feedback aFeedback, ComponentRegistry aRegistry) {
        super(aInput, aFeedback.withBundle(GraalChannel.class), aRegistry);
    }

    void setMailStorage(MailStorage s) {
        this.mailStorage = s;
    }

    private MailStorage initMailStorage() {
        if (mailStorage == null) {
            mailStorage = new MailStorage(localRegistry, fb);
        }
        return mailStorage;
    }

    /**
     * GDS will require the user to supply an e-mail that can be used collected by the GDS services.
     * Right now, the last-used e-mail is just stored in the GDS-private local storage.
     *
     * @param info original info
     * @param dn downloader instance
     * @return configured downloader
     */
    @Override
    public FileDownloader configureDownloader(ComponentInfo info, FileDownloader dn) {
        ensureMailAddress();
        return dn;
    }

    private void ensureMailAddress() {
        String reportEmailAddress = initMailStorage().getEmailAddress();
        if (reportEmailAddress == null) {
            if (input == null) {
                throw fb.failure("ERR_EmailAddressMissing", null);
            }
            reportEmailAddress = MailStorage.checkEmailAddress(receiveEmailAddress(), fb);
            try {
                mailStorage.setEmailAddress(reportEmailAddress);
                mailStorage.save();
            } catch (IOException ex) {
                fb.error("WARN_CannotSaveEmailAddress", ex, ex.getLocalizedMessage());
            }
        }
    }

    @Override
    public MetadataLoader interceptMetadataLoader(ComponentInfo info, MetadataLoader delegate) {
        return new MetadataLoaderAdapter(delegate) {
            @Override
            public Boolean recordLicenseAccepted(ComponentInfo nfo, String licenseID, String licenseText, Date d) throws IOException {
                ensureMailAddress();
                return initMailStorage().getEmailAddress() != null ? null : false;
            }
        };
    }

    @SuppressFBWarnings(value = "ES_COMPARING_STRINGS_WITH_EQ", justification = "AUTO_YES is a special tag value instance")
    String receiveEmailAddress() {
        String mail = input.optValue(GdsCommands.OPTION_EMAIL_ADDRESS);
        if (mail == null) {
            if (prompted || fb.isNonInteractive()) {
                return null;
            }
            fb.output("MSG_EmailAddressEntry");
            fb.outputPart("PROMPT_EmailAddressEntry");
            mail = fb.acceptLine(true);
            if (Feedback.AUTO_YES == mail) {
                mail = null;
            }
            prompted = true;
        }
        return mail;
    }

    @Override
    protected ComponentStorage loadStorage() throws IOException {
        FileDownloader dn = new FileDownloader(fb.l10n("OLDS_ReleaseFile"), getIndexURL(), fb);
        dn.download();
        Path storagePath = dn.getLocalFile().toPath();
        List<ReleaseEntry> releases = loadReleasesIndex(storagePath);
        if (releases.isEmpty()) {
            return throwEmptyStorage();
        }
        MergeStorage store = new MergeStorage(localRegistry, fb);
        store.setAcceptAllSources(true);
        for (ReleaseEntry en : releases) {
            URL catURL = en.getCatalogURL();
            Version v = Version.fromString(en.getVersion().displayString());
            SoftwareChannelSource src = new SoftwareChannelSource(
                            catURL.toString(), en.getLabel());
            WebCatalog cata = new WebCatalog(src.getLocationURL(), src) {
                @Override
                protected RemotePropertiesStorage createPropertiesStorage(Feedback aFeedback, ComponentRegistry aLocal, Properties props, String selector, URL baseURL) {
                    return new RemotePropertiesStorage(
                                    aFeedback, aLocal, props, selector, v, baseURL);
                }
            };
            cata.init(localRegistry, fb);
            cata.setMatchVersion(en.getVersion().match(Version.Match.Type.EXACT));
            cata.setRemoteProcessor((i) -> configureLicense(i, en));
            store.addChannel(src, cata);
        }
        return store;
    }

    private static ComponentInfo configureLicense(ComponentInfo info, ReleaseEntry en) {
        if (info.getLicensePath() != null) {
            return info;
        }

        String urlString = en.getLicenseURL().toString();
        String label = en.getLicenseLabel();
        if (label == null) {
            label = urlString;
        }
        info.setLicenseType(label);
        info.setLicensePath(urlString);
        return info;
    }

    /**
     * Loads the release index. Must be loaded from a local file.
     *
     * @param releasesIndexPath path to the downloaded releases index.
     * @return list of entries in the index
     * @throws IOException in case of I/O error.
     */
    List<ReleaseEntry> loadReleasesIndex(Path releasesIndexPath) throws IOException {
        if (edition == null) {
            edition = localRegistry.getGraalCapabilities().get(CommonConstants.CAP_EDITION);
        }
        List<ReleaseEntry> result = new ArrayList<>();
        try (Reader urlReader = new InputStreamReader(Files.newInputStream(releasesIndexPath))) {
            JSONTokener tokener = new JSONTokener(urlReader);
            JSONObject obj = new JSONObject(tokener);

            JSONObject releases = obj.getJSONObject(KEY_RELEASES);
            if (releases == null) {
                // malformed releases file;
                throw new IncompatibleException(fb.l10n("OLDS_InvalidReleasesFile"));
            }

            Version v = localRegistry.getGraalVersion();
            for (String k : releases.keySet()) {
                JSONObject jo = releases.getJSONObject(k);
                ReleaseEntry e = null;

                try {
                    e = jsonToRelease(k, jo);
                } catch (JSONException | IllegalArgumentException ex) {
                    fb.error("OLDS_ErrorReadingRelease", ex, k, ex.getLocalizedMessage());
                }
                if (e == null) {
                    invalidReleases.add(k);
                } else if (!localRegistry.getJavaVersion().equals(e.getJavaVersion())) {
                    LOG.log(Level.FINER, "Invalid Java: {0}", k);
                } else if (e.getBasePackages().isEmpty()) {
                    LOG.log(Level.FINER, "No distribution packages: {0}", k);
                } else if (edition != null && !edition.equals(e.getEdition())) {
                    LOG.log(Level.FINER, "Incorrect edition: {0}", k);
                } else if (!acceptsVersion(v, e.getVersion())) {
                    LOG.log(Level.FINER, "Old version: {0}", k);
                } else {
                    result.add(e);
                }
            }
        }
        return result;
    }

    private URL resolveURL(String u) throws MalformedURLException {
        return getIndexURL() == null ? new URL(u) : new URL(getIndexURL(), u);
    }

    /**
     * Filters out different OS/Architecture.
     *
     * @param rk entry id prefix, for diagnostic purposes.
     * @param bp package object
     * @return true, if the base package is OK for the current release.
     */
    boolean verifyOsArch(String rk, ReleaseEntry.BasePackage bp) {
        if (bp.getOs() != null) {
            String bpos = SystemUtils.normalizeOSName(bp.getOs(), null);
            if (!bpos.equals(localRegistry.getGraalCapabilities().get(CommonConstants.CAP_OS_NAME))) {
                LOG.log(Level.FINER, "OS mismatch ({0}) for {1}", new Object[]{bp.getOs(), rk});
                return false;
            }
        }
        if (bp.getArch() != null) {
            String bparch = SystemUtils.normalizeArchitecture(null, bp.getArch());
            if (!bparch.equals(localRegistry.getGraalCapabilities().get(CommonConstants.CAP_OS_ARCH))) {
                LOG.log(Level.FINER, "Arch mismatch ({0}) for {1}", new Object[]{bp.getArch(), rk});
                return false;
            }
        }
        return true;
    }

    /**
     * Reads JSON object into {@link ReleaseEntry}.
     *
     * @param rk release id in the index; diagnostics.
     * @param jo the JSON object to convert
     * @return Release entry or {@code null} if the entry is for a different release / os / arch
     * @throws IOException in case of I/O error or a malformed JSON.
     */
    ReleaseEntry jsonToRelease(String rk, JSONObject jo) throws IOException {
        String licenseString;
        String catalogString;
        JSONObject bases;
        String name;
        String versionString;

        name = jo.getString(KEY_RELEASE_NAME);
        licenseString = jo.has(KEY_RELEASE_LICENSE) ? jo.getString(KEY_RELEASE_LICENSE) : null;
        catalogString = jo.getString(KEY_RELEASE_CATALOG);
        bases = jo.getJSONObject(KEY_RELEASE_BASES);

        if (catalogString == null || catalogString.isEmpty()) {
            return null;
        }

        versionString = jo.getString(KEY_RELEASE_VRESION);
        String javaString = jo.getString(KEY_RELEASE_JAVA);
        String editionString = jo.getString(KEY_RELEASE_EDITION);
        String licenseLabel = jo.has(KEY_RELEASE_LICENSE_LABEL) ? jo.getString(KEY_RELEASE_LICENSE_LABEL) : null;

        Version v = Version.fromString(versionString);
        String jv;
        if (javaString.startsWith("jdk")) { // NOI18N
            jv = "" + SystemUtils.interpretJavaMajorVersion(javaString.substring(3)); // NOI18N
        } else if (javaString.startsWith("java")) { // NOI18N
            jv = "" + SystemUtils.interpretJavaMajorVersion(javaString.substring(4)); // NOI18N
        } else {
            return null;
        }
        String u = null;

        try {
            u = licenseString;
            URL licenseURL = licenseString == null || licenseString.isEmpty() ? null : resolveURL(licenseString);

            u = catalogString;
            URL catalogURL = resolveURL(catalogString);

            ReleaseEntry e = new ReleaseEntry(rk, name, v, licenseURL, catalogURL);
            e.setEdition(editionString);
            e.setJavaVersion(jv);
            e.setLicenseLabel(licenseLabel);
            LOG.log(Level.FINEST, "Reading: {0}", rk);

            if (bases == null) {
                LOG.log(Level.FINER, "Release {0} has no bases.", rk);
                return null;
            }

            for (String k : bases.keySet()) {
                ReleaseEntry.BasePackage bp = jsonToBase(k, bases.getJSONObject(k));
                if (bp == null) {
                    LOG.log(Level.FINER, "Invalid base: {0}", k);
                    continue;
                }
                if (verifyOsArch(rk + "-" + k, bp)) {
                    e.addBasePackage(bp);
                }
            }
            return e;
        } catch (MalformedURLException ex) {
            invalidURLs.add(u);
            return null;
        }
    }

    /**
     * Deserializes JSON object to {@link ReleaseEntry.BasePackage}.
     *
     * @param s package id
     * @param jo json input
     * @return Package object or {@code null} if invalid arch/os
     */
    ReleaseEntry.BasePackage jsonToBase(String s, JSONObject jo) {
        String os;
        String arch;
        String urlString;

        try {
            os = jo.getString(KEY_BASE_OS);
            arch = jo.getString(KEY_BASE_ARCH);
            urlString = jo.getString(KEY_BASE_URL);
        } catch (JSONException ex) {
            invalidOsArchEntries.add(s);
            return null;
        }
        try {
            URL u = resolveURL(urlString);
            return new ReleaseEntry.BasePackage(
                            SystemUtils.normalizeOSName(os, arch),
                            SystemUtils.normalizeArchitecture(os, arch),
                            u);
        } catch (MalformedURLException ex) {
            invalidURLs.add(urlString);
            return null;
        }
    }

    private static final String KEY_BASE_URL = "url"; // NOI18N
    private static final String KEY_BASE_ARCH = "arch";  // NOI18N
    private static final String KEY_BASE_OS = "os"; // NOI18N

    ReleaseEntry.BasePackage jsonToBase(String s, String urlString) {
        String[] parts = s.split("-");
        if (parts.length != 2) {
            invalidOsArchEntries.add(s);
            return null;
        }
        if (urlString == null || urlString.isEmpty()) {
            invalidOsArchEntries.add(s);
            return null;
        }
        try {
            URL u = resolveURL(urlString);
            return new ReleaseEntry.BasePackage(
                            SystemUtils.normalizeOSName(parts[0], parts[1]),
                            SystemUtils.normalizeArchitecture(parts[0], parts[1]),
                            u);
        } catch (MalformedURLException ex) {
            invalidURLs.add(urlString);
            return null;
        }
    }
}
