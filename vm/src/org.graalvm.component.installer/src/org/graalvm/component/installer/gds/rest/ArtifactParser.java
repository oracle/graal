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
import com.oracle.truffle.tools.utils.json.JSONObject;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.SystemUtils.OS;
import org.graalvm.component.installer.SystemUtils.ARCH;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.StabilityLevel;
import org.graalvm.component.installer.persist.HeaderParser;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 *
 * @author odouda
 */
class ArtifactParser {
    private static final String JSON_META = "metadata";
    private static final String JSON_META_KEY = "key";
    private static final String JSON_META_VAL = "value";
    private static final String JSON_ID = "id";
    private static final String JSON_NAME = "displayName";
    private static final String JSON_LICENSE = "licenseId";
    private static final String JSON_LICENSE_LABEL = "licenseName";
    private static final String JSON_HASH = "checksum";

    private static final String META_VERSION = "version";
    private static final String META_EDITION = "edition";
    private static final String META_JAVA = "java";
    private static final String META_ARCH = "arch";
    private static final String META_OS = "os";
    private static final String META_STABILITY_LEVEL = "stabilityLevel";
    private static final String META_STABILITY = "stability";
    private static final String META_SYMBOLIC_NAME = "symbolicName";
    private static final String META_DEPENDENCY = "requireBundle";
    private static final String META_REQUIRED = "requiredCapabilities";
    private static final String META_PROVIDED = "providedCapabilities";
    private static final String META_WORK_DIR = "workingDirectories";

    private final JSONObject json;

    ArtifactParser(JSONObject json) {
        if (json == null) {
            throw new IllegalArgumentException("Parsed Artifact JSON cannot be null.");
        }
        this.json = json;
        checkContent();
    }

    private void checkContent() {
        getId();
        getChecksum();
        getMetadata();
        abreviatedDisplayName();
        getLicenseId();
        getLicenseName();
    }

    public String getVersion() {
        return getMetadata(META_VERSION);
    }

    public String getJava() {
        String java = getMetadata(META_JAVA, () -> Integer.toString(SystemUtils.getJavaMajorVersion()));
        if (java.startsWith("jdk")) {
            java = java.substring(3);
        }
        return java;
    }

    public String getArch() {
        return getMetadata(META_ARCH, () -> ARCH.get().getName());
    }

    public String getOs() {
        return getMetadata(META_OS, () -> OS.get().getName());
    }

    public String getEdition() {
        return getMetadata(META_EDITION);
    }

    private JSONArray getMetadata() {
        return json.getJSONArray(JSON_META);
    }

    private String getId() {
        return json.getString(JSON_ID);
    }

    public String getLabel() {
        return getMetadata(META_SYMBOLIC_NAME);
    }

    private String getDisplayName() {
        return json.getString(JSON_NAME);
    }

    private String getLicenseId() {
        return json.getString(JSON_LICENSE);
    }

    private String getChecksum() {
        return json.getString(JSON_HASH);
    }

    private String getLicenseName() {
        return json.getString(JSON_LICENSE_LABEL);
    }

    private String getStability() {
        return getMetadata(META_STABILITY_LEVEL, () -> getMetadata(META_STABILITY));
    }

    private String getRequiredDependency() {
        return getMetadata(META_DEPENDENCY);
    }

    private String getRequiredCapabilities() {
        return getMetadata(META_REQUIRED);
    }

    private String getProvidedCapabilities() {
        return getMetadata(META_PROVIDED);
    }

    private String getWorkingDir() {
        return getMetadata(META_WORK_DIR);
    }

    public ComponentInfo asComponentInfo(GDSRESTConnector connector, Feedback fb) {
        return fillInComponent(
                        new ComponentInfo(
                                        getLabel(),
                                        abreviatedDisplayName(),
                                        getVersion(),
                                        getChecksum()),
                        connector,
                        fb);
    }

    private ComponentInfo fillInComponent(ComponentInfo info, GDSRESTConnector connector, Feedback fb) {
        info.addRequiredValues(translateRequiredValues(fb));
        info.addProvidedValues(translateProvidedValues(fb));
        info.setDependencies(translateDependency(fb));
        info.setStability(translateStability());
        info.addWorkingDirectories(translateWorkingDirs());
        info.setLicenseType(getLicenseName());
        info.setLicensePath(connector.makeLicenseURL(getLicenseId()));
        info.setOrigin(connector.makeArtifactsURL(getJava()));
        info.setRemoteURL(connector.makeArtifactDownloadURL(getId()));
        info.setShaDigest(SystemUtils.toHashBytes(getChecksum()));
        return info;
    }

    private Map<String, String> translateRequiredValues(Feedback fb) {
        String req = getRequiredCapabilities();
        if (req == null) {
            return Collections.emptyMap();
        }
        return new HeaderParser("GDS Required capabilities.", req, fb).parseRequiredCapabilities();
    }

    private String abreviatedDisplayName() {
        String dispName = getDisplayName();
        String osName = getOs();
        if (OS.fromName(osName) == OS.MAC) {
            osName = dispName.contains(CommonConstants.OS_TOKEN_MAC)
                            ? CommonConstants.OS_TOKEN_MAC
                            : CommonConstants.OS_MACOS_DARWIN;
        }
        return dispName.substring(0, dispName.indexOf(osName)).trim();
    }

    private Map<String, Object> translateProvidedValues(Feedback fb) {
        String prov = getProvidedCapabilities();
        if (prov == null || prov.isBlank()) {
            return Collections.emptyMap();
        }
        return new HeaderParser("GDS Provided capabilities.", prov, fb).parseProvidedCapabilities();
    }

    private Collection<String> translateWorkingDirs() {
        String dir = getWorkingDir();
        if (dir == null || dir.isBlank()) {
            return Collections.emptyList();
        }
        return Collections.singleton(dir);
    }

    private StabilityLevel translateStability() {
        return StabilityLevel.fromName(getStability());
    }

    private Set<String> translateDependency(Feedback fb) {
        String dep = getRequiredDependency();
        if (dep == null || dep.isBlank()) {
            return Collections.emptySet();
        }
        return new HeaderParser("GDS Dependencies.", dep, fb).parseDependencies();
    }

    private String getMetadata(String key) {
        return getMetadata(key, () -> null);
    }

    private String getMetadata(String key, Supplier<String> defValue) {
        for (Object o : getMetadata()) {
            JSONObject mo = (JSONObject) o;
            if (key.equals(mo.getString(JSON_META_KEY))) {
                return mo.getString(JSON_META_VAL);
            }
        }
        return defValue.get();
    }

    @Override
    public String toString() {
        return json.toString();
    }
}
