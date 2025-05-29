/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package org.graalvm.visualizer.filter.profiles.impl;

import jdk.graal.compiler.graphio.parsing.model.Folder;
import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import jdk.graal.compiler.graphio.parsing.model.Properties.EqualityPropertyMatcher;
import jdk.graal.compiler.graphio.parsing.model.Properties.PropertyMatcher;
import jdk.graal.compiler.graphio.parsing.model.Properties.PropertySelector;
import jdk.graal.compiler.graphio.parsing.model.Properties.RegexpPropertyMatcher;
import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileStorage;
import org.graalvm.visualizer.filter.profiles.mgmt.SimpleProfileSelector;
import org.graalvm.visualizer.filter.profiles.spi.ProfileGraphMatcher;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem.AtomicAction;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * Matches filter profiles using conditions stored as Property files.
 * The implementation supports matches on
 * <ul>
 * <li>graph attributes, with prefix {@code matchGraph.}
 * <li>graph properties, with prefix {@code matchGraphProperty.}
 * <li>parent group properties, with prefix {@code matchGroupProperty.}
 * <li>contained node properties, with prefix {@code matchNode.}
 * </ul>
 * After the prefix (target object specification), the following is parsed:
 * {@code propertyName[,type]}, where "type" can be one of
 * <ul>
 * <li>float - a float number, numeric comparison
 * <li>number - a long, numeric comparison
 * <li>contains - matches, if contains a case-insensitive substring
 * <li>regexp - matches regexp, case-insensitive
 * <li>same - exact string match
 * </ul>
 * Numeric comparison support relational operators, they must start the value. The property's
 * value is used for match/comparison with the target object's value.
 * <p/>
 * It is also possible to provide a custom matcher implementation, with file attribute {@code profileGraphMatcher}.
 * This custom matcher is applied when all the 'standard' matches succeed - there can be no
 * standard matches at all, in which case the custom matcher will decide whether the profile matches or not.
 *
 * @author sdedic
 */
@ServiceProvider(service = ProfileGraphMatcher.class)
public class BuiltinGraphMatcher implements ProfileGraphMatcher {
    public static final String SELECTOR_EXTENSION = "selector"; // NOI18N
    public static final String SELECTOR_ORDER = "selector.order"; // NOI18N

    public static final String ATTR_MATCH_GRAPH_PREFIX = "matchGraph."; // NOI18N
    public static final String ATTR_MATCH_NODE_PREFIX = "matchNode."; // NOI18N
    public static final String ATTR_MATCH_GROUP_PREFIX = "matchGroupProperty."; // NOI18N
    public static final String ATTR_MATCH_GRAPH_PROPERTY_PREFIX = "matchGraphProperty."; // NOI18N
    public static final String ATTR_CUSTOM_MATCHER = "profileGraphMatcher"; // NOI18N
    private static final String ATTR_ORDER = "order"; // NOI18N

    public static final String GRAPH_TYPE = "type";    // NOI18N
    public static final String GRAPH_NAME = "name";    // NOI18N
    public static final String GRAPH_NODE_COUNT = "nodeCount";    // NOI18N

    private static final String FORMAT_FLOAT = "float"; // NOI18N
    private static final String FORMAT_NUMBER = "number"; // NOI18N
    private static final String FORMAT_CONTAINS = "contains"; // NOI18N
    private static final String FORMAT_REGEXP = "regexp"; // NOI18N
    private static final String FORMAT_SAME = "same"; // NOI18N

    private static final String DEFAULT_SELECTOR_EXT = "selector"; // NOI18N
    private static final String DEFAULT_SELECTOR_NAME = "simple"; // NOI18N

    private final ProfileStorage profiles;

    private final Map<String, List<ProfileMatch>> acceptors = new HashMap<>();

    private FileChangeListener fl = new FL();

    public BuiltinGraphMatcher() {
        profiles = Lookup.getDefault().lookup(ProfileStorage.class);
    }

    private class FL extends FileChangeAdapter {
        @Override
        public void fileDeleted(FileEvent fe) {
            FileObject f = fe.getFile();
            if (f.isFolder()) {
                // the profile
                fe.getFile().removeFileChangeListener(this);
            } else {
                clearMatches(fe.getFile().getParent());
                fe.getFile().removeFileChangeListener(this);
            }
        }

        @Override
        public void fileChanged(FileEvent fe) {
            clearMatches(fe.getFile().getParent());
        }

        @Override
        public void fileDataCreated(FileEvent fe) {
            FileObject f = fe.getFile();
            if ("selector".equals(f.getExt())) {
                clearMatches(fe.getFile());
                fe.getFile().addFileChangeListener(this);
            }
        }
    }

    private void clearMatches(FileObject f) {
        synchronized (this) {
            acceptors.put(f.getPath(), null);
        }
    }

    @Override
    public int matchesInputGraph(FilterProfile profile, InputGraph gr, GraphContainer parent, Lookup context) {
        FileObject folder = profiles.getProfileFolder(profile);
        if (folder == null) {
            return REJECT;
        }
        List<ProfileMatch> matches;
        synchronized (this) {
            matches = acceptors.get(folder.getPath());
            if (matches == null) {
                if (!acceptors.containsKey(folder.getPath())) {
                    folder.addFileChangeListener(fl);
                }
                matches = buildProfileMatchers(folder);
                acceptors.put(folder.getPath(), matches);
            }
        }
        int max = REJECT;
        for (ProfileMatch m : matches) {
            max = Math.max(max, m.match(gr, parent, profile, context));
        }
        return max;
    }

    private List<ProfileMatch> buildProfileMatchers(FileObject profileFolder) {
        List<ProfileMatch> result = new ArrayList<>();
        int defaultOrder = 0;
        for (FileObject fo : profileFolder.getChildren()) {
            if (!SELECTOR_EXTENSION.equals(fo.getExt())) {
                continue;
            }

            Object o = fo.getAttribute(ATTR_CUSTOM_MATCHER);
            ProfileGraphMatcher customMatcher = null;
            if (o instanceof ProfileGraphMatcher) {
                customMatcher = (ProfileGraphMatcher) o;
            }
            java.util.Properties props = new java.util.Properties();
            try (InputStream fos = fo.getInputStream();
                 Reader isr = new InputStreamReader(fos, "ISO-8859-1")) { // NOI18N
                props.load(isr);
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }

            int order = defaultOrder;
            if (props.getProperty(SELECTOR_ORDER) != null) {
                try {
                    order = Integer.parseInt(props.getProperty(SELECTOR_ORDER));
                } catch (NumberFormatException ex) {
                    // ignore, let's order be the default order.
                }
            } else {
                o = fo.getAttribute(ATTR_ORDER);
                if (o instanceof Integer) {
                    order = (Integer) o;
                }
            }

            ProfileMatch m = new ProfileMatchBuilder(
                    props,
                    customMatcher).build();
            m.order = order;
            result.add(m);
        }
        return result;
    }

    private static class ProfileMatchBuilder {
        private final java.util.Properties selectorProperties;
        private final ProfileMatch result;
        private boolean hasFilter;

        public ProfileMatchBuilder(java.util.Properties props, ProfileGraphMatcher customMatcher) {
            this.selectorProperties = props;
            this.result = new ProfileMatch(customMatcher);
        }

        public ProfileMatch build() {
            processMatchAttribute(ATTR_MATCH_GRAPH_PREFIX, result.graphMatches::put);
            processMatchAttribute(ATTR_MATCH_GROUP_PREFIX, result.groupPropertyMatches::put);
            processMatchAttribute(ATTR_MATCH_GRAPH_PROPERTY_PREFIX, result.graphPropertyMatches::put);
            processMatchAttribute(ATTR_MATCH_NODE_PREFIX, result.nodeMatches::put);
            if (!hasFilter) {
                result.dead = true;
            }
            return result;
        }

        private static final Map<String, NumericMatcher.Op> OPER_SYMS = new HashMap<>();

        static {
            OPER_SYMS.put(">", NumericMatcher.Op.GT);
            OPER_SYMS.put(">=", NumericMatcher.Op.GE);
            OPER_SYMS.put("<", NumericMatcher.Op.LT);
            OPER_SYMS.put("<=", NumericMatcher.Op.LE);
            OPER_SYMS.put("=", NumericMatcher.Op.EQ);
            OPER_SYMS.put("!=", NumericMatcher.Op.NE);
        }

        private PropertyMatcher parseNumericComparison(String name, String rawVal, boolean fl) {
            List<String> m = new ArrayList<>(OPER_SYMS.keySet());
            Collections.sort(m, Collections.reverseOrder());
            for (String s : m) {
                if (rawVal.startsWith(s)) {
                    String v = rawVal.substring(s.length()).trim();
                    Number match = fl ? Double.parseDouble(v) : Integer.parseInt(v);
                    return new NumericMatcher(name, OPER_SYMS.get(s), match);
                }
            }
            // nothing matches, interpret as equals
            Number match = fl ? Double.parseDouble(rawVal) : Integer.parseInt(rawVal);
            return new NumericMatcher(name, NumericMatcher.Op.EQ, match);
        }

        private void processMatchAttribute(String prefix, BiFunction<String, PropertyMatcher, PropertyMatcher> putFunction) {
            for (String aName : selectorProperties.stringPropertyNames()) {
                if (!aName.startsWith(prefix)) {
                    continue;
                }
                String prop = aName.substring(prefix.length());
                int comma = prop.lastIndexOf(',');
                String format = "regexp";
                if (comma != -1) {
                    format = prop.substring(comma + 1, prop.length()).trim();
                    prop = prop.substring(0, comma);
                }
                String rawVal = selectorProperties.getProperty(aName);
                PropertyMatcher pm;

                switch (format) {
                    case FORMAT_SAME:
                        pm = new EqualityPropertyMatcher(prop, rawVal);
                        break;
                    default:
                        // modifier ignored
                    case FORMAT_REGEXP:
                        pm = new RegexpPropertyMatcher(prop, rawVal, false, Pattern.CASE_INSENSITIVE);
                        break;
                    case FORMAT_CONTAINS:
                        pm = new SubstringPropertyMatcher(prop, rawVal);
                        break;
                    case FORMAT_NUMBER:
                        pm = parseNumericComparison(prop, rawVal, false);
                        break;
                    case FORMAT_FLOAT:
                        pm = parseNumericComparison(prop, rawVal, true);
                        break;
                }
                hasFilter = true;
                putFunction.apply(prop, pm);
            }
        }
    }


    static class NumericMatcher implements PropertyMatcher {
        public enum Op {
            LT, LE, GT, GE, EQ, NE
        }

        private final String name;
        private final Op op;
        private final Number match;

        /**
         * Threshold for equal / inequal
         */
        private float threshold = 0.01f;

        public NumericMatcher(String name, Op op, Number match) {
            this.name = name;
            this.op = op;
            this.match = match;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean match(Object value) {
            if (match == null) {
                return value == null;
            } else if (value == null) {
                return false;
            }
            if (match instanceof Double) {
                double v;
                double m = (Double) match;
                try {
                    v = Double.parseDouble(value.toString());
                } catch (NumberFormatException ex) {
                    return false;
                }

                switch (op) {
                    case LT:
                        return v < m;
                    case LE:
                        return v <= m;
                    case GT:
                        return v > m;
                    case GE:
                        return v >= m;
                    case EQ:
                        return Math.abs(v - m) < threshold;
                    case NE:
                        return Math.abs(v - m) >= threshold;
                    default:
                        return false;
                }
            } else {
                int v;
                int m = (Integer) match;
                try {
                    v = Integer.parseInt(value.toString());
                } catch (NumberFormatException ex) {
                    return false;
                }
                switch (op) {
                    case LT:
                        return v < m;
                    case LE:
                        return v <= m;
                    case GT:
                        return v > m;
                    case GE:
                        return v >= m;
                    case EQ:
                        return v == m;
                    case NE:
                        return v != m;
                    default:
                        return false;
                }
            }
        }
    }

    static class SubstringPropertyMatcher implements PropertyMatcher {
        private final String name;
        private final String substr;

        public SubstringPropertyMatcher(String name, String substr) {
            this.name = name;
            this.substr = substr.toLowerCase();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean match(Object v) {
            if (substr == null || substr.isEmpty()) {
                return v == null;
            } else if (v == null) {
                return false;
            }
            return v.toString().toLowerCase().contains(substr);
        }
    }

    private static class ProfileMatch {
        final Map<String, PropertyMatcher> graphMatches = new HashMap<>();
        final Map<String, PropertyMatcher> nodeMatches = new HashMap<>();
        final Map<String, PropertyMatcher> graphPropertyMatches = new HashMap<>();
        final Map<String, PropertyMatcher> groupPropertyMatches = new HashMap<>();
        final ProfileGraphMatcher customMatcher;

        private boolean dead;
        private int order;

        public ProfileMatch(ProfileGraphMatcher m) {
            this.customMatcher = m;
        }

        boolean matchProperty(Properties p, Map<String, PropertyMatcher> map) {
            for (Map.Entry<String, PropertyMatcher> me : map.entrySet()) {
                String pn = me.getKey();
                String val = p.get(pn, String.class);
                if (me.getValue().match(val)) {
                    return true;
                }
            }
            return map.isEmpty();
        }

        boolean matchNodeProperty(InputGraph data, Map<String, PropertyMatcher> map) {
            PropertySelector psel = new Properties.PropertySelector<>(data.getNodes());
            for (Map.Entry<String, PropertyMatcher> me : map.entrySet()) {
                String pn = me.getKey();
                PropertyMatcher m = map.get(pn);
                if (psel.selectSingle(m) == null) {
                    return false;
                }
            }
            return map.isEmpty();
        }

        private boolean matchGraphProperties(InputGraph data, GraphContainer parent) {
            boolean success = false;
            for (String s : graphMatches.keySet()) {
                PropertyMatcher matcher = graphMatches.get(s);
                Object val;

                switch (s) {
                    case GRAPH_TYPE:
                        val = data.getGraphType();
                        break;
                    case GRAPH_NAME:
                        val = data.getName();
                        break;
                    case GRAPH_NODE_COUNT:
                        val = data.getNodeCount();
                        break;
                    default:
                        val = null;
                }
                if (!matcher.match(val)) {
                    return false;
                }
                success = true;
            }
            return success || graphMatches.isEmpty();
        }

        int match(InputGraph data, GraphContainer parent, FilterProfile profile, Lookup context) {
            if (dead) {
                return REJECT;
            }
            if (!matchGraphProperties(data, parent)) {
                return REJECT;
            }
            if (!matchProperty(data.getProperties(), graphPropertyMatches)) {
                return REJECT;
            }
            Group g = parent.getContentOwner();
            if (g == null) {
                if (!groupPropertyMatches.isEmpty()) {
                    return REJECT;
                }
            } else if (!groupPropertyMatches.isEmpty()) {
                while (true) {
                    if (matchProperty(g.getProperties(), groupPropertyMatches)) {
                        break;
                    }
                    Folder f = g.getParent();
                    if (f instanceof Group) {
                        g = (Group) f;
                    } else {
                        return REJECT;
                    }
                }
            }
            if (!matchNodeProperty(data, nodeMatches)) {
                return REJECT;
            }
            if (customMatcher != null) {
                return customMatcher.matchesInputGraph(profile, data, parent, context);
            }
            return order;
        }
    }

    /**
     * Saves a {@link SimpleProfileSelector}. The Selector must be previously obtained by
     * {@link #simpleSelector} and must be valid. Invalid selectors cannot be saved.
     *
     * @param selector the selector
     * @throws IOException in case of I/O error, or when the selector is invalid
     */
    public static void saveSelectorImpl(SimpleProfileSelector selector) throws IOException {
        FileObject f = ProfileAccessor.getInstance().getSelectorFile(selector);
        if (!selector.isValid()) {
            throw new IOException("Invalid selector");
        }
        ProfileAccessor gate = ProfileAccessor.getInstance();
        java.util.Properties props = new java.util.Properties();
        storeSelector(selector, props);
        f.getFileSystem().runAtomicAction(new AtomicAction() {
            @Override
            public void run() throws IOException {
                FileObject toDelete = null;
                if (f.isFolder()) {
                    // create if the file does not exist; delete in case of an error.
                    String n = FileUtil.findFreeFileName(f, DEFAULT_SELECTOR_NAME, DEFAULT_SELECTOR_EXT);
                    toDelete = f.createData(n);
                }
                try (OutputStream outStream = f.getOutputStream()) {
                    props.store(outStream, null);
                } catch (IOException ex) {
                    if (toDelete != null) {
                        toDelete.delete();
                    }
                }
            }
        });
    }

    /**
     * Implements {@link SimpleProfileSelector} loading.
     */
    public static SimpleProfileSelector simpleSelectorImpl(FilterProfile p) {
        ProfileService profS = Lookup.getDefault().lookup(ProfileService.class);
        ProfileStorage storage;
        if (profS == null) {
            return null;
        }
        if (!(profS instanceof ProfileStorage)) {
            storage = profS.getLookup().lookup(ProfileStorage.class);
            if (storage == null) {
                return null;
            }
        } else {
            storage = (ProfileStorage) profS;
        }
        FileObject folder = storage.getProfileFolder(p);
        if (folder == null || folder == storage.getProfileFolder(profS.getDefaultProfile())) {
            return null;
        }
        FileObject selectorFile = null;
        for (FileObject fo : folder.getChildren()) {
            if (!BuiltinGraphMatcher.SELECTOR_EXTENSION.equals(fo.getExt())) {
                continue;
            }
            if (selectorFile != null) {
                return null;
            }
            selectorFile = fo;
        }
        SimpleProfileSelector.init();
        ProfileAccessor gate = ProfileAccessor.getInstance();
        FileObject selFile = selectorFile != null ? selectorFile : folder;
        SimpleProfileSelector sel = gate.createSimpleSelector(selFile);
        if (selectorFile == null) {
            gate.setSelectorValid(sel, true);
            return sel;
        }
        try (InputStream inStream = selectorFile.getInputStream()) {
            java.util.Properties data = new java.util.Properties();
            data.load(inStream);
            loadSelector(sel, data);
            Object o = selectorFile.getAttribute(ATTR_ORDER);
            if (o instanceof Integer) {
                sel.setOrder((Integer) o);
            }
            gate.setSelectorValid(sel, true);
        } catch (IOException | IllegalStateException ex) {
            gate.setSelectorValid(sel, false);
        }
        return sel;
    }


    private interface C {
        void accept(String key, String value, boolean regexp);
    }

    private static void filterRegexpProperties(java.util.Properties props, String prefix, C callback) {
        Set<String> processed = new HashSet<>();
        props.stringPropertyNames().stream().filter((s) -> s.startsWith(prefix)).forEach(s -> {
            String rest = s.substring(prefix.length());
            processed.add(s);
            int i = rest.indexOf(',');
            boolean regexp = true;

            if (i != -1) {
                String format = rest.substring(i + 1);
                rest = rest.substring(0, i);
                if ("regexp".equals(format)) { // NOI18N
                    regexp = true;
                } else if ("same".equals(format)) { // NOI18N
                    regexp = false;
                } else {
                    // unsupported modifier
                    throw new IllegalStateException();
                }
            }
            callback.accept(rest, props.getProperty(s), regexp);
        });
        props.keySet().removeAll(processed);
    }

    private static void loadSelector(SimpleProfileSelector sele, java.util.Properties props) throws IllegalStateException {
        sele.setGraphNameRegexp(true);
        sele.setOwnerNameRegexp(true);
        filterRegexpProperties(props, ATTR_MATCH_GRAPH_PREFIX, (k, val, r) -> {
            switch (k) {
                case BuiltinGraphMatcher.GRAPH_TYPE:
                    sele.setGraphType(val);
                    break;
                case BuiltinGraphMatcher.GRAPH_NAME:
                    sele.setGraphName(val);
                    sele.setGraphNameRegexp(r);
                    break;
                default:
                    throw new IllegalStateException();
            }
        });
        filterRegexpProperties(props, BuiltinGraphMatcher.ATTR_MATCH_GROUP_PREFIX, (k, val, r) -> {
            switch (k) {
                case "name": // NOI18N
                    sele.setOwnerName(val);
                    sele.setOwnerNameRegexp(r);
                    break;

                default:
                    throw new IllegalStateException();
            }
        });
        String orderText = props.getProperty(BuiltinGraphMatcher.SELECTOR_ORDER);
        if (orderText != null) {
            try {
                Integer o = Integer.parseInt(orderText);
                sele.setOrder(o);
            } catch (NumberFormatException ex) {
                throw new IllegalStateException(ex);
            }
        }
        props.remove(BuiltinGraphMatcher.SELECTOR_ORDER);
        if (!props.isEmpty()) {
            throw new IllegalStateException();
        }
    }

    private static void storeSelector(SimpleProfileSelector sele, java.util.Properties props) {
        int o = sele.getOrder();
        if (o > 0) {
            props.setProperty(BuiltinGraphMatcher.SELECTOR_ORDER, Integer.toString(o));
        }

        String n;
        String v = sele.getGraphName();
        if (v != null && !v.trim().isEmpty()) {
            n = ATTR_MATCH_GRAPH_PREFIX + GRAPH_NAME;
            if (!sele.isGraphNameRegexp()) {
                n += ",same";  // NOI18N
            }
            props.setProperty(n, v.trim());
        }
        v = sele.getGraphType();
        if (v != null && !v.trim().isEmpty()) {
            n = ATTR_MATCH_GRAPH_PREFIX + GRAPH_TYPE;
            props.setProperty(n, v.trim());
        }
        v = sele.getOwnerName();
        if (v != null && !v.trim().isEmpty()) {
            n = ATTR_MATCH_GROUP_PREFIX + "name";  // NOI18N
            if (!sele.isOwnerNameRegexp()) {
                n += ",same";  // NOI18N
            }
            props.setProperty(n, v.trim());
        }
    }
}


