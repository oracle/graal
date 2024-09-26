/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.search.ui.actions;

import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.data.services.GraphViewer;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionState;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;

import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * @author sdedic
 */
@ActionRegistration(displayName = "Select nodes", lazy = true, surviveFocusChange = true,
        key = "select-nodes",
        iconBase = "org/graalvm/visualizer/search/resources/navigate.gif",
        enabledOn = @ActionState(type = InputNode.class, useActionInstance = true)
)
@ActionID(category = "Diagram", id = "org.graalvm.visualizer.search.gotonode")
public class GotoNodesAction extends GraphContextAction {

    public GotoNodesAction(List<InputNode> nodes) {
        super(nodes);
    }

    private GotoNodesAction(Lookup context) {
        super(context);
    }

    @Override
    public boolean isEnabled() {
        DiagramViewer vwr = Lookup.getDefault().lookup(GraphViewer.class).getActiveViewer();
        if (vwr != null) {
            TopComponent viewerComp = vwr.getLookup().lookup(TopComponent.class);
            return viewerComp != TopComponent.getRegistry().getActivated();
        }
        return super.isEnabled();
    }

    @NbBundle.Messages({
            "NAME_SelectNodesInGraph=Select nodes in {0}"
    })
    @Override
    protected String createNameWithTarget(Group targetGroup, InputGraph targetGraph) {
        return Bundle.NAME_SelectNodesInGraph(targetGroup.getName());
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new GotoNodesAction(lkp);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DiagramViewer vwr = Lookup.getDefault().lookup(GraphViewer.class).getActiveViewer();
        vwr.getSelections().setSelectedNodes(getInputNodes());
        TopComponent tc = vwr.getLookup().lookup(TopComponent.class);
        if (tc != null) {
            tc.requestFocus();
        }
    }
}
