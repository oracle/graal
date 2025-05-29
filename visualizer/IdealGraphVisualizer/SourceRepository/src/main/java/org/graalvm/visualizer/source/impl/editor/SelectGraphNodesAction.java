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

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NAME;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

import javax.swing.*;

import org.graalvm.visualizer.source.*;
import org.graalvm.visualizer.util.LookupHistory;
import org.graalvm.visualizer.util.swing.DropdownButton;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.netbeans.api.editor.EditorActionRegistration;
import org.netbeans.api.editor.document.EditorDocumentUtils;
import org.openide.filesystems.FileObject;
import org.openide.util.ContextAwareAction;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.ProxyLookup;

import jdk.graal.compiler.graphio.parsing.model.InputNode;

/**
 * @author sdedic
 */
@NbBundle.Messages({
        "graph-select-nodes=Select graph nodes",
        "graph-select-nodes2=Select and cycle through nodes on line. Shift-click to select all.",
        "hint_graph-select-nodes=Selects nodes that correspond to the current line or selection",})
public class SelectGraphNodesAction extends SelectOrExtractNodesAction implements Presenter.Toolbar, ContextAwareAction {
    public static final String NAME_SELECT = "graph-select-nodes"; // NOI18N
    private static final String ICON = "org/graalvm/visualizer/source/resources/selectNodes.gif"; // NOI18N
    private final Lookup lkp;
    private final JEditorPane pane;
    private final NodeLocationContext ctx;

    public SelectGraphNodesAction() {
        this(Lookup.EMPTY);
    }

    private SelectGraphNodesAction(Lookup lkp) {
        super(true, NAME_SELECT, lkp);
        putValue(Action.SMALL_ICON, ImageUtilities.loadImageIcon("org/graalvm/visualizer/source/resources/selectNodes.gif", false));
        this.lkp = lkp;
        this.pane = lkp.lookup(JEditorPane.class);
        this.ctx = lkp.lookup(NodeLocationContext.class);
    }

    @EditorActionRegistration(
            name = NAME_SELECT,
            mimeType = "",
            category = "Source View",
            popupText = "#hint_graph-select-nodes",
            iconResource = ICON
    )
    // Map parameter forces the processor to register the class directly instead of through wrapper.
    public static Action selectNodesAction(Map<String, Object> params) {
        Action a = new SelectGraphNodesAction();
        return a;
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new SelectGraphNodesAction(new ProxyLookup(lkp, Lookup.getDefault()));
    }

    private DropdownButton popButton;

    @Override
    public Component getToolbarPresenter() {
        if (popButton != null) {
            return popButton;
        }
        DropdownButton button = new DropdownButton("", ImageUtilities.loadImageIcon(ICON, false), true) { // NOI18N
            @Override
            protected void populatePopup(JPopupMenu menu) {
                SelectGraphNodesAction.this.populatePopup(menu);
            }

            @Override
            protected void performAction(ActionEvent e) {
                boolean shiftPressed = (e.getModifiers() & ActionEvent.SHIFT_MASK) > 0;
                if (shiftPressed) {
                    SelectGraphNodesAction.super.actionPerformed(e, pane);
                    return;
                }
                cycleNextNode();
            }

        };
        button.setPopupEnabled(false);
        button.setEnabled(isEnabled());
        button.setToolTipText(Bundle.graph_select_nodes2());
        popButton = button;
        return button;
    }

    @Override
    public void setEnabled(boolean newValue) {
        super.setEnabled(newValue);
        if (popButton != null) {
            popButton.setEnabled(newValue);
        }
    }

    private List<InputNode> lineNodes = Collections.emptyList();
    private List<Location> lineLocs = Collections.emptyList();

    @Override
    protected void refreshUI() {
        FileObject fo = EditorDocumentUtils.getFileObject(pane.getDocument());
        NodeLocationContext context = Lookup.getDefault().lookup(NodeLocationContext.class);
        Collection<InputNode> nodes = null;
        List<Location> llocs = new ArrayList<>();
        if (context != null && fo != null) {
            nodes = findLineNodes(pane, llocs);
        }
        lineLocs = llocs;
        if (nodes == null || nodes.isEmpty()) {
            // no action
            lineNodes = Collections.emptyList();
            setEnabled(false);
            return;
        }
        DiagramViewer sel = LookupHistory.getLast(DiagramViewer.class);
        SourceUtils.resolveSelectableNodes(nodes, sel, (nn) -> {
            lineNodes = new ArrayList<>(nn);
            Collections.sort(lineNodes, InputNode.COMPARATOR);
            setEnabled(!lineNodes.isEmpty());
            if (popButton != null) {
                popButton.setPopupEnabled(nn.size() > 1);
            }
        }, true);
    }

    @NbBundle.Messages({
            "# {0} - input node ID",
            "# {1} - input node name",
            "FMT_SelectNodePopupFormat=<html><i>{0}:</i> {1}</html>",
            "# {0} - input node ID",
            "# {1} - input node name",
            "FMT_SelectNodePopupCurrentFormat=<html><b><i>{0}:</i> {1}</b></html>",
            "LBL_TooManyNodes=<html><i><font color='light-gray'>Too many results, use Stack View</font></i></html>"
    })
    private void doPopulateMenu(JPopupMenu menu, Location loc) {
        int count = 0;
        InputNode curNode = ctx.getCurrentNode();
        for (InputNode nn : lineNodes) {
            String name = nn.getProperties().getString(PROPNAME_NAME, "").replace("<", "&lt;");
            String n = nn == curNode
                    ? Bundle.FMT_SelectNodePopupCurrentFormat(nn.getId(), name)
                    : Bundle.FMT_SelectNodePopupFormat(nn.getId(), name);
            menu.add(new SelectNodesAction(n, loc, nn));
            if (++count >= 15) {
                JMenuItem lastItem = new JMenuItem(Bundle.LBL_TooManyNodes());
                menu.add(lastItem);
                return;
            }
        }
    }

    public void populatePopup(JPopupMenu menu) {
        if (pane == null || lineLocs.isEmpty()) {
            return;
        }
        Location loc = lineLocs.iterator().next();
        DiagramViewer v = LookupHistory.getLast(DiagramViewer.class);
        if (cancelRefresh()) {
            Collection<InputNode> nodes = findLineNodes(pane, null);
            SourceUtils.resolveSelectableNodes(nodes, v, (nn) -> {
                lineNodes = new ArrayList<>(nn);
                Collections.sort(lineNodes, InputNode.COMPARATOR);
                doPopulateMenu(menu, loc);
            }, true);
        } else {
            doPopulateMenu(menu, loc);
        }

    }

    void cycleNextNode() {
        InputNode in = ctx.getCurrentNode();
        int index = in != null ? lineNodes.indexOf(in) : -1;
        if (index == -1) {
            ctx.setSelectedNodes(lineNodes);
            index = 0;
        } else {
            index = (index + 1) % lineNodes.size();
        }
        in = lineNodes.get(index);
        Location loc = lineLocs.iterator().next();
        gotoNode(loc, in);
    }

    private void gotoNode(Location loc, InputNode nodeToSelect) {
        FileObject fo = EditorDocumentUtils.getFileObject(pane.getDocument());
        if (fo == null || loc == null) {
            return;
        }

        NodeStack.Frame curFrame = ctx.getSelectedFrame();
        GraphSource src = ctx.getGraphSource();
        if (src == null) {
            return;
        }
        ctx.setGraphContext(src.getGraph(), lineNodes);
        NodeStack ns = ctx.getStack(nodeToSelect);
        List<Location> locs = src.getFileLocations(fo, false);
        Collection<Location> lineLocs = SourceLocationUtils.atLine(locs, loc.getLine());
        NodeStack.Frame locFrame = null;
        NodeStack.Frame selFrame = null;
        if (ns != null) {
            for (NodeStack.Frame fr : ns) {
                if (fr == curFrame) {
                    selFrame = fr;
                    break;
                }
                if (lineLocs.contains(fr.getLocation())) {
                    locFrame = fr;
                }
            }
            if (selFrame == null) {
                selFrame = locFrame;
            }
        }
        ctx.setCurrentNode(nodeToSelect, selFrame);
        DiagramViewer sel = LookupHistory.getLast(DiagramViewer.class);
        SourceUtils.resolveSelectableNodes(Collections.singletonList(nodeToSelect), sel, (nn) -> {
            sel.getSelections().setSelectedNodes(nn);
            sel.getSelections().scrollToVisible(nn);
        }, true);
//        ctx.navigateCurrentNode();
    }

    private class SelectNodesAction extends AbstractAction {
        private final InputNode nodeToSelect;
        private final Location loc;

        public SelectNodesAction(String name, Location loc, InputNode node) {
            super(name);
            this.nodeToSelect = node;
            this.loc = loc;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            gotoNode(loc, nodeToSelect);
        }
    }
}
