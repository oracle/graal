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
package org.graalvm.visualizer.search.ui;

import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.data.services.GraphViewer;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.util.Lookup;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.List;

/**
 * @author sdedic
 */
@ActionRegistration(displayName = "Select nodes", lazy = true, surviveFocusChange = true)
@ActionID(category = "Diagram", id = "org.graalvm.visualizer.search.selectnodes")
public class SelectNodesAction implements ActionListener {
    private final Collection<InputNode> inputNodes;

    public SelectNodesAction(List<InputNode> nodes) {
        this.inputNodes = nodes;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DiagramViewer vwr = Lookup.getDefault().lookup(GraphViewer.class).getActiveViewer();
        vwr.getSelections().setSelectedNodes(inputNodes);
    }
}
