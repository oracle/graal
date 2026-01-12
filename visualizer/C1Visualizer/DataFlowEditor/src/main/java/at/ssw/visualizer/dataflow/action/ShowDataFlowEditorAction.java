/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.dataflow.action;

import at.ssw.visualizer.core.focus.Focus;
import at.ssw.visualizer.dataflow.editor.DFEditorSupport;
import at.ssw.visualizer.dataflow.editor.DataFlowEditorTopComponent;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CookieAction;

/**
 * This is the action that is hooked to an compilation-x-element within the
 * filesystem menu. Essentially it extracts the ControlFlowGraph from the
 * selected node and passes it to the DFEditorSupport bringing the
 * viewer to the front.
 *
 * @author Stefan Loidl
 * @author Christian Wimmer
 */
public final class ShowDataFlowEditorAction extends CookieAction {
    private static final String ICON_PATH = "at/ssw/visualizer/dataflow/icons/dfg.gif";

    /*
     * The Action extracts the Control- Flow- Graph- Data- Object from the nodes which
     * contain information for data flow too.
     */
    protected void performAction(Node[] activatedNodes) {
        ControlFlowGraph cfg = activatedNodes[0].getLookup().lookup(ControlFlowGraph.class);
        if (!Focus.findEditor(DataFlowEditorTopComponent.class, cfg)) {
            DFEditorSupport editor = new DFEditorSupport(cfg);
            editor.open();
        }
    }

    /*
     * Defines when the context menu item triggering this action should be
     * enabled (one element has to be chose and this elements has to have an
     * ControlFlowGraph Cookie!- moreover HIR Codes has to be present within
     * the node)
     */
    @Override
    protected boolean enable(Node[] activatedNodes) {
        if (!super.enable(activatedNodes)) {
            return false;
        }
        ControlFlowGraph cfg = activatedNodes[0].getLookup().lookup(ControlFlowGraph.class);
        return cfg.hasHir();
    }

    public String getName() {
        return "Open Data Flow Graph";
    }

    @Override
    protected String iconResource() {
        return ICON_PATH;
    }

    protected int mode() {
        return CookieAction.MODE_EXACTLY_ONE;
    }

    protected Class[] cookieClasses() {
        return new Class[]{ControlFlowGraph.class};
    }

    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }
}
