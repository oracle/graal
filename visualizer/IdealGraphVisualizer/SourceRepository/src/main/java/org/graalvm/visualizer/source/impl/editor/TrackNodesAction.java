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

package org.graalvm.visualizer.source.impl.editor;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.data.services.GraphSelections;
import org.graalvm.visualizer.source.GraphSource;
import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.NodeLocationContext;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.graalvm.visualizer.view.api.DiagramViewerLocator;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.api.editor.document.EditorDocumentUtils;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.editor.BaseAction;
import org.openide.filesystems.FileObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Exceptions;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.actions.Presenter;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JEditorPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * @author sdedic
 */
@NbBundle.Messages({
        "SHORT_TrackNodesFromEditor=Tracks line nodes",
        "HINT_TrackNodesFromEditor=Tracks nodes for current position",
})
@EditorActionRegistration(
        name = TrackNodesAction.ID,
        shortDescription = "#SHORT_TrackNodesFromEditor",
        mimeType = "",
        category = "Source View",
        popupText = "#HINT_TrackNodesFromEditor",
        iconResource = "org/graalvm/visualizer/source/resources/followSource.png"
)
public class TrackNodesAction extends BaseAction implements Presenter.Toolbar, ContextAwareAction {
    public static final String ID = "graph-track-nodes"; // NOI18N

    private Reference<JEditorPane> paneRef;
    private Reference<JToggleButton> toggleButtonRef;
    static final RequestProcessor DELAY_RP = new RequestProcessor(TrackNodesAction.class);
    private static final int DELAY = 500;
    private boolean trackNodes;

    private class Tracker implements CaretListener, Runnable {
        RequestProcessor.Task task;

        @Override
        public void caretUpdate(CaretEvent e) {
            synchronized (this) {
                if (task != null) {
                    task.cancel();
                }
                task = DELAY_RP.post(this, DELAY);
            }
        }

        @Override
        public void run() {
            if (SwingUtilities.isEventDispatchThread()) {
                selectNodesForCurrentLine();
            } else {
                synchronized (this) {
                    task = null;
                }
                SwingUtilities.invokeLater(this);
            }
        }
    }

    private final Tracker tracker = new Tracker();

    public TrackNodesAction() {
        super(ID);
        putValue(Action.SHORT_DESCRIPTION, Bundle.SHORT_TrackNodesFromEditor());
        putValue(Action.LONG_DESCRIPTION, Bundle.HINT_TrackNodesFromEditor());
        putValue(Action.SMALL_ICON, ImageUtilities.loadImageIcon("org/graalvm/visualizer/source/resources/followSource.png", false)); // NOI18N
    }

    JEditorPane getPane() {
        return (paneRef != null ? paneRef.get() : null);
    }

    JToggleButton getToggleButton() {
        return (toggleButtonRef != null ? toggleButtonRef.get() : null);
    }

    void updateState() {
        JToggleButton tog = getToggleButton();
        if (tog == null) {
            return;
        }
        tog.setSelected(trackNodes);
    }

    @Override
    public Component getToolbarPresenter() {
        JToggleButton toggleButton = new JToggleButton();
        toggleButtonRef = new WeakReference<>(toggleButton);
        toggleButton.putClientProperty("hideActionText", Boolean.TRUE); //NOI18N
        toggleButton.setIcon((Icon) getValue(SMALL_ICON));
        toggleButton.setAction(this); // this will make hard ref to button => check GC
        return toggleButton;
    }

    void setPane(JEditorPane pane) {
        assert (pane != null);
        this.paneRef = new WeakReference<>(pane);
        Object o = pane.getClientProperty(TrackNodesAction.class);
        if (o == this) {
            return;
        }
        pane.putClientProperty(TrackNodesAction.class, this);
        pane.addCaretListener(tracker);
        updateState();
    }

    @Override
    public void actionPerformed(ActionEvent ae, JTextComponent jtc) {
        trackNodes = !trackNodes;
        updateState();
        if (trackNodes) {
            selectNodesForCurrentLine();
        }
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        JEditorPane pane = actionContext.lookup(JEditorPane.class);
        if (pane != null) {
            TrackNodesAction action = (TrackNodesAction) pane.getClientProperty(TrackNodesAction.class);
            if (action == null) {
                action = new TrackNodesAction();
            }
            action.setPane(pane);
            return action;
        }
        return this;
    }

    private void selectNodesForCurrentLine() {
        JEditorPane pane = getPane();
        if (pane == null || !trackNodes) {
            return;
        }
        int caretDot = pane.getCaret().getDot();
        LineDocument ldoc = LineDocumentUtils.as(pane.getDocument(), LineDocument.class);
        if (ldoc == null) {
            return;
        }
        int lineno;
        try {
            lineno = LineDocumentUtils.getLineIndex(ldoc, caretDot) + 1;
        } catch (BadLocationException ex) {
            Exceptions.printStackTrace(ex);
            return;
        }
        FileObject fo = EditorDocumentUtils.getFileObject(ldoc);
        if (fo == null) {
            return;
        }
        NodeLocationContext ctx = Lookup.getDefault().lookup(NodeLocationContext.class);
        GraphSource gSource = ctx.getGraphSource();
        if (gSource == null) {
            return;
        }
        InputGraph gr = gSource.getGraph();
        DiagramViewerLocator locator = Lookup.getDefault().lookup(DiagramViewerLocator.class);
        if (gr == null || locator == null) {
            return;
        }

        DiagramViewer active = locator.getActiveViewer();
        if (active == null || active.getModel().getGraphToView() != gr) {
            return;
        }

        Collection<Location> locs = gSource.getFileLocations(fo, true);
        if (locs.isEmpty()) {
            return;
        }
        Set<InputNode> nodes = new HashSet<>(locs.size());
        for (Iterator<Location> it = locs.iterator(); it.hasNext(); ) {
            Location l = it.next();
            if (l.getLine() == lineno) {
                // PENDING process exact offsets
                nodes.addAll(gSource.getNodesAt(l));
            }
        }
        GraphSelections sel = active.getSelections();

        SelectOrExtractNodesAction.selectFirstOrNext(active.getModel().getDiagramToView(), active, nodes, false, true,
                (newSel) -> active.getSelections().setSelectedNodes(newSel));
    }
}
