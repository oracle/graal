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
package org.graalvm.component.installer.remote;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.CommandInput.CatalogFactory;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.ComponentCatalog;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.SoftwareChannelSource;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.model.GraalEdition;
import org.graalvm.component.installer.persist.DirectoryStorage;

/**
 * The class parses catalog definitions and builds a {@link GraalEdition} list of available
 * editions. It also serves as a factory for eventual lists for 'foreign graals' in different
 * directories at the same time.
 * <p>
 * For compatibility reasons it loads from various sources with with precedence as follows:
 * <ol>
 * <li>{@link #overrideCatalogSpec}, which should be set from {@code -C} or GRAALVM_CATALOG_URL
 * environment variable by installer launcher.
 * <li>catalog related properties from the release file; each software sources has an URL
 * (mandatory), label and potentially parameters.
 * <li>component_catalog property, which defines all the software sources in a single property
 * </ol>
 * The edition with empty ({@code ""}) id, the edition whose id matches
 * {@link CommonConstants#CAP_EDITION} from the release file (see {@link DirectoryStorage} for how
 * the defaults are computed) or the first edition found is the <b>default</b> one: its software
 * sources will be used to load the catalogs, unless overriden by a command switch.
 * 
 * @author sdedic
 */
public final class GraalEditionList implements CatalogFactory {
    static final String CAP_CATALOG_URL_SUFFIX = "_" + CommonConstants.CAP_CATALOG_URL; // NOI18N

    private final CommandInput input;
    private final ComponentRegistry targetGraal;
    private final Feedback feedback;

    /**
     * Editions, the default should be listed first.
     */
    private final List<GraalEdition> editions = new ArrayList<>();

    /**
     * Map ID > edition.
     */
    private final Map<String, GraalEdition> editionMap = new HashMap<>();

    /**
     * Cache of foreign GraalVM edition definitions. Note there is not identity-consistency between
     * foreign's foreign pointing back to this installation.
     */
    private final Map<ComponentRegistry, GraalEditionList> foreignGraals = new HashMap<>();

    /**
     * Hand-override, takes precedence over everything.
     */
    private String overrideCatalogSpec;

    /**
     * Default single-property definition, read from the release file. Lower priority than separated
     * release file properties.
     */
    private String defaultCatalogSpec;

    private GraalEdition defaultEdition;

    private boolean remoteSourcesAllowed = true;

    private List<SoftwareChannelSource> localSources = new ArrayList<>();

    /**
     * Compares property keys so that software sources can be ordered. It tries to numeric-order
     * according to the substring after {@code component_catalog_} prefix (
     * {@link CommonConstants#CAP_CATALOG_PREFIX}; strings having lower precedence than any any
     * number. Two nonnumeric strings are compared alphabetically.
     */
    private static final Comparator<String> CHANNEL_KEY_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
            int i1 = Integer.MAX_VALUE;
            int i2 = Integer.MAX_VALUE;

            String k1 = o1.substring(CommonConstants.CAP_CATALOG_PREFIX.length() - 1);
            String k2 = o2.substring(CommonConstants.CAP_CATALOG_PREFIX.length() - 1);
            if (k1.equals("")) {
                i1 = 0;
            } else {
                if (k1.startsWith("_")) {
                    k1 = k1.substring(1);
                }
                try {
                    i1 = Integer.parseInt(k1);
                } catch (NumberFormatException ex) {
                }
            }
            if (k2.equals("")) {
                i2 = 0;
            } else {
                if (k2.startsWith("_")) {
                    k2 = k2.substring(1);
                }
                try {
                    i2 = Integer.parseInt(k2);
                } catch (NumberFormatException ex) {
                }
            }
            if (i1 != i2) {
                return i1 - i2;
            }
            return k1.compareToIgnoreCase(k2);
        }
    };

    public GraalEditionList(Feedback feedback, CommandInput input, ComponentRegistry reg) {
        this.input = input;
        this.targetGraal = reg;
        this.feedback = feedback.withBundle(GraalEditionList.class);
    }

    public String getOverrideCatalogSpec() {
        return overrideCatalogSpec;
    }

    public void setOverrideCatalogSpec(String overrideCatalogSpec) {
        this.overrideCatalogSpec = overrideCatalogSpec;
    }

    public String getDefaultCatalogSpec() {
        return defaultCatalogSpec;
    }

    public void setDefaultCatalogSpec(String defaultCatalogSpec) {
        this.defaultCatalogSpec = defaultCatalogSpec;
    }

    /**
     * Returns the default GraalVM edition.
     * 
     * @return default edition.
     */
    public GraalEdition getDefaultEdition() {
        init();
        return defaultEdition;
    }

    public void setDefaultEdition(GraalEdition ed) {
        this.defaultEdition = ed;
    }

    /**
     * Lists all editions.
     * 
     * @return list of editions, the default one first.
     */
    public List<GraalEdition> editions() {
        init();
        return editions;
    }

    /**
     * Gets an edition with the specified ID. Throws an exception, if that edition does not exist.
     * 
     * @param id edition id. "" or null means default edition.
     * @return edition instance
     * @throws FailedOperationException if no such edition is configured.
     */
    public GraalEdition getEdition(String id) {
        if (id == null || "".equals(id)) {
            return getDefaultEdition();
        } else {
            init();
            GraalEdition e = editionMap.get(id.toLowerCase(Locale.ENGLISH));
            if (e == null) {
                throw feedback.failure("ERR_NoSuchEdition", null, id);
            }
            return e;
        }
    }

    @SuppressWarnings("ThrowableResultIgnored")
    List<SoftwareChannelSource> parseChannelSources(String edId, String overrideSpec) {
        List<SoftwareChannelSource> sources = new ArrayList<>();
        if (!remoteSourcesAllowed || overrideSpec == null) {
            return sources;
        }
        int priority = 1;
        String[] parts = overrideSpec.split("\\|"); // NOI18N
        String id = edId;
        if (id == null) {
            id = targetGraal.getGraalCapabilities().get(CommonConstants.CAP_EDITION);
        }
        for (String s : parts) {
            try {
                SoftwareChannelSource chs = new SoftwareChannelSource(s);
                chs.setPriority(priority);
                chs.setParameter("edition", id);
                sources.add(chs);
            } catch (MalformedURLException ex) {
                feedback.error("REMOTE_FailedToParseParameter", ex, s); // NOI18N
            }
            priority++;
        }
        return sources;
    }

    void parseSimpleSpecification(String defId, String spec) {
        if (spec == null) {
            return;
        }
        String[] eds = spec.split("\\{");
        for (String part : eds) {
            if ("".equals(part)) {
                continue;
            }
            String edName;
            String edId;

            String src = part;
            int endBracket = part.indexOf('}');
            if (endBracket != -1) {
                int eqSign = part.indexOf('=');
                if (eqSign == -1 || eqSign >= endBracket) {
                    edId = edName = part.substring(0, endBracket);
                } else {
                    edId = part.substring(0, eqSign);
                    edName = part.substring(eqSign + 1, endBracket);
                }
                src = part.substring(endBracket + 1).trim();
                if (src.endsWith("|")) {
                    src = src.substring(0, src.length() - 1);
                }
            } else {
                edId = defId != null ? defId : "ce";
                edName = getEditionLabel(edId);
                if (edName == null) {
                    edName = getEditionLabel(null);
                }
            }
            GraalEdition ge = new GraalEdition(edId, edName);
            boolean def = false;
            ge.setSoftwareSources(parseChannelSources(edId, src));
            if (defaultEdition == null && endBracket == -1) {
                def = true;
            } else if (edId.equals(defId)) {
                def = true;
            }
            registerEdition(ge, def);
        }
    }

    /**
     * @return true, if an explicit override is present.
     */
    boolean isExplicitOverride() {
        return overrideCatalogSpec != null;
    }

    void init() {
        if (defaultEdition != null) {
            return;
        }
        String defEditionId = targetGraal.getGraalCapabilities().get(CommonConstants.CAP_EDITION);

        if (isExplicitOverride()) {
            initSimple(defEditionId, overrideCatalogSpec);
            return;
        }
        List<SoftwareChannelSource> srcs = readChannelSources(defEditionId);
        if (srcs.isEmpty()) {
            // no source channels defined for the editionId
            srcs = readChannelSources(null);
        }

        if (srcs.isEmpty()) {
            initSimple(defEditionId, defaultCatalogSpec);
            return;
        }
        List<String> edList = listEditionsFromRelease();
        String label;
        List<SoftwareChannelSource> sources;

        if (edList.contains(defEditionId)) {
            edList.remove(defEditionId);
            label = getEditionLabel(defEditionId);
            sources = readChannelSources(defEditionId);
        } else if (!edList.remove("")) {
            throw new IllegalStateException("Malformed release file.");
        } else {
            label = getEditionLabel(null);
            sources = readChannelSources(null);
        }
        GraalEdition ge = new GraalEdition(defEditionId, label);
        ge.setSoftwareSources(sources);
        registerEdition(ge, true);
        for (String id : edList) {
            label = getEditionLabel(id);
            sources = readChannelSources(id);
            ge = new GraalEdition(id, label);
            ge.setSoftwareSources(sources);

            registerEdition(ge, false);
        }
    }

    private void registerEdition(GraalEdition ge, boolean defaultEd) {
        if (!editions.contains(ge)) {
            editions.add(ge);
        }
        if (defaultEd) {
            editionMap.put("", ge);
            defaultEdition = ge;
        }
        editionMap.put(ge.getId().toLowerCase(Locale.ENGLISH), ge);
    }

    /**
     * Regexp to select individual edition's properties from the release file. The property must
     * start with the edition id, separated by {@code "_"}) from the normal property prefix (
     * {@code component_catalog_}), followed by individual attributes of the software source
     * definition. Since URL is the only mandatory attribute, the regexp looks for that.
     */
    private static final String EDITION_MATCH_REGEXP = "(?:([^_]+)_)?component_catalog(_?.*)_url"; // NOI18N

    private List<String> listEditionsFromRelease() {
        Map<String, String> editionOrder = new HashMap<>();
        addEditions(
                        editionOrder,
                        targetGraal.getGraalCapabilities(),
                        Pattern.compile(EDITION_MATCH_REGEXP));
        addEditions(
                        editionOrder,
                        lowercaseMap(input.parameters(false)),
                        Pattern.compile(CommonConstants.ENV_CATALOG_PREFIX.toLowerCase(Locale.ENGLISH) + EDITION_MATCH_REGEXP));
        List<String> editionIds = new ArrayList<>(editionOrder.keySet());
        Collections.sort(editionIds, (a, b) -> CHANNEL_KEY_COMPARATOR.compare(editionOrder.get(a), editionOrder.get(b)));
        return editionIds;
    }

    private static void addEditions(Map<String, String> eds, Map<String, String> params, Pattern match) {
        for (String k : params.keySet()) {
            Matcher m = match.matcher(k);
            if (m.matches()) {
                String id = m.group(1);
                if (null == id) {
                    id = ""; // NOI18N
                }
                eds.putIfAbsent(id, k);
            }
        }
    }

    private void ensureDefaultDefined(String defEditionId) {
        GraalEdition ge;
        if (editions.isEmpty()) {
            String label = getEditionLabel(defEditionId);
            ge = new GraalEdition(defEditionId == null ? "" : defEditionId, label);
        } else if (defaultEdition == null) {
            ge = editions.get(0);
        } else {
            ge = defaultEdition;
        }
        registerEdition(ge, true);
    }

    /**
     * Performs single-property initialization.
     * 
     * @param defEditionId default edition's ID, as it appears (or defaults) in the release file.
     * @param spec specification string.
     */
    private void initSimple(String defEditionId, String spec) {
        parseSimpleSpecification(defEditionId, spec);
        if (editions.isEmpty()) {
            String label = getEditionLabel(defEditionId);
            GraalEdition ge = new GraalEdition(defEditionId, label);
            defaultEdition = ge;
            editions.add(ge);
        }
        foreignGraals.put(targetGraal, this);
        ensureDefaultDefined(defEditionId);
    }

    String getEditionLabel(String id) {
        String readPrefix = id == null ? "" : id + "_";
        String key = readPrefix + CommonConstants.CAP_CATALOG_PREFIX + "editionLabel";
        String label = input.getParameter(CommonConstants.ENV_VARIABLE_PREFIX + key.toUpperCase(Locale.ENGLISH), false);
        if (label == null) {
            label = targetGraal.getGraalCapabilities().get(key);
        }
        if (label == null) {
            label = targetGraal.getGraalCapabilities().get(CommonConstants.CAP_EDITION);
            if (label == null) {
                if ("".equals(id) || id == null) {
                    return "CE"; // NOI18N
                } else {
                    return id.toUpperCase(Locale.ENGLISH);
                }
            } else {
                return label.toUpperCase(Locale.ENGLISH);
            }
        }
        return label;
    }

    public boolean isRemoteSourcesAllowed() {
        return remoteSourcesAllowed;
    }

    public void setRemoteSourcesAllowed(boolean remoteSourcesAllowed) {
        this.remoteSourcesAllowed = remoteSourcesAllowed;
    }

    private static Map<String, String> lowercaseMap(Map<String, String> map) {
        Map<String, String> res = new HashMap<>();
        for (String s : map.keySet()) {
            res.put(s.toLowerCase(Locale.ENGLISH), map.get(s));
        }
        return res;
    }

    List<SoftwareChannelSource> readChannelSources(String editionPrefix) {
        List<SoftwareChannelSource> res;
        String readPrefix = editionPrefix == null ? "" : editionPrefix + "_";
        Map<String, String> lcEnv = lowercaseMap(input.parameters(false));
        res = readChannelSources(editionPrefix, CommonConstants.ENV_VARIABLE_PREFIX.toLowerCase(Locale.ENGLISH) + readPrefix, lcEnv);
        if (res != null && !res.isEmpty()) {
            return res;
        }
        if (remoteSourcesAllowed) {
            return readChannelSources(editionPrefix, readPrefix, input.getLocalRegistry().getGraalCapabilities()); // NOI18N
        } else {
            List<SoftwareChannelSource> l = new ArrayList<>();
            return l;
        }
    }

    List<SoftwareChannelSource> readChannelSources(String id, String pref, Map<String, String> graalCaps) {
        List<SoftwareChannelSource> sources = new ArrayList<>();
        if (!remoteSourcesAllowed) {
            return sources;
        }
        String prefix = pref + CommonConstants.CAP_CATALOG_PREFIX;
        List<String> orderedKeys = graalCaps.keySet().stream().filter((k) -> {
            String lk = k.toLowerCase(Locale.ENGLISH);
            return lk.startsWith(prefix) && lk.endsWith(CAP_CATALOG_URL_SUFFIX);
        }).map((k) -> k.substring(0, k.length() - CAP_CATALOG_URL_SUFFIX.length())).collect(Collectors.toList());
        Collections.sort(orderedKeys, CHANNEL_KEY_COMPARATOR);

        int priority = 0;
        for (String key : orderedKeys) {
            String url = graalCaps.get(key + CAP_CATALOG_URL_SUFFIX);
            String lab = graalCaps.get(key + "_" + CommonConstants.CAP_CATALOG_LABEL);
            if (url == null) {
                continue;
            }
            SoftwareChannelSource s = new SoftwareChannelSource(url, lab);
            s.setPriority(priority);
            for (String a : graalCaps.keySet()) {
                if (!(a.startsWith(key) && a.length() > key.length() + 1)) {
                    continue;
                }
                String k = a.substring(key.length() + 1).toLowerCase(Locale.ENGLISH);
                switch (k) {
                    case CommonConstants.CAP_CATALOG_LABEL:
                    case CommonConstants.CAP_CATALOG_URL:
                        continue;
                }
                s.setParameter(k, graalCaps.get(a));
            }
            if (s.getParameter("edition") == null) {
                s.setParameter("edition", id != null ? id : targetGraal.getGraalCapabilities().get(CommonConstants.CAP_EDITION));
            }

            sources.add(s);
            priority++;
        }
        return sources;
    }

    RemoteCatalogDownloader createEditionDownloader(GraalEdition edition) {
        GraalEdition ed = edition;
        if (ed == null) {
            ed = defaultEdition;
        }
        RemoteCatalogDownloader dn = new RemoteCatalogDownloader(input, feedback, overrideCatalogSpec);
        Stream.concat(ed.getSoftwareSources().stream(), localSources.stream()).forEach(dn::addLocalChannelSource);
        // FIXME: temporary hack, remember to cleanup RemoteCatalogDownloader from debris, most of
        // functionality
        // has moved here.
        dn.setRemoteSourcesAllowed(false);
        return dn;
    }

    public void addLocalChannelSource(SoftwareChannelSource src) {
        src.setParameter("reportErrors", Boolean.FALSE.toString());
        localSources.add(src);
    }

    final class GE extends GraalEdition {
        GE(String id, String displayName) {
            super(id, displayName);
        }

        @Override
        public SoftwareChannel getCatalogProvider() {
            SoftwareChannel ch = super.getCatalogProvider();
            if (ch == null) {
                ch = createEditionDownloader(this);
                setCatalogProvider(ch);
            }
            return ch;
        }
    }

    GraalEditionList listGraalEditions(CommandInput in, ComponentRegistry otherGraal) {
        return foreignGraals.computeIfAbsent(otherGraal, (og) -> {
            GraalEditionList gl = new GraalEditionList(feedback, in, og);
            gl.setRemoteSourcesAllowed(remoteSourcesAllowed);
            String defCatalog = og.getGraalCapabilities().get(CommonConstants.RELEASE_CATALOG_KEY);
            gl.setDefaultCatalogSpec(defCatalog);
            return gl;
        });
    }

    /**
     * Cached instance of catalog, the storage tracks origin of supplied ComponentInfos.
     */
    private ComponentCatalog catalog;

    @Override
    public ComponentCatalog createComponentCatalog(CommandInput in) {
        ComponentRegistry targetGraalVM = in.getLocalRegistry();
        if (targetGraalVM != this.targetGraal) {
            GraalEditionList gl = listGraalEditions(in, targetGraalVM);
            return gl.createComponentCatalog(in);
        }
        if (catalog != null) {
            return catalog;
        }
        String edId = in.optValue(Commands.OPTION_USE_EDITION, "");
        GraalEdition ge = getEdition(edId);
        RemoteCatalogDownloader downloader = createEditionDownloader(ge);
        CatalogContents col = new CatalogContents(feedback, downloader.getStorage(), targetGraalVM);
        return catalog = col;
    }

    @Override
    public List<GraalEdition> listEditions(ComponentRegistry targetGraalVM) {
        return editions();
    }
}
