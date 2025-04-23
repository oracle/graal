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
package org.graalvm.visualizer.search.ui.actions;

import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.data.services.GraphViewer;
import org.graalvm.visualizer.data.services.NodeContext;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.openide.util.ContextAwareAction;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.Utilities;
import org.openide.util.WeakListeners;

import javax.swing.AbstractAction;
import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * @author sdedic
 */
public abstract class GraphContextAction extends AbstractAction implements ContextAwareAction, LookupListener {

    private static final BitSet EMPTY = new BitSet();
    private static final Map<Group, BitSet> nodeIdsCache = new WeakHashMap<>();
    private final List<InputNode> inputNodes;
    private final Lookup context;
    private final Lookup.Result<InputNode> res;
    private InputGraph targetGraph;
    private Group targetGroup;

    protected GraphContextAction(List<InputNode> nodes) {
        this(nodes, Utilities.actionsGlobalContext());
    }

    protected GraphContextAction(Lookup context) {
        this(new ArrayList<>(context.lookupAll(InputNode.class)), context);
    }

    private GraphContextAction(List<? extends InputNode> nodes, Lookup context) {
        this.inputNodes = (List<InputNode>) nodes;
        this.context = context;
        res = context.lookupResult(InputNode.class);
        res.addLookupListener(WeakListeners.create(LookupListener.class, this, res));
        update();
    }

    @Override
    public void resultChanged(LookupEvent le) {
        if (this.changeSupport != null && this.changeSupport.getPropertyChangeListeners().length == 0) {
            return;
        }
        SwingUtilities.invokeLater(this::update);
    }

    protected List<InputNode> getInputNodes() {
        return inputNodes;
    }

    protected InputGraph getTargetGraph() {
        return targetGraph;
    }

    protected Group getTargetGroup() {
        return targetGroup;
    }

    protected Lookup ctx() {
        return context;
    }

    private void update() {
        DiagramViewer vwr = Lookup.getDefault().lookup(GraphViewer.class).getActiveViewer();
        InputGraph curGraph = vwr == null ? null : vwr.getGraph();
        Group pg = vwr == null ? null : vwr.getGraph().getGroup();

        BitSet nodeIds = null;
        boolean ok = true;
        boolean somePresent = false;

        InputGraph firstGraph = null;
        Group targetGroup = null;

        for (InputNode n : inputNodes) {
            int id = n.getId();
            NodeContext ctx = NodeContext.fromNode(n);
            if (ctx != null) {
                if (curGraph == null) {
                    curGraph = ctx.getGraph();
                    pg = ctx.getParent().getContentOwner();
                }
                Group owner = ctx.getParent().getContentOwner();
                if (targetGroup != null && targetGroup != owner) {
                    // refuse to extract different groups
                    ok = false;
                    break;
                }
                targetGroup = owner;
                if (owner == pg) {
                    if (firstGraph == null) {
                        firstGraph = ctx.getGraph();
                    }
                    if (curGraph.getNodeIds().contains(id)) {
                        somePresent = true;
                    }
                }
                continue;
            } else if (pg != null) {
                if (nodeIds == null) {
                    nodeIds = findNodeIds(pg);
                }
                if (!nodeIds.get(id)) {
                    ok = false;
                    break;
                }
            }
        }
        if (targetGroup == null) {
            targetGroup = pg;
        }
        if (firstGraph == null) {
            firstGraph = curGraph;
        }

        if (!ok || targetGroup == null) {
            setEnabled(false);
            return;
        }
        if (!somePresent || curGraph == null) {
            this.targetGraph = firstGraph;
        } else {
            this.targetGraph = curGraph;
        }
        if (vwr == null || targetGroup != pg) {
            putValue(NAME, createNameWithTarget(targetGroup, targetGraph));
        }
        this.targetGroup = targetGroup;
    }

    protected abstract String createNameWithTarget(Group targetGroup, InputGraph targetGraph);

    private static BitSet findNodeIds(Group g) {
        if (g == null) {
            return EMPTY;
        }
        BitSet bs = nodeIdsCache.get(g);
        if (bs != null) {
            return bs;
        }
        bs = new BitSet();
        for (InputGraph ig : g.getGraphs()) {
            for (Integer i : ig.getNodeIds()) {
                bs.set(i);
            }
        }
        nodeIdsCache.put(g, bs);
        return bs;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.getClass() == getClass();
    }

    /**
     * Allow multiple actions to merge together
     *
     * @return
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
