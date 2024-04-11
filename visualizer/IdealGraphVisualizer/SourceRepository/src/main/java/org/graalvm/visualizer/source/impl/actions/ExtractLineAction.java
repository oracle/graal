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

package org.graalvm.visualizer.source.impl.actions;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.data.services.GraphSelections;
import org.graalvm.visualizer.source.GraphSource;
import org.graalvm.visualizer.source.Location;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import javax.swing.Action;
import javax.swing.KeyStroke;
import java.awt.Event;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 */
@NbBundle.Messages({
        "WARN_UnresolvedLocations=Unresolved files encountered, some nodes are not extracted",
        "WARN_ExtractEmptySet=Unable to find any file/line information in the selection",
        "ACTION_ExtractLine=Extract nodes on same line"
})
@ActionID(category = "CallStack", id = "org.graalvm.visualizer.source.impl.actions.ExtractLineAction")
@ActionRegistration(displayName = "#ACTION_ExtractLine",
        lazy = false)
@ActionReferences({
        @ActionReference(path = "NodeGraphViewer/Actions", position = 12500),
        @ActionReference(path = "Menu/View", position = 330),

})
public class ExtractLineAction extends CallableSystemAction {
    @Override
    public void performAction() {
        TopComponent tc = WindowManager.getDefault().getRegistry().getActivated();
        if (tc == null) {
            return;
        }
        GraphSelections sel = tc.getLookup().lookup(GraphSelections.class);
        if (sel == null) {
            return;
        }
        boolean unresolvedFound = false;
        InputGraph g = sel.getGraph();
        GraphSource gSrc = GraphSource.getGraphSource(g);

        Node[] nodes = tc.getActivatedNodes();
        Set<InputNode> selectNodes = new HashSet<>();
        Set<Location.Line> seenLines = new HashSet<>();

        for (Node n : nodes) {
            InputNode gn = n.getLookup().lookup(InputNode.class);
            if (gn == null) {
                continue;
            }
            Location l = gSrc.findNodeLocation(gn);
            if (l == null) {
                continue;
            }
            if (!l.isResolved()) {
                unresolvedFound = true;
                continue;
            }
            if (!seenLines.add(l.line())) {
                continue;
            }
            List<Location> searchIn = new ArrayList<>(gSrc.getFileLocations(l.getOriginFile(), true));
            int locIndex = Collections.binarySearch(searchIn, l, Location::compareLineOnly);
            if (locIndex < 0) {
                // nothing special
                continue;
            }
            int lineno = l.getLine();
            for (int i = locIndex; i >= 0; i--) {
                Location x = searchIn.get(i);
                if (x.getLine() != lineno) {
                    break;
                }
                selectNodes.addAll(gSrc.getNodesAt(x));
            }
            int max = searchIn.size();
            for (int i = locIndex; i < max; i++) {
                Location x = searchIn.get(i);
                if (x.getLine() != lineno) {
                    break;
                }
                selectNodes.addAll(gSrc.getNodesAt(x));
            }
        }
        if (selectNodes.isEmpty()) {
            StatusDisplayer.getDefault().setStatusText(Bundle.WARN_ExtractEmptySet());
            return;
        }
        if (unresolvedFound) {
            StatusDisplayer.getDefault().setStatusText(Bundle.WARN_UnresolvedLocations());
        }
        // have selected nodes from the same line
        sel.extractNodes(selectNodes);
    }

    public ExtractLineAction() {
        putValue(Action.SHORT_DESCRIPTION, Bundle.ACTION_ExtractLine());
        putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_X, Event.SHIFT_MASK | Event.CTRL_MASK, false));
    }

    @Override
    public String getName() {
        return Bundle.ACTION_ExtractLine();
    }

    @Override
    protected void initialize() {
        super.initialize();
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }

    @Override
    protected String iconResource() {
        return "org/graalvm/visualizer/source/resources/extractline.png";
    }
}
