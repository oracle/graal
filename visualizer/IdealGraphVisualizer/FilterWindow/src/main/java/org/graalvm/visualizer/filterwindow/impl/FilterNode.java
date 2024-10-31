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

import jdk.graal.compiler.graphio.parsing.model.ChangedListener;
import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterCanceledException;
import org.graalvm.visualizer.filter.FilterEvent;
import org.graalvm.visualizer.filter.FilterExecution;
import org.graalvm.visualizer.filter.FilterExecutionService;
import org.graalvm.visualizer.filter.FilterListener;
import org.graalvm.visualizer.filter.FilterSequence;
import org.graalvm.visualizer.filter.profiles.FilterProfile;
import org.graalvm.visualizer.filter.profiles.FilterRegistry;
import org.graalvm.visualizer.filter.profiles.mgmt.ProfileService;
import org.graalvm.visualizer.filterwindow.CheckNode;
import org.graalvm.visualizer.filterwindow.actions.MoveFilterAction;
import org.graalvm.visualizer.filterwindow.actions.RenameFilterAction;
import org.graalvm.visualizer.util.ListenerSupport;
import org.graalvm.visualizer.util.PropertiesSheet;
import org.openide.actions.OpenAction;
import org.openide.awt.Actions;
import org.openide.cookies.OpenCookie;
import org.openide.nodes.Children;
import org.openide.nodes.Sheet;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.Utilities;
import org.openide.util.WeakListeners;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;
import org.openide.windows.IOProvider;
import org.openide.windows.InputOutput;

import javax.swing.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@NbBundle.Messages({
        "# name for shadow of an object",
        "# {0} - real name of the shadow",
        "# {1} - real name of the original",
        "FMT_FilterLinkName={0} (→ {1})",
        "# {0} - real name of the shadow",
        "# {1} - real name of the original",
        "FMT_FilterLinkTooltip=Derives from {1}",
        "TOOLTIP_FilterClickEdit=Double click to open filter contents"
})
public final class FilterNode extends CheckNode implements FilterListener {
    private static final String ACTIONS = "IGV/Actions/Filter"; // NOI18N

    private static final String ICON_NORMAL = "org/graalvm/visualizer/filterwindow/images/filterIcon.png"; // NOI18N
    private static final String ICON_ERROR = "org/graalvm/visualizer/filterwindow/images/filterIconError.png"; // NOI18N

    private final ProfileService service;
    private final Filter filter;
    private final FilterProfile profile;
    private final boolean defaultProfile;

    /**
     * Helps to suppress duplicate errors. The key string is extracted from classname, message and stacktrace elements
     */
    private Map<String, Throwable> reportedErrors = new LinkedHashMap<>();
    private FilterListener weakL;
    private final ChangedListener<Filter> changedL = new ChangedListener<Filter>() {
        @Override
        public void changed(Filter source) {
            SwingUtilities.invokeLater(() -> update());
        }
    };

    public FilterNode(Filter filter, FilterProfile profile, ProfileService service) {
        this(filter, profile, service, new InstanceContent());
    }

    private FilterNode(Filter filter, FilterProfile profile, ProfileService service, InstanceContent content) {
        super(Children.LEAF, new ProxyLookup(new AbstractLookup(content)));
        this.filter = filter;
        this.profile = profile;
        this.service = service;
        defaultProfile = Lookup.getDefault().lookup(FilterRegistry.class).getDefaultProfile() == profile;

        if (defaultProfile) {
            setShortDescription(Bundle.TOOLTIP_FilterClickEdit());
        }

        content.add(filter);
        OpenCookie oc = filter.getLookup().lookup(OpenCookie.class);
        if (oc == null) {
            oc = filter.getEditor();
        }
        if (oc != null) {
            content.add(oc);
        }


        filter.getChangedEvent().addListener(new ChangedListener<Filter>() {
            @Override
            public void changed(Filter source) {
                update();
            }
        });
        if (profile != null) {
            profile.getSelectedFilters().getChangedEvent().addListener(new ChangedListener<FilterSequence>() {
                @Override
                public void changed(FilterSequence source) {
                    filterSequenceChanged();
                }
            });
        }

        ListenerSupport.addWeakListener(changedL, filter.getChangedEvent());

        update();
        if (profile != null) {
            filterSequenceChanged();
        }
        FilterExecutionService srv = FilterExecution.getExecutionService();
        weakL = WeakListeners.create(FilterListener.class, this, srv);
        srv.addFilterListener(weakL);
    }

    private void update() {
        Filter original = service.findDefaultFilter(filter);
        if (original != null && original != filter && !original.getName().equals(filter.getName())) {
            setDisplayName(Bundle.FMT_FilterLinkName(
                    filter.getName(),
                    original.getName()));
            setShortDescription(Bundle.FMT_FilterLinkTooltip(filter.getName(), original.getName()));
        } else {
            this.setDisplayName(filter.getName());
        }
        this.reportedErrors.clear();
        setIconBaseWithExtension(ICON_NORMAL);
    }

    public Filter getFilter() {
        return filter;
    }

    @Override
    protected Sheet createSheet() {
        Sheet s = super.createSheet();
        PropertiesSheet.initializeSheet(getFilter().getProperties(), s);
        return s;
    }

    @Override
    public Action[] getActions(boolean b) {
        return new Action[]{
                (Action) OpenAction.findObject(OpenAction.class, true),
                context(Actions.forID(MoveFilterAction.CATEGORY, MoveFilterAction.ID_MOVE_DOWN)),
                context(Actions.forID(MoveFilterAction.CATEGORY, MoveFilterAction.ID_MOVE_UP)),
                context(Actions.forID(RenameFilterAction.CATEGORY, RenameFilterAction.ID))
        };
    }

    private Action context(Action orig) {
        if (!(orig instanceof ContextAwareAction)) {
            return orig;
        }
        return ((ContextAwareAction) orig).createContextAwareInstance(getLookup());
    }

    @Override
    public Action getPreferredAction() {
        return OpenAction.get(OpenAction.class).createContextAwareInstance(Utilities.actionsGlobalContext());
    }

    private void filterSequenceChanged() {
        if (profile == null) {
            return;
        }
        super.setSelected(
                profile.getSelectedFilters().getFilters().contains(filter)
        );
    }

    @Override
    public void filterStart(FilterEvent e) {
    }

    @NbBundle.Messages({
            "TITLE_FilterExecution=Filter execution",
            "# {0} - filter name",
            "MSG_ErrorExecutionFilter=Error(s) encountered when executing filter {0}:\n"
    })
    @Override
    public void filterEnd(FilterEvent e) {
        if (e.getFilter() != this.filter) {
            Filter f = e.getFilter();
            if (f == null) {
                return;
            }
            Collection<? extends Filter> filters = f.getLookup().lookupAll(Filter.class);
            if (!filters.stream().anyMatch((t) -> t == this.filter)) {
                return;
            }
        }
        Throwable err = e.getExecutionError();
        if (err == null) {
            if (!reportedErrors.isEmpty()) {
                update();
            }
            return;
        }
        if (err instanceof FilterCanceledException) {
            return;
        }
        setIconBaseWithExtension(ICON_ERROR);
        String str = exceptionString(e.getExecutionError());
        if (reportedErrors.containsKey(str)) {
            return;
        }
        reportedErrors.put(str, e.getExecutionError());
        fireIconChange();
        InputOutput io = IOProvider.getDefault().getIO(Bundle.TITLE_FilterExecution(), false);
        PrintWriter pw = new PrintWriter(io.getErr()) {
            @Override
            public void close() {
                // suppress close on the delegate
            }
        };
        io.select();
        pw.print(Bundle.MSG_ErrorExecutionFilter(e.getFilter().getName()));
        e.getExecutionError().printStackTrace(pw);
    }

    private String exceptionString(Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getClass().getName()).append(":").append(t.toString()).append(";");
        for (StackTraceElement sel : t.getStackTrace()) {
            sb.append(sel.getFileName()).append(":").append(sel.getLineNumber()).append("\n");
        }
        return sb.toString();
    }

    @Override
    public void setSelected(boolean b) {
        super.setSelected(b);
        if (profile != null) {
            try {
                profile.setEnabled(filter, isSelected());
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }
    }


}
