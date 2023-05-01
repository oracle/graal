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

import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.data.services.GraphSelections;
import org.graalvm.visualizer.source.NodeLocationContext;
import org.graalvm.visualizer.source.NodeStack;
import org.graalvm.visualizer.view.api.DiagramViewerLocator;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import java.awt.event.ActionEvent;
import java.util.Collections;
import javax.swing.Action;
import org.graalvm.visualizer.view.api.DiagramModel;

/**
 *
 * @author sdedic
 */
@NbBundle.Messages({
    "ACTION_GoToGraphNode=Goto graph node"
})
@ActionID(category = "CallStack", id = GotoNodeAction.ACTION_ID)
@ActionRegistration(displayName = "#ACTION_GoToGraphNode", lazy=true, iconBase = "org/graalvm/visualizer/source/resources/GoToNode.png")
public class GotoNodeAction extends LocationAction {
    public static final String ACTION_ID = "org.graalvm.visualizer.source.impl.actions.GotoNodeActionn"; // NOI18N
    public static final String CATEGORY = "CallStack"; // NOI18N
    
    private final NodeLocationContext context;

    public GotoNodeAction() {
        this.context = Lookup.getDefault().lookup(NodeLocationContext.class);
    }
    
    private GotoNodeAction(Lookup lkp) {
        super(lkp);
        this.context = Lookup.getDefault().lookup(NodeLocationContext.class);
    }
    
    @Override
    protected void actionPerformed(ActionEvent e, InputGraph g, InputNode[] nodes) {
        Node[] activatedNodes = activeNodes();
        NodeStack.Frame frame = activatedNodes[0].getLookup().lookup(NodeStack.Frame.class);
        if (frame == null) {
            return;
        }
        InputNode gNode = frame.getNode();
        InputGraph graph = frame.getGraph();
        
        DiagramViewerLocator locator = Lookup.getDefault().lookup(DiagramViewerLocator.class);
        if (locator == null) {
            return;
        }
        DiagramModel actModel = locator.getActiveModel();
        if (actModel == null || actModel.getGraphToView() != graph) {
            return;
        }
        // TODO: add to the DiagramViewer interface.
        GraphSelections prov = locator.getActiveViewer().getSelections();
        prov.setSelectedNodes(Collections.singleton(gNode));
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new GotoNodeAction(lkp);
    }
}
