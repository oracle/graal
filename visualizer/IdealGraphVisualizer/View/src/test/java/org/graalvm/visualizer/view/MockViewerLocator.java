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

package org.graalvm.visualizer.view;

import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.data.services.GraphViewer;
import org.graalvm.visualizer.data.services.InputGraphProvider;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.graalvm.visualizer.view.api.DiagramViewerLocator;
import org.graalvm.visualizer.view.impl.GraphViewerImplementation;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;

import javax.swing.event.ChangeListener;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author sdedic
 */
@ServiceProviders({
        @ServiceProvider(service = DiagramViewerLocator.class, supersedes = {"org.graalvm.visualizer.view.impl.GraphViewerImplementation"}),
        @ServiceProvider(service = GraphViewer.class, supersedes = {"org.graalvm.visualizer.view.impl.GraphViewerImplementation"})
})
public class MockViewerLocator implements DiagramViewerLocator {
    GraphViewerImplementation impl = new GraphViewerImplementation() {
        @Override
        protected Stream<DiagramViewer> viewers() {
            return MockViewerLocator.this.getViewers().stream();
        }
    };

    static DiagramViewModel viewModel;
    static DiagramViewer viewer;
    static List<DiagramViewer> viewers = Collections.emptyList();

    @Override
    public DiagramViewer getActiveViewer() {
        return viewer;
    }

    @Override
    public List<DiagramViewer> getViewers() {
        return viewers;
    }

    @Override
    public List<DiagramViewer> find(Group g) {
        return viewers.stream().filter((v) -> v.getModel().getContainer() == g).collect(Collectors.toList());
    }

    @Override
    public List<DiagramViewer> findCompatible(InputGraph g) {
        return find(g.getGroup());
    }

    @Override
    public DiagramViewModel getActiveModel() {
        return viewModel;
    }

    @Override
    public void addChangeListener(ChangeListener l) {
    }

    @Override
    public void removeChangeListener(ChangeListener l) {
    }

    public List<DiagramViewer> find(InputGraph graph) {
        return viewers.stream().filter((v) -> v.getModel().getGraphToView() == graph).collect(Collectors.toList());
    }

    @Override
    public void view(InputGraph graph, boolean clone, Object... parameters) {
        impl.view(graph, clone, parameters);
    }

    @Override
    public void view(BiConsumer<Boolean, InputGraphProvider> viewerInit, InputGraph graph, boolean clone, boolean activate, Object... parameters) {
        impl.view(viewerInit, graph, clone, activate, parameters);
    }

}
