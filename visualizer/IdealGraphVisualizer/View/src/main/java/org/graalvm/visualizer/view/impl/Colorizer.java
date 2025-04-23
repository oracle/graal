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

package org.graalvm.visualizer.view.impl;

import jdk.graal.compiler.graphio.parsing.model.GraphContainer;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.util.RangeSliderModel;
import org.openide.util.WeakListeners;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Colorizes the RangeSlider based on node selection.
 * Selected nodes need to be set by {@link #setTrackNodes}. Automatically tracks changes to
 * the range slider and retrieves the matching grpahs from the graph container.
 *
 * @author sdedic
 */
public final class Colorizer {
    /**
     * The container for graphs to search for node's presence
     */
    private final GraphContainer container;

    /**
     * The target slider to colorize.
     */
    private final RangeSliderModel slider;

    /**
     * The current set of nodes.
     */
    private Set<Integer> trackedNodes = Collections.emptySet();

    /**
     * Watches changes of the range slider.
     */
    private final PropertyChangeListener propL;

    public Colorizer(GraphContainer container, RangeSliderModel slider) {
        this.container = container;
        this.slider = slider;

        // must prevent GC
        this.propL = this::rangeSliderChanged;
        // only weak change listener
        slider.addPropertyChangeListener(
                RangeSliderModel.PROP_POSITIONS,
                WeakListeners.propertyChange(propL, RangeSliderModel.PROP_POSITIONS, slider));
    }

    private void rangeSliderChanged(PropertyChangeEvent e) {
        refreshColors();
    }

    public Set<Integer> getTrackedNodes() {
        return Collections.unmodifiableSet(trackedNodes);
    }

    public void setTrackedNodes(Set<Integer> trackedNodes) {
        this.trackedNodes = new HashSet<>(trackedNodes);
        refreshColors();
    }

    private void refreshColors() {
        List<InputGraph> graphs = container.getGraphs();
        List<String> sliderPositions = slider.getPositions();
        List<Color> colors = new ArrayList<>(Collections.nCopies(sliderPositions.size(), Color.black));
        List<Color> hatches = new ArrayList<>(Collections.nCopies(sliderPositions.size(), null));
        if (trackedNodes.size() >= 1) {
            for (Integer id : trackedNodes) {
                if (id < 0) {
                    id = -id;
                }
                boolean firstDefined = true;
                int index;
                InputGraph lastGraph = null;
                for (InputGraph g : graphs) {
                    index = sliderPositions.indexOf(g.getName());
                    if (index == -1) {
                        continue;
                    }
                    Color curColor = colors.get(index);
                    if (g.containsNode(id)) {
                        if (firstDefined) {
                            curColor = Color.green;
                        } else {
                            assert lastGraph != null;
                            if (!container.isNodeChanged(lastGraph, g, id)) {
                                if (curColor == Color.black) {
                                    curColor = Color.white;
                                }
                            } else {
                                if (curColor != Color.green) {
                                    curColor = Color.orange;
                                }
                            }
                        }
                        firstDefined = false;
                        lastGraph = g;
                    } else {
                        // can the node re-appear ??
                        firstDefined = true;
                        lastGraph = null;
                    }
                    // make duplicate graph's colors somewhat lighter.
                    if (g.isDuplicate()) {
                        if (curColor == Color.white) {
                            hatches.set(index, Color.gray);
                        } else {
                            hatches.set(index, Color.white);
                        }
                    }
                    colors.set(index, curColor);
                }
            }
        }
        slider.getColorChangedEvent().beginAtomic();
        slider.setColors(colors);
        slider.setHatchColors(hatches);
        slider.getColorChangedEvent().endAtomic();
    }
}
