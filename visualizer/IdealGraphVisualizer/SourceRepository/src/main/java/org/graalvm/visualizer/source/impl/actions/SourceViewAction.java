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

package org.graalvm.visualizer.source.impl.actions;

import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.source.NodeLocationContext;
import org.graalvm.visualizer.source.NodeLocationEvent;
import org.graalvm.visualizer.source.NodeLocationListener;
import org.graalvm.visualizer.source.NodeStack;
import org.netbeans.api.actions.Viewable;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.awt.Actions;
import org.openide.util.ContextAwareAction;
import org.openide.util.ImageUtilities;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.WeakListeners;
import org.openide.util.actions.Presenter;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JToggleButton;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.Collection;

@NbBundle.Messages({
        "DESC_SourceViewSync=Autoscroll to Source"
})
@ActionID(category = "CallStack", id = SourceViewAction.ID)
@ActionRegistration(displayName = "#DESC_SourceViewSync", lazy = false)
@ActionReferences({
        @ActionReference(path = "NodeGraphViewer/Actions", position = 12000, separatorBefore = 11950)
})
public final class SourceViewAction extends AbstractAction
        implements NodeLocationListener, ContextAwareAction, Presenter.Toolbar {

    public static final String ID = "org.graalvm.visualizer.source.impl.actions.SourceViewAction"; // NOI18N

    final NodeLocationContext context;
    final TopComponent myEditor;

    public SourceViewAction() {
        this(null);
    }

    public SourceViewAction(TopComponent ed) {
        putValue(AbstractAction.SMALL_ICON, new ImageIcon(ImageUtilities.loadImage(iconResource())));
        putValue(SELECTED_KEY, false);
        putValue(Action.SHORT_DESCRIPTION, Bundle.DESC_SourceViewSync());

        context = Lookup.getDefault().lookup(NodeLocationContext.class);
        if (context != null) {
            context.addNodeLocationListener(WeakListeners.create(NodeLocationListener.class, this, context));
        }
        this.myEditor = ed;
    }

    public boolean isSelected() {
        return (Boolean) getValue(SELECTED_KEY);
    }

    public void setSelected(boolean b) {
        if (isSelected() != b) {
            this.putValue(SELECTED_KEY, b);
        }
    }

    protected String iconResource() {
        return "org/graalvm/visualizer/view/images/followSource.png";
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        setSelected(!isSelected());
        nodesChanged(null);
    }

    @Override
    public void nodesChanged(NodeLocationEvent evt) {
        if (!isSelected() || context == null || WindowManager.getDefault().getRegistry().getActivated() != myEditor) {
            return;
        }
        Collection<InputNode> nodes = evt == null ? context.getGraphNodes() : evt.getNodes();
        if (nodes.isEmpty()) {
            return;
        }
        InputNode n = nodes.iterator().next();
        viewSelectedNodeSource(n);
    }

    private void viewSelectedNodeSource(InputNode n) {
        if (!isSelected()) {
            return;
        }
        NodeStack locs = context.getStack(n);
        if (locs == null || locs.isEmpty()) {
            return;
        }
        NodeStack.Frame loc = locs.top();
        context.setSelectedLocation(loc);
        Viewable v = loc.getLookup().lookup(Viewable.class);
        if (v != null) {
            v.view();
        }
    }

    @Override
    public void locationsResolved(NodeLocationEvent evt) {
    }

    @Override
    public void selectedLocationChanged(NodeLocationEvent evt) {
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        TopComponent tc = actionContext.lookup(TopComponent.class);
        if (tc != null) {
            return new SourceViewAction(tc);
        } else {
            return null;
        }
    }

    @Override
    public void selectedNodeChanged(NodeLocationEvent evt) {
        InputNode selNode = evt.getSelectedNode();
        if (!isSelected() || selNode == null) {
            return;
        }
        if (TopComponent.getRegistry().getActivated() != myEditor) {
            return;
        }
        viewSelectedNodeSource(selNode);
    }

    @Override
    public Component getToolbarPresenter() {
        JToggleButton b = new JToggleButton();
        Actions.connect(b, this);
//        b.addActionListener(this);
        return b;
    }
}
