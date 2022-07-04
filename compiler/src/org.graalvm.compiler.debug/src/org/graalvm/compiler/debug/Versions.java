/*
 * Copyright (c) 2012, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import jdk.vm.ci.services.Services;

/** Avoid using directly. Only public for the needs of unit testing. */
public final class Versions {
    static final Versions VERSIONS;
    static {
        String home = Services.getSavedProperties().get("java.home");
        VERSIONS = new Versions(home);
    }

    private final Map<Object, Object> versions;

    public Versions(String home) {
        Map<Object, Object> map = new HashMap<>();
        ASSIGN: try {
            String info = findReleaseInfo(home);
            if (info == null) {
                break ASSIGN;
            }

            try (InputStream in = PathUtilities.openInputStream(info)) {
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                for (String line = br.readLine(); line != null; line = br.readLine()) {
                    final String prefix = "SOURCE=";
                    if (line.startsWith(prefix)) {
                        for (String versionInfo : line.substring(prefix.length()).replace('"', ' ').split(" ")) {
                            String[] idVersion = versionInfo.split(":");
                            if (idVersion != null && idVersion.length == 2) {
                                map.put("version." + idVersion[0], idVersion[1]);
                            }
                        }
                        break ASSIGN;
                    }
                }
            }
        } catch (IOException ex) {
            // no versions file found
        }
        versions = Collections.unmodifiableMap(map);
    }

    public Map<Object, Object> withVersions(Map<Object, Object> properties) {
        if (properties == null) {
            return versions;
        } else {
            properties.putAll(versions);
            return properties;
        }
    }

    private static String findReleaseInfo(String jreDir) {
        if (jreDir == null) {
            return null;
        }
        String releaseInJre = PathUtilities.getPath(jreDir, "release");
        if (PathUtilities.exists(releaseInJre)) {
            return releaseInJre;
        }
        String jdkDir = PathUtilities.getParent(jreDir);
        if (jdkDir == null) {
            return null;
        }
        String releaseInJdk = PathUtilities.getPath(jdkDir, "release");
        return PathUtilities.exists(releaseInJdk) ? releaseInJdk : null;
    }

}
