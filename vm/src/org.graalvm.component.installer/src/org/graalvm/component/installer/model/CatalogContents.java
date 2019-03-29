/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.ComponentCollection;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.Version;

/**
 *
 * @author sdedic
 */
public class CatalogContents implements ComponentCollection {
    private static final List<ComponentInfo> NONE = new ArrayList<>();

    private final ComponentStorage storage;
    private final Feedback env;
    private final Map<String, List<ComponentInfo>> components = new HashMap<>();

    /**
     * The registry of installed components.
     */
    private final ComponentRegistry installedRegistry;

    private final Verifier verifier;

    /**
     * Allows update to a newer distribution, not just patches. This will cause components from
     * newer GraalVM distributions to be accepted, even though it means a reinstall. Normally just
     * minor patches are accepted so the current component can be replaced.
     */
    private boolean allowDistUpdate;

    public CatalogContents(Feedback env, ComponentStorage storage, ComponentRegistry installed) {
        this.storage = storage;
        this.env = env.withBundle(Feedback.class);
        this.installedRegistry = installed;
        this.verifier = new Verifier(env, installed, this);
        verifier.setCollectErrors(true);
        verifier.setVersionMatch(installed.getGraalVersion().match(Version.Match.Type.GREATER));
    }

    public boolean compatibleVersion(ComponentInfo info) {
        if (verifier.validateRequirements(info).hasErrors()) {
            return false;
        }
        Version v = info.getVersion();
        Version gv = installedRegistry.getGraalVersion();
        if (allowDistUpdate) {
            return gv.updatable().equals(v.updatable());
        } else {
            return gv.onlyVersion().equals(v.installVersion());
        }
    }

    /**
     * Should return a most recent Component, matching the current state. With a special option
     * 
     * @param id component id.
     * @return most recent component, or {@code null} if does not exist
     */
    private ComponentInfo mostRecentComponent(String id, Version.Match versionSelect, boolean fallback) {
        if (id == null) {
            return null;
        }
        Collection<ComponentInfo> infos = components.get(id);
        if (infos == NONE) {
            return null;
        }
        if (infos == null) {
            infos = loadComponents(id, versionSelect, fallback);
        }
        if (infos == null) {
            return null;
        }
        return mostRecentComponent(infos, versionSelect, fallback);
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
            if (compatibleVersion(ci)) {
                return ci;
            }
        }
        return fallback ? first : null;
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
    public void setAllowDistUpdate(boolean allowDistUpdate) {
        this.allowDistUpdate = allowDistUpdate;
    }

    @Override
    public ComponentInfo findComponent(String id) {
        return mostRecentComponent(id,
                        installedRegistry.getGraalVersion().match(
                                        allowDistUpdate ? Version.Match.Type.GREATER : Version.Match.Type.MOSTRECENT),
                        true);
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

    @Override
    public Collection<String> getComponentIDs() {
        try {
            return storage.listComponentIDs();
        } catch (IOException ex) {
            throw env.failure("REGISTRY_ReadingComponentList", ex, ex.getLocalizedMessage());
        }
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

    @Override
    public Collection<ComponentInfo> loadComponents(String id, Version.Match vmatch, boolean filelist) {
        String fid = findAbbreviatedId(id);
        if (fid == null) {
            return null;
        }
        List<ComponentInfo> v = components.get(fid);
        if (v == null) {
            try {
                Set<ComponentInfo> infos = storage.loadComponentMetadata(fid);
                if (infos == null || infos.isEmpty()) {
                    components.put(fid, NONE);
                    return null;
                }
                List<ComponentInfo> versions = new ArrayList<>(infos);
                Collections.sort(versions, ComponentInfo.versionComparator());
                if (filelist) {
                    for (ComponentInfo ci : infos) {
                        storage.loadComponentFiles(ci);
                    }
                    components.put(fid, versions);
                }
                v = versions;
            } catch (NoSuchFileException ex) {
                return null;
            } catch (IOException ex) {
                throw env.failure("REGISTRY_ReadingComponentMetadata", ex, id, ex.getLocalizedMessage(), fid);
            }
        }
        if (v == NONE) {
            return null;
        }
        if (vmatch.getType() == Version.Match.Type.MOSTRECENT) {
            ComponentInfo comp = compatibleComponent(v, vmatch, true);
            return comp == null ? Collections.emptyList() : Collections.singleton(comp);
        }
        List<ComponentInfo> versions = new ArrayList<>(v);
        for (Iterator<ComponentInfo> it = versions.iterator(); it.hasNext();) {
            ComponentInfo cv = it.next();
            if (!vmatch.test(cv.getVersion())) {
                it.remove();
            }
        }
        return versions;
    }
}
