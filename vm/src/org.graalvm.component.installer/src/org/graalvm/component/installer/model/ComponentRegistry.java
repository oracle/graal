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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.Version;

/**
 * Models catalog of installed components. Works closely with {@link ComponentStorage} which handles
 * serialization.
 */
public final class ComponentRegistry {
    private final ComponentStorage storage;
    private final Feedback env;
    
    /**
     * The registry of installed components
     */
    private ComponentRegistry installedRegistry;

    /**
     * All components have been loaded.
     */
    private boolean allLoaded;

    /**
     * Indexes files path -> component(s).
     */
    private Map<String, Collection<String>> fileIndex;
    
    /**
     * For each component ID, a list of components, in their ascending
     * Version order.
     */
    private Map<String, List<ComponentInfo>> components = new HashMap<>();
    private Map<String, String> graalAttributes;
    private Map<String, Collection<String>> replacedFiles;
    private Set<String> componentDirectories;

    /**
     * True, if replaced files have been changed.
     */
    private boolean replaceFilesChanged;
    
    /**
     * Allows update to a newer distribution, not just patches.
     * This will cause components from newer GraalVM distributions to be accepted, even though it means a reinstall.
     * Normally just minor patches are accepted so the current component can be replaced.
     */
    private boolean allowDistUpdate;

    public ComponentRegistry(Feedback env, ComponentStorage storage) {
        this(env, null, storage);
    }
    
    public ComponentRegistry(Feedback env, ComponentRegistry installedReg, ComponentStorage storage) {
        this.storage = storage;
        this.env = env;
        this.installedRegistry = installedReg;
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
     * Should return a most recent Component, matching the current
     * state. With a special option 
     * @param id
     * @return 
     */
    private ComponentInfo mostRecentComponent(String id, Version.Match versionSelect) {
        return mostRecentComponent(id, versionSelect, false);
    }

    /**
     * @return True, if components from newer distributions are allowed.
     */
    public boolean isAllowDistUpdate() {
        return allowDistUpdate;
    }

    /**
     * Enables components from newer distributions. 
     * @param allowDistUpdate 
     */
    public void setAllowDistUpdate(boolean allowDistUpdate) {
        this.allowDistUpdate = allowDistUpdate;
    }
    
    private ComponentInfo mostRecentComponent(String id, Version.Match versionSelect, boolean fallback) {
        if (id == null) {
            return null;
        }
        return mostRecentComponent(components.get(id), versionSelect, fallback);
    }
    
    private ComponentInfo mostRecentComponent(Collection<ComponentInfo> infos, Version.Match versionSelect, boolean fallback) {
        if (infos == null) {
            return null;
        }
        List<ComponentInfo> cis = new ArrayList<>(infos);
        Collections.sort(cis, ComponentInfo.versionComparator());
        return compatibleComponent(cis, versionSelect, fallback);
    }
    
    private ComponentInfo compatibleComponent(List<ComponentInfo> cis, Version.Match versionSelect, boolean fallback) {
        if (cis == null) {
            return null;
        }
        ComponentInfo first = null;
        
        for (int i = cis.size() - 1; i >= 0; i--) {
            ComponentInfo ci = cis.get(i);
            if (first == null) {
                first = ci;
            }
            Version v = ci.getVersion();
            if (!versionSelect.test(v)) {
                continue;
            }
            if (compatibleVersion(v)) {
                return ci;
            }
        }
        return fallback ? first : null;
    }
    
    private Version.Match matchInstalledVersion() {
        return getGraalVersion().installVersion().match(Version.Match.Type.EXACT);
    }
    
    public ComponentInfo findComponent(String id) {
        if (id == null) {
            return null;
        }
        Version.Match[] matchV = new Version.Match[1];
        Version.Match vm = matchV[0];
        if (vm == null || vm.equals(Version.NO_VERSION)) {
            vm = matchInstalledVersion();
        }
        id = Version.idAndVersion(id, matchV);
        if (!allLoaded) {
            Collection<ComponentInfo> infos = loadComponents(id, vm, false, false);
            if (infos == null || infos.isEmpty()) {
                return null;
            }
            return infos.iterator().next();
        }
        ComponentInfo ci = mostRecentComponent(id, vm);
        if (ci != null) {
            return ci;
        }
        return mostRecentComponent(findAbbreviatedId(id), vm);
    }

    private String findAbbreviatedId(String id) {
        String candidate = null;
        String end = "." + id.toLowerCase(); // NOI18N
        for (String s : getComponentIDs()) {
            if (s.equals(id)) {
                return id;
            }
            if (s.toLowerCase().endsWith(end)) {
                if (candidate != null) {
                    throw env.failure("COMPONENT_AmbiguousIdFound", null, candidate, s);
                }
                candidate = s;
            }
        }
        return candidate;
    }

    public Map<String, String> getGraalCapabilities() {
        if (graalAttributes != null) {
            return graalAttributes;
        }
        graalAttributes = storage.loadGraalVersionInfo();
        return graalAttributes;
    }

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
            Collection<ComponentInfo> col = components.get(id);
            if (col != null) {
                col.remove(info);
                if (col.isEmpty()) {
                    components.remove(id);
                }
            }
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
        List<ComponentInfo> newInfos = components.computeIfAbsent(id, (x) -> new ArrayList<>());
        if (!newInfos.contains(info)) {
            newInfos.add(info);
            Collections.sort(newInfos, ComponentInfo.versionComparator());
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
        Version.Match[] matchV = new Version.Match[1];
        id = Version.idAndVersion(id, matchV);
        Collection<ComponentInfo> infos = loadComponents(id, matchV[0], filelist, false);
        return infos == null || infos.isEmpty() ? null : infos.iterator().next();
    }
    
    public Collection<ComponentInfo> loadComponents(String id, Version.Match selector, boolean filelist) {
        return loadComponents(id, selector, filelist, false);
    }
    
    Collection<ComponentInfo> loadComponents(String id, Version.Match vmatch, boolean filelist, boolean notFoundFailure) {
        String fid = findAbbreviatedId(id);
        if (fid == null) {
            if (notFoundFailure) {
                throw env.failure("REMOTE_UnknownComponentId", null, id);
            } else {
                return null;
            }
        }
        if (vmatch.getType() == Version.Match.Type.MOSTRECENT) {
            ComponentInfo info = mostRecentComponent(id, vmatch);
            if (info != null) {
                return Collections.singletonList(info);
            }
        } else {
            List<ComponentInfo> v = components.get(fid);
            if (v != null) {
                if (vmatch.getType() == Version.Match.Type.MOSTRECENT) {
                    ComponentInfo comp = compatibleComponent(v, vmatch, true);
                    return comp == null ? Collections.emptyList() : Collections.singleton(comp);
                }
                List<ComponentInfo> versions = new ArrayList<>(v);
                for (Iterator<ComponentInfo> it = versions.iterator(); it.hasNext(); ) {
                    ComponentInfo cv = it.next();
                    if (!vmatch.test(cv.getVersion())) {
                        it.remove();
                    }
                }
                return versions;
            }
        }
        try {
            Set<ComponentInfo> infos = storage.loadComponentMetadata(fid);
            if (infos == null || infos.isEmpty()) {
                if (notFoundFailure) {
                    throw env.failure("REMOTE_UnknownComponentId", null, id);
                }
                return null;
            }
            List<ComponentInfo> versions = new ArrayList<>(infos);
            Collections.sort(versions, ComponentInfo.versionComparator());
            if (filelist) {
                for (ComponentInfo ci : versions) {
                    storage.loadComponentFiles(ci);
                    addComponentToCache(ci);
                }
            }
            if (vmatch.getType() == Version.Match.Type.MOSTRECENT) {
                ComponentInfo comp = compatibleComponent(versions, vmatch, true);
                return comp == null ? Collections.emptyList() : Collections.singleton(comp);
            }
            return versions;
        } catch (NoSuchFileException ex) {
            return null;
        } catch (IOException ex) {
            throw env.failure("REGISTRY_ReadingComponentMetadata", ex, id, ex.getLocalizedMessage(), fid);
        }
    }

    private void buildFileIndex() {
        // first load all the components
        if (fileIndex != null) {
            return;
        }
        loadAllComponents();
        componentDirectories = new HashSet<>();
        fileIndex = new HashMap<>();
        for (String cid : components.keySet()) {
            ComponentInfo nfo = mostRecentComponent(cid, matchInstalledVersion(), true);
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
        try {
            storage.recordLicenseAccepted(info, id, text);
        } catch (IOException ex) {
            env.error("ERROR_RecordLicenseAccepted", ex, ex.getLocalizedMessage());
        }
    }
    
    private Version graalVer;
    
    public Version getGraalVersion() {
        if (graalVer == null) {
            graalVer = Version.fromString(SystemUtils.normalizeOldVersions(getGraalCapabilities().get(CommonConstants.CAP_GRAALVM_VERSION)));
        }
        return graalVer;
    }
}
