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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentRegistry;
import java.util.regex.Pattern;

/**
 *
 * @author sdedic
 */
public class MailStorage {
    static final Path PROPERTIES_PATH = SystemUtils.fromCommonRelative(CommonConstants.PATH_COMPONENT_STORAGE +
                    "/gds/gds.properties");
    static final String PROP_LAST_EMAIL = "last.email"; // NOI18N

    private final ComponentRegistry localRegistry;
    private final Feedback feedback;

    private Properties properties;
    private Path propertiesPath;
    private Path storagePath;
    private boolean changed;

    public MailStorage(ComponentRegistry localRegistry, Feedback feedback) {
        this.localRegistry = localRegistry;
        this.feedback = feedback;
    }

    public void setStorage(Path storage) {
        this.storagePath = storage;
        propertiesPath = storagePath.resolve(PROPERTIES_PATH);
    }

    private Properties load() {
        localRegistry.getManagementStorage();
        if (properties != null) {
            return properties;
        }
        if (!Files.exists(propertiesPath)) {
            properties = new Properties();
        } else {
            try (InputStream is = Files.newInputStream(propertiesPath)) {
                Properties read = new Properties();
                read.load(is);
                this.properties = read;
            } catch (IOException ex) {
                feedback.error("ERR_CouldNotLoadGDS", ex, propertiesPath, ex.getLocalizedMessage());
                properties = new Properties();
            }
        }
        return properties;
    }

    public String getEmailAddress() {
        load();
        return properties.getProperty(PROP_LAST_EMAIL);
    }

    public void setEmailAddress(String mailAddress) {
        Properties props = load();

        if (mailAddress == null) {
            if (props.containsKey(PROP_LAST_EMAIL)) {
                props.remove(PROP_LAST_EMAIL);
                changed = true;
            } else {
                return;
            }
        } else {
            String p = getEmailAddress();
            if (mailAddress.equals(p)) {
                return;
            }
            props.setProperty(PROP_LAST_EMAIL, mailAddress);
        }
        changed = true;
    }

    public void save() throws IOException {
        if (!changed || properties == null || propertiesPath == null) {
            return;
        }
        Path parent = propertiesPath.getParent();
        if (parent == null) {
            // cannot happen, but Spotbugs keeps yelling
            return;
        }
        Files.createDirectories(propertiesPath.getParent());
        try (OutputStream os = Files.newOutputStream(propertiesPath)) {
            properties.store(os, null);
        }
    }

    /**
     * Simple regexp pattern for verifying an e-mail. Definition taken from
     * https://www.w3.org/TR/html52/sec-forms.html#valid-e-mail-address; does not support
     * internationalized domains well.
     */
    private static final Pattern EMAIL_PATTERN = Pattern
                    .compile("^[a-zA-Z0-9.!#$%&'*+\\/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$");

    public static String checkEmailAddress(String mail, Feedback fb) {
        String m;
        if (mail == null) {
            return null;
        } else {
            m = mail.trim();
        }
        if ("".equals(m)) {
            return null;
        }
        if (!EMAIL_PATTERN.matcher(m).matches()) {
            throw fb.failure("ERR_EmailNotValid", null, m);
        }
        return mail;
    }
}
