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
package org.graalvm.visualizer.data.services;

import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;

import javax.swing.event.ChangeListener;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Represents a registry or manager of {@link InputGraphProviders} that operate on
 * different {@link Group}s. There's always at most one <b>active</b> {@link InputGraph}:
 * the one that has currently focus, or was the last one displayed.
 *
 * @author sdedic
 */
public interface GraphViewer {
    /**
     * Returns active graph. The method may return {@code null} if all viewers
     * are closed.
     *
     * @return active graph or {@code null}.
     */
    public InputGraph getActiveGraph();

    /**
     * Returns all available viewers
     *
     * @return
     */
    public List<? extends InputGraphProvider> getViewers();

    /**
     * Returns the active viewer, if one exists.
     *
     * @param T viewer type
     * @return the active instance
     */
    public <T extends InputGraphProvider> T getActiveViewer();

    /**
     * Finds Providers that operate on the group.
     *
     * @param g group which the viewer should display
     * @return list of viewers
     */
    public List<? extends InputGraphProvider> find(Group g);

    /**
     * Finds viewers compatible with the given graph. Unlike {@link #find(org.graalvm.visualizer.data.Group)},
     * this method only returns viewers which display the same type of graph as the one passed in.
     *
     * @param g graph to detect type
     * @return compatible opened viewers
     */
    public List<? extends InputGraphProvider> findCompatible(InputGraph g);

    /**
     * Finds Providers display the given graph. Note that even though a Provider may
     * exist for the graph's group it may display a differen graph; this method does
     * not return such Providers.
     *
     * @param graph compiler grap
     * @return
     */
    public List<? extends InputGraphProvider> find(InputGraph graph);

    /**
     * Ensures that a viewer is displayed and visible for the graph.If `clone' is true, the viewer is always created even though another
     * viewer capable of displaying the graph exists.
     *
     * @param graph      input graph to open
     * @param clone      true, if a fresh viewer should be created
     * @param parameters additional parameters to initialize the viewer
     */
    public default void view(InputGraph graph, boolean clone, Object... parameters) {
        view(null, graph, clone, true, parameters);
    }

    /**
     * Ensures that a viewer is displayed and visible for the graph.If `clone' is true, the viewer is always created even though another
     * viewer capable of displaying the graph exists.
     * After the viewer opens or is brought to front, the {@code viewerInit} code is called (if not null). The consumer gets is informed whether
     * the viewer was opened new (true) or not (false) and can manipulate / configure the viewer.
     *
     * @param graph      input graph to open
     * @param clone      true, if a fresh viewer should be created
     * @param parameters additional parameters to initialize the viewer
     * @param activate   if true, transfers focus to the view. If false, it only makes the view visible in its Mode.
     * @param viewerInit initialization callback
     */
    public void view(BiConsumer<Boolean, InputGraphProvider> viewerInit, InputGraph graph, boolean clone, boolean activate, Object... parameters);

    /**
     * Adds a listener to be informed about changes. The listener will be notified
     * when the {@link #getActiveGraph} changes, or when the set of available
     * {@link InputGraphProvider}s change.
     *
     * @param l listener instance
     */
    public void addChangeListener(ChangeListener l);

    /**
     * Removes the change listener
     *
     * @param l listener instance
     */
    public void removeChangeListener(ChangeListener l);
}
