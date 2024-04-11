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

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.source.NodeLocationContext;
import org.graalvm.visualizer.source.NodeStack;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.nodes.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

import javax.swing.Action;
import java.awt.event.ActionEvent;

/**
 *
 */
public class GoStackUpDownAction extends LocationAction {
    public static final String ACTION_GO_DOWN = "org.graalvm.visualizer.source.impl.actions.GoStackDown"; // NOI18N
    public static final String ACTION_GO_UP = "org.graalvm.visualizer.source.impl.actions.GoStackUp"; // NOI18N
    public static final String CATEGORY = "CallStack"; // NOI18N

    private final boolean goUp;
    private final NodeLocationContext context;

    private GoStackUpDownAction(boolean goUp) {
        this.goUp = goUp;
        this.context = Lookup.getDefault().lookup(NodeLocationContext.class);
    }

    private GoStackUpDownAction(boolean goUp, Lookup context) {
        super(context);
        this.goUp = goUp;
        this.context = Lookup.getDefault().lookup(NodeLocationContext.class);
    }

    @Override
    public Action createContextAwareInstance(Lookup actionContext) {
        return new GoStackUpDownAction(goUp, actionContext);
    }

    static class U extends GoStackUpDownAction {
        public U() {
            super(true);
        }
    }

    static class D extends GoStackUpDownAction {
        public D() {
            super(false);
        }
    }

    @NbBundle.Messages({
            "ACTION_GotoStackUp=Go up the call stack"
    })
    @ActionID(category = "CallStack", id = "org.graalvm.visualizer.source.impl.actions.GoStackUp")
    @ActionRegistration(displayName = "#ACTION_GotoStackUp", lazy = false)
    public static GoStackUpDownAction createUpAction() {
        return new GoStackUpDownAction(true);
    }

    @NbBundle.Messages({
            "ACTION_GotoStackDown=Go down the call stack"
    })
    @ActionID(category = "CallStack", id = "org.graalvm.visualizer.source.impl.actions.GoStackDown")
    @ActionRegistration(displayName = "#ACTION_GotoStackDown", lazy = false)
    public static GoStackUpDownAction createDownAction() {
        return new GoStackUpDownAction(false);
    }

    @Override
    protected void actionPerformed(ActionEvent e, InputGraph g, InputNode[] nodes) {
        Node[] activatedNodes = activeNodes();
        NodeStack.Frame frame = activatedNodes[0].getLookup().lookup(NodeStack.Frame.class);
        if (frame == null) {
            return;
        }
        NodeStack.Frame next = goUp ? frame.getParent() : frame.getNested();
        if (next != null) {
            context.setSelectedLocation(next);
        }
    }

    @Override
    protected boolean computeEnabled(InputGraph graph, InputNode[] nodes) {
        Node[] activatedNodes = activeNodes();
        if (activatedNodes.length != 1) {
            return false;
        }
        NodeStack.Frame l = activatedNodes[0].getLookup().lookup(NodeStack.Frame.class);
        if (l == null) {
            return false;
        }
        return goUp ? l.getParent() != null : l.getNested() != null;
    }

    @Override
    public String getName() {
        return goUp ? Bundle.ACTION_GotoStackUp() : Bundle.ACTION_GotoStackDown();
    }

    @Override
    protected String iconResource() {
        return goUp ? "org/graalvm/visualizer/source/resources/disassembler_step_out.gif" : "org/graalvm/visualizer/source/resources/disassembler_step_into.gif";
    }

}
