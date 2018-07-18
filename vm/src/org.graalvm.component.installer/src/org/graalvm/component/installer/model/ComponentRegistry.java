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

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Set;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;

/**
 * Models catalog of installed components. Works closely with {@link ComponentStorage} which handles
 * serialization.
 */
public final class ComponentRegistry {
    private final ComponentStorage storage;
    private final Feedback env;

    /**
     * All components have been loaded.
     */
    private boolean allLoaded;

    /**
     * Indexes files path -> component(s).
     */
    private Map<String, Collection<String>> fileIndex;
    private Map<String, ComponentInfo> components = new HashMap<>();
    private Map<String, String> graalAttributes;
    private Map<String, Collection<String>> replacedFiles;
    private Set<String> componentDirectories;

    /**
     * True, if replaced files have been changed.
     */
    private boolean replaceFilesChanged;

    public ComponentRegistry(Feedback env, ComponentStorage storage) {
        this.storage = storage;
        this.env = env;
    }

    public ComponentInfo findComponent(String id) {
        if (!allLoaded) {
            return loadSingleComponent(id, false, false);
        }
        ComponentInfo ci = components.get(id);
        if (ci != null) {
            return ci;
        }
        String fullId = findAbbreviatedId(id);
        return fullId == null ? null : components.get(fullId);
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
            components.remove(id, info);
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
            components.put(id, info);
        }
        storage.saveComponent(info);
        updateReplacedFiles();
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
        return loadSingleComponent(id, filelist, false);
    }

    ComponentInfo loadSingleComponent(String id, boolean filelist, boolean notFoundFailure) {
        String fid = findAbbreviatedId(id);
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
            info = storage.loadComponentMetadata(fid);
            if (info == null) {
                if (notFoundFailure) {
                    throw env.failure("REMOTE_UnknownComponentId", null, id);
                }
                return null;
            }
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

    public String shortenComponentId(ComponentInfo info) {
        String id = info.getId();
        if (id.startsWith(CommonConstants.GRAALVM_CORE_PREFIX)) {
            String shortId = id.substring(CommonConstants.GRAALVM_CORE_PREFIX.length());
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
}
