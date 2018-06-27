/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.model;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Information about an installable Component.
 */
public final class ComponentInfo {
    /**
     * Component ID.
     */
    private final String id;

    /**
     * Version of the component.
     */
    private final String versionString;

    /**
     * Human-readable name of the component.
     */
    private final String name;

    /**
     * During (un)installation only: the path for the .component info file.
     */
    private String infoPath;

    /**
     * Versioned license file path.
     */
    private String licensePath;

    /**
     * Assertions on graalVM installation.
     */
    private final Map<String, String> requiredGraalValues = new HashMap<>();

    private final List<String> paths = new ArrayList<>();

    private final Set<String> workingDirectories = new LinkedHashSet<>();

    private URL remoteURL;

    private boolean polyglotRebuild;

    private byte[] shaDigest;

    private String postinstMessage;

    public ComponentInfo(String id, String name, String versionString) {
        this.id = id;
        this.versionString = versionString;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getVersionString() {
        return versionString;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getRequiredGraalValues() {
        return Collections.unmodifiableMap(requiredGraalValues);
    }

    public void addRequiredValues(Map<String, String> vals) {
        requiredGraalValues.putAll(vals);
    }

    public void addRequiredValue(String s, String val) {
        if (val == null) {
            requiredGraalValues.remove(s);
        } else {
            requiredGraalValues.put(s, val);
        }
    }

    public void addPaths(List<String> filePaths) {
        paths.addAll(filePaths);
    }

    public void setPaths(List<String> filePaths) {
        paths.clear();
        addPaths(filePaths);
    }

    public List<String> getPaths() {
        return Collections.unmodifiableList(paths);
    }

    public String getInfoPath() {
        return infoPath;
    }

    public void setInfoPath(String infoPath) {
        this.infoPath = infoPath;
    }

    public String getLicensePath() {
        return licensePath;
    }

    public void setLicensePath(String licensePath) {
        this.licensePath = licensePath;
    }

    public URL getRemoteURL() {
        return remoteURL;
    }

    public void setRemoteURL(URL remoteURL) {
        this.remoteURL = remoteURL;
    }

    public byte[] getShaDigest() {
        return shaDigest;
    }

    public void setShaDigest(byte[] shaDigest) {
        this.shaDigest = shaDigest;
    }

    public boolean isPolyglotRebuild() {
        return polyglotRebuild;
    }

    public void setPolyglotRebuild(boolean polyglotRebuild) {
        this.polyglotRebuild = polyglotRebuild;
    }

    public Set<String> getWorkingDirectories() {
        return workingDirectories;
    }

    public void addWorkingDirectories(Collection<String> dirs) {
        workingDirectories.addAll(dirs);
    }

    public String getPostinstMessage() {
        return postinstMessage;
    }

    public void setPostinstMessage(String postinstMessage) {
        this.postinstMessage = postinstMessage;
    }
}
