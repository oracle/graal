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
package org.graalvm.visualizer.view.api;

import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.graph.Diagram;
import org.openide.util.Lookup;

import java.util.Collection;
import java.util.concurrent.Future;
import java.util.function.Consumer;

/**
 * Public interface to the diagram viewer data.
 *
 * @author sdedic
 */
public interface DiagramModel extends Lookup.Provider {
    /**
     * Returns graph container that this viewer operates upon. It should be the same
     * as {@link #getTimeline()}.{@link TimelineModel#getPrimaryPartition()}.
     * <p>
     * There's always a GraphContainer instance, although virtual. This method never
     * returns {@code null}.
     *
     * @return GraphContainer instance.
     */
    public GraphContainer getContainer();

    /**
     * @return the current graph to be displayed.
     */
    public default InputGraph getGraphToView() {
        return getDiagramToView().getGraph();
    }

    /**
     * Provides access to the timeline model of the viewer. The Timeline can be
     * used to select a graph or diff of graphs to view.
     *
     * @return timeline model instance
     */
    public TimelineModel getTimeline();

    /**
     * Specifies that a certain graph should be viewed. The graph must be currently
     * visible in the timeline's main partition.
     *
     * @param g graph to select.
     * @return true on successful switch
     */
    public boolean selectGraph(InputGraph g);

    /**
     * Returns the current diagram to work with. Note that the diagram may be a temporary stub
     * that should be used before the actual diagram is processed and prepared.
     *
     * @return current diagram to view.
     */
    public Diagram getDiagramToView();

    /**
     * Determines if diagram is a temporary stub.
     *
     * @param d diagram to check
     * @return true, if d is a stub.
     */
    public boolean isStubDiagram(Diagram d);

    /**
     * Executes a task with a fully initialized diagram. The task may run
     * immediately, or may be deferred to a later time, when the diagram becomes
     * ready. The Future can be used to wait on the initialization and to get
     * the diagram.
     * <p/>
     * Threading note: if this method is called in EDT, the delayed task will be
     * also called in EDT. Otherwise the thread executing the task is
     * unspecified.
     *
     * @param task the task to execute
     * @return Future that produces the diagram instance
     */
    public Future<Diagram> withDiagramToView(Consumer<Diagram> task);

    /**
     * Selects specific nodes in the diagram.
     *
     * @param nodes nodes to select.
     */
    public void setSelectedNodes(Collection<InputNode> nodes);

    /**
     * @return currently selected nodes
     */
    public Collection<InputNode> getSelectedNodes();

    /**
     * @return nodes hidden by the current extraction.
     */
    public Collection<Integer> getHiddenNodes();

    /**
     * Hides specified nodes. Diagram is recomputed.
     *
     * @param excludedNodes nodes to exclude.
     *                      1
     */
    public void showNot(Collection<Integer> excludedNodes);

    /**
     * Shows only specified nodes - extract those nodes.
     *
     * @param nodesToExtract nodes to remain visible.
     */
    public void showOnly(Collection<Integer> nodesToExtract);

    /**
     * Attaches listener.
     *
     * @param l listener instance.
     */
    public void addDiagramListener(DiagramListener l);

    /**
     * Removes listener.
     *
     * @param l listener instance.
     */
    public void removeDiagramListener(DiagramListener l);
}
