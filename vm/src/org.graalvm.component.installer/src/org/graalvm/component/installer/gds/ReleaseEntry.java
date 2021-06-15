/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.graalvm.component.installer.Version;

/**
 * Entry from the release.json file. Note the file's structure is not documented anywhere; let the
 * content in the org/graalvm/component/installer/commands/data/releases.json serve as a
 * specification.
 * 
 * @author sdedic
 */
public final class ReleaseEntry {
    private final String id;
    private final String label;
    private final Version version;
    private final URL licenseURL;
    private final URL catalogURL;

    private String licenseLabel;
    private String edition;
    private String javaVersion;

    private final List<BasePackage> basePackages = new ArrayList<>();

    public ReleaseEntry(String aId, String aLabel, Version aVersion, URL aLicenseURL, URL aCatalogURL) {
        this.id = aId;
        this.label = aLabel;
        this.version = aVersion;
        this.licenseURL = aLicenseURL;
        this.catalogURL = aCatalogURL;
    }

    public String getLicenseLabel() {
        return licenseLabel;
    }

    void setLicenseLabel(String licenseLabel) {
        this.licenseLabel = licenseLabel;
    }

    public String getId() {
        return id;
    }

    public String getEdition() {
        return edition;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    void setJavaVersion(String aJavaVersion) {
        this.javaVersion = aJavaVersion;
    }

    void setEdition(String edition) {
        this.edition = edition;
    }

    public String getLabel() {
        return label;
    }

    public Version getVersion() {
        return version;
    }

    public URL getLicenseURL() {
        return licenseURL;
    }

    public URL getCatalogURL() {
        return catalogURL;
    }

    public List<BasePackage> getBasePackages() {
        return Collections.unmodifiableList(basePackages);
    }

    public void addBasePackage(BasePackage p) {
        basePackages.add(p);
    }

    public static class BasePackage {
        private final String os;
        private final String arch;
        private final URL downloadURL;

        public BasePackage(String aOs, String aArch, URL aDonloadURL) {
            this.os = aOs;
            this.arch = aArch;
            this.downloadURL = aDonloadURL;
        }

        public String getOs() {
            return os;
        }

        public String getArch() {
            return arch;
        }

        public URL getDownloadURL() {
            return downloadURL;
        }
    }

    @Override
    public String toString() {
        return "Release[" + id + ", java=" + getJavaVersion() + ", ed=" + getEdition() + "]";
    }
}
