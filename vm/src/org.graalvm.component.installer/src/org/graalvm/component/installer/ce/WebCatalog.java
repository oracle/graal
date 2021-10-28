/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.ce;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.NoRouteToHostException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.IncompatibleException;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.model.ComponentStorage;
import org.graalvm.component.installer.remote.FileDownloader;
import org.graalvm.component.installer.remote.RemotePropertiesStorage;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.SoftwareChannelSource;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.RemoteInfoProcessor;

public class WebCatalog implements SoftwareChannel {
    private final String urlString;
    private final SoftwareChannelSource source;

    private URL catalogURL;
    private Feedback feedback;
    private ComponentRegistry local;
    private ComponentStorage storage;
    private RuntimeException savedException;
    private RemoteInfoProcessor remoteProcessor = RemoteInfoProcessor.NONE;

    /**
     * The accepted version - only exact match is supported at the moment.
     */
    private Version.Match matchVersion;

    /**
     * If true, enables loading different versions from V1 catalogs.
     */
    private boolean enableV1Versions;

    public WebCatalog(String u, SoftwareChannelSource source) {
        this.urlString = u;
        this.source = source;
    }

    public RemoteInfoProcessor getRemoteProcessor() {
        return remoteProcessor;
    }

    public void setRemoteProcessor(RemoteInfoProcessor remoteProcessor) {
        this.remoteProcessor = remoteProcessor;
    }

    public Version.Match getMatchVersion() {
        return matchVersion;
    }

    public boolean isEnableV1Versions() {
        return enableV1Versions;
    }

    /**
     * Enables version processing in V1 catalogs. Note that not complete version string semantics is
     * possible with v1 format, as version may contain underscores (_), which are delimiters in v1
     * catalogs.
     * <p/>
     * The default setting is false.
     * 
     * @param enableV1Versions true, if should be enabled.
     */
    public void setEnableV1Versions(boolean enableV1Versions) {
        this.enableV1Versions = enableV1Versions;
    }

    /**
     * Overrides versions loaded by this catalog. By default own version only is accepted. If
     * matchVersion is set, the catalog will load matching version(s). At this moment <b>only exact
     * match is supported</b>.
     * 
     * @param aMatchVersion
     */
    public void setMatchVersion(Version.Match aMatchVersion) {
        this.matchVersion = aMatchVersion;
    }

    protected static boolean acceptURLScheme(String scheme, String urlSpec) {
        switch (scheme) {
            case "http":    // NOI18N
            case "https":   // NOI18N
            case "ftp":     // NOI18N
            case "ftps":    // NOI18N
                return true;
            case "file":    // NOI18N
                // accept only regular files
                try {
                    Path p = new File(new URI(urlSpec)).toPath();
                    return Files.isRegularFile(p) && Files.isReadable(p);
                } catch (URISyntaxException ex) {
                    // cannot be converted to file, bail out
                    break;
                }
        }
        return false;
    }

    public void init(CommandInput in, Feedback out) {
        init(in.getLocalRegistry(), out);
    }

    public void init(ComponentRegistry aLocal, Feedback out) {
        this.feedback = out.withBundle(WebCatalog.class);
        this.local = aLocal;
    }

    @Override
    public ComponentStorage getStorage() {
        if (this.storage != null) {
            return this.storage;
        }

        Map<String, String> graalCaps = local.getGraalCapabilities();

        StringBuilder sb = new StringBuilder();
        sb.append(SystemUtils.patternOsName(graalCaps.get(CommonConstants.CAP_OS_NAME)).toLowerCase());
        sb.append("_");
        sb.append(SystemUtils.patternOsArch(graalCaps.get(CommonConstants.CAP_OS_ARCH).toLowerCase()));

        try {
            catalogURL = new URL(urlString);
        } catch (MalformedURLException ex) {
            throw feedback.failure("REMOTE_InvalidURL", ex, catalogURL, ex.getLocalizedMessage());
        }

        Properties props = new Properties();
        // create the storage. If the init fails, but process will not terminate, the storage will
        // serve no components on the next call.
        RemotePropertiesStorage newStorage = createPropertiesStorage(feedback, local, props, sb.toString(), catalogURL);
        if (remoteProcessor != null) {
            newStorage.setRemoteProcessor(remoteProcessor);
        }
        Properties loadProps = new Properties();
        FileDownloader dn;
        try {
            // avoid duplicate (failed) downloads
            if (savedException != null) {
                throw savedException;
            }
            catalogURL = new URL(urlString);
            String l = source.getLabel();
            dn = new FileDownloader(feedback.l10n(l == null || l.isEmpty() ? "REMOTE_CatalogLabel2" : "REMOTE_CatalogLabel", l), catalogURL, feedback);
            dn.download();
        } catch (NoRouteToHostException | ConnectException ex) {
            throw savedException = feedback.failure("REMOTE_ErrorDownloadCatalogProxy", ex, catalogURL, ex.getLocalizedMessage());
        } catch (FileNotFoundException ex) {
            // treat missing resources as non-fatal errors, print warning
            feedback.error("REMOTE_WarningErrorDownloadCatalogNotFoundSkip", ex, catalogURL);
            this.storage = newStorage;
            return storage;
        } catch (IOException ex) {
            throw savedException = feedback.failure("REMOTE_ErrorDownloadCatalog", ex, catalogURL, ex.getLocalizedMessage());
        }
        // download is successful; if the processing fails after download, next call will report an
        // empty catalog.
        this.storage = newStorage;

        StringBuilder oldGraalPref = new StringBuilder("^" + BundleConstants.GRAAL_COMPONENT_ID);
        oldGraalPref.append('.');

        String graalVersionString;
        Version normalizedVersion;

        if (matchVersion != null) {
            graalVersionString = matchVersion.getVersion().displayString();
            normalizedVersion = matchVersion.getVersion().installVersion();
        } else {
            // read from the release file
            graalVersionString = graalCaps.get(CommonConstants.CAP_GRAALVM_VERSION).toLowerCase();
            normalizedVersion = local.getGraalVersion().installVersion();
        }

        StringBuilder graalPref = new StringBuilder(oldGraalPref);

        oldGraalPref.append(Pattern.quote(graalVersionString));

        oldGraalPref.append('_').append(sb);
        graalPref.append(sb).append('/');
        graalPref.append("(?<ver>[^/]+)$"); // NOI18N

        try (FileInputStream fis = new FileInputStream(dn.getLocalFile())) {
            loadProps.load(fis);
        } catch (IllegalArgumentException | IOException ex) {
            throw feedback.failure("REMOTE_CorruptedCatalogFile", ex, catalogURL);
        }

        Pattern oldPrefixPattern = Pattern.compile(oldGraalPref.toString(), Pattern.CASE_INSENSITIVE);
        Pattern newPrefixPattern = Pattern.compile(graalPref.toString(), Pattern.CASE_INSENSITIVE);
        Stream<String> propNames = loadProps.stringPropertyNames().stream();
        boolean foundPrefix = propNames.anyMatch(p -> {
            if (oldPrefixPattern.matcher(p).matches()) {
                return true;
            }
            Matcher m = newPrefixPattern.matcher(p);
            if (!m.find() || m.start() > 0) {
                return false;
            }
            try {
                Version v = Version.fromString(m.group("ver")); // NOI18N
                return normalizedVersion.match(Version.Match.Type.INSTALLABLE).test(v);
            } catch (IllegalArgumentException ex) {
                return false;
            }
        });
        if (!foundPrefix) {
            boolean graalPrefixFound = false;
            boolean componentFound = false;
            for (String s : loadProps.stringPropertyNames()) {
                if (s.startsWith(BundleConstants.GRAAL_COMPONENT_ID)) {
                    graalPrefixFound = true;
                }
                if (s.startsWith("Component.")) {
                    componentFound = true;
                }
            }
            if (!componentFound) {
                // no graal prefix, no components
                feedback.verboseOutput("REMOTE_CatalogDoesNotContainComponents", catalogURL);
                return newStorage;
            } else if (!graalPrefixFound) {
                // strange thing, no graal declaration, but components are there ?
                throw feedback.failure("REMOTE_CorruptedCatalogFile", null, catalogURL);
            } else {
                throw new IncompatibleException(
                                feedback.l10n("REMOTE_UnsupportedGraalVersion",
                                                graalCaps.get(CommonConstants.CAP_GRAALVM_VERSION),
                                                graalCaps.get(CommonConstants.CAP_OS_NAME),
                                                graalCaps.get(CommonConstants.CAP_OS_ARCH)),
                                null);
            }
        }
        props.putAll(loadProps);
        return newStorage;
    }

    protected RemotePropertiesStorage createPropertiesStorage(Feedback aFeedback,
                    ComponentRegistry aLocal, Properties props, String selector, URL baseURL) {
        return new RemotePropertiesStorage(
                        aFeedback, aLocal, props, selector, null, baseURL);
    }

    @Override
    public FileDownloader configureDownloader(ComponentInfo cInfo, FileDownloader dn) {
        return dn;
    }

    public static class WebCatalogFactory implements SoftwareChannel.Factory {
        private CommandInput input;

        @Override
        public SoftwareChannel createChannel(SoftwareChannelSource src, CommandInput in, Feedback fb) {
            String urlSpec = src.getLocationURL();
            int schColon = urlSpec.indexOf(':'); // NOI18N
            if (schColon == -1) {
                return null;
            }
            String scheme = urlSpec.toLowerCase().substring(0, schColon);
            if (acceptURLScheme(scheme, urlSpec)) {
                WebCatalog c = new WebCatalog(urlSpec, src);
                c.init(in, fb);
                return c;
            }
            return null;
        }

        @Override
        public void init(CommandInput in, Feedback out) {
            assert this.input == null;
            this.input = in;
        }
    }
}
