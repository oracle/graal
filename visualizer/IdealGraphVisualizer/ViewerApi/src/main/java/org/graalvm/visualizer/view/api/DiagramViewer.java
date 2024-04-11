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

import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.graalvm.visualizer.data.services.GraphSelections;
import org.graalvm.visualizer.data.services.InputGraphProvider;
import org.graalvm.visualizer.graph.Figure;
import org.openide.awt.UndoRedo;
import org.openide.util.Lookup;

import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Graphics2D;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Extension of {@link #InputGraphProvider} that is UI-aware.
 *
 * @author sdedic
 */
public interface DiagramViewer extends InputGraphProvider {
    enum InteractionMode {
        SELECTION,
        PANNING,
    }

    public void requestActive(boolean toFront, boolean attention);

    public DiagramModel getModel();

    public GraphSelections getSelections();

    public void paint(Graphics2D svgGenerator);

    public Lookup getLookup();

    public JComponent createSatelliteView();

    public Component getComponent();

    public void zoomOut();

    public void zoomIn();

    public UndoRedo getUndoRedo();

    public Set<InputNode> nodesForFigure(Figure f);

    public Collection<Figure> figuresForNodes(Collection<InputNode> nodes);

    public void setSelection(Collection<Figure> list);

    public List<Figure> getSelection();

    public void centerFigures(Collection<Figure> list);

    public void setInteractionMode(InteractionMode mode);

    /**
     * Execute the runnable after the layout is computed. If no layout is pending,
     * the runnable is executed immediately.
     * <p/>
     * The Runnable is always executed in Swing EDT thread. The caller must not rely
     * on that the Runnable completes before this method returns.
     *
     * @param r the runnable.
     */
    public void executeWithDiagramShown(Runnable r);

    public InteractionMode getInteractionMode();

    public void addDiagramViewerListener(DiagramViewerListener l);

    public void removeDiagramViewerListener(DiagramViewerListener l);
}
