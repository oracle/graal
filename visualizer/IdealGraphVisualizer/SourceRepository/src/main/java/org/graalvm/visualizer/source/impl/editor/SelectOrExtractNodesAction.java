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
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.NodeLocationContext;
import org.graalvm.visualizer.source.SourceUtils;
import org.graalvm.visualizer.util.LookupHistory;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.netbeans.api.editor.document.EditorDocumentUtils;
import org.netbeans.api.editor.document.LineDocument;
import org.netbeans.api.editor.document.LineDocumentUtils;
import org.netbeans.editor.BaseAction;
import org.openide.awt.StatusDisplayer;
import org.openide.filesystems.FileObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.JTextComponent;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * Selects or extract nodes in the graph on the current line
 */
@NbBundle.Messages({
        "# {0} - filename",
        "ERR_NoLocationsInFile=The current graph does not contain any node originating from file {0}"
})
public abstract class SelectOrExtractNodesAction extends BaseAction implements ContextAwareAction {
    public static final String NAME_EXTRACT = "graph-extract-nodes"; // NOI18N

    private final boolean selection;
    private final JEditorPane pane;

    SelectOrExtractNodesAction(boolean selection, String name) {
        this(selection, name, Lookup.EMPTY);
    }

    SelectOrExtractNodesAction(boolean selection, String name, Lookup lkp) {
        super(name);
        this.selection = selection;
        this.pane = lkp.lookup(JEditorPane.class);
        if (pane != null) {
            pane.addCaretListener(tracker = new Tracker());
        } else {
            tracker = null;
        }
    }

    @Override
    protected boolean asynchonous() {
        return false;
    }

    // FIXME: handle setEnabled() based on editor registry changes, fire events
    static final RequestProcessor DELAY_RP = new RequestProcessor(TrackNodesAction.class);
    private static final int DELAY = 500;

    private boolean pendingRefresh;

    private class Tracker implements CaretListener, Runnable {
        RequestProcessor.Task task;

        @Override
        public void caretUpdate(CaretEvent e) {
            synchronized (this) {
                if (task != null) {
                    task.cancel();
                }
                pendingRefresh = true;
                task = DELAY_RP.post(this, DELAY);
            }
        }

        @Override
        public void run() {
            if (SwingUtilities.isEventDispatchThread()) {
                pendingRefresh = false;
                refreshUI();
            } else {
                synchronized (this) {
                    task = null;
                }
                SwingUtilities.invokeLater(this);
            }
        }
    }

    protected boolean cancelRefresh() {
        synchronized (this) {
            if (pendingRefresh) {
                tracker.task.cancel();
                return true;
            }
        }
        return false;
    }

    private Tracker tracker;

    @Override
    public void actionPerformed(ActionEvent evt, JTextComponent target) {
        boolean shiftPressed = (evt.getModifiers() & ActionEvent.SHIFT_MASK) > 0;
        FileObject fo = EditorDocumentUtils.getFileObject(target.getDocument());
        NodeLocationContext context = Lookup.getDefault().lookup(NodeLocationContext.class);
        Collection<InputNode> nodes = null;

        if (context != null && fo != null) {
            Caret c = target.getCaret();
            int p1 = c.getDot();
            int p2 = c.getMark();

            LineDocument ld = LineDocumentUtils.as(target.getDocument(), LineDocument.class);
            if (ld != null) {
                int n1;
                int n2;
                try {
                    n1 = LineDocumentUtils.getLineIndex(ld, p1);
                    n2 = LineDocumentUtils.getLineIndex(ld, p2);
                } catch (BadLocationException ex) {
                    return;
                }
                int from = Math.min(n1, n2) + 1;
                int to = Math.max(n1, n2) + 1;
                nodes = SourceUtils.findLineNodes(fo, from, to, null, null, true);
            } else {
                nodes = findLineNodes(target, null);
            }
        }
        if (nodes == null) {
            // no action
            Toolkit.getDefaultToolkit().beep();
            StatusDisplayer.getDefault().setStatusText(Bundle.ERR_NoLocationsInFile(fo.getNameExt()));
            return;
        }
        InputGraph graph = context.getGraph();
        context.setGraphContext(graph, nodes);

        GraphSelections sel = LookupHistory.getLast(GraphSelections.class);
        DiagramViewer viewer = LookupHistory.getLast(DiagramViewer.class);
        final Collection<InputNode> fNodes = nodes;
        viewer.getModel().withDiagramToView((dg) -> {
            SwingUtilities.invokeLater(() -> {
                if (selection) {
                    selectFirstOrNext(dg, viewer, fNodes, shiftPressed, false, (ns) -> viewer.getSelections().setSelectedNodes(ns));
                } else {
                    sel.extractNodes(fNodes);
                }
            });
        });
    }

    protected void refreshUI() {
    }

    static void selectFirstOrNext(Diagram dg, DiagramViewer view, Collection<InputNode> nodes, boolean all,
                                  boolean doNothingIfFound, Consumer<Collection<InputNode>> consumer) {
        if (all) {
            consumer.accept(SourceUtils.findTopNodes(view, nodes));
        } else {
            Collection<InputNode> sel = SourceUtils.selectNext(dg, view, nodes, doNothingIfFound);
            if (sel != null) {
                consumer.accept(sel);
            }
        }
    }

    protected static Collection<InputNode> findLineNodes(JTextComponent target, Collection<Location> outLocs) {
        return SourceUtils.findLineNodes(target, null, outLocs, true);
    }

}
