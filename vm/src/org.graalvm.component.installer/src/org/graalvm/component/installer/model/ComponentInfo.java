/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.Version;

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
     * Parsed version of the Component.
     */
    private final Version version;

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
     * License type.
     */
    private String licenseType;

    /**
     * Assertions on graalVM installation.
     */
    private final Map<String, String> requiredGraalValues = new HashMap<>();

    private final List<String> paths = new ArrayList<>();

    private final Set<String> workingDirectories = new LinkedHashSet<>();

    private final Map<String, Object> providedValues = new HashMap<>();

    private URL remoteURL;

    private byte[] shaDigest;

    private String postinstMessage;

    private boolean nativeComponent;

    private String tag = "";

    /**
     * Component direct dependencies. Contains component canonical IDs.
     */
    private Set<String> dependencies = Collections.emptySet();

    /**
     * Origin of the component.
     */
    private String origin;

    /**
     * The distribution type.
     */
    private DistributionType distributionType = DistributionType.OPTIONAL;

    /**
     * Component priority.
     */
    private int priority;

    /**
     * Implicitly accepted license.
     */
    private boolean implicitlyAccepted = false;

    private StabilityLevel stability = StabilityLevel.Undefined;

    public ComponentInfo(String id, String name, String versionString, String tag) {
        this.id = id;
        this.versionString = versionString;
        this.name = name;
        this.version = Version.fromString(versionString);
        this.tag = tag;
    }

    public ComponentInfo(String id, String name, String versionString) {
        this(id, name, versionString, ""); // NOI18N
    }

    public ComponentInfo(String id, String name, Version v) {
        this.id = id;
        this.versionString = v == null ? null : v.originalString();
        this.name = name;
        this.version = v == null ? Version.NO_VERSION : v;
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

    public DistributionType getDistributionType() {
        return distributionType;
    }

    public void setDistributionType(DistributionType distributionType) {
        this.distributionType = distributionType;
    }

    public Map<String, String> getRequiredGraalValues() {
        return Collections.unmodifiableMap(requiredGraalValues);
    }

    public void addRequiredValues(Map<String, String> vals) {
        String os = vals.get(CommonConstants.CAP_OS_NAME);
        String arch = vals.get(CommonConstants.CAP_OS_ARCH);
        if (os != null) {
            String nos = SystemUtils.normalizeOSName(os, arch);
            if (!nos.equals(os)) {
                vals.put(CommonConstants.CAP_OS_NAME, nos);
            }
        }
        if (arch != null) {
            String narch = SystemUtils.normalizeArchitecture(os, arch);
            if (!narch.equals(os)) {
                vals.put(CommonConstants.CAP_OS_ARCH, narch);
            }
        }
        requiredGraalValues.putAll(vals);
    }

    public void addRequiredValue(String s, String val) {
        if (val == null) {
            requiredGraalValues.remove(s);
        } else {
            String v = val;
            switch (s) {
                case CommonConstants.CAP_OS_ARCH:
                    v = SystemUtils.normalizeArchitecture(null, val);
                    break;
                case CommonConstants.CAP_OS_NAME:
                    v = SystemUtils.normalizeOSName(val, null);
                    break;
                default:
                    break;
            }
            requiredGraalValues.put(s, v);
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

    public String getLicenseType() {
        return licenseType;
    }

    public void setLicenseType(String licenseType) {
        this.licenseType = licenseType;
    }

    public boolean isImplicitlyAccepted() {
        return implicitlyAccepted;
    }

    public void setImplicitlyAccepted(boolean implicitlyAccepted) {
        this.implicitlyAccepted = implicitlyAccepted;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 37 * hash + Objects.hashCode(this.id);
        hash = 37 * hash + Objects.hashCode(this.version);
        hash = 37 * hash + Objects.hashCode(this.tag);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ComponentInfo other = (ComponentInfo) obj;
        if (!Objects.equals(this.id, other.id)) {
            return false;
        }
        if (!Objects.equals(this.version, other.version)) {
            return false;
        }
        if (!Objects.equals(this.tag, other.tag)) {
            return false;
        }
        return true;
    }

    public Version getVersion() {
        return version;
    }

    private static Comparator<ComponentInfo> editionComparator(String myEdition) {
        return new Comparator<>() {
            @Override
            public int compare(ComponentInfo o1, ComponentInfo o2) {
                if (o1 == null) {
                    if (o2 == null) {
                        return 0;
                    } else {
                        return -1;
                    }
                } else if (o2 == null) {
                    return 1;
                }
                String ed1 = o1.getRequiredGraalValues().get(CommonConstants.CAP_CATALOG_EDITION);
                String ed2 = o2.getRequiredGraalValues().get(CommonConstants.CAP_CATALOG_EDITION);

                boolean m1 = Objects.equals(ed1, myEdition);
                boolean m2 = Objects.equals(ed2, myEdition);

                // one of the components exactly matches my edition:
                if (m1) {
                    return m2 ? 0 : -1;
                } else if (m2) {
                    return 1;
                }
                if (ed1 == null) {
                    ed1 = "";
                }
                if (ed2 == null) {
                    ed2 = "";
                }
                return ed1.compareToIgnoreCase(ed2);
            }
        };
    }

    private static final Comparator<ComponentInfo> COMPARATOR_VERSIONS = new Comparator<>() {
        @Override
        public int compare(ComponentInfo o1, ComponentInfo o2) {
            if (o1 == null) {
                if (o2 == null) {
                    return 0;
                } else {
                    return -1;
                }
            } else if (o2 == null) {
                return 1;
            }

            int n = o1.getVersion().compareTo(o2.getVersion());
            if (n == 0) {
                return o2.getPriority() - o1.getPriority();
            } else {
                return n;
            }
        }
    };

    @Override
    public String toString() {
        return getId() + "[" + getVersion().toString() +
                        (tag.isEmpty() ? "" : "/" + tag) + "]"; // NOI18N
    }

    public static Comparator<ComponentInfo> versionComparator() {
        return COMPARATOR_VERSIONS;
    }

    public static Comparator<ComponentInfo> reverseVersionComparator(ComponentStorage target) {
        String myEdition = target.loadGraalVersionInfo().get(CommonConstants.CAP_GRAALVM_VERSION);
        return versionComparator().reversed().thenComparing(editionComparator(myEdition));
    }

    public static Comparator<ComponentInfo> versionComparator(ComponentStorage target) {
        String myEdition = target.loadGraalVersionInfo().get(CommonConstants.CAP_GRAALVM_VERSION);
        return versionComparator().thenComparing(editionComparator(myEdition));
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public boolean isNativeComponent() {
        return nativeComponent;
    }

    public void setNativeComponent(boolean nativeComponent) {
        this.nativeComponent = nativeComponent;
    }

    public <T> void provideValue(String k, T v) {
        providedValues.put(k, v);
    }

    public <T> T getProvidedValue(String k, Class<T> type) {
        Object o = providedValues.get(k);
        if (!type.isInstance(o)) {
            if (type != String.class || o == null) {
                return null;
            }
            o = o.toString();
        }
        @SuppressWarnings("unchecked")
        T ret = (T) o;
        return ret;
    }

    public Map<String, Object> getProvidedValues() {
        return Collections.unmodifiableMap(providedValues);
    }

    public void addProvidedValues(Map<String, Object> vals) {
        for (String s : vals.keySet()) {
            provideValue(s, vals.get(s));
        }
    }

    public void setDependencies(Set<String> deps) {
        this.dependencies = deps;
    }

    public Set<String> getDependencies() {
        return Collections.unmodifiableSet(dependencies);
    }

    /**
     * @return True, if the Component is already installed.
     */
    public boolean isInstalled() {
        return infoPath != null;
    }

    public String getTag() {
        return tag;
    }

    /**
     * Sets the component tag. WARNING: do not use this after Component has been constructed; the
     * call will change the hashCode + equals !
     *
     * @param tag component tag/serial
     */
    public void setTag(String tag) {
        this.tag = tag;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public StabilityLevel getStability() {
        return stability;
    }

    public void setStability(StabilityLevel stab) {
        if (stab == null) {
            this.stability = StabilityLevel.Undefined;
        } else {
            this.stability = stab;
        }
    }
}
