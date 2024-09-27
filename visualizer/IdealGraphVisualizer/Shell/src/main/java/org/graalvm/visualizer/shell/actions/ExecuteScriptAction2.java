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
package org.graalvm.visualizer.shell.actions;

import org.graalvm.visualizer.filter.DiagramFilters;
import org.graalvm.visualizer.filter.Filter;
import org.graalvm.visualizer.filter.FilterProvider;
import org.graalvm.visualizer.shell.ShellSession;
import org.graalvm.visualizer.view.api.DiagramModel;
import org.netbeans.api.editor.EditorActionRegistration;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import javax.swing.Action;
import java.awt.event.ActionEvent;
import java.util.Map;

/**
 * @author sdedic
 */
// FIXME: make a specific script Lookup provider that will inject
// some subtree into MIMELookup
@NbBundle.Messages({
        "execute-script=Execute Script"
})
public class ExecuteScriptAction2 extends AbstractGraphShellAction {
    public ExecuteScriptAction2() {
        this(null, null);
    }

    ExecuteScriptAction2(Map<String, ?> attrs, Lookup lkp) {
        super(attrs, lkp);
    }

    @EditorActionRegistration(
            name = "execute-script",
            // register for all mime types
            mimeType = "",
            iconResource = "org/graalvm/visualizer/shell/resources/execute.png",
            toolBarPosition = 100
    )
    public static ExecuteScriptAction2 createEditorAction(Map params) {
        return new ExecuteScriptAction2(params, null);
    }

    @Override
    protected boolean computeEnabled(DiagramModel model, FilterProvider filterSource) {
        return filterSource.getFilter() != null && model != null;
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        DiagramModel mdl = findDiagramModel();
        Filter filter = getFilterSource().createFilter(true);

        if (mdl == null || filter == null) {
            return;
        }
        DiagramFilters modelFilters = mdl.getLookup().lookup(DiagramFilters.class);
        if (modelFilters == null) {
            return;
        }
        // apply the filter onto the model
        modelFilters.applyScriptFilter(filter, ShellSession.getCurrentSession().getSharedEnvironment(), false, null);
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new ExecuteScriptAction2(attributes, actionContext);
    }
}
