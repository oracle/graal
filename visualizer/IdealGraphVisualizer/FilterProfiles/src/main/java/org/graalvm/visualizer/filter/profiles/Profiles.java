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

package org.graalvm.visualizer.filter.profiles;

import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.filter.profiles.impl.BuiltinGraphMatcher;
import org.graalvm.visualizer.filter.profiles.mgmt.SimpleProfileSelector;
import org.graalvm.visualizer.filter.profiles.spi.ProfileGraphMatcher;
import org.openide.util.Lookup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provides helper function to work with and manage filter profiles.
 *
 * @author sdedic
 */
public class Profiles {
    private Profiles() {
        // prevent instantiation
    }

    /**
     * Attempts to select a suitable profile for the graph.
     *
     * @param profiles FilterProfieles to select from.
     * @param in       graph data
     * @param parent   the container
     * @return suitable profiles, in the decreasing precedence order
     */
    public static List<FilterProfile> selectProfiles(Collection<FilterProfile> profiles, InputGraph in, GraphContainer parent, Lookup context) {
        Collection<? extends ProfileGraphMatcher> matchers = Lookup.getDefault().lookupAll(ProfileGraphMatcher.class);
        Set<FilterProfile> notAssigned = new HashSet<>(profiles);
        Map<FilterProfile, Integer> priorities = new HashMap<>();
        for (ProfileGraphMatcher m : matchers) {
            for (FilterProfile fp : new ArrayList<>(notAssigned)) {
                int priority = m.matchesInputGraph(fp, in, parent, context);
                if (priority == ProfileGraphMatcher.REJECT) {
                    continue;
                }
                notAssigned.remove(fp);
                priorities.put(fp, priority);
            }
        }
        // add the default profile as an ultimate fallback:
        priorities.putIfAbsent(Lookup.getDefault().lookup(FilterRegistry.class).getDefaultProfile(),
                Integer.MIN_VALUE);
        List<FilterProfile> result = new ArrayList<>(priorities.keySet());
        Collections.sort(result, new Comparator<FilterProfile>() {
            @Override
            public int compare(FilterProfile o1, FilterProfile o2) {
                return priorities.get(o1) - priorities.get(o2);
            }
        });
        return result;
    }

    /**
     * Returns all profiles. Just a convenience method that calls {@link FilterRegistry#getProfiles()}.
     *
     * @return all profiles.
     */
    public static List<FilterProfile> allProfiles() {
        return Lookup.getDefault().lookupAll(FilterRegistry.class).stream().
                flatMap((r) -> r.getProfiles().stream()).collect(Collectors.toList());
    }

    /**
     * Saves a {@link SimpleProfileSelector}. The Selector must be previously obtained by
     * {@link #simpleSelector} and must be valid. Invalid selectors cannot be saved.
     *
     * @param selector the selector
     * @throws IOException in case of I/O error, or when the selector is invalid
     */
    public static void saveSelector(SimpleProfileSelector selector) throws IOException {
        BuiltinGraphMatcher.saveSelectorImpl(selector);
    }


    /**
     * Determines if profile match can be represented by {@link SimpleProfileSelector}.
     * If the match is more complex, returns false.
     *
     * @param p the profile
     * @return true, if SimpleProfileSelector can represent the profile match.
     */
    public static boolean canSimpleEdit(FilterProfile p) {
        SimpleProfileSelector ss = simpleSelector(p);
        return ss != null && ss.isValid();
    }

    /**
     * Loads profile selector description. Only a very simple selection rules are
     * currently supported. If the storage contains more complex setup, a selector
     * instance is returned, but its {@link SimpleProfileSelector#isValid()} reports
     * {@code false}. Such selector can't be saved and contains only partial, if any,
     * data.
     * <p/>
     * If the profile does not use storage compatible with a simple filter, it returns
     * {@code null}.
     *
     * @param p profile
     * @return selector for the profile.
     */
    public static SimpleProfileSelector simpleSelector(FilterProfile p) {
        return BuiltinGraphMatcher.simpleSelectorImpl(p);
    }
}
