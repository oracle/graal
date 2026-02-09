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
package at.ssw.visualizer.interval;

import at.ssw.visualizer.core.focus.Focus;
import at.ssw.visualizer.model.interval.IntervalList;
import at.ssw.visualizer.interval.icons.Icons;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.actions.CookieAction;

/**
 * Opens a new interval visualization window.
 *
 * @author Bernhard Stiftner
 * @author Christian Wimmer
 */
public final class ShowIntervalEditorAction extends CookieAction {
    protected void performAction(Node[] activatedNodes) {
        IntervalList intervalList = activatedNodes[0].getLookup().lookup(IntervalList.class);
        if (!Focus.findEditor(IntervalEditorTopComponent.class, intervalList)) {
            IntervalEditorSupport editor = new IntervalEditorSupport(intervalList);
            editor.open();
        }
    }

    public String getName() {
        return "Open Intervals";
    }

    @Override
    protected String iconResource() {
        return Icons.INTERVALS;
    }

    protected int mode() {
        return CookieAction.MODE_EXACTLY_ONE;
    }

    protected Class[] cookieClasses() {
        return new Class[]{IntervalList.class};
    }

    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    protected boolean asynchronous() {
        return false;
    }
}
