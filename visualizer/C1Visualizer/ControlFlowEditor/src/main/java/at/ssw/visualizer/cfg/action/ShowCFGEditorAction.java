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
package at.ssw.visualizer.cfg.action;

import at.ssw.visualizer.cfg.editor.CfgEditorSupport;
import at.ssw.visualizer.cfg.editor.CfgEditorTopComponent;
import at.ssw.visualizer.cfg.icons.Icons;
import at.ssw.visualizer.core.focus.Focus;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CookieAction;

/**
 * Shows the CFG visualizer for the currently selected compilation.
 *
 * @author Bernhard Stiftner
 * @author Christian Wimmer
 */
public final class ShowCFGEditorAction extends CookieAction {
         
    protected void performAction(Node[] activatedNodes) {       
        ControlFlowGraph cfg = activatedNodes[0].getLookup().lookup(ControlFlowGraph.class);
        if (!Focus.findEditor(CfgEditorTopComponent.class, cfg)) {
            CfgEditorSupport editor = new CfgEditorSupport(cfg);
            editor.open();
        }
    }

    @Override
    protected boolean enable(Node[] activatedNodes) {
        if (!super.enable(activatedNodes)) {
            return false;
        }
        ControlFlowGraph cfg = activatedNodes[0].getLookup().lookup(ControlFlowGraph.class);
        return cfg.getBasicBlocks().size() > 0;
    }

    public String getName() {
        return "Open Control Flow Graph";
    }

    @Override
    protected String iconResource() {
        return Icons.CFG;
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
