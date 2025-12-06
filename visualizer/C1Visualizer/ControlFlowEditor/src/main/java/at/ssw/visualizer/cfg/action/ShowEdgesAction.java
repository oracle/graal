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

import at.ssw.visualizer.cfg.editor.CfgEditorTopComponent;
import at.ssw.visualizer.cfg.graph.CfgScene;
import at.ssw.visualizer.cfg.graph.EdgeWidget;
import at.ssw.visualizer.cfg.model.CfgEdge;
import at.ssw.visualizer.cfg.model.CfgNode;
import org.openide.util.HelpCtx;

/**
 * Shows all edges connected to the selected node.
 *
 * @author Bernhard Stiftner
 * @author Rumpfhuber Stefan
 */
public class ShowEdgesAction extends AbstractCfgEditorAction {

    public void performAction() {
        CfgEditorTopComponent tc = getEditor();
        if (tc != null) {
            tc.getCfgScene().setSelectedEdgesVisibility(true);
        }
    }

    public String getName() {
        return "Show edges";
    }


    @Override
    protected String iconResource() {
        return "at/ssw/visualizer/cfg/icons/showedges.gif";
    }

    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override  
    public void selectionChanged(CfgScene scene) {      
        for (CfgNode n : scene.getSelectedNodes()) {  
            for (CfgEdge e : scene.findNodeEdges(n, true, true) ){
                EdgeWidget ew = (EdgeWidget) scene.findWidget(e);
                if(!ew.isVisible()) {
                    setEnabled(true);
                    return;
                }
            }
        }
        setEnabled(false);
    }
}
