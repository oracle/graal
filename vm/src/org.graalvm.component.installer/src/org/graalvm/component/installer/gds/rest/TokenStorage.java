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

import static org.graalvm.component.installer.CommonConstants.PATH_USER_CREDENTIALS;
import static org.graalvm.component.installer.CommonConstants.PATH_USER_GRAALVM;
import static org.graalvm.component.installer.CommonConstants.SYSPROP_USER_HOME;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.graalvm.component.installer.Feedback;
import java.util.Map;

/**
 *
 * @author odouda
 */
public class TokenStorage {
    private final Feedback feedback;
    private final Path propertiesPath;

    private Properties properties;
    private boolean changed;

    public TokenStorage(Feedback feedback) {
        this.feedback = feedback;
        propertiesPath = Path.of(System.getProperty(SYSPROP_USER_HOME), PATH_USER_GRAALVM, PATH_USER_CREDENTIALS);
    }

    private Properties load() {
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

    public Map.Entry<String, String> getToken() {
        load();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            return Map.entry((String) entry.getKey(), (String) entry.getValue());
        }
        return null;
    }

    public void setToken(Map.Entry<String, String> token) {
        if (token == null) {
            throw new IllegalArgumentException("Download Token cannot be null.");
        }
        Properties props = load();
        Map.Entry<String, String> p = getToken();
        if (token.equals(p)) {
            return;
        }
        if (p != null) {
            props.remove(p);
        }
        props.setProperty(token.getKey(), token.getValue());
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
}
