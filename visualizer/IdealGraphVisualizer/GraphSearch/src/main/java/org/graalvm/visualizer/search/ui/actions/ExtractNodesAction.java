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
import org.graalvm.visualizer.data.services.InputGraphProvider;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.ActionState;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.util.Collection;
import java.util.List;

/**
 * @author sdedic
 */
@ActionRegistration(displayName = "Extract nodes", lazy = true, surviveFocusChange = true,
        key = "extract-nodes",
        enabledOn = @ActionState(type = InputGraph.class, useActionInstance = true),
        iconBase = "org/graalvm/visualizer/search/resources/extract.gif"
)
@ActionReferences({
        @ActionReference(path = "Menu/View", position = 2030),
        @ActionReference(path = "Shortcuts", name = "O-X")
})
@ActionID(category = "Diagram", id = "org.graalvm.visualizer.search.extractnodes")
@NbBundle.Messages({
        "# {0} - target graph name",
        "NAME_ExtractInGraph=Extract in {0}"
})
public class ExtractNodesAction extends GraphContextAction {
    public ExtractNodesAction(List<InputNode> nodes) {
        super(nodes);
    }

    private ExtractNodesAction(Lookup context) {
        super(context);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DiagramViewer vwr = Lookup.getDefault().lookup(GraphViewer.class).getActiveViewer();
        if (vwr != null && vwr.getModel().getContainer().getContentOwner() == getTargetGroup()) {
            vwr.getSelections().extractNodes(getInputNodes());
        }
        Lookup.getDefault().lookup(GraphViewer.class).view(
                (success, provider) -> doExtract(provider, getInputNodes()),
                getTargetGraph(), false, true);

    }

    private void doExtract(InputGraphProvider provider, Collection<InputNode> nodes) {
        DiagramViewer v = (DiagramViewer) provider;
        v.getSelections().extractNodes(nodes);
    }

    @Override
    public Action createContextAwareInstance(Lookup lkp) {
        return new ExtractNodesAction(lkp);
    }

    @Override
    protected String createNameWithTarget(Group targetGroup, InputGraph targetGraph) {
        return Bundle.NAME_ExtractInGraph(targetGroup.getName());
    }
}
