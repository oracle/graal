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
package at.ssw.visualizer.bc.action;

import at.ssw.visualizer.bc.BCEditor;
import at.ssw.visualizer.bc.BCEditorSupport;
import at.ssw.visualizer.bc.icons.Icons;
import at.ssw.visualizer.bc.model.BytecodeModel;
import at.ssw.visualizer.core.focus.Focus;
import at.ssw.visualizer.model.bc.Bytecodes;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import javax.swing.JOptionPane;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.Lookup;
import org.openide.util.actions.CookieAction;

/**
 * This class provides methods for the action to open the BytecodeEditor
 * for an entry of the compilation menu.
 *
 * @author Alexander Reder
 * @author Christian Wimmer
 */
public final class ShowBCEditorAction extends CookieAction {
    /**
     * Opens the BCEditor for the control flow graph of the active node.
     *
     * @param   activatedNodes  Nodes that are selected
     */
    protected void performAction(Node[] activatedNodes) {
        BytecodeModel bcModel = Lookup.getDefault().lookup(BytecodeModel.class);
        ControlFlowGraph cfg = activatedNodes[0].getLookup().lookup(ControlFlowGraph.class);
        Bytecodes bytecodes = bcModel.getBytecodes(cfg);
        if (bytecodes == null) {
            JOptionPane.showMessageDialog(null, bcModel.noBytecodesMsg(cfg), "No bytecodes available", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (!Focus.findEditor(BCEditor.class, bytecodes.getControlFlowGraph())) {
            BCEditorSupport editor = new BCEditorSupport(bytecodes);
            editor.open();
        }
    }

    /**
     * Returns how often an action can be performed on a selected node.
     *
     * @return  it can be performed only one time.
     */
    protected int mode() {
        return CookieAction.MODE_EXACTLY_ONE;
    }

    /**
     * When should the BCEditor be available.
     *
     * @return  returns true if exactely one BlockListBuilder node is selected
     *  or exactly one After Generation of HIR node is selected and there is
     *  only one BlockListerBuilder node, false otherwise.
     */
    @Override
    protected boolean enable(Node[] activatedNodes) {
        if (!super.enable(activatedNodes)) {
            return false;
        }
        BytecodeModel bcModel = Lookup.getDefault().lookup(BytecodeModel.class);
        ControlFlowGraph cfg = activatedNodes[0].getLookup().lookup(ControlFlowGraph.class);
        return bcModel.hasBytecodes(cfg);
    }

    /**
     * Returns the name of the action.
     *
     * @return  name of the action
     */
    public String getName() {
        return "Open Bytecodes";
    }

    /**
     * Returns the icon of the action.
     *
     * @return icon of the action
     */
    @Override
    protected String iconResource() {
        return Icons.BYTECODE;
    }

    /**
     * Specfies the cookie classes where this action should be available.
     *
     * @return  ControlFlowGraph.class
     */
    protected Class[] cookieClasses() {
        return new Class[]{ControlFlowGraph.class};
    }

    /**
     * Returns the help context for this action.
     *
     * @return  null, no help context available
     */
    public HelpCtx getHelpCtx() {
        return null;
    }

    /**
     * How this action should be performed.
     *
     * @return  false, this action will be performed immediatly
     */
    @Override
    protected boolean asynchronous() {
        return false;
    }
}
