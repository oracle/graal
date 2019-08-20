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
import java.util.Locale;
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
    private final Version graalVersion;

    private final Verifier verifier;

    /**
     * Allows update to a newer distribution, not just patches. This will cause components from
     * newer GraalVM distributions to be accepted, even though it means a reinstall. Normally just
     * minor patches are accepted so the current component can be replaced.
     */
    private boolean allowDistUpdate;

    public CatalogContents(Feedback env, ComponentStorage storage, ComponentRegistry installed) {
        this(env, storage, installed, installed.getGraalVersion());
    }

    public CatalogContents(Feedback env, ComponentStorage storage, ComponentRegistry installed, Version version) {
        this.storage = storage;
        this.env = env.withBundle(Feedback.class);
        this.verifier = new Verifier(env, installed, this);
        this.graalVersion = version;
        verifier.ignoreExisting(true);
        verifier.setSilent(true);
        verifier.setCollectErrors(true);
        verifier.setVersionMatch(graalVersion.match(Version.Match.Type.SATISFIES));
    }

    public boolean compatibleVersion(ComponentInfo info) {
        // excludes components that depend on obsolete versions
        // excludes components that depend on
        if (verifier.validateRequirements(info).hasErrors()) {
            return false;
        }
        Version v = info.getVersion();
        Version gv = graalVersion;
        if (allowDistUpdate) {
            return gv.updatable().compareTo(v.updatable()) <= 0;
        } else {
            Version giv = gv.installVersion();
            Version civ = v.installVersion();
            return giv.equals(civ);
        }
    }

    private ComponentInfo compatibleComponent(List<ComponentInfo> cis, Version.Match versionSelect, boolean fallback) {
        if (cis == null) {
            return null;
        }
        ComponentInfo first = null;
        Version.Match vm = versionMatch(versionSelect, cis);
        boolean explicit = versionSelect != null && versionSelect.getType() != Version.Match.Type.MOSTRECENT;
        for (int i = cis.size() - 1; i >= 0; i--) {
            ComponentInfo ci = cis.get(i);
            if (first == null) {
                first = ci;
            }
            Version v = ci.getVersion();
            if (!vm.test(v)) {
                continue;
            }
            if (explicit || compatibleVersion(ci)) {
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
    @Override
    public void setAllowDistUpdate(boolean allowDistUpdate) {
        this.allowDistUpdate = allowDistUpdate;
    }

    private Version.Match versionMatch(Version.Match m, List<ComponentInfo> infos) {
        if (m != null && m.getType() != Version.Match.Type.MOSTRECENT) {
            return resolveMatch(m, infos, env);
        }
        Version v = m == null ? graalVersion : m.getVersion();
        if (v == Version.NO_VERSION) {
            v = graalVersion;
        }
        return v.match(allowDistUpdate ? Version.Match.Type.INSTALLABLE : Version.Match.Type.COMPATIBLE);
    }

    private static Version.Match resolveMatch(Version.Match vm, List<ComponentInfo> comps, Feedback f) {
        if (vm == null) {
            return null;
        }
        List<Version> vers = new ArrayList<>(comps.size());
        for (ComponentInfo ci : comps) {
            vers.add(ci.getVersion());
        }
        Collections.sort(vers);
        return vm.resolveWildcards(vers, f);
    }

    @Override
    public ComponentInfo findComponent(String id, Version.Match vm) {
        if (id == null) {
            return null;
        }
        List<ComponentInfo> infos = doLoadComponents(id, false);
        if (infos == null) {
            return null;
        }
        return compatibleComponent(infos, vm, false);
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
                Collection<ComponentInfo> regs = doLoadComponents(shortId, false);
                if (regs == null) {
                    return shortId;
                }
                boolean success = true;
                for (ComponentInfo reg : regs) {
                    if (!reg.getId().equals(id)) {
                        success = false;
                        break;
                    }
                }
                if (success) {
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
        String lcid = id.toLowerCase(Locale.ENGLISH);
        String end = "." + lcid; // NOI18N
        for (String s : getComponentIDs()) {
            String lcs = s.toLowerCase(Locale.ENGLISH);
            if (lcs.equals(lcid)) {
                return s;
            }
            if (lcs.endsWith(end)) {
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
        List<ComponentInfo> v = doLoadComponents(id, filelist);
        if (v == null) {
            return null;
        }
        if (vmatch.getType() == Version.Match.Type.MOSTRECENT) {
            ComponentInfo comp = compatibleComponent(v, versionMatch(vmatch, v), true);
            return comp == null ? Collections.emptyList() : Collections.singleton(comp);
        }
        Version.Match resolvedMatch = resolveMatch(vmatch, v, env);
        List<ComponentInfo> versions = new ArrayList<>(v);
        for (Iterator<ComponentInfo> it = versions.iterator(); it.hasNext();) {
            ComponentInfo cv = it.next();
            if (!resolvedMatch.test(cv.getVersion())) {
                it.remove();
            }
        }
        return versions;
    }

    private List<ComponentInfo> doLoadComponents(String id, boolean filelist) {
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
        return v;
    }
}
