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
package org.graalvm.visualizer.filterwindow.actions;

import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionState;
import org.openide.util.NbBundle;

import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MoveFilterAction extends AbstractAction {
    public static final String ID_MOVE_DOWN = "org.graalvm.visualizer.filterwindow.actions.MoveDown"; // NOI18N
    public static final String ID_MOVE_UP = "org.graalvm.visualizer.filterwindow.actions.MoveUp"; // NOI18N
    public static final String CATEGORY = "Filters"; // NOI18N

    private final boolean direction;
    private final List<Filter> filters;

    @NbBundle.Messages({
            "# {0} - error description",
            "FILTER_MoveFailed1=Could not move filter: {0}"
    })
    @Override
    public void actionPerformed(ActionEvent e) {
        FilterProfile ops = findOperations();
        if (ops == null) {
            return;
        }
        List<IOException> exceptions = new ArrayList<>();

        // moving filters one by one; when moving subsequent filters, the 1st would become 2nd,
        // and in the next step the former 2nd (now 1st) would again become 2nd. Need to move from the
        // last filter for down, and from the first filter for up, using the order of appearance in the profile.
        List<Filter> profFilters = new ArrayList<>(ops.getProfileFilters());
        profFilters.retainAll(filters);
        if (direction) {
            Collections.reverse(profFilters);
        }
        for (Filter c : profFilters) {
            if (c != null) {
                try {
                    if (direction) {
                        ops.moveDown(c);
                    } else {
                        ops.moveUp(c);
                    }
                } catch (IOException ex) {
                    exceptions.add(ex);
                }
            }
        }
        if (exceptions.isEmpty()) {
            return;
        }
        NotifyDescriptor.Message msg = new NotifyDescriptor.Message(
                Bundle.FILTER_MoveFailed1(exceptions.get(0).toString()),
                NotifyDescriptor.ERROR_MESSAGE);
        DialogDisplayer.getDefault().notifyLater(msg);
    }

    public MoveFilterAction(List<Filter> filters, boolean direction) {
        this.direction = direction;
        this.filters = filters;
    }

    @NbBundle.Messages({
            "ACTION_MoveFilterDownAction=Move filter downwards"
    })
    @ActionID(
            category = "Filters",
            id = "org.graalvm.visualizer.filterwindow.actions.MoveDown"
    )
    @ActionReferences({
            @ActionReference(path = "Menu/Edit", position = 500),
            @ActionReference(path = "IGV/ContextActions/Filter", position = 300)
    })
    @ActionRegistration(
            displayName = "#ACTION_MoveFilterDownAction",
            iconBase = "org/graalvm/visualizer/filterwindow/images/down.png",
            enabledOn = @ActionState(
                    type = Filter.class,
                    useActionInstance = true
            )
    )
    public static class Down extends MoveFilterAction {
        public Down(List<Filter> context) {
            super(context, true);
        }
    }

    @NbBundle.Messages({
            "ACTION_MoveFilterUpAction=Move filter upwards"
    })
    @ActionID(
            category = "Filters",
            id = "org.graalvm.visualizer.filterwindow.actions.MoveUp"
    )
    @ActionReferences({
            @ActionReference(path = "Menu/Edit", position = 400),
            @ActionReference(path = "IGV/ContextActions/Filter", position = 400)
    })
    @ActionRegistration(
            displayName = "#ACTION_MoveFilterUpAction",
            iconBase = "org/graalvm/visualizer/filterwindow/images/up.png",
            lazy = true,
            enabledOn = @ActionState(
                    type = Filter.class,
                    useActionInstance = true
            )
    )
    public static class Up extends MoveFilterAction {
        public Up(List<Filter> context) {
            super(context, false);
        }
    }

    private FilterProfile findOperations() {
        FilterProfile ops = null;
        for (Filter f : filters) {
            FilterProfile test = f.getLookup().lookup(FilterProfile.class);
            if (test != null) {
                if (ops != null && ops != test) {
                    return null;
                }
                ops = test;
            }
        }
        return ops;
    }

    @Override
    public boolean isEnabled() {
        if (!super.isEnabled()) {
            return false;
        }
        FilterProfile ops = findOperations();
        if (ops == null) {
            return false;
        }
        List<Filter> seq = ops.getProfileFilters();
        for (Filter f : filters) {
            if (f == null) {
                continue;
            }
            if (direction) {
                if (seq.indexOf(f) >= seq.size() - 1) {
                    return false;
                }
            } else {
                if (seq.indexOf(f) == 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
