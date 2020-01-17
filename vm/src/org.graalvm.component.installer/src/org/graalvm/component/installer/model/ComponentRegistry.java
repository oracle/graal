/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.ComponentCollection;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.Version;

/**
 * Models catalog of installed components. Works closely with {@link ComponentStorage} which handles
 * serialization.
 */
public final class ComponentRegistry implements ComponentCollection {
    private final ManagementStorage storage;
    private final Feedback env;

    /**
     * All components have been loaded.
     */
    private boolean allLoaded;

    /**
     * Indexes files path -> component(s).
     */
    private Map<String, Collection<String>> fileIndex;

    /**
     * For each component ID, a list of components, in their ascending Version order.
     */
    private Map<String, ComponentInfo> components = new HashMap<>();
    private Map<String, String> graalAttributes;
    private Map<String, Collection<String>> replacedFiles;
    private Set<String> componentDirectories;

    /**
     * True, if replaced files have been changed.
     */
    private boolean replaceFilesChanged;

    /**
     * Allows update to a newer distribution, not just patches. This will cause components from
     * newer GraalVM distributions to be accepted, even though it means a reinstall. Normally just
     * minor patches are accepted so the current component can be replaced.
     */
    private boolean allowDistUpdate;

    public ComponentRegistry(Feedback env, ManagementStorage storage) {
        this.storage = storage;
        this.env = env;
    }

    public boolean compatibleVersion(Version v) {
        Version gv = getGraalVersion();
        if (allowDistUpdate) {
            return gv.updatable().equals(v.updatable());
        } else {
            return gv.onlyVersion().equals(v.installVersion());
        }
    }

    /**
     * @return True, if components from newer distributions are allowed.
     */
    public boolean isAllowDistUpdate() {
        return allowDistUpdate;
    }

    /**
     * Enables components from newer distributions.
     * 
     * @param allowDistUpdate
     */
    @Override
    public void setAllowDistUpdate(boolean allowDistUpdate) {
        this.allowDistUpdate = allowDistUpdate;
    }

    @Override
    public ComponentInfo findComponentMatch(String idspec, Version.Match vm, boolean exact) {
        Version.Match[] vmatch = new Version.Match[1];
        String id = Version.idAndVersion(idspec, vmatch);
        if (!allLoaded) {
            return loadSingleComponent(id, false, false, exact);
        }
        ComponentInfo ci = components.get(id);
        if (ci != null) {
            return ci;
        }
        String fullId = exact ? id : findAbbreviatedId(id);
        return fullId == null ? null : components.get(fullId);
    }

    private String findAbbreviatedId(String id) {
        String candidate = null;
        String lcid = id.toLowerCase(Locale.ENGLISH);
        String end = "." + lcid; // NOI18N
        Collection<String> ids = getComponentIDs();
        String ambiguous = null;
        for (String s : ids) {
            String lcs = s.toLowerCase(Locale.ENGLISH);
            if (lcs.equals(lcid)) {
                return s;
            }
            if (lcs.endsWith(end)) {
                if (candidate != null) {
                    ambiguous = s;
                } else {
                    candidate = s;
                }
            }
        }
        if (ambiguous != null) {
            throw env.failure("COMPONENT_AmbiguousIdFound", null, candidate, ambiguous);
        }
        return candidate;
    }

    /**
     * Regexp to extract specification version. Optional {@code "1."} in front, optional
     * {@code ".micro_patchlevel"} suffix.
     */
    private static final Pattern JAVA_VERSION_PATTERN = Pattern.compile("((?:1\\.)?[0-9]+)([._].*)?"); // NOI18N

    public Map<String, String> getGraalCapabilities() {
        if (graalAttributes != null) {
            return graalAttributes;
        }
        Map<String, String> m = new HashMap<>(storage.loadGraalVersionInfo());
        String v = m.get(CommonConstants.CAP_JAVA_VERSION);
        if (v != null) {
            Matcher rm = JAVA_VERSION_PATTERN.matcher(v);
            if (rm.matches()) {
                v = rm.group(1);
            }
            int mv = SystemUtils.interpretJavaMajorVersion(v);
            if (mv < 1) {
                m.remove(CommonConstants.CAP_JAVA_VERSION);
            } else {
                m.put(CommonConstants.CAP_JAVA_VERSION, "" + mv); // NOI18N
            }
            graalAttributes = m;
        }
        // On JDK11, amd64 architecture name changed to x86_64.
        v = SystemUtils.normalizeArchitecture(
                        m.get(CommonConstants.CAP_OS_NAME),
                        m.get(CommonConstants.CAP_OS_ARCH));
        if (v != null) {
            m.put(CommonConstants.CAP_OS_ARCH, v);
        }
        v = SystemUtils.normalizeOSName(
                        m.get(CommonConstants.CAP_OS_NAME),
                        m.get(CommonConstants.CAP_OS_ARCH));
        if (v != null) {
            m.put(CommonConstants.CAP_OS_NAME, v);
        }
        return graalAttributes;
    }

    @Override
    public Collection<String> getComponentIDs() {
        if (!allLoaded) {
            try {
                return storage.listComponentIDs();
            } catch (IOException ex) {
                throw env.failure("REGISTRY_ReadingComponentList", ex, ex.getLocalizedMessage());
            }
        } else {
            return Collections.unmodifiableCollection(components.keySet());
        }
    }

    public void removeComponent(ComponentInfo info) throws IOException {
        replaceFilesChanged = false;
        buildFileIndex();
        String id = info.getId();
        for (String p : info.getPaths()) {
            Collection<String> compIds = fileIndex.get(p);
            if (compIds != null && compIds.remove(id)) {
                replaceFilesChanged = true;
                if (compIds.isEmpty()) {
                    fileIndex.remove(p);
                    componentDirectories.remove(p);
                }
            }
        }
        if (allLoaded) {
            components.remove(id);
        }
        storage.deleteComponent(id);
        updateReplacedFiles();
    }

    /**
     * Adds a Component to the registry. Will not save info, but will merge component information
     * with the rest.
     * 
     * @param info
     */
    public void addComponent(ComponentInfo info) throws IOException {
        replaceFilesChanged = false;
        // includes load all components
        buildFileIndex();
        String id = info.getId();
        for (String p : info.getPaths()) {
            Collection<String> compIds = fileIndex.computeIfAbsent(p, (k) -> new HashSet<>());
            replaceFilesChanged |= !compIds.isEmpty();
            compIds.add(id);
            if (p.endsWith("/")) {
                componentDirectories.add(p);
            }
        }
        if (allLoaded) {
            addComponentToCache(info);
        }
        storage.saveComponent(info);
        updateReplacedFiles();
    }

    private void addComponentToCache(ComponentInfo info) {
        String id = info.getId();
        ComponentInfo old = components.put(id, info);
        if (old != null) {
            throw new IllegalStateException("Replacing existing component");
        }
    }

    private void computeReplacedFiles() {
        Map<String, Collection<String>> shared = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>(fileIndex.keySet());
        Collections.sort(keys);
        for (String p : keys) {
            Collection<String> compIds = fileIndex.get(p);
            if (compIds != null && compIds.size() > 1) {
                shared.put(p, compIds);
            }
        }
        this.replacedFiles = shared;
    }

    private void updateReplacedFiles() throws IOException {
        if (!replaceFilesChanged) {
            return;
        }
        computeReplacedFiles();
        storage.updateReplacedFiles(replacedFiles);
    }

    public List<String> getPreservedFiles(ComponentInfo info) {
        buildFileIndex();

        List<String> result = new ArrayList<>();
        for (String p : info.getPaths()) {
            Collection<String> compIds = fileIndex.get(p);
            if (compIds != null && compIds.size() > 1) {
                result.add(p);
            }
        }
        // we should preserve this one
        result.add(CommonConstants.PATH_COMPONENT_STORAGE);
        return result;
    }

    public List<String> getOwners(String p) {
        buildFileIndex();
        Collection<String> compIds = fileIndex.get(p);
        if (compIds == null) {
            return Collections.emptyList();
        }
        List<String> ret = new ArrayList<>(compIds);
        Collections.sort(ret);
        return ret;
    }

    public boolean isReplacedFilesChanged() {
        return replaceFilesChanged;
    }

    private void loadAllComponents() {
        if (allLoaded) {
            return;
        }
        String currentId = null;
        try {
            Collection<String> ids = storage.listComponentIDs();
            components = new HashMap<>();
            for (String id : ids) {
                loadSingleComponent(id, true);
            }
            allLoaded = true;
        } catch (IOException ex) {
            throw env.failure("REGISTRY_ReadingComponentMetadata", ex, currentId, ex.getLocalizedMessage());
        }
    }

    public ComponentInfo loadSingleComponent(String id, boolean filelist) {
        return loadSingleComponent(id, filelist, false, false);
    }

    @Override
    public Collection<ComponentInfo> loadComponents(String id, Version.Match selector, boolean filelist) {
        ComponentInfo ci = loadSingleComponent(id, filelist);
        return ci == null ? null : Collections.singletonList(ci);
    }

    ComponentInfo loadSingleComponent(String id, boolean filelist, boolean notFoundFailure, boolean exact) {
        String fid = exact ? id : findAbbreviatedId(id);
        if (fid == null) {
            if (notFoundFailure) {
                throw env.failure("REMOTE_UnknownComponentId", null, id);
            } else {
                return null;
            }
        }
        ComponentInfo info = components.get(fid);
        if (info != null) {
            return info;
        }
        String cid = id;
        try {
            Collection<ComponentInfo> infos = storage.loadComponentMetadata(fid);
            if (infos == null || infos.isEmpty()) {
                if (notFoundFailure) {
                    throw env.failure("REMOTE_UnknownComponentId", null, id);
                }
                return null;
            }
            if (infos.size() != 1) {
                throw new IllegalArgumentException("Wrong storage");
            }
            info = infos.iterator().next();
            cid = info.getId(); // may change if id was an abbreviation
            if (filelist) {
                storage.loadComponentFiles(info);
                components.put(cid, info);
            }
        } catch (NoSuchFileException ex) {
            return null;
        } catch (IOException ex) {
            throw env.failure("REGISTRY_ReadingComponentMetadata", ex, id, ex.getLocalizedMessage(), fid);
        }
        return info;
    }

    private void buildFileIndex() {
        // first load all the components
        if (fileIndex != null) {
            return;
        }
        loadAllComponents();
        componentDirectories = new HashSet<>();
        fileIndex = new HashMap<>();
        for (ComponentInfo nfo : components.values()) {
            for (String path : nfo.getPaths()) {
                if (path.endsWith("/")) {
                    componentDirectories.add(path);
                }
                fileIndex.computeIfAbsent(path, (k) -> new ArrayList<>()).add(nfo.getId());
            }
        }
        computeReplacedFiles();
    }

    public List<String> getReplacedFiles(String path) {
        buildFileIndex();
        Collection<String> l = replacedFiles.get(path);
        if (l == null) {
            return Collections.emptyList();
        }
        List<String> ret = new ArrayList<>(l);
        Collections.sort(ret);
        return ret;
    }

    /**
     * Computes a set of directories created by components.
     * 
     * @return set of directories created by components
     */
    public Set<String> getComponentDirectories() {
        buildFileIndex();
        return componentDirectories;
    }

    public String localizeCapabilityName(String s) {
        String capName = "INSTALL_Capability_" + s.toLowerCase();
        String dispCapName;
        try {
            dispCapName = env.l10n(capName);
        } catch (MissingResourceException ex) {
            // some additional header
            dispCapName = s;
        }
        return dispCapName;
    }

    @Override
    public String shortenComponentId(ComponentInfo info) {
        String id = info.getId();
        if (id.startsWith(CommonConstants.GRAALVM_CORE_PREFIX)) {
            int l = CommonConstants.GRAALVM_CORE_PREFIX.length();
            if (id.length() == l) {
                return CommonConstants.GRAALVM_CORE_SHORT_ID;
            }
            if (id.charAt(l) != '.' && id.length() > l + 1) {
                return id;
            }
            String shortId = id.substring(l + 1);
            try {
                ComponentInfo reg = findComponent(shortId);
                if (reg == null || reg.getId().equals(id)) {
                    return shortId;
                }
            } catch (FailedOperationException ex) {
                // ambiguous, ignore
            }
        }
        return id;
    }

    public Date isLicenseAccepted(ComponentInfo info, String id) {
        return storage.licenseAccepted(info, id);
    }

    public void acceptLicense(ComponentInfo info, String id, String text) {
        acceptLicense(info, id, text, null);
    }

    public void acceptLicense(ComponentInfo info, String id, String text, Date d) {
        try {
            storage.recordLicenseAccepted(info, id, text, d);
        } catch (IOException ex) {
            env.error("ERROR_RecordLicenseAccepted", ex, ex.getLocalizedMessage());
        }
    }

    private Version graalVer;

    public Version getGraalVersion() {
        if (graalVer == null) {
            graalVer = Version.fromString(getGraalCapabilities().get(CommonConstants.CAP_GRAALVM_VERSION));
        }
        return graalVer;
    }

    public Map<String, Collection<String>> getAcceptedLicenses() {
        return storage.findAcceptedLicenses();
    }

    public String licenseText(String licId) {
        return storage.licenseText(licId);
    }

    public boolean isMacOsX() {
        return storage.loadGraalVersionInfo().get("os_name").toLowerCase().contains("macos");
    }

    public void verifyAdministratorAccess() throws IOException {
        storage.saveComponent(null);
    }

    /**
     * Finds components which depend on the supplied one. Optionally searches recursively, so it
     * finds the dependency closure.
     * 
     * @param recursive create closure of dependent components.
     * @param startFrom Component whose dependents should be returned.
     * @return Dependent components or closure thereof, depending on parameters
     */
    public Set<ComponentInfo> findDependentComponents(ComponentInfo startFrom, boolean recursive) {
        if (startFrom == null) {
            return Collections.emptySet();
        }
        Deque<String> toSearch = new ArrayDeque<>();
        toSearch.add(startFrom.getId());
        Set<ComponentInfo> result = new HashSet<>();

        while (!toSearch.isEmpty()) {
            String id = toSearch.poll();
            for (String cid : getComponentIDs()) {
                ComponentInfo ci = loadSingleComponent(cid, false, false, true);
                if (ci.getDependencies().contains(id)) {
                    result.add(ci);
                    if (recursive) {
                        toSearch.add(ci.getId());
                    }
                }
            }
        }
        return result;
    }

    public String getJavaVersion() {
        return getGraalCapabilities().get(CommonConstants.CAP_JAVA_VERSION);
    }
}
