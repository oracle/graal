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
import org.graalvm.visualizer.source.GraphSource;
import org.graalvm.visualizer.source.Location;
import org.graalvm.visualizer.source.NodeLocationContext;
import org.graalvm.visualizer.source.NodeStack;
import org.netbeans.api.actions.Openable;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.nodes.Node;
import org.openide.util.ContextAwareAction;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import javax.swing.Action;
import java.awt.event.ActionEvent;

/**
 *
 */
@NbBundle.Messages({
        "ACTION_GotoSource=Navigate to Source"
})
@ActionID(category = "CallStack", id = GotoSourceAction.ACTION_ID)
@ActionRegistration(displayName = "#ACTION_GotoSource", lazy = false)
@ActionReferences({
        @ActionReference(path = "Shortcuts", name = "C-L"),
        @ActionReference(path = "NodeGraphViewer/Actions", position = 13000)
})
public class GotoSourceAction extends LocationAction implements ContextAwareAction {
    public static final String ACTION_ID = "org.graalvm.visualizer.source.impl.actions.GotoSourceAction1";
    public static final String CATEGORY = "CallStack";

    private final NodeLocationContext locationContext;

    public GotoSourceAction() {
        super(ACTION_ID);
        this.locationContext = Lookup.getDefault().lookup(NodeLocationContext.class);
    }

    private GotoSourceAction(Lookup context) {
        super(ACTION_ID, context);
        this.locationContext = Lookup.getDefault().lookup(NodeLocationContext.class);
    }

    @Override
    protected String iconResource() {
        return "org/graalvm/visualizer/source/resources/GoToSource.png";
    }

    private NodeStack getNodeStack(InputNode in, InputGraph gr) {
        GraphSource src = GraphSource.getGraphSource(gr);
        if (src == null) {
            return null;
        }
        String selectedMime = locationContext.getSelectedLanguage();
        NodeStack nodeStack = src.getNodeStack(in, selectedMime);
        if (nodeStack == null) {
            src.getNodeStack(in);
        }
        return nodeStack;
    }

    @Override
    public final void actionPerformed(ActionEvent e, InputGraph g, InputNode[] nodes) {
        NodeStack.Frame loc = context().lookup(NodeStack.Frame.class);
        if (loc != null) {
            Openable op = loc.getLookup().lookup(Openable.class);
            if (!loc.isResolved() || op == null) {
                return;
            }
            op.open();
            locationContext.setSelectedLocation(loc);
            return;
        }
        // perhaps an InputNode is selected ?
        InputNode in = context().lookup(InputNode.class);
        if (in == null) {
            return;
        }
        InputGraph gr = context().lookup(InputGraph.class);
        NodeStack nodeStack = getNodeStack(in, gr);
        if (nodeStack == null) {
            return;
        }
        NodeStack.Frame f = nodeStack.top();
        locationContext.setSelectedLocation(f);
        if (f.isResolved()) {
            Openable op = f.getLookup().lookup(Openable.class);
            if (op != null) {
                op.open();
            }
        }
    }

    @Override
    protected boolean computeEnabled(InputGraph graph, InputNode[] nodes) {
        Node[] activatedNodes = activeNodes();
        if (activatedNodes.length != 1) {
            return false;
        }
        Node n = activatedNodes[0];
        Location l = n.getLookup().lookup(Location.class);
        if (l != null) {
            return l.isResolved();
        }
        InputNode in = n.getLookup().lookup(InputNode.class);
        if (in == null) {
            return false;
        }
        InputGraph gr = n.getLookup().lookup(InputGraph.class);
        NodeStack nodeStack = getNodeStack(in, gr);
        if (nodeStack == null) {
            return false;
        }
        NodeStack.Frame f = nodeStack.top();
        return f.isResolved();
    }

    @Override
    public String getName() {
        return Bundle.ACTION_GotoSource();
    }

    //    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new GotoSourceAction(actionContext);
    }
}
