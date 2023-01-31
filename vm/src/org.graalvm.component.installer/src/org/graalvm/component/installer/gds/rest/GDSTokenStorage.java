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

import org.graalvm.component.installer.CommandInput;
import static org.graalvm.component.installer.CommonConstants.SYSPROP_USER_HOME;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.gds.GdsCommands;
import static org.graalvm.component.installer.CommonConstants.PATH_GDS_CONFIG;
import org.graalvm.component.installer.SystemUtils;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import static org.graalvm.component.installer.CommonConstants.PATH_USER_GU;
import java.io.File;
import java.util.Map;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.gds.MailStorage;

/**
 *
 * @author odouda
 */
public class GDSTokenStorage {

    static final String GRAAL_EE_DOWNLOAD_TOKEN = "GRAAL_EE_DOWNLOAD_TOKEN";
    private final Feedback feedback;
    private final CommandInput input;
    private final Path propertiesPath;

    private Source tokenSource = Source.NON;
    private Properties properties;
    private boolean changed;

    public GDSTokenStorage(Feedback feedback, CommandInput input) {
        this.feedback = feedback.withBundle(GDSTokenStorage.class);
        this.input = input;
        propertiesPath = Path.of(System.getProperty(SYSPROP_USER_HOME), PATH_USER_GU, PATH_GDS_CONFIG);
    }

    Path getPropertiesPath() {
        return propertiesPath;
    }

    public Source getConfSource() {
        return tokenSource;
    }

    private Properties load() {
        if (properties != null) {
            return properties;
        }
        return properties = load(getPropertiesPath());
    }

    private Properties load(Path propsPath) {
        Properties props = new Properties();
        if (Files.exists(propsPath)) {
            try (InputStream is = Files.newInputStream(propsPath)) {
                props.load(is);
            } catch (IllegalArgumentException | IOException ex) {
                feedback.error("ERR_CouldNotLoadToken", ex, propsPath, ex.getLocalizedMessage());
            }
        }
        return props;
    }

    public String getToken() {
        String token = getCmdToken();
        if (SystemUtils.nonBlankString(token)) {
            tokenSource = Source.CMD;
            return token;
        }
        token = getEnvToken();
        if (SystemUtils.nonBlankString(token)) {
            tokenSource = Source.ENV;
            return token;
        }
        token = getFileToken();
        if (SystemUtils.nonBlankString(token)) {
            tokenSource = Source.FIL;
            return token;
        }
        tokenSource = Source.NON;
        return null;
    }

    private String getCmdToken() {
        String userFile = getCmdFile();
        if (!SystemUtils.nonBlankString(userFile)) {
            return null;
        }
        return load(SystemUtils.fromUserString(userFile)).getProperty(GRAAL_EE_DOWNLOAD_TOKEN);
    }

    private String getCmdFile() {
        return input.optValue(GdsCommands.OPTION_GDS_CONFIG);
    }

    private String getEnvToken() {
        return input.getParameter(GRAAL_EE_DOWNLOAD_TOKEN, false);
    }

    private String getFileToken() {
        return load().getProperty(GRAAL_EE_DOWNLOAD_TOKEN);
    }

    public void setToken(String token) {
        if (token == null) {
            throw new IllegalArgumentException("Download token cannot be null.");
        }
        String p = getFileToken();
        if (token.equals(p)) {
            return;
        }
        properties.setProperty(GRAAL_EE_DOWNLOAD_TOKEN, token);
        changed = true;
    }

    public void save() throws IOException {
        Path propsPath = getPropertiesPath();
        if (!changed || properties == null || propsPath == null) {
            return;
        }
        Path parent = propsPath.getParent();
        if (parent == null) {
            // cannot happen, but Spotbugs keeps yelling
            return;
        }
        Files.createDirectories(parent);
        if (SystemUtils.isWindows()) {
            File file = parent.toFile();
            file.setReadable(false, false);
            file.setExecutable(false, false);
            file.setWritable(false, false);
            file.setReadable(true);
            file.setExecutable(true);
            file.setWritable(true);
        } else {
            Files.setPosixFilePermissions(parent, Set.of(PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
        }
        try (OutputStream os = Files.newOutputStream(propsPath)) {
            properties.store(os, null);
        }
        if (SystemUtils.isWindows()) {
            File file = propsPath.toFile();
            file.setReadable(false, false);
            file.setExecutable(false, false);
            file.setWritable(false, false);
            file.setReadable(true);
            file.setWritable(true);
        } else {
            Files.setPosixFilePermissions(propsPath,
                            Set.of(PosixFilePermission.OWNER_READ,
                                            PosixFilePermission.OWNER_WRITE));
        }
        changed = false;
    }

    public static void printToken(Feedback feedback, CommandInput input) {
        new GDSTokenStorage(feedback, input).printToken();
    }

    public static void revokeToken(Feedback feedback, CommandInput input, String token) {
        new GDSTokenStorage(feedback, input).revokeToken(token);
    }

    public static void revokeAllTokens(Feedback feedback, CommandInput input, String email) {
        new GDSTokenStorage(feedback, input).revokeAllTokens(email);
    }

    public void revokeAllTokens(String email) {
        GDSRESTConnector connector = makeConnector();
        if (connector == null) {
            feedback.message("MSG_NoGDSAddress");
            return;
        }
        String m = MailStorage.checkEmailAddress(email, feedback);
        if (m == null || m.isEmpty()) {
            feedback.message("MSG_NoEmail");
            return;
        }
        connector.revokeTokens(m);
        feedback.message("MSG_AcceptRevoke");
    }

    public void revokeToken(String token) {
        GDSRESTConnector connector = makeConnector();
        if (connector == null) {
            feedback.message("MSG_NoGDSAddress");
            return;
        }
        String t = token;
        if (t == null || t.isEmpty()) {
            t = getFileToken();
            if (t == null || t.isEmpty()) {
                feedback.message("MSG_NoRevokableToken");
                return;
            }
            removeFileToken();
        }
        connector.revokeToken(t);
        feedback.message("MSG_AcceptRevoke");
    }

    GDSRESTConnector makeConnector() {
        Map<String, String> caps = input.getLocalRegistry().getGraalCapabilities();
        String releaseCatalog = caps.get(CommonConstants.RELEASE_CATALOG_KEY);
        if (releaseCatalog == null || releaseCatalog.isEmpty()) {
            return null;
        }
        String[] catalogs = releaseCatalog.split("\\|");
        String catalog = null;
        for (String c : catalogs) {
            if (c.contains("rest://")) {
                catalog = "https" + c.substring(c.indexOf("rest://") + 4);
            }
        }
        if (catalog == null || catalog.isEmpty()) {
            return null;
        }
        return new GDSRESTConnector(catalog, feedback, caps.get(CommonConstants.RELEASE_GDS_PRODUCT_ID_KEY), input.getLocalRegistry().getGraalVersion());
    }

    public void removeFileToken() {
        load().remove(GRAAL_EE_DOWNLOAD_TOKEN);
        changed = true;
        try {
            save();
        } catch (IOException ex) {
            feedback.error("ERR_CouldNotRemoveToken", ex);
        }
    }

    public void printToken() {
        String token = getToken();
        String msg = tokenSource == Source.NON ? "MSG_EmptyToken" : "MSG_PrintToken";
        String source = "";
        switch (tokenSource) {
            case ENV:
                source = feedback.l10n("MSG_PrintTokenEnv", GRAAL_EE_DOWNLOAD_TOKEN);
                break;
            case CMD:
                source = feedback.l10n("MSG_PrintTokenCmdFile", getCmdFile());
                break;
            case FIL:
                source = feedback.l10n("MSG_PrintTokenFile", getPropertiesPath().toString());
                break;
            default:
                // NOOP
                break;
        }
        feedback.output(msg, token, source);
    }

    public enum Source {
        NON,
        ENV,
        CMD,
        FIL
    }
}
