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
package org.graalvm.visualizer.shell.actions;

import org.graalvm.visualizer.filter.FilterProvider;
import org.graalvm.visualizer.filter.Filters;
import org.graalvm.visualizer.view.api.DiagramModel;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.graalvm.visualizer.view.api.DiagramViewerLocator;
import org.openide.awt.DynamicMenuContent;
import org.openide.util.Lookup;
import org.openide.util.Utilities;
import org.openide.util.WeakListeners;

import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.Component;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;

/**
 * Basis on actions which operate on a script window (a {@link FilterchainSource}
 * and the activ graph.
 *
 * @author sdedic
 */
public abstract class AbstractGraphShellAction extends EditorContextActionBase {
    private final FilterProvider source;
    private final Lookup lkp;
    private volatile Reference<DiagramModel> modelRef = new WeakReference<>(null);
    private final DiagramViewerLocator viewLocator;
    private static final WeakReference<DiagramModel> NO_DIAGRAM = new WeakReference<>(null);
    private boolean initialized;

    private final ChangeListener l = new ChangeListener() {
        @Override
        public void stateChanged(ChangeEvent e) {
            boolean checkDiagram = viewLocator == e.getSource();
            if (SwingUtilities.isEventDispatchThread()) {
                computeEnabledAWT(checkDiagram);
            } else {
                SwingUtilities.invokeLater(
                        () -> computeEnabledAWT(checkDiagram)
                );
            }
        }

    };

    public AbstractGraphShellAction() {
        this(null, null);
    }

    AbstractGraphShellAction(Map<String, ?> attrs, Lookup lkp) {
        super(attrs);
        if (lkp == null) {
            this.lkp = Utilities.actionsGlobalContext();
        } else {
            this.lkp = lkp;
        }
        source = findSource(lkp);
        if (source != null) {
            source.addChangeListener(WeakListeners.change(l, source));
        }
        viewLocator = Lookup.getDefault().lookup(DiagramViewerLocator.class);
        if (viewLocator != null) {
            viewLocator.addChangeListener(WeakListeners.change(l, viewLocator));
        }
        // XXX
        // must not initialize enabled now, since the editor is just being constructed
        // and a call to {@link EditorCookie#getOpenedPanes} will deadlock
        // XXX - but if not initialized NOW, the presenters will never become enabled.
//        computeEnabledAWT(false);
        setEnabled(source != null);
        if (source == null) {
            putValue(DynamicMenuContent.HIDE_WHEN_DISABLED, Boolean.TRUE);
        }
    }

    private void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        computeEnabledAWT(false);
    }

    protected final Lookup getLookup() {
        return lkp;
    }

    private static FilterProvider findSource(Lookup lkp) {
        return Filters.locateChainSource(lkp);
    }

    private void computeEnabledAWT(boolean checkDiagram) {
        if (source == null || viewLocator == null) {
            setEnabled(false);
            return;

        }
        DiagramModel oldModel;

        synchronized (this) {
            oldModel = modelRef.get();
            if (checkDiagram) {
                modelRef = null;
            }
        }

        DiagramModel current = findDiagramModel();

        if (checkDiagram || oldModel == null) {
            if (current != oldModel) {
                diagramModelChanged(oldModel, current);
            }
        }

        if (source != null && viewLocator != null) {
            setEnabled(computeEnabled(current, source));
        } else {
            setEnabled(false);
        }
    }

    protected final void refresh() {
        computeEnabledAWT(true);
    }

    protected void diagramModelChanged(DiagramModel previous, DiagramModel current) {
        // no op
    }

    protected final DiagramViewer findViewer() {
        return viewLocator.getActiveViewer();
    }

    protected final DiagramModel findDiagramModel() {
        Reference<DiagramModel> mr = modelRef;
        DiagramModel mdl;

        if (mr != null) {
            mdl = mr.get();
            if (mdl != null || mr == NO_DIAGRAM) {
                return mdl;
            }
        }
        mdl = viewLocator.getActiveModel();
        synchronized (this) {
            if (mr == modelRef) {
                modelRef = mdl == null ? NO_DIAGRAM : new WeakReference<>(mdl);
            }
        }
        return mdl;
    }

    protected final FilterProvider getFilterSource() {
        return source;
    }

    protected abstract boolean computeEnabled(DiagramModel model, FilterProvider filterSource);

    @Override
    public JMenuItem getPopupPresenter() {
        initialize();
        return super.getPopupPresenter();
    }

    @Override
    public JMenuItem getMenuPresenter() {
        initialize();
        return super.getMenuPresenter();
    }


    @Override
    public Component getToolbarPresenter() {
        initialize();
        return super.getToolbarPresenter();
    }
}
