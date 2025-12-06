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
package at.ssw.visualizer.nc.action;

import at.ssw.visualizer.core.focus.Focus;
import at.ssw.visualizer.model.cfg.ControlFlowGraph;
import at.ssw.visualizer.nc.NCEditor;
import at.ssw.visualizer.nc.NCEditorSupport;
import at.ssw.visualizer.nc.icons.Icons;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CookieAction;

/**
 *
 * @author Alexander Reder
 */
public final class ShowNCEditorAction extends CookieAction {

    protected void performAction(Node[] activatedNodes) {
        ControlFlowGraph cfg = activatedNodes[0].getLookup().lookup(ControlFlowGraph.class);
        if (!Focus.findEditor(NCEditor.class, cfg)) {
            NCEditorSupport support = new NCEditorSupport(cfg);
            support.open();
        }
    }

    @Override
    protected boolean enable(Node[] activatedNodes) {
        if(activatedNodes == null || activatedNodes.length == 0) {
            return false;
        }
        ControlFlowGraph cfg = activatedNodes[0].getLookup().lookup(ControlFlowGraph.class);
        if(cfg == null) {
            return false;
        }
        return cfg.getNativeMethod() != null;
    }
    
    protected int mode() {
        return CookieAction.MODE_EXACTLY_ONE;
    }

    public String getName() {
        return "Open Native code";
    }

    protected Class[] cookieClasses() {
        return new Class[]{ControlFlowGraph.class};
    }

    @Override
    protected String iconResource() {
        return Icons.NATIVECODE;
    }

    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    protected @Override
    boolean asynchronous() {
        return false;
    }
}

