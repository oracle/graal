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

package org.graalvm.visualizer.filterwindow.impl;

import org.graalvm.visualizer.data.ChangedListener;
import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterSequence;
import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.graalvm.visualizer.util.ListenerSupport;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import java.util.HashMap;

/**
 *
 * @author sdedic
 */
public class FilterChildren extends Children.Keys<Filter> {
    private final ProfileService service;
    private final FilterProfile profile;
    private final HashMap<Filter, Node> nodeHash = new HashMap<>();
    
    private ChangedListener seqL;
    private ChangedListener keep;

    @Override
    protected Node[] createNodes(Filter filter) {
        if (nodeHash.containsKey(filter)) {
            return new Node[]{nodeHash.get(filter)};
        }

        FilterNode node = new FilterNode(filter, profile, service);
        nodeHash.put(filter, node);
        return new Node[]{node};
    }

    public FilterChildren(FilterProfile profile, ProfileService service) {
        this.profile = profile;
        this.service = service;
    }

    @Override
    protected void addNotify() {
        seqL = ListenerSupport.addWeakListener(keep = new ChangedListener<FilterSequence>() {
            @Override
            public void changed(FilterSequence source) {
                makeRefresh();
            }
        }, profile.getAllFilters().getChangedEvent());
        makeRefresh();
    }

    @Override
    protected void removeNotify() {
        profile.getAllFilters().getChangedEvent().removeListener(seqL);
    }

    private void makeRefresh() {
        setKeys(profile.getProfileFilters());
    }
}
