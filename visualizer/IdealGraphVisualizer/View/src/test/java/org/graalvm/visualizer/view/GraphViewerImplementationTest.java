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

import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.data.services.GraphSelections;
import org.graalvm.visualizer.data.services.GraphViewer;
import org.graalvm.visualizer.filter.FilterChain;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.settings.layout.LayoutSettings;
import org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;
import org.graalvm.visualizer.view.api.DiagramViewer;
import org.graalvm.visualizer.view.api.DiagramViewerListener;
import org.junit.Assert;
import org.junit.Test;
import org.openide.awt.UndoRedo;
import org.openide.util.Lookup;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertSame;

/**
 * @author sdedic
 */
public class GraphViewerImplementationTest {
    private static final LayoutSettingBean layoutSetting = LayoutSettings.getBean();
    Group g;

    private void loadMegaData() throws Exception {
        GraphDocument checkDocument = ViewTestUtil.loadMegaData();
        Group parent = (Group) checkDocument.getElements().get(0);
        g = (Group) parent.getElements().get(0);
    }

    class MockViewer implements DiagramViewer {
        DiagramViewModel model;

        @Override
        public GraphContainer getContainer() {
            return model.getContainer();
        }

        @Override
        public InputGraph getGraph() {
            return model.getGraphToView();
        }

        @Override
        public void setSelectedNodes(Set<InputNode> nodes) {
        }

        @Override
        public Iterable<InputGraph> searchForward() {
            return Collections.emptyList();
        }

        @Override
        public Iterable<InputGraph> searchBackward() {
            return Collections.emptyList();
        }

        public MockViewer(DiagramViewModel model) {
            this.model = model;
        }

        @Override
        public GraphSelections getSelections() {
            return null;
        }

        @Override
        public DiagramViewModel getModel() {
            return model;
        }

        @Override
        public void paint(Graphics2D svgGenerator) {
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }

        @Override
        public JComponent createSatelliteView() {
            return null;
        }

        @Override
        public Component getComponent() {
            return null;
        }

        @Override
        public void zoomOut() {
        }

        @Override
        public void zoomIn() {
        }

        @Override
        public UndoRedo getUndoRedo() {
            return null;
        }

        @Override
        public void setSelection(Collection<Figure> list) {
        }

        @Override
        public void centerFigures(Collection<Figure> list) {
        }

        @Override
        public void setInteractionMode(InteractionMode mode) {
        }

        @Override
        public void executeWithDiagramShown(Runnable r) {
        }

        @Override
        public List<Figure> getSelection() {
            return Collections.emptyList();
        }

        @Override
        public Collection<Figure> figuresForNodes(Collection<InputNode> nodes) {
            return Collections.emptyList();
        }

        @Override
        public InteractionMode getInteractionMode() {
            return InteractionMode.PANNING;
        }

        @Override
        public void addDiagramViewerListener(DiagramViewerListener l) {
        }

        @Override
        public void removeDiagramViewerListener(DiagramViewerListener l) {
        }

        public Set<InputNode> nodesForFigure(Figure f) {
            return Collections.emptySet();
        }

        @Override
        public void requestActive(boolean toFront, boolean attention) {
        }
    }

    @Test
    public void testViewExisting() throws Exception {
        loadMegaData();
        InputGraph graph = g.getGraphs().get(0);

        assert graph != null;

        FilterChain fch = new FilterChain();

        DiagramViewModel model = new DiagramViewModel(g, fch, layoutSetting);
        MockViewer v = new MockViewer(model);
        List<DiagramViewer> viewers = new ArrayList<>();
        MockViewer v2 = new MockViewer(new DiagramViewModel((Group) g.getElements().get(1), fch, layoutSetting));

        viewers.add(v2);
        viewers.add(v);

        MockViewerLocator.viewers = viewers;

        InputGraph nextGraph = v.getModel().getContainer().getGraphs().get(1);
        Assert.assertNotSame(nextGraph, v.getModel().getGraphToView());
        // perform the action:
        SwingUtilities.invokeAndWait(() -> Lookup.getDefault().lookup(GraphViewer.class).view(nextGraph, false)
        );
        // must wait until queued events process
        SwingUtilities.invokeAndWait(() -> {
        });

        // assert that the viewer's selected graph has changed
        assertSame(nextGraph, v.getModel().getGraphToView());
    }
}
